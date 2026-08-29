package org.project.automation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.automation.client.PluginCatalogClient;
import org.project.automation.client.PluginRuntimeClient;
import org.project.automation.client.PluginExecutionClient;
import org.project.automation.model.EntrypointMatch;
import org.project.automation.model.FileAvailableEventAdapter;
import org.project.automation.model.LifecycleEvent;
import org.project.automation.model.RuntimeChainResult;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * file.content.ready 自动化编排。
 *
 * <p>所有终态都会创建 file.content.processed Outbox。目录/Runtime 异常不会抛回核心
 * 文件流水线，而是生成 failed/timeout 结果，让 Storage 选择 original 并继续。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationExecutionService {
    private static final String READY_TYPE = "pcd.file.content.ready.v1";
    private static final Set<String> ALLOWED_RESULT = Set.of("success", "skipped", "failed", "timeout");

    private final ObjectMapper objectMapper;
    private final AutomationPersistenceService persistenceService;
    private final PluginCatalogClient pluginCatalogClient;
    private final PluginRuntimeClient runtimeClient;
    private final PluginExecutionClient executionClient;

    public ClaimOutcome processRaw(String payloadJson) throws Exception {
        LifecycleEvent event = objectMapper.readValue(payloadJson, LifecycleEvent.class);
        validateReadyEvent(event);
        String payloadHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(payloadJson.getBytes(StandardCharsets.UTF_8))
        );
        ClaimOutcome claim = persistenceService.claim(
                event.id(), event.type(), payloadHash, payloadJson
        );
        if (claim != ClaimOutcome.CLAIMED) {
            return claim;
        }

        Instant startedAt = Instant.now();
        List<EntrypointMatch> matches;
        RuntimeChainResult result;
        try {
            matches = pluginCatalogClient.matchPreprocess(event);
            if (matches.isEmpty()) {
                result = new RuntimeChainResult(
                        "skipped", false, null, null, null,
                        0, null, "未匹配内容预处理入口", null, null, List.of()
                );
            } else if (matches.stream().anyMatch(entry ->
                    entry.permissions() == null
                            || !entry.permissions().contains("file.content.write_pre_activation"))) {
                result = new RuntimeChainResult(
                        "failed", false, null, null, null,
                        0, "ENTRYPOINT_PERMISSION_INVALID",
                        "预处理入口未声明激活前内容写权限", null, null, List.of()
                );
            } else {
                result = runtimeClient.executePreprocessChain(event, matches);
            }
        } catch (ResourceAccessException timeoutOrUnavailable) {
            matches = List.of();
            boolean timeout = timeoutOrUnavailable.getMessage() != null
                    && timeoutOrUnavailable.getMessage().toLowerCase().contains("timed out");
            result = new RuntimeChainResult(
                    timeout ? "timeout" : "failed",
                    false, null, null, null, 0,
                    timeout ? "PLUGIN_RUNTIME_TIMEOUT" : "AUTOMATION_DEPENDENCY_UNAVAILABLE",
                    ErrorSanitizer.summarize(timeoutOrUnavailable), null, null, List.of()
            );
        } catch (Exception exception) {
            matches = List.of();
            log.error("预处理自动化执行失败 eventId={} gateId={}",
                    event.id(), event.data().path("gate_id").asText(), exception);
            result = new RuntimeChainResult(
                    "failed", false, null, null, null, 0,
                    "AUTOMATION_EXECUTION_FAILED", ErrorSanitizer.summarize(exception), null, null, List.of()
            );
        }

        String normalizedStatus = ALLOWED_RESULT.contains(result.status())
                ? result.status() : "failed";
        executionClient.record(event, matches, result, startedAt, Instant.now());
        String processedPayload = buildProcessedEvent(event, result, normalizedStatus, matches.size());
        persistenceService.complete(
                event.id(),
                event.data().path("gate_id").asText(),
                event.data().path("backend_task_id").asText(),
                event.actorUserId(),
                event.spaceId(),
                matches.size(),
                result.completedEntrypoints(),
                normalizedStatus,
                result.failureSummary(),
                processedPayload
        );
        return ClaimOutcome.CLAIMED;
    }

    public ClaimOutcome processAvailableRaw(String payloadJson) throws Exception {
        LifecycleEvent event = FileAvailableEventAdapter.adapt(payloadJson, objectMapper);
        String payloadHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(payloadJson.getBytes(StandardCharsets.UTF_8))
        );
        ClaimOutcome claim = persistenceService.claim(
                event.id(), event.type(), payloadHash, payloadJson
        );
        if (claim != ClaimOutcome.CLAIMED) {
            return claim;
        }

        Instant startedAt = Instant.now();
        List<EntrypointMatch> matches = List.of();
        RuntimeChainResult result;
        try {
            matches = pluginCatalogClient.matchAvailable(event);
            if (matches.isEmpty()) {
                result = new RuntimeChainResult(
                        "skipped", false, null, null, null,
                        0, null, "未匹配激活后入口", null, null, List.of()
                );
            } else {
                result = runtimeClient.executePostAvailableChain(event, matches);
            }
        } catch (Exception exception) {
            log.error("激活后自动化执行失败 eventId={} fileId={}",
                    event.id(), event.data().path("file_id").asText(), exception);
            result = new RuntimeChainResult(
                    "failed", false, null, null, null, 0,
                    "POST_AVAILABLE_EXECUTION_FAILED", ErrorSanitizer.summarize(exception), null, null, List.of()
            );
        }
        executionClient.record(event, matches, result, startedAt, Instant.now());
        persistenceService.completeWithoutOutbox(
                event.id(),
                event.actorUserId(),
                event.spaceId(),
                "pcd.file.available.v1",
                matches.size(),
                result.completedEntrypoints(),
                result.status(),
                result.failureSummary()
        );
        return ClaimOutcome.CLAIMED;
    }

    private void validateReadyEvent(LifecycleEvent event) {
        if (!READY_TYPE.equals(event.type())) {
            throw new IllegalArgumentException("不支持的事件类型: " + event.type());
        }
        if (event.id() == null
                || event.data() == null
                || event.data().path("gate_id").asText().isBlank()
                || event.data().path("backend_task_id").asText().isBlank()
                || event.data().path("content_lease_ref").asText().isBlank()) {
            throw new IllegalArgumentException("file.content.ready 缺少必填字段");
        }
    }

    private String buildProcessedEvent(
            LifecycleEvent ready,
            RuntimeChainResult result,
            String status,
            int matched
    ) throws Exception {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("gate_id", ready.data().path("gate_id").asText());
        data.put("backend_task_id", ready.data().path("backend_task_id").asText());
        data.put("ready_event_id", ready.id());
        data.put("status", status);
        data.put("content_modified", "success".equals(status) && result.contentModified());
        if ("success".equals(status) && result.contentModified()) {
            data.put("candidate_id", result.candidateId());
            data.put("candidate_checksum", result.candidateChecksum());
            if (result.candidateSize() != null) {
                data.put("candidate_size", result.candidateSize());
            }
        }
        data.put("matched_entrypoints", matched);
        data.put("completed_entrypoints", result.completedEntrypoints());
        if (result.failureCode() != null) {
            data.put("failure_code", result.failureCode());
        }
        if (result.failureSummary() != null) {
            data.put("failure_summary",
                    result.failureSummary().substring(0, Math.min(1000, result.failureSummary().length())));
        }
        data.put("finished_at", Instant.now().toString());

        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("specversion", "1.0");
        envelope.put("id", UUID.randomUUID().toString());
        envelope.put("source", "pcd.automation-service");
        envelope.put("type", "pcd.file.content.processed.v1");
        envelope.put("subject", ready.subject());
        envelope.put("time", Instant.now().toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("schema_version", 1);
        envelope.put("actor_user_id", ready.actorUserId());
        if (ready.spaceId() == null) {
            envelope.putNull("space_id");
        } else {
            envelope.put("space_id", ready.spaceId());
        }
        envelope.put("correlation_id", ready.correlationId());
        envelope.put("causation_id", ready.id());
        envelope.set("data", data);
        return objectMapper.writeValueAsString(envelope);
    }
}
