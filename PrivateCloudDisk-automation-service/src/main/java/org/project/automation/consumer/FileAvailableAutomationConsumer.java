package org.project.automation.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.automation.config.RabbitLifecycleConfig;
import org.project.automation.service.AutomationExecutionService;
import org.project.automation.service.ClaimOutcome;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** file.available 独立消费者；不复用 Platform 原队列，保持既有业务完全不变。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileAvailableAutomationConsumer {
    private final AutomationExecutionService executionService;

    @RabbitListener(queues = RabbitLifecycleConfig.AVAILABLE_QUEUE)
    public void consume(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            ClaimOutcome outcome = executionService.processAvailableRaw(
                    new String(message.getBody(), StandardCharsets.UTF_8)
            );
            log.info("file.available 自动化入口已处理 outcome={}", outcome);
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            // 激活后插件失败不影响文件可访问，但消息进入独立 DLQ 供审计与人工重放。
            log.error("file.available 自动化处理失败，进入独立 DLQ", exception);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
