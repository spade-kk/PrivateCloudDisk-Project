package org.project.plugin.model;

import java.time.LocalDateTime;
import java.util.Map;

/** 插件执行详情概要；日志与审计通过独立游标接口按需装载。 */
public record PluginExecutionDetail(
        String executionId,
        String pluginId,
        String pluginName,
        String versionId,
        String version,
        String runtime,
        String entrypoint,
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
        String correlationId,
        long logLineCount,
        long auditCallCount,
        // [PLUGIN-EXEC-OBS-001] 前端执行详情契约统一为 manifestLimits；避免将 Runtime
        // 限制映射为不一致的 resourceLimits 字段而导致“摘要”页错误显示为空。
        Map<String, Object> manifestLimits
) {
}
