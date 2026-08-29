package org.project.im.platform.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.constant.ImConstants;
import org.project.im.common.enums.MessageStatus;
import org.project.im.common.mq.IMMQProto;
import org.project.im.platform.service.MessageService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;
import java.io.IOException;

import static org.project.im.common.constant.ImConstants.*;

// ============================================================
// IM Platform MQ 事件消费者（含重试与死信处理）
// ============================================================
// 消费来自 IM Server 的事件：
//   C. im.message.delivered.event.platform — 消息送达事件 → 更新消息状态为 DELIVERED
//   D. im.message.failed.event.platform    — 消息失败事件 → 更新消息状态为 FAILED
//   E. im.user.online.event.platform       — 用户上线事件 → 仅记录/状态监控（离线消息改为客户端主动 HTTP 拉取）
//   F. im.user.offline.event.platform      — 用户离线事件 → 标记用户离线
//   G. im.message.read.event.platform      — 消息已读事件 → 更新消息已读状态
//
// 事件总线原则：
//   - 事件生产者只发布事实，不关心消费者
//   - 每个消费者独立处理失败，拥有独立的死信队列
//   - 不可重试错误（反序列化失败、格式错误）：ACK 丢弃
//   - 可重试错误（临时依赖不可用）：NACK(requeue=false) → 独立 DLQ
// ============================================================

