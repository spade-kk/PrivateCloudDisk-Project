package org.project.billing.model.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付回调消息 (内部事件)
 * 支付成功后发布此消息，由多个消费者处理
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private String messageId;

    /** 订单号 */
    private String orderNo;

    /** 用户ID */
    private String userId;

    /** 订单类型 */
    private String orderType;

    /** 计划ID */
    private Long planId;

    /** 计费周期 */
    private String billingCycle;

    /** 实付金额 */
    private BigDecimal amountPaid;

    /** 支付方式 */
    private String paymentMethod;

    /** 第三方交易号 */
    private String thirdPartyTradeNo;

    /** 支付时间 */
    private LocalDateTime paidAt;

    /** 优惠券码 */
    private String couponCode;
}