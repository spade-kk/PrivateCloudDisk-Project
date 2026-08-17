package org.project.automation.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.automation.config.RabbitLifecycleConfig;
import org.project.automation.service.AutomationExecutionService;
import org.project.automation.service.ClaimOutcome;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * file.content.ready 消费者。
 *
 * <p>只有 Inbox/Outbox 本地事务提交后才 ACK。契约错误或数据库异常进入 ready DLQ；
 * Storage timeout sentinel 与 DB sweeper 会继续文件主链，因此这里不可能永久卡住文件。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileContentReadyConsumer {
    private final AutomationExecutionService executionService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitLifecycleConfig.READY_QUEUE)
    public void consume(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            ClaimOutcome outcome = executionService.processRaw(payload);
            log.info("file.content.ready 已处理 messageId={} outcome={}",
                    message.getMessageProperties().getMessageId(), outcome);
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            int retryCount = retryCount(message);
            if (retryCount < RabbitLifecycleConfig.READY_RETRY_DELAYS_MS.length) {
                int nextAttempt = retryCount + 1;
                try {
                    Message retryMessage = MessageBuilder.fromMessage(message)
                            .setHeader(RabbitLifecycleConfig.READY_RETRY_HEADER, nextAttempt)
                            .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                            .build();
                    CorrelationData correlation = new CorrelationData(
                            "ready-retry-" + message.getMessageProperties().getMessageId()
                                    + "-" + nextAttempt
                    );
                    rabbitTemplate.send(
                            RabbitLifecycleConfig.EXCHANGE,
                            RabbitLifecycleConfig.READY_RETRY_ROUTING_PREFIX + nextAttempt,
                            retryMessage,
                            correlation
                    );
                    CorrelationData.Confirm confirm = correlation.getFuture()
                            .get(5, TimeUnit.SECONDS);
                    if (!confirm.isAck()) {
                        throw new IllegalStateException(
                                "RabbitMQ NACK: " + confirm.getReason()
                        );
                    }
                    channel.basicAck(deliveryTag, false);
                    log.warn(
                            "file.content.ready 处理失败，已进入第 {} 次指数退避 retry",
                            nextAttempt,
                            exception
                    );
                    return;
                } catch (Exception retryPublishFailure) {
                    retryPublishFailure.addSuppressed(exception);
                    log.error("file.content.ready retry 发布失败，直接进入 DLQ", retryPublishFailure);
                }
            } else {
                log.error(
                        "file.content.ready 已重试 {} 次仍失败，进入 DLQ 并由 Storage 自动降级",
                        retryCount,
                        exception
                );
            }
            channel.basicNack(deliveryTag, false, false);
        }
    }

    static int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders()
                .get(RabbitLifecycleConfig.READY_RETRY_HEADER);
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
