package org.project.billing.model.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配额更新消息 (发送给 platform-service)
 * 当订阅计划变更时，通知 platform-service 更新用户存储配额
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaUpdateMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private String messageId;

    /** 用户ID */
    private String userId;

    /** 订阅计划编码 */
    private String planCode;

    /** 计划等级 */
    private Integer planTier;

    /** 新存储配额(字节) */
    private Long storageLimitBytes;

    /** 单文件最大大小(字节) */
    private Long maxFileSizeBytes;

    /** 操作时间 */
    private LocalDateTime operatedAt;
}