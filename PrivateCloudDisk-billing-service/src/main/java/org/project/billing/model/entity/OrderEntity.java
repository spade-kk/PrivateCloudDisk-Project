package org.project.billing.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体
 */
@Data
public class OrderEntity {
    private Long id;
    private String orderNo;
    private String userId;
    private String orderType;
    private Long planId;
    private String billingCycle;
    private BigDecimal amountOriginal;
    private BigDecimal amountDiscount;
    private BigDecimal amountPayable;
    private BigDecimal amountPaid;
    private String currency;
    private String status;
    private String paymentMethod;
    private String paymentChannel;
    private String thirdPartyTradeNo;
    private Long couponId;
    private String couponCode;
    private BigDecimal refundAmount;
    private String refundReason;
    private LocalDateTime refundedAt;
    private LocalDateTime paidAt;
    private LocalDateTime expiredAt;
    private String remark;
    private String extraParams;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}