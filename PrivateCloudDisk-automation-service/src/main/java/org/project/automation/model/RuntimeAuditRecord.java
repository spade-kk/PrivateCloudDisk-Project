package org.project.automation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** Runtime 返回的已脱敏能力调用事实；由 Automation 绑定到稳定插件执行记录。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeAuditRecord(
        @JsonProperty("audit_id") String auditId,
        @JsonProperty("parent_audit_id") String parentAuditId,
        @JsonProperty("capability_key") String capabilityKey,
        @JsonProperty("capability_type") String capabilityType,
        @JsonProperty("summary_template") String summaryTemplate,
        @JsonProperty("target_context") Map<String, Object> targetContext,
        @JsonProperty("input_params") Map<String, Object> inputParams,
        @JsonProperty("output_result") Map<String, Object> outputResult,
        String status,
        @JsonProperty("duration_ms") Long durationMs,
        @JsonProperty("retry_count") Integer retryCount,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("error_summary") String errorSummary,
        String timestamp
) {
}
