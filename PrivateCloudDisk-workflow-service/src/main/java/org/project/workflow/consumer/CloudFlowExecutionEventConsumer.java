package org.project.workflow.consumer;

import com.rabbitmq.client.Channel;
import org.project.workflow.config.WorkflowRabbitConfig;
import org.project.workflow.repository.ExecutionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/** Rust Runtime 执行状态回写消费者；event_id 由 Runtime Outbox 保证稳定。 */
@Component
public class CloudFlowExecutionEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(CloudFlowExecutionEventConsumer.class);
    private final ExecutionMapper mapper;

    public CloudFlowExecutionEventConsumer(ExecutionMapper mapper) {
        this.mapper = mapper;
    }

    @RabbitListener(queues = WorkflowRabbitConfig.CLOUDFLOW_RESULT_QUEUE)
    public void handle(Map<String, Object> envelope, Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            String eventType = text(envelope.get("eventType"));
            Map<String, Object> payload = envelope.get("payload") instanceof Map<?, ?> value
                    ? cast(value) : Map.of();
            String executionId = text(payload.get("executionId"));
            if (executionId.isBlank()) {
                throw new IllegalArgumentException("CloudFlow 回写事件缺少 executionId");
            }
            if ("cloudflow.execution.accepted.v1".equals(eventType)) {
                mapper.markRuntimeAccepted(executionId);
            } else if ("cloudflow.execution.completed.v1".equals(eventType)) {
                String status = text(payload.get("status"));
                if (!status.matches("SUCCESS|FAILED|CANCELLED")) {
                    throw new IllegalArgumentException("CloudFlow 终态无效");
                }
                mapper.markRuntimeCompleted(
                        executionId,
                        status,
                        nullable(payload.get("errorCode")),
                        nullable(payload.get("errorSummary"))
                );
            } else {
                log.warn("忽略未知 CloudFlow 回写事件 event_type={}", eventType);
            }
            channel.basicAck(tag, false);
        } catch (IllegalArgumentException exception) {
            log.error("CloudFlow 回写事件契约错误: {}", exception.getMessage());
            channel.basicReject(tag, false);
        } catch (RuntimeException exception) {
            log.error("CloudFlow 回写事件处理失败", exception);
            channel.basicNack(tag, false, true);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> value) {
        return (Map<String, Object>) value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullable(Object value) {
        String text = text(value);
        return text.isBlank() ? null : text;
    }
}
