package org.project.workflow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Scheduler → Workflow 的持久化事件拓扑；失败消息进入独立 DLQ。 */
@Configuration
public class WorkflowRabbitConfig {
    public static final String EXCHANGE = "pcd.workflow.exchange";
    public static final String DLX = "pcd.workflow.dlx";
    public static final String SCHEDULE_QUEUE = "pcd.workflow.schedule.fire.q";
    public static final String SCHEDULE_DLQ = "pcd.workflow.schedule.fire.dlq";
    public static final String SCHEDULE_ROUTING_KEY = "workflow.schedule.fire";

    @Bean
    DirectExchange workflowExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange workflowDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue workflowScheduleQueue() {
        return QueueBuilder.durable(SCHEDULE_QUEUE)
                .quorum()
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(SCHEDULE_ROUTING_KEY + ".dlq")
                .withArgument("x-message-ttl", 7L * 24L * 60L * 60L * 1000L)
                .build();
    }

    @Bean
    Queue workflowScheduleDlq() {
        return QueueBuilder.durable(SCHEDULE_DLQ)
                .quorum()
                .withArgument("x-message-ttl", 30L * 24L * 60L * 60L * 1000L)
                .build();
    }

    @Bean
    Binding workflowScheduleBinding(Queue workflowScheduleQueue, DirectExchange workflowExchange) {
        return BindingBuilder.bind(workflowScheduleQueue)
                .to(workflowExchange)
                .with(SCHEDULE_ROUTING_KEY);
    }

    @Bean
    Binding workflowScheduleDlqBinding(
            Queue workflowScheduleDlq,
            DirectExchange workflowDeadLetterExchange
    ) {
        return BindingBuilder.bind(workflowScheduleDlq)
                .to(workflowDeadLetterExchange)
                .with(SCHEDULE_ROUTING_KEY + ".dlq");
    }
}
