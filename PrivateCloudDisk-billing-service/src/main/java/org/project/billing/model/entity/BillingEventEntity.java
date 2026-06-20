package org.project.billing.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 计费事件实体 (审计日志)
 */
@Data
public class BillingEventEntity {
    private Long id;
    private String userId;
    private String eventType;
    private String eventData;
    private String operator;
    private LocalDateTime createdAt;
}