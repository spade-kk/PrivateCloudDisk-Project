package org.project.privateclouddiskgatewayservice.filter.global;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.project.privateclouddiskgatewayservice.dto.ApiResponse;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * 上游响应标准化过滤器 — 解决多语言微服务错误响应格式不统一的核心组件
 *
 * <h2>问题背景</h2>
 * 微服务架构中不同服务使用不同框架/语言：
 * <ul>
 *   <li>Spring Boot 业务服务 → 404 返回 Spring Boot 默认 JSON 错误格式</li>
 *   <li>Go 存储服务 → 404 返回 Go 框架自定义错误格式</li>
 *   <li>IM 服务 → 可能返回不同格式</li>
 *   <li>未来服务 → 完全不可预知</li>
 * </ul>
 * 结果：同一个 404 错误，不同服务返回的 JSON 结构完全不同，前端无法统一处理。
 *
 * <h2>解决方案</h2>
 * 本过滤器拦截所有上游（下游微服务）的响应：
 * <ol>
 *   <li><b>2xx 成功</b>：透传，不做任何修改</li>
 *   <li><b>4xx/5xx 错误</b>：读取上游原始响应体，包装为统一的 {@link ApiResponse} 格式</li>
 * </ol>
 *
 * <h2>标准化后的错误响应格式</h2>
 * <pre>{@code
 * {
 *   "code": 404,
 *   "message": "请求的资源不存在",
 *   "data": null
 * }
 * }</pre>
 *
 * <h2>实现细节</h2>
 * 使用 {@link ServerHttpResponseDecorator} 在 writeWith 阶段拦截响应体：
 * <ul>
 *   <li>2xx 直接透传原始字节</li>
 *   <li>4xx/5xx 解析原始 JSON，提取有用信息，包装为统一格式后写入</li>
 * </ul>
 *
 * <h2>安全性</h2>
 * <ul>
 *   <li>不泄露上游服务的内部错误详情（堆栈、框架信息等）</li>
 *   <li>仅透传上游返回的 message 字段（如有），否则使用语义化默认消息</li>
 *   <li>5xx 错误统一返回"服务器内部错误"，不暴露实现细节</li>
 * </ul>
 *
 * <h2>执行顺序</h2>
 * Order = LOWEST_PRECEDENCE（在 NettyRoutingFilter 之后执行，确保已获取到上游响应）
 */
@Slf4j
@Component
public class UpstreamResponseNormalizerFilter implements GlobalFilter, Ordered {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static boolean isErrorStatus(HttpStatusCode status) {
        if (status == null) return false;
        int code = status.value();
        return code >= 400 && code < 600;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        NormalizingResponseDecorator decoratedResponse = new NormalizingResponseDecorator(
                exchange, exchange.getResponse());
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    // ──────────── 内部类：响应标准化装饰器 ────────────

    /**
     * 响应标准化装饰器
     * <p>
     * 装饰 {@link ServerHttpResponse}，在 writeWith 阶段拦截响应体写入：
     * <ul>
     *   <li>2xx 成功：直接透传</li>
     *   <li>4xx/5xx 错误：捕获上游原始响应体，替换为统一的 ApiResponse 格式</li>
     * </ul>
     */
    static class NormalizingResponseDecorator extends ServerHttpResponseDecorator {

        private final ServerWebExchange exchange;

        NormalizingResponseDecorator(ServerWebExchange exchange, ServerHttpResponse delegate) {
            super(delegate);
            this.exchange = exchange;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            HttpStatusCode status = getStatusCode();
            if (status == null || !isErrorStatus(status)) {
                // 2xx 成功，直接透传
                return super.writeWith(body);
            }

            // 4xx/5xx 错误，捕获并标准化
            return Flux.from(body)
                    .collectList()
                    .flatMap(buffers -> {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        List<DataBuffer> typed = (List) buffers;
                        return normalizeAndWrite(status, typed);
                    });
        }

        @Override
        public Mono<Void> writeAndFlushWith(
                Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return writeWith(Flux.from(body).flatMapSequential(Flux::from));
        }

