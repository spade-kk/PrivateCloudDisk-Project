package org.project.plugin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.project.plugin.config.PluginProperties;
import org.project.plugin.model.ExecutionObservabilityRequest;
import org.project.plugin.model.ExecutionRecordRequest;
import org.project.plugin.service.PluginExecutionService;
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
import java.util.Map;

/** Automation/客户端 Broker 使用的执行摘要入口；完整日志不得通过该接口上传。 */
@RestController
@RequestMapping("/internal/v1/executions")
@RequiredArgsConstructor
public class InternalExecutionController {
    private final PluginExecutionService service;
    private final PluginProperties properties;

    @PostMapping("/batch")
    public Map<String, Object> record(
            @RequestHeader(value = "X-PCD-Service-Token", required = false) String token,
            @Valid @RequestBody List<@Valid ExecutionRecordRequest> requests
    ) {
        requireToken(token);
        if (requests.isEmpty() || requests.size() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "执行摘要批量大小必须为 1-100"
            );
        }
        service.record(requests);
        return Map.of("accepted", requests.size());
    }

    /**
     * [PLUGIN-EXEC-OBS-001] 仅 Automation/Runtime 等受信服务可写入完整脱敏日志和审计。
     * 原有 /batch 继续只承接摘要，避免任何公开路径意外扩大日志数据面。
     */
    @PostMapping("/observability/batch")
    public Map<String, Object> recordObservability(
            @RequestHeader(value = "X-PCD-Service-Token", required = false) String token,
            @Valid @RequestBody List<@Valid ExecutionObservabilityRequest> requests
    ) {
        requireToken(token);
        if (requests.isEmpty() || requests.size() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "可观测性批量大小必须为 1-100"
            );
        }
        requests.forEach(service::recordObservability);
        return Map.of("accepted", requests.size());
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
