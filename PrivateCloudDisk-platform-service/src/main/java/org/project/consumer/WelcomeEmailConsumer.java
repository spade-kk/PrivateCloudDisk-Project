package org.project.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.event.UserRegisteredEvent;
import org.project.model.entity.NotificationSendLogEntity;
import org.project.repository.NotificationSendLogRepository;
import org.project.service.EmailService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 欢迎邮件消费者
 * <p>消费用户注册事件，发送欢迎邮件。
 *
 * <p>处理流程：
 * <ol>
 *   <li>参数校验（邮箱是否为空）</li>
 *   <li>调用 {@link NotificationSendLogRepository#tryStart} 做幂等性检查</li>
 *   <li>成功获取处理权 → 调用 {@link EmailService#sendWelcomeEmail} 发送邮件</li>
 *   <li>发送成功 → 更新日志为 SUCCESS，调用 basicAck 确认消息</li>
 *   <li>发送失败 → 更新日志为 FAILED，调用 basicNack 将消息转入死信队列</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WelcomeEmailConsumer {

    private final EmailService emailService;
    private final NotificationSendLogRepository notificationRepository;

    @RabbitListener(
            containerFactory = "manualRabbitListenerContainerFactory",
            queues = RabbitMQConifgure.QUEUE_WELCOME_EMAIL
    )
    public void consume(UserRegisteredEvent event,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("[欢迎邮件] 收到事件. eventId={}, userId={}, email={}",
                event.getEventId(), event.getUserId(), event.getEmail());

        try {
            // 步骤1：参数校验 - 邮箱为空直接ACK（无需处理）
            if (event.getEmail() == null || event.getEmail().isEmpty()) {
                log.info("[欢迎邮件] 用户未提供邮箱，跳过. eventId={}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 步骤2：幂等性检查 - 获取处理权
            boolean shouldSend = notificationRepository.tryStart(
                    event.getEventId(),
                    NotificationSendLogEntity.CHANNEL_EMAIL,
                    event.getEmail(),
                    event.getUserId()
            );
            if (!shouldSend) {
                log.info("[欢迎邮件] 幂等检查：该邮件已处理或正在处理，跳过. eventId={}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 步骤3：调用业务服务发送
            String userName = (event.getUserName() != null && !event.getUserName().isEmpty())
                    ? event.getUserName() : event.getUserAccount();
            emailService.sendWelcomeEmail(event.getEmail(), userName);

            // 步骤4：成功 - 更新状态 + 确认消息
            notificationRepository.markSuccess(
                    event.getEventId(),
                    NotificationSendLogEntity.CHANNEL_EMAIL,
                    event.getEmail()
            );
            channel.basicAck(deliveryTag, false);
            log.info("[欢迎邮件] 发送成功，消息已确认. eventId={}", event.getEventId());

        } catch (Exception e) {
            log.error("[欢迎邮件] 处理失败. eventId={}, error={}", event.getEventId(), e.getMessage(), e);
            try {
                // 标记为失败
                notificationRepository.markFailed(
                        event.getEventId(),
                        NotificationSendLogEntity.CHANNEL_EMAIL,
                        event.getEmail(),
                        e.getMessage()
                );
                // basicNack requeue=false → 消息被转发到 死信交换机 → 死信队列
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception channelEx) {
                log.error("[欢迎邮件] 更新状态或Nack时发生异常. eventId={}, error={}",
                        event.getEventId(), channelEx.getMessage(), channelEx);
            }
        }
    }
}
