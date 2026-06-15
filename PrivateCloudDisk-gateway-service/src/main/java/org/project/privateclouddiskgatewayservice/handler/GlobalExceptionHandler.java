package org.project.privateclouddiskgatewayservice.handler;

import lombok.extern.slf4j.Slf4j;
import org.project.privateclouddiskgatewayservice.dto.ApiErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import reactor.netty.channel.AbortedException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 全局异常处理器（WebFlux 响应式版本）
 * <p>
 * 实现 {@link WebExceptionHandler} 而非 {@code @RestControllerAdvice}，
 * 确保能够拦截 Spring Cloud Gateway 路由匹配阶段的所有异常。
 * <p>
 * 异常处理优先级（安全设计）：
 * <ol>
 *   <li>认证失败 → 401（在 AuthGlobalFilter 中处理，此处兜底）</li>
 *   <li>权限不足 → 403</li>
 *   <li>路由不存在 → 404</li>
 *   <li>请求方法不允许 → 405</li>
 *   <li>参数格式错误 → 400</li>
 *   <li>其他异常 → 500（不泄露内部细节）</li>
 * </ol>
 */
@Slf4j
@Component
@Order(-2) // 优先级高于默认的 DefaultErrorWebExceptionHandler (Order -1)
public class GlobalExceptionHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        // 响应已提交，无法再修改
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // 客户端断开连接，无需响应
        if (ex instanceof AbortedException) {
            log.warn("客户端断开连接: {}", ex.getMessage());
            return Mono.empty();
        }

        HttpStatus status;
        String message;
        String logLevel;

        // ═══════════════════════════════════════════════
        // 按异常类型精确匹配，避免兜底 500
        // ═══════════════════════════════════════════════

        if (ex instanceof ResponseStatusException rse) {
            // Spring WebFlux 标准响应状态异常（含 404, 405 等）
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            message = resolveResponseStatusMessage(status, rse.getReason());
            logLevel = status.is4xxClientError() ? "WARN" : "ERROR";

        } else if (ex instanceof ServerWebInputException) {
            // 请求参数格式错误
            status = HttpStatus.BAD_REQUEST;
            message = "请求参数格式错误";
            logLevel = "WARN";

        } else if (ex instanceof UnsupportedMediaTypeStatusException) {
            // 不支持的 Content-Type
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            message = "不支持的媒体类型";
            logLevel = "WARN";

        } else if (ex instanceof MethodNotAllowedException) {
            // 请求方法不允许
            status = HttpStatus.METHOD_NOT_ALLOWED;
            message = "请求方法不允许";
            logLevel = "WARN";

        } else if (ex instanceof SecurityException) {
            // 安全异常（权限不足）
            status = HttpStatus.FORBIDDEN;
            message = "访问被拒绝";
            logLevel = "WARN";

        } else if (ex instanceof IllegalArgumentException) {
            // 非法参数
            status = HttpStatus.BAD_REQUEST;
            message = "请求参数非法";
            logLevel = "WARN";

        } else {
            // ═══════════════════════════════════════════════
            // 未知异常：500，但不泄露内部细节
            // ═══════════════════════════════════════════════
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "服务器内部错误";
            logLevel = "ERROR";
        }

        // 统一日志输出
        String requestPath = exchange.getRequest().getURI().getPath();
        String requestMethod = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name() : "UNKNOWN";

        if ("ERROR".equals(logLevel)) {
            log.error("网关异常 [{}] {} {}: {}", status.value(), requestMethod, requestPath, ex.getMessage(), ex);
        } else {
            log.warn("网关异常 [{}] {} {}: {}", status.value(), requestMethod, requestPath, ex.getMessage());
        }

        // 构建标准 JSON 错误响应
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiErrorResponse errorBody = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(requestPath)
                .build();

        try {
            // 使用 Jackson 序列化（保证 JSON 格式正确且无注入风险）
            tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
            byte[] bytes = mapper.writeValueAsBytes(errorBody);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (Exception serializationError) {
            // 序列化失败，返回最简 JSON
            log.error("错误响应序列化失败", serializationError);
            String fallback = String.format(
                    "{\"code\":%d,\"message\":\"%s\",\"timestamp\":\"%s\"}",
                    status.value(), message, LocalDateTime.now()
            );
            return response.writeWith(Mono.just(
                    response.bufferFactory().wrap(fallback.getBytes(StandardCharsets.UTF_8))
            ));
        }
    }

    /**
     * 将 ResponseStatusException 的 reason 映射为中文提示
     * <p>
     * 注意：不暴露路由是否存在的信息，404 统一返回"请求的资源不存在"
     */
    private String resolveResponseStatusMessage(HttpStatus status, String reason) {
        if (reason != null && !reason.isBlank()) {
            return reason;
        }
        return switch (status) {
            case NOT_FOUND             -> "请求的资源不存在";
            case METHOD_NOT_ALLOWED    -> "请求方法不允许";
            case BAD_REQUEST           -> "请求参数错误";
            case UNSUPPORTED_MEDIA_TYPE -> "不支持的媒体类型";
            case TOO_MANY_REQUESTS     -> "请求过于频繁，请稍后重试";
            case UNAUTHORIZED          -> "未授权，请先登录";
            case FORBIDDEN             -> "没有权限访问该资源";
            case INTERNAL_SERVER_ERROR -> "服务器内部错误";
            case SERVICE_UNAVAILABLE   -> "服务暂不可用，请稍后重试";
            case GATEWAY_TIMEOUT       -> "网关超时";
            default                    -> status.getReasonPhrase();
        };
    }
}