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
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {
    @Value("classpath:keys/private_key.pem")
    private Resource privateKeyResource;
    private PrivateKey secretKey;
    private final long expirationMs = 86400000; // 24小时

    @PostConstruct
    public void init() {
        try {
            //从本地文件中加载私匙 公匙
            secretKey = loadPrivateKey(privateKeyResource);
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
                .signWith(secretKey, SignatureAlgorithm.RS256)
                .compact();
    }
}
