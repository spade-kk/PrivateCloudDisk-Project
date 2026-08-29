package org.project.workflow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
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
    public static final String CLOUDFLOW_EXCHANGE = "pcd.cloudflow.exchange";
    public static final String CLOUDFLOW_RESULT_QUEUE = "pcd.workflow.cloudflow.result.q";
    public static final String CLOUDFLOW_RESULT_DLQ = "pcd.workflow.cloudflow.result.dlq";
    public static final String CLOUDFLOW_RESULT_DLQ_ROUTE = "cloudflow.execution.result.dlq";
    /** [REQ-GIT-CI-10.1~10.4] Workflow 是 git.push.completed 的独立订阅者，拥有独立队列/DLX/DLQ。 */
    public static final String GIT_EVENT_EXCHANGE = "pcd.git.event.exchange";
    public static final String GIT_PUSH_QUEUE = "pcd.workflow.git.push.completed.q";
    public static final String GIT_PUSH_DLX = "pcd.workflow.git.push.completed.dlx";
    public static final String GIT_PUSH_DLQ = "pcd.workflow.git.push.completed.dlq";
    public static final String GIT_PUSH_ROUTE = "git.push.completed";

    @Bean
    TopicExchange gitEventExchange() {
        return new TopicExchange(GIT_EVENT_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange gitPushDeadLetterExchange() {
        return new DirectExchange(GIT_PUSH_DLX, true, false);
    }

    @Bean
    Queue gitPushQueue() {
        return QueueBuilder.durable(GIT_PUSH_QUEUE).quorum()
                .deadLetterExchange(GIT_PUSH_DLX)
                .deadLetterRoutingKey(GIT_PUSH_ROUTE + ".dlq")
                .withArgument("x-message-ttl", 7L * 24L * 60L * 60L * 1000L)
                .build();
    }

    @Bean
    Queue gitPushDeadLetterQueue() {
        return QueueBuilder.durable(GIT_PUSH_DLQ).quorum()
                .withArgument("x-message-ttl", 30L * 24L * 60L * 60L * 1000L)
                .build();
    }

    @Bean
    Binding gitPushBinding(Queue gitPushQueue, TopicExchange gitEventExchange) {
        return BindingBuilder.bind(gitPushQueue).to(gitEventExchange).with(GIT_PUSH_ROUTE);
    }

    @Bean
    Binding gitPushDeadLetterBinding(Queue gitPushDeadLetterQueue, DirectExchange gitPushDeadLetterExchange) {
        return BindingBuilder.bind(gitPushDeadLetterQueue).to(gitPushDeadLetterExchange).with(GIT_PUSH_ROUTE + ".dlq");
    }

    @Bean
    TopicExchange cloudFlowExchange() {
        return new TopicExchange(CLOUDFLOW_EXCHANGE, true, false);
    }

    @Bean
    Queue cloudFlowResultQueue() {
        return QueueBuilder.durable(CLOUDFLOW_RESULT_QUEUE)
                .quorum()
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(CLOUDFLOW_RESULT_DLQ_ROUTE)
                .build();
    }

    @Bean
    Queue cloudFlowResultDeadLetterQueue() {
        return QueueBuilder.durable(CLOUDFLOW_RESULT_DLQ).quorum().build();
    }

    @Bean
    Binding cloudFlowResultDeadLetterBinding(
            Queue cloudFlowResultDeadLetterQueue,
            DirectExchange workflowDeadLetterExchange
    ) {
        return BindingBuilder.bind(cloudFlowResultDeadLetterQueue)
                .to(workflowDeadLetterExchange)
                .with(CLOUDFLOW_RESULT_DLQ_ROUTE);
    }

    @Bean
    Binding cloudFlowAcceptedBinding(Queue cloudFlowResultQueue, TopicExchange cloudFlowExchange) {
        return BindingBuilder.bind(cloudFlowResultQueue)
                .to(cloudFlowExchange)
                .with("cloudflow.execution.accepted");
    }

    @Bean
    Binding cloudFlowCompletedBinding(Queue cloudFlowResultQueue, TopicExchange cloudFlowExchange) {
        return BindingBuilder.bind(cloudFlowResultQueue)
                .to(cloudFlowExchange)
                .with("cloudflow.execution.completed");
    }

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
