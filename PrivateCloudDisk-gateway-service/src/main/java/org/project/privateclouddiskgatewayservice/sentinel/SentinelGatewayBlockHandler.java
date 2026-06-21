package org.project.sentinel;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.DefaultBlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sentinel 网关层统一 Block 回调处理器。
 * <p>
 * 职责：当 Sentinel 在网关层拦截请求时，返回统一的 JSON 错误响应。
 * 与 {@link org.project.privateclouddiskgatewayservice.filter.global.GatewayRateLimitFilter}
 * 形成互补——前者是业务策略限流，后者是系统保护限流。
 *
 * <h3>错误码设计</h3>
 * <table>
 *   <tr><th>异常类型</th><th>HTTP 状态码</th><th>业务码</th><th>含义</th></tr>
 *   <tr><td>FlowException</td><td>429</td><td>42901</td><td>流量控制（QPS 超限）</td></tr>
 *   <tr><td>ParamFlowException</td><td>429</td><td>42902</td><td>热点参数限流</td></tr>
 *   <tr><td>DegradeException</td><td>503</td><td>50301</td><td>熔断降级</td></tr>
 *   <tr><td>AuthorityException</td><td>403</td><td>40301</td><td>授权规则（黑白名单）</td></tr>
 *   <tr><td>SystemBlockException</td><td>429</td><td>42903</td><td>系统保护（LOAD/CPU 等）</td></tr>
 * </table>
 */
@Slf4j
@Component
public class SentinelGatewayBlockHandler {

    @PostConstruct
    public void init() {
        GatewayCallbackManager.setBlockHandler(new CustomBlockRequestHandler());
        log.info("Sentinel Gateway BlockHandler registered");
    }

    /**
     * 自定义 Block 请求处理器。
     * 根据不同的 BlockException 子类型返回差异化错误信息。
     */
    static class CustomBlockRequestHandler implements DefaultBlockRequestHandler {

        @Override
        public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable ex) {
            BlockException blockException = findBlockException(ex);

            String path = exchange.getRequest().getURI().getPath();
            String method = exchange.getRequest().getMethod().name();
            String clientIp = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", Instant.now().toString());
            body.put("path", method + " " + path);

            if (blockException instanceof FlowException) {
                // QPS 流量控制
                body.put("code", 42901);
                body.put("message", "请求过于频繁，系统流量控制已触发");
                body.put("detail", "当前接口 QPS 超过阈值，请稍后重试");
                return buildResponse(HttpStatus.TOO_MANY_REQUESTS, body);

            } else if (blockException instanceof ParamFlowException) {
                // 热点参数限流
                ParamFlowException pfe = (ParamFlowException) blockException;
                body.put("code", 42902);
                body.put("message", "操作过于频繁，热点参数限流已触发");
                body.put("detail", "该资源（文件/用户）当前访问热度超限，请稍后重试");
                return buildResponse(HttpStatus.TOO_MANY_REQUESTS, body);

            } else if (blockException instanceof DegradeException) {
                // 熔断降级
                DegradeException de = (DegradeException) blockException;
                body.put("code", 50301);
                body.put("message", "服务暂时不可用，系统熔断已触发");
                body.put("detail", "后端服务异常率过高，已自动熔断保护，请稍后重试");
                return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, body);

            } else if (blockException instanceof AuthorityException) {
                // 黑白名单
                body.put("code", 40301);
                body.put("message", "访问被拒绝，授权规则不通过");
                body.put("detail", "当前请求来源不在允许范围内");
                return buildResponse(HttpStatus.FORBIDDEN, body);

            } else if (blockException instanceof SystemBlockException) {
                // 系统保护
                body.put("code", 42903);
                body.put("message", "系统负载过高，系统保护已触发");
                body.put("detail", "当前系统负载（LOAD/CPU/RT）超过保护阈值，请稍后重试");
                return buildResponse(HttpStatus.TOO_MANY_REQUESTS, body);

            } else {
                // 未知限流类型
                body.put("code", 42900);
                body.put("message", "请求被限流");
                body.put("detail", blockException != null ? blockException.getMessage() : "unknown");
                return buildResponse(HttpStatus.TOO_MANY_REQUESTS, body);
            }
        }

        /**
         * 递归查找 BlockException（可能被包装在其他异常中）。
         */
        private BlockException findBlockException(Throwable ex) {
            if (ex == null) return null;
            if (ex instanceof BlockException) return (BlockException) ex;
            return findBlockException(ex.getCause());
        }

        private Mono<ServerResponse> buildResponse(HttpStatus status, Map<String, Object> body) {
            return ServerResponse.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body);
        }
    }
}