package org.project.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.billing.mapper.OrderMapper;
import org.project.billing.mapper.PaymentCallbackLogMapper;
import org.project.billing.model.entity.OrderEntity;
import org.project.billing.model.entity.PaymentCallbackLogEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付服务
 * 整合支付宝、微信支付、Apple IAP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderMapper orderMapper;
    private final PaymentCallbackLogMapper callbackLogMapper;
    private final OrderService orderService;

    /**
     * 处理支付宝支付回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleAlipayCallback(String orderNo, String tradeNo, BigDecimal totalAmount, String rawData) {
        log.info("收到支付宝支付回调: orderNo={}, tradeNo={}, amount={}", orderNo, tradeNo, totalAmount);

        // 记录回调日志
        PaymentCallbackLogEntity logEntity = new PaymentCallbackLogEntity();
        logEntity.setOrderNo(orderNo);
        logEntity.setPaymentMethod("ALIPAY");
        logEntity.setCallbackType("PAYMENT");
        logEntity.setThirdPartyTradeNo(tradeNo);
        logEntity.setCallbackRaw(rawData);
        logEntity.setCallbackStatus("SUCCESS");
        logEntity.setRetryCount(0);
        callbackLogMapper.insert(logEntity);

        // 处理支付成功
        orderService.processPaymentSuccess(orderNo, "ALIPAY", tradeNo, totalAmount);
    }

    /**
     * 处理微信支付回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleWechatCallback(String orderNo, String transactionId, BigDecimal totalAmount, String rawData) {
        log.info("收到微信支付回调: orderNo={}, transactionId={}, amount={}", orderNo, transactionId, totalAmount);

        PaymentCallbackLogEntity logEntity = new PaymentCallbackLogEntity();
        logEntity.setOrderNo(orderNo);
        logEntity.setPaymentMethod("WECHAT");
        logEntity.setCallbackType("PAYMENT");
        logEntity.setThirdPartyTradeNo(transactionId);
        logEntity.setCallbackRaw(rawData);
        logEntity.setCallbackStatus("SUCCESS");
        logEntity.setRetryCount(0);
        callbackLogMapper.insert(logEntity);

        orderService.processPaymentSuccess(orderNo, "WECHAT", transactionId, totalAmount);
    }

    /**
     * 处理 Apple IAP 回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleAppleIAPCallback(String orderNo, String transactionId, BigDecimal totalAmount, String rawData) {
        log.info("收到Apple IAP回调: orderNo={}, transactionId={}, amount={}", orderNo, transactionId, totalAmount);

        PaymentCallbackLogEntity logEntity = new PaymentCallbackLogEntity();
        logEntity.setOrderNo(orderNo);
        logEntity.setPaymentMethod("APPLE_IAP");
        logEntity.setCallbackType("PAYMENT");
        logEntity.setThirdPartyTradeNo(transactionId);
        logEntity.setCallbackRaw(rawData);
        logEntity.setCallbackStatus("SUCCESS");
        logEntity.setRetryCount(0);
        callbackLogMapper.insert(logEntity);

        orderService.processPaymentSuccess(orderNo, "APPLE_IAP", transactionId, totalAmount);
    }

    /**
     * 处理退款回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleRefundCallback(String orderNo, String paymentMethod, String tradeNo, String rawData) {
        log.info("收到退款回调: orderNo={}, method={}, tradeNo={}", orderNo, paymentMethod, tradeNo);

        PaymentCallbackLogEntity logEntity = new PaymentCallbackLogEntity();
        logEntity.setOrderNo(orderNo);
        logEntity.setPaymentMethod(paymentMethod);
        logEntity.setCallbackType("REFUND");
        logEntity.setThirdPartyTradeNo(tradeNo);
        logEntity.setCallbackRaw(rawData);
        logEntity.setCallbackStatus("SUCCESS");
        logEntity.setRetryCount(0);
        callbackLogMapper.insert(logEntity);
    }
}