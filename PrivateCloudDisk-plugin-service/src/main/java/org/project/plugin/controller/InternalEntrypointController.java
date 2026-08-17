package org.project.plugin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.project.plugin.config.PluginProperties;
import org.project.plugin.model.EntrypointMatchRequest;
import org.project.plugin.model.EntrypointMatchResponse;
import org.project.plugin.service.EntrypointMatchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Automation Service 专用入口目录；禁止配置公网 Gateway 路由。 */
@RestController
@RequestMapping("/internal/v1/entrypoints")
@RequiredArgsConstructor
public class InternalEntrypointController {
    private final EntrypointMatchService matchService;
    private final PluginProperties properties;

    @PostMapping("/match")
    public List<EntrypointMatchResponse> match(
            @RequestHeader(value = "X-PCD-Service-Token", required = false) String token,
            @Valid @RequestBody EntrypointMatchRequest request
    ) {
        verifyServiceToken(token);
        return matchService.match(request);
    }

    private void verifyServiceToken(String token) {
        String expected = properties.internalServiceToken();
        if (expected == null || expected.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "内部服务认证尚未配置"
            );
        }
        if (token == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部服务认证失败");
        }
    }
}

