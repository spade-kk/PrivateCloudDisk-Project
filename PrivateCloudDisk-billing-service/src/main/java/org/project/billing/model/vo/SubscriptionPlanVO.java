package org.project.billing.model.vo;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订阅计划 VO
 */
@Data
@Builder
public class SubscriptionPlanVO {
    private Long id;
    private String planCode;
    private String planName;
    private Integer planTier;
    private String description;
    private Long storageLimitBytes;
    private String storageLimitDisplay;
    private Long maxFileSizeBytes;
    private String maxFileSizeDisplay;
    private Integer maxShareLinks;
    private String featuresJson;
    private BigDecimal priceMonthly;
    private BigDecimal priceYearly;
    private BigDecimal priceQuarterly;
    private BigDecimal overageUnitPrice;
    private Integer trialDays;
    private Integer sortOrder;

    public static String formatBytes(long bytes) {
        if (bytes <= 0) return "无限";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format("%.1f %s", size, units[unitIndex]);
    }
}