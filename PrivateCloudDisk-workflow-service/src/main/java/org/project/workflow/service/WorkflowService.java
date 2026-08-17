package org.project.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.project.workflow.client.PlatformAuthorizationClient;
import org.project.workflow.client.SchedulerClient;
import org.project.workflow.exception.WorkflowApiException;
import org.project.workflow.model.WorkflowModels.CreateWorkflowRequest;
import org.project.workflow.model.WorkflowModels.CreateScheduleRequest;
import org.project.workflow.model.WorkflowModels.ExecutionRow;
import org.project.workflow.model.WorkflowModels.RunWorkflowRequest;
import org.project.workflow.model.WorkflowModels.UpdateWorkflowRequest;
import org.project.workflow.model.WorkflowModels.ValidationReport;
import org.project.workflow.model.WorkflowModels.WorkflowRow;
import org.project.workflow.model.WorkflowModels.WorkflowVersionRow;
import org.project.workflow.repository.ExecutionMapper;
import org.project.workflow.repository.WorkflowMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 工作流草稿、不可变版本和执行命令应用服务。 */
@Service
public class WorkflowService {
    private final WorkflowMapper workflowMapper;
    private final ExecutionMapper executionMapper;
    private final WorkflowDslValidator validator;
    private final PlatformAuthorizationClient authorizationClient;
    private final SchedulerClient schedulerClient;
    private final ObjectMapper objectMapper;

