package org.project.billing.model.vo;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户订阅 VO
 */
@Data
@Builder
public class UserSubscriptionVO {
    private Long id;
    private String userId;
    private Long planId;
    private String planCode;
    private String planName;
    private String status;
    private String billingCycle;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean autoRenew;
    private Long storageLimitBytes;
    private String storageLimitDisplay;
    private Integer trialDays;
    private LocalDateTime trialEndedAt;
    private LocalDateTime nextBillingDate;
    private Long remainingDays;
}