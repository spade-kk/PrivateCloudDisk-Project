package org.project.privateclouddiskgatewayservice.filter.global;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.privateclouddiskgatewayservice.config.properties.AdminGatewayProperties;
import org.project.privateclouddiskgatewayservice.utils.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 管理员 API 网关安全过滤器
 * <p>
 * 职责：
 * <ol>
 *   <li>IP 白名单校验 — 仅允许配置的 IP 访问管理员接口</li>
 *   <li>管理员密钥校验 — 验证 X-Admin-Key 请求头</li>
 *   <li>管理员 JWT 校验 — 对非登录接口验证管理员令牌</li>
 * </ol>
 * <p>
 * 优先级 Order = -95，在 AuthGlobalFilter (-100) 之后、RateLimitFilter (-90) 之前执行。
 * 管理员请求经过此过滤器验证后，跳过 AuthGlobalFilter 直接放行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminGatewayFilter implements GlobalFilter, Ordered {

    private final AdminGatewayProperties adminGatewayProperties;
    private final JwtUtil jwtUtil;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ADMIN_TOKEN_PREFIX = "admin:";
    private static final tools.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new tools.jackson.databind.ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!adminGatewayProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        String requestPath = exchange.getRequest().getURI().getPath();
        String requestMethod = exchange.getRequest().getMethod().name();

        if (!requestPath.startsWith(adminGatewayProperties.getPathPrefix())) {
            return chain.filter(exchange);
        }

        log.info("管理员 API 请求: {} {}", requestMethod, requestPath);

        // ═══════════════════════════════════════════════
        // 步骤1: IP 白名单校验
        // ═══════════════════════════════════════════════
        String clientIp = resolveClientIp(exchange);
        if (!isAllowedIp(clientIp)) {
            log.warn("管理员 API 访问被拒绝 - IP 不在白名单: ip={}, path={}", clientIp, requestPath);
            return writeErrorResponse(exchange, HttpStatus.FORBIDDEN,
                    "Access denied: IP not in admin whitelist");
        }

        // ═══════════════════════════════════════════════
        // 步骤2: 管理员密钥校验
        // ═══════════════════════════════════════════════
        String adminKey = exchange.getRequest().getHeaders().getFirst("X-Admin-Key");
        if (adminKey == null || !adminGatewayProperties.getAdminKey().equals(adminKey)) {
            log.warn("管理员 API 访问被拒绝 - 管理员密钥无效: ip={}, path={}", clientIp, requestPath);
            return writeErrorResponse(exchange, HttpStatus.FORBIDDEN,
                    "Access denied: invalid admin key");
        }

        // ═══════════════════════════════════════════════
        // 步骤3: 登录接口直接放行（不需要 JWT）
        // ═══════════════════════════════════════════════
        if (requestPath.equals(adminGatewayProperties.getLoginPath())) {
            log.info("管理员登录接口放行: ip={}", clientIp);
            return chain.filter(exchange);
        }

        // ═══════════════════════════════════════════════
        // 步骤4: 非登录接口 — 验证管理员 JWT
        // ═══════════════════════════════════════════════
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("管理员 API 访问被拒绝 - 缺少 Authorization 头: ip={}, path={}", clientIp, requestPath);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    "Missing admin authentication token");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        try {
            String userId = jwtUtil.getUserIdFromToken(token);

            if (!userId.startsWith(ADMIN_TOKEN_PREFIX)) {
                log.warn("管理员 API 访问被拒绝 - 非管理员令牌: ip={}, subject={}", clientIp, userId);
                return writeErrorResponse(exchange, HttpStatus.FORBIDDEN,
                        "Access denied: not an admin token");
            }

            String adminId = userId.substring(ADMIN_TOKEN_PREFIX.length());

            // 将管理员信息注入请求头，透传给下游业务服务
            var mutatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.set("X-Admin-Id", adminId);
                        headers.set("X-Auth-Source", "admin-gateway");
                    })
                    .build();

            log.info("管理员 API 认证通过: adminId={}, ip={}, {} {}", adminId, clientIp, requestMethod, requestPath);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.warn("管理员 API 认证失败 - JWT 验证异常: ip={}, path={}, error={}",
                    clientIp, requestPath, e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    "Invalid admin authentication token");
        }
    }

    /**
     * 解析客户端真实 IP
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.trim();
        }
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    /**
     * 检查 IP 是否在白名单中
     */
    private boolean isAllowedIp(String ip) {
        return adminGatewayProperties.getAllowedIps().contains(ip);
    }

    /**
     * 写入错误响应
     */
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"code\":%d,\"message\":\"%s\",\"timestamp\":\"%s\"}",
                status.value(), message, LocalDateTime.now()
        );
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -95;
    }
}