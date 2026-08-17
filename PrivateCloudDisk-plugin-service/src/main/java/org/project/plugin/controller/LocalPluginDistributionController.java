package org.project.plugin.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.project.plugin.model.ApiResponse;
import org.project.plugin.model.ExecutionRecordRequest;
import org.project.plugin.service.PluginExecutionService;
import org.project.plugin.service.LocalPluginDistributionService;
import org.project.plugin.service.PluginPackageSigner;
import org.project.plugin.storage.PluginPackageHandle;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;

/** 本地插件可信分发公开 API；JWT 与设备签名均由网关前置验证。 */
@RestController
@RequestMapping("/plugins/local")
@RequiredArgsConstructor
public class LocalPluginDistributionController {
    private final LocalPluginDistributionService service;
    private final PluginPackageSigner signer;
    private final PluginExecutionService executionService;

    @GetMapping("/distribution")
    public ApiResponse<?> distribution(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestHeader("X-Client-ID") String clientId,
            @RequestParam String platform,
            @RequestParam("client_type") String clientType,
            @RequestParam("app_version") String appVersion,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                service.distribute(
                        userId, spaceId, clientId, platform, clientType, appVersion
                ),
                requestId(request)
        );
    }

    @GetMapping("/packages/{grantToken}")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable String grantToken,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Client-ID") String clientId
    ) {
        PluginPackageHandle handle = service.consume(grantToken, userId, clientId);
        StreamingResponseBody body = output -> {
            try (handle) {
                handle.inputStream().transferTo(output);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.pcd.plugin+zip"))
                .contentLength(handle.size())
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, no-store")
                .header("X-PCD-Package-SHA256", handle.sha256())
                .header("Content-Disposition", "attachment; filename=\"plugin.pcdpkg\"")
                .body(body);
    }

    @GetMapping("/signing-keys/{keyId}")
    public ApiResponse<?> signingKey(
            @PathVariable String keyId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(Map.of(
                "key_id", keyId,
                "algorithm", "Ed25519",
                "public_key_base64", signer.publicKeyBase64(keyId),
                "canonical_payload", "PCD-PLUGIN-PACKAGE-V1\\n"
                        + "plugin_id\\nversion_id\\nversion\\nsha256\\npackage_size"
        ), requestId(request));
    }

    @PostMapping("/executions")
    public ApiResponse<?> recordExecution(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Client-ID") String clientId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @jakarta.validation.Valid @RequestBody ExecutionRecordRequest body,
            HttpServletRequest request
    ) {
        executionService.recordLocal(body, userId, clientId, spaceId);
        return ApiResponse.ok(Map.of("accepted", true), requestId(request));
    }

    private static String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value == null ? "" : value;
    }
}
