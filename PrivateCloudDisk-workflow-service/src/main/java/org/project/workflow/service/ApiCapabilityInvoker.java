package org.project.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.project.workflow.config.WorkflowProperties;
import org.project.workflow.exception.WorkflowApiException;
import org.project.workflow.model.WorkflowModels.CapabilityInvocation;
import org.project.workflow.model.WorkflowModels.CapabilityResult;
import org.project.workflow.model.WorkflowModels.RunWorkflowRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 平台 API 能力调用器（需求四 4.x / 六 6.11-6.19）。
 *
 * <p>职责与边界：</p>
 * <ul>
 *   <li>目标服务与路径来自代码内白名单路由表（5.21 防 SSRF），基址由配置解析（4.22）；</li>
 *   <li>透传执行上下文与内部凭证（4.14/6.14）：X-PCD-User-Id / X-PCD-Space-Id / X-PCD-Service-Token
 *       以及 execution/step 链路头，由数据面二次校验资源权限（4.15）；</li>
 *   <li>每能力超时来自 availability_policy.timeout_seconds（默认 30s，6.23）；</li>
 *   <li>仅幂等能力重试（6.24）；能力级熔断（6.25/2.18）；统一错误包装（4.17/4.18）。</li>
 * </ul>
 */
@Component
public class ApiCapabilityInvoker {
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRIES = 2;

    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final RestClient.Builder restClientBuilder;
    private final SimpleCapabilityBreaker breaker;
    private final RestClient injectedPlatformClient;
    private final RestClient injectedStorageClient;
    private final WorkflowService workflowService;

    public ApiCapabilityInvoker(
            WorkflowProperties properties,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate,
            RestClient.Builder restClientBuilder,
            SimpleCapabilityBreaker breaker
    ) {
        // 保留既有测试/适配器构造器；仅调用新 workflow capability 时才需要 WorkflowService。
        this(properties, objectMapper, rabbitTemplate, restClientBuilder, breaker, null, null, null);
    }

    @Autowired
    public ApiCapabilityInvoker(
            WorkflowProperties properties,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate,
            RestClient.Builder restClientBuilder,
            SimpleCapabilityBreaker breaker,
            WorkflowService workflowService
    ) {
        this(properties, objectMapper, rabbitTemplate, restClientBuilder, breaker, null, null, workflowService);
    }

    /** 包可见测试构造器：直接注入平台/存储 RestClient，便于 MockRestServiceServer 校验路由与上下文头。 */
    ApiCapabilityInvoker(
            WorkflowProperties properties,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate,
            SimpleCapabilityBreaker breaker,
            RestClient platformClient,
            RestClient storageClient
    ) {
        this(properties, objectMapper, rabbitTemplate, RestClient.builder(), breaker, platformClient, storageClient, null);
    }

    ApiCapabilityInvoker(
            WorkflowProperties properties,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate,
            SimpleCapabilityBreaker breaker,
            RestClient platformClient,
            RestClient storageClient,
            WorkflowService workflowService
    ) {
        this(properties, objectMapper, rabbitTemplate, RestClient.builder(), breaker, platformClient, storageClient, workflowService);
    }

    private ApiCapabilityInvoker(
            WorkflowProperties properties,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate,
            RestClient.Builder restClientBuilder,
            SimpleCapabilityBreaker breaker,
            RestClient injectedPlatformClient,
            RestClient injectedStorageClient,
            WorkflowService workflowService
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.restClientBuilder = restClientBuilder;
        this.breaker = breaker;
        this.injectedPlatformClient = injectedPlatformClient;
        this.injectedStorageClient = injectedStorageClient;
        this.workflowService = workflowService;
    }

