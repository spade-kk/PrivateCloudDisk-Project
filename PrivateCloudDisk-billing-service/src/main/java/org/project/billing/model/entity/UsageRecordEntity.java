package org.project.billing.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用量记录实体
 */
@Data
public class UsageRecordEntity {
    private Long id;
    private String userId;
    private LocalDate recordDate;
    private Long storageUsedBytes;
    private Long storageLimitBytes;
    private Long storageOverageBytes;
    private Long trafficUsedBytes;
    private Long trafficLimitBytes;
    private Long trafficOverageBytes;
    private BigDecimal overageCost;
    private Boolean isBilled;
    private Long billingOrderId;
    private LocalDateTime createdAt;
}