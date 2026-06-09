package org.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.event.AvatarReviewEvent;
import org.project.event.EmailVerificationEvent;
import org.project.event.PhoneVerificationEvent;
import org.project.event.UserRegisteredEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * 业务事件发布者
 * <p>封装RabbitTemplate，提供强类型的事件发布方法。
 *
 * <p>设计要点：
 * <ul>
 *   <li>业务代码通过此类发布事件，不直接调用RabbitTemplate，关注点分离</li>
 *   <li>每个事件类型发布到不同的交换机/routing-key，对应独立的队列</li>
 *   <li>发布异常不阻断主业务流程（日志告警即可，失败不影响用户注册）</li>
 *   <li>事件对象中包含唯一eventId，用于消费者端做幂等性检查</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布"用户注册"事件
     * <p>该事件会分别被 欢迎邮件消费者 和 欢迎短信消费者 消费。
     */
    public void publishUserRegistered(UserRegisteredEvent event) {
        // 发布到欢迎邮件队列
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConifgure.BUSINESS_EXCHANGE,
                    RabbitMQConifgure.ROUTING_WELCOME_EMAIL,
                    event
            );
            log.info("[事件发布] 用户注册 → 欢迎邮件队列. eventId={}, userId={}",
                    event.getEventId(), event.getUserId());
        } catch (Exception e) {
            log.error("[事件发布] 用户注册 → 欢迎邮件队列 失败. eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);
        }

        // 发布到欢迎短信队列（如果配置了手机号）
        if (event.getPhone() != null && !event.getPhone().isEmpty()) {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConifgure.BUSINESS_EXCHANGE,
                        RabbitMQConifgure.ROUTING_WELCOME_SMS,
                        event
                );
                log.info("[事件发布] 用户注册 → 欢迎短信队列. eventId={}, userId={}",
                        event.getEventId(), event.getUserId());
            } catch (Exception e) {
                log.error("[事件发布] 用户注册 → 欢迎短信队列 失败. eventId={}, error={}",
                        event.getEventId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 发布"邮箱验证码"事件
     */
    public void publishEmailVerification(EmailVerificationEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConifgure.BUSINESS_EXCHANGE,
                    RabbitMQConifgure.ROUTING_EMAIL_VERIFICATION,
                    event
            );
            log.info("[事件发布] 邮箱验证码. eventId={}", event.getEventId());
        } catch (Exception e) {
            log.error("[事件发布] 邮箱验证码 失败. eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);
        }
    }

    /**
     * 发布"手机验证码"事件
     */
    public void publishPhoneVerification(PhoneVerificationEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConifgure.BUSINESS_EXCHANGE,
                    RabbitMQConifgure.ROUTING_PHONE_VERIFICATION,
                    event
            );
            log.info("[事件发布] 手机验证码. eventId={}", event.getEventId());
        } catch (Exception e) {
            log.error("[事件发布] 手机验证码 失败. eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);
        }
    }

    /**
     * 发布"头像审核"事件
     */
    public void publishAvatarReview(AvatarReviewEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConifgure.BUSINESS_EXCHANGE,
                    RabbitMQConifgure.ROUTING_AVATAR_REVIEW,
                    event
            );
            log.info("[事件发布] 头像审核. eventId={}, userId={}",
                    event.getEventId(), event.getUserId());
        } catch (Exception e) {
            log.error("[事件发布] 头像审核 失败. eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);
        }
    }
}
