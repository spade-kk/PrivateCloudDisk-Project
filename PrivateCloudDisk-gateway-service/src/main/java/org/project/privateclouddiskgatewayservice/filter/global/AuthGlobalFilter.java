package org.project.privateclouddiskgatewayservice.filter.global;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.privateclouddiskgatewayservice.utils.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * JWT 认证全局过滤器（WebFlux 响应式版本）
 * <p>
 * 职责：
 * 1. 检查请求是否在白名单中（直接放行）
 * 2. 从请求头提取 Bearer Token
 * 3. 验证 JWT 签名和有效期
 * 4. 将用户信息注入请求头，透传给下游服务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    // 白名单路径配置（包含路径和允许的HTTP方法，*表示所有方法）
    private record ExcludedPath(String pathPattern, String method) {}

    private static final List<ExcludedPath> EXCLUDED_PATHS = Arrays.asList(
            new ExcludedPath("/api/v1/business/users/login", "POST"),      // 登录接口 (仅POST)
            new ExcludedPath("/api/v1/business/users/", "POST"),   // 注册接口 (仅POST)
            new ExcludedPath("/api/v1/business/internal/**", "*")       // 业务服务内部通信接口 (所有方法)
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestPath = exchange.getRequest().getURI().getPath();
        String requestMethod = exchange.getRequest().getMethod().name();
        log.debug("网关拦截请求: {} {}", requestMethod, requestPath);

        // 1. 白名单路径直接放行
        if (isExcludedPath(requestMethod, requestPath)) {
            log.debug("白名单路径，直接放行: {}", requestPath);
            return chain.filter(exchange);
        }

        // 2. 从请求头中提取 JWT token
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("请求缺少 Authorization 头: {}", requestPath);
            return unauthorizedResponse(exchange, "缺少认证令牌");
        }

        String token = authHeader.substring(7);

        try {
            // 3. 验证并解析 JWT
            String userId = jwtUtil.getUserIdFromToken(token);

            // 4. 验证成功，将用户信息注入请求头，透传给下游服务
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-Auth-Source", "gateway")
                    .build();

            log.info("认证通过: 用户ID={}, 请求路径={}", userId, requestPath);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException e) {
            log.warn("JWT 验证失败: {}, 原因: {}", requestPath, e.getMessage());
            return unauthorizedResponse(exchange, "令牌无效或已过期");
        } catch (Exception e) {
            log.error("认证过程发生内部错误: {}", requestPath, e);
            return errorResponse(exchange, "认证服务内部错误");
        }
    }

    /**
     * 判断请求路径和方法是否在白名单中
     */
    private boolean isExcludedPath(String requestMethod, String requestPath) {
        return EXCLUDED_PATHS.stream()
                .anyMatch(excluded -> pathMatcher.match(excluded.pathPattern(), requestPath) &&
                        ("*".equals(excluded.method()) || excluded.method().equals(requestMethod)));
    }

    /**
     * 返回 401 未授权响应
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"code\":401,\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message, java.time.LocalDateTime.now()
        );
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 返回 500 服务器内部错误响应
     */
    private Mono<Void> errorResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"code\":500,\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message, java.time.LocalDateTime.now()
        );
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 设置过滤器执行顺序
     * <p>
     * 数值越小优先级越高。负数确保在大多数内置过滤器之前执行，
     * 实现认证优先处理。
     */
    @Override
    public int getOrder() {
        return -100;
    }
}