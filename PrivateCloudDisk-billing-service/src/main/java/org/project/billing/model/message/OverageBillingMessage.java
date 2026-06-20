package org.project.billing.model.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 超额计费消息
 * 每日定时任务统计超额用量后发送此消息进行计费
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverageBillingMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private String messageId;

    /** 用户ID */
    private String userId;

    /** 记录日期 */
    private String recordDate;

    /** 超额存储量(字节) */
    private Long storageOverageBytes;

    /** 超额流量(字节) */
    private Long trafficOverageBytes;

    /** 超额费用 */
    private BigDecimal overageCost;

    /** 计费时间 */
    private LocalDateTime billedAt;
}