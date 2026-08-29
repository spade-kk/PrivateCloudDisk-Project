package org.project.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.project.workflow.model.WorkflowModels.WorkflowOutboxRow;
import org.project.workflow.repository.WorkflowOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Workflow 事务 Outbox 发布器。
 *
 * <p>[CLOUDFLOW-RUNTIME-MQ-001] 数据库提交与命令生成同事务；只有 RabbitMQ publisher confirm
 * ACK 后才标记 PUBLISHED。进程退出后 PUBLISHING 租约会恢复。</p>
 */
@Service
public class WorkflowOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(WorkflowOutboxPublisher.class);
    private static final String EXCHANGE = "pcd.cloudflow.exchange";
    private final WorkflowOutboxMapper mapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public WorkflowOutboxPublisher(
            WorkflowOutboxMapper mapper,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.mapper = mapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${pcd.outbox.poll-delay-ms:500}")
    public void publish() {
        for (WorkflowOutboxRow row : claimBatch()) {
            try {
                Map<String, Object> envelope = new LinkedHashMap<>();
                envelope.put("id", row.eventId());
                envelope.put("eventType", row.eventType());
                envelope.put("correlationId", row.aggregateId());
                envelope.put("causationId", null);
                envelope.put("userId", "");
                envelope.put("spaceId", null);
                envelope.put("retryCount", row.attempt());
                envelope.put("occurredAt", Instant.now().toString());
                envelope.put("payload", objectMapper.readValue(
                        row.payloadJson(), new TypeReference<Map<String, Object>>() { }
                ));
                CorrelationData correlation = new CorrelationData(row.eventId());
                rabbitTemplate.convertAndSend(
                        EXCHANGE,
                        row.routingKey(),
                        envelope,
                        message -> {
                            message.getMessageProperties().setMessageId(row.eventId());
                            message.getMessageProperties().setType(row.eventType());
                            message.getMessageProperties().setContentType("application/json");
                            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                            return message;
                        },
                        correlation
                );
                CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (confirm.isAck()) {
                    mapper.published(row.eventId());
                } else {
                    retry(row, "RabbitMQ NACK: " + confirm.getReason());
                }
            } catch (Exception exception) {
                retry(row, exception.getMessage());
            }
        }
    }

    public List<WorkflowOutboxRow> claimBatch() {
        // 不使用同类方法上的 @Transactional，避免 Spring self-invocation 导致 FOR UPDATE 提前失效。
        List<WorkflowOutboxRow> rows = transactionTemplate.execute(status -> {
            mapper.recoverStale();
            List<WorkflowOutboxRow> pending = mapper.selectPendingForUpdate(50);
            return pending.stream().filter(row -> mapper.claim(row.eventId()) == 1).toList();
        });
        return rows == null ? List.of() : rows;
    }

    private void retry(WorkflowOutboxRow row, String reason) {
        int delay = Math.min(900, 1 << Math.min(row.attempt(), 10));
        mapper.retry(row.eventId(), delay);
        log.warn(
                "CloudFlow Outbox 发布失败 event_id={} attempt={} reason={}",
                row.eventId(), row.attempt(), sanitize(reason)
        );
    }

    private static String sanitize(String value) {
        if (value == null) return "unknown";
        String sanitized = value.replaceAll("[\\r\\n\\t]+", " ");
        return sanitized.substring(0, Math.min(sanitized.length(), 500));
    }
}
