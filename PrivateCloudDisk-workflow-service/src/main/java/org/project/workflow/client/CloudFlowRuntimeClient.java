package org.project.workflow.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.project.workflow.config.CloudFlowRuntimeProperties;
import org.project.workflow.model.WorkflowModels.ValidationIssue;
import org.project.workflow.model.WorkflowModels.ValidationReport;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CloudFlow Runtime 编译适配器。
 *
 * <p>改动点（CLOUDFLOW-RUNTIME-001）：原 Workflow Service 内部维护了一套正则/行解析器；
 * 现在 Java 仅负责身份上下文和响应投影，语法、AST、DAG 与错误诊断统一由 Rust Runtime
 * 完成，避免控制面与执行面出现两套语言语义。</p>
 *
 * <p>改动点（CLOUDFLOW-RUNTIME-002）：编译地址切换为 cloudflow.runtime.compile-url，
 * 增加轻量 fail-closed 熔断。Runtime 连续失败时短期开路，草稿、发布和执行均不能把 DSL
 * 标记为已校验；原业务返回结构保持不变。</p>
 */
@Component
public final class CloudFlowRuntimeClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalMapper;
    private final CloudFlowRuntimeProperties properties;
    private final Clock clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntilMillis = new AtomicLong();

    @Autowired
    public CloudFlowRuntimeClient(
            RestClient.Builder builder,
            CloudFlowRuntimeProperties properties,
            ObjectMapper objectMapper
    ) {
        this(builder, properties, objectMapper, Clock.systemUTC());
    }

    CloudFlowRuntimeClient(
            RestClient.Builder builder,
            CloudFlowRuntimeProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.client = builder.clone().build();
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.canonicalMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public ValidationReport compile(String source, String userId, String spaceId, String filename) {
        long now = clock.millis();
        if (circuitOpenUntilMillis.get() > now) return unavailableReport("CloudFlow Runtime 熔断保护已开启，请稍后重试");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("source", source);
        request.put("filename", filename == null || filename.isBlank() ? "workflow.flow" : filename);
        request.put("target_ir_version", "v1");
        request.put("userId", userId);
        request.put("spaceId", spaceId);
        try {
            JsonNode body = client.post()
                    .uri(properties.compileUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((requestMessage, response) -> objectMapper.readTree(response.getBody()));
            consecutiveFailures.set(0);
            circuitOpenUntilMillis.set(0);
            return toReport(body);
        } catch (RestClientException | JsonProcessingException exception) {
            if (consecutiveFailures.incrementAndGet() >= properties.circuitFailureThreshold()) {
                circuitOpenUntilMillis.set(now + properties.circuitOpenSeconds() * 1000L);
            }
            return unavailableReport("CloudFlow Runtime 编译服务暂不可用，请稍后重试");
        }
    }

    @SuppressWarnings("unchecked")
    private ValidationReport toReport(JsonNode body) throws JsonProcessingException {
        if (body == null || body.isMissingNode()) return unavailableReport("CloudFlow Runtime 返回空响应，请稍后重试");
        List<ValidationIssue> issues = new ArrayList<>();
        for (JsonNode diagnostic : body.path("diagnostics")) {
            String code = diagnostic.path("code").asText("CF1201");
            JsonNode location = diagnostic.path("location");
            int line = location.path("line").asInt(1);
            int column = location.path("column").asInt(1);
            String path = "line[" + line + "]:" + column;
            issues.add(new ValidationIssue(
                    code,
                    path,
                    diagnostic.path("message").asText("CloudFlow 编译失败"),
                    line,
                    column,
                    diagnostic.path("severity").asText("ERROR"),
                    diagnostic.path("category").asText(null),
                    diagnostic.path("cliOutput").asText(null),
                    objectMapper.convertValue(diagnostic.path("suggestions"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)),
                    diagnostic.path("help").asText(null),
                    diagnostic.path("documentationUrl").asText(null)
            ));
        }
        JsonNode ir = body.path("ir");
        Map<String, Object> normalized = ir.isMissingNode() || ir.isNull() ? Map.of() : objectMapper.convertValue(ir, Map.class);
        // [CLOUDFLOW-RUNTIME-EXEC-001] 原行为为了 Java Worker 生成 legacySteps 二次执行投影；
        // 新行为保持 Rust Compiler IR 原样，避免 Java 与 Rust 形成两套 DAG/条件/重试语义。
        return new ValidationReport(body.path("valid").asBoolean(false) && issues.isEmpty(), List.copyOf(issues), normalized, sha256(normalized));
    }

    private ValidationReport unavailableReport(String message) {
        // 安全门禁：即使配置误写为 ALLOW_UNVALIDATED，也不允许发布链路绕过 Rust Compiler。
        return new ValidationReport(false, List.of(new ValidationIssue(
                "CF-RUNTIME-UNAVAILABLE", "runtime", message, null, null, "ERROR", "RUNTIME_ERROR",
                message + "\n请检查 cloudflow.runtime.compile-url 与 Runtime 健康状态。", List.of("稍后重试"),
                "校验服务恢复后点击重新校验", "/docs/cloudflow/runtime-deployment"
        )), Map.of(), "");
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
