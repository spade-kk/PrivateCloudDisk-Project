package org.project.plugin.model;

import java.time.LocalDateTime;
import java.util.Map;

/** API 对外审计投影；input/output 在服务端二次脱敏后以结构化 JSON 返回。 */
public record PluginExecutionAuditTrail(
        String auditId,
        String parentAuditId,
        long sequenceNo,
        String capabilityKey,
        String capabilityType,
        String summaryTemplate,
        String summary,
        Map<String, Object> targetContext,
        Map<String, Object> inputParams,
        String inputSummary,
        Map<String, Object> outputResult,
        String outputSummary,
        String status,
        Long durationMs,
        int retryCount,
        String errorCode,
        String errorSummary,
        LocalDateTime timestamp
) {
}
