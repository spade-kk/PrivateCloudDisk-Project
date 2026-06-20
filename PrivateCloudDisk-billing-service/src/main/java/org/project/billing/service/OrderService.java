package org.project.billing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.billing.common.BillingException;
import org.project.billing.config.BillingRabbitMQConfig;
import org.project.billing.mapper.*;
import org.project.billing.model.entity.*;
import org.project.billing.model.message.PaymentSuccessMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final SubscriptionPlanMapper subscriptionPlanMapper;
    private final RabbitTemplate rabbitTemplate;

    public OrderEntity getOrderByOrderNo(String orderNo) {
        OrderEntity order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw BillingException.notFound("订单不存在: " + orderNo);
        }
        return order;
    }

    public List<OrderEntity> getUserOrders(String userId) {
        return orderMapper.findByUserId(userId);
    }

    /**
     * 支付成功处理 (Seata 分布式事务)
     * 更新订单状态 + 激活订阅 + 发布支付成功事件
     */
    @Transactional(rollbackFor = Exception.class)
    public void processPaymentSuccess(String orderNo, String paymentMethod,
                                      String thirdPartyTradeNo, BigDecimal amountPaid) {
        OrderEntity order = orderMapper.findByOrderNoForUpdate(orderNo);
        if (order == null) {
            throw BillingException.notFound("订单不存在: " + orderNo);
        }
        if (!"PENDING".equals(order.getStatus())) {
            log.warn("订单状态非PENDING，跳过支付处理: orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }

        // 更新订单支付信息
        orderMapper.updatePaymentInfo(orderNo, paymentMethod, thirdPartyTradeNo,
                amountPaid, LocalDateTime.now(), "PAID");

        // 发布支付成功消息，由消费者异步处理订阅激活
        PaymentSuccessMessage message = PaymentSuccessMessage.builder()
                .messageId("PAY-" + orderNo + "-" + System.currentTimeMillis())
                .orderNo(orderNo)
                .userId(order.getUserId())
                .orderType(order.getOrderType())
                .planId(order.getPlanId())
                .billingCycle(order.getBillingCycle())
                .amountPaid(amountPaid)
                .paymentMethod(paymentMethod)
                .thirdPartyTradeNo(thirdPartyTradeNo)
                .paidAt(LocalDateTime.now())
                .couponCode(order.getCouponCode())
                .build();

        rabbitTemplate.convertAndSend(BillingRabbitMQConfig.BILLING_EXCHANGE,
                BillingRabbitMQConfig.RK_PAYMENT_SUCCESS, message);

        log.info("支付成功处理完成: orderNo={}, method={}, amount={}", orderNo, paymentMethod, amountPaid);
    }

    /**
     * 退款处理
     */
    @Transactional(rollbackFor = Exception.class)
    public void processRefund(String orderNo, String refundReason, String operatorId) {
        OrderEntity order = orderMapper.findByOrderNoForUpdate(orderNo);
        if (order == null) {
            throw BillingException.notFound("订单不存在: " + orderNo);
        }
        if (!"PAID".equals(order.getStatus())) {
            throw BillingException.badRequest("订单状态不允许退款: " + order.getStatus());
        }

        BigDecimal refundAmount = order.getAmountPaid();
        orderMapper.updateRefundInfo(orderNo, refundAmount, refundReason, LocalDateTime.now(), "REFUNDED");

        log.info("退款处理完成: orderNo={}, refundAmount={}, operator={}", orderNo, refundAmount, operatorId);
    }

    /**
     * 取消过期订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelExpiredOrders() {
        List<OrderEntity> expiredOrders = orderMapper.findExpiredPendingOrders(LocalDateTime.now());
        for (OrderEntity order : expiredOrders) {
            orderMapper.updateStatus(order.getOrderNo(), "EXPIRED");
            log.info("过期订单已取消: orderNo={}", order.getOrderNo());
        }
    }
}