    public WorkflowService(
            WorkflowMapper workflowMapper,
            ExecutionMapper executionMapper,
            WorkflowDslValidator validator,
            PlatformAuthorizationClient authorizationClient,
            SchedulerClient schedulerClient,
            ObjectMapper objectMapper
    ) {
        this.workflowMapper = workflowMapper;
        this.executionMapper = executionMapper;
        this.validator = validator;
        this.authorizationClient = authorizationClient;
        this.schedulerClient = schedulerClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowRow create(String userId, String spaceId, CreateWorkflowRequest request) {
        requireUuid(userId, "用户身份无效");
        String scopeType = "USER";
        String scopeId = userId;
        if (spaceId != null && !spaceId.isBlank()) {
            requireUuid(spaceId, "空间标识无效");
            authorizationClient.requireManage(userId, spaceId);
            scopeType = "SPACE";
            scopeId = spaceId;
        }
        ValidationReport report = requireValid(request.dsl(), userId, spaceId);
        String workflowId = UUID.randomUUID().toString();
        String versionId = UUID.randomUUID().toString();
        try {
            workflowMapper.insertWorkflow(
                    workflowId, userId, scopeType, scopeId,
                    request.name().trim(), request.slug(), request.description()
            );
            workflowMapper.insertVersion(
                    versionId, workflowId, 1, request.dsl(), report.sha256(),
                    json(request.graph() == null ? Map.of() : request.graph()), json(report)
            );
            workflowMapper.attachLatestVersion(workflowId, versionId);
        } catch (DuplicateKeyException exception) {
            throw new WorkflowApiException(
                    "WF-SLUG-CONFLICT", HttpStatus.CONFLICT, "当前范围内已存在同名工作流标识"
            );
        }
        return requireAccessible(workflowId, userId, spaceId);
    }

    public List<WorkflowRow> list(String userId, String spaceId, int page, int size) {
        requireUuid(userId, "用户身份无效");
        if (spaceId != null && !spaceId.isBlank()) {
            requireUuid(spaceId, "空间标识无效");
        }
        int safeSize = Math.max(1, Math.min(size, 100));
        return workflowMapper.listAccessible(
                userId, blank(spaceId), safeSize, (Math.max(page, 1) - 1) * safeSize
        );
    }

    public WorkflowRow get(String workflowId, String userId, String spaceId) {
        return requireAccessible(workflowId, userId, spaceId);
    }

    /** 编辑器读取最新不可变/草稿版本；访问范围沿用工作流本身的用户与空间校验。 */
    public WorkflowVersionRow latestVersion(String workflowId, String userId, String spaceId) {
        requireAccessible(workflowId, userId, spaceId);
        WorkflowVersionRow version = workflowMapper.findLatestVersion(workflowId);
        if (version == null) {
            throw notFound();
        }
        return version;
    }

    @Transactional
    public WorkflowRow update(
            String workflowId,
            String userId,
            String spaceId,
            long expectedVersion,
            UpdateWorkflowRequest request
    ) {
        WorkflowRow workflow = requireAccessible(workflowId, userId, spaceId);
        requireManageIfSpace(workflow, userId, spaceId);
        ValidationReport report = requireValid(request.dsl(), userId, spaceId);
        if (workflowMapper.updateMetadata(
                workflowId, userId, expectedVersion, request.name(), request.description()
        ) != 1) {
            throw new WorkflowApiException(
                    "WF-DRAFT-CONFLICT", HttpStatus.CONFLICT,
                    "草稿已被其他会话修改，请刷新后比较差异"
            );
        }
        int nextVersion = workflowMapper.maxVersion(workflowId) + 1;
        String versionId = UUID.randomUUID().toString();
        workflowMapper.insertVersion(
                versionId, workflowId, nextVersion, request.dsl(), report.sha256(),
                json(request.graph() == null ? Map.of() : request.graph()), json(report)
        );
        workflowMapper.attachLatestVersion(workflowId, versionId);
        return requireAccessible(workflowId, userId, spaceId);
    }

    @Transactional
    public void publish(String workflowId, int version, String userId, String spaceId) {
        WorkflowRow workflow = requireAccessible(workflowId, userId, spaceId);
        requireManageIfSpace(workflow, userId, spaceId);
        WorkflowVersionRow versionRow = requireVersion(workflowId, version);
        requireValid(versionRow.dslText(), userId, spaceId);
        if (!versionRow.immutable()) {
            workflowMapper.publishVersion(versionRow.versionId());
        }
        workflowMapper.markPublished(workflowId, versionRow.versionId());
    }

    @Transactional
    public ExecutionRow run(
            String workflowId,
            String userId,
            String spaceId,
            String idempotencyKey,
            RunWorkflowRequest request
    ) {
        WorkflowRow workflow = requireAccessible(workflowId, userId, spaceId);
        requireManageIfSpace(workflow, userId, spaceId);
        if (!"PUBLISHED".equals(workflow.status())) {
            throw new WorkflowApiException(
                    "WF-NOT-PUBLISHED", HttpStatus.CONFLICT, "工作流尚未发布"
            );
        }
        WorkflowVersionRow version = request.version() == null
                ? workflowMapper.findLatestVersion(workflowId)
                : requireVersion(workflowId, request.version());
        if (version == null || !version.immutable()) {
            throw new WorkflowApiException(
                    "WF-VERSION-NOT-PUBLISHED", HttpStatus.CONFLICT, "指定版本尚未发布"
            );
        }
        return createExecution(
                workflow, version, userId, blank(spaceId), idempotencyKey,
                request.inputs() == null ? Map.of() : request.inputs(), null
        );
    }

    @Transactional
    public ExecutionRow retry(
            String executionId, String userId, String spaceId, String idempotencyKey
    ) {
        ExecutionRow previous = requireExecution(executionId, userId);
        WorkflowRow workflow = requireAccessible(previous.workflowId(), userId, spaceId);
        requireManageIfSpace(workflow, userId, spaceId);
        WorkflowVersionRow version = workflowMapper.findVersionById(previous.versionId());
        Map<String, Object> inputs = readMap(previous.inputSummaryJson());
        return createExecution(
                workflow, version, userId, blank(spaceId), idempotencyKey, inputs, previous.executionId()
        );
    }

    public List<ExecutionRow> executions(
            String workflowId, String userId, String spaceId, int page, int size
    ) {
        requireAccessible(workflowId, userId, spaceId);
        int safeSize = Math.max(1, Math.min(size, 100));
        return executionMapper.list(workflowId, safeSize, (Math.max(page, 1) - 1) * safeSize);
    }

    public ExecutionRow execution(String executionId, String userId) {
        return requireExecution(executionId, userId);
    }

    public void cancel(String executionId, String userId) {
        if (executionMapper.requestCancel(executionId, userId) != 1) {
            throw new WorkflowApiException(
                    "WF-CANCEL-CONFLICT", HttpStatus.CONFLICT,
                    "执行不存在、已结束或无权取消"
            );
        }
    }

    public void archive(String workflowId, String userId, String spaceId) {
        WorkflowRow workflow = requireAccessible(workflowId, userId, spaceId);
        requireManageIfSpace(workflow, userId, spaceId);
        if (workflowMapper.archive(workflowId, userId) != 1) {
            throw notFound();
        }
    }

    public ValidationReport validate(String dsl, String userId, String spaceId) {
        return validator.validate(dsl, userId, spaceId, "workflow.flow");
    }

    public Object createSchedule(
            String workflowId,
            String userId,
            String spaceId,
            CreateScheduleRequest request
    ) {
        WorkflowRow workflow = requireAccessible(workflowId, userId, spaceId);
        requireManageIfSpace(workflow, userId, spaceId);
        if (!"PUBLISHED".equals(workflow.status())) {
            throw new WorkflowApiException(
                    "WF-NOT-PUBLISHED", HttpStatus.CONFLICT, "只有已发布工作流可以创建定时计划"
            );
        }
        WorkflowVersionRow version = request.version() == null
                ? workflowMapper.findLatestVersion(workflowId)
                : requireVersion(workflowId, request.version());
        if (version == null || !version.immutable()) {
            throw new WorkflowApiException(
                    "WF-VERSION-NOT-PUBLISHED", HttpStatus.CONFLICT, "定时计划必须绑定已发布版本"
            );
        }
        return schedulerClient.create(workflow, version, userId, blank(spaceId), request);
    }

    public Object schedules(String workflowId, String userId, String spaceId) {
        requireAccessible(workflowId, userId, spaceId);
        return schedulerClient.list(workflowId);
    }

    public Object setScheduleEnabled(
            String workflowId,
            String scheduleId,
            String userId,
            String spaceId,
            boolean enabled
    ) {
        WorkflowRow workflow = requireAccessible(workflowId, userId, spaceId);
        requireManageIfSpace(workflow, userId, spaceId);
        return schedulerClient.setEnabled(scheduleId, userId, enabled);
    }

    /** Scheduler 事件已经由内部队列认证；仍只允许引用已发布的不可变版本。 */
    @Transactional
    public ExecutionRow runScheduled(
            String workflowId,
            String versionId,
            String userId,
            String spaceId,
            String scheduleId,
            String scheduledAt,
            Map<String, Object> inputs
    ) {
        WorkflowRow workflow = workflowMapper.findById(workflowId);
        WorkflowVersionRow version = workflowMapper.findVersionById(versionId);
        if (workflow == null || version == null || !version.immutable()
                || !"PUBLISHED".equals(workflow.status())) {
            throw new WorkflowApiException(
                    "WF-SCHEDULE-VERSION", HttpStatus.CONFLICT,
                    "定时任务引用的工作流或版本已不可用"
            );
        }
        String idempotencyKey = "schedule:" + scheduleId + ":" + scheduledAt;
        try {
            String executionId = UUID.randomUUID().toString();
            executionMapper.insertExecution(
                    executionId, workflowId, versionId, userId, blank(spaceId),
                    "SCHEDULE", scheduleId, json(inputs), UUID.randomUUID().toString().replace("-", ""),
                    scheduleId, null, idempotencyKey, null
            );
            return executionMapper.findById(executionId);
        } catch (DuplicateKeyException exception) {
            // 至少一次 MQ 投递下，同一 schedule_id + scheduled_at 只产生一个执行实例。
            return null;
        }
    }

    private ExecutionRow createExecution(
            WorkflowRow workflow,
            WorkflowVersionRow version,
            String userId,
            String spaceId,
            String idempotencyKey,
            Map<String, Object> inputs,
            String retryOf
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
            throw new WorkflowApiException(
                    "WF-IDEMPOTENCY-REQUIRED", HttpStatus.BAD_REQUEST,
                    "运行工作流必须提供长度不超过 160 的 Idempotency-Key"
            );
        }
        String executionId = UUID.randomUUID().toString();
        try {
            executionMapper.insertExecution(
                    executionId, workflow.workflowId(), version.versionId(), userId, spaceId,
                    "MANUAL", null, json(inputs), UUID.randomUUID().toString().replace("-", ""),
                    null, null, idempotencyKey, retryOf
            );
        } catch (DuplicateKeyException exception) {
            throw new WorkflowApiException(
                    "WF-IDEMPOTENCY-CONFLICT", HttpStatus.CONFLICT,
                    "该幂等键已经提交过运行请求"
            );
        }
        return executionMapper.findById(executionId);
    }

