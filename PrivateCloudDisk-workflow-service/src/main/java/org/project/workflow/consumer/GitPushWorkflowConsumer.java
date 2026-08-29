package org.project.workflow.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.project.workflow.config.WorkflowRabbitConfig;
import org.project.workflow.exception.WorkflowApiException;
import org.project.workflow.service.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * [REQ-GIT-CI-10/13.4] Git push → CloudFlow 的轻量接入器。
 * 原系统没有 Git 触发源；新实现只把已显式绑定的 push 事件转换为既有 Workflow 执行，
 * 不在 Git Service 内复制 Actions Runner。重复事件由 Workflow 幂等键消除。
 */
@Component
public class GitPushWorkflowConsumer {
    private static final Logger log = LoggerFactory.getLogger(GitPushWorkflowConsumer.class);
    private final ObjectMapper objectMapper;
    private final WorkflowService workflowService;

    public GitPushWorkflowConsumer(ObjectMapper objectMapper, WorkflowService workflowService) {
        this.objectMapper = objectMapper;
        this.workflowService = workflowService;
    }

    @RabbitListener(queues = WorkflowRabbitConfig.GIT_PUSH_QUEUE)
    public void consume(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            Map<String, Object> event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    new TypeReference<Map<String, Object>>() { }
            );
            String eventId = required(event.get("id"), "Git push 事件缺少 id");
            String eventType = required(event.get("type"), "Git push 事件缺少 type");
            Map<String, Object> data = map(event.get("data"));
            String userId = required(data.get("actor_id"), "Git push 事件缺少 actor_id");
            String spaceId = required(data.get("space_id"), "Git push 事件缺少 space_id");
            Map<String, Object> changedRefs = map(data.get("changed_refs"));
            for (Map<String, Object> binding : listOfMaps(data.get("workflow_bindings"))) {
                if (!Boolean.parseBoolean(String.valueOf(binding.getOrDefault("enabled", true)))) continue;
                if (!list(binding.get("events")).contains(eventType)) continue;
                String refPattern = String.valueOf(binding.getOrDefault("refPattern", "refs/heads/main"));
                if (changedRefs.keySet().stream().noneMatch(ref -> matches(refPattern, ref))) continue;
                String workflowId = required(binding.get("workflowId"), "Git 工作流绑定缺少 workflowId");
                try {
                    workflowService.runGitPush(workflowId, userId, spaceId, eventId, Map.of(
                            "git", data,
                            "event_id", eventId,
                            "event_type", eventType
                    ));
                } catch (WorkflowApiException invalidBinding) {
                    // 单个已失效/未发布绑定不能阻塞同一 push 上的其他有效工作流。
                    log.warn("跳过不可执行的 Git 工作流绑定 workflowId={} eventId={}: {}",
                            workflowId, eventId, invalidBinding.getMessage());
                }
            }
            channel.basicAck(deliveryTag, false);
        } catch (IllegalArgumentException malformed) {
            log.warn("丢弃格式错误的 Git push 事件: {}", malformed.getMessage());
            channel.basicReject(deliveryTag, false);
        } catch (Exception transientFailure) {
            log.error("Git push 工作流触发失败，消息进入订阅者独立 DLQ", transientFailure);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private static boolean matches(String glob, String ref) {
        // [REQ-GIT-CI-10.4] 原实现先整体 Pattern.quote 后替换通配符，\"*\" 已被引号包住，
        // 实际不会匹配任意 ref；新行为逐段 quote 后只把显式 * 转为 .*，保留 ref 注入防护。
        String regex = Arrays.stream(glob.split("\\*", -1))
                .map(Pattern::quote)
                .collect(Collectors.joining(".*"));
        return ref.matches(regex);
    }

    private static String required(Object value, String message) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.isBlank()) throw new IllegalArgumentException(message);
        return text;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        return raw.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                entry -> String.valueOf(entry.getKey()), Map.Entry::getValue));
    }

    private static List<String> list(Object value) {
        if (!(value instanceof List<?> raw)) return List.of();
        return raw.stream().map(String::valueOf).toList();
    }

    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> raw)) return List.of();
        return raw.stream().filter(Map.class::isInstance).map(GitPushWorkflowConsumer::map).toList();
    }
}
