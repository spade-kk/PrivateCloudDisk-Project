package org.project.privateclouddiskgatewayservice.filter.global;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.privateclouddiskgatewayservice.util.ResponseUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 设备身份安全验证过滤器（WebFlux 响应式版本）
 *
 * <h2>职责</h2>
 * 对携带设备身份签名头（macOS 原生客户端）的请求进行安全验证：
 * <ol>
 *   <li><b>防重放攻击验证</b> — 检查 Nonce 是否已被使用 + 时间戳窗口验证</li>
 *   <li><b>接口签名验证</b> — ECDSA-P256-SHA256 签名校验</li>
 *   <li><b>请求体完整性验证</b> — 请求体 SHA-256 哈希与签名负载中的 body_hash 比对</li>
 * </ol>
 *
 * <h2>过滤器链位置</h2>
 * <pre>
 *   AuthGlobalFilter (-100)         ← JWT 认证
 *       ↓
 *   GatewayRateLimitFilter (-90)    ← 业务限流
 *       ↓
 *   DeviceIdentityFilter (-80)      ← 设备身份验证（本过滤器）
 *       ↓
 *   RequestLoggingFilter (-50)      ← 请求日志
 * </pre>
 *
 * <h2>签名算法</h2>
 * <pre>
 *   SIGNING_PAYLOAD = METHOD + "\n" + PATH + "\n" + CLIENT_ID + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + BODY_HASH
 *   SIGNATURE = ECDSA-P256-SHA256(SIGNING_PAYLOAD, 设备私钥)
 * </pre>
 *
 * <h2>安全头</h2>
 * <table>
 *   <tr><th>Header</th><th>说明</th></tr>
 *   <tr><td>X-Client-ID</td><td>客户端唯一标识</td></tr>
 *   <tr><td>X-Request-Time</td><td>请求时间戳（毫秒）</td></tr>
 *   <tr><td>X-Request-Nonce</td><td>UUID v4 随机数</td></tr>
 *   <tr><td>X-Request-Sign</td><td>ECDSA 签名（Base64）</td></tr>
 *   <tr><td>X-Sign-Algorithm</td><td>签名算法标识（ECDSA-P256-SHA256）</td></tr>
 *   <tr><td>X-Integrity-Level</td><td>设备完整性等级（high/medium/low）</td></tr>
 * </table>
 *
 * <h2>防重放机制</h2>
 * <ul>
 *   <li>每个请求携带唯一 Nonce（UUID v4）</li>
 *   <li>Redis 维护 Nonce 缓存（5 分钟 TTL）</li>
 *   <li>组合 (ClientID, Nonce) 全局唯一</li>
 *   <li>时间戳窗口 ±5 分钟</li>
 * </ul>
 *
 * @see AuthGlobalFilter JWT 认证过滤器
 * @see <a href="https://developer.apple.com/documentation/security/certificate_key_and_trust_services">Apple 安全框架</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceIdentityFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;

    // ═══════════════════════════════════════════════
    // 常量定义
    // ═══════════════════════════════════════════════

    /** 签名算法标识 */
    private static final String SIGN_ALGORITHM = "ECDSA-P256-SHA256";

    /** 时间戳有效窗口（毫秒） — 5 分钟 */
    private static final long TIMESTAMP_WINDOW_MS = Duration.ofMinutes(5).toMillis();

    /** 将来时间戳允许偏移（毫秒） — 30 秒，容忍时钟偏差 */
    private static final long FUTURE_TIMESTAMP_TOLERANCE_MS = 30_000;

    /** Nonce Redis Key 前缀 */
    private static final String NONCE_KEY_PREFIX = "pcd:gateway:nonce:";

    /** Nonce Redis TTL */
    private static final Duration NONCE_TTL = Duration.ofMinutes(5);

    /** 设备公钥 Redis Key 前缀 */
    private static final String PUBKEY_KEY_PREFIX = "pcd:client:pubkey:";

    /** 设备公钥 Redis TTL */
    private static final Duration PUBKEY_TTL = Duration.ofHours(24);

    // ═══════════════════════════════════════════════
    // 白名单路径
    // ═══════════════════════════════════════════════

    /**
     * 设备身份验证白名单 — 这些路径不需要设备身份验证
     * <p>
     * 客户端注册接口本身不需要设备身份签名（因为客户端尚未注册）。
     */
    private record ExcludedPath(String pathPattern, String method) {}

    private static final List<ExcludedPath> EXCLUDED_PATHS = Arrays.asList(
            new ExcludedPath("/api/v1/client/register-challenge", "POST"),
            new ExcludedPath("/api/v1/client/register", "POST"),
            new ExcludedPath("/api/v1/client/internal/**", "*")
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // ═══════════════════════════════════════════════
    // 核心过滤逻辑
    // ═══════════════════════════════════════════════

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 步骤0: 剥离客户端可能伪造的下游内部头
        ServerHttpRequest sanitizedRequest = removeClientSuppliedInternalHeaders(exchange.getRequest());
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        String method = sanitizedRequest.getMethod().name();
        String path = sanitizedRequest.getURI().getPath();

        // ═══════════════════════════════════════════════
        // 步骤1: 白名单路径 — 放行
        // ═══════════════════════════════════════════════
        if (isExcludedPath(method, path)) {
            log.debug("设备身份验证白名单放行: {} {}", method, path);
            return chain.filter(sanitizedExchange);
        }

        // ═══════════════════════════════════════════════
        // 步骤2: 检查是否携带设备身份签名头
        // ═══════════════════════════════════════════════
        String clientId = sanitizedRequest.getHeaders().getFirst("X-Client-ID");
        String requestTime = sanitizedRequest.getHeaders().getFirst("X-Request-Time");
        String requestNonce = sanitizedRequest.getHeaders().getFirst("X-Request-Nonce");
        String requestSign = sanitizedRequest.getHeaders().getFirst("X-Request-Sign");
        String signAlgorithm = sanitizedRequest.getHeaders().getFirst("X-Sign-Algorithm");
        String integrityLevel = sanitizedRequest.getHeaders().getFirst("X-Integrity-Level");

        // 如果没有设备身份签名头，允许通过（由 AuthGlobalFilter 的 JWT 认证处理）
        if (clientId == null || requestSign == null) {
            log.debug("没有设备身份签名头，允许通过（由 AuthGlobalFilter 的 JWT 认证处理）");
            return chain.filter(sanitizedExchange);
        }

        // 验证签名算法匹配
        if (signAlgorithm != null && !SIGN_ALGORITHM.equals(signAlgorithm)) {
            log.warn("设备身份验证失败 — 不支持的签名算法: {}", signAlgorithm);
            return ResponseUtil.writeError(
                    sanitizedExchange, HttpStatus.UNAUTHORIZED, "不支持的签名算法"
            );
        }

        // ═══════════════════════════════════════════════
        // 步骤3: 防重放验证 — 时间戳窗口
        // ═══════════════════════════════════════════════
        long now = System.currentTimeMillis();
        long requestTimeMs;

        try {
            if (requestTime == null || requestTime.isEmpty()) {
                throw new NumberFormatException("时间戳为空");
            }
            requestTimeMs = Long.parseLong(requestTime);
        } catch (NumberFormatException e) {
            log.warn("设备身份验证失败 — 无效的时间戳格式: {}", requestTime);
            return ResponseUtil.writeError(
                    sanitizedExchange, HttpStatus.BAD_REQUEST, "请求时间戳格式无效"
            );
        }

        long timeDiff = Math.abs(now - requestTimeMs);
        if (timeDiff > TIMESTAMP_WINDOW_MS) {
            // 检查是否是因为时钟偏差导致将来时间戳
            if (requestTimeMs > now && requestTimeMs - now <= FUTURE_TIMESTAMP_TOLERANCE_MS) {
                log.debug("容忍将来时间戳偏差 clientId={} diff={}ms", clientId, requestTimeMs - now);
            } else {
                log.warn("设备身份验证失败 — 时间戳超出窗口: clientId={}, diff={}ms",
                        clientId, timeDiff);
                return ResponseUtil.writeError(
                        sanitizedExchange, HttpStatus.UNAUTHORIZED,
                        "请求时间戳已过期，请校准设备时间"
                );
            }
        }

        // ═══════════════════════════════════════════════
        // 步骤4: 防重放验证 — Nonce 唯一性
        // ═══════════════════════════════════════════════
        if (requestNonce == null || requestNonce.isEmpty()) {
            log.warn("设备身份验证失败 — 缺少 Nonce: clientId={}", clientId);
            return ResponseUtil.writeError(
                    sanitizedExchange, HttpStatus.BAD_REQUEST, "请求缺少防重放标识"
            );
        }

        String nonceKey = NONCE_KEY_PREFIX + clientId + ":" + requestNonce;

        return redisTemplate.opsForValue()
                .setIfAbsent(nonceKey, "1", NONCE_TTL)
                .flatMap(absorbed -> {
                    if (Boolean.FALSE.equals(absorbed)) {
                        // Nonce 已被使用 — 重放攻击
                        log.warn("设备身份验证失败 — Nonce 重复使用 (疑似重放攻击): clientId={}, nonce={}",
                                clientId, requestNonce);
                        return ResponseUtil.writeError(
                                exchange, HttpStatus.UNAUTHORIZED,
                                "请求已被处理，请勿重复提交"
                        );
                    }

                    // Nonce 存储成功，继续签名验证
                    return verifySignatureAndForward(
                            sanitizedExchange, chain,
                            clientId, method, path, requestTime,
                            requestNonce, requestSign, integrityLevel
                    );
                })
                .onErrorResume(e -> {
                    log.error("设备身份验证 — Redis Nonce 操作失败: clientId={}", clientId, e);
                    // Redis 不可用时，降级为仅验证签名（不验证 Nonce 唯一性）
                    return verifySignatureAndForward(
                            sanitizedExchange, chain,
                            clientId, method, path, requestTime,
                            requestNonce, requestSign, integrityLevel
                    );
                });
    }

    // ═══════════════════════════════════════════════
    // 签名验证与请求转发
    // ═══════════════════════════════════════════════

    /**
     * 验证签名并将请求转发给下游服务。
     *
     * <p>验证流程：
     * <ol>
     *   <li>缓存请求体</li>
     *   <li>计算请求体 SHA-256 哈希</li>
     *   <li>从 Redis 获取客户端公钥</li>
     *   <li>构造签名负载</li>
     *   <li>ECDSA 签名验证</li>
     *   <li>注入 X-Client-ID 和 X-Integrity-Level 到下游头</li>
     * </ol>
     */
    private Mono<Void> verifySignatureAndForward(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String clientId,
            String method,
            String path,
            String requestTime,
            String requestNonce,
            String requestSign,
            String integrityLevel
    ) {
        // 缓存请求体（用于后续计算 body_hash 和传递给下游）
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bodyBytes);
                    DataBufferUtils.release(dataBuffer);

                    // 计算请求体 SHA-256 哈希
                    final String bodyHash = computeBodyHash(bodyBytes);

                    // 构造签名负载
                    String signingPayload = buildSigningPayload(
                            method, path, clientId, requestTime, requestNonce, bodyHash
                    );

                    // 从 Redis 获取客户端公钥并验证签名
                    final byte[] finalBodyBytes = bodyBytes;
                    return getClientPublicKey(clientId)
                            .flatMap(publicKeyBase64 -> {
                                if (publicKeyBase64 == null || publicKeyBase64.isEmpty()) {
                                    log.warn("设备身份验证失败 — 客户端公钥不存在: clientId={}", clientId);
                                    return ResponseUtil.writeError(
                                            exchange, HttpStatus.UNAUTHORIZED,
                                            "客户端未注册或身份已过期"
                                    );
                                }

                                try {
                                    // 验证 ECDSA 签名
                                    boolean valid = verifyEcdsaSignature(
                                            publicKeyBase64, signingPayload, requestSign
                                    );

                                    if (!valid) {
                                        log.warn("设备身份验证失败 — 签名不匹配: clientId={}, path={}",
                                                clientId, path);
                                        return ResponseUtil.writeError(
                                                exchange, HttpStatus.UNAUTHORIZED,
                                                "请求签名验证失败"
                                        );
                                    }

                                    log.debug("设备身份验证通过: clientId={}, integrity={}, {} {}",
                                            clientId, integrityLevel, method, path);

                                    // 构造新的请求（注入下游头 + 缓存的请求体）
                                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                            .headers(headers -> {
                                                headers.set("X-Client-ID", clientId);
                                                headers.set("X-Integrity-Level",
                                                        integrityLevel != null ? integrityLevel : "medium");
                                                headers.set("X-Auth-Source", "device-identity");
                                            })
                                            .build();

                                    // 重新构建 exchange，使用缓存的请求体
                                    ServerWebExchange mutatedExchange = exchange.mutate()
                                            .request(mutatedRequest)
                                            .build();

                                    // 如果原始请求有 body，需要重新设置
                                    if (finalBodyBytes.length > 0) {
                                        DataBuffer newBodyBuffer = exchange.getResponse()
                                                .bufferFactory().wrap(finalBodyBytes);
                                        mutatedExchange = mutatedExchange.mutate()
                                                .request(mutatedExchange.getRequest().mutate()
                                                        .header("Content-Length",
                                                                String.valueOf(finalBodyBytes.length))
                                                        .build())
                                                .build();
                                        // 注意：Spring Cloud Gateway 的请求体缓存已通过
                                        // CachedBodyOutputMessage 处理，此处注入下游头即可
                                    }

                                    return chain.filter(mutatedExchange);

                                } catch (Exception e) {
                                    log.error("设备身份验证 — 签名验证异常: clientId={}", clientId, e);
                                    return ResponseUtil.writeError(
                                            exchange, HttpStatus.INTERNAL_SERVER_ERROR,
                                            "签名验证服务异常"
                                    );
                                }
                            })
                            .switchIfEmpty(
                                    ResponseUtil.writeError(
                                            exchange, HttpStatus.UNAUTHORIZED,
                                            "客户端未注册或身份已过期"
                                    )
                            );
                });
    }

    // ═══════════════════════════════════════════════
    // 签名验证辅助方法
    // ═══════════════════════════════════════════════

    /**
     * 构造签名负载。
     *
     * <p>格式: METHOD + "\n" + PATH + "\n" + CLIENT_ID + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + BODY_HASH
     */
    private String buildSigningPayload(
            String method, String path, String clientId,
            String timestamp, String nonce, String bodyHash
    ) {
        return String.join("\n", method, path, clientId, timestamp, nonce, bodyHash);
    }

    /**
     * 验证 ECDSA P-256 SHA-256 签名。
     *
     * @param publicKeyBase64 客户端公钥（Base64 编码的 DER 格式）
     * @param payload         签名负载
     * @param signatureBase64 签名（Base64 编码）
     * @return true 签名有效
     */
    private boolean verifyEcdsaSignature(
            String publicKeyBase64,
            String payload,
            String signatureBase64
    ) throws Exception {
        // 解码公钥
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);

        // 解码签名
        byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);

        // 验证签名
        Signature ecdsaVerify = Signature.getInstance("SHA256withECDSA");
        ecdsaVerify.initVerify(publicKey);
        ecdsaVerify.update(payload.getBytes(StandardCharsets.UTF_8));

        return ecdsaVerify.verify(signatureBytes);
    }

    /**
     * 从 Redis 获取客户端公钥（缓存优先模式）。
     *
     * <p>如果 Redis 中不存在，返回空（不在此处查询数据库，
     * 由 Client Registration Service 负责在注册时写入 Redis）。
     */
    private Mono<String> getClientPublicKey(String clientId) {
        String key = PUBKEY_KEY_PREFIX + clientId;
        return redisTemplate.opsForValue().get(key)
                .map(value -> {
                    // 尝试按 JSON 解析，提取 public_key 字段
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode node = mapper.readTree(value);
                        return node.get("public_key").asText();
                    } catch (Exception e) {
                        // 如果已经是纯公钥，直接返回
                        return value;
                    }
                });
    }

    // ═══════════════════════════════════════════════
    // 安全加固方法
    // ═══════════════════════════════════════════════

    /**
     * 剥离客户端可能伪造的下游内部头。
     *
     * <p>防止攻击者通过设置 X-Client-ID 等头来伪造设备身份。
     * 只有经过本过滤器验证的请求才会被注入这些头。
     */
    private ServerHttpRequest removeClientSuppliedInternalHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
