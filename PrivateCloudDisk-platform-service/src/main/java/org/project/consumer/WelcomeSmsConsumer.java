package org.project.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.event.UserRegisteredEvent;
import org.project.model.entity.NotificationSendLogEntity;
import org.project.repository.NotificationSendLogRepository;
import org.project.service.SmsService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 欢迎短信消费者
 * <p>消费用户注册事件，发送欢迎短信。逻辑与欢迎邮件消费者一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WelcomeSmsConsumer {

    private final SmsService smsService;
    private final NotificationSendLogRepository notificationRepository;

    @RabbitListener(
            containerFactory = "manualRabbitListenerContainerFactory",
            queues = RabbitMQConifgure.QUEUE_WELCOME_SMS
    )
    public void consume(UserRegisteredEvent event,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("[欢迎短信] 收到事件. eventId={}, userId={}, phone={}",
                event.getEventId(), event.getUserId(), event.getPhone());

        try {
            // 步骤1：参数校验
            if (event.getPhone() == null || event.getPhone().isEmpty()) {
                log.info("[欢迎短信] 用户未提供手机号，跳过. eventId={}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 步骤2：幂等性检查
            boolean shouldSend = notificationRepository.tryStart(
                    event.getEventId(),
                    NotificationSendLogEntity.CHANNEL_SMS,
                    event.getPhone(),
                    event.getUserId()
            );
            if (!shouldSend) {
                log.info("[欢迎短信] 幂等检查：该短信已处理或正在处理，跳过. eventId={}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 步骤3：发送
            String userName = (event.getUserName() != null && !event.getUserName().isEmpty())
                    ? event.getUserName() : event.getUserAccount();
            smsService.sendWelcomeSms(event.getPhone(), userName);

            // 步骤4：成功 - 更新状态 + 确认消息
            notificationRepository.markSuccess(
                    event.getEventId(),
                    NotificationSendLogEntity.CHANNEL_SMS,
                    event.getPhone()
            );
            channel.basicAck(deliveryTag, false);
            log.info("[欢迎短信] 发送成功，消息已确认. eventId={}", event.getEventId());

        } catch (Exception e) {
            log.error("[欢迎短信] 处理失败. eventId={}, error={}", event.getEventId(), e.getMessage(), e);
            try {
                notificationRepository.markFailed(
                        event.getEventId(),
                        NotificationSendLogEntity.CHANNEL_SMS,
                        event.getPhone(),
                        e.getMessage()
                );
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception channelEx) {
                log.error("[欢迎短信] 更新状态或Nack时发生异常. eventId={}, error={}",
                        event.getEventId(), channelEx.getMessage(), channelEx);
            }
        }
    }
}
