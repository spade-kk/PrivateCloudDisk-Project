package org.project.im.common.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * IM 加密/解密编解码器
 * <p>
 * 企业级双层加密架构：
 * <pre>
 * Layer 1 (外层): AES-256-GCM
 *   - 密钥: Session Key (ECDH 协商)
 *   - 加密对象: IMEnvelope 整个 protobuf 消息
 *   - IV: 随机生成 12 bytes，附带在消息头中
 *   - Auth Tag: 16 bytes，GCM 自动生成
 *
 * Layer 2 (内层): AES-256-GCM
 *   - 密钥: Derived Key = HKDF(sessionKey, messageType)
 *   - 加密对象: 类型特定的 Payload (TextPayload, ImagePayload...)
 *   - IV: 随机生成 12 bytes
 *   - Auth Tag: 16 bytes
 * </pre>
 * <p>
 * 安全特性：
 * <ul>
 *   <li>AES-256-GCM 提供认证加密（AEAD），同时保证机密性和完整性</li>
 *   <li>每次加密使用随机 IV，防止模式重用攻击</li>
 *   <li>GCM 认证标签防止密文篡改</li>
 *   <li>双层加密隔离：外层泄露不影响内层 payload 安全</li>
 * </ul>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
public final class IMCryptoCodec {

    // ---- 常量 ----
    /** AES-GCM 加密算法 */
    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    /** GCM IV 长度 (推荐 12 bytes) */
    public static final int GCM_IV_LENGTH = 12;
    /** GCM 认证标签长度 (128 bits) */
    public static final int GCM_TAG_LENGTH = 128;
    /** AES 密钥长度 */
    private static final int AES_KEY_SIZE = 256;

    /** RSA 算法 */
    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    /** RSA 密钥长度 */
    private static final int RSA_KEY_SIZE = 2048;

    /** 安全随机数生成器 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ---- 防止实例化 ----
    private IMCryptoCodec() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== Layer 1: 外层加密（Session Key） ====================

    /**
     * 使用 Session Key 进行 AES-256-GCM 加密
     *
     * @param plaintext  明文数据（IMEnvelope 序列化字节）
     * @param sessionKey 会话密钥
     * @return 加密结果（包含 IV + 密文 + Auth Tag 的组合字节）
     */
    public static EncryptionResult encryptLayer1(byte[] plaintext, SecretKey sessionKey) {
        return encryptAesGcm(plaintext, sessionKey);
    }

