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
import java.util.LinkedHashMap;
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
            // [PLUGIN-EXEC-OBS-001] 原行为只写执行摘要，导致插件中心无法回看 Runtime 真实日志。
            // 新行为在摘要成功落库后，通过同一受信通道写入已脱敏日志与调用审计；写入失败
            // 仍不反向阻塞文件生命周期，与既有可靠性边界保持一致。
            recordObservability(event, records, result, endedAt);
        } catch (Exception exception) {
            log.error("插件执行摘要写入失败 eventId={}，不阻塞文件生命周期",
                    event.id(), exception);
        }
    }

    private void recordObservability(
            LifecycleEvent event,
            List<Map<String, Object>> records,
            RuntimeChainResult result,
            Instant endedAt
    ) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (Map<String, Object> record : records) {
            String executionId = String.valueOf(record.get("execution_id"));
            Map<String, Object> observation = new LinkedHashMap<>();
            observation.put("execution_id", executionId);
            observation.put("observation_id", UUID.nameUUIDFromBytes((
                    executionId + ":" + result.status() + ":" + (result.logs() == null ? 0 : result.logs().hashCode())
            ).getBytes(StandardCharsets.UTF_8)).toString());
            observation.put("logs", logLines(result.logs(), endedAt));
            observation.put("audit_trails", auditRecords(event, executionId, result, endedAt));
            payload.add(observation);
        }
        try {
            restClientBuilder.clone()
                    .baseUrl(properties.pluginServiceUrl())
                    .build()
                    .post()
                    .uri("/internal/v1/executions/observability/batch")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            log.error("插件执行可观测性写入失败 eventId={}，不阻塞文件生命周期", event.id(), exception);
        }
    }

    private static List<Map<String, Object>> logLines(String logs, Instant timestamp) {
        if (logs == null || logs.isBlank()) return List.of();
        List<Map<String, Object>> lines = new ArrayList<>();
        long offset = 0;
        for (String raw : logs.split("\\R")) {
            if (raw.isBlank()) {
                offset++;
                continue;
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("timestamp", timestamp.toString());
            line.put("byte_offset", offset);
            if (raw.startsWith("{\"level\":") || raw.contains("\"fields\":{\"")) {
                line.put("source", "PYCLOUDSDK");
                line.put("level", raw.contains("\"level\":\"error\"") ? "ERROR"
                        : raw.contains("\"level\":\"warning\"") ? "WARN" : "INFO");
            } else if (raw.startsWith("plugin_error=")) {
                line.put("source", "STDERR");
                line.put("level", "ERROR");
            } else {
                line.put("source", "STDOUT");
                line.put("level", "INFO");
            }
            line.put("message", raw);
            lines.add(line);
            offset += raw.length() + 1L;
        }
        return lines;
    }

    private static List<Map<String, Object>> auditRecords(
            LifecycleEvent event, String executionId, RuntimeChainResult result, Instant timestamp
    ) {
        List<Map<String, Object>> records = new ArrayList<>();
        // Runtime 低版本不会返回 audit_trails；保留真实的 Runtime 容器执行事实作为审计根节点，
        // 绝不在前端伪造能力调用。新 Runtime 返回的 SDK 调用记录会作为同一数据源的后续节点入库。
        Map<String, Object> root = new LinkedHashMap<>();
		// [PLUGIN-EXEC-OBS-001] 稳定根 ID 让前端可依据 parent_audit_id 还原能力调用链。
		// 旧行为没有调用树；新行为不改变执行语义，仅补足已发生调用的可观测关联。
        String rootAuditId = UUID.nameUUIDFromBytes((executionId + ":runtime-root")
                .getBytes(StandardCharsets.UTF_8)).toString();
        root.put("audit_id", rootAuditId);
        root.put("capability_key", "runtime.sandbox.execute");
        root.put("capability_type", "BUILTIN");
        root.put("summary_template", "runtime.execute");
        root.put("target_context", Map.of("event", event.type(), "space_id", event.spaceId() == null ? "" : event.spaceId()));
        root.put("input_params", Map.of("trigger_event", event.type(), "event_id", event.id()));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("content_modified", result.contentModified());
        output.put("completed_entrypoints", result.completedEntrypoints());
        if (result.output() != null) output.put("result", result.output());
        root.put("output_result", output);
        root.put("status", toAuditStatus(result.status()));
        root.put("duration_ms", 0);
        root.put("timestamp", timestamp.toString());
        root.put("error_code", result.failureCode() == null ? "" : result.failureCode());
        root.put("error_summary", result.failureSummary() == null ? "" : result.failureSummary());
        records.add(root);
        if (result.auditTrails() == null) return records;
        for (var item : result.auditTrails()) {
            if (item == null || item.capabilityKey() == null || item.capabilityKey().isBlank()) continue;
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("audit_id", item.auditId() == null ? "" : item.auditId());
            mapped.put("parent_audit_id", item.parentAuditId() == null || item.parentAuditId().isBlank()
                    ? rootAuditId : item.parentAuditId());
            mapped.put("capability_key", item.capabilityKey());
            mapped.put("capability_type", item.capabilityType() == null ? "PLATFORM_API" : item.capabilityType());
            mapped.put("summary_template", item.summaryTemplate() == null ? "" : item.summaryTemplate());
            mapped.put("target_context", item.targetContext() == null ? Map.of() : item.targetContext());
            mapped.put("input_params", item.inputParams() == null ? Map.of() : item.inputParams());
            mapped.put("output_result", item.outputResult() == null ? Map.of() : item.outputResult());
            mapped.put("status", item.status() == null ? "FAILED" : item.status());
            mapped.put("duration_ms", item.durationMs() == null ? 0 : item.durationMs());
            mapped.put("retry_count", item.retryCount() == null ? 0 : item.retryCount());
            mapped.put("error_code", item.errorCode() == null ? "" : item.errorCode());
            mapped.put("error_summary", item.errorSummary() == null ? "" : item.errorSummary());
            mapped.put("timestamp", item.timestamp() == null ? timestamp.toString() : item.timestamp());
            records.add(mapped);
        }
        return records;
    }

    private static String toAuditStatus(String status) {
        if ("success".equals(status)) return "SUCCESS";
        if ("timeout".equals(status)) return "TIMEOUT";
        if ("skipped".equals(status)) return "SKIPPED";
        return "FAILED";
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
