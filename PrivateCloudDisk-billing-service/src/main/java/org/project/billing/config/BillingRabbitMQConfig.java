package org.project.billing.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 计费服务 RabbitMQ 消息拓扑配置
 *
 * 消息拓扑总览:
 * ┌─────────────────────────────────────────────────────────────────────────────────┐
 * │                            billing.exchange (Topic)                              │
 * │  ┌─────────────────────────────────────────────────────────────────────────────┐ │
 * │  │  Routing Keys:                                                              │ │
 * │  │    billing.payment.success    → 支付成功队列                                 │ │
 * │  │    billing.subscription.sync  → 订阅同步队列                                 │ │
 * │  │    billing.order.timeout      → 订单超时队列 (延迟)                           │ │
 * │  │    billing.quota.update       → 配额更新队列 (发往platform-service)           │ │
 * │  │    billing.overage.billing    → 超额计费队列                                 │ │
 * │  │    billing.invoice.issue      → 发票开具队列                                 │ │
 * │  └─────────────────────────────────────────────────────────────────────────────┘ │
 * └─────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────────────────────┐
 * │                         billing.dlx.exchange (死信交换机 - Direct)                │
 * │  ┌───────────────────────────────┐    ┌───────────────────────────────────────┐ │
 * │  │  billing.payment.dlq          │    │  billing.subscription.dlq              │ │
 * │  │  billing.overage.dlq          │    │  billing.invoice.dlq                   │ │
 * │  └───────────────────────────────┘    └───────────────────────────────────────┘ │
 * └─────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────────────────────┐
 * │            billing.delayed.exchange (延迟交换机 - x-delayed-message)              │
 * │  ┌─────────────────────────────────────────────────────────────────────────────┐ │
 * │  │  Routing Key: billing.order.timeout.delayed → 订单超时延迟队列                │ │
 * │  └─────────────────────────────────────────────────────────────────────────────┘ │
 * └─────────────────────────────────────────────────────────────────────────────────┘
 */
@Configuration
public class BillingRabbitMQConfig {

    // ============================================================
    // 交换机定义
    // ============================================================

    /** 计费业务主交换机 (Topic) */
    public static final String BILLING_EXCHANGE = "pcd.billing.exchange";

    /** 计费死信交换机 (Direct) */
    public static final String BILLING_DLX_EXCHANGE = "pcd.billing.exchange.dlx";

    /** 延迟消息交换机 (用于订单超时等场景) */
    public static final String BILLING_DELAYED_EXCHANGE = "pcd.billing.exchange.delayed";

    // ============================================================
    // 队列定义
    // ============================================================

    /** 支付成功处理队列 */
    public static final String PAYMENT_SUCCESS_QUEUE = "pcd.billing.payment.success.queue";

    /** 订阅同步队列 */
    public static final String SUBSCRIPTION_SYNC_QUEUE = "pcd.billing.subscription.sync.queue";

    /** 订单超时处理队列 (延迟队列) */
    public static final String ORDER_TIMEOUT_QUEUE = "pcd.billing.order.timeout.queue";

    /** 配额更新队列 (与 platform-service 共享) */
    public static final String QUOTA_UPDATE_QUEUE = "pcd.quota.update.queue";

    /** 超额计费队列 */
    public static final String OVERAGE_BILLING_QUEUE = "pcd.billing.overage.billing.queue";

    /** 发票开具队列 */
    public static final String INVOICE_ISSUE_QUEUE = "pcd.billing.invoice.issue.queue";

    // ============================================================
    // 死信队列定义
    // ============================================================

    /** 支付成功死信队列 */
    public static final String PAYMENT_SUCCESS_DLQ = "pcd.billing.payment.success.dlq";

    /** 订阅同步死信队列 */
    public static final String SUBSCRIPTION_SYNC_DLQ = "pcd.billing.subscription.sync.dlq";

    /** 超额计费死信队列 */
    public static final String OVERAGE_BILLING_DLQ = "pcd.billing.overage.billing.dlq";

    /** 发票开具死信队列 */
    public static final String INVOICE_ISSUE_DLQ = "pcd.billing.invoice.issue.dlq";

    // ============================================================
    // Routing Key 定义
    // ============================================================

    public static final String RK_PAYMENT_SUCCESS = "billing.payment.success";
    public static final String RK_SUBSCRIPTION_SYNC = "billing.subscription.sync";
    public static final String RK_ORDER_TIMEOUT = "billing.order.timeout";
    public static final String RK_ORDER_TIMEOUT_DELAYED = "billing.order.timeout.delayed";
    public static final String RK_QUOTA_UPDATE = "quota.update";
    public static final String RK_OVERAGE_BILLING = "billing.overage.billing";
    public static final String RK_INVOICE_ISSUE = "billing.invoice.issue";

    // ============================================================
    // 死信 Routing Key
    // ============================================================

    public static final String DLK_PAYMENT_SUCCESS = "billing.payment.success.dl";
    public static final String DLK_SUBSCRIPTION_SYNC = "billing.subscription.sync.dl";
    public static final String DLK_OVERAGE_BILLING = "billing.overage.billing.dl";
    public static final String DLK_INVOICE_ISSUE = "billing.invoice.issue.dl";

