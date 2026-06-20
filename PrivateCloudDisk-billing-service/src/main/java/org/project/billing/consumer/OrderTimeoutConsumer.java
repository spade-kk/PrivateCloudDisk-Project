package org.project.billing.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.billing.config.BillingRabbitMQConfig;
import org.project.billing.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时消费者
 * 监听延迟队列中的订单超时消息，取消过期订单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutConsumer {

    private final OrderService orderService;

    /**
     * 消费订单超时消息
     * 当订单超过支付时间未支付时，自动取消
     */
    @RabbitListener(queues = BillingRabbitMQConfig.ORDER_TIMEOUT_QUEUE)
    public void handleOrderTimeout(String orderNo) {
        log.info("收到订单超时消息: orderNo={}", orderNo);
        try {
            orderService.cancelExpiredOrders();
            log.info("订单超时处理完毕: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("订单超时处理失败: orderNo={}, error={}", orderNo, e.getMessage(), e);
        }
    }
}