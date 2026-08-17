package org.project.plugin.client;

import lombok.RequiredArgsConstructor;
import org.project.plugin.config.PluginProperties;
import org.project.plugin.exception.PluginApiException;
import org.project.plugin.model.PluginTestRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

/** 通过内部令牌向 Runtime 提交异步测试任务；Plugin Service 不执行用户脚本。 */
@Component
@RequiredArgsConstructor
public class RuntimeTestExecutionClient {
    private final RestClient.Builder restClientBuilder;
    private final PluginProperties properties;

    public Map<String, Object> create(
            String executionId,
            String pluginId,
            String versionId,
            String userId,
            String spaceId,
            PluginTestRequest request
    ) {
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("plugin_id", pluginId);
            body.put("execution_id", executionId);
            body.put("version_id", versionId);
            body.put("user_id", userId);
            if (spaceId != null && !spaceId.isBlank()) body.put("space_id", spaceId);
            body.put("test_entrypoint", request.testEntrypoint());
            body.put("script_entry", request.scriptEntry());
            body.put("parameters", request.parameters() == null ? Map.of() : request.parameters());
            return restClientBuilder.clone().baseUrl(properties.runtimeUrl()).build()
                    .post().uri("/internal/v1/test-executions")
                    // [SEC-RUNTIME-AUTH-001] 测试任务同样属于内部接口，必须携带服务令牌。
                    .header("X-PCD-Service-Token", properties.internalServiceToken())
                    .header("Idempotency-Key", executionId)
                    .body(body).retrieve().body(Map.class);
        } catch (RestClientException exception) {
            throw new PluginApiException(
                    "PLG-TEST-RUNTIME-UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                    "插件测试运行时暂时不可用，请稍后重试"
            );
        }
    }

    public Map<String, Object> get(String executionId) {
        try {
            return restClientBuilder.clone().baseUrl(properties.runtimeUrl()).build()
                    .get().uri("/internal/v1/test-executions/{id}", executionId)
                    .header("X-PCD-Service-Token", properties.internalServiceToken())
                    .retrieve().body(Map.class);
        } catch (RestClientException exception) {
            throw new PluginApiException("PLG-TEST-RUNTIME-UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                    "插件测试状态暂时不可用，请稍后重试");
        }
    }

    public Map<String, Object> cancel(String executionId) {
        try {
            return restClientBuilder.clone().baseUrl(properties.runtimeUrl()).build()
                    .post().uri("/internal/v1/test-executions/{id}/cancel", executionId)
                    .header("X-PCD-Service-Token", properties.internalServiceToken())
                    .retrieve().body(Map.class);
        } catch (RestClientException exception) {
            throw new PluginApiException("PLG-TEST-RUNTIME-UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                    "插件测试取消服务暂时不可用，请稍后重试");
        }
    }
}
