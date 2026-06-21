package org.project.privateclouddiskgatewayservice.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.project.privateclouddiskgatewayservice.dto.ApiResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 响应工具类 — 统一写入 JSON 格式的 {@link ApiResponse} 到响应体
 * <p>
 * 网关中所有需要直接返回错误响应的地方（认证失败、限流、异常处理等）
 * 都通过此工具类写入，确保响应格式完全一致。
 * <p>
 * 线程安全：内部持有的 {@link ObjectMapper} 是不可变配置，多线程安全。
 */
@Slf4j
public final class ResponseUtil {

    /**
     * 全局共享的 ObjectMapper（线程安全）
     * <p>注册 JavaTimeModule 以支持 LocalDateTime 序列化</p>
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ResponseUtil() {
        // 工具类不允许实例化
    }

    /**
     * 写入统一格式的错误响应
     *
     * @param exchange 当前请求的 ServerWebExchange
     * @param status   HTTP 状态码
     * @param message  错误提示信息（中文）
     * @return Mono<Void> 响应式完成信号
     */
    public static Mono<Void> writeError(ServerWebExchange exchange,
                                         HttpStatus status,
                                         String message) {
        return writeResponse(exchange, status, ApiResponse.error(status.value(), message));
    }

    /**
     * 写入任意 ApiResponse 到响应体
     */
    public static Mono<Void> writeResponse(ServerWebExchange exchange,
                                            HttpStatus status,
                                            ApiResponse<?> body) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            log.warn("响应已提交，无法写入: status={}, path={}",
                    status.value(), exchange.getRequest().getURI().getPath());
            return Mono.empty();
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = OBJECT_MAPPER.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            log.error("序列化 ApiResponse 失败", e);
            bytes = fallbackJson(status.value(), body.getMessage());
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 序列化失败时的兜底 JSON
     */
    private static byte[] fallbackJson(int code, String message) {
        String json = String.format(
                "{\"code\":%d,\"message\":\"%s\",\"data\":null}",
                code,
                escapeJson(message != null ? message : "服务器内部错误")
        );
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * JSON 字符串转义（兜底使用）
     */
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