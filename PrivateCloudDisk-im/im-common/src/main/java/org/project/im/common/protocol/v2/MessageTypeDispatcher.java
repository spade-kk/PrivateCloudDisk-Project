package org.project.im.common.protocol.v2;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import org.project.im.common.security.IMCryptoCodec;
import org.project.im.common.security.IMSessionKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * IM 消息负载分发器 — 双层解密核心
 * <p>
 * 企业级消息分发架构：
 * <pre>
 * 二进制帧
 *   │
 *   ▼
 * [Layer 1] AES-256-GCM 解密 (Session Key)
 *   │
 *   ▼
 * IMEnvelope (message_type = TEXT, encrypted_payload = [...])
 *   │
 *   ▼
 * [MessageTypeDispatcher.dispatch()]
 *   │
 *   ├── TEXT    → TextPayloadCodec  → TextPayload
 *   ├── IMAGE   → ImagePayloadCodec → ImagePayload
 *   ├── VOICE   → VoicePayloadCodec → VoicePayload
 *   ├── VIDEO   → VideoPayloadCodec → VideoPayload
 *   ├── FILE    → FilePayloadCodec  → FilePayload
 *   ├── STICKER → StickerPayloadCodec → StickerPayload
 *   ├── LOCATION→ LocationPayloadCodec → LocationPayload
 *   ├── SYSTEM  → SystemPayloadCodec → SystemPayload
 *   ├── CALL    → CallPayloadCodec → CallPayload
 *   ├── ACK     → AckPayloadCodec → AckPayload
 *   └── ...     → CustomPayloadCodec → CustomPayload
 * </pre>
 * <p>
 * 每个 Payload Codec 使用<b>派生密钥</b>（deriveKeyForType）进行 Layer 2 解密，
 * 确保不同消息类型的加密密钥不同，即使外层密钥泄露也不会影响内层安全。
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
public class MessageTypeDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MessageTypeDispatcher.class);

    /** 注册的 Payload Codec 映射 */
    private static final Map<IMProtocolV2.IMMessageType, PayloadCodec<?>> codecRegistry = new ConcurrentHashMap<>();

    static {
        // 注册所有 Payload Codec
        register(IMProtocolV2.IMMessageType.TEXT,
                new PayloadCodec<>("TEXT", IMProtocolV2.TextPayload.class,
                        wrapParser(IMProtocolV2.TextPayload::parseFrom)));
        register(IMProtocolV2.IMMessageType.IMAGE,
                new PayloadCodec<>("IMAGE", IMProtocolV2.ImagePayload.class,
                        wrapParser(IMProtocolV2.ImagePayload::parseFrom)));
        register(IMProtocolV2.IMMessageType.VOICE,
                new PayloadCodec<>("VOICE", IMProtocolV2.VoicePayload.class,
                        wrapParser(IMProtocolV2.VoicePayload::parseFrom)));
        register(IMProtocolV2.IMMessageType.VIDEO,
                new PayloadCodec<>("VIDEO", IMProtocolV2.VideoPayload.class,
                        wrapParser(IMProtocolV2.VideoPayload::parseFrom)));
        register(IMProtocolV2.IMMessageType.FILE,
                new PayloadCodec<>("FILE", IMProtocolV2.FilePayload.class,
                        wrapParser(IMProtocolV2.FilePayload::parseFrom)));
        register(IMProtocolV2.IMMessageType.STICKER,
                new PayloadCodec<>("STICKER", IMProtocolV2.StickerPayload.class,
                        wrapParser(IMProtocolV2.StickerPayload::parseFrom)));
        register(IMProtocolV2.IMMessageType.LOCATION,
                new PayloadCodec<>("LOCATION", IMProtocolV2.LocationPayload.class,
                        wrapParser(IMProtocolV2.LocationPayload::parseFrom)));
        register(IMProtocolV2.IMMessageType.REPLY,
                new PayloadCodec<>("REPLY", IMProtocolV2.ReplyPayload.class,
                        wrapParser(IMProtocolV2.ReplyPayload::parseFrom)));
        register(IMProtocolV2.IMMessageType.VIDEO_CALL,
                new PayloadCodec<>("VIDEO_CALL", IMProtocolV2.CallPayload.class,
                        wrapParser(IMProtocolV2.CallPayload::parseFrom)));
        register(IMProtocolV2.IMMessageType.VOICE_CALL,
                new PayloadCodec<>("VOICE_CALL", IMProtocolV2.CallPayload.class,
                        wrapParser(IMProtocolV2.CallPayload::parseFrom)));
        register(IMProtocolV2.IMMessageType.SYSTEM_NOTICE,
                new PayloadCodec<>("SYSTEM_NOTICE", IMProtocolV2.SystemPayload.class,
                        wrapParser(IMProtocolV2.SystemPayload::parseFrom)));
        register(IMProtocolV2.IMMessageType.READ_RECEIPT,
                new PayloadCodec<>("READ_RECEIPT", IMProtocolV2.ReadReceiptPayload.class,
                        wrapParser(IMProtocolV2.ReadReceiptPayload::parseFrom)));
        register(IMProtocolV2.IMMessageType.MSG_TYPING,
                new PayloadCodec<>("TYPING", IMProtocolV2.TypingPayload.class,
                        wrapParser(IMProtocolV2.TypingPayload::parseFrom)));
        register(IMProtocolV2.IMMessageType.ACK,
                new PayloadCodec<>("ACK", IMProtocolV2.AckPayload.class,
                        wrapParser(IMProtocolV2.AckPayload::parseFrom)));
        register(IMProtocolV2.IMMessageType.CUSTOM,
                new PayloadCodec<>("CUSTOM", IMProtocolV2.CustomPayload.class,
                        wrapParser(IMProtocolV2.CustomPayload::parseFrom)));
    }

    /**
     * 双层解密并分发消息
     * <p>
     * 完整流程：
     * 1. Layer 1 解密 → IMEnvelope
     * 2. 根据 message_type 查找对应的 PayloadCodec
     * 3. 使用派生密钥（deriveKeyForType）进行 Layer 2 解密
     * 4. 反序列化类型特定的 Payload protobuf
     *
     * @param envelope    Layer 1 解密后的 IMEnvelope
     * @param sessionKeys 会话密钥
     * @return 解密后的 DispatchedMessage（包含 envelope 和类型特定的 payload）
     * @throws DispatchException 如果消息类型不支持或解密失败
     */
    public static DispatchedMessage dispatch(IMProtocolV2.IMEnvelope envelope, IMSessionKeys sessionKeys) {
        IMProtocolV2.IMMessageType messageType = envelope.getMessageType();

        // 查找 Codec
        @SuppressWarnings("unchecked")
        PayloadCodec<Message> codec = (PayloadCodec<Message>) codecRegistry.get(messageType);
        if (codec == null) {
            throw new DispatchException("Unsupported message type: " + messageType
                    + " (code=" + messageType.getNumber() + ")");
        }

        // 无加密负载的消息类型（HEARTBEAT, ACK, TYPING 等）
        if (envelope.getEncryptedPayload().isEmpty()) {
            log.debug("No encrypted payload for message type: {}", messageType);
            return new DispatchedMessage(envelope, null);
        }

        // Layer 2 解密
        try {
            SecretKey derivedKey = sessionKeys.deriveKeyForType(codec.typeName);
            byte[] payloadBytes = IMCryptoCodec.decryptLayer2(
                    envelope.getEncryptedPayload().toByteArray(), derivedKey);

            // 反序列化类型特定的 Payload
            Message payload = codec.parsePayload(payloadBytes);

            log.debug("Message dispatched: type={}, messageId={}, sender={}",
                    messageType, envelope.getMessageId(), envelope.getSenderId());

            return new DispatchedMessage(envelope, payload);
        } catch (SecurityException e) {
            throw new DispatchException("Layer 2 decryption failed for type: " + messageType
                    + " — " + e.getMessage(), e);
        } catch (InvalidProtocolBufferException e) {
            throw new DispatchException("Payload protobuf parse failed for type: " + messageType, e);
        }
    }

    /**
     * 反向操作：编码 Payload 为双层加密的 Envelope
     * <p>
     * 用于构建待发送消息。
     *
     * @param envelopeBuilder IMEnvelope Builder
     * @param payload        类型特定的 Payload 消息
     * @param sessionKeys     会话密钥
     * @return 填充了加密负载的 Envelope
     */
    public static IMProtocolV2.IMEnvelope encodePayload(
            IMProtocolV2.IMEnvelope.Builder envelopeBuilder,
            Message payload,
            IMSessionKeys sessionKeys) {

        IMProtocolV2.IMMessageType messageType = envelopeBuilder.getMessageType();

        if (payload == null) {
            return envelopeBuilder.build();
        }

        @SuppressWarnings("unchecked")
        PayloadCodec<Message> codec = (PayloadCodec<Message>) codecRegistry.get(messageType);
        if (codec == null) {
            throw new DispatchException("Unsupported message type for encoding: " + messageType);
        }

        // Layer 2 加密
        SecretKey derivedKey = sessionKeys.deriveKeyForType(codec.typeName);
        IMCryptoCodec.EncryptionResult encrypted = IMCryptoCodec.encryptLayer2(
                payload.toByteArray(), derivedKey);

        envelopeBuilder.setEncryptedPayload(
                com.google.protobuf.ByteString.copyFrom(encrypted.combined()));

        return envelopeBuilder.build();
    }

    /**
     * 注册消息类型处理器
     */
    public static <T extends Message> void register(
            IMProtocolV2.IMMessageType type,
            PayloadCodec<T> codec) {
        codecRegistry.put(type, codec);
    }

    /**
     * 检查消息类型是否已注册
     */
    public static boolean isRegistered(IMProtocolV2.IMMessageType type) {
        return codecRegistry.containsKey(type);
    }

    /**
     * 包装 Protobuf parseFrom 方法，将受检异常转为 RuntimeException
     */
    @SuppressWarnings("unchecked")
    private static <T extends Message> Function<byte[], T> wrapParser(
            ThrowingParser<T> parser) {
        return bytes -> {
            try {
                return (T) parser.parse(bytes);
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException("Protobuf parse failed", e);
            }
        };
    }

    /**
     * 抛出受检异常的解析器接口
     */
    @FunctionalInterface
    private interface ThrowingParser<T extends Message> {
        T parse(byte[] bytes) throws InvalidProtocolBufferException;
    }

    // ==================== 分发结果 ====================

    /**
     * 分发后的消息（包含 Envelope 和类型特定的 Payload）
     */
    public record DispatchedMessage(
            IMProtocolV2.IMEnvelope envelope,
            Message payload
    ) {
        /**
         * 获取消息类型
         */
        public IMProtocolV2.IMMessageType getType() {
            return envelope.getMessageType();
        }

        /**
         * 获取 Payload 并转换为指定类型
         */
        @SuppressWarnings("unchecked")
        public <T extends Message> T getPayload(Class<T> clazz) {
            if (payload == null) return null;
            return (T) payload;
        }

        /**
         * 便捷方法：获取 TextPayload
         */
        public IMProtocolV2.TextPayload getTextPayload() {
            return getPayload(IMProtocolV2.TextPayload.class);
        }

        /**
         * 便捷方法：获取 ImagePayload
         */
        public IMProtocolV2.ImagePayload getImagePayload() {
            return getPayload(IMProtocolV2.ImagePayload.class);
        }

        /**
         * 便捷方法：获取 VoicePayload
         */
        public IMProtocolV2.VoicePayload getVoicePayload() {
            return getPayload(IMProtocolV2.VoicePayload.class);
        }

        /**
         * 便捷方法：获取 VideoPayload
         */
        public IMProtocolV2.VideoPayload getVideoPayload() {
            return getPayload(IMProtocolV2.VideoPayload.class);
        }

        /**
         * 便捷方法：获取 FilePayload
         */
        public IMProtocolV2.FilePayload getFilePayload() {
            return getPayload(IMProtocolV2.FilePayload.class);
        }

        /**
         * 便捷方法：获取 StickerPayload
         */
        public IMProtocolV2.StickerPayload getStickerPayload() {
            return getPayload(IMProtocolV2.StickerPayload.class);
        }

        /**
         * 便捷方法：获取 CallPayload
         */
        public IMProtocolV2.CallPayload getCallPayload() {
            return getPayload(IMProtocolV2.CallPayload.class);
        }

        /**
         * 便捷方法：获取 SystemPayload
         */
        public IMProtocolV2.SystemPayload getSystemPayload() {
            return getPayload(IMProtocolV2.SystemPayload.class);
        }
    }

    // ==================== Payload Codec ====================

    /**
     * Payload 编解码器
     *
     * @param <T> Payload protobuf 类型
     */
    public static class PayloadCodec<T extends Message> {
        /** 类型名称（用于派生密钥） */
        public final String typeName;
        /** Payload 类 */
        public final Class<T> payloadClass;
        /** Protobuf 解析器 */
        private final Function<byte[], T> parser;

        public PayloadCodec(String typeName, Class<T> payloadClass, Function<byte[], T> parser) {
            this.typeName = typeName;
            this.payloadClass = payloadClass;
            this.parser = parser;
        }

        /**
         * 解析 Payload 字节
         */
        public T parsePayload(byte[] bytes) throws InvalidProtocolBufferException {
            if (parser != null) {
                return parser.apply(bytes);
            }
            // 回退：通过反射查找 parseFrom 方法
            try {
                java.lang.reflect.Method method = payloadClass.getMethod("parseFrom", byte[].class);
                @SuppressWarnings("unchecked")
                T result = (T) method.invoke(null, (Object) bytes);
                return result;
            } catch (Exception e) {
                throw new InvalidProtocolBufferException("Cannot parse payload: " + e.getMessage());
            }
        }
    }

    // ==================== 异常 ====================

    /**
     * 消息分发异常
     */
    public static class DispatchException extends RuntimeException {
        public DispatchException(String message) {
            super(message);
        }

        public DispatchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}