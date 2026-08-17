package org.project.plugin.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.project.plugin.config.PluginProperties;
import org.project.plugin.exception.PluginApiException;
import org.project.plugin.service.InternalPackageService;
import org.project.plugin.storage.PluginPackageHandle;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Runtime 私网包分发接口；不配置 Gateway 路由。 */
@RestController
@RequestMapping("/internal/v1/packages")
@RequiredArgsConstructor
public class InternalPackageController {
    private final InternalPackageService service;
    private final PluginProperties properties;

    @GetMapping("/{versionId}")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable String versionId,
            @RequestHeader(value = "X-PCD-Service-Token", required = false) String token,
            HttpServletResponse response
    ) {
        requireServiceToken(token);
        PluginPackageHandle handle = service.open(versionId);
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

    private void requireServiceToken(String token) {
        String expected = properties.internalServiceToken();
        if (expected == null || expected.isBlank()
                || token == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            throw new PluginApiException(
                    "AUTH-UNAUTHENTICATED",
                    HttpStatus.UNAUTHORIZED,
                    "内部服务认证失败"
            );
        }
    }
}
