package org.project.plugin.controller;

import lombok.RequiredArgsConstructor;
import org.project.plugin.config.PluginProperties;
import org.project.plugin.service.PluginMarketplaceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** 管理端市场审核入口；只允许服务网络携带共享凭证调用。 */
@RestController
@RequestMapping("/internal/v1/marketplace/plugins")
@RequiredArgsConstructor
public class InternalMarketplaceReviewController {
    private final PluginMarketplaceService service;
    private final PluginProperties properties;

    @PostMapping("/{pluginId}/review")
    public Map<String, Object> review(
            @PathVariable String pluginId,
            @RequestParam String status,
            @RequestHeader(value = "X-PCD-Service-Token", required = false) String token
    ) {
        requireToken(token);
        service.review(pluginId, status);
        return Map.of("plugin_id", pluginId, "review_status", status);
    }

    private void requireToken(String token) {
        String expected = properties.internalServiceToken();
        if (expected == null || expected.isBlank() || token == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部服务认证失败");
        }
    }
}
