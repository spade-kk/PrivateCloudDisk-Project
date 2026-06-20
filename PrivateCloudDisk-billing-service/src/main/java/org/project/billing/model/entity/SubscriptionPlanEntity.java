package org.project.billing.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订阅计划实体
 */
@Data
public class SubscriptionPlanEntity {
    private Long id;
    private String planCode;
    private String planName;
    private Integer planTier;
    private String description;
    private Long storageLimitBytes;
    private Long maxFileSizeBytes;
    private Integer maxShareLinks;
    private Integer maxDownloadSpeed;
    private String featuresJson;
    private BigDecimal priceMonthly;
    private BigDecimal priceYearly;
    private BigDecimal priceQuarterly;
    private BigDecimal overageUnitPrice;
    private Integer trialDays;
    private Integer sortOrder;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}