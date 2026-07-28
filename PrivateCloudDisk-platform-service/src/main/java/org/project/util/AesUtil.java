package org.project.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具 — 用于分享提取码的可逆加密 及 虚拟资源ID的生成
 *
 * <p>设计背景：
 * <ul>
 *   <li>分享提取码不同于登录密码，用户需要能够在管理端查看和修改</li>
 *   <li>因此不能使用 BCrypt（单向哈希），必须使用可逆加密</li>
 *   <li>AES-256-GCM 提供认证加密，同时保证机密性和完整性</li>
 * </ul>
 *
 * <p>虚拟资源ID：
 * <ul>
 *   <li>子节点浏览时需生成虚拟 share_resource_id 替代内部 file_id/node_id</li>
 *   <li>使用 Base64URL 编码（RFC 4648 §5），无 + / = 等 URL 不安全字符</li>
 *   <li>可直接作为 RESTful 路径参数，不会与路径分隔符冲突</li>
 * </ul>
 *
 * <p>密钥管理：通过 application.properties 中的 {@code share.aes.secret-key} 配置，
 * 必须为 256-bit（32 字节）Base64 编码的密钥。
 */
@Component
public class AesUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;   // 96 bits
    private static final int GCM_TAG_LENGTH = 128;  // 128 bits

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom;

    public AesUtil(@Value("${share.aes.secret-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("share.aes.secret-key must be 256-bit (32 bytes) Base64 encoded");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
    }

    /**
     * AES-256-GCM 加密（标准 Base64 输出）
     *
     * @param plaintext 明文
     * @return Base64 编码的密文（IV + 密文 + 认证标签）
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // IV + 密文 → Base64
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /**
     * AES-256-GCM 解密（标准 Base64 输入）
     *
     * @param encrypted Base64 编码的密文（IV + 密文 + 认证标签）
     * @return 明文
     * @throws RuntimeException 解密失败（密钥错误或数据被篡改）
     */
    public String decrypt(String encrypted) {
        try {
            byte[] data = Base64.getDecoder().decode(encrypted);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败 — 密钥错误或数据被篡改", e);
        }
    }

    // ==================== Base64URL 编解码（用于虚拟资源ID，URL 路径安全） ====================

    /**
     * AES-256-GCM 加密，输出 Base64URL 编码（RFC 4648 §5）
     * <p>Base64URL 与标准 Base64 的区别：
     * <ul>
     *   <li>'+' → '-'</li>
     *   <li>'/' → '_'</li>
     *   <li>去除末尾 '=' 填充</li>
     * </ul>
     * <p>结果可直接嵌入 URL 路径参数，不会与路径分隔符 '/' 冲突。
     *
     * @param plaintext 明文
     * @return Base64URL 编码的密文（无 + / = 字符）
     */
    public String encryptToBase64Url(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            // Base64 → Base64URL 转换
            String base64 = Base64.getEncoder().encodeToString(buffer.array());
            return base64
                    .replace('+', '-')   // RFC 4648 §5
                    .replace('/', '_')   // RFC 4648 §5
                    .replace("=", "");   // 去除填充
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /**
     * AES-256-GCM 解密，输入 Base64URL 编码（RFC 4648 §5）
     * <p>先将 Base64URL 还原为标准 Base64，再解密。
     *
     * @param base64UrlEncrypted Base64URL 编码的密文
     * @return 明文
     * @throws RuntimeException 解密失败（密钥错误或数据被篡改）
     */
    public String decryptFromBase64Url(String base64UrlEncrypted) {
        try {
            // Base64URL → 标准 Base64
            String base64 = base64UrlEncrypted
                    .replace('-', '+')
                    .replace('_', '/');

            // 补回 Base64 填充
            int mod = base64.length() % 4;
            if (mod != 0) {
                base64 += "=".repeat(4 - mod);
            }

            byte[] data = Base64.getDecoder().decode(base64);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败 — 密钥错误或数据被篡改", e);
        }
    }

    // ==================== 字节数组加密/解密（用于虚拟资源ID，压缩格式） ====================

    /**
     * AES-256-GCM 加密字节数组，输出 Base64URL 编码（RFC 4648 §5）
     * <p>用于虚拟资源ID的紧凑编码：将 UUID 转为 16 字节二进制 + 1 字节类型标记，
     * 加密后输出约 60 字符的 Base64URL 字符串，比字符串加密方式缩短约 27%。
     * <p>格式：Base64URL( IV(12B) + AES-GCM(type_flag + uuid_bytes) + 认证标签(16B) )
     *
     * @param plainBytes 明文字节数组（17 字节：1 字节类型标记 + 16 字节 UUID）
     * @return Base64URL 编码的密文（无 + / = 字符，约 60 字符）
     */
    public String encryptBytesToBase64Url(byte[] plainBytes) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            byte[] ciphertext = cipher.doFinal(plainBytes);

            // IV + 密文（含认证标签）
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            // Base64 → Base64URL 转换
            String base64 = Base64.getEncoder().encodeToString(buffer.array());
            return base64
                    .replace('+', '-')   // RFC 4648 §5
                    .replace('/', '_')   // RFC 4648 §5
                    .replace("=", "");   // 去除填充
        } catch (Exception e) {
            throw new RuntimeException("AES 字节加密失败", e);
        }
    }

    /**
     * AES-256-GCM 解密 Base64URL 编码的密文，返回原始字节数组
     * <p>用于解析虚拟资源ID：Base64URL 解码 → AES-GCM 解密 → 还原为原始字节数组。
     * <p>返回的字节数组格式：[1 字节类型标记][16 字节 UUID]
     *
     * @param base64UrlEncrypted Base64URL 编码的密文
     * @return 原始明文字节数组（17 字节）
     * @throws RuntimeException 解密失败（密钥错误或数据被篡改）
     */
    public byte[] decryptBytesFromBase64Url(String base64UrlEncrypted) {
        try {
            // Base64URL → 标准 Base64
            String base64 = base64UrlEncrypted
                    .replace('-', '+')
                    .replace('_', '/');

            // 补回 Base64 填充
            int mod = base64.length() % 4;
            if (mod != 0) {
                base64 += "=".repeat(4 - mod);
            }

            byte[] data = Base64.getDecoder().decode(base64);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("AES 字节解密失败 — 密钥错误或数据被篡改", e);
        }
    }
}