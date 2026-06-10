package org.project.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.NotificationSendLogMapper;
import org.project.model.entity.NotificationSendLogEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 通知发送日志Repository - 实现消费者幂等性
 *
 * <p>核心方法 {@link #tryStart(String, String, String, String)} 是关键：
 * <p>当MQ消息重复投递时（例如网络波动、消费者重启后重连），
 * 我们不希望对同一用户重复发送邮件/短信。
 *
 * <p>通过以下状态流转实现幂等性：
 * <pre>
 *         ┌──────────────┐
 *   首次  │   PENDING    │  发送成功 → SUCCESS
 *   ─────▶│  (处理中)     │─────────────┐
 *         └──────┬───────┘              ▼
 *                │ 失败            ┌─────────────┐
 *                ▼                 │   SUCCESS   │ (最终状态)
 *         ┌──────────────┐          └─────────────┘
 *         │   FAILED     │
 *         │   (失败)     │
 *         └──────┬───────┘
 *                │ 人工重试 → resetToPending → PENDING
 *                ▼
 *         (进入死信队列DLQ)
 * </pre>
 *
 * <p>并发安全性说明：
 * <ul>
 *   <li>数据库层：在 (event_id, channel, receiver) 上建立唯一索引，保证同一事件不会重复插入</li>
 *   <li>应用层：对同一事件，第一个消费者插入成功（PENDING），后续调用 tryStart 返回 false</li>
 *   <li>已成功（SUCCESS）的记录：tryStart 直接返回 false，不会重复发送</li>
 * </ul>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class NotificationSendLogRepository {

    private final NotificationSendLogMapper mapper;

    /**
     * 尝试开始处理一个通知事件。
     *
     * <p>返回值说明：
     * <ul>
     *   <li>{@code true}：成功获取"处理权"，可以发送通知</li>
     *   <li>{@code false}：该事件已被处理（或正在处理），应跳过（直接ack消息）</li>
     * </ul>
     *
     * <p>典型调用顺序：
     * <pre>
     * boolean shouldSend = repository.tryStart(eventId, channel, receiver, userId);
     * if (!shouldSend) {
     *     channel.basicAck(deliveryTag, false);  // 直接确认，不重复发送
     *     return;
     * }
     * try {
     *     // 发送通知
     *     sendNotification(...);
     *     repository.markSuccess(eventId, channel, receiver);
     *     channel.basicAck(deliveryTag, false);
     * } catch (Exception ex) {
     *     repository.markFailed(eventId, channel, receiver, ex.getMessage());
     *     channel.basicNack(deliveryTag, false, false);  // 进入死信队列
     * }
     * </pre>
     */
    public boolean tryStart(String eventId, String channel, String receiver, String userId) {
        // 步骤1：先查询当前状态
        NotificationSendLogEntity existing = mapper.findByEventIdAndChannelAndReceiver(
                eventId, channel, receiver
        );

        if (existing != null) {
            // 状态为SUCCESS/PENDING：直接返回false，不重复处理
            if (NotificationSendLogEntity.STATUS_SUCCESS.equals(existing.getStatus())
                    || NotificationSendLogEntity.STATUS_PENDING.equals(existing.getStatus())) {
                log.info("[幂等检查] 通知已处理或正在处理，跳过. eventId={}, channel={}, receiver={}",
                        eventId, channel, maskReceiver(receiver));
                return false;
            }

            // 状态为FAILED：这是重试场景（或人工触发），重置为PENDING继续处理
            if (NotificationSendLogEntity.STATUS_FAILED.equals(existing.getStatus())) {
                int updated = mapper.resetToPending(eventId, channel, receiver);
                if (updated == 0) {
                    // 并发竞争下被其他消费者抢先
                    return false;
                }
                log.info("[幂等检查] 检测到失败记录，重置为处理中. eventId={}, channel={}, receiver={}",
                        eventId, channel, maskReceiver(receiver));
                return true;
            }

            // 未知状态（理论上不会发生）：保守处理，返回false
            return false;
        }

        // 步骤2：无记录 → 插入新记录（原子操作，受唯一索引保护）
        NotificationSendLogEntity entity = NotificationSendLogEntity.builder()
                .eventId(eventId)
                .channel(channel)
                .receiver(receiver)
                .userId(UUID.fromString(userId))
                .status(NotificationSendLogEntity.STATUS_PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        try {
            int inserted = mapper.insert(entity);
            if (inserted > 0) {
                return true;
            }
            // insert返回0（理论上不会发生）：保守返回false
            log.warn("[幂等检查] insert返回0行，可能是并发竞争. eventId={}", eventId);
            return false;
        } catch (DuplicateKeyException e) {
            // 唯一键冲突：其他消费者已经抢先插入，当前消费者直接跳过
            log.info("[幂等检查] 唯一键冲突，其他消费者已抢占处理权. eventId={}, channel={}", eventId, channel);
            return false;
        }
    }

    /**
     * 标记通知发送成功。
     */
    public void markSuccess(String eventId, String channel, String receiver) {
        mapper.markSuccess(eventId, channel, receiver);
    }

    /**
     * 标记通知发送失败（记录错误信息，截断至1000字符）。
     */
    public void markFailed(String eventId, String channel, String receiver, String errorMessage) {
        String trimmed = errorMessage == null ? ""
                : (errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage);
        mapper.markFailed(eventId, channel, receiver, trimmed);
    }

    /**
     * 脱敏显示接收者，避免日志泄露隐私。
     * <p>邮箱：user***@example.com，手机号：138****8000
     */
    private String maskReceiver(String receiver) {
        if (receiver == null) return "";
        if (receiver.contains("@")) {
            int at = receiver.indexOf("@");
            if (at <= 2) return "***" + receiver.substring(at);
            return receiver.substring(0, 2) + "***" + receiver.substring(at);
        }
        if (receiver.length() >= 7) {
            return receiver.substring(0, 3) + "****" + receiver.substring(receiver.length() - 4);
        }
        return "***";
    }
}
