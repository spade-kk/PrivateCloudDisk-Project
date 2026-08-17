package org.project.automation.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;

/** 与 Storage Worker 完全一致的文件内容生命周期拓扑。 */
@Configuration
public class RabbitLifecycleConfig {
    public static final String EXCHANGE = "pcd.file.lifecycle.exchange";
    public static final String DLX = "pcd.file.lifecycle.dlx";
    public static final String READY_QUEUE = "pcd.automation.file.content.ready.q";
    public static final String READY_DLQ = "pcd.automation.file.content.ready.dlq";
    public static final String READY_ROUTING = "file.content.ready";
    public static final String READY_DLQ_ROUTING = "file.content.ready.dlq";
    public static final String READY_RETRY_HEADER = "x-pcd-ready-retry";
    public static final String READY_RETRY_ROUTING_PREFIX = "file.content.ready.retry.";
    public static final int[] READY_RETRY_DELAYS_MS = {1_000, 4_000, 16_000};
    public static final String PROCESSED_ROUTING = "file.content.processed";
    public static final String FILE_EVENT_EXCHANGE = "pcd.file.event.exchange";
    public static final String FILE_EVENT_DLX = "pcd.file.event.dlx";
    public static final String AVAILABLE_QUEUE = "pcd.automation.file.available.q";
    public static final String AVAILABLE_DLQ = "pcd.automation.file.available.dlq";
    public static final String AVAILABLE_ROUTING = "file.available";
    public static final String AVAILABLE_DLQ_ROUTING = "file.available.automation.dlq";

    @Bean
    TopicExchange lifecycleExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    TopicExchange lifecycleDlx() {
        return ExchangeBuilder.topicExchange(DLX).durable(true).build();
    }

    @Bean
    Queue contentReadyQueue() {
        return QueueBuilder.durable(READY_QUEUE)
                .quorum()
                .ttl(7 * 24 * 60 * 60 * 1000)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(READY_DLQ_ROUTING)
                .build();
    }

    @Bean
    Queue contentReadyDlq() {
        return QueueBuilder.durable(READY_DLQ)
                .quorum()
                .ttl(30 * 24 * 60 * 60 * 1000)
                .build();
    }

    @Bean
    Binding contentReadyBinding(
            @Qualifier("contentReadyQueue") Queue contentReadyQueue,
            @Qualifier("lifecycleExchange") TopicExchange lifecycleExchange
    ) {
        return BindingBuilder.bind(contentReadyQueue).to(lifecycleExchange).with(READY_ROUTING);
    }

    @Bean
    Binding contentReadyDlqBinding(
            @Qualifier("contentReadyDlq") Queue contentReadyDlq,
            @Qualifier("lifecycleDlx") TopicExchange lifecycleDlx
    ) {
        return BindingBuilder.bind(contentReadyDlq).to(lifecycleDlx).with(READY_DLQ_ROUTING);
    }

    /**
     * file.content.ready 三段指数退避队列。
     *
     * <p>消费者先把失败消息可靠发布到对应延迟队列并 ACK 原消息；TTL 到期后回到主队列。
     * 三次仍失败才进入 DLQ，Storage 的超时哨兵始终独立存在，不依赖这些重试成功。</p>
     */
    @Bean
    Declarables contentReadyRetryTopology(
            @Qualifier("lifecycleExchange") TopicExchange lifecycleExchange
    ) {
        List<Declarable> declarables = new ArrayList<>();
        for (int index = 0; index < READY_RETRY_DELAYS_MS.length; index++) {
            int attempt = index + 1;
            Queue queue = QueueBuilder.durable(READY_QUEUE + ".retry." + attempt)
                    .quorum()
                    .ttl(READY_RETRY_DELAYS_MS[index])
                    .deadLetterExchange(EXCHANGE)
                    .deadLetterRoutingKey(READY_ROUTING)
                    .build();
            declarables.add(queue);
            declarables.add(
                    BindingBuilder.bind(queue)
                            .to(lifecycleExchange)
                            .with(READY_RETRY_ROUTING_PREFIX + attempt)
            );
        }
        return new Declarables(declarables);
    }

    @Bean
    TopicExchange fileEventExchange() {
        return ExchangeBuilder.topicExchange(FILE_EVENT_EXCHANGE).durable(true).build();
    }

    @Bean
    TopicExchange fileEventDlx() {
        return ExchangeBuilder.topicExchange(FILE_EVENT_DLX).durable(true).build();
    }

    @Bean
    Queue automationAvailableQueue() {
        return QueueBuilder.durable(AVAILABLE_QUEUE)
                .quorum()
                .ttl(7 * 24 * 60 * 60 * 1000)
                .deadLetterExchange(FILE_EVENT_DLX)
                .deadLetterRoutingKey(AVAILABLE_DLQ_ROUTING)
                .build();
    }

    @Bean
    Queue automationAvailableDlq() {
        return QueueBuilder.durable(AVAILABLE_DLQ)
                .quorum()
                .ttl(30 * 24 * 60 * 60 * 1000)
                .build();
    }

    @Bean
    Binding automationAvailableBinding(
            @Qualifier("automationAvailableQueue") Queue automationAvailableQueue,
            @Qualifier("fileEventExchange") TopicExchange fileEventExchange
    ) {
        return BindingBuilder.bind(automationAvailableQueue)
                .to(fileEventExchange)
                .with(AVAILABLE_ROUTING);
    }

    @Bean
    Binding automationAvailableDlqBinding(
            @Qualifier("automationAvailableDlq") Queue automationAvailableDlq,
            @Qualifier("fileEventDlx") TopicExchange fileEventDlx
    ) {
        return BindingBuilder.bind(automationAvailableDlq)
                .to(fileEventDlx)
                .with(AVAILABLE_DLQ_ROUTING);
    }
}