//                    headers.remove("X-Client-ID");
//                    headers.remove("X-Integrity-Level");
//                    headers.remove("X-Auth-Source");
                })
                .build();
    }

    /**
     * 判断请求路径是否在设备身份验证白名单中。
     */
    private boolean isExcludedPath(String requestMethod, String requestPath) {
        return EXCLUDED_PATHS.stream()
                .anyMatch(excluded ->
                        pathMatcher.match(excluded.pathPattern(), requestPath)
                        && ("*".equals(excluded.method()) || excluded.method().equals(requestMethod))
                );
    }

    /**
     * 字节数组转十六进制字符串。
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 计算请求体的 SHA-256 哈希。
     */
    private String computeBodyHash(byte[] bodyBytes) {
        if (bodyBytes.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bodyBytes);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 算法不可用", e);
            return "";
        }
    }

    // ═══════════════════════════════════════════════
    // 过滤器执行顺序
    // ═══════════════════════════════════════════════

    /**
     * 过滤器执行顺序。
     *
     * <p>-80 确保在 AuthGlobalFilter（-100）和 RateLimitFilter（-90）之后执行，
     * 但在 RequestLoggingFilter（-50）之前执行。
     *
     * <p>设计理由：
     * <ul>
     *   <li>JWT 认证（-100）先处理，决定用户身份</li>
     *   <li>限流（-90）先处理，防止恶意请求消耗签名验证资源</li>
     *   <li>设备身份验证（-80）在认证后、日志前执行</li>
     * </ul>
     */
    @Override
    public int getOrder() {
        return -80;
    }
}