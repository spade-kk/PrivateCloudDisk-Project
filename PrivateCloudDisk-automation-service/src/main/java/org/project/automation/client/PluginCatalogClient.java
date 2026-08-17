package org.project.automation.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.project.automation.config.AutomationProperties;
import org.project.automation.model.EntrypointMatch;
import org.project.automation.model.LifecycleEvent;
import org.springframework.stereotype.Component;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Plugin Service 内部目录客户端。
 *
 * <p>Plugin Service 必须完成空间安装、成员实时权限、事件条件和版本撤销校验；
 * Automation 仍会再次校验事件作用域权限，形成纵深防御。</p>
 */
@Component
@RequiredArgsConstructor
public class PluginCatalogClient {
    private final RestClient.Builder restClientBuilder;
    private final AutomationProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * REQ-WORKER-TASKBUS-2026-07：目录匹配使用独立短超时。
     * 原行为复用默认客户端，插件服务未启动时会等待较长连接/读取超时；新行为在 500ms
     * 默认窗口内返回失败，由 Automation 将本次预处理标记为无匹配并让 Storage Gate 继续。
     */
    private RestClient catalogClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofMillis(properties.triggerMatchTimeoutMs()));
        return restClientBuilder.clone()
                .baseUrl(properties.pluginServiceUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public List<EntrypointMatch> matchPreprocess(LifecycleEvent event) {
        Map<String, Object> request = Map.of(
                "event_type", event.type(),
                "actor_user_id", event.actorUserId(),
                "space_id", event.spaceId() == null ? "" : event.spaceId(),
                "file", objectMapper.convertValue(event.data(), new TypeReference<Map<String, Object>>() {})
        );
        String response = catalogClient()
                .post()
                .uri("/internal/v1/entrypoints/match")
                .body(request)
                .retrieve()
                .body(String.class);
        try {
            return objectMapper.readValue(
                    response == null ? "[]" : response,
                    new TypeReference<List<EntrypointMatch>>() {}
            );
        } catch (Exception exception) {
            throw new IllegalStateException("插件入口目录响应格式非法", exception);
        }
    }

    public List<EntrypointMatch> matchAvailable(LifecycleEvent event) {
        return matchPreprocess(event);
    }
}
