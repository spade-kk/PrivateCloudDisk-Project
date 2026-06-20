package org.project.billing.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.billing.config.BillingRabbitMQConfig;
import org.project.billing.model.message.PaymentSuccessMessage;
import org.project.billing.service.SubscriptionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 支付成功消息消费者
 * 监听支付成功消息，异步激活订阅
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSuccessConsumer {

    private final SubscriptionService subscriptionService;

    /**
     * 消费支付成功消息
     * 处理订阅激活、续费、升级等场景
     */
    @RabbitListener(queues = BillingRabbitMQConfig.PAYMENT_SUCCESS_QUEUE)
    public void handlePaymentSuccess(PaymentSuccessMessage message) {
        log.info("收到支付成功消息: orderNo={}, userId={}, orderType={}, amount={}",
                message.getOrderNo(), message.getUserId(), message.getOrderType(), message.getAmountPaid());

        try {
            switch (message.getOrderType()) {
                case "SUBSCRIPTION":
                case "UPGRADE":
                case "RENEWAL":
                    subscriptionService.activatePaidSubscription(
                            message.getUserId(),
                            message.getPlanId(),
                            message.getBillingCycle(),
                            message.getOrderNo());
                    break;
                case "OVERAGE":
                    // 超额计费订单支付成功，仅记录即可
                    log.info("超额计费订单支付成功: orderNo={}", message.getOrderNo());
                    break;
                default:
                    log.warn("未知订单类型: {}", message.getOrderType());
            }

            log.info("支付成功消息处理完毕: orderNo={}", message.getOrderNo());
        } catch (Exception e) {
            log.error("支付成功消息处理失败: orderNo={}, error={}", message.getOrderNo(), e.getMessage(), e);
            throw e; // 抛出异常触发重试
        }
    }
}