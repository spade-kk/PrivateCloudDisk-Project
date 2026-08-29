package org.project.im.platform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.project.im.common.constant.ImConstants.*;

// ============================================================
// RabbitMQ 配置 — 分布式 IM 事件总线（含 DLX/DLQ 死信机制）
// ============================================================
// 架构设计：
//   命令总线（Command Bus）—— 表示"要做某件事"
//     生产者发出指令，消费者执行。使用 direct 交换机精确路由。
//     失败时统一路由到 im.dlx.command 死信交换机，经重试队列延迟后重新投递。
//
//   事件总线（Event Bus）—— 表示"某件事已经发生"
//     生产者按事件类型路由键发布，消费者各自按路由键订阅。使用 topic 交换机。
//     每个消费者拥有独立的死信队列，失败时路由到各自 DLQ。
//
// 死信策略：
//   命令消息：NACK(requeue=false) → im.dlx.command → retry queue(TTL) → 原队列（循环）
//            重试耗尽 → 进入 im.dlq.command.send / im.dlq.command.push
//   事件消息：NACK(requeue=false) → im.dlx.event → 各消费者独立 DLQ
//
// 队列拓扑：
//   im.command.exchange (direct)
//     ├── im.message.send.command   (DLX: im.dlx.command, RK: im.retry.command.send)
//     └── im.message.push.command   (DLX: im.dlx.command, RK: im.retry.command.push)
//
//   im.dlx.command (direct)
//     ├── im.retry.command.send (TTL=5s, DLX: im.command.exchange, RK: im.message.send.command)
//     ├── im.retry.command.push (TTL=5s, DLX: im.command.exchange, RK: im.message.push.command)
//     ├── im.dlq.command.send
//     └── im.dlq.command.push
//
//   im.event.exchange (topic) — 按路由键订阅
//     ├── im.message.delivered.event.platform   (DLX: im.dlx.event, RK: im.dlq.event.delivered.platform)
//     ├── im.message.failed.event.platform      (DLX: im.dlx.event, RK: im.dlq.event.failed.platform)
//     ├── im.user.online.event.platform         (DLX: im.dlx.event, RK: im.dlq.event.online.platform)
//     ├── im.user.offline.event.platform        (DLX: im.dlx.event, RK: im.dlq.event.offline.platform)
//     ├── im.message.read.event.platform        (DLX: im.dlx.event, RK: im.dlq.event.read.platform)
//     ├── im.message.delivered.event.router     (DLX: im.dlx.event, RK: im.dlq.event.delivered.router)
//     ├── im.message.failed.event.router        (DLX: im.dlx.event, RK: im.dlq.event.failed.router)
//     └── im.message.send.failed.event.router   (DLX: im.dlx.event, RK: im.dlq.event.send.failed.router)
//
//   im.dlx.event (direct)
//     ├── im.dlq.event.delivered.platform
//     ├── im.dlq.event.failed.platform
//     ├── im.dlq.event.online.platform
//     ├── im.dlq.event.offline.platform
//     ├── im.dlq.event.read.platform
//     ├── im.dlq.event.delivered.router
//     ├── im.dlq.event.failed.router
//     └── im.dlq.event.send.failed.router
// ============================================================

