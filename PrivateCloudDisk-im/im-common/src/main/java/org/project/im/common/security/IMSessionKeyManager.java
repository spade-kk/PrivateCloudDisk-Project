package org.project.im.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IM 会话密钥管理器
 * <p>
 * 管理所有 WebSocket 连接的会话密钥生命周期：
 * <ul>
 *   <li>密钥协商 — 客户端连接时执行 ECDH 密钥交换</li>
 *   <li>密钥存储 — 以 userId 或 connectionId 为索引缓存密钥</li>
 *   <li>密钥轮换 — 定时轮换密钥，防止长期使用同一密钥</li>
 *   <li>密钥销毁 — 连接断开时安全销毁密钥材料</li>
 * </ul>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
public class IMSessionKeyManager {

    private static final Logger log = LoggerFactory.getLogger(IMSessionKeyManager.class);

    /** 全局密钥 ID 计数器 */
    private static final AtomicInteger KEY_ID_COUNTER = new AtomicInteger(1);

    /** 用户 ID → 会话密钥 */
    private final Map<String, IMSessionKeys> userKeyMap = new ConcurrentHashMap<>();

    /** 连接 ID → 会话密钥 */
    private final Map<String, IMSessionKeys> connectionKeyMap = new ConcurrentHashMap<>();

    /** 服务端 ECDH 密钥对（可定期轮换） */
    private volatile KeyPair serverKeyPair;

    /** 服务端 RSA 密钥对（用于签名） */
    private volatile KeyPair serverRSAKeyPair;

    private IMSessionKeyManager() {
        try {
            this.serverKeyPair = IMSessionKeys.generateECKeyPair();
            this.serverRSAKeyPair = IMCryptoCodec.generateRSAKeyPair();
            log.info("Session key manager initialized");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize key pairs", e);
        }
    }

    public static IMSessionKeyManager createIMSessionKeyManager() {
        return new IMSessionKeyManager();
    }

    /**
     * 执行 ECDH 密钥协商
     *
     * @param userId           用户 ID
     * @param connectionId     连接 ID
     * @param clientPublicKeyBytes 客户端 ECDH 公钥（X.509 编码）
     * @return 协商后的会话密钥
     */
    public IMSessionKeys negotiate(String userId, String connectionId, byte[] clientPublicKeyBytes) {
        try {
            // 解码客户端公钥
            ECPublicKey clientPublicKey = (ECPublicKey) IMCryptoCodec.decodePublicKey(clientPublicKeyBytes);

            // 执行 ECDH 协商
            int keyId = KEY_ID_COUNTER.getAndIncrement();
            IMSessionKeys sessionKeys = IMSessionKeys.negotiate(
                    keyId, serverKeyPair, clientPublicKey, userId, connectionId);

            // 存储
            userKeyMap.put(userId, sessionKeys);
            connectionKeyMap.put(connectionId, sessionKeys);

            log.info("Session key negotiated: userId={}, keyId={}, connectionId={}",
                    userId, keyId, connectionId);

            return sessionKeys;
        } catch (Exception e) {
            log.error("Key negotiation failed: userId={}", userId, e);
            throw new SecurityException("Key negotiation failed", e);
        }
    }

    /**
     * 获取用户的会话密钥
     */
    public IMSessionKeys getByUser(String userId) {
        IMSessionKeys keys = userKeyMap.get(userId);
        if (keys != null && !keys.isExpired()) {
            return keys;
        }
        return null;
    }

    /**
     * 获取连接的会话密钥
     */
    public IMSessionKeys getByConnection(String connectionId) {
        return connectionKeyMap.get(connectionId);
    }

    /**
     * 移除并销毁会话密钥
     */
    public void remove(String connectionId) {
        IMSessionKeys keys = connectionKeyMap.remove(connectionId);
        if (keys != null) {
            userKeyMap.remove(keys.getUserId());
            keys.destroy();
            log.debug("Session key destroyed: userId={}, connectionId={}",
                    keys.getUserId(), connectionId);
        }
    }

    /**
     * 获取服务端 ECDH 公钥（X.509 编码）
     */
    public byte[] getServerPublicKey() {
        return serverKeyPair.getPublic().getEncoded();
    }

    /**
     * 获取服务端 RSA 公钥（用于客户端验证签名）
     */
    public byte[] getServerRSAPublicKey() {
        return serverRSAKeyPair.getPublic().getEncoded();
    }

    /**
     * 使用服务端 RSA 私钥签名
     */
    public byte[] sign(byte[] data) {
        return IMCryptoCodec.signWithRSA(data, serverRSAKeyPair.getPrivate());
    }

    /**
     * 定时轮换 ECDH 密钥对
     */
    public void rotateKeyPair() {
        try {
            KeyPair newKeyPair = IMSessionKeys.generateECKeyPair();
            this.serverKeyPair = newKeyPair;
            log.info("ECDH key pair rotated");
        } catch (Exception e) {
            log.error("Key rotation failed", e);
        }
    }

    /**
     * 获取活跃会话数
     */
    public int getActiveSessionCount() {
        return connectionKeyMap.size();
    }

    /**
     * 检查用户是否已建立密钥
     */
    public boolean hasKey(String userId) {
        IMSessionKeys keys = userKeyMap.get(userId);
        return keys != null && !keys.isExpired();
    }
}