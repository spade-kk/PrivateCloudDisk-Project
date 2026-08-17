package org.project.automation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.automation.config.AutomationProperties;
import org.project.automation.model.OutboxRow;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** 等待 Rabbit publisher confirm 后才把 Automation Outbox 标记 SENT。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationOutboxPublisher {
    private final AutomationOutboxService outboxService;
    private final RabbitTemplate rabbitTemplate;
    private final AutomationProperties properties;

    @Scheduled(fixedDelayString = "${pcd.outbox-poll-ms:250}")
    public void publishPending() {
        for (OutboxRow row : outboxService.claim(50)) {
            try {
                CorrelationData correlation = new CorrelationData(row.outboxId());
                rabbitTemplate.convertAndSend(
                        row.exchangeName(),
                        row.routingKey(),
                        row.payloadJson(),
                        message -> {
                            message.getMessageProperties().setContentType("application/json");
                            message.getMessageProperties().setMessageId(row.outboxId());
                            message.getMessageProperties().setDeliveryMode(
                                    org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT
                            );
                            return message;
                        },
                        correlation
                );
                CorrelationData.Confirm confirm = correlation.getFuture()
                        .get(10, TimeUnit.SECONDS);
                if (!confirm.isAck()) {
                    throw new IllegalStateException("RabbitMQ NACK: " + confirm.getReason());
                }
                outboxService.markSent(row.outboxId());
            } catch (Exception exception) {
                outboxService.markFailed(row.outboxId(), exception, row.retryCount());
                log.error("Automation Outbox 发布失败 outboxId={} eventType={}",
                        row.outboxId(), row.eventType(), exception);
            }
        }
    }
}

