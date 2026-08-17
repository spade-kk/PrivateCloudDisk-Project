package org.project.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.project.workflow.config.WorkflowProperties;
import org.project.workflow.client.PlatformAuthorizationClient;
import org.project.workflow.model.WorkflowModels.CapabilityInvocation;
import org.project.workflow.model.WorkflowModels.CapabilityResult;
import org.project.workflow.model.WorkflowModels.ExecutionRow;
import org.project.workflow.model.WorkflowModels.ValidationReport;
import org.project.workflow.repository.ExecutionMapper;
import org.project.workflow.repository.WorkflowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 数据库检查点驱动的工作流 Worker；进程退出后从已提交步骤恢复。 */
@Service
public class WorkflowExecutionWorker {
    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionWorker.class);
    private static final Pattern EXACT_EXPRESSION = Pattern.compile("^\\$\\{\\{\\s*([^}]+?)\\s*}}$");
    private final WorkflowProperties properties;
    private final ExecutionClaimService claimService;
    private final ExecutionMapper executionMapper;
    private final WorkflowMapper workflowMapper;
    private final WorkflowDslValidator validator;
    private final CapabilityHubService capabilityHub;
    private final PlatformAuthorizationClient authorizationClient;
    private final ObjectMapper objectMapper;

    public WorkflowExecutionWorker(
            WorkflowProperties properties,
            ExecutionClaimService claimService,
            ExecutionMapper executionMapper,
            WorkflowMapper workflowMapper,
            WorkflowDslValidator validator,
            CapabilityHubService capabilityHub,
            PlatformAuthorizationClient authorizationClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.claimService = claimService;
        this.executionMapper = executionMapper;
        this.workflowMapper = workflowMapper;
        this.validator = validator;
        this.capabilityHub = capabilityHub;
        this.authorizationClient = authorizationClient;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${pcd.worker.poll-delay-ms:1000}")
    public void poll() {
        if (!properties.worker().enabled()) {
            return;
        }
        executionMapper.recoverStale(properties.worker().staleSeconds());
        ExecutionRow execution = claimService.claimNext();
        if (execution == null) {
            return;
        }
        try {
            execute(execution);
        } catch (RuntimeException exception) {
            log.error("工作流执行发生未捕获异常 execution_id={}", execution.executionId(), exception);
            executionMapper.finish(
                    execution.executionId(), "FAILED", "{}",
                    "WF-WORKER-UNEXPECTED", sanitize(exception.getMessage())
            );
        }
    }

    @SuppressWarnings("unchecked")
    private void execute(ExecutionRow execution) {
        if (execution.spaceId() != null && !execution.spaceId().isBlank()) {
            // 异步任务不能沿用触发时权限快照；每次真正运行前重新确认空间成员关系和管理权限。
            authorizationClient.requireExecute(execution.userId(), execution.spaceId());
        }
        var version = workflowMapper.findVersionById(execution.versionId());
        if (version == null || !version.immutable()) {
            executionMapper.finish(
                    execution.executionId(), "FAILED", "{}",
                    "WF-VERSION-UNAVAILABLE", "执行引用的已发布版本不存在"
            );
            return;
        }
        ValidationReport report = validator.validate(
                version.dslText(), execution.userId(), execution.spaceId(), "published.flow"
        );
        if (!report.valid()) {
            executionMapper.finish(
                    execution.executionId(), "FAILED", "{}",
                    "WF-DSL-DRIFT", "已发布 DSL 当前无法通过能力或安全校验"
            );
            return;
        }

        // [CLOUDFLOW-DSL-001] 原执行器从 YAML spec.steps 读取步骤；CloudFlow 控制面
        // 规范化结果直接以 steps 为根字段，保持数据库检查点、重试和权限逻辑不变。
        Map<String, Object> root = report.normalized();
        List<Map<String, Object>> steps = listOfMaps(root.get("steps"));
        List<Map<String, Object>> ordered = topological(steps);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("inputs", readMap(execution.inputSummaryJson()));
        context.put("steps", new LinkedHashMap<String, Object>());
        context.put("workflow", new LinkedHashMap<>(Map.of(
                "executionId", execution.executionId(),
                "failed", false
        )));

        for (Map<String, Object> step : ordered) {
            ExecutionRow fresh = executionMapper.findById(execution.executionId());
            if (fresh.cancelRequested()) {
                executionMapper.finish(
                        execution.executionId(), "CANCELLED", json(context),
                        "WF-CANCELLED", "用户已取消执行"
                );
                return;
            }
            String stepId = Objects.toString(step.get("id"), "");
            if (executionMapper.completedStep(execution.executionId(), stepId) > 0) {
                putStepOutput(context, stepId, readMap(
                        executionMapper.completedStepOutput(execution.executionId(), stepId)
                ));
                continue;
            }
            if (!condition(step.get("if"), context)) {
                putStepOutput(context, stepId, Map.of("skipped", true));
                continue;
            }
            executionMapper.heartbeat(execution.executionId(), stepId);
            CapabilityResult result = invokeStep(execution, step, context);
            if (!result.success()) {
                map(context.get("workflow")).put("failed", true);
                map(context.get("workflow")).put("failedStep", stepId);
                executionMapper.finish(
                        execution.executionId(), "FAILED", truncateJson(context),
                        result.errorCode(), sanitize(result.errorSummary())
                );
                return;
            }
            putStepOutput(context, stepId, result.output());
        }
        executionMapper.finish(
                execution.executionId(), "SUCCEEDED", truncateJson(context), null, null
        );
    }

    private CapabilityResult invokeStep(
            ExecutionRow execution,
            Map<String, Object> step,
            Map<String, Object> context
    ) {
        String stepId = Objects.toString(step.get("id"), "");
        String capabilityKey = Objects.toString(step.get("uses"), "");
        String stepName = Objects.toString(step.getOrDefault("name", stepId), stepId);
        Map<String, Object> input = map(resolve(step.getOrDefault("with", Map.of()), context));
        Map<String, Object> retry = map(step.get("retry"));
        int maxAttempts = Math.max(1, Math.min(number(retry.get("maxAttempts"), 1), 5));
        CapabilityResult last = CapabilityResult.failure("WF-CAPABILITY-FAILED", "能力执行失败");

        Object loopConfig = step.get("for_each");
        if (loopConfig instanceof Map<?, ?>) {
            Map<String, Object> loop = map(loopConfig);
            Object itemsValue = resolve(loop.get("items"), context);
            if (!(itemsValue instanceof Collection<?> items)) {
                return CapabilityResult.failure("WF-LOOP-INPUT", "for_each.items 必须解析为数组");
            }
            if (items.size() > 100) {
                return CapabilityResult.failure("WF-LOOP-LIMIT", "循环项超过 100");
            }
            List<Object> outputs = new ArrayList<>();
            int index = 0;
            for (Object item : items) {
                Map<String, Object> loopInput = new LinkedHashMap<>(input);
                loopInput.put(Objects.toString(loop.getOrDefault("as", "item")), item);
                loopInput.put("index", index++);
                CapabilityResult itemResult = invokeWithRetry(
                        execution, stepId, stepName, capabilityKey, loopInput, maxAttempts
                );
                if (!itemResult.success()) {
                    return itemResult;
                }
                outputs.add(itemResult.output());
            }
            return CapabilityResult.success(Map.of("items", outputs, "count", outputs.size()));
        }
        return invokeWithRetry(
                execution, stepId, stepName, capabilityKey, input, maxAttempts
        );
    }

    private CapabilityResult invokeWithRetry(
            ExecutionRow execution,
            String stepId,
            String stepName,
            String capabilityKey,
            Map<String, Object> input,
            int maxAttempts
    ) {
        CapabilityResult last = CapabilityResult.failure("WF-CAPABILITY-FAILED", "能力执行失败");
        int baseAttempt = executionMapper.maxStepAttempt(execution.executionId(), stepId);
        for (int offset = 1; offset <= maxAttempts; offset++) {
            int attempt = baseAttempt + offset;
            String stepExecutionId = UUID.randomUUID().toString();
            executionMapper.insertStep(
                    stepExecutionId, execution.executionId(), stepId, stepName,
                    capabilityKey, attempt, truncateJson(input)
            );
            last = capabilityHub.invoke(new CapabilityInvocation(
                    capabilityKey, execution.executionId(), stepId,
                    execution.userId(), execution.spaceId(), input
            ));
            executionMapper.finishStep(
                    stepExecutionId,
                    last.success() ? "SUCCEEDED" : "FAILED",
                    truncateJson(last.output()),
                    last.errorCode(),
                    sanitize(last.errorSummary())
            );
            if (last.success()) {
                return last;
            }
            if (offset < maxAttempts) {
                try {
                    Thread.sleep(Math.min(4000L, 250L * (1L << Math.min(offset, 4))));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return CapabilityResult.failure("WF-WORKER-INTERRUPTED", "工作流 Worker 已中断");
                }
            }
        }
        return last;
    }

    private boolean condition(Object condition, Map<String, Object> context) {
        if (condition == null) {
            return true;
        }
        Object resolved;
        if (condition instanceof String rawCondition) {
            Matcher expressionMatcher = EXACT_EXPRESSION.matcher(rawCondition);
            resolved = expressionMatcher.matches()
                    ? expressionMatcher.group(1).trim()
                    : resolve(condition, context);
        } else {
            resolved = resolve(condition, context);
        }
        if (resolved instanceof Boolean value) {
            return value;
        }
        String text = Objects.toString(resolved, "").trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || text.isBlank()) {
            return false;
        }
        // DSL v1 执行器只接受校验器允许的简单比较；不调用脚本引擎或 eval。
        for (String operator : List.of("==", "!=", ">=", "<=", ">", "<")) {
            int position = text.indexOf(operator);
            if (position > 0) {
                Object left = lookup(text.substring(0, position).trim(), context);
                Object right = scalar(text.substring(position + operator.length()).trim(), context);
                return compare(left, right, operator);
            }
        }
        Object value = lookup(text, context);
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private Object resolve(Object value, Map<String, Object> context) {
        if (value instanceof String text) {
            Matcher matcher = EXACT_EXPRESSION.matcher(text);
            if (matcher.matches()) {
                return lookup(matcher.group(1).trim(), context);
            }
            return text;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, child) -> result.put(String.valueOf(key), resolve(child, context)));
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> resolve(item, context)).toList();
        }
        return value;
    }

    private Object lookup(String path, Map<String, Object> context) {
        String normalized = path.replace(".output.", ".").replace(".output", "");
        Object current = context;
        for (String segment : normalized.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(segment);
            } else {
                return null;
            }
        }
        return current;
    }

    private Object scalar(String token, Map<String, Object> context) {
        if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
            return token.substring(1, token.length() - 1);
        }
        if ("true".equals(token) || "false".equals(token)) {
            return Boolean.parseBoolean(token);
        }
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException ignored) {
            return lookup(token, context);
        }
    }

    private boolean compare(Object left, Object right, String operator) {
        if ("==".equals(operator)) {
            return Objects.equals(left, right);
        }
        if ("!=".equals(operator)) {
            return !Objects.equals(left, right);
        }
        if (left instanceof Number a && right instanceof Number b) {
            double difference = a.doubleValue() - b.doubleValue();
            return switch (operator) {
                case ">" -> difference > 0;
                case "<" -> difference < 0;
                case ">=" -> difference >= 0;
                case "<=" -> difference <= 0;
                default -> false;
            };
        }
        return false;
    }

    private List<Map<String, Object>> topological(List<Map<String, Object>> steps) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        Map<String, Set<String>> needs = new LinkedHashMap<>();
        for (Map<String, Object> step : steps) {
            String id = Objects.toString(step.get("id"), "");
            byId.put(id, step);
            Set<String> dependencies = new HashSet<>();
            Object rawNeeds = step.get("needs");
            if (rawNeeds instanceof Collection<?> collection) {
                collection.forEach(value -> dependencies.add(String.valueOf(value)));
            } else if (rawNeeds instanceof String text && !text.isBlank()) {
                dependencies.add(text);
            }
            needs.put(id, dependencies);
        }
        List<Map<String, Object>> ordered = new ArrayList<>();
        Set<String> done = new HashSet<>();
        while (ordered.size() < steps.size()) {
            boolean progressed = false;
            for (Map.Entry<String, Map<String, Object>> entry : byId.entrySet()) {
                if (!done.contains(entry.getKey())
                        && done.containsAll(needs.getOrDefault(entry.getKey(), Set.of()))) {
                    ordered.add(entry.getValue());
                    done.add(entry.getKey());
                    progressed = true;
                }
            }
            if (!progressed) {
                throw new IllegalStateException("工作流 DAG 无法排序");
            }
        }
        return ordered;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, child) -> result.put(String.valueOf(key), child));
        return result;
    }

    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance)
                .map(WorkflowExecutionWorker::map).toList();
    }

    @SuppressWarnings("unchecked")
    private static void putStepOutput(Map<String, Object> context, String stepId,
                                      Map<String, Object> output) {
        ((Map<String, Object>) context.get("steps")).put(stepId, output);
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException exception) {
            return new LinkedHashMap<>();
        }
    }

    private String truncateJson(Object value) {
        String serialized = json(value);
        int max = Math.max(properties.worker().maxStepOutputBytes(), 1024);
        if (serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= max) {
            return serialized;
        }
        return json(Map.of(
                "truncated", true,
                "summary", serialized.substring(0, Math.min(serialized.length(), max / 2))
        ));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private static int number(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Objects.toString(value, ""));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "工作流执行失败";
        }
        String sanitized = value.replaceAll("(?i)(token|password|secret)=[^\\s,;]+", "$1=[redacted]")
                .replaceAll("[\\r\\n\\t]+", " ");
        return sanitized.length() > 2000 ? sanitized.substring(0, 2000) : sanitized;
    }
}
