package org.project.billing.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.billing.config.BillingRabbitMQConfig;
import org.project.billing.model.message.SubscriptionChangedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订阅变更事件消费者
 * 处理订阅状态变更的后续操作
 * 例如: 通知其他服务(platform-service)更新用户配额
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionSyncConsumer {

    /**
     * 消费订阅变更事件
     */
    @RabbitListener(queues = BillingRabbitMQConfig.SUBSCRIPTION_SYNC_QUEUE)
    public void handleSubscriptionChanged(SubscriptionChangedEvent event) {
        log.info("收到订阅变更事件: eventId={}, userId={}, changeType={}, old={}, new={}",
                event.getEventId(), event.getUserId(), event.getChangeType(),
                event.getOldPlanCode(), event.getNewPlanCode());

        try {
            switch (event.getChangeType()) {
                case "ACTIVATED":
                    log.info("用户订阅已激活: userId={}, plan={}", event.getUserId(), event.getNewPlanCode());
                    break;
                case "RENEWED":
                    log.info("用户订阅已续费: userId={}, plan={}", event.getUserId(), event.getNewPlanCode());
                    break;
                case "UPGRADED":
                    log.info("用户订阅已升级: userId={}, {} -> {}", event.getUserId(),
                            event.getOldPlanCode(), event.getNewPlanCode());
                    break;
                case "DOWNGRADED":
                    log.info("用户订阅已降级: userId={}, {} -> {}", event.getUserId(),
                            event.getOldPlanCode(), event.getNewPlanCode());
                    break;
                case "CANCELLED":
                    log.info("用户订阅已取消: userId={}, plan={}", event.getUserId(), event.getOldPlanCode());
                    break;
                case "EXPIRED":
                    log.info("用户订阅已过期: userId={}, plan={}", event.getUserId(), event.getOldPlanCode());
                    break;
                default:
                    log.warn("未知订阅变更类型: {}", event.getChangeType());
            }

            log.info("订阅变更事件处理完毕: eventId={}", event.getEventId());
        } catch (Exception e) {
            log.error("订阅变更事件处理失败: eventId={}, error={}", event.getEventId(), e.getMessage(), e);
            throw e;
        }
    }
}