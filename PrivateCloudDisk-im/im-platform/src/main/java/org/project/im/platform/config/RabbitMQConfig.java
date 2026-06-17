package org.project.im.platform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.project.im.common.constant.ImConstants.*;

/**
 * RabbitMQ 配置
 * <p>
 * 声明 IM 系统所需的交换机、队列和绑定关系：
 * <ul>
 *   <li>消息推送交换机：im.message.exchange（Topic 类型）</li>
 *   <li>消息持久化队列：im.message.persist.queue</li>
 *   <li>离线消息队列：im.message.offline.queue</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    /**
     * 消息推送交换机（Topic 类型）
     * <p>
     * 路由规则：
     * <ul>
     *   <li>im.message.private → 单聊消息</li>
     *   <li>im.message.group → 群聊消息</li>
     *   <li>im.message.system → 系统通知</li>
     * </ul>
     * </p>
     */
    @Bean
    public TopicExchange messageExchange() {
        return new TopicExchange(MQ_EXCHANGE_MESSAGE, true, false);
    }

    /**
     * 消息推送队列
     */
    @Bean
    public Queue messagePushQueue() {
        return QueueBuilder.durable(MQ_QUEUE_MESSAGE_PUSH).build();
    }

    /**
     * 消息持久化队列
     */
    @Bean
    public Queue messagePersistQueue() {
        return QueueBuilder.durable(MQ_QUEUE_MESSAGE_PERSIST).build();
    }

    /**
     * 离线消息队列
     */
    @Bean
    public Queue offlineQueue() {
        return QueueBuilder.durable(MQ_QUEUE_OFFLINE).build();
    }

    /**
     * 绑定消息推送队列到交换机（接收所有 IM 消息）
     */
    @Bean
    public Binding bindingPush(Queue messagePushQueue, TopicExchange messageExchange) {
        return BindingBuilder.bind(messagePushQueue)
                .to(messageExchange)
                .with("im.message.#");
    }

    /**
     * 绑定持久化队列到交换机
     */
    @Bean
    public Binding bindingPersist(Queue messagePersistQueue, TopicExchange messageExchange) {
        return BindingBuilder.bind(messagePersistQueue)
                .to(messageExchange)
                .with("im.message.#");
    }

    /**
     * 绑定离线消息队列到交换机
     */
    @Bean
    public Binding bindingOffline(Queue offlineQueue, TopicExchange messageExchange) {
        return BindingBuilder.bind(offlineQueue)
                .to(messageExchange)
                .with("im.message.#");
    }

    /**
     * 配置 RabbitTemplate
     * <ul>
     *   <li>使用 Jackson2Json 序列化消息</li>
     *   <li>开启发送方确认（Publisher Confirms）</li>
     *   <li>开启返回回调（Return Callback）</li>
     * </ul>
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());

        // 发送方确认
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack && correlationData != null) {
                log.error("消息发送失败: id={}, cause={}", correlationData.getId(), cause);
            }
        });

        // 无法路由的消息回调
        template.setReturnsCallback(returned -> {
            log.error("消息无法路由: exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
        });

        template.setMandatory(true);
        return template;
    }
}