    // ============================================================
    // Bean 定义 - 交换机
    // ============================================================

    @Bean
    public TopicExchange billingExchange() {
        return new TopicExchange(BILLING_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange billingDlxExchange() {
        return new DirectExchange(BILLING_DLX_EXCHANGE, true, false);
    }

    @Bean
    public CustomExchange billingDelayedExchange() {
        return new CustomExchange(BILLING_DELAYED_EXCHANGE, "x-delayed-message", true, false,
                java.util.Map.of("x-delayed-type", "direct"));
    }

    // ============================================================
    // Bean 定义 - 业务队列 + 死信队列
    // ============================================================

    @Bean
    public Queue paymentSuccessQueue() {
        return QueueBuilder.durable(PAYMENT_SUCCESS_QUEUE)
                .deadLetterExchange(BILLING_DLX_EXCHANGE)
                .deadLetterRoutingKey(DLK_PAYMENT_SUCCESS)
                .build();
    }

    @Bean
    public Queue paymentSuccessDlq() {
        return QueueBuilder.durable(PAYMENT_SUCCESS_DLQ).build();
    }

    @Bean
    public Queue subscriptionSyncQueue() {
        return QueueBuilder.durable(SUBSCRIPTION_SYNC_QUEUE)
                .deadLetterExchange(BILLING_DLX_EXCHANGE)
                .deadLetterRoutingKey(DLK_SUBSCRIPTION_SYNC)
                .build();
    }

    @Bean
    public Queue subscriptionSyncDlq() {
        return QueueBuilder.durable(SUBSCRIPTION_SYNC_DLQ).build();
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return QueueBuilder.durable(ORDER_TIMEOUT_QUEUE)
                .deadLetterExchange(BILLING_DLX_EXCHANGE)
                .deadLetterRoutingKey("billing.order.timeout.dl")
                .build();
    }

    @Bean
    public Queue overageBillingQueue() {
        return QueueBuilder.durable(OVERAGE_BILLING_QUEUE)
                .deadLetterExchange(BILLING_DLX_EXCHANGE)
                .deadLetterRoutingKey(DLK_OVERAGE_BILLING)
                .build();
    }

    @Bean
    public Queue overageBillingDlq() {
        return QueueBuilder.durable(OVERAGE_BILLING_DLQ).build();
    }

    @Bean
    public Queue invoiceIssueQueue() {
        return QueueBuilder.durable(INVOICE_ISSUE_QUEUE)
                .deadLetterExchange(BILLING_DLX_EXCHANGE)
                .deadLetterRoutingKey(DLK_INVOICE_ISSUE)
                .build();
    }

    @Bean
    public Queue invoiceIssueDlq() {
        return QueueBuilder.durable(INVOICE_ISSUE_DLQ).build();
    }

    // ============================================================
    // Bean 定义 - 绑定关系
    // ============================================================

    @Bean
    public Binding bindPaymentSuccess() {
        return BindingBuilder.bind(paymentSuccessQueue())
                .to(billingExchange()).with(RK_PAYMENT_SUCCESS);
    }

    @Bean
    public Binding bindSubscriptionSync() {
        return BindingBuilder.bind(subscriptionSyncQueue())
                .to(billingExchange()).with(RK_SUBSCRIPTION_SYNC);
    }

    @Bean
    public Binding bindOrderTimeout() {
        return BindingBuilder.bind(orderTimeoutQueue())
                .to(billingDelayedExchange()).with(RK_ORDER_TIMEOUT_DELAYED).noargs();
    }

    @Bean
    public Binding bindOverageBilling() {
        return BindingBuilder.bind(overageBillingQueue())
                .to(billingExchange()).with(RK_OVERAGE_BILLING);
    }

    @Bean
    public Binding bindInvoiceIssue() {
        return BindingBuilder.bind(invoiceIssueQueue())
                .to(billingExchange()).with(RK_INVOICE_ISSUE);
    }

    // 死信绑定
    @Bean
    public Binding bindPaymentSuccessDlq() {
        return BindingBuilder.bind(paymentSuccessDlq())
                .to(billingDlxExchange()).with(DLK_PAYMENT_SUCCESS);
    }

    @Bean
    public Binding bindSubscriptionSyncDlq() {
        return BindingBuilder.bind(subscriptionSyncDlq())
                .to(billingDlxExchange()).with(DLK_SUBSCRIPTION_SYNC);
    }

    @Bean
    public Binding bindOverageBillingDlq() {
        return BindingBuilder.bind(overageBillingDlq())
                .to(billingDlxExchange()).with(DLK_OVERAGE_BILLING);
    }

    @Bean
    public Binding bindInvoiceIssueDlq() {
        return BindingBuilder.bind(invoiceIssueDlq())
                .to(billingDlxExchange()).with(DLK_INVOICE_ISSUE);
    }

    // ============================================================
    // 消息转换器
    // ============================================================

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public RabbitListenerContainerFactory<?> rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(10);
        return factory;
    }
}