    /**
     * 使用 Session Key 进行 AES-256-GCM 解密
     *
     * @param ciphertextWithIV 加密结果（IV + 密文）
     * @param sessionKey       会话密钥
     * @return 解密后的明文数据
     * @throws SecurityException 如果认证标签验证失败（密文被篡改）
     */
    public static byte[] decryptLayer1(byte[] ciphertextWithIV, SecretKey sessionKey) {
        // IV 在前 12 bytes，密文在后
        byte[] iv = Arrays.copyOfRange(ciphertextWithIV, 0, GCM_IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(ciphertextWithIV, GCM_IV_LENGTH, ciphertextWithIV.length);
        return decryptAesGcm(ciphertext, sessionKey, iv);
    }

    // ==================== Layer 2: 内层加密（Derived Key） ====================

    /**
     * 使用派生密钥加密类型特定 Payload
     *
     * @param payloadBytes  Payload protobuf 序列化字节
     * @param derivedKey    派生密钥（基于消息类型）
     * @return 加密结果
     */
    public static EncryptionResult encryptLayer2(byte[] payloadBytes, SecretKey derivedKey) {
        return encryptAesGcm(payloadBytes, derivedKey);
    }

    /**
     * 使用派生密钥解密类型特定 Payload
     *
     * @param ciphertextWithIV 加密结果
     * @param derivedKey       派生密钥
     * @return 解密后的 Payload 字节
     */
    public static byte[] decryptLayer2(byte[] ciphertextWithIV, SecretKey derivedKey) {
        int minSize = GCM_IV_LENGTH + (GCM_TAG_LENGTH / 8); // 12 + 16 = 28 字节
        if (ciphertextWithIV == null || ciphertextWithIV.length < minSize) {
            throw new SecurityException(
                    "Layer 2 解密失败: 加密负载太短 (" + (ciphertextWithIV == null ? 0 : ciphertextWithIV.length)
                    + " 字节)，至少需要 " + minSize + " 字节"
                    + " (IV=" + GCM_IV_LENGTH + " + Tag=" + (GCM_TAG_LENGTH / 8) + ")。"
                    + " 请检查客户端是否进行了 Layer 2 加密");
        }
        byte[] iv = Arrays.copyOfRange(ciphertextWithIV, 0, GCM_IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(ciphertextWithIV, GCM_IV_LENGTH, ciphertextWithIV.length);
        return decryptAesGcm(ciphertext, derivedKey, iv);
    }

    // ==================== RSA 密钥交换辅助 ====================

    /**
     * 使用 RSA 公钥加密 AES 会话密钥（用于键交换）
     *
     * @param sessionKey 会话密钥
     * @param publicKey  RSA 公钥
     * @return 加密的会话密钥
     */
    public static byte[] encryptSessionKeyWithRSA(SecretKey sessionKey, PublicKey publicKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(sessionKey.getEncoded());
        } catch (GeneralSecurityException e) {
            throw new SecurityException("RSA encrypt session key failed", e);
        }
    }

    /**
     * 使用 RSA 私钥解密 AES 会话密钥
     */
    public static SecretKey decryptSessionKeyWithRSA(byte[] encryptedKey, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] keyBytes = cipher.doFinal(encryptedKey);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new SecurityException("RSA decrypt session key failed", e);
        }
    }

    /**
     * 生成 RSA 密钥对
     */
    public static KeyPair generateRSAKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_SIZE, SECURE_RANDOM);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new SecurityException("RSA key generation failed", e);
        }
    }

    /**
     * 从 X.509 编码字节恢复公钥
     */
    public static PublicKey decodePublicKey(byte[] encodedKey) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePublic(new X509EncodedKeySpec(encodedKey));
        } catch (GeneralSecurityException e) {
            throw new SecurityException("EC public key decode failed", e);
        }
    }

    // ==================== 签名验证 ====================

    /**
     * 使用 RSA 私钥签名数据
     */
    public static byte[] signWithRSA(byte[] data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey, SECURE_RANDOM);
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new SecurityException("RSA sign failed", e);
        }
    }

    /**
     * 使用 RSA 公钥验证签名
     *
     * @return true 如果签名有效
     */
    public static boolean verifyRSASignature(byte[] data, byte[] signatureBytes, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(signatureBytes);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    // ==================== 内部 AES-GCM 实现 ====================

    /**
     * AES-256-GCM 加密
     */
    private static EncryptionResult encryptAesGcm(byte[] plaintext, SecretKey key) {
        try {
            // 生成随机 IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] ciphertext = cipher.doFinal(plaintext);

            // 返回: IV + 密文(含 Auth Tag)
            byte[] result = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, GCM_IV_LENGTH, ciphertext.length);

            return new EncryptionResult(iv, ciphertext, result);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("AES-GCM encrypt failed", e);
        }
    }

    /**
     * AES-256-GCM 解密
     *
     * @throws SecurityException 如果认证标签验证失败（篡改检测）
     */
    private static byte[] decryptAesGcm(byte[] ciphertext, SecretKey key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("AES-GCM decrypt failed: authentication tag mismatch", e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成安全的随机 IV
     */
    public static byte[] generateIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    /**
     * 生成安全的随机字节
     */
    public static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    // ==================== 加密结果容器 ====================

    /**
     * 加密结果
     */
    public record EncryptionResult(byte[] iv, byte[] ciphertext, byte[] combined) {}
}