package org.project.util;

import io.jsonwebtoken.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {
    @Value("classpath:keys/private_key.pem")
    private Resource privateKeyResource;
    @Value("classpath:keys/public_key.pem")
    private Resource publicKeyResource;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private final long expirationMs = 86400000; // 24小时
    private final long shareTokenExpirationMs = 15 * 60 * 1000; // 分享访问令牌 15 分钟

    @PostConstruct
    public void init() {
        try {
            //从本地文件中加载私匙 公匙
            privateKey = loadPrivateKey(privateKeyResource);
            publicKey = loadPublicKey(publicKeyResource);
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException("公私钥匙加载失败...", e);
        }
    }

    /**
     * 从 PEM 文件路径加载私钥
     * @param pemFileResource 私钥文件路径
     * @return PrivateKey 对象
     */
    private static PrivateKey loadPrivateKey(Resource pemFileResource) throws Exception {
        // 1. 读取PEM文件内容
        String privateKeyPEM = StreamUtils.copyToString(
                        pemFileResource.getInputStream(), StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);

        // 2. 将字节内容编码为 PKCS8E 规范的私钥对象
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return  keyFactory.generatePrivate(keySpec);
    }

    /**
     * 从 PEM 文件加载公钥
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

    /**
     * 生成登陆身份认证通行证令牌 JWT Token
     * @param user_id
     * @return 通行证令牌
     */
    public String generateAccessToken(String user_id) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(user_id)
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    /**
     * 生成分享短期访问令牌
     * @param share_token 分享令牌
     * @return 短期访问令牌（15 分钟有效）
     */
    public String generateShareAccessToken(String share_token) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + shareTokenExpirationMs);

        return Jwts.builder()
                .setSubject("share:" + share_token)
                .claim("purpose", "share_access")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    /**
     * 验证分享短期访问令牌
     * @param token 访问令牌
     * @return share_token，如果无效返回 null
     */
    public String verifyShareAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String subject = claims.getSubject();
            String purpose = claims.get("purpose", String.class);
            if (subject != null && subject.startsWith("share:") && "share_access".equals(purpose)) {
                return subject.substring(6); // 去掉 "share:" 前缀
            }
            return null;
        } catch (JwtException e) {
            log.warn("分享访问令牌验证失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证身份认证令牌
     * @param token 令牌
     * @return user_id，如果无效返回 null
     */
    public String verifyAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException e) {
            log.warn("身份认证令牌验证失败: {}", e.getMessage());
            return null;
        }
    }
}
