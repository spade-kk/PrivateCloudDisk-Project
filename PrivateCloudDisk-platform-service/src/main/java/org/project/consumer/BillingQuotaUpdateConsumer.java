package org.project.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.mapper.QuotaMapper;
import org.project.model.entity.QuotaEntity;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 计费服务配额更新消费者
 * 监听 billing-service 发送的配额更新消息，更新用户存储配额
 *
 * 消息拓扑:
 *   billing-service (publisher)
 *     → pcd.billing.exchange (Topic)
 *     → routing key: quota.update
 *     → pcd.quota.update.queue
 *     → BillingQuotaUpdateConsumer (this)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingQuotaUpdateConsumer {

    private final QuotaMapper quotaMapper;

    /**
     * 消费计费服务发送的配额更新消息
     *
     * 消息格式:
     * {
     *   "messageId": "MSG...",
     *   "userId": "uuid-string",
     *   "planCode": "pro",
     *   "planTier": 1,
     *   "storageLimitBytes": 1099511627776,
     *   "maxFileSizeBytes": 10737418240,
     *   "operatedAt": "2024-01-01T00:00:00"
     * }
     */
    @RabbitListener(queues = RabbitMQConifgure.QUOTA_UPDATE_QUEUE)
    public void handleBillingQuotaUpdate(Map<String, Object> message) {
        String messageId = (String) message.get("messageId");
        String userId = (String) message.get("userId");
        String planCode = (String) message.get("planCode");
        Integer planTier = (Integer) message.get("planTier");
        Object storageLimitBytesObj = message.get("storageLimitBytes");

        log.info("收到计费服务配额更新消息: messageId={}, userId={}, planCode={}, planTier={}",
                messageId, userId, planCode, planTier);

        try {
            UUID userUuid = UUID.fromString(userId);
            QuotaEntity quota = quotaMapper.findQuotaByUserId(userUuid);

            if (quota == null) {
                log.warn("用户配额不存在: userId={}, 跳过更新", userId);
                return;
            }

            Long storageLimitBytes = storageLimitBytesObj != null ?
                    ((Number) storageLimitBytesObj).longValue() : null;

            // 更新配额总容量
            if (storageLimitBytes != null) {
                // 企业版 unlimited = 0
                long newTotalCapacity = storageLimitBytes > 0 ? storageLimitBytes : Long.MAX_VALUE;
                quotaMapper.updateQuotaTotalCapacity(newTotalCapacity, userUuid);
                log.info("配额总容量已更新: userId={}, planCode={}, totalCapacity={}",
                        userId, planCode,
                        storageLimitBytes > 0 ? formatBytes(storageLimitBytes) : "无限");
            }

            log.info("计费服务配额更新处理完成: messageId={}, userId={}", messageId, userId);
        } catch (IllegalArgumentException e) {
            log.error("无效的用户ID: userId={}", userId, e);
        } catch (Exception e) {
            log.error("配额更新失败: messageId={}, userId={}, error={}", messageId, userId, e.getMessage(), e);
            throw e; // 抛出异常触发重试
        }
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "无限";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format("%.1f %s", size, units[unitIndex]);
    }
}