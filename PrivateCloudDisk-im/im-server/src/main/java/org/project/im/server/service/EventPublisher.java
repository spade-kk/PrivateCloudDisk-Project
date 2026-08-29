package org.project.im.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.constant.ImConstants;
import org.project.im.common.mq.IMMQProto;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// ============================================================
// 事件发布器 v2.0 — 统一 MQ 事件发布
// ============================================================
// 职责：
//   1. 将 IM Server 产生的所有事件以 Protobuf 序列化发布到 MQ 事件交换机
//   2. 事件类型：用户上线/离线、消息送达/失败、消息已读
//   3. 所有事件附带 MQMessageHeader（traceId、timestamp、sourceNode）
//
// 与 Go IM Router 的协议契约：
//   - MQ 消息体为 Protobuf 二进制字节（非 JSON）
//   - Go Router 通过 proto.Unmarshal 解析
//   - content_type = application/x-protobuf
//   - 事件交换机为 topic 类型，发布时需指定路由键
// ============================================================

/**
 * MQ 事件发布器
 * <p>
 * IM Server 通过此组件将运行时事件发布到 RabbitMQ 事件交换机，
 * 由 IM Business 和 IM Router 各自消费。
 * </p>
 *
 * <h3>事件流向</h3>
 * <pre>
 * IM Server
 *   ├── UserOnlineEvent    → im.event.exchange (topic, rk: im.user.online.event)       → IM Platform
 *   ├── UserOfflineEvent   → im.event.exchange (topic, rk: im.user.offline.event)      → IM Platform
 *   ├── MessageDeliveredEvent → im.event.exchange (topic, rk: im.message.delivered.event) → IM Platform + IM Router
 *   ├── MessageFailedEvent    → im.event.exchange (topic, rk: im.message.failed.event)    → IM Platform + IM Router
 *   └── MessageReadEvent      → im.event.exchange (topic, rk: im.message.read.event)      → IM Platform
 * </pre>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /** 当前 IM Server 节点 ID（由 SessionManager 管理，此处运行时获取） */
    private final org.project.im.server.netty.SessionManager sessionManager;

    // ============================================================
    // 用户上下线事件
    // ============================================================

    /**
     * 发布用户上线事件
     * <p>
     * 用户完成 WebSocket 握手与密钥协商后触发。
     * IM Business 消费后执行离线消息补偿流程。
     * </p>
     *
     * @param userId         用户 ID
     * @param connectionId   连接 ID（Channel 唯一标识）
     * @param deviceType     设备类型（0=Desktop, 1=Mobile, 2=Web）
     * @param platform       客户端平台标识
     * @param clientVersion  客户端版本
     */
    public void publishUserOnlineEvent(
            String userId, String connectionId,
            int deviceType, String platform, String clientVersion) {
        try {
            IMMQProto.UserOnlineEvent event = IMMQProto.UserOnlineEvent.newBuilder()
                    .setHeader(buildHeader("im.user.online.event", userId))
                    .setUserId(userId)
                    .setServerNodeId(sessionManager.getNodeId())
                    .setDeviceType(deviceType)
                    .setPlatform(nullToEmpty(platform))
                    .setClientVersion(nullToEmpty(clientVersion))
                    .setOnlineAt(System.currentTimeMillis())
                    .setConnectionId(connectionId)
                    .build();

            publishEvent(ImConstants.MQ_EXCHANGE_EVENT, event.toByteArray(),
                    "im.user.online.event", userId);
            log.info("用户上线事件已发布: userId={}, nodeId={}", userId, sessionManager.getNodeId());
        } catch (Exception e) {
            log.error("发布用户上线事件失败: userId={}", userId, e);
        }
    }

    /**
     * 发布用户离线事件
     * <p>
     * 用户 WebSocket 连接断开时触发。
     * 离线原因：1=正常断开, 2=心跳超时, 3=踢出, 4=异常断开
     * </p>
     *
     * @param userId         用户 ID
     * @param connectionId   连接 ID
     * @param offlineReason  离线原因码
     */
    public void publishUserOfflineEvent(
            String userId, String connectionId, int offlineReason) {
        try {
            IMMQProto.UserOfflineEvent event = IMMQProto.UserOfflineEvent.newBuilder()
                    .setHeader(buildHeader("im.user.offline.event", userId))
                    .setUserId(userId)
                    .setServerNodeId(sessionManager.getNodeId())
                    .setOfflineReason(offlineReason)
                    .setOfflineAt(System.currentTimeMillis())
                    .setConnectionId(connectionId)
                    .build();

            publishEvent(ImConstants.MQ_EXCHANGE_EVENT, event.toByteArray(),
                    "im.user.offline.event", userId);
            log.info("用户离线事件已发布: userId={}, reason={}", userId, offlineReason);
        } catch (Exception e) {
            log.error("发布用户离线事件失败: userId={}", userId, e);
        }
    }

    // ============================================================
    // 消息状态事件
    // ============================================================

    /**
     * 发布消息送达事件
     * <p>
     * 消息一经推送即视为送达时触发（不再等待客户端 ACK）。
     * IM Business 更新消息状态为 DELIVERED。
     * </p>
     *
     * @param messageId      消息 ID
     * @param receiverId     接收者用户 ID（确认送达的用户）
     * @param senderId       发送者用户 ID
     * @param conversationId 会话 ID
     * @param messageType    统一推送消息类型
     * @param originalMessageId 原始消息 ID（追溯最原始的聊天消息）
     * @param originalSenderId  原始发送者用户 ID
     */
    public void publishMessageDeliveredEvent(
            String messageId, String receiverId, String senderId, String conversationId,
            IMMQProto.MessageType messageType,
            String originalMessageId, String originalSenderId) {
        try {
            IMMQProto.MessageDeliveredEvent event = IMMQProto.MessageDeliveredEvent.newBuilder()
                    .setHeader(buildHeader("im.message.delivered.event", messageId))
                    .setMessageId(messageId)
                    .setReceiverId(receiverId)
                    .setSenderId(senderId)
                    .setConversationId(nullToEmpty(conversationId))
                    .setDeliveredAt(System.currentTimeMillis())
                    .setMessageType(messageType != null
                            ? messageType
                            : IMMQProto.MessageType.CHAT_MESSAGE)
                    .setOriginalMessageId(nullToEmpty(originalMessageId))
                    .setOriginalSenderId(nullToEmpty(originalSenderId))
                    .build();

            publishEvent(ImConstants.MQ_EXCHANGE_EVENT, event.toByteArray(),
                    "im.message.delivered.event", messageId);
            log.debug("消息送达事件已发布: messageId={}, receiverId={}", messageId, receiverId);
        } catch (Exception e) {
            log.error("发布消息送达事件失败: messageId={}", messageId, e);
        }
    }

    /**
     * 发布消息失败事件
     * <p>
     * 消息推送失败（未找到接收方连接或推送异常）时触发。
     * IM Business 更新消息状态为 FAILED。
     * </p>
     *
     * @param messageId      消息 ID
     * @param receiverId     接收者用户 ID
     * @param senderId       发送者用户 ID
     * @param conversationId 会话 ID
     * @param failCode       失败原因码：1=未找到接收方连接, 2=推送异常/其他
     * @param failReason     失败原因描述
     * @param messageType    统一推送消息类型
     * @param originalMessageId 原始消息 ID（追溯最原始的聊天消息）
     * @param originalSenderId  原始发送者用户 ID
     */
    public void publishMessageFailedEvent(
            String messageId, String receiverId, String senderId,
            String conversationId, int failCode, String failReason,
            IMMQProto.MessageType messageType,
            String originalMessageId, String originalSenderId) {
        try {
            IMMQProto.MessageFailedEvent event = IMMQProto.MessageFailedEvent.newBuilder()
                    .setHeader(buildHeader("im.message.failed.event", messageId))
                    .setMessageId(messageId)
                    .setReceiverId(receiverId)
                    .setSenderId(senderId)
                    .setConversationId(nullToEmpty(conversationId))
                    .setFailCode(failCode)
                    .setFailReason(nullToEmpty(failReason))
                    .setFailedAt(System.currentTimeMillis())
                    .setMessageType(messageType != null
                            ? messageType
                            : IMMQProto.MessageType.CHAT_MESSAGE)
                    .setOriginalMessageId(nullToEmpty(originalMessageId))
                    .setOriginalSenderId(nullToEmpty(originalSenderId))
                    .build();

            publishEvent(ImConstants.MQ_EXCHANGE_EVENT, event.toByteArray(),
                    "im.message.failed.event", messageId);
            log.warn("消息失败事件已发布: messageId={}, failCode={}, reason={}",
                    messageId, failCode, failReason);
        } catch (Exception e) {
            log.error("发布消息失败事件失败: messageId={}", messageId, e);
        }
    }

    // ============================================================
    // 消息已读事件
    // ============================================================

    /**
     * 发布消息已读事件
     * <p>
     * 客户端上报已读回执后触发。
     * IM Business 更新消息已读状态，清零会话未读计数。
     * </p>
     *
     * @param userId         执行已读操作的用户 ID
     * @param conversationId 会话 ID
     * @param messageIds     已读的消息 ID 列表
     */
    public void publishMessageReadEvent(
            String userId, String conversationId, List<String> messageIds) {
        try {
            IMMQProto.MessageReadEvent.Builder builder = IMMQProto.MessageReadEvent.newBuilder()
                    .setHeader(buildHeader("im.message.read.event", userId))
                    .setUserId(userId)
                    .setConversationId(nullToEmpty(conversationId))
                    .setReadAt(System.currentTimeMillis());
            if (messageIds != null) {
                builder.addAllMessageIds(messageIds);
            }

            publishEvent(ImConstants.MQ_EXCHANGE_EVENT, builder.build().toByteArray(),
                    "im.message.read.event", userId);
            log.debug("消息已读事件已发布: userId={}, conversationId={}, count={}",
                    userId, conversationId, messageIds != null ? messageIds.size() : 0);
        } catch (Exception e) {
            log.error("发布消息已读事件失败: userId={}", userId, e);
        }
    }

    // ============================================================
    // 私有方法
    // ============================================================

    /**
     * 构建 MQ 消息公共头部
     */
    private IMMQProto.MQMessageHeader buildHeader(String eventType, String messageId) {
        return IMMQProto.MQMessageHeader.newBuilder()
                .setEventType(eventType)
                .setMessageId(nullToEmpty(messageId))
                .setTimestamp(System.currentTimeMillis())
                .setTraceId(generateTraceId(messageId))
                .setSourceNode(sessionManager.getNodeId())
                .setRetryCount(0)
                .build();
    }

    /**
     * 发布事件到 MQ 事件交换机
     * <p>
     * 使用 topic 交换机，以 eventType 作为路由键发布，
     * 消费者队列按路由键绑定订阅，避免不相关事件广播。
     * </p>
     */
    private void publishEvent(String exchange, byte[] body, String eventType, String key) {
        Message message = MessageBuilder
                .withBody(body)
                .setContentType(MessageProperties.CONTENT_TYPE_BYTES)
                .setHeader("event_type", eventType)
                .setMessageId(key)
                .build();
        // topic 交换机：以 eventType 作为路由键，消费者按路由键订阅
        rabbitTemplate.send(exchange, eventType, message);
    }

    private String generateTraceId(String seed) {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
