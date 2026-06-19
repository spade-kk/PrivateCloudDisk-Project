package org.project.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.project.control.result.JsonResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

/**
 * Sentinel 流量控制 + 熔断降级配置
 *
 * 企业级用法:
 *   - @SentinelResource 注解: 对关键方法进行流量控制和熔断降级
 *   - 支持 flow / degrade / param / system / authority 规则
 *   - 规则持久化到 Nacos 配置中心，实现动态管理和热更新
 *   - 自定义 BlockExceptionHandler 统一返回结构化的降级响应
 */
@Configuration
public class SentinelConfig {

    private final ObjectMapper objectMapper;

    public SentinelConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 自定义 BlockException 处理器
     * 统一返回 JSON 格式的降级响应，避免默认的异常页面
     */
    @Bean
    public BlockExceptionHandler sentinelBlockExceptionHandler() {
        return (HttpServletRequest request, HttpServletResponse response, String resourceName, BlockException ex) -> {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            JsonResult<Void> result;
            int status = HttpServletResponse.SC_TOO_MANY_REQUESTS;

            if (ex instanceof FlowException) {
                result = JsonResult.error(1429, "请求过于频繁，请稍后再试 (限流)");
            } else if (ex instanceof DegradeException) {
                result = JsonResult.error(1503, "服务暂时不可用，请稍后再试 (熔断)");
                status = HttpServletResponse.SC_SERVICE_UNAVAILABLE;
            } else if (ex instanceof ParamFlowException) {
                result = JsonResult.error(1429, "热点参数访问过于频繁，请稍后再试 (热点限流)");
            } else if (ex instanceof SystemBlockException) {
                result = JsonResult.error(1429, "系统负载过高，请稍后再试 (系统保护)");
            } else if (ex instanceof AuthorityException) {
                result = JsonResult.error(1403, "您没有权限访问该资源 (授权)");
                status = HttpServletResponse.SC_FORBIDDEN;
            } else {
                result = JsonResult.error(1429, "请求被限制，请稍后再试");
            }

            response.setStatus(status);
            objectMapper.writeValue(response.getWriter(), result);
        };
    }
}