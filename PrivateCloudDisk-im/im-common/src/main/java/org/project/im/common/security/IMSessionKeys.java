package org.project.im.common.security;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Arrays;

/**
 * IM 会话密钥对
 * <p>
 * 每个 WebSocket 连接建立时通过 ECDH 密钥协商生成一对会话密钥：
 * <ul>
 *   <li>{@code sessionKey} — 外层加密密钥（AES-256-GCM），用于加密/解密 IMEnvelope</li>
 *   <li>{@code derivedKey} — 派生密钥，用于 Layer 2 负载加密（不同消息类型使用不同派生密钥）</li>
 * </ul>
 * <p>
 * 密钥派生规则：
 * <pre>
 *   derivedKey = HKDF-SHA256(
 *     ikm = sessionKey,
 *     salt = "pcd-im-v2-derived",
 *     info = messageType.name()
 *   )
 * </pre>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
public class IMSessionKeys {

    // ---- 常量 ----
    /** ECDH 曲线: secp256r1 (NIST P-256) */
    public static final String EC_CURVE = "secp256r1";
    /** AES 密钥长度: 256 bits */
    private static final int AES_KEY_SIZE = 256;
    /** 外层密钥轮换间隔（秒） */
    public static final long KEY_ROTATION_INTERVAL_SEC = 3600;

    // ---- 实例字段 ----
    /** 会话密钥 ID（全局唯一递增） */
    private final int keyId;
    /** 外层加密密钥（AES-256） */
    private final SecretKey sessionKey;
    /** 密钥创建时间戳 */
    private final Instant createdAt;
    /** 密钥过期时间戳 */
    private final Instant expireAt;
    /** 用户 ID */
    private final String userId;
    /** 连接 ID */
    private final String connectionId;
    /** ECDH 共享密钥（仅用于调试，生产环境不存储） */
    private final byte[] sharedSecret;

    public IMSessionKeys(int keyId, SecretKey sessionKey, Instant createdAt,
                         Instant expireAt, String userId, String connectionId,
                         byte[] sharedSecret) {
        this.keyId = keyId;
        this.sessionKey = sessionKey;
        this.createdAt = createdAt;
        this.expireAt = expireAt;
        this.userId = userId;
        this.connectionId = connectionId;
        this.sharedSecret = sharedSecret;
    }

    /**
     * 执行 ECDH 密钥协商，生成会话密钥
     *
     * @param keyId          密钥 ID
     * @param serverKeyPair  服务端 ECDH 密钥对
     * @param clientPublicKey 客户端公钥
     * @param userId         用户 ID
     * @param connectionId   连接 ID
     * @return 协商完成的会话密钥
     */
    public static IMSessionKeys negotiate(
            int keyId,
            KeyPair serverKeyPair,
            ECPublicKey clientPublicKey,
            String userId,
            String connectionId) throws GeneralSecurityException {

        // 1. ECDH 共享密钥
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
        keyAgreement.init(serverKeyPair.getPrivate());
        keyAgreement.doPhase(clientPublicKey, true);
        byte[] sharedSecret = keyAgreement.generateSecret();

        // 2. HKDF-SHA256 派生 AES-256 密钥
        byte[] sessionKeyBytes = hkdfExpand(sharedSecret, "pcd-im-v2-session-key", AES_KEY_SIZE / 8);
        SecretKey sessionKey = new SecretKeySpec(sessionKeyBytes, "AES");

        Instant now = Instant.now();
        return new IMSessionKeys(
                keyId, sessionKey, now,
                now.plusSeconds(KEY_ROTATION_INTERVAL_SEC),
                userId, connectionId, sharedSecret);
    }

    /**
     * 为指定消息类型派生 Layer 2 加密密钥
     *
     * @param messageType 消息类型名称（如 "TEXT", "IMAGE"）
     * @return 派生密钥
     */
    public SecretKey deriveKeyForType(String messageType) {
        byte[] derivedBytes = hkdfExpand(
                sessionKey.getEncoded(),
                "pcd-im-v2-derived:" + messageType,
                AES_KEY_SIZE / 8);
        return new SecretKeySpec(derivedBytes, "AES");
    }

    /**
     * 生成服务端 ECDH 密钥对
     */
    public static KeyPair generateECKeyPair() throws GeneralSecurityException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec(EC_CURVE));
        return keyPairGenerator.generateKeyPair();
    }

    // ---- HKDF Expand（简化实现） ----

    /**
     * HKDF-Expand: 从输入密钥材料派生指定长度的密钥
     * <p>
     * HMAC-SHA256 作为伪随机函数
     */
    private static byte[] hkdfExpand(byte[] ikm, String info, int length) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            // PRK = HMAC-SHA256(salt="", ikm)
            hmac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
            byte[] prk = hmac.doFinal(ikm);

            byte[] result = new byte[length];
            byte[] t = new byte[0];
            byte[] infoBytes = info.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int offset = 0;

            for (int i = 1; offset < length; i++) {
                hmac.init(new SecretKeySpec(prk, "HmacSHA256"));
                hmac.update(t);
                hmac.update(infoBytes);
                hmac.update((byte) i);
                t = hmac.doFinal();
                int copyLen = Math.min(t.length, length - offset);
                System.arraycopy(t, 0, result, offset, copyLen);
                offset += copyLen;
            }
            return result;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("HKDF-Expand failed", e);
        }
    }

    // ---- Getters ----

    public int getKeyId() { return keyId; }
    public SecretKey getSessionKey() { return sessionKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpireAt() { return expireAt; }
    public String getUserId() { return userId; }
    public String getConnectionId() { return connectionId; }

    /** 密钥是否已过期 */
    public boolean isExpired() {
        return Instant.now().isAfter(expireAt);
    }

    /**
     * 安全销毁密钥材料
     */
    public void destroy() {
        Arrays.fill(sharedSecret, (byte) 0);
        Arrays.fill(sessionKey.getEncoded(), (byte) 0);
    }
}