/**
 * RabbitMQ 分布式配置（含完整死信机制）
 * <p>
 * 定义 IM 系统的命令队列、事件队列、死信交换机、死信队列和重试队列，
 * 实现命令总线与事件总线分离，以及可靠的失败处理策略。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    // ============================================================
    // 交换机
    // ============================================================

    /**
     * 命令交换机（direct 类型，精确路由）
     */
    @Bean
    public DirectExchange commandExchange() {
        return ExchangeBuilder.directExchange(MQ_EXCHANGE_COMMAND)
                .durable(true)
                .build();
    }

    /**
     * 事件交换机（topic 类型，按路由键订阅）
     * <p>
     * 使用 topic 而非 fanout，使得每个消费者队列只订阅自己关心的事件类型，
     * 避免不相关事件的无谓广播。
     * </p>
     */
    @Bean
    public TopicExchange eventExchange() {
        return ExchangeBuilder.topicExchange(MQ_EXCHANGE_EVENT)
                .durable(true)
                .build();
    }

    /**
     * 命令死信交换机（direct 类型）
     * <p>
     * 统一接收所有命令队列的死信消息，根据路由键分发到重试队列或死信队列。
     * </p>
     */
    @Bean
    public DirectExchange commandDlxExchange() {
        return ExchangeBuilder.directExchange(MQ_DLX_COMMAND)
                .durable(true)
                .build();
    }

    /**
     * 事件死信交换机（direct 类型）
     * <p>
     * 接收所有事件队列的死信消息，按路由键分发到各消费者独立的死信队列。
     * </p>
     */
    @Bean
    public DirectExchange eventDlxExchange() {
        return ExchangeBuilder.directExchange(MQ_DLX_EVENT)
                .durable(true)
                .build();
    }

    // ============================================================
    // 命令队列 — A. 消息发送命令（IM Server → IM Business）
    // ============================================================

    @Bean
    public Queue sendCommandQueue() {
        return QueueBuilder.durable(MQ_QUEUE_SEND_COMMAND)
                .withArgument("x-dead-letter-exchange", MQ_DLX_COMMAND)
                .withArgument("x-dead-letter-routing-key", MQ_RETRY_SEND_COMMAND)
                .build();
    }

    @Bean
    public Binding sendCommandBinding(
            @Qualifier("sendCommandQueue") Queue queue,
            @Qualifier("commandExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(MQ_ROUTING_SEND_COMMAND);
    }

    // ============================================================
    // 命令队列 — B. 消息推送命令（IM Business → IM Router）
    // ============================================================

    @Bean
    public Queue pushCommandQueue() {
        return QueueBuilder.durable(MQ_QUEUE_PUSH_COMMAND)
                .withArgument("x-dead-letter-exchange", MQ_DLX_COMMAND)
                .withArgument("x-dead-letter-routing-key", MQ_RETRY_PUSH_COMMAND)
                .build();
    }

    @Bean
    public Binding pushCommandBinding(
            @Qualifier("pushCommandQueue") Queue queue,
            @Qualifier("commandExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(MQ_ROUTING_PUSH_COMMAND);
    }

    // ============================================================
    // 命令重试队列 — 带 TTL，过期后 DLX 重新投递到原队列
    // ============================================================

    /**
     * 消息发送命令重试队列
     * <p>
     * TTL 5 秒后消息过期，通过 DLX 重新投递到 im.command.exchange，
     * 路由键为 im.message.send.command，实现延迟重试。
     * </p>
     */
    @Bean
    public Queue retrySendCommandQueue() {
        return QueueBuilder.durable(MQ_RETRY_SEND_COMMAND)
                .withArgument("x-dead-letter-exchange", MQ_EXCHANGE_COMMAND)
                .withArgument("x-dead-letter-routing-key", MQ_ROUTING_SEND_COMMAND)
                .withArgument("x-message-ttl", COMMAND_RETRY_TTL_MS)
                .build();
    }

    @Bean
    public Binding retrySendCommandBinding(
            @Qualifier("retrySendCommandQueue") Queue queue,
            @Qualifier("commandDlxExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(MQ_RETRY_SEND_COMMAND);
    }

    /**
     * 消息推送命令重试队列
     */
    @Bean
    public Queue retryPushCommandQueue() {
        return QueueBuilder.durable(MQ_RETRY_PUSH_COMMAND)
                .withArgument("x-dead-letter-exchange", MQ_EXCHANGE_COMMAND)
                .withArgument("x-dead-letter-routing-key", MQ_ROUTING_PUSH_COMMAND)
                .withArgument("x-message-ttl", COMMAND_RETRY_TTL_MS)
                .build();
    }

    @Bean
    public Binding retryPushCommandBinding(
            @Qualifier("retryPushCommandQueue") Queue queue,
            @Qualifier("commandDlxExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(MQ_RETRY_PUSH_COMMAND);
    }

    // ============================================================
    // 命令死信队列（DLQ）— 重试耗尽后的最终归宿
    // ============================================================

    @Bean
    public Queue dlqSendCommandQueue() {
        return QueueBuilder.durable(MQ_DLQ_SEND_COMMAND).build();
    }

    @Bean
    public Binding dlqSendCommandBinding(
            @Qualifier("dlqSendCommandQueue") Queue queue,
            @Qualifier("commandDlxExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(MQ_DLQ_SEND_COMMAND);
    }

    @Bean
    public Queue dlqPushCommandQueue() {
        return QueueBuilder.durable(MQ_DLQ_PUSH_COMMAND).build();
    }

    @Bean
    public Binding dlqPushCommandBinding(
            @Qualifier("dlqPushCommandQueue") Queue queue,
            @Qualifier("commandDlxExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(MQ_DLQ_PUSH_COMMAND);
    }

    // ============================================================
    // 事件队列 — C. 消息送达事件（IM Platform 消费）
    // ============================================================

    @Bean
    public Queue deliveredEventPlatformQueue() {
        return QueueBuilder.durable(MQ_QUEUE_DELIVERED_EVENT_PLATFORM)
                .withArgument("x-dead-letter-exchange", MQ_DLX_EVENT)
                .withArgument("x-dead-letter-routing-key", MQ_DLQ_DELIVERED_PLATFORM)
                .build();
    }

    @Bean
    public Binding deliveredPlatformBinding(
            @Qualifier("deliveredEventPlatformQueue") Queue queue,
            @Qualifier("eventExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_ROUTING_DELIVERED_EVENT);
    }

    // ============================================================
    // 事件队列 — D. 消息失败事件（IM Platform 消费）
    // ============================================================

    @Bean
    public Queue failedEventPlatformQueue() {
        return QueueBuilder.durable(MQ_QUEUE_FAILED_EVENT_PLATFORM)
                .withArgument("x-dead-letter-exchange", MQ_DLX_EVENT)
                .withArgument("x-dead-letter-routing-key", MQ_DLQ_FAILED_PLATFORM)
                .build();
    }

    @Bean
    public Binding failedPlatformBinding(
            @Qualifier("failedEventPlatformQueue") Queue queue,
            @Qualifier("eventExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_ROUTING_FAILED_EVENT);
    }

    // ============================================================
    // 事件队列 — E. 用户上线事件（IM Platform 消费）
    // ============================================================

    @Bean
    public Queue userOnlineEventQueue() {
        return QueueBuilder.durable(MQ_QUEUE_USER_ONLINE_EVENT)
                .withArgument("x-dead-letter-exchange", MQ_DLX_EVENT)
                .withArgument("x-dead-letter-routing-key", MQ_DLQ_ONLINE)
                .build();
    }

    @Bean
    public Binding userOnlineBinding(
            @Qualifier("userOnlineEventQueue") Queue queue,
            @Qualifier("eventExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_ROUTING_USER_ONLINE_EVENT);
    }

    // ============================================================
    // 事件队列 — F. 用户离线事件（IM Platform 消费）
    // ============================================================

    @Bean
    public Queue userOfflineEventQueue() {
        return QueueBuilder.durable(MQ_QUEUE_USER_OFFLINE_EVENT)
                .withArgument("x-dead-letter-exchange", MQ_DLX_EVENT)
                .withArgument("x-dead-letter-routing-key", MQ_DLQ_OFFLINE)
                .build();
    }

    @Bean
    public Binding userOfflineBinding(
            @Qualifier("userOfflineEventQueue") Queue queue,
            @Qualifier("eventExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_ROUTING_USER_OFFLINE_EVENT);
    }

    // ============================================================
    // 事件队列 — G. 消息已读事件（IM Platform 消费）
    // ============================================================

    @Bean
    public Queue messageReadEventQueue() {
        return QueueBuilder.durable(MQ_QUEUE_MESSAGE_READ_EVENT)
                .withArgument("x-dead-letter-exchange", MQ_DLX_EVENT)
                .withArgument("x-dead-letter-routing-key", MQ_DLQ_READ)
                .build();
    }

    @Bean
    public Binding messageReadBinding(
            @Qualifier("messageReadEventQueue") Queue queue,
            @Qualifier("eventExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_ROUTING_MESSAGE_READ_EVENT);
    }

    // ============================================================
    // 事件队列 — H. 消息送达事件（IM Router 消费）
    // ============================================================

    @Bean
    public Queue deliveredEventRouterQueue() {
        return QueueBuilder.durable(MQ_QUEUE_DELIVERED_EVENT_ROUTER)
                .withArgument("x-dead-letter-exchange", MQ_DLX_EVENT)
                .withArgument("x-dead-letter-routing-key", MQ_DLQ_DELIVERED_ROUTER)
                .build();
    }

    @Bean
    public Binding deliveredRouterBinding(
            @Qualifier("deliveredEventRouterQueue") Queue queue,
            @Qualifier("eventExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_ROUTING_DELIVERED_EVENT);
    }

    // ============================================================
    // 事件队列 — I. 消息失败事件（IM Router 消费）
    // ============================================================

    @Bean
    public Queue failedEventRouterQueue() {
        return QueueBuilder.durable(MQ_QUEUE_FAILED_EVENT_ROUTER)
                .withArgument("x-dead-letter-exchange", MQ_DLX_EVENT)
                .withArgument("x-dead-letter-routing-key", MQ_DLQ_FAILED_ROUTER)
                .build();
    }

    @Bean
    public Binding failedRouterBinding(
            @Qualifier("failedEventRouterQueue") Queue queue,
            @Qualifier("eventExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_ROUTING_FAILED_EVENT);
    }

    // ============================================================
    // 事件队列 — J. 消息发送失败事件（IM Router 消费）
    // ============================================================

    @Bean
    public Queue sendFailedEventRouterQueue() {
        return QueueBuilder.durable(MQ_QUEUE_SEND_FAILED_EVENT_ROUTER)
                .withArgument("x-dead-letter-exchange", MQ_DLX_EVENT)
                .withArgument("x-dead-letter-routing-key", MQ_DLQ_SEND_FAILED_ROUTER)
                .build();
    }

    @Bean
    public Binding sendFailedRouterBinding(
            @Qualifier("sendFailedEventRouterQueue") Queue queue,
            @Qualifier("eventExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_ROUTING_SEND_FAILED_EVENT);
    }

    // ============================================================
    // 事件死信队列（DLQ）— 每个消费者独立
    // ============================================================

    @Bean
    public Queue dlqDeliveredPlatformQueue() {
        return QueueBuilder.durable(MQ_DLQ_DELIVERED_PLATFORM).build();
    }

    @Bean
    public Binding dlqDeliveredPlatformBinding(
            @Qualifier("dlqDeliveredPlatformQueue") Queue queue,
            @Qualifier("eventDlxExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_DLQ_DELIVERED_PLATFORM);
    }

    @Bean
    public Queue dlqFailedPlatformQueue() {
        return QueueBuilder.durable(MQ_DLQ_FAILED_PLATFORM).build();
    }

    @Bean
    public Binding dlqFailedPlatformBinding(
            @Qualifier("dlqFailedPlatformQueue") Queue queue,
            @Qualifier("eventDlxExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_DLQ_FAILED_PLATFORM);
    }

    @Bean
    public Queue dlqOnlineQueue() {
        return QueueBuilder.durable(MQ_DLQ_ONLINE).build();
    }

    @Bean
    public Binding dlqOnlineBinding(
            @Qualifier("dlqOnlineQueue") Queue queue,
            @Qualifier("eventDlxExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_DLQ_ONLINE);
    }

    @Bean
    public Queue dlqOfflineQueue() {
        return QueueBuilder.durable(MQ_DLQ_OFFLINE).build();
    }

    @Bean
    public Binding dlqOfflineBinding(
            @Qualifier("dlqOfflineQueue") Queue queue,
            @Qualifier("eventDlxExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_DLQ_OFFLINE);
    }

    @Bean
    public Queue dlqReadQueue() {
        return QueueBuilder.durable(MQ_DLQ_READ).build();
    }

    @Bean
    public Binding dlqReadBinding(
            @Qualifier("dlqReadQueue") Queue queue,
            @Qualifier("eventDlxExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_DLQ_READ);
    }

    @Bean
    public Queue dlqDeliveredRouterQueue() {
        return QueueBuilder.durable(MQ_DLQ_DELIVERED_ROUTER).build();
    }

    @Bean
    public Binding dlqDeliveredRouterBinding(
            @Qualifier("dlqDeliveredRouterQueue") Queue queue,
            @Qualifier("eventDlxExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_DLQ_DELIVERED_ROUTER);
    }

    @Bean
    public Queue dlqFailedRouterQueue() {
        return QueueBuilder.durable(MQ_DLQ_FAILED_ROUTER).build();
    }

    @Bean
    public Binding dlqFailedRouterBinding(
            @Qualifier("dlqFailedRouterQueue") Queue queue,
            @Qualifier("eventDlxExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_DLQ_FAILED_ROUTER);
    }

    @Bean
    public Queue dlqSendFailedRouterQueue() {
        return QueueBuilder.durable(MQ_DLQ_SEND_FAILED_ROUTER).build();
    }

    @Bean
    public Binding dlqSendFailedRouterBinding(
            @Qualifier("dlqSendFailedRouterQueue") Queue queue,
            @Qualifier("eventDlxExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(MQ_DLQ_SEND_FAILED_ROUTER);
    }

    // ============================================================
    // RabbitTemplate 配置
    // ============================================================

    /**
     * 配置 RabbitTemplate
     * <ul>
     *   <li>使用默认 SimpleMessageConverter 支持 byte[] 传输（Protobuf 序列化）</li>
     *   <li>开启发送方确认（Publisher Confirms）</li>
     *   <li>开启返回回调（Return Callback）</li>
     * </ul>
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);

        // 发送方确认
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack && correlationData != null) {
                log.error("MQ 消息发送失败: id={}, cause={}", correlationData.getId(), cause);
            }
        });

        // 无法路由的消息回调
        template.setReturnsCallback(returned -> {
            log.error("MQ 消息无法路由: exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
        });

        template.setMandatory(true);
        return template;
    }
}
