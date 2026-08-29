package org.project.automation.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.automation.config.AutomationProperties;
import org.project.automation.model.EntrypointMatch;
import org.project.automation.model.LifecycleEvent;
import org.project.automation.model.RuntimeChainResult;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Plugin Runtime 内部执行客户端；调用发生在 MQ Worker，不阻塞任何用户 HTTP 请求。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginRuntimeClient {
    private final RestClient.Builder restClientBuilder;
    private final AutomationProperties properties;
    private final ObjectMapper objectMapper;

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
        logResult("preprocess-chain", result);
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
        logResult("post-available-chain", result);
        return result;
    }

    /** 把 Runtime 回传的执行结果（含 output/logs，均已在 Runtime 侧脱敏）落到本服务日志，便于控制面追踪执行面。 */
    private void logResult(String path, RuntimeChainResult result) {
        if (!log.isDebugEnabled()) {
            return;
        }
        String output = "";
        if (result.output() != null && !result.output().isEmpty()) {
            try {
                output = ", output=" + abbrev(objectMapper.writeValueAsString(result.output()), 2000);
            } catch (Exception ignored) {
                output = "";
            }
        }
        String logs = result.logs() == null ? "" : result.logs().trim();
        log.debug("Runtime {} 执行结果 status={} completed={} failure_code={}{}{}",
                path, result.status(), result.completedEntrypoints(),
                result.failureCode() == null ? "" : result.failureCode(),
                output,
                logs.isBlank() ? "" : ", logs=" + abbrev(logs, 2000));
    }

    private static String abbrev(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...(截断)";
    }
}
