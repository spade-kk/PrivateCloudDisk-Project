package org.project.billing.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户订阅实体
 */
@Data
public class UserSubscriptionEntity {
    private Long id;
    private String userId;
    private Long planId;
    private String status;
    private String billingCycle;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean autoRenew;
    private LocalDateTime cancelledAt;
    private LocalDateTime trialStartedAt;
    private LocalDateTime trialEndedAt;
    private LocalDateTime lastBillingDate;
    private LocalDateTime nextBillingDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}