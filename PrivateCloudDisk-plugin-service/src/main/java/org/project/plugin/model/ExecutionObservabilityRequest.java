package org.project.plugin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 受信内部服务批量写入某一次插件执行的日志行与能力审计。 */
public record ExecutionObservabilityRequest(
        @NotBlank @JsonProperty("execution_id") String executionId,
        @JsonProperty("observation_id") String observationId,
        @Size(max = 2048) List<@Valid ExecutionLogInput> logs,
        @JsonProperty("audit_trails") @Size(max = 1024) List<@Valid ExecutionAuditInput> auditTrails
) {
}
