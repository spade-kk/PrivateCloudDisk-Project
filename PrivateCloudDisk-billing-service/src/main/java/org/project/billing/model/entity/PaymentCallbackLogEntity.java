package org.project.billing.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 支付回调日志实体
 */
@Data
public class PaymentCallbackLogEntity {
    private Long id;
    private String orderNo;
    private String paymentMethod;
    private String callbackType;
    private String thirdPartyTradeNo;
    private String callbackRaw;
    private String callbackStatus;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime createdAt;
}