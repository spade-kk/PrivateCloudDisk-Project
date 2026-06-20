package org.project.billing.model.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订阅状态变更事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionChangedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 事件ID */
    private String eventId;

    /** 用户ID */
    private String userId;

    /** 变更类型: ACTIVATED/RENEWED/UPGRADED/DOWNGRADED/CANCELLED/EXPIRED */
    private String changeType;

    /** 旧计划编码 */
    private String oldPlanCode;

    /** 新计划编码 */
    private String newPlanCode;

    /** 旧计划等级 */
    private Integer oldPlanTier;

    /** 新计划等级 */
    private Integer newPlanTier;

    /** 变更时间 */
    private LocalDateTime changedAt;
}