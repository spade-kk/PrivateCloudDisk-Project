package org.project.plugin.model;

import java.time.LocalDateTime;

/** MyBatis 查询行：JSON 字段保留文本，避免 Mapper 绑定具体 JSON 实现。 */
public record PluginExecutionDetailRow(
        String executionId, String pluginId, String pluginName, String versionId, String version,
        String runtime, String entrypoint, String installationId, String userId, String spaceId,
        String triggerEvent, String triggerSource, String executionStatus, LocalDateTime startedAt,
        LocalDateTime endedAt, Long durationMs, String outputSummary, String errorCode,
        String correlationId, long logLineCount, long auditCallCount, String manifestJson
) {
}
