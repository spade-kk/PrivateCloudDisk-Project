package org.project.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConifgure {
    // 队列名称
    public static final String REGISTER_QUEUE = "message_queue";
    // 交换机名称
    public static final String REGISTER_EXCHANGE = "message_exchange";
    // 路由键
    public static final String REGISTER_ROUTING_KEY = "internal.message";

    @Bean
    public Queue messageQueue() {
        return new Queue(REGISTER_QUEUE, true);
    }

    @Bean
    public DirectExchange messageExchange() {
        return new DirectExchange(REGISTER_EXCHANGE);
    }

    @Bean
    public Binding messageBinding() {
        return BindingBuilder
                .bind(messageQueue())
                .to(messageExchange())
                .with(REGISTER_ROUTING_KEY);
    }
}
