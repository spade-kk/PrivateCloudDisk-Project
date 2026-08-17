package org.project.plugin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Automation/客户端 Runtime 上报的脱敏执行摘要。 */
public record ExecutionRecordRequest(
        @NotBlank @JsonProperty("execution_id") String executionId,
        @NotBlank @JsonProperty("plugin_id") String pluginId,
        @NotBlank @JsonProperty("version_id") String versionId,
        @NotBlank @JsonProperty("installation_id") String installationId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("space_id") String spaceId,
        @JsonProperty("client_id") String clientId,
        @NotBlank @Size(max = 128) @JsonProperty("trigger_event") String triggerEvent,
        @NotBlank @Pattern(regexp = "EVENT|WORKFLOW|PLUGIN|MANUAL|LOCAL")
        @JsonProperty("trigger_source") String triggerSource,
        @NotBlank @Pattern(regexp = "SUCCESS|FAILED|TIMEOUT|SKIPPED|CANCELLED")
        @JsonProperty("status") String status,
        @NotNull @JsonProperty("started_at") Instant startedAt,
        @NotNull @JsonProperty("ended_at") Instant endedAt,
        @Size(max = 4000) @JsonProperty("output_summary") String outputSummary,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("causation_id") String causationId
) {
}
