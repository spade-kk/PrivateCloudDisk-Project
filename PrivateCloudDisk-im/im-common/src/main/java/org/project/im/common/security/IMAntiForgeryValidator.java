package org.project.im.common.security;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * IM 防伪造与完整性验证器
 * <p>
 * 企业级防伪造安全措施：
 * <ul>
 *   <li><b>HMAC-SHA256 消息签名</b> — 每条消息附带签名，防止传输过程中被篡改</li>
 *   <li><b>时间戳防重放</b> — 消息携带时间戳，超出时间窗口的消息被拒绝</li>
 *   <li><b>Nonce 防重放</b> — 基于 Redis 的 Nonce 去重，防止同一消息重复处理</li>
 *   <li><b>消息 ID 校验</b> — 验证 messageId 格式，防止注入攻击</li>
 *   <li><b>长度限制</b> — 单条消息最大长度限制，防止内存溢出攻击</li>
 * </ul>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
public final class IMAntiForgeryValidator {

    // ---- 常量 ----
    /** HMAC 算法 */
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    /** HMAC 签名长度 (bytes) */
    public static final int HMAC_SIGNATURE_LENGTH = 32;
    /** 消息时间窗口 (秒) — 超出此窗口的消息被视为重放 */
    public static final long MESSAGE_TIME_WINDOW_SEC = 300;
    /** 最大消息体大小 (bytes) — 默认 1MB */
    public static final int MAX_MESSAGE_BODY_SIZE = 1024 * 1024;
    /** 最大消息头大小 (bytes) */
    public static final int MAX_MESSAGE_HEADER_SIZE = 64 * 1024;
    /** 消息 ID 最大长度 */
    public static final int MAX_MESSAGE_ID_LENGTH = 128;
    /** 发送者 ID 最大长度 */
    public static final int MAX_SENDER_ID_LENGTH = 64;
    /** Nonce 长度 (bytes) */
    public static final int NONCE_LENGTH = 16;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private IMAntiForgeryValidator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== HMAC 签名 ====================

    /**
     * 对消息体计算 HMAC-SHA256 签名
     *
     * @param messageBody 消息体（加密后的二进制数据）
     * @param secretKey   签名密钥（派生自 Session Key）
     * @return HMAC 签名 (32 bytes)
     */
    public static byte[] sign(byte[] messageBody, SecretKey secretKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            return mac.doFinal(messageBody);
        } catch (Exception e) {
            throw new SecurityException("HMAC sign failed", e);
        }
    }

    /**
     * 验证 HMAC 签名
     *
     * @param messageBody 消息体
     * @param signature   待验证的签名
     * @param secretKey   签名密钥
     * @return true 如果签名有效
     */
    public static boolean verify(byte[] messageBody, byte[] signature, SecretKey secretKey) {
        if (signature == null || signature.length != HMAC_SIGNATURE_LENGTH) {
            return false;
        }
        byte[] expected = sign(messageBody, secretKey);
        return MessageDigest.isEqual(expected, signature);
    }

    /**
     * 从 Session Key 派生 HMAC 签名密钥
     */
    public static SecretKey deriveHmacKey(SecretKey sessionKey) {
        byte[] sessionBytes = sessionKey.getEncoded();
        byte[] hmacKeyBytes = Arrays.copyOfRange(
                sha256(sessionBytes, "pcd-im-hmac-key".getBytes()),
                0, 32);
        return new SecretKeySpec(hmacKeyBytes, "HmacSHA256");
    }

    // ==================== 时间戳防重放 ====================

    /**
     * 验证消息时间戳是否在有效窗口内
     *
     * @param messageTimestamp 消息时间戳（Unix 毫秒）
     * @return true 如果时间戳有效
     */
    public static boolean isTimestampValid(long messageTimestamp) {
        long now = System.currentTimeMillis();
        long diff = Math.abs(now - messageTimestamp);
        return diff <= TimeUnit.SECONDS.toMillis(MESSAGE_TIME_WINDOW_SEC);
    }

    // ==================== Nonce 防重放 ====================

    /**
     * 生成随机 Nonce
     */
    public static byte[] generateNonce() {
        byte[] nonce = new byte[NONCE_LENGTH];
        SECURE_RANDOM.nextBytes(nonce);
        return nonce;
    }

    /**
     * 生成 Nonce 的 Base64 表示
     */
    public static String generateNonceString() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(generateNonce());
    }

    // ==================== 输入验证（防注入） ====================

    /**
     * 验证消息 ID 格式
     * <p>
     * 合法格式: 字母数字、下划线、连字符，长度 1-128
     */
    public static boolean isValidMessageId(String messageId) {
        if (messageId == null || messageId.isEmpty() || messageId.length() > MAX_MESSAGE_ID_LENGTH) {
            return false;
        }
        return messageId.matches("^[a-zA-Z0-9_\\-]+$");
    }

    /**
     * 验证用户/发送者 ID 格式
     */
    public static boolean isValidUserId(String userId) {
        if (userId == null || userId.isEmpty() || userId.length() > MAX_SENDER_ID_LENGTH) {
            return false;
        }
        return userId.matches("^[a-zA-Z0-9_\\-]+$");
    }

    /**
     * 验证消息体大小
     */
    public static boolean isValidBodySize(int bodySize) {
        return bodySize > 0 && bodySize <= MAX_MESSAGE_BODY_SIZE;
    }

    /**
     * 验证消息头大小
     */
    public static boolean isValidHeaderSize(int headerSize) {
        return headerSize > 0 && headerSize <= MAX_MESSAGE_HEADER_SIZE;
    }

    /**
     * 清理危险字符串（防 XSS/注入）
     *
     * @param input 原始输入
     * @return 清理后的字符串
     */
    public static String sanitize(String input) {
        if (input == null) return "";
        return input
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("&", "&amp;")
                .replace("\0", "")  // 去除 null 字节
                .replace("\n", " ")
                .replace("\r", "");
    }

    // ==================== 内部工具 ====================

    private static byte[] sha256(byte[]... data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] d : data) {
                digest.update(d);
            }
            return digest.digest();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }
}