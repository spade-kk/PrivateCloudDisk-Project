package org.project.im.server.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.project.im.common.constant.ImConstants.MQ_EXCHANGE_MESSAGE;

/**
 * RabbitMQ 配置（im-server）
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange messageExchange() {
        return new TopicExchange(MQ_EXCHANGE_MESSAGE, true, false);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }
}