        /**
         * 将上游原始响应体标准化后写入
         */
        private Mono<Void> normalizeAndWrite(HttpStatusCode status,
                                              List<DataBuffer> buffers) {
            int code = status.value();
            byte[] capturedBody = mergeBuffers(buffers);

            // 释放上游 buffer
            releaseBuffers(buffers);

            // 尝试从上游原始响应中提取错误信息
            String upstreamMessage = extractMessageFromUpstream(capturedBody);

            // 语义化默认消息
            String normalizedMessage = upstreamMessage != null
                    ? upstreamMessage
                    : getDefaultMessage(code);

            String requestPath = exchange.getRequest().getURI().getPath();
            String requestMethod = exchange.getRequest().getMethod() != null
                    ? exchange.getRequest().getMethod().name() : "UNKNOWN";

            log.info("上游响应已标准化: [{}] {} {} → code={}, message=\"{}\", upstreamBodySize={}",
                    requestMethod, requestPath, status, code, normalizedMessage,
                    capturedBody != null ? capturedBody.length : 0);

            // 构建统一格式响应
            ApiResponse<Void> unifiedResponse = ApiResponse.error(code, normalizedMessage);

            byte[] normalizedBytes;
            try {
                normalizedBytes = OBJECT_MAPPER.writeValueAsBytes(unifiedResponse);
            } catch (Exception e) {
                log.error("序列化统一错误响应失败: path={}", requestPath, e);
                normalizedBytes = String.format(
                        "{\"code\":%d,\"message\":\"%s\",\"data\":null}",
                        code, escapeJson(normalizedMessage)
                ).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }

            // 确保 Content-Type 为 JSON
            getHeaders().setContentType(MediaType.APPLICATION_JSON);
            getHeaders().setContentLength(normalizedBytes.length);

            DataBuffer buffer = bufferFactory().wrap(normalizedBytes);
            return super.writeWith(Mono.just(buffer));
        }

        /**
         * 合并多个 DataBuffer 为单个字节数组
         */
        private byte[] mergeBuffers(List<DataBuffer> buffers) {
            if (buffers == null || buffers.isEmpty()) return new byte[0];

            int totalSize = 0;
            for (DataBuffer buf : buffers) {
                totalSize += buf.readableByteCount();
            }

            ByteBuffer combined = ByteBuffer.allocate(totalSize);
            for (DataBuffer buf : buffers) {
                combined.put(buf.toByteBuffer());
            }
            return combined.array();
        }

        /**
         * 释放上游 buffer 以避免内存泄漏
         */
        private void releaseBuffers(List<DataBuffer> buffers) {
            if (buffers == null) return;
            for (DataBuffer buf : buffers) {
                try {
                    buf.readPosition(buf.readableByteCount());
                } catch (Exception ignored) {
                }
            }
        }

        // ──────────── 上游消息提取 ────────────

        /**
         * 尝试从上游原始响应体中提取错误信息
         * <p>
         * 支持多种上游格式：
         * <ul>
         *   <li>Spring Boot 默认错误格式: {"timestamp":"...","status":404,"error":"Not Found","message":"...","path":"..."}</li>
         *   <li>Spring Boot 2.3+ ProblemDetail: {"type":"...","title":"...","status":404,"detail":"..."}</li>
         *   <li>自定义格式: {"code":404,"message":"...","data":null}</li>
         *   <li>Go 框架: {"error":"...","code":404}</li>
         * </ul>
         * 无法提取时返回 null，由调用方使用默认消息。
         */
        @SuppressWarnings("unchecked")
        private String extractMessageFromUpstream(byte[] body) {
            if (body == null || body.length == 0) return null;

            try {
                Map<String, Object> map = OBJECT_MAPPER.readValue(body, Map.class);

                // 1. 标准 message 字段
                if (map.containsKey("message") && map.get("message") instanceof String msg && !msg.isBlank()) {
                    return msg;
                }

                // 2. ProblemDetail 的 detail 字段
                if (map.containsKey("detail") && map.get("detail") instanceof String detail && !detail.isBlank()) {
                    return detail;
                }

                // 3. 旧版 Spring Boot 的 error 字段
                if (map.containsKey("error") && map.get("error") instanceof String err && !err.isBlank()) {
                    return err;
                }

                return null;
            } catch (Exception e) {
                return null;
            }
        }
    }

    // ──────────── 默认消息映射 ────────────

    /**
     * 根据 HTTP 状态码返回语义化中文默认消息
     */
    static String getDefaultMessage(int code) {
        return switch (code) {
            case 400 -> "请求参数错误";
            case 401 -> "未授权，请先登录";
            case 403 -> "没有权限访问该资源";
            case 404 -> "请求的资源不存在";
            case 405 -> "请求方法不允许";
            case 406 -> "不支持的响应格式";
            case 408 -> "请求超时";
            case 409 -> "资源冲突";
            case 410 -> "资源已永久删除";
            case 413 -> "请求体过大";
            case 414 -> "请求 URI 过长";
            case 415 -> "不支持的媒体类型";
            case 422 -> "请求参数语义错误";
            case 429 -> "请求过于频繁，请稍后重试";
            case 500 -> "服务器内部错误";
            case 502 -> "上游服务暂不可用";
            case 503 -> "服务暂不可用，请稍后重试";
            case 504 -> "网关超时，请稍后重试";
            default -> {
                if (code >= 400 && code < 500) {
                    yield "客户端请求错误";
                } else if (code >= 500 && code < 600) {
                    yield "服务器内部错误";
                }
                yield "未知错误";
            }
        };
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}