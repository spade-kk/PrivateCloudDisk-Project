package org.project.automation.model;

/** 待发布的 Automation Outbox 消息。 */
public record OutboxRow(
        String outboxId,
        String eventType,
        String exchangeName,
        String routingKey,
        String payloadJson,
        int retryCount
) {
}

