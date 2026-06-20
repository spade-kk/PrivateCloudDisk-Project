package org.project.billing.model.vo;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单 VO
 */
@Data
@Builder
public class OrderVO {
    private Long id;
    private String orderNo;
    private String userId;
    private String orderType;
    private String planCode;
    private String planName;
    private String billingCycle;
    private BigDecimal amountOriginal;
    private BigDecimal amountDiscount;
    private BigDecimal amountPayable;
    private BigDecimal amountPaid;
    private String currency;
    private String status;
    private String paymentMethod;
    private String couponCode;
    private BigDecimal refundAmount;
    private String refundReason;
    private LocalDateTime paidAt;
    private LocalDateTime expiredAt;
    private LocalDateTime createdAt;
    private Long expireSeconds;
}