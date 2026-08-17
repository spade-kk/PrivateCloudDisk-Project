package org.project.plugin.model;

import java.time.LocalDateTime;

/** 对外执行历史只包含脱敏摘要，不暴露宿主路径或完整堆栈。 */
public record PluginExecutionRow(
        String executionId,
        String pluginId,
        String versionId,
        String installationId,
        String userId,
        String spaceId,
        String triggerEvent,
        String triggerSource,
        String executionStatus,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long durationMs,
        String outputSummary,
        String errorCode,
        String correlationId
) {
}
