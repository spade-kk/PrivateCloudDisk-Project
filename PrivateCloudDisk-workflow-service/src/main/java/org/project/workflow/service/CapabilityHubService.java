package org.project.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.project.workflow.config.WorkflowProperties;
import org.project.workflow.model.WorkflowModels.CapabilityInvocation;
import org.project.workflow.model.WorkflowModels.CapabilityResult;
import org.project.workflow.model.WorkflowModels.CapabilityRow;
import org.project.workflow.repository.CapabilityMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 能力调用中心：注册、Schema 前置校验、路由和统一错误边界。 */
@Service
public class CapabilityHubService {
    private static final int MAX_TEXT_LENGTH = 65_536;
    private final CapabilityMapper mapper;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final PluginCapabilityClient pluginCapabilityClient;

    public CapabilityHubService(
            CapabilityMapper mapper,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate,
            PluginCapabilityClient pluginCapabilityClient
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.pluginCapabilityClient = pluginCapabilityClient;
    }

    public List<CapabilityRow> search(String sourceType, String query, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        return mapper.search(blank(sourceType), blank(query), safeSize, (Math.max(page, 1) - 1) * safeSize);
    }

    public CapabilityRow get(String key) {
        return mapper.findByKey(key);
    }

    public CapabilityResult invoke(CapabilityInvocation invocation) {
        CapabilityRow capability = mapper.findByKey(invocation.capabilityKey());
        if (capability == null || "DISABLED".equals(capability.status())) {
            return CapabilityResult.failure("WF-CAPABILITY-NOT-FOUND", "能力不存在或已下架");
        }
        List<String> schemaErrors = validateRequiredFields(capability.inputSchemaJson(), invocation.input());
        if (!schemaErrors.isEmpty()) {
            return CapabilityResult.failure("WF-CAPABILITY-INPUT", String.join("；", schemaErrors));
        }
        try {
            return switch (capability.sourceType()) {
                case "BUILTIN" -> invokeBuiltin(invocation);
                case "API" -> invokePlatformApi(invocation);
                case "PLUGIN" -> pluginCapabilityClient.invoke(invocation, capability);
                case "LOCAL_PLUGIN" -> CapabilityResult.failure(
                        "WF-LOCAL-CLIENT-OFFLINE",
                        "该步骤需要兼容的本地客户端在线，当前没有可用客户端"
                );
                default -> CapabilityResult.failure("WF-CAPABILITY-SOURCE", "能力来源不受支持");
            };
        } catch (RuntimeException exception) {
            return CapabilityResult.failure(
                    "WF-CAPABILITY-FAILED",
                    sanitize(exception.getMessage())
            );
        }
    }

    public void upsertProjection(CapabilityRow capability) {
        mapper.upsert(
                capability.capabilityKey(),
                capability.sourceType(),
                capability.sourceId(),
                capability.sourceVersion(),
                capability.displayName(),
                capability.description(),
                capability.inputSchemaJson(),
                capability.outputSchemaJson(),
                capability.requiredPermissionsJson(),
                capability.availabilityPolicyJson(),
                capability.status(),
                capability.revision()
        );
    }

    private CapabilityResult invokeBuiltin(CapabilityInvocation invocation) {
        return switch (invocation.capabilityKey()) {
            case "builtin:date.now" -> {
                String zone = Objects.toString(invocation.input().getOrDefault("timezone", "UTC"));
                yield CapabilityResult.success(Map.of(
                        "iso", ZonedDateTime.now(ZoneId.of(zone)).toString(),
                        "timezone", zone
                ));
            }
            case "builtin:text.transform" -> {
                String text = Objects.toString(invocation.input().getOrDefault("text", ""));
                if (text.length() > MAX_TEXT_LENGTH) {
                    yield CapabilityResult.failure("WF-CAPABILITY-INPUT", "文本长度超过 65536 字符");
                }
                String operation = Objects.toString(invocation.input().getOrDefault("operation", "trim"));
                String output = switch (operation) {
                    case "upper" -> text.toUpperCase(Locale.ROOT);
                    case "lower" -> text.toLowerCase(Locale.ROOT);
                    case "trim" -> text.trim();
                    default -> throw new IllegalArgumentException("不支持的文本转换操作");
                };
                yield CapabilityResult.success(Map.of("text", output));
            }
            default -> CapabilityResult.failure("WF-CAPABILITY-NOT-IMPLEMENTED", "内置能力尚未实现");
        };
    }

    private CapabilityResult invokePlatformApi(CapabilityInvocation invocation) {
        if (!"api:user.notify".equals(invocation.capabilityKey())) {
            return CapabilityResult.failure("WF-CAPABILITY-NOT-IMPLEMENTED", "平台能力尚未实现");
        }
        Map<String, Object> event = Map.of(
                "event_id", UUID.randomUUID().toString(),
                "event_type", "system_notify",
                "user_id", invocation.userId(),
                "space_id", invocation.spaceId() == null ? "" : invocation.spaceId(),
                "data", Map.of(
                        "title", Objects.toString(invocation.input().get("title"), ""),
                        "body", Objects.toString(invocation.input().get("body"), ""),
                        "source", "workflow",
                        "workflow_execution_id", invocation.executionId()
                )
        );
        rabbitTemplate.convertAndSend("pcd.notification.exchange", "notification.push", event);
        return CapabilityResult.success(Map.of("accepted", true, "event_id", event.get("event_id")));
    }

    private List<String> validateRequiredFields(String schemaJson, Map<String, Object> input) {
        try {
            Map<String, Object> schema = objectMapper.readValue(
                    schemaJson, new TypeReference<Map<String, Object>>() { }
            );
            List<String> errors = new ArrayList<>();
            Object required = schema.get("required");
            if (required instanceof List<?> names) {
                for (Object item : names) {
                    String name = String.valueOf(item);
                    if (!input.containsKey(name) || input.get(name) == null) {
                        errors.add("缺少必填参数 " + name);
                    }
                }
            }
            return errors;
        } catch (Exception exception) {
            return List.of("能力输入 Schema 无法解析");
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "能力执行失败";
        }
        String sanitized = value.replaceAll("(?i)(token|password|secret)=[^\\s,;]+", "$1=[redacted]")
                .replaceAll("[\\r\\n\\t]+", " ");
        return sanitized.length() > 1000 ? sanitized.substring(0, 1000) : sanitized;
    }
}
