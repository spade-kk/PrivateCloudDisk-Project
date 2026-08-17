package org.project.plugin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.project.plugin.config.PluginProperties;
import org.project.plugin.exception.PluginApiException;
import org.project.plugin.model.CapabilityResolveRequest;
import org.project.plugin.service.CapabilityResolutionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Workflow Service 私网解析插件能力；不配置公网 Gateway 路由。 */
@RestController
@RequestMapping("/internal/v1/capabilities")
@RequiredArgsConstructor
public class InternalCapabilityController {
    private final CapabilityResolutionService service;
    private final PluginProperties properties;

    @PostMapping("/resolve")
    public Map<String, Object> resolve(
            @RequestHeader(value = "X-PCD-Service-Token", required = false) String token,
            @Valid @RequestBody CapabilityResolveRequest request
    ) {
        requireServiceToken(token);
        return service.resolve(request);
    }

    private void requireServiceToken(String token) {
        String expected = properties.internalServiceToken();
        if (expected == null || expected.isBlank()
                || token == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            throw new PluginApiException(
                    "AUTH-UNAUTHENTICATED", HttpStatus.UNAUTHORIZED, "内部服务认证失败"
            );
        }
    }
}
