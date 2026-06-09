package org.project.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.event.PhoneVerificationEvent;
import org.project.model.entity.NotificationSendLogEntity;
import org.project.repository.NotificationSendLogRepository;
import org.project.service.SmsService;
import org.project.service.VerificationCodeService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 手机验证码消费者
 * <p>消费手机验证码事件，发送验证短信，并将验证码存入Redis。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhoneVerificationConsumer {

    private final SmsService smsService;
    private final VerificationCodeService verificationCodeService;
    private final NotificationSendLogRepository notificationRepository;

    @RabbitListener(
            containerFactory = "manualRabbitListenerContainerFactory",
            queues = RabbitMQConifgure.QUEUE_PHONE_VERIFICATION
    )
    public void consume(PhoneVerificationEvent event,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("[手机验证码] 收到事件. eventId={}, phone={}, purpose={}",
                event.getEventId(), event.getPhone(), event.getPurpose());

        try {
            if (event.getPhone() == null || event.getPhone().isEmpty()) {
                log.warn("[手机验证码] 手机号为空，跳过. eventId={}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            boolean shouldSend = notificationRepository.tryStart(
                    event.getEventId(),
                    NotificationSendLogEntity.CHANNEL_SMS,
                    event.getPhone(),
                    event.getUserId()
            );
            if (!shouldSend) {
                log.info("[手机验证码] 幂等检查：跳过. eventId={}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            boolean stored = verificationCodeService.storePhoneCode(
                    event.getPhone(),
                    event.getVerificationCode(),
                    event.getExpireSeconds()
            );
            if (!stored) {
                log.warn("[手机验证码] 频率超限，拒绝发送. eventId={}", event.getEventId());
                notificationRepository.markFailed(
                        event.getEventId(),
                        NotificationSendLogEntity.CHANNEL_SMS,
                        event.getPhone(),
                        "Send rate limit exceeded"
                );
                channel.basicAck(deliveryTag, false);
                return;
            }

            smsService.sendVerificationSms(
                    event.getPhone(),
                    event.getVerificationCode(),
                    event.getExpireSeconds()
            );

            notificationRepository.markSuccess(
                    event.getEventId(),
                    NotificationSendLogEntity.CHANNEL_SMS,
                    event.getPhone()
            );
            channel.basicAck(deliveryTag, false);
            log.info("[手机验证码] 发送成功. eventId={}", event.getEventId());

        } catch (Exception e) {
            log.error("[手机验证码] 处理失败. eventId={}, error={}", event.getEventId(), e.getMessage(), e);
            try {
                notificationRepository.markFailed(
                        event.getEventId(),
                        NotificationSendLogEntity.CHANNEL_SMS,
                        event.getPhone(),
                        e.getMessage()
                );
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception channelEx) {
                log.error("[手机验证码] 更新状态或Nack异常. eventId={}, error={}",
                        event.getEventId(), channelEx.getMessage(), channelEx);
            }
        }
    }
}
