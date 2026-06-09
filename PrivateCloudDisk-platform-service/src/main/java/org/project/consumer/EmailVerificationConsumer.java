package org.project.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.event.EmailVerificationEvent;
import org.project.model.entity.NotificationSendLogEntity;
import org.project.repository.NotificationSendLogRepository;
import org.project.service.EmailService;
import org.project.service.VerificationCodeService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 邮箱验证码消费者
 * <p>消费邮箱验证码事件，发送验证邮件，并将验证码存入Redis（供后续校验使用）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationConsumer {

    private final EmailService emailService;
    private final VerificationCodeService verificationCodeService;
    private final NotificationSendLogRepository notificationRepository;

    @RabbitListener(
            containerFactory = "manualRabbitListenerContainerFactory",
            queues = RabbitMQConifgure.QUEUE_EMAIL_VERIFICATION
    )
    public void consume(EmailVerificationEvent event,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("[邮箱验证码] 收到事件. eventId={}, email={}, purpose={}",
                event.getEventId(), event.getEmail(), event.getPurpose());

        try {
            // 步骤1：参数校验
            if (event.getEmail() == null || event.getEmail().isEmpty()) {
                log.warn("[邮箱验证码] 邮箱为空，跳过. eventId={}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 步骤2：幂等性检查
            boolean shouldSend = notificationRepository.tryStart(
                    event.getEventId(),
                    NotificationSendLogEntity.CHANNEL_EMAIL,
                    event.getEmail(),
                    event.getUserId()
            );
            if (!shouldSend) {
                log.info("[邮箱验证码] 幂等检查：该验证码已处理，跳过. eventId={}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 步骤3：将验证码存入Redis（发送邮件前必须先存好，确保用户收到时能校验）
            boolean stored = verificationCodeService.storeEmailCode(
                    event.getEmail(),
                    event.getVerificationCode(),
                    event.getExpireSeconds()
            );
            if (!stored) {
                // 频率超限，记录为失败状态，但消息ACK掉（避免反复重发）
                log.warn("[邮箱验证码] 发送频率超限，拒绝发送. eventId={}", event.getEventId());
                notificationRepository.markFailed(
                        event.getEventId(),
                        NotificationSendLogEntity.CHANNEL_EMAIL,
                        event.getEmail(),
                        "Send rate limit exceeded"
                );
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 步骤4：发送验证码邮件
            emailService.sendVerificationEmail(
                    event.getEmail(),
                    event.getVerificationCode(),
                    event.getExpireSeconds(),
                    event.getPurpose()
            );

            // 步骤5：成功 - 更新状态 + 确认消息
            notificationRepository.markSuccess(
                    event.getEventId(),
                    NotificationSendLogEntity.CHANNEL_EMAIL,
                    event.getEmail()
            );
            channel.basicAck(deliveryTag, false);
            log.info("[邮箱验证码] 发送成功，消息已确认. eventId={}", event.getEventId());

        } catch (Exception e) {
            log.error("[邮箱验证码] 处理失败. eventId={}, error={}", event.getEventId(), e.getMessage(), e);
            try {
                notificationRepository.markFailed(
                        event.getEventId(),
                        NotificationSendLogEntity.CHANNEL_EMAIL,
                        event.getEmail(),
                        e.getMessage()
                );
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception channelEx) {
                log.error("[邮箱验证码] 更新状态或Nack时发生异常. eventId={}, error={}",
                        event.getEventId(), channelEx.getMessage(), channelEx);
            }
        }
    }
}
