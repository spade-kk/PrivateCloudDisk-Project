package org.project.plugin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** 插件生命周期公开 API；身份由网关注入，空间 ID 只从请求头读取。 */
@Validated
@RestController
@RequestMapping("/plugins")
@RequiredArgsConstructor
public class PluginController {
    private final PluginManagementService service;
    private final PluginExecutionService executionService;

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
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                executionService.list(pluginId, userId, status, page, size),
                requestId(request)
        );
    }

    @GetMapping("/{pluginId}/execution-stats")
    public ApiResponse<?> executionStats(
            @PathVariable String pluginId,
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                executionService.stats(pluginId, userId),
                requestId(request)
        );
    }

    private static String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value == null ? "" : value;
    }
}
