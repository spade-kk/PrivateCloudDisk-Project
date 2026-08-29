package org.project.plugin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.project.plugin.model.ApiResponse;
import org.project.plugin.model.CreatePluginRequest;
import org.project.plugin.model.CreatePluginVersionRequest;
import org.project.plugin.model.PluginInstallRequest;
import org.project.plugin.model.PluginTestRequest;
import org.project.plugin.model.UpdatePluginRequest;
import org.project.plugin.service.PluginManagementService;
import org.project.plugin.service.PluginExecutionService;
import org.project.plugin.storage.StoredPluginPackage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 插件生命周期公开 API；身份由网关注入，空间 ID 只从请求头读取。 */
@Validated
@RestController
@RequestMapping("/plugins")
@RequiredArgsConstructor
@Slf4j
public class PluginController {
    private final PluginManagementService service;
    private final PluginExecutionService executionService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePluginRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.ok(service.create(userId, request), requestId(servletRequest))
        );
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

    @GetMapping("/installations")
    public ApiResponse<?> installations(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                service.listInstallations(userId, spaceId), requestId(request)
        );
    }

    @GetMapping("/{pluginId}")
    public ApiResponse<?> get(
            @PathVariable String pluginId,
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.getOwned(pluginId, userId), requestId(request));
    }

    @PatchMapping("/{pluginId}")
    public ApiResponse<?> update(
            @PathVariable String pluginId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("If-Match") long rowVersion,
            @Valid @RequestBody UpdatePluginRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                service.update(pluginId, userId, rowVersion, body), requestId(request)
        );
    }

    @DeleteMapping("/{pluginId}")
    public ApiResponse<?> delete(
            @PathVariable String pluginId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        service.delete(pluginId, userId);
        return ApiResponse.ok(Map.of("deleted", true), requestId(request));
    }

    @PostMapping("/{pluginId}/versions")
    public ResponseEntity<ApiResponse<?>> createVersion(
            @PathVariable String pluginId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePluginVersionRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                service.createVersion(pluginId, userId, body), requestId(request)
        ));
    }

    @GetMapping("/{pluginId}/versions")
    public ApiResponse<?> listVersions(
            @PathVariable String pluginId,
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.listVersions(pluginId, userId), requestId(request));
    }

    @PutMapping("/{pluginId}/versions/{version}/source")
    public ApiResponse<?> uploadSource(
            @PathVariable String pluginId,
            @PathVariable String version,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestPart("package") MultipartFile packageFile,
            HttpServletRequest request
    ) {
        StoredPluginPackage stored = service.uploadPackage(
                pluginId, version, userId, packageFile
        );
        // 不把源码、清单或宿主路径回传给浏览器，只返回完整性和容量摘要。
        return ApiResponse.ok(Map.of(
                "object_key", stored.objectKey(),
                "sha256", stored.sha256(),
                "package_bytes", stored.packageBytes(),
                "file_count", stored.fileCount(),
                "expanded_bytes", stored.expandedBytes()
        ), requestId(request));
    }

    @PostMapping("/{pluginId}/versions/{version}/validate")
    public ApiResponse<?> validate(
            @PathVariable String pluginId,
            @PathVariable String version,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                service.validate(pluginId, version, userId), requestId(request)
        );
    }

    @PostMapping("/{pluginId}/versions/{version}/publish")
    public ApiResponse<?> publish(
            @PathVariable String pluginId,
            @PathVariable String version,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                service.publish(pluginId, version, userId), requestId(request)
        );
    }

    /**
     * [PLUGIN-TEST-001] 开发阶段测试：只创建异步 Runtime 任务，不在 Plugin Service JVM 执行源码。
     */
    @PostMapping("/{pluginId}/versions/{version}/test")
    public ResponseEntity<ApiResponse<?>> test(
            @PathVariable String pluginId,
            @PathVariable String version,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @Valid @RequestBody PluginTestRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.accepted().body(ApiResponse.ok(
                service.createTestExecution(pluginId, version, userId, spaceId, body),
                requestId(request)
        ));
    }

    /** [PLUGIN-TEST-001] 状态查询只允许任务所属用户访问，Runtime 不直接暴露给浏览器。 */
    @GetMapping("/test-executions/{taskId}")
    public ApiResponse<?> testStatus(
            @PathVariable String taskId,
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.getTestExecution(taskId, userId), requestId(request));
    }

    @PostMapping("/test-executions/{taskId}/cancel")
    public ApiResponse<?> cancelTest(
            @PathVariable String taskId,
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.cancelTestExecution(taskId, userId), requestId(request));
    }

    @PostMapping("/{pluginId}/installations/user")
    public ApiResponse<?> installForUser(
            @PathVariable String pluginId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PluginInstallRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                Map.of("installation_id", service.installForUser(pluginId, userId, body)),
                requestId(request)
        );
    }

    @PostMapping("/{pluginId}/installations/space")
    public ApiResponse<?> installForSpace(
            @PathVariable String pluginId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Space-Id") String spaceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PluginInstallRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                Map.of(
                        "installation_id",
                        service.installForSpace(pluginId, userId, spaceId, body)
                ),
                requestId(request)
        );
    }

    @PatchMapping("/installations/{installationId}")
    public ApiResponse<?> updateInstallation(
            @PathVariable String installationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam boolean enabled,
            HttpServletRequest request
    ) {
        service.setUserInstallationEnabled(installationId, userId, enabled);
        return ApiResponse.ok(Map.of("enabled", enabled), requestId(request));
    }

    @DeleteMapping("/installations/{installationId}")
    public ApiResponse<?> uninstall(
            @PathVariable String installationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        service.uninstallForUser(installationId, userId);
        return ApiResponse.ok(Map.of("uninstalled", true), requestId(request));
    }

    @PatchMapping("/installations/space/{installationId}")
    public ApiResponse<?> updateSpaceInstallation(
            @PathVariable String installationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Space-Id") String spaceId,
            @RequestParam boolean enabled,
            HttpServletRequest request
    ) {
        service.setSpaceInstallationEnabled(installationId, userId, spaceId, enabled);
        return ApiResponse.ok(Map.of("enabled", enabled), requestId(request));
    }

    @DeleteMapping("/installations/space/{installationId}")
    public ApiResponse<?> uninstallFromSpace(
            @PathVariable String installationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Space-Id") String spaceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        service.uninstallForSpace(installationId, userId, spaceId);
        return ApiResponse.ok(Map.of("uninstalled", true), requestId(request));
    }

    @GetMapping("/{pluginId}/executions")
    public ApiResponse<?> executions(
            @PathVariable String pluginId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                executionService.list(pluginId, userId, spaceId, status, page, size),
                requestId(request)
        );
    }

    /** [PLUGIN-EXEC-OBS-001] 独立执行详情页与浮窗复用的受权详情入口。 */
    @GetMapping("/executions/{executionId}")
    public ApiResponse<?> executionDetail(
            @PathVariable String executionId,
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(executionService.detail(executionId, userId), requestId(request));
    }

    @GetMapping("/executions/{executionId}/logs")
    public ApiResponse<?> executionLogs(
            @PathVariable String executionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "200") @Min(1) @Max(500) int limit,
            @RequestParam(defaultValue = "asc") String order,
            @RequestParam(value = "start_time", required = false) Instant startTime,
            @RequestParam(value = "end_time", required = false) Instant endTime,
            @RequestParam(defaultValue = "") String level,
            @RequestParam(defaultValue = "") String source,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                executionService.logs(executionId, userId, cursor, limit, order, startTime, endTime, level, source),
                requestId(request)
        );
    }

    /**
     * 短连接 SSE 追尾：完成态从持久化表读取，运行态可由受信写端持续追加。
     * 浏览器不能访问 Runtime；此流仍经过 Plugin Service 的所有者/空间管理员门禁。
     */
    @GetMapping(value = "/executions/{executionId}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executionLogStream(
            @PathVariable String executionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") long after
    ) {
        executionService.detail(executionId, userId);
        SseEmitter emitter = new SseEmitter(25_000L);
        CompletableFuture.runAsync(() -> {
            long cursor = Math.max(0, after);
            try {
                for (int attempt = 0; attempt < 20; attempt++) {
                    List<?> lines = executionService.tailLogs(executionId, userId, cursor);
                    for (Object line : lines) {
                        var typed = (org.project.plugin.model.PluginExecutionLogLine) line;
                        cursor = typed.sequenceNo();
                        emitter.send(SseEmitter.event().name("log").id(String.valueOf(cursor)).data(typed));
                    }
                    emitter.send(SseEmitter.event().name("heartbeat").data(Map.of("cursor", cursor)));
                    Thread.sleep(1_000L);
                }
                emitter.complete();
            } catch (Exception exception) {
                // 客户端断开/网络抖动是正常路径；只记录关联信息，不把日志正文写入服务端日志。
                log.debug("插件日志 SSE 结束 executionId={} reason={}", executionId,
                        exception.getClass().getSimpleName());
                emitter.complete();
            }
        });
        return emitter;
    }

    @GetMapping(value = "/executions/{executionId}/logs/download", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<StreamingResponseBody> downloadExecutionLogs(
            @PathVariable String executionId,
            @RequestHeader("X-User-Id") String userId
    ) {
        // [PLUGIN-EXEC-OBS-001] 原实现为下载设置 20,000 行上限，会把大执行日志伪装为完整
        // 下载。改为逐页 StreamingResponseBody，避免大日志全量进入 JVM 堆且绝不静默截断。
        StreamingResponseBody body = outputStream -> {
            String cursor = null;
            do {
                var page = executionService.logs(executionId, userId, cursor, 500, "asc", null, null, "", "");
                for (var line : page.items()) {
                    String rendered = "%s %-5s %-10s %s%n".formatted(
                            line.timestamp(), line.level(), line.source(), line.content()
                    );
                    outputStream.write(rendered.getBytes(StandardCharsets.UTF_8));
                }
                outputStream.flush();
                cursor = page.nextCursor();
            } while (cursor != null);
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=plugin-execution-" + executionId + ".log")
                .body(body);
    }

    @GetMapping("/executions/{executionId}/audit-trails")
    public ApiResponse<?> executionAuditTrails(
            @PathVariable String executionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit,
            @RequestParam(value = "capability_type", defaultValue = "") String capabilityType,
            @RequestParam(defaultValue = "") String status,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                executionService.auditTrails(executionId, userId, cursor, limit, capabilityType, status),
                requestId(request)
        );
    }

    @GetMapping("/audit-trails/{auditId}")
    public ApiResponse<?> auditTrailDetail(
            @PathVariable String auditId,
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(executionService.auditDetail(auditId, userId), requestId(request));
    }

    @GetMapping(value = "/executions/{executionId}/audit-trails/download", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadExecutionAudits(
            @PathVariable String executionId,
            @RequestHeader("X-User-Id") String userId
    ) {
        // 与日志下载相同：审计导出采用 JSON 流，不给调用链设置隐藏的条数上限。
        StreamingResponseBody body = outputStream -> {
            String cursor = null;
            boolean first = true;
            outputStream.write('[');
            do {
                var page = executionService.auditTrails(executionId, userId, cursor, 200, "", "");
                for (var audit : page.items()) {
                    if (!first) outputStream.write(',');
                    objectMapper.writeValue(outputStream, audit);
                    first = false;
                }
                outputStream.flush();
                cursor = page.nextCursor();
            } while (cursor != null);
            outputStream.write(']');
        };
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=plugin-execution-" + executionId + "-audit.json")
                .body(body);
    }

    @GetMapping("/{pluginId}/execution-stats")
    public ApiResponse<?> executionStats(
            @PathVariable String pluginId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                executionService.stats(pluginId, userId, spaceId),
                requestId(request)
        );
    }

    private static String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value == null ? "" : value;
    }
}
