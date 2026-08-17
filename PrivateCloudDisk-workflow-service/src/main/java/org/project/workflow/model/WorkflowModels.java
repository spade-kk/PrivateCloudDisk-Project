package org.project.workflow.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 工作流领域请求与只读投影；集中定义可减少 Controller 与 Mapper 间重复 DTO。 */
public final class WorkflowModels {
    private WorkflowModels() {
    }

    public record CreateWorkflowRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,118}[a-z0-9]$") String slug,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 1_048_576) String dsl,
            Map<String, Object> graph
    ) {
    }

    public record UpdateWorkflowRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 1_048_576) String dsl,
            Map<String, Object> graph
    ) {
    }

    public record ValidateWorkflowRequest(
            @NotBlank @Size(max = 1_048_576) String dsl,
            Map<String, Object> graph
    ) {
    }

    public record RunWorkflowRequest(
            Integer version,
            Map<String, Object> inputs
    ) {
    }

    public record CreateScheduleRequest(
            Integer version,
            @NotBlank @Size(max = 128) String cron,
            @NotBlank @Size(max = 64) String timezone,
            @NotBlank @Pattern(regexp = "SKIP|FIRE_ONCE|CATCH_UP_LIMITED") String misfirePolicy,
            Map<String, Object> inputs
    ) {
    }

    public record WorkflowRow(
            String workflowId,
            String ownerUserId,
            String ownerScopeType,
            String ownerScopeId,
            String name,
            String slug,
            String description,
            String status,
            String latestVersionId,
            long rowVersion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record WorkflowVersionRow(
            String versionId,
            String workflowId,
            int version,
            String dslText,
            String graphJson,
            String schemaVersion,
            String validationReportJson,
            boolean immutable,
            LocalDateTime publishedAt,
            LocalDateTime createdAt
    ) {
    }

    public record ExecutionRow(
            String executionId,
            String workflowId,
            String versionId,
            String userId,
            String spaceId,
            String triggerType,
            String status,
            String currentStep,
            String inputSummaryJson,
            String outputSummaryJson,
            String errorCode,
            String errorSummary,
            String traceId,
            String retryOfExecutionId,
            boolean cancelRequested,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            LocalDateTime createdAt
    ) {
    }

    public record CapabilityRow(
            String capabilityKey,
            String sourceType,
            String sourceId,
            String sourceVersion,
            String displayName,
            String description,
            String inputSchemaJson,
            String outputSchemaJson,
            String requiredPermissionsJson,
            String availabilityPolicyJson,
            String status,
            long revision
    ) {
    }

    public record ValidationIssue(String code, String path, String message) {
    }

    public record ValidationReport(
            boolean valid,
            List<ValidationIssue> issues,
            Map<String, Object> normalized,
            String sha256
    ) {
    }

    public record CapabilityInvocation(
            String capabilityKey,
            String executionId,
            String stepId,
            String userId,
            String spaceId,
            Map<String, Object> input
    ) {
    }

    public record CapabilityResult(boolean success, Map<String, Object> output,
                                   String errorCode, String errorSummary) {
        public static CapabilityResult success(Map<String, Object> output) {
            return new CapabilityResult(true, output, null, null);
        }

        public static CapabilityResult failure(String code, String summary) {
            return new CapabilityResult(false, Map.of(), code, summary);
        }
    }
}
