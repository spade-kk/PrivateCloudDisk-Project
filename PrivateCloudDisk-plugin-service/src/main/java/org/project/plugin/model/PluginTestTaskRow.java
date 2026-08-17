package org.project.plugin.model;

import java.time.LocalDateTime;

/** [PLUGIN-TEST-001] 测试任务持久化投影；Runtime 状态丢失时保留最后一次可审计状态。 */
public record PluginTestTaskRow(
        String taskId,
        String pluginId,
        String versionId,
        String userId,
        String spaceId,
        String status,
        String resultJson,
        String errorCode,
        String errorSummary,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime expiresAt
) {
}