    /** 返回 null 表示本调用器不处理该能力键（应由上层回落到其他分发器）。 */
    public CapabilityResult invoke(CapabilityInvocation invocation, String availabilityPolicyJson) {
        return switch (invocation.capabilityKey()) {
            case "api:file.metadata.get" -> platformGet(
                    invocation, availabilityPolicyJson, "/business/internal/storage/files/{file_id}",
                    List.of("file_id"), true);
            case "api:file.list" -> platformGet(
                    invocation, availabilityPolicyJson, "/business/internal/capability/files/list",
                    List.of(), true);
            case "api:file.search" -> platformGet(
                    invocation, availabilityPolicyJson, "/business/internal/capability/files/search",
                    List.of(), true);
            case "api:file.scan" -> platformPost(
                    invocation, availabilityPolicyJson, "/business/internal/capability/files/{file_id}/scan",
                    List.of("file_id"), false);
            case "api:space.info" -> platformGet(
                    invocation, availabilityPolicyJson, "/business/internal/capability/spaces/{space_id}/info",
                    List.of("space_id"), true);
            case "api:space.members.list" -> platformGet(
                    invocation, availabilityPolicyJson, "/business/internal/capability/spaces/{space_id}/members",
                    List.of("space_id"), true);
            case "api:user.info" -> platformGet(
                    invocation, availabilityPolicyJson, "/business/internal/capability/users/{user_id}/info",
                    List.of("user_id"), true);
            case "api:tag.list" -> platformGet(
                    invocation, availabilityPolicyJson, "/business/internal/capability/files/{file_id}/tags",
                    List.of("file_id"), true);
            case "api:share.create" -> platformPost(
                    invocation, availabilityPolicyJson, "/business/internal/capability/shares",
                    List.of(), false);
            case "api:file.content.get" -> fileContent(invocation, availabilityPolicyJson);
            case "api:notification.send" -> notificationSend(invocation);
            // [AI-AGENT-CAPABILITY-001] Do not expose WorkflowController or CloudFlow
            // Runtime directly to Agent callers. Hub keeps schema/permission/idempotency/
            // audit processing, then delegates to the same domain service used by public APIs.
            case "api:workflow.list" -> workflowList(invocation);
            case "api:workflow.validate" -> workflowValidate(invocation);
            case "api:workflow.execute" -> workflowExecute(invocation);
            case "api:workflow.status" -> workflowStatus(invocation);
            default -> null;
        };
    }

    private CapabilityResult workflowList(CapabilityInvocation invocation) {
        if (workflowService == null) return CapabilityResult.failure("WF-CAPABILITY-NOT-CONFIGURED", "工作流能力调用器尚未配置");
        try {
            Map<String, Object> input = safeInput(invocation);
            int page = number(input.get("page"), 1);
            int size = number(input.get("size"), 20);
            return CapabilityResult.success(Map.of(
                    "items", objectMapper.convertValue(workflowService.list(invocation.userId(), invocation.spaceId(), page, size), List.class)
            ));
        } catch (WorkflowApiException exception) {
            return CapabilityResult.failure(exception.code(), exception.getMessage());
        }
    }

    private CapabilityResult workflowValidate(CapabilityInvocation invocation) {
        if (workflowService == null) return CapabilityResult.failure("WF-CAPABILITY-NOT-CONFIGURED", "工作流能力调用器尚未配置");
        try {
            String dsl = String.valueOf(safeInput(invocation).getOrDefault("dsl", ""));
            return CapabilityResult.success(objectMapper.convertValue(
                    workflowService.validate(dsl, invocation.userId(), invocation.spaceId()), Map.class
            ));
        } catch (WorkflowApiException exception) {
            return CapabilityResult.failure(exception.code(), exception.getMessage());
        }
    }

    private CapabilityResult workflowExecute(CapabilityInvocation invocation) {
        if (workflowService == null) return CapabilityResult.failure("WF-CAPABILITY-NOT-CONFIGURED", "工作流能力调用器尚未配置");
        try {
            Map<String, Object> input = safeInput(invocation);
            String workflowId = String.valueOf(input.getOrDefault("workflow_id", ""));
            Integer version = input.get("version") instanceof Number number ? number.intValue() : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> inputs = input.get("inputs") instanceof Map<?, ?> values
                    ? (Map<String, Object>) values : Map.of();
            return CapabilityResult.success(objectMapper.convertValue(
                    // [AI-AGENT-CAPABILITY-002] CapabilityInvocation intentionally carries
                    // execution/step identity but not the outer Agent idempotency token.
                    // Derive the domain-command idempotency key from those immutable Hub
                    // coordinates so a retry cannot create a second workflow execution.
                    workflowService.run(workflowId, invocation.userId(), invocation.spaceId(),
                            workflowExecutionIdempotencyKey(invocation), new RunWorkflowRequest(version, inputs)),
                    Map.class
            ));
        } catch (WorkflowApiException exception) {
            return CapabilityResult.failure(exception.code(), exception.getMessage());
        }
    }

