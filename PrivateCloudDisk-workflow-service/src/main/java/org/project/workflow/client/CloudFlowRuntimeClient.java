package org.project.workflow.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.project.workflow.config.WorkflowProperties;
import org.project.workflow.model.WorkflowModels.ValidationIssue;
import org.project.workflow.model.WorkflowModels.ValidationReport;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CloudFlow Runtime 编译适配器。
 *
 * <p>改动点（CLOUDFLOW-RUNTIME-001）：原 Workflow Service 内部维护了一套正则/行解析器；
 * 现在 Java 仅负责身份上下文和响应投影，语法、AST、DAG 与错误诊断统一由 Rust Runtime
 * 完成，避免控制面与执行面出现两套语言语义。</p>
 */
@Component
public final class CloudFlowRuntimeClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalMapper;

    public CloudFlowRuntimeClient(RestClient.Builder builder, WorkflowProperties properties, ObjectMapper objectMapper) {
        this.client = builder.clone().baseUrl(properties.cloudflowRuntimeUrl()).build();
        this.objectMapper = objectMapper;
        this.canonicalMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public ValidationReport compile(String source, String userId, String spaceId, String filename) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("source", source);
        request.put("filename", filename == null || filename.isBlank() ? "workflow.flow" : filename);
        request.put("userId", userId);
        request.put("spaceId", spaceId);
        try {
            JsonNode body = client.post()
                    .uri("/internal/v1/cloudflow/compile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((requestMessage, response) -> objectMapper.readTree(response.getBody()));
            return toReport(body);
        } catch (RestClientException | JsonProcessingException exception) {
            return new ValidationReport(false, List.of(new ValidationIssue(
                    "CF-RUNTIME-UNAVAILABLE", "runtime", "CloudFlow Runtime 编译服务暂不可用，请稍后重试"
            )), Map.of(), "");
        }
    }

    @SuppressWarnings("unchecked")
    private ValidationReport toReport(JsonNode body) throws JsonProcessingException {
        List<ValidationIssue> issues = new ArrayList<>();
        for (JsonNode diagnostic : body.path("diagnostics")) {
            String code = diagnostic.path("code").asText("CF1201");
            JsonNode location = diagnostic.path("location");
            String path = location.isMissingNode() ? "workflow" : "line[" + location.path("line").asInt(1) + "]:" + location.path("column").asInt(1);
            issues.add(new ValidationIssue(code, path, diagnostic.path("message").asText("CloudFlow 编译失败")));
        }
        JsonNode ir = body.path("ir");
        Map<String, Object> normalized = ir.isMissingNode() || ir.isNull()
                ? Map.of()
                : objectMapper.convertValue(ir, Map.class);
        // 兼容旧检查点读取器：执行面迁移期间只投影 Runtime IR graph.nodes，不再解析 DSL。
        if (!normalized.isEmpty()) {
            Map<String, Object> spec = (Map<String, Object>) normalized.getOrDefault("spec", Map.of());
            Map<String, Object> graph = (Map<String, Object>) spec.getOrDefault("graph", Map.of());
            normalized = new LinkedHashMap<>(normalized);
            normalized.put("steps", legacySteps(graph.get("nodes")));
            normalized.put("trigger", spec.getOrDefault("trigger", Map.of()));
        }
        return new ValidationReport(body.path("valid").asBoolean(false) && issues.isEmpty(), List.copyOf(issues), normalized, sha256(normalized));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> legacySteps(Object rawNodes) {
        if (!(rawNodes instanceof List<?> nodes)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object rawNode : nodes) {
            if (!(rawNode instanceof Map<?, ?> raw)) continue;
            Map<String, Object> node = (Map<String, Object>) raw;
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("id", node.get("id"));
            step.put("name", node.getOrDefault("name", node.get("id")));
            step.put("needs", node.getOrDefault("dependsOn", List.of()));
            step.put("with", node.getOrDefault("inputs", Map.of()));
            step.put("if", node.get("condition"));
            Object actionValue = node.get("action");
            if (actionValue instanceof Map<?, ?> action) {
                String provider = String.valueOf(action.get("provider") == null ? "builtin" : action.get("provider"));
                String capability;
                if ("plugin".equals(provider)) {
                    capability = "plugin:" + value(action, "pluginId") + ":" + value(action, "function");
                } else {
                    capability = provider + ":" + value(action, "service") + "." + value(action, "method");
                }
                step.put("uses", capability);
                step.put("with", action.get("arguments") == null ? Map.of() : action.get("arguments"));
            }
            Object retry = node.get("retry");
            if (retry instanceof Map<?, ?> value) step.put("retry", Map.of("maxAttempts", value.get("maxAttempts") == null ? 1 : value.get("maxAttempts"), "strategy", value.get("strategy") == null ? "fixed" : value.get("strategy")));
            result.add(step);
        }
        return result;
    }

    private String value(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String sha256(Object value) {
        try {
            byte[] bytes = canonicalMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            return "";
        }
    }
}
