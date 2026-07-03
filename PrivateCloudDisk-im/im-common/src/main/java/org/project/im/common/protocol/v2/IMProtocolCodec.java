package org.project.im.common.protocol.v2;

import org.project.im.common.security.IMAntiForgeryValidator;
import org.project.im.common.security.IMCryptoCodec;
import org.project.im.common.security.IMSessionKeys;

import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * IM v2 协议编解码器
 * <p>
 * 企业级二进制协议编解码，负责从原始字节流中解析/构建消息。
 * <p>
 * <b>Wire Format（二进制帧格式）:</b>
 * <pre>
 * ┌────────────────┬────────────────┬──────────────────┬──────────────────┐
 * │  Total Length  │  Header Length │  Encrypted Header│  Encrypted Body  │
 * │   (4 bytes)    │   (4 bytes)    │   (variable)     │   (variable)     │
 * ├────────────────┴────────────────┴──────────────────┴──────────────────┤
 * │                          HMAC Signature (32 bytes)                     │
 * └───────────────────────────────────────────────────────────────────────┘
 * </pre>
 * <p>
 * <b>处理流程:</b>
 * <pre>
 * 编码: IMEnvelope → serialize → AES-GCM encrypt → prepend lengths → append HMAC → binary frame
 * 解码: binary frame → verify HMAC → extract lengths → AES-GCM decrypt → deserialize → IMEnvelope
 * </pre>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
public final class IMProtocolCodec {

    // ---- 帧格式常量 ----
    /** 总长度字段偏移 */
    public static final int OFFSET_TOTAL_LENGTH = 0;
    /** 总长度字段大小 */
    public static final int SIZE_TOTAL_LENGTH = 4;
    /** 头长度字段偏移 */
    public static final int OFFSET_HEADER_LENGTH = OFFSET_TOTAL_LENGTH + SIZE_TOTAL_LENGTH;
    /** 头长度字段大小 */
    public static final int SIZE_HEADER_LENGTH = 4;
    /** 帧头总大小 */
    public static final int FRAME_HEADER_SIZE = SIZE_TOTAL_LENGTH + SIZE_HEADER_LENGTH;
    /** HMAC 签名大小 */
    public static final int HMAC_SIZE = 32;
    /** 最小帧大小 */
    public static final int MIN_FRAME_SIZE = FRAME_HEADER_SIZE + HMAC_SIZE + 1;
    /** 最大帧大小 (1MB) */
    public static final int MAX_FRAME_SIZE = IMAntiForgeryValidator.MAX_MESSAGE_BODY_SIZE + FRAME_HEADER_SIZE + HMAC_SIZE;

    private IMProtocolCodec() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 编码（发送方向） ====================

    /**
     * 编码消息为二进制帧
     * <p>
     * 流程：
     * 1. 序列化 IMEnvelope 为 protobuf 字节
     * 2. 使用 Session Key 进行 AES-256-GCM 加密
     * 3. 构建帧头（totalLength + headerLength）
     * 4. 计算 HMAC 签名
     * 5. 组装完整帧
     *
     * @param envelope   IMEnvelope protobuf 消息
     * @param sessionKeys 会话密钥
     * @return 完整的二进制帧
     */
    public static byte[] encode(IMProtocolV2.IMEnvelope envelope, IMSessionKeys sessionKeys) {
        try {
            // 1. 序列化 Envelope
            byte[] envelopeBytes = envelope.toByteArray();

            // 2. Layer 1 加密
            IMCryptoCodec.EncryptionResult encrypted = IMCryptoCodec.encryptLayer1(
                    envelopeBytes, sessionKeys.getSessionKey());

            // 3. 构建帧
            int totalLength = FRAME_HEADER_SIZE + encrypted.combined().length + HMAC_SIZE;
            ByteBuffer buffer = ByteBuffer.allocate(totalLength);
            buffer.order(ByteOrder.BIG_ENDIAN);

            // 总长度
            buffer.putInt(totalLength);
            // 加密头长度（加密后的 envelope 长度）
            buffer.putInt(encrypted.combined().length);
            // 加密后的 envelope
            buffer.put(encrypted.combined());

            // 4. HMAC 签名（对帧头+加密数据进行签名）
            byte[] frameData = Arrays.copyOfRange(buffer.array(), 0, buffer.position());
            SecretKey hmacKey = IMAntiForgeryValidator.deriveHmacKey(sessionKeys.getSessionKey());
            byte[] hmac = IMAntiForgeryValidator.sign(frameData, hmacKey);
            buffer.put(hmac);

            return buffer.array();
        } catch (Exception e) {
            throw new ProtocolCodecException("Encode failed", e);
        }
    }

