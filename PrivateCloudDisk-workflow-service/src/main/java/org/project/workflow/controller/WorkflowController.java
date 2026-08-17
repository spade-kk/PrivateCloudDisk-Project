package org.project.workflow.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.project.workflow.model.ApiResponse;
import org.project.workflow.model.WorkflowModels.CreateWorkflowRequest;
import org.project.workflow.model.WorkflowModels.CreateScheduleRequest;
import org.project.workflow.model.WorkflowModels.RunWorkflowRequest;
import org.project.workflow.model.WorkflowModels.UpdateWorkflowRequest;
import org.project.workflow.model.WorkflowModels.ValidateWorkflowRequest;
import org.project.workflow.service.WorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 工作流公开 API；空间上下文始终从 X-Space-Id 获取。 */
@Validated
@RestController
@RequestMapping("/workflows")
public class WorkflowController {
    private final WorkflowService service;

    public WorkflowController(WorkflowService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateWorkflowRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                service.create(userId, spaceId, body), requestId(request)
        ));
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.list(userId, spaceId, page, size), requestId(request));
    }

    @GetMapping("/{workflowId}")
    public ApiResponse<?> get(
            @PathVariable String workflowId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.get(workflowId, userId, spaceId), requestId(request));
    }

    @GetMapping("/{workflowId}/versions/latest")
    public ApiResponse<?> latestVersion(
            @PathVariable String workflowId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                service.latestVersion(workflowId, userId, spaceId), requestId(request)
        );
    }

    @PatchMapping("/{workflowId}")
    public ApiResponse<?> update(
            @PathVariable String workflowId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestHeader("If-Match") long expectedVersion,
            @Valid @RequestBody UpdateWorkflowRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                service.update(workflowId, userId, spaceId, expectedVersion, body),
                requestId(request)
        );
    }

    @DeleteMapping("/{workflowId}")
    public ApiResponse<?> archive(
            @PathVariable String workflowId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        service.archive(workflowId, userId, spaceId);
        return ApiResponse.ok(Map.of("archived", true), requestId(request));
    }

    @PostMapping("/validate")
    public ApiResponse<?> validate(
            @Valid @RequestBody ValidateWorkflowRequest body,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.validate(body.dsl(), userId, spaceId), requestId(request));
    }

    @PostMapping("/{workflowId}/versions/{version}/publish")
    public ApiResponse<?> publish(
            @PathVariable String workflowId,
            @PathVariable @Min(1) int version,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        service.publish(workflowId, version, userId, spaceId);
        return ApiResponse.ok(Map.of("published", true, "version", version), requestId(request));
    }

    @PostMapping("/{workflowId}/run")
    public ResponseEntity<ApiResponse<?>> run(
            @PathVariable String workflowId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) RunWorkflowRequest body,
            HttpServletRequest request
    ) {
        RunWorkflowRequest safeBody = body == null ? new RunWorkflowRequest(null, Map.of()) : body;
        var execution = service.run(workflowId, userId, spaceId, idempotencyKey, safeBody);
        return ResponseEntity.accepted().body(new ApiResponse<>(
                "WF-ACCEPTED", "工作流已进入执行队列", execution, requestId(request)
        ));
    }

    @PostMapping("/{workflowId}/schedules")
    public ResponseEntity<ApiResponse<?>> createSchedule(
            @PathVariable String workflowId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateScheduleRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                service.createSchedule(workflowId, userId, spaceId, body), requestId(request)
        ));
    }

    @GetMapping("/{workflowId}/schedules")
    public ApiResponse<?> schedules(
            @PathVariable String workflowId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.schedules(workflowId, userId, spaceId), requestId(request));
    }

    @PatchMapping("/{workflowId}/schedules/{scheduleId}")
    public ApiResponse<?> setScheduleEnabled(
            @PathVariable String workflowId,
            @PathVariable String scheduleId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestParam boolean enabled,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                service.setScheduleEnabled(workflowId, scheduleId, userId, spaceId, enabled),
                requestId(request)
        );
    }

    @GetMapping("/{workflowId}/executions")
    public ApiResponse<?> executions(
            @PathVariable String workflowId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                service.executions(workflowId, userId, spaceId, page, size), requestId(request)
        );
    }

    @GetMapping("/executions/{executionId}")
    public ApiResponse<?> execution(
            @PathVariable String executionId,
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.execution(executionId, userId), requestId(request));
    }

    @PostMapping("/executions/{executionId}/retry")
    public ResponseEntity<ApiResponse<?>> retry(
            @PathVariable String executionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        return ResponseEntity.accepted().body(new ApiResponse<>(
                "WF-ACCEPTED", "失败执行已进入重跑队列",
                service.retry(executionId, userId, spaceId, idempotencyKey),
                requestId(request)
        ));
    }

    @PostMapping("/executions/{executionId}/cancel")
    public ApiResponse<?> cancel(
            @PathVariable String executionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        service.cancel(executionId, userId);
        return ApiResponse.ok(Map.of("cancel_requested", true), requestId(request));
    }

    private static String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value == null ? "" : value;
    }
}
