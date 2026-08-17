package org.project.automation.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.automation.config.AutomationProperties;
import org.project.automation.model.EntrypointMatch;
import org.project.automation.model.LifecycleEvent;
import org.project.automation.model.RuntimeChainResult;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 向 Plugin Service 写入每个入口的脱敏执行摘要；失败不反向阻塞文件主链。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginExecutionClient {
    private final RestClient.Builder restClientBuilder;
    private final AutomationProperties properties;

    public void record(
            LifecycleEvent event,
            List<EntrypointMatch> matches,
            RuntimeChainResult result,
            Instant startedAt,
            Instant endedAt
    ) {
        if (matches.isEmpty()) {
            return;
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (int index = 0; index < matches.size(); index++) {
            EntrypointMatch entrypoint = matches.get(index);
            String status = statusAt(index, result);
            String stableKey = event.id() + ":" + entrypoint.installationId();
            records.add(Map.ofEntries(
                    Map.entry("execution_id", UUID.nameUUIDFromBytes(
                            stableKey.getBytes(StandardCharsets.UTF_8)).toString()),
                    Map.entry("plugin_id", entrypoint.pluginId()),
                    Map.entry("version_id", entrypoint.versionId()),
                    Map.entry("installation_id", entrypoint.installationId()),
                    Map.entry("user_id", event.actorUserId()),
                    Map.entry("space_id", event.spaceId() == null ? "" : event.spaceId()),
                    Map.entry("client_id", ""),
                    Map.entry("trigger_event", event.type()),
                    Map.entry("trigger_source", "EVENT"),
                    Map.entry("status", status),
                    Map.entry("started_at", startedAt.toString()),
                    Map.entry("ended_at", endedAt.toString()),
                    Map.entry("output_summary", summary(result, status)),
                    Map.entry("error_code", result.failureCode() == null ? "" : result.failureCode()),
                    Map.entry("correlation_id",
                            event.correlationId() == null ? "" : event.correlationId()),
                    Map.entry("causation_id", event.id())
            ));
        }
        try {
            restClientBuilder.clone()
                    .baseUrl(properties.pluginServiceUrl())
                    .build()
                    .post()
                    .uri("/internal/v1/executions/batch")
                    .body(records)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            log.error("插件执行摘要写入失败 eventId={}，不阻塞文件生命周期",
                    event.id(), exception);
        }
    }

    private static String statusAt(int index, RuntimeChainResult result) {
        if (index < result.completedEntrypoints()) {
            return "SUCCESS";
        }
        if (index == result.completedEntrypoints()) {
            return "timeout".equals(result.status()) ? "TIMEOUT"
                    : "success".equals(result.status()) ? "SUCCESS" : "FAILED";
        }
        return "SKIPPED";
    }

    private static String summary(RuntimeChainResult result, String status) {
        String summary = result.failureSummary();
        if (summary == null || summary.isBlank()) {
            summary = "SUCCESS".equals(status) ? "执行成功" : "未执行";
        }
        return summary.substring(0, Math.min(summary.length(), 4000));
    }
}