    // ==================== 解码（接收方向） ====================

    /**
     * 从二进制帧解码为 IMEnvelope
     * <p>
     * 流程：
     * 1. 验证帧格式
     * 2. 验证 HMAC 签名
     * 3. 提取加密数据
     * 4. 使用 Session Key 进行 AES-256-GCM 解密
     * 5. 反序列化 IMEnvelope
     *
     * @param frame       二进制帧
     * @param sessionKeys 会话密钥
     * @return 解码后的 IMEnvelope
     * @throws ProtocolCodecException 如果格式错误、签名无效或解密失败
     */
    public static IMProtocolV2.IMEnvelope decode(byte[] frame, IMSessionKeys sessionKeys) {
        // 1. 验证帧大小
        if (frame == null || frame.length < MIN_FRAME_SIZE) {
            throw new ProtocolCodecException("Frame too small: " + (frame == null ? 0 : frame.length));
        }
        if (frame.length > MAX_FRAME_SIZE) {
            throw new ProtocolCodecException("Frame too large: " + frame.length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(frame);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // 2. 读取帧头
        int totalLength = buffer.getInt();
        int headerLength = buffer.getInt();

        // 验证长度一致性
        if (totalLength != frame.length) {
            throw new ProtocolCodecException(
                    "Total length mismatch: declared=" + totalLength + ", actual=" + frame.length);
        }
        if (headerLength <= 0 || headerLength > totalLength - FRAME_HEADER_SIZE - HMAC_SIZE) {
            throw new ProtocolCodecException("Invalid header length: " + headerLength);
        }

        // 3. 提取帧数据部分（不含 HMAC）
        int dataEnd = totalLength - HMAC_SIZE;
        byte[] frameData = Arrays.copyOfRange(frame, 0, dataEnd);

        // 4. 验证 HMAC
        byte[] hmac = Arrays.copyOfRange(frame, dataEnd, totalLength);
        SecretKey hmacKey = IMAntiForgeryValidator.deriveHmacKey(sessionKeys.getSessionKey());
        if (!IMAntiForgeryValidator.verify(frameData, hmac, hmacKey)) {
            throw new ProtocolCodecException("HMAC verification failed — message tampered or wrong key");
        }

        // 5. 提取加密数据
        byte[] encryptedData = Arrays.copyOfRange(frame, FRAME_HEADER_SIZE, dataEnd);

        // 6. Layer 1 解密
        byte[] envelopeBytes;
        try {
            envelopeBytes = IMCryptoCodec.decryptLayer1(encryptedData, sessionKeys.getSessionKey());
        } catch (SecurityException e) {
            throw new ProtocolCodecException("Decryption failed: " + e.getMessage(), e);
        }

        // 7. 反序列化 IMEnvelope
        try {
            return IMProtocolV2.IMEnvelope.parseFrom(envelopeBytes);
        } catch (Exception e) {
            throw new ProtocolCodecException("Protobuf deserialization failed", e);
        }
    }

    // ==================== 长度分隔帧解析（用于 TCP 流式传输） ====================

    /**
     * 从 ByteBuffer 中读取完整帧
     * <p>
     * 用于 TCP 流式传输场景，通过 totalLength 字段分隔帧。
     *
     * @param buffer 累积缓冲区（position 指向已写入位置）
     * @return 完整帧字节，如果数据不完整则返回 null
     */
    public static byte[] readFrame(ByteBuffer buffer) {
        buffer.flip();
        if (buffer.remaining() < SIZE_TOTAL_LENGTH) {
            buffer.compact();
            return null;
        }

        int totalLength = buffer.getInt(OFFSET_TOTAL_LENGTH);
        if (totalLength < MIN_FRAME_SIZE || totalLength > MAX_FRAME_SIZE) {
            throw new ProtocolCodecException("Invalid total length in frame: " + totalLength);
        }

        if (buffer.remaining() < totalLength) {
            buffer.compact();
            return null;
        }

        byte[] frame = new byte[totalLength];
        buffer.get(frame);
        buffer.compact();
        return frame;
    }

    // ==================== 异常类 ====================

    /**
     * 协议编解码异常
     */
    public static class ProtocolCodecException extends RuntimeException {
        public ProtocolCodecException(String message) {
            super(message);
        }

        public ProtocolCodecException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}