package org.project.workflow.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.project.workflow.config.WorkflowRabbitConfig;
import org.project.workflow.service.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 消费 Scheduler 的幂等触发事件，重复消息不会产生重复执行。 */
@Component
public class ScheduleFireConsumer {
    private static final Logger log = LoggerFactory.getLogger(ScheduleFireConsumer.class);
    private final ObjectMapper objectMapper;
    private final WorkflowService service;

    public ScheduleFireConsumer(ObjectMapper objectMapper, WorkflowService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @RabbitListener(queues = WorkflowRabbitConfig.SCHEDULE_QUEUE)
    public void consume(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            Map<String, Object> event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    new TypeReference<Map<String, Object>>() { }
            );
            Map<String, Object> data = event.get("data") instanceof Map<?, ?> raw
                    ? raw.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                    entry -> String.valueOf(entry.getKey()), Map.Entry::getValue
            )) : Map.of();
            service.runScheduled(
                    text(data.get("workflow_id")),
                    text(data.get("version_id")),
                    text(data.get("user_id")),
                    optionalText(data.get("space_id")),
                    text(data.get("schedule_id")),
                    text(data.get("scheduled_at")),
                    data.get("inputs") instanceof Map<?, ?> input
                            ? input.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                            entry -> String.valueOf(entry.getKey()), Map.Entry::getValue
                    )) : Map.of()
            );
            channel.basicAck(deliveryTag, false);
        } catch (IllegalArgumentException exception) {
            log.warn("丢弃格式错误的 schedule fire 事件: {}", exception.getMessage());
            channel.basicReject(deliveryTag, false);
        } catch (Exception exception) {
            log.error("schedule fire 消费失败，交由队列重投或 DLQ", exception);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private static String text(Object value) {
        String result = value == null ? "" : String.valueOf(value);
        if (result.isBlank()) {
            throw new IllegalArgumentException("schedule fire 缺少必填字段");
        }
        return result;
    }

    private static String optionalText(Object value) {
        String result = value == null ? "" : String.valueOf(value);
        return result.isBlank() ? null : result;
    }
}
