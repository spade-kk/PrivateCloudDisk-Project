package org.project.privateclouddiskgatewayservice.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.privateclouddiskgatewayservice.dto.ApiErrorResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

/**
 * 自定义 403 访问拒绝处理器（WebFlux 版本）
 * <p>
 * 当已认证用户尝试访问无权限资源时，返回统一 JSON 格式的 403 响应。
 * <p>
 * 注意：未认证用户会先被 AuthGlobalFilter 拦截返回 401，
 * 因此本处理器仅在用户已认证但权限不足时触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        String requestPath = exchange.getRequest().getURI().getPath();
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

        log.warn("访问被拒绝: userId={}, path={}", userId, requestPath);

        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("您没有权限访问该资源")
                .path(requestPath)
                .build();

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(error);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("403 响应序列化失败", e);
            return Mono.error(e);
        }
    }
}