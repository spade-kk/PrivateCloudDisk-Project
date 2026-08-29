package org.project.plugin.model;

import java.time.LocalDateTime;

/** MyBatis 审计行：JSON 文本由服务层统一二次脱敏、解析后再返回浏览器。 */
public record PluginExecutionAuditTrailRow(
        String auditId, String parentAuditId, long sequenceNo, String capabilityKey,
        String capabilityType, String summaryTemplate, String summary, String targetContextJson,
        String inputParamsJson, String inputSummary, String outputResultJson, String outputSummary,
        String status, Long durationMs, int retryCount, String errorCode, String errorSummary,
        LocalDateTime timestamp
) {
}