    private WorkflowRow requireAccessible(String workflowId, String userId, String spaceId) {
        requireUuid(workflowId, "工作流标识无效");
        requireUuid(userId, "用户身份无效");
        WorkflowRow workflow = workflowMapper.findById(workflowId);
        if (workflow == null) {
            throw notFound();
        }
        boolean personal = "USER".equals(workflow.ownerScopeType())
                && userId.equals(workflow.ownerScopeId());
        boolean currentSpace = "SPACE".equals(workflow.ownerScopeType())
                && spaceId != null && spaceId.equals(workflow.ownerScopeId());
        if (!personal && !currentSpace) {
            // 资源枚举保护：跨空间和无权限统一表现为不存在。
            throw notFound();
        }
        return workflow;
    }

    private void requireManageIfSpace(WorkflowRow workflow, String userId, String spaceId) {
        if ("SPACE".equals(workflow.ownerScopeType())) {
            authorizationClient.requireManage(userId, spaceId);
        } else if (!userId.equals(workflow.ownerUserId())) {
            throw notFound();
        }
    }

    private WorkflowVersionRow requireVersion(String workflowId, int version) {
        WorkflowVersionRow row = workflowMapper.findVersion(workflowId, version);
        if (row == null) {
            throw new WorkflowApiException(
                    "WF-VERSION-NOT-FOUND", HttpStatus.NOT_FOUND, "工作流版本不存在"
            );
        }
        return row;
    }

