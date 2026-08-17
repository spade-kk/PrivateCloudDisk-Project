package org.project.automation.client;

import lombok.RequiredArgsConstructor;
import org.project.automation.config.AutomationProperties;
import org.project.automation.model.EntrypointMatch;
import org.project.automation.model.LifecycleEvent;
import org.project.automation.model.RuntimeChainResult;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Plugin Runtime 内部执行客户端；调用发生在 MQ Worker，不阻塞任何用户 HTTP 请求。 */
@Component
@RequiredArgsConstructor
public class PluginRuntimeClient {
    private final RestClient.Builder restClientBuilder;
    private final AutomationProperties properties;

    public RuntimeChainResult executePreprocessChain(
            LifecycleEvent event,
            List<EntrypointMatch> entrypoints
    ) {
        Map<String, Object> request = Map.of(
                "execution_id", java.util.UUID.randomUUID().toString(),
                "event", event,
                "entrypoints", entrypoints,
                "deadline_at", event.data().path("preprocess_deadline_at").asText(),
                "content_lease_ref", event.data().path("content_lease_ref").asText()
        );
        RuntimeChainResult result = restClientBuilder
                .baseUrl(properties.runtimeServiceUrl())
                .build()
                .post()
                .uri("/internal/v1/executions/preprocess-chain")
                .body(request)
                .retrieve()
                .body(RuntimeChainResult.class);
        if (result == null) {
            throw new IllegalStateException("Runtime 未返回执行结果");
        }
        return result;
    }

    public RuntimeChainResult executePostAvailableChain(
            LifecycleEvent event,
            List<EntrypointMatch> entrypoints
    ) {
        Map<String, Object> request = Map.of(
                "execution_id", java.util.UUID.randomUUID().toString(),
                "event", event,
                "entrypoints", entrypoints
        );
        RuntimeChainResult result = restClientBuilder
                .baseUrl(properties.runtimeServiceUrl())
                .build()
                .post()
                .uri("/internal/v1/executions/post-available-chain")
                .body(request)
                .retrieve()
                .body(RuntimeChainResult.class);
        if (result == null) {
            throw new IllegalStateException("Runtime 未返回后处理执行结果");
        }
        return result;
    }
}
