package org.project.privateclouddiskgatewayservice.filter.global;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.privateclouddiskgatewayservice.util.ResponseUtil;
import org.project.privateclouddiskgatewayservice.config.properties.AiIdentitySigningProperties;
import org.project.privateclouddiskgatewayservice.config.properties.McpIdentitySigningProperties;
import org.project.privateclouddiskgatewayservice.utils.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * JWT 认证全局过滤器（WebFlux 响应式版本）
 * <p>
 * 过滤器职责与优先级（Order = -100，在所有路由匹配之前执行）：
 * <ol>
 *   <li>剥离客户端伪造的内部请求头（防注入）</li>
 *   <li>检查请求是否在白名单中（直接放行，跳过认证）</li>
 *   <li>从 Authorization 头提取 Bearer Token</li>
 *   <li>验证 JWT 签名、有效期和格式</li>
 *   <li>将用户信息注入请求头，透传给下游服务</li>
 * </ol>
 * <p>
 * 安全设计：未认证请求直接返回 401，不进入路由匹配。
 * 这防止了攻击者通过探测不同路径的响应码来枚举 API 端点。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final AiIdentitySigningProperties aiIdentitySigningProperties;
    private final McpIdentitySigningProperties mcpIdentitySigningProperties;

    // ═══════════════════════════════════════════════
    // 白名单路径配置
    // ═══════════════════════════════════════════════
    private record ExcludedPath(String pathPattern, String method) {}

    private static final List<ExcludedPath> EXCLUDED_PATHS = Arrays.asList(
            new ExcludedPath("/api/v1/business/users/login", "POST"),                  // 登录
            new ExcludedPath("/api/v1/business/users/", "POST"),                       // 注册
            new ExcludedPath("/api/v1/business/public/shares/**", "*"),                 // 主业务服务公开分享相关接口
            new ExcludedPath("/api/v1/business/verification-code/register/send", "POST"),// 邮箱验证码
            new ExcludedPath("/api/v1/business/verification-code/register/resend", "POST"),// 邮箱验证码
            new ExcludedPath("/api/v1/client/register-challenge", "POST"),             // 客户端注册挑战值
            new ExcludedPath("/api/v1/client/register", "POST"),                       // 客户端注册
            // MCP OAuth Protected Resource Metadata is intentionally public so MCP clients can
            // discover the configured authorization server before obtaining a bearer token.
            new ExcludedPath("/api/v1/.well-known/oauth-protected-resource/**", "GET"),
            /*
             * [REQ-GIT-AUDIT-2.1~2.50/4.16] 原行为仅放行三条 Smart HTTP 路径，
             * HEAD 与只读 dumb HTTP object/pack/refs 请求会被 Gateway JWT 拦截，
             * 造成标准 Git CLI 与代理缓存兼容性断裂。新行为仅放行 .git 协议根下的
             * GET/HEAD 和两条 Smart RPC POST，以及让 Git Service 明确拒绝的 object PUT；
             * 所有 PAT、匿名公开读取、隐藏仓库掩码和写权限仍由 git-service 统一校验。
             * /api/v1/git/repos/** 管理 API 不在白名单，继续使用 Gateway JWT。
             */
            new ExcludedPath("/api/v1/git/*.git/**", "GET"),
            new ExcludedPath("/api/v1/git/*.git/**", "HEAD"),
            new ExcludedPath("/api/v1/git/*.git/git-upload-pack", "POST"),
            new ExcludedPath("/api/v1/git/*.git/git-receive-pack", "POST"),
            new ExcludedPath("/api/v1/git/*.git/objects/**", "PUT"),
            // [REQ-GIT-HTTP-4.7] 兼容标准 Git URL https://domain/git/{repo}.git；管理 API 仍保留 /api/v1 前缀。
            new ExcludedPath("/git/*.git/**", "GET"),
            new ExcludedPath("/git/*.git/**", "HEAD"),
            new ExcludedPath("/git/*.git/git-upload-pack", "POST"),
            new ExcludedPath("/git/*.git/git-receive-pack", "POST"),
            new ExcludedPath("/git/*.git/objects/**", "PUT"),
            new ExcludedPath("/api/v1/business/admin/**", "*"),                        // 管理员接口（由 AdminGatewayFilter 处理）
            new ExcludedPath("/ws/**", "*")                                             // im websocket协议服务 方向代理路径地址 不需要网关鉴权直接放行下游
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // ═══════════════════════════════════════════════
    // 核心过滤逻辑
    // ═══════════════════════════════════════════════

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 步骤0: 安全加固 — 剥离客户端可能伪造的内部请求头
        ServerHttpRequest sanitizedRequest = removeClientSuppliedInternalHeaders(exchange.getRequest());
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        String requestPath = sanitizedRequest.getURI().getPath();
        String requestMethod = sanitizedRequest.getMethod().name();
        boolean mcpRequest = "/api/v1/mcp".equals(requestPath) || requestPath.startsWith("/api/v1/mcp/");

        /*
         * Sprint 0 安全基线（插件生态设计文档 16.1）：
         * 原行为把 /api/v1/business/internal/** 加入公网白名单并转发至业务服务；
         * 新行为在公网网关最前置阶段直接拒绝。文件服务、插件服务、工作流服务必须
         * 通过容器私网直连 platform-service-backend:8081，并携带服务间凭证。
         */
        if (pathMatcher.match("/api/v1/business/internal/**", requestPath)
                || pathMatcher.match("/api/v1/client/internal/**", requestPath)) {
            log.warn("拒绝通过公网网关访问内部接口: {} {}", requestMethod, requestPath);
            return ResponseUtil.writeError(
                    sanitizedExchange,
                    HttpStatus.NOT_FOUND,
                    "请求的资源不存在"
            );
        }

        // ═══════════════════════════════════════════════
        // 步骤1: 白名单路径 — 直接放行，不执行认证
        // ═══════════════════════════════════════════════
        if (isExcludedPath(requestMethod, requestPath)) {
            log.debug("白名单路径放行: {} {}", requestMethod, requestPath);
            return chain.filter(sanitizedExchange);
        }

        // ═══════════════════════════════════════════════
        // 步骤2: 提取 Authorization 头
        // ═══════════════════════════════════════════════
        String authHeader = sanitizedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null) {
            log.warn("认证失败 - 缺少 Authorization 头: {} {}", requestMethod, requestPath);
            return writeAuthenticationError(
                    sanitizedExchange,
                    HttpStatus.UNAUTHORIZED,
                    "缺少认证令牌，请先登录",
                    mcpRequest
            );
        }

        if (!authHeader.startsWith("Bearer ")) {
            log.warn("认证失败 - Authorization 头格式错误 (非 Bearer): {} {}",
                    requestMethod, requestPath);
            return writeAuthenticationError(
                    sanitizedExchange,
                    HttpStatus.UNAUTHORIZED,
                    "认证令牌格式错误",
                    mcpRequest
            );
        }

        String token = authHeader.substring(7).trim();

        if (token.isEmpty()) {
            log.warn("认证失败 - Bearer Token 为空: {} {}", requestMethod, requestPath);
            return writeAuthenticationError(
                    sanitizedExchange,
                    HttpStatus.UNAUTHORIZED,
                    "认证令牌不能为空",
                    mcpRequest
            );
        }

        // ═══════════════════════════════════════════════
        // 步骤3: 验证并解析 JWT
        // ═══════════════════════════════════════════════
        try {
            String userId = jwtUtil.getUserIdFromToken(token);
            if (mcpRequest && !jwtUtil.hasMcpAudienceAndScope(
                    token,
                    mcpIdentitySigningProperties.getRequiredAudience(),
                    mcpIdentitySigningProperties.getRequiredScope()
            )) {
                log.warn("MCP 认证失败 - access token 未满足配置的 audience/scope: {} {}", requestMethod, requestPath);
                return writeAuthenticationError(
                        sanitizedExchange,
                        HttpStatus.UNAUTHORIZED,
                        "MCP access token 的 audience 或 scope 不满足要求",
                        true
                );
            }

            /*
             * [AI-AGENT-IDENTITY-001] Original behavior injected only X-User-Id for
             * every downstream service. New behavior keeps that legacy header intact,
             * but for the isolated Cloud AI Agent route also removes browser-supplied
             * agent headers and signs user/space/request/path context. The Agent rejects
             * unsigned/expired context, while Capability Hub still enforces resource
             * permissions. Scope is deliberately limited to /api/v1/ai/**.
             */
            boolean aiAgentRequest = requestPath.startsWith("/api/v1/ai/");
            if (aiAgentRequest && (aiIdentitySigningProperties.getIdentitySigningSecret() == null
                    || aiIdentitySigningProperties.getIdentitySigningSecret().isBlank())) {
                log.error("AI Agent 路由拒绝：未配置 gateway.ai.identity-signing-secret");
                return ResponseUtil.writeError(
                        sanitizedExchange,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "AI 助手身份服务暂不可用"
                );
            }
            if (mcpRequest && (mcpIdentitySigningProperties.getIdentitySigningSecret() == null
                    || mcpIdentitySigningProperties.getIdentitySigningSecret().isBlank())) {
                log.error("MCP 路由拒绝：未配置 gateway.mcp.identity-signing-secret");
                return ResponseUtil.writeError(
                        sanitizedExchange,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "MCP 身份服务暂不可用"
                );
            }
            String spaceId = sanitizedRequest.getHeaders().getFirst("X-Space-Id");
            String tenantId = mcpRequest ? jwtUtil.getTenantIdFromToken(token) : "";
            String requestId = sanitizedRequest.getHeaders().getFirst("X-Request-Id");
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }
            // AI Python verifier uses epoch seconds for its established wire contract. MCP uses
            // RFC3339 because its Go verifier validates a typed UTC timestamp. Keep both formats
            // isolated rather than silently changing the existing AI Agent signature protocol.
            String aiIdentityTimestamp = String.valueOf(System.currentTimeMillis() / 1000L);
            String mcpIdentityTimestamp = java.time.Instant.now().toString();
            String agentSignature = aiAgentRequest
                    ? signAiIdentity(requestMethod, requestPath, requestId, aiIdentityTimestamp, userId, spaceId)
                    : null;
            // StripPrefix=2 changes the downstream path to /mcp. Sign this canonical private-hop
            // path so the Go server validates exactly the request it receives, not a mutable URI.
            String mcpSignature = mcpRequest
                    ? signMcpIdentity(requestMethod, "/mcp", requestId, mcpIdentityTimestamp, userId, tenantId, spaceId)
                    : null;

            // 步骤4: 将用户信息注入请求头，透传给下游服务
            String finalRequestId = requestId;
            String finalAiIdentityTimestamp = aiIdentityTimestamp;
            String finalMcpIdentityTimestamp = mcpIdentityTimestamp;
            String finalAgentSignature = agentSignature;
            ServerHttpRequest mutatedRequest = sanitizedRequest.mutate()
                    .headers(headers -> {
                        headers.set("X-User-Id", userId);
                        headers.set("X-Auth-Source", "gateway");
                        headers.set("X-Request-Id", finalRequestId);
                        if (aiAgentRequest) {
                            headers.set("X-PCD-User-Id", userId);
                            if (spaceId != null && !spaceId.isBlank()) {
                                headers.set("X-PCD-Space-Id", spaceId);
                            } else {
                                headers.remove("X-PCD-Space-Id");
                            }
                            headers.set("X-PCD-Request-Id", finalRequestId);
                            headers.set("X-PCD-Identity-Timestamp", finalAiIdentityTimestamp);
                            headers.set("X-PCD-Identity-Signature", finalAgentSignature);
                        }
                        if (mcpRequest) {
                            headers.set("X-PCD-User-Id", userId);
                            if (tenantId != null && !tenantId.isBlank()) {
                                headers.set("X-PCD-Tenant-Id", tenantId);
                            } else {
                                headers.remove("X-PCD-Tenant-Id");
                            }
                            if (spaceId != null && !spaceId.isBlank()) {
                                headers.set("X-PCD-Space-Id", spaceId);
                            } else {
                                headers.remove("X-PCD-Space-Id");
                            }
                            headers.set("X-PCD-Request-Id", finalRequestId);
                            headers.set("X-PCD-Identity-Timestamp", finalMcpIdentityTimestamp);
                            headers.set("X-PCD-Identity-Signature", mcpSignature);
                        }
                    })
                    .build();

            log.info("认证通过: userId={}, {} {}", userId, requestMethod, requestPath);
            return chain.filter(sanitizedExchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            log.warn("认证失败 - JWT 已过期: {} {}, 过期时间: {}",
                    requestMethod, requestPath, e.getClaims().getExpiration());
            return writeAuthenticationError(
                    sanitizedExchange,
                    HttpStatus.UNAUTHORIZED,
                    "认证令牌已过期，请重新登录",
                    mcpRequest
            );

        } catch (SignatureException e) {
            log.warn("认证失败 - JWT 签名无效 (可能被篡改): {} {}", requestMethod, requestPath);
            return writeAuthenticationError(
                    sanitizedExchange,
                    HttpStatus.UNAUTHORIZED,
                    "认证令牌无效",
                    mcpRequest
            );

        } catch (MalformedJwtException e) {
            log.warn("认证失败 - JWT 格式错误: {} {}", requestMethod, requestPath);
            return writeAuthenticationError(
                    sanitizedExchange,
                    HttpStatus.UNAUTHORIZED,
                    "认证令牌格式无效",
                    mcpRequest
            );

        } catch (UnsupportedJwtException e) {
            log.warn("认证失败 - 不支持的 JWT 类型: {} {}", requestMethod, requestPath);
            return writeAuthenticationError(
                    sanitizedExchange,
                    HttpStatus.UNAUTHORIZED,
                    "认证令牌类型不支持",
                    mcpRequest
            );

        } catch (JwtException e) {
            // 其他 JWT 异常
            log.warn("认证失败 - JWT 验证异常: {} {}, 原因: {}",
                    requestMethod, requestPath, e.getMessage());
            return writeAuthenticationError(
                    sanitizedExchange,
                    HttpStatus.UNAUTHORIZED,
                    "认证令牌验证失败",
                    mcpRequest
            );

        } catch (Exception e) {
            // JWT 解析过程中不可预期的内部错误（如公钥加载失败）
            log.error("认证过程内部错误: {} {}, 异常类型: {}",
                    requestMethod, requestPath, e.getClass().getSimpleName(), e);
            return ResponseUtil.writeError(
                    sanitizedExchange,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "认证服务暂时不可用"
            );
        }
    }

    // ═══════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════

    /**
     * MCP clients discover OAuth metadata from an unauthenticated challenge.
     * Other Gateway APIs retain their existing JSON-only 401 behavior.
     */
    private Mono<Void> writeAuthenticationError(
            ServerWebExchange exchange,
            HttpStatus status,
            String message,
            boolean mcpRequest
    ) {
        if (mcpRequest && status == HttpStatus.UNAUTHORIZED) {
            exchange.getResponse().getHeaders().set(
                    HttpHeaders.WWW_AUTHENTICATE,
                    "Bearer realm=\"cloudflow-mcp\", resource_metadata=\""
                            + mcpProtectedResourceMetadataUrl() + "\""
            );
        }
        return ResponseUtil.writeError(exchange, status, message);
    }

    private String mcpProtectedResourceMetadataUrl() {
        String baseUrl = mcpIdentitySigningProperties.getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            // Development fallback only. Production deployment must configure
            // MCP_PUBLIC_BASE_URL so this response never derives a URL from a
            // request-controlled Host/X-Forwarded-Host header.
            return "/api/v1/.well-known/oauth-protected-resource/mcp";
        }
        return baseUrl.replaceAll("/+$", "")
                + "/api/v1/.well-known/oauth-protected-resource/mcp";
    }

    /**
     * 剥离客户端请求中可能伪造的内部请求头
     * <p>
     * 防止攻击者通过设置 X-User-Id 等头来伪造身份。
     * 只有经过本过滤器认证的请求才会被注入这些头。
     * <p>
     * 注意：以下请求头是合法的客户端标识，不会剥离：
     * <ul>
     *   <li>X-Device-Fingerprint — 设备指纹（Web 浏览器）</li>
     *   <li>X-Device-Id — 设备 ID（Native App）</li>
     *   <li>X-Client-Type — 客户端类型</li>
     *   <li>X-Timestamp — 请求时间戳</li>
     *   <li>X-Nonce — 请求防重放 nonce</li>
     *   <li>X-Request-Signature — 请求签名（Native App）</li>
     *   <li>X-Platform — 平台标识（Native App）</li>
     *   <li>X-Client-Version — 客户端版本（Native App）</li>
     * </ul>
     * <p>
     * 设备身份相关的下游头由 {@link DeviceIdentityFilter} 负责剥离和注入。
     */
    private ServerHttpRequest removeClientSuppliedInternalHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-Auth-Source");
                    headers.remove("X-Session-Id");
                    headers.remove("X-PCD-Service-Token");
                    // [AI-AGENT-IDENTITY-001] These are reissued only after JWT validation.
                    headers.remove("X-PCD-User-Id");
                    headers.remove("X-PCD-Tenant-Id");
                    headers.remove("X-PCD-Space-Id");
                    headers.remove("X-PCD-Request-Id");
                    headers.remove("X-PCD-Identity-Timestamp");
                    headers.remove("X-PCD-Identity-Signature");
                })
                .build();
    }

    private String signAiIdentity(
            String method,
            String path,
            String requestId,
            String timestamp,
            String userId,
            String spaceId
    ) {
        try {
            String canonical = String.join("\n",
                    "pcd-ai-v1", method.toUpperCase(), path, requestId, timestamp, userId,
                    spaceId == null ? "" : spaceId);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    aiIdentitySigningProperties.getIdentitySigningSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return java.util.HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法签名 AI Agent 身份上下文", exception);
        }
    }

    private String signMcpIdentity(
            String method,
            String path,
            String requestId,
            String timestamp,
            String userId,
            String tenantId,
            String spaceId
    ) {
        try {
            String canonical = String.join("\n",
                    "pcd-mcp-v1", method.toUpperCase(), path, requestId, timestamp, userId,
                    tenantId == null ? "" : tenantId,
                    spaceId == null ? "" : spaceId);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    mcpIdentitySigningProperties.getIdentitySigningSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return java.util.HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法签名 MCP 身份上下文", exception);
        }
    }

    /**
     * 判断请求路径和方法是否在认证白名单中
     */
    private boolean isExcludedPath(String requestMethod, String requestPath) {
        return EXCLUDED_PATHS.stream()
                .anyMatch(excluded ->
                        pathMatcher.match(excluded.pathPattern(), requestPath)
                        && ("*".equals(excluded.method()) || excluded.method().equals(requestMethod))
                );
    }

    /**
     * 过滤器执行顺序
     * <p>
     * -100 确保在所有内置过滤器和路由匹配之前执行。
     * 这样做的好处：未认证请求在进入路由匹配前就被拦截，
     * 攻击者无法通过不同路径的响应差异来枚举 API 端点。
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
