package org.project.privateclouddiskgatewayservice.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
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
 * JWT 解析与验证工具类
 * <p>
 * 使用 RS256 非对称加密算法，公钥验证 JWT 签名。
 * 公钥从类路径下的 public.pem 文件加载。
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("classpath:keys/public_key.pem")
    private Resource publicKeyResource;
    private PublicKey publicKey;

    /**
     * Bean 初始化后立即加载公钥，避免每次请求都读取文件
     */
    @PostConstruct
    public void init() {
        try {
            publicKey = loadPublicKey(publicKeyResource);

            log.info("公钥加载成功");
        } catch (Exception e) {
            log.error("公钥加载失败", e);
            throw new RuntimeException("Failed to load JWT public key", e);
        }
    }

    /**
     * 从 PEM 文件路径加载公钥
     * @param pemFileResource 公钥文件路径
     * @return PublicKey 对象
     */
    private static PublicKey loadPublicKey(Resource pemFileResource) throws Exception {
        String publicKeyPEM = StreamUtils.copyToString(
                        pemFileResource.getInputStream(), StandardCharsets.UTF_8)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        return  keyFactory.generatePublic(keySpec);
    }

    /**
     * 使用公钥验证并解析 JWT
     *
     * @param token JWT 字符串
     * @return 解析后的 Jws 对象，可通过 getPayload() 获取 Claims
     * @throws JwtException 当 JWT 无效或过期时抛出
     */
    public Jws<Claims> parseAndVerifyAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)       // 使用公钥验证签名
                .build()
                .parseSignedClaims(token);   // 解析已签名的 JWT
    }

    /**
     * 从 JWT 中提取用户ID
     */
    public String getUserIdFromToken(String token) {
        return parseAndVerifyAccessToken(token)
                .getPayload()
                .getSubject();
    }
}
