package org.project.im.server.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * JWT Token 验证器
 * <p>
 * 使用与 platform-service 相同的 RSA 公钥验证 JWT Token，
 * 从 Token 的 subject 中提取用户 ID。
 * </p>
 *
 * <h3>架构设计</h3>
 * <p>
 * platform-service 使用 RSA 私钥签发 Token（RS256），im-server 使用对应的
 * 公钥验证 Token。这是一个非对称签名方案：
 * <ul>
 *   <li>签发方（platform-service）：持有私钥，负责生成 Token</li>
 *   <li>验证方（im-server）：仅持有公钥，负责验证 Token 签名和提取用户 ID</li>
 * </ul>
 * 这种设计确保即使 im-server 被攻破，攻击者也无法伪造 Token。
 * </p>
 *
 * <h3>Token 结构</h3>
 * <pre>
 * Header:  { "alg": "RS256", "typ": "JWT" }
 * Payload: { "sub": "user-uuid", "iat": 1689500000, "exp": 1689586400 }
 * Signature: RSA-SHA256(Header.Payload, privateKey)
 * </pre>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class JwtTokenVerifier {

    @Value("classpath:keys/public_key.pem")
    private Resource publicKeyResource;

    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            publicKey = loadPublicKey(publicKeyResource);
            log.info("JWT 公钥加载成功，Token 验证器就绪");
        } catch (Exception e) {
            log.error("JWT 公钥加载失败: {}", e.getMessage(), e);
            throw new RuntimeException("JWT 公钥加载失败", e);
        }
    }

    /**
     * 验证 Token 并返回用户 ID
     * <p>
     * 使用 RSA 公钥验证 Token 签名，验证通过后从 subject 中提取用户 ID。
     * 如果 Token 无效、已过期或签名不匹配，返回 null。
     * </p>
     *
     * @param token JWT Token 字符串
     * @return 用户 ID，如果验证失败返回 null
     */
    public String verifyToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();

            if (userId == null || userId.isEmpty()) {
                log.warn("Token 验证成功但 subject 为空");
                return null;
            }

            return userId;
        } catch (JwtException e) {
            log.warn("Token 验证失败: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Token 验证异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 验证 Token 并返回完整的 Claims（负载）
     * <p>
     * 用于需要访问 Token 中自定义字段的场景。
     * </p>
     *
     * @param token JWT Token 字符串
     * @return Claims 对象，如果验证失败返回 null
     */
    public Claims verifyAndGetClaims(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            log.warn("Token 验证失败: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Token 验证异常: {}", e.getMessage(), e);
            return null;
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 从 PEM 文件加载 RSA 公钥
     */
    private static PublicKey loadPublicKey(Resource pemFileResource) throws Exception {
        String publicKeyPEM = StreamUtils.copyToString(
                        pemFileResource.getInputStream(), StandardCharsets.UTF_8)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }
}
