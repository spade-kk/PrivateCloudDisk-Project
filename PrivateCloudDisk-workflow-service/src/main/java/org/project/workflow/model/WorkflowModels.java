package org.project.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /**
     * DSL 校验请求。
     *
     * <p>改动点（CLOUDFLOW-REQUEST-001）：原实现把 dsl 直接声明为 String，当前端旧版本
     * 或恢复的编辑器快照以 {source|dsl|text: "..."} 包装源码时，Jackson 会在 Controller
     * 之前抛出 String ← Object 的 400。新行为在 JSON 边界先接收 JsonNode，再统一解包为
     * String 交给 Rust Runtime；标准请求仍然必须传递字符串，兼容逻辑只用于迁移期间，
     * 不改变 CloudFlow DSL 的语义或保存格式。</p>
     */
    public record ValidateWorkflowRequest(
            @JsonProperty("dsl") @NotNull JsonNode dslPayload,
            Map<String, Object> graph
    ) {
        /** 保留旧版 Java 调用方直接传 String 的构造方式，避免 DTO 边界改造影响既有测试/适配器。 */
        public ValidateWorkflowRequest(String dsl, Map<String, Object> graph) {
            this(dsl == null ? null : TextNode.valueOf(dsl), graph);
        }

        public String dsl() {
            if (dslPayload == null || dslPayload.isNull()) {
                return "";
            }
            if (dslPayload.isTextual()) {
                return dslPayload.textValue();
            }
            if (dslPayload.isObject()) {
                for (String key : List.of("source", "dsl", "text")) {
                    JsonNode candidate = dslPayload.get(key);
                    if (candidate != null && candidate.isTextual()) {
                        return candidate.textValue();
                    }
                }
            }
            // 让 Runtime 返回结构化 DSL 诊断，而不是在 Spring JSON 反序列化层直接丢失上下文。
            return dslPayload.toString();
        }
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

    /** Workflow Service 事务 Outbox 投递视图。 */
    public record WorkflowOutboxRow(
            String eventId,
            String aggregateId,
            String eventType,
            String routingKey,
            String payloadJson,
            int attempt
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

    /**
     * CloudFlow 结构化诊断投影。
     *
     * <p>改动点（CLOUDFLOW-DIAGNOSTIC-001）：原记录只保留 code/path/message，导致 Monaco
     * 无法精确标记且终端丢失多行 cliOutput；新字段完整透传 Runtime 诊断。三参数构造器保留，
     * 兼容原有业务代码和测试。</p>
     */
    public record ValidationIssue(
            String code,
            String path,
            String message,
            Integer line,
            Integer column,
            String severity,
            String category,
            String cliOutput,
            List<String> suggestions,
            String help,
            String documentationUrl
    ) {
        public ValidationIssue(String code, String path, String message) {
            this(code, path, message, null, null, "ERROR", null, null, List.of(), null, null);
        }
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

    /** Rust Runtime gRPC Agent 转交的能力调用命令；权限快照在服务端再次取交集校验。 */
    public record AgentCapabilityInvocation(
            @NotBlank @Size(max = 255) String capabilityKey,
            @NotBlank @Size(max = 36) String executionId,
            @NotBlank @Size(max = 128) String stepId,
            @Min(1) int attempt,
            @NotBlank @Size(max = 128) String userId,
            @Size(max = 128) String spaceId,
            Map<String, Object> input,
            List<String> declaredPermissions,
            List<String> grantedPermissions,
            @NotBlank @Size(max = 64) String traceId,
            @NotBlank @Size(max = 300) String idempotencyKey
    ) {
    }

    /**
     * CloudFlow MCP Server 的受服务凭证保护内部调用信封。
     *
     * <p>它与 Runtime Agent 信封刻意分离：MCP 没有执行图、步骤或调用方可声明的权限；
     * 用户、租户、空间和幂等键都由网关签名上下文与 MCP Adapter 派生，Hub 仍然以实时
     * 授权和 registry policy 作最终判定。这样第三方 Agent 不会获得 Runtime 内部协议。</p>
     */
    public record McpCapabilityInvocation(
            @NotBlank @Size(max = 255) String capabilityKey,
            @NotBlank @Size(max = 128) String userId,
            @Size(max = 128) String tenantId,
            @Size(max = 128) String spaceId,
            Map<String, Object> input,
            @NotBlank @Size(max = 64) String traceId,
            @NotBlank @Size(max = 300) String idempotencyKey,
            @Size(max = 128) String agentId
    ) {
    }

    /** MCP tools/list 的内部游标页；offset 只在私网 Hub↔MCP 契约中出现。 */
    public record McpCapabilityListRequest(
            @NotBlank @Size(max = 128) String userId,
            @Size(max = 128) String tenantId,
            @Size(max = 128) String spaceId,
            @Min(0) int offset,
            @Min(1) @Max(100) int limit
    ) {
    }

    public record McpCapabilityPage(List<CapabilityRow> capabilities, Integer nextOffset) {
    }

    /** MCP 的协议级审计（initialize/list/resources/prompts 等无 capability 执行时使用）。 */
    public record McpProtocolAuditEntry(
            @NotBlank @Size(max = 128) String method,
            @NotBlank @Size(max = 128) String userId,
            @Size(max = 128) String tenantId,
            @Size(max = 128) String spaceId,
            @NotBlank @Size(max = 64) String traceId,
            @Size(max = 128) String agentId,
            Map<String, Object> parameterSummary,
            boolean success,
            @Size(max = 64) String resultCode,
            @Min(0) long durationMs
    ) {
    }

    /** 能力调用审计条目（需求五 5.16-5.17 / 四 4.20）：记录调用者服务、用户/空间、参数摘要、结果与耗时。 */
    public record CapabilityAuditEntry(
            String capabilityKey,
            String callerService,
            String executionId,
            String stepId,
            String userId,
            String spaceId,
            String traceId,
            String paramSummaryJson,
            boolean success,
            String resultCode,
            String targetService,
            Long durationMs
    ) {
    }

    public record CapabilityResult(boolean success, Map<String, Object> output,
                                   String errorCode, String errorSummary, boolean retryable) {
        public static CapabilityResult success(Map<String, Object> output) {
            return new CapabilityResult(true, output, null, null, false);
        }

        public static CapabilityResult failure(String code, String summary) {
            return new CapabilityResult(false, Map.of(), code, summary, false);
        }

        public static CapabilityResult retryableFailure(String code, String summary) {
            return new CapabilityResult(false, Map.of(), code, summary, true);
        }
    }
}