    private ExecutionRow requireExecution(String executionId, String userId) {
        requireUuid(executionId, "执行标识无效");
        ExecutionRow row = executionMapper.findById(executionId);
        if (row == null || !userId.equals(row.userId())) {
            throw new WorkflowApiException(
                    "WF-EXECUTION-NOT-FOUND", HttpStatus.NOT_FOUND, "工作流执行记录不存在"
            );
        }
        return row;
    }

    private ValidationReport requireValid(String dsl, String userId, String spaceId) {
        ValidationReport report = validator.validate(dsl, userId, spaceId, "workflow.flow");
        if (!report.valid()) {
            throw new WorkflowApiException(
                    "WF-DSL-INVALID", HttpStatus.UNPROCESSABLE_ENTITY,
                    report.issues().isEmpty() ? "工作流 DSL 不合法" : report.issues().get(0).message()
            );
        }
        return report;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化工作流数据", exception);
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(
                    json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }
            );
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void requireUuid(String value, String message) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new WorkflowApiException("WF-ID-INVALID", HttpStatus.BAD_REQUEST, message);
        }
    }

    private static WorkflowApiException notFound() {
        return new WorkflowApiException(
                "WF-NOT-FOUND", HttpStatus.NOT_FOUND, "工作流不存在"
        );
    }
}