/**
 * IM Platform 事件消费者
 * <p>
 * 消费 IM Server 产生的事件队列消息，更新业务状态。
 * 使用手动 ACK 模式，处理失败时根据异常类型和重试次数决定是否进入死信队列。
 * 每个事件消费者拥有独立的死信队列（通过 DLX 路由键区分）。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {

    private final MessageService messageService;

    /** 事件消息最大重试次数 */
    private static final int EVENT_MAX_RETRY_COUNT = 3;

    // ============================================================
    // C. 消息送达事件 — 更新消息状态为 DELIVERED
    // ============================================================

    @RabbitListener(
            queues = ImConstants.MQ_QUEUE_DELIVERED_EVENT_PLATFORM,
            ackMode = "MANUAL"
    )
    public void onMessageDelivered(
            @Payload byte[] message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(value = "x-retry-count", required = false, defaultValue = "0") Integer retryCount) throws IOException {
        try {
            IMMQProto.MessageDeliveredEvent event = IMMQProto.MessageDeliveredEvent.parseFrom(message);
            String messageId = event.getMessageId();
            String receiverId = event.getReceiverId();
            IMMQProto.MessageType messageType = event.getMessageType();

            // 消息类型过滤：仅 CHAT_MESSAGE（普通聊天消息）存在对应的业务消息记录，
            // 才更新状态；通知/回执类消息无业务记录，仅记录日志并 ACK，避免误更新。
            if (messageType == IMMQProto.MessageType.CHAT_MESSAGE
                    || messageType == IMMQProto.MessageType.MESSAGE_TYPE_UNSPECIFIED) {
                log.info("消息送达事件: messageId={}, receiverId={}, messageType={}",
                        messageId, receiverId, messageType);
                // 送达事件 → 更新消息状态为 DELIVERED（在线推送成功即送达）
                messageService.updateStatus(messageId, MessageStatus.DELIVERED);
            } else {
                log.info("通知类送达事件（无业务记录可更新，仅记录）: messageId={}, messageType={}",
                        messageId, messageType);
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            handleEventFailure(channel, deliveryTag, retryCount, "送达事件", "delivered", e);
        }
    }

    // ============================================================
    // D. 消息失败事件 — 更新消息状态为 FAILED
    // ============================================================

    @RabbitListener(
            queues = ImConstants.MQ_QUEUE_FAILED_EVENT_PLATFORM,
            ackMode = "MANUAL"
    )
    public void onMessageFailed(
            @Payload byte[] message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(value = "x-retry-count", required = false, defaultValue = "0") Integer retryCount) throws IOException {
        try {
            IMMQProto.MessageFailedEvent event = IMMQProto.MessageFailedEvent.parseFrom(message);
            String messageId = event.getMessageId();
            String failReason = event.getFailReason();
            IMMQProto.MessageType messageType = event.getMessageType();

            // 消息类型过滤：仅 CHAT_MESSAGE（或旧事件未指定按普通消息处理）才更新状态；
            // 通知类消息无对应的业务消息记录，仅记录日志并 ACK，避免误更新。
            boolean unifiedNotification = messageType != IMMQProto.MessageType.CHAT_MESSAGE
                    && messageType != IMMQProto.MessageType.MESSAGE_TYPE_UNSPECIFIED;

            if (unifiedNotification) {
                // 送达通知消息失败：通知消息没有对应的业务消息记录，
                // 无需更新消息状态，仅记录 WARN 日志。
                log.warn("通知类消息推送失败（无业务记录可更新，仅记录）: messageId={}, reason={}, messageType={}",
                        messageId, failReason, messageType);
            } else {
                // 普通业务消息失败：更新消息状态为 FAILED。
                // UNSPECIFIED（未携带类型）按普通消息处理。
                log.info("消息失败事件: messageId={}, reason={}, messageType={}",
                        messageId, failReason, messageType);
                // 推送失败事件 → 更新消息状态为 FAILED
                messageService.updateStatus(messageId, MessageStatus.FAILED);
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            handleEventFailure(channel, deliveryTag, retryCount, "失败事件", "failed", e);
        }
    }

    // ============================================================
    // E. 用户上线事件 — 触发离线消息补偿
    // ============================================================

    @RabbitListener(
            queues = ImConstants.MQ_QUEUE_USER_ONLINE_EVENT,
            ackMode = "MANUAL"
    )
    public void onUserOnline(
            @Payload byte[] message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(value = "x-retry-count", required = false, defaultValue = "0") Integer retryCount) throws IOException {
        try {
            IMMQProto.UserOnlineEvent event = IMMQProto.UserOnlineEvent.parseFrom(message);
            String userId = event.getUserId();
            String serverNodeId = event.getServerNodeId();

            log.info("用户上线事件: userId={}, serverNode={}", userId, serverNodeId);
            // 离线消息改为客户端主动 HTTP 拉取（GET /im/messages/offline），
            // 此处仅记录上线事件用于在线状态监控，不再触发服务端自动推送补偿。

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            handleEventFailure(channel, deliveryTag, retryCount, "用户上线事件", "online", e);
        }
    }

    // ============================================================
    // F. 用户离线事件 — 标记用户离线，清理在线状态缓存
    // ============================================================

    @RabbitListener(
            queues = ImConstants.MQ_QUEUE_USER_OFFLINE_EVENT,
            ackMode = "MANUAL"
    )
    public void onUserOffline(
            @Payload byte[] message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(value = "x-retry-count", required = false, defaultValue = "0") Integer retryCount) throws IOException {
        try {
            IMMQProto.UserOfflineEvent event = IMMQProto.UserOfflineEvent.parseFrom(message);
            String userId = event.getUserId();
            String serverNodeId = event.getServerNodeId();

            log.info("用户离线事件: userId={}, serverNode={}", userId, serverNodeId);
            // TODO: 清理用户在线状态缓存

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            handleEventFailure(channel, deliveryTag, retryCount, "用户离线事件", "offline", e);
        }
    }

    // ============================================================
    // G. 消息已读事件 — 更新消息已读状态，更新未读计数
    // ============================================================

    @RabbitListener(
            queues = ImConstants.MQ_QUEUE_MESSAGE_READ_EVENT,
            ackMode = "MANUAL"
    )
    public void onMessageRead(
            @Payload byte[] message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(value = "x-retry-count", required = false, defaultValue = "0") Integer retryCount) throws IOException {
        try {
            IMMQProto.MessageReadEvent event = IMMQProto.MessageReadEvent.parseFrom(message);
            String userId = event.getUserId();
            String conversationId = event.getConversationId();

            log.info("消息已读事件: userId={}, conversationId={}", userId, conversationId);
            messageService.markAsRead(conversationId, userId);

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            handleEventFailure(channel, deliveryTag, retryCount, "消息已读事件", "read", e);
        }
    }

    // ============================================================
    // 统一事件失败处理
    // ============================================================

    /**
     * 统一处理事件消费失败
     * <p>
     * 策略：
     * <ul>
     *   <li>反序列化失败（坏消息）→ 不可重试，ACK 丢弃</li>
     *   <li>其他异常，retry_count < 3 → NACK(requeue=false) → 独立 DLQ（通过 DLX 路由）</li>
     *   <li>其他异常，retry_count >= 3 → ACK 丢弃（已在 DLQ 中）</li>
     * </ul>
     * </p>
     *
     * @param channel     AMQP Channel
     * @param deliveryTag 投递标签
     * @param retryCount  当前重试次数
     * @param eventName   事件名称（用于日志）
     * @param eventType   事件类型标识（用于监控）
     * @param e           异常
     */
    private void handleEventFailure(Channel channel, long deliveryTag, Integer retryCount,
                                     String eventName, String eventType, Exception e) throws IOException {
        int currentRetry = retryCount != null ? retryCount : 0;

        // 反序列化失败（InvalidProtocolBufferException）属于坏消息，不可重试
        if (isDeserializationError(e)) {
            log.error("处理{}失败（反序列化错误，不可重试）: {}", eventName, e.getMessage());
            channel.basicAck(deliveryTag, false);
            return;
        }

        if (currentRetry < EVENT_MAX_RETRY_COUNT) {
            log.warn("处理{}失败，将重试: retryCount={}/{}, error={}",
                    eventName, currentRetry, EVENT_MAX_RETRY_COUNT, e.getMessage());
            // NACK(requeue=false) → 消息通过 DLX 路由到该消费者独立的 DLQ
            channel.basicNack(deliveryTag, false, false);
        } else {
            log.error("处理{}已达最大重试次数，消息进入死信队列: retryCount={}, error={}",
                    eventName, currentRetry, e.getMessage());
            // ACK 丢弃，消息已通过 DLX 进入独立 DLQ
            channel.basicAck(deliveryTag, false);
        }
    }

    /**
     * 判断是否为反序列化错误（不可重试的坏消息）
     */
    private boolean isDeserializationError(Exception e) {
        String className = e.getClass().getName();
        return className.contains("InvalidProtocolBufferException")
                || className.contains("JsonParseException")
                || className.contains("JsonMappingException");
    }
}
