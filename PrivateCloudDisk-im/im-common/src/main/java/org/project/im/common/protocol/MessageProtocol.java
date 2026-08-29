package org.project.im.common.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 消息协议（WebSocket 通信协议）
 * <p>
 * 客户端与服务端之间 WebSocket 通信的顶层协议结构。
 * 所有消息都通过此结构进行序列化/反序列化传输。
 * <p>
 * 协议格式（JSON）：
 * <pre>
 * {
 *   "version": 1,
 *   "command": 201,
 *   "seq": 123456,
 *   "timestamp": 1700000000000,
 *   "senderId": "user_001",
 *   "receiverId": "user_002",
 *   "payload": { ... },
 *   "extra": { ... }
 * }
 * </pre>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageProtocol implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 协议版本 */
    private Integer version;

    /** 命令类型（见 {@link org.project.im.common.enums.CommandType}） */
    private Integer command;

    /** 客户端消息序列号（用于请求-响应匹配） */
    private Long seq;

    /** 消息时间戳（毫秒） */
    private Long timestamp;

    /** 发送者 ID */
    private String senderId;

    /** 接收者 ID（单聊为用户 ID，群聊为群组 ID） */
    private String receiverId;

    /** 消息体（具体业务数据，JSON 对象） */
    private Object payload;

    /** 扩展字段（用于携带额外元数据） */
    private Map<String, Object> extra;

    // ==================== 便捷工厂方法 ====================

    /**
     * 创建心跳协议
     */
    public static MessageProtocol heartbeat(String userId) {
        return MessageProtocol.builder()
                .version(1)
                .command(103)
                .senderId(userId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建消息发送协议
     */
    public static MessageProtocol ofMessage(String senderId, String receiverId, Object payload) {
        return MessageProtocol.builder()
                .version(1)
                .command(201)
                .senderId(senderId)
                .receiverId(receiverId)
                .payload(payload)
                .timestamp(System.currentTimeMillis())
                .seq(System.nanoTime())
                .build();
    }

    /**
     * 创建系统通知协议
     */
    public static MessageProtocol ofSystemNotify(String receiverId, Object payload) {
        return MessageProtocol.builder()
                .version(1)
                .command(901)
                .senderId("SYSTEM")
                .receiverId(receiverId)
                .payload(payload)
                .timestamp(System.currentTimeMillis())
                .build();
    }

}
