package org.project.plugin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

/**
 * Runtime/自动化服务上报的能力调用事实。
 *
 * <p>[PLUGIN-EXEC-OBS-001] 浏览器绝不直接写入该模型；服务端会再次脱敏并生成摘要，
 * 从而让摘要模式与专业参数模式基于同一可信数据源。</p>
 */
public record ExecutionAuditInput(
        @JsonProperty("audit_id") String auditId,
        @JsonProperty("parent_audit_id") String parentAuditId,
        @NotBlank @Size(max = 160) @JsonProperty("capability_key") String capabilityKey,
        @NotBlank @Pattern(regexp = "BUILTIN|PLATFORM_API|PLUGIN")
        @JsonProperty("capability_type") String capabilityType,
        @JsonProperty("summary_template") String summaryTemplate,
        @JsonProperty("target_context") Map<String, Object> targetContext,
        @JsonProperty("input_params") Map<String, Object> inputParams,
        @JsonProperty("output_result") Map<String, Object> outputResult,
        @NotBlank @Pattern(regexp = "SUCCESS|FAILED|TIMEOUT|RUNNING|SKIPPED") String status,
        @JsonProperty("duration_ms") Long durationMs,
        @JsonProperty("retry_count") Integer retryCount,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("error_summary") String errorSummary,
        @JsonProperty("timestamp") Instant timestamp
) {
}
