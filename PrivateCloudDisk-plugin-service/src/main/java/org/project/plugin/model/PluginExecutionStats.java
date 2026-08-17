package org.project.plugin.model;

import java.time.LocalDateTime;

/** 插件管理页执行统计。 */
public record PluginExecutionStats(
        long totalExecutions,
        long successfulExecutions,
        long failedExecutions,
        double successRate,
        LocalDateTime lastExecutedAt
) {
}