    private CapabilityResult workflowStatus(CapabilityInvocation invocation) {
        if (workflowService == null) return CapabilityResult.failure("WF-CAPABILITY-NOT-CONFIGURED", "工作流能力调用器尚未配置");
        try {
            String executionId = String.valueOf(safeInput(invocation).getOrDefault("execution_id", ""));
            return CapabilityResult.success(objectMapper.convertValue(
                    workflowService.execution(executionId, invocation.userId()), Map.class
            ));
        } catch (WorkflowApiException exception) {
            return CapabilityResult.failure(exception.code(), exception.getMessage());
        }
    }

    private static Map<String, Object> safeInput(CapabilityInvocation invocation) {
        return invocation.input() == null ? Map.of() : invocation.input();
    }

    private static String workflowExecutionIdempotencyKey(CapabilityInvocation invocation) {
        return "capability:" + invocation.executionId() + ":" + invocation.stepId();
    }

    private static int number(Object value, int defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private CapabilityResult platformGet(
            CapabilityInvocation invocation,
            String availabilityPolicyJson,
            String pathTemplate,
            List<String> pathParams,
            boolean idempotent
    ) {
        return platformCall(invocation, availabilityPolicyJson, HttpMethod.GET, pathTemplate,
                pathParams, idempotent);
    }

    private CapabilityResult platformPost(
            CapabilityInvocation invocation,
            String availabilityPolicyJson,
            String pathTemplate,
            List<String> pathParams,
            boolean idempotent
    ) {
        return platformCall(invocation, availabilityPolicyJson, HttpMethod.POST, pathTemplate,
                pathParams, idempotent);
    }

    private CapabilityResult platformCall(
            CapabilityInvocation invocation,
            String availabilityPolicyJson,
            HttpMethod method,
            String pathTemplate,
            List<String> pathParams,
            boolean idempotent
    ) {
        String key = invocation.capabilityKey();
        if (!breaker.tryAcquire(key)) {
            return CapabilityResult.failure("WF-CAPABILITY-CIRCUIT-OPEN", "能力暂不可用（熔断中），请稍后重试");
        }
        int timeoutSeconds = timeoutSeconds(availabilityPolicyJson, DEFAULT_TIMEOUT_SECONDS);
        Map<String, Object> input = invocation.input() == null ? Map.of() : invocation.input();
        String path = pathTemplate;
        for (String param : pathParams) {
            Object value = input.getOrDefault(param, "");
            path = path.replace("{" + param + "}", String.valueOf(value));
        }
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(platformUrl() + path);
        if (method == HttpMethod.GET) {
            for (String param : List.of("space_id", "parent_id", "keyword", "page", "size")) {
                Object value = input.get(param);
                if (value != null) {
                    uri.queryParam(param, value);
                }
            }
        }
        // 数据面身份契约（4.14/6.14）：Platform 内部接口以 uid 查询参数识别调用者（横向越权复核基准），
        // 空间上下文另以 X-Space-Id 头传递；缺一都会导致下游 400/空间错解析。
        uri.queryParam("uid", invocation.userId());
        String requestUri = uri.build(false).toUriString();
        RuntimeException lastError = null;
        int attempts = idempotent ? 1 + MAX_RETRIES : 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                RestClient.RequestBodySpec spec = client(timeoutSeconds).method(method)
                        .uri(requestUri)
                        .header("X-PCD-User-Id", invocation.userId())
                        .header("X-PCD-Space-Id", invocation.spaceId() == null ? "" : invocation.spaceId())
                        .header("X-Space-Id", invocation.spaceId() == null ? "" : invocation.spaceId())
                        .header("X-PCD-Execution-Id", invocation.executionId())
                        .header("X-PCD-Step-Id", invocation.stepId());
                Map<String, Object> data;
                if (method == HttpMethod.POST) {
                    data = spec.header("Content-Type", "application/json")
                            .body(method == HttpMethod.POST ? input : null)
                            .retrieve()
                            .body(Map.class);
                } else {
                    data = spec.retrieve().body(Map.class);
                }
                CapabilityResult result = unwrap(key, data);
                mark(key, result);
                return result;
            } catch (RestClientException exception) {
                lastError = exception;
                if (attempt + 1 < attempts) {
                    continue;
                }
            }
        }
        breaker.recordFailure(key);
        return CapabilityResult.retryableFailure(
                "WF-CAPABILITY-DATAPLANE-UNAVAILABLE",
                lastError == null ? "数据面服务暂时不可用" : "数据面服务暂时不可用：" + sanitize(lastError.getMessage())
        );
    }

    private void mark(String key, CapabilityResult result) {
        if (result.success()) {
            breaker.recordSuccess(key);
        } else {
            breaker.recordFailure(key);
        }
    }

    private CapabilityResult unwrap(String key, Map<String, Object> data) {
        if (data == null) {
            return CapabilityResult.failure("WF-CAPABILITY-DATAPLANE-EMPTY", "数据面服务未返回结果");
        }
        Object code = data.get("code");
        if (code != null && !String.valueOf(code).equals("200")) {
            Object message = data.get("message");
            return CapabilityResult.failure(
                    "WF-CAPABILITY-DATAPLANE-ERROR",
                    message == null ? "数据面返回错误" : sanitize(String.valueOf(message))
            );
        }
        Object payload = data.get("data");
        if (payload instanceof Map<?, ?> map) {
            Map<String, Object> output = new LinkedHashMap<>();
            map.forEach((k, v) -> output.put(String.valueOf(k), v));
            return CapabilityResult.success(output);
        }
        if (payload != null) {
            return CapabilityResult.success(Map.of("value", payload));
        }
        return CapabilityResult.success(Map.of());
    }

    /**
     * api:file.content.get —— 先经 Platform 数据面校验元数据与权限（4.15），再基于文本类型白名单与
     * 大小上限（5.8）经 Storage 数据面 operation-token 流程取文本内容（5.9 限制读取范围）。
     */
    private CapabilityResult fileContent(CapabilityInvocation invocation, String availabilityPolicyJson) {
        String key = "api:file.content.get";
        if (!breaker.tryAcquire(key)) {
            return CapabilityResult.failure("WF-CAPABILITY-CIRCUIT-OPEN", "能力暂不可用（熔断中），请稍后重试");
        }
        int timeoutSeconds = timeoutSeconds(availabilityPolicyJson, DEFAULT_TIMEOUT_SECONDS);
        Map<String, Object> input = invocation.input() == null ? Map.of() : invocation.input();
        String fileId = String.valueOf(input.get("file_id"));
        int maxBytes = input.get("max_bytes") instanceof Number number
                ? Math.min(Math.max(number.intValue(), 1024), 1_048_576) : 262_144;

        CapabilityResult metadata = platformGet(
                invocation, availabilityPolicyJson,
                "/business/internal/storage/files/{file_id}", List.of("file_id"), true);
        if (!metadata.success()) {
            return metadata;
        }
        String fileType = String.valueOf(metadata.output().getOrDefault("type", ""));
        String fileName = String.valueOf(metadata.output().getOrDefault("name", ""));
        long size = metadata.output().get("size") instanceof Number number
                ? number.longValue() : 0;
        if (!TEXT_TYPE_ALLOWLIST.isText(fileType)) {
            return CapabilityResult.failure(
                    "WF-CAPABILITY-CONTENT-TYPE", "仅支持文本、代码与 Markdown 等可预览类型");
        }
        if (size > 1_048_576L) {
            return CapabilityResult.failure(
                    "WF-CAPABILITY-CONTENT-TOO-LARGE", "文件过大，仅支持 1MiB 以内的可预览文件");
        }
        if (size > maxBytes) {
            return CapabilityResult.failure(
                    "WF-CAPABILITY-CONTENT-LIMIT", "文件超过本步骤 max_bytes 限制");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> issued = storageClient(timeoutSeconds).post()
                    .uri(storageUrl() + "/files/operation-tokens")
                    .header("X-PCD-User-Id", invocation.userId())
                    .header("X-PCD-Space-Id", invocation.spaceId() == null ? "" : invocation.spaceId())
                    .body(Map.of("file_id", fileId, "operation_type", "preview"))
                    .retrieve()
                    .body(Map.class);
            String operationToken = extractToken(issued);
            byte[] bytes = storageClient(timeoutSeconds).get()
                    .uri(storageUrl() + "/files/files/" + fileId + "/content")
                    .header("X-PCD-User-Id", invocation.userId())
                    .header("X-PCD-Space-Id", invocation.spaceId() == null ? "" : invocation.spaceId())
                    .header("X-Operation-Token", operationToken)
                    .header("Range", "bytes=0-" + (maxBytes - 1))
                    .retrieve()
                    .body(byte[].class);
            if (bytes == null) {
                bytes = new byte[0];
            }
            boolean truncated = bytes.length >= maxBytes;
            String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            breaker.recordSuccess(key);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("file_id", fileId);
            output.put("file_name", fileName);
            output.put("file_type", fileType);
            output.put("size", size);
            output.put("content", content);
            output.put("truncated", truncated);
            return CapabilityResult.success(output);
        } catch (RestClientException exception) {
            breaker.recordFailure(key);
            return CapabilityResult.retryableFailure(
                    "WF-CAPABILITY-CONTENT-UNAVAILABLE", "存储服务读取内容失败");
        }
    }

    /** api:notification.send —— 沿用平台通知事件流（同一 outbox/Rabbit 拓扑，4.11/5.11）。 */
    private CapabilityResult notificationSend(CapabilityInvocation invocation) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_id", eventId);
        event.put("event_type", "system_notify");
        event.put("user_id", invocation.userId());
        event.put("space_id", invocation.spaceId() == null ? "" : invocation.spaceId());
        event.put("data", Map.of(
                "title", String.valueOf(invocation.input().getOrDefault("title", "")),
                "body", String.valueOf(invocation.input().getOrDefault("body", "")),
                "source", "workflow",
                "workflow_execution_id", invocation.executionId()
        ));
        rabbitTemplate.convertAndSend("pcd.notification.exchange", "notification.push", event);
        return CapabilityResult.success(Map.of("accepted", true, "event_id", eventId));
    }

    private String extractToken(Map<String, Object> issued) {
        if (issued == null) {
            throw new IllegalStateException("operation token 签发失败");
        }
        Object data = issued.get("data");
        if (data instanceof Map<?, ?> map && map.get("operation_token") != null) {
            return String.valueOf(map.get("operation_token"));
        }
        throw new IllegalStateException("operation token 签发失败");
    }

    private RestClient client(int timeoutSeconds) {
        if (injectedPlatformClient != null) {
            return injectedPlatformClient;
        }
        return cloneWithTimeout(timeoutSeconds);
    }

    private RestClient storageClient(int timeoutSeconds) {
        if (injectedStorageClient != null) {
            return injectedStorageClient;
        }
        return cloneWithTimeout(timeoutSeconds);
    }

    private RestClient cloneWithTimeout(int timeoutSeconds) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)));
        return restClientBuilder.clone().requestFactory(factory).build();
    }

    private String platformUrl() {
        return properties.platformUrl();
    }

    private String storageUrl() {
        return properties.storageUrl();
    }

    private static int timeoutSeconds(String availabilityPolicyJson, int fallback) {
        if (availabilityPolicyJson == null || availabilityPolicyJson.isBlank()) {
            return fallback;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode timeout =
                    new ObjectMapper().readTree(availabilityPolicyJson).get("timeout_seconds");
            if (timeout != null && timeout.isNumber()) {
                return Math.max(1, timeout.asInt());
            }
        } catch (Exception ignored) {
            // 非法策略文本回退默认 30s。
        }
        return fallback;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "未知错误";
        }
        String cleaned = value.replaceAll("\\s+", " ");
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }

    /** 文本类文件类型白名单（5.8：默认仅允许文本、代码、Markdown 等可预览类型）。 */
    private enum TextTypeAllowlist {
        INSTANCE;

        boolean isText(String type) {
            if (type == null || type.isBlank()) {
                return false;
            }
            String lower = type.toLowerCase(Locale.ROOT).trim();
            if (lower.startsWith("text/") || lower.startsWith("markdown")) {
                return true;
            }
            if (lower.startsWith("application/")) {
                return lower.contains("json") || lower.contains("xml")
                        || lower.contains("yaml") || lower.contains("javascript")
                        || lower.contains("x-sh") || lower.contains("x-python")
                        || lower.contains("x-httpd") || lower.contains("markdown");
            }
            return TEXT_EXTENSIONS.contains(lower);
        }
    }

    private static final TextTypeAllowlist TEXT_TYPE_ALLOWLIST = TextTypeAllowlist.INSTANCE;

    private static final java.util.Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "log", "md", "markdown", "json", "xml", "yaml", "yml",
            "java", "kt", "kts", "go", "rs", "py", "js", "mjs", "cjs", "ts", "tsx", "jsx", "vue",
            "c", "h", "cpp", "hpp", "cc", "cs", "sh", "bash", "zsh", "sql", "html", "htm",
            "css", "properties", "conf", "ini", "toml", "gradle", "dockerfile", "makefile"
    );
}
