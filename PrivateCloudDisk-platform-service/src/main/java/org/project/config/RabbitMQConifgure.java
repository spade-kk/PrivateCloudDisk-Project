package org.project.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ消息队列配置（企业级标准实现）
 *
 * <p>设计原则：
 * <ol>
 *   <li>每个消息类型使用独立队列，互不干扰</li>
 *   <li>每个业务队列配置对应的死信队列（DLQ），失败消息集中处理</li>
 *   <li>使用Topic交换机，支持灵活的路由key模式匹配</li>
 *   <li>使用Jackson JSON消息转换器，避免Java对象序列化问题</li>
 *   <li>消费者使用手动确认模式（ack/nack），保证消息至少被成功处理一次</li>
 * </ol>
 *
 * <p>队列拓扑图：
 * <pre>
 *                 [pcd.business.exchange (topic)]
 *                           │
 *         ┌─────────────────┼─────────────────┐
 *         │                 │                 │
 *   routing:welcome.email routing:welcome.sms  ...
 *         │                 │
 *         ▼                 ▼
 * [pcd.business.welcome.email]  [pcd.business.welcome.sms]
 *         │(dead-letter)           │(dead-letter)
 *         ▼                        ▼
 *   [pcd.business.exchange.dlx (topic)]
 *         │
 *   [pcd.business.dlq] (死信队列，集中存储失败消息)
 * </pre>
 */
@Configuration
public class RabbitMQConifgure {

    // ==================== 交换机定义 ====================

    /**
     * 业务消息主交换机（Topic类型，支持灵活路由）
     */
    public static final String BUSINESS_EXCHANGE = "pcd.business.exchange";

    /**
     * 业务消息死信交换机
     */
    public static final String BUSINESS_DLX_EXCHANGE = "pcd.business.exchange.dlx";

    // ==================== 业务队列与路由键 ====================
    // 用户注册 - 欢迎邮件
    public static final String QUEUE_WELCOME_EMAIL = "pcd.business.welcome.email";
    public static final String ROUTING_WELCOME_EMAIL = "user.registered.email";

    // 用户注册 - 欢迎短信
    public static final String QUEUE_WELCOME_SMS = "pcd.business.welcome.sms";
    public static final String ROUTING_WELCOME_SMS = "user.registered.sms";

    // 邮箱验证码
    public static final String QUEUE_EMAIL_VERIFICATION = "pcd.business.email.verification";
    public static final String ROUTING_EMAIL_VERIFICATION = "email.verification";

    // 手机验证码
    public static final String QUEUE_PHONE_VERIFICATION = "pcd.business.phone.verification";
    public static final String ROUTING_PHONE_VERIFICATION = "phone.verification";

    // 头像审核
    public static final String QUEUE_AVATAR_REVIEW = "pcd.business.avatar.review";
    public static final String ROUTING_AVATAR_REVIEW = "avatar.review";

    // 死信队列（所有业务消息失败后统一路由到此处）
    public static final String QUEUE_BUSINESS_DLQ = "pcd.business.dlq";
    public static final String ROUTING_DLQ = "business.dlq";

    // ==================== 现有队列（向后兼容） ====================
    public static final String BUSINESS_QUEUE = "pcd.business.queue";
    public static final String BUSINESS_ROUTING_KEY = "business.message";

    // 向后兼容：注册相关常量
    public static final String REGISTER_EXCHANGE = BUSINESS_EXCHANGE;
    public static final String REGISTER_ROUTING_KEY = BUSINESS_ROUTING_KEY;

    // 文件处理队列（由文件服务消费）
    public static final String FILE_PROCESS_QUEUE = "pcd.file.process.queue";
    public static final String FILE_PROCESS_EXCHANGE = "pcd.file.process.exchange";
    public static final String FILE_PROCESS_ROUTING_KEY = "file.process";

    // 文件处理死信交换机（与 Python Worker 保持一致）
    public static final String FILE_PROCESS_DLX = "pcd.file.process.dlx";
    public static final String FILE_PROCESS_DLQ = "pcd.file.process.dlq";
    public static final String FILE_PROCESS_DLQ_ROUTING_KEY = "file.process.dlq";

    // 文件删除队列（由文件服务消费）
    public static final String FILE_DELETE_QUEUE = "pcd.file.delete.queue";
    public static final String FILE_DELETE_EXCHANGE = "pcd.file.delete.exchange";
    public static final String FILE_DELETE_ROUTING_KEY = "file.delete";

    // 文件删除死信交换机（与 Python Worker 保持一致）
    public static final String FILE_DELETE_DLX = "pcd.file.delete.dlx";
    public static final String FILE_DELETE_DLQ = "pcd.file.delete.dlq";
    public static final String FILE_DELETE_DLQ_ROUTING_KEY = "file.delete.dlq";

    // 配额更新队列
    public static final String QUOTA_UPDATE_QUEUE = "pcd.quota.update.queue";
    public static final String QUOTA_UPDATE_EXCHANGE = "pcd.quota.update.exchange";
    public static final String QUOTA_UPDATE_ROUTING_KEY = "quota.update";

    // ==================== 文件事件交换机（预占+提交模式） ====================

    /** 文件事件主交换机（Topic），发布文件生命周期事件 */
    public static final String FILE_EVENT_EXCHANGE = "pcd.file.event.exchange";
    /** 文件事件死信交换机 */
    public static final String FILE_EVENT_DLX = "pcd.file.event.dlx";
    /** 文件事件死信队列 */
    public static final String FILE_EVENT_DLQ = "pcd.file.event.dlq";
    public static final String FILE_EVENT_DLQ_ROUTING_KEY = "file.event.dlq";

    // 文件可获得事件（消费者：主业务服务 → 提交配额）
    public static final String QUEUE_FILE_AVAILABLE = "pcd.file.available.queue";
    public static final String ROUTING_FILE_AVAILABLE = "file.available";

    // 文件合并失败事件（消费者：主业务服务 → 回滚配额）
    public static final String QUEUE_FILE_MERGE_FAILED = "pcd.file.merge.failed.queue";
    public static final String ROUTING_FILE_MERGE_FAILED = "file.merge.failed";

    // 文件扫毒失败事件（消费者：主业务服务 → 回滚配额）
    public static final String QUEUE_FILE_SCAN_FAILED = "pcd.file.scan.failed.queue";
    public static final String ROUTING_FILE_SCAN_FAILED = "file.scan.failed";

    // ==================== 上传会话事件交换机 ====================

    /** 上传会话事件主交换机（Topic），发布上传会话生命周期事件 */
    public static final String UPLOADS_EVENT_EXCHANGE = "pcd.uploads.event.exchange";
    /** 上传会话事件死信交换机 */
    public static final String UPLOADS_EVENT_DLX = "pcd.uploads.event.dlx";
    /** 上传会话事件死信队列 */
    public static final String UPLOADS_EVENT_DLQ = "pcd.uploads.event.dlq";
    public static final String UPLOADS_EVENT_DLQ_ROUTING_KEY = "uploads.event.dlq";

    // 上传会话删除事件（消费者：文件存储服务 → 删除物理分块文件）
    public static final String QUEUE_UPLOADS_SESSION_DELETE = "pcd.uploads.session.delete.queue";
    public static final String ROUTING_UPLOADS_SESSION_DELETE = "uploads.session.delete";

    // 上传会话已删除事件（消费者：主业务服务 → 释放配额）
    public static final String QUEUE_UPLOADS_SESSION_DELETED = "pcd.uploads.session.deleted.queue";
    public static final String ROUTING_UPLOADS_SESSION_DELETED = "uploads.session.deleted";

    // 消息存活时间（毫秒）：业务消息24小时
    public static final long MESSAGE_TTL_BUSINESS_MS = 24 * 60 * 60 * 1000L;

    // ==================== 交换机Bean ====================

    /**
     * 业务消息主交换机（Topic类型）
     * <p>Topic交换机支持通配符匹配，便于后续扩展新的消息类型而无需修改交换机。
     */
    @Bean
    public TopicExchange businessExchange() {
        return ExchangeBuilder
                .topicExchange(BUSINESS_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 业务消息死信交换机（Topic类型）
     * <p>所有业务队列的失败消息都会转发到这里，由死信消费者统一处理。
     */
    @Bean
    public TopicExchange businessDlxExchange() {
        return ExchangeBuilder
                .topicExchange(BUSINESS_DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    // ==================== 业务队列Bean（带死信配置） ====================

    /**
     * 欢迎邮件队列：绑定到主交换机，失败消息转发到DLX
     */
    @Bean
    public Queue welcomeEmailQueue() {
        return QueueBuilder
                .durable(QUEUE_WELCOME_EMAIL)
                .deadLetterExchange(BUSINESS_DLX_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_DLQ)
                .ttl((int) MESSAGE_TTL_BUSINESS_MS)
                .build();
    }

    /**
     * 欢迎短信队列
     */
    @Bean
    public Queue welcomeSmsQueue() {
        return QueueBuilder
                .durable(QUEUE_WELCOME_SMS)
                .deadLetterExchange(BUSINESS_DLX_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_DLQ)
                .ttl((int) MESSAGE_TTL_BUSINESS_MS)
                .build();
    }

    /**
     * 邮箱验证码队列
     */
    @Bean
    public Queue emailVerificationQueue() {
        return QueueBuilder
                .durable(QUEUE_EMAIL_VERIFICATION)
                .deadLetterExchange(BUSINESS_DLX_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_DLQ)
                .ttl(10 * 60 * 1000) // 验证码消息存活10分钟
                .build();
    }

    /**
     * 手机验证码队列
     */
    @Bean
    public Queue phoneVerificationQueue() {
        return QueueBuilder
                .durable(QUEUE_PHONE_VERIFICATION)
                .deadLetterExchange(BUSINESS_DLX_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_DLQ)
                .ttl(10 * 60 * 1000)
                .build();
    }

    /**
     * 头像审核队列
     */
    @Bean
    public Queue avatarReviewQueue() {
        return QueueBuilder
                .durable(QUEUE_AVATAR_REVIEW)
                .deadLetterExchange(BUSINESS_DLX_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_DLQ)
                .ttl((int) MESSAGE_TTL_BUSINESS_MS)
                .build();
    }

    /**
     * 死信队列：集中存放所有失败的业务消息
     */
    @Bean
    public Queue businessDlq() {
        return QueueBuilder
                .durable(QUEUE_BUSINESS_DLQ)
                .build();
    }

    // ==================== 原有队列（向后兼容） ====================

    @Bean
    public Queue businessQueue() {
        return QueueBuilder
                .durable(BUSINESS_QUEUE)
                .withArgument("x-message-ttl", MESSAGE_TTL_BUSINESS_MS)
                .build();
    }

    @Bean
    public DirectExchange businessCompatExchange() {
        return new DirectExchange("pcd.business.compat.exchange");
    }

    @Bean
    public Binding businessCompatBinding() {
        return BindingBuilder
                .bind(businessQueue())
                .to(businessCompatExchange())
                .with(BUSINESS_ROUTING_KEY);
    }

    // ==================== 文件相关队列（用于发送消息，文件服务消费） ====================

    /**
     * 文件处理死信交换机（与 Python Worker 保持一致）
     */
    @Bean
    public DirectExchange fileProcessDlxExchange() {
        return new DirectExchange(FILE_PROCESS_DLX);
    }

    /**
     * 文件处理死信队列（与 Python Worker 保持一致的 30 天 TTL）
     */
    @Bean
    public Queue fileProcessDlq() {
        return QueueBuilder
                .durable(FILE_PROCESS_DLQ)
                .withArgument("x-message-ttl", 2592000000L) // 30 天，与 Python Worker 一致
                .build();
    }

    @Bean
    public Binding fileProcessDlqBinding() {
        return BindingBuilder
                .bind(fileProcessDlq())
                .to(fileProcessDlxExchange())
                .with(FILE_PROCESS_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue fileProcessQueue() {
        return QueueBuilder
                .durable(FILE_PROCESS_QUEUE)
                .deadLetterExchange(FILE_PROCESS_DLX)
                .deadLetterRoutingKey(FILE_PROCESS_DLQ_ROUTING_KEY)
                .ttl(7 * 24 * 60 * 60 * 1000)
                .build();
    }

    @Bean
    public DirectExchange fileProcessExchange() {
        return new DirectExchange(FILE_PROCESS_EXCHANGE);
    }

    @Bean
    public Binding fileProcessBinding() {
        return BindingBuilder
                .bind(fileProcessQueue())
                .to(fileProcessExchange())
                .with(FILE_PROCESS_ROUTING_KEY);
    }

    /**
     * 文件删除死信交换机（与 Python Worker 保持一致）
     */
    @Bean
    public DirectExchange fileDeleteDlxExchange() {
        return new DirectExchange(FILE_DELETE_DLX);
    }

    /**
     * 文件删除死信队列（与 Python Worker 保持一致的 30 天 TTL）
     */
    @Bean
    public Queue fileDeleteDlq() {
        return QueueBuilder
                .durable(FILE_DELETE_DLQ)
                .withArgument("x-message-ttl", 2592000000L) // 30 天，与 Python Worker 一致
                .build();
    }

    @Bean
    public Binding fileDeleteDlqBinding() {
        return BindingBuilder
                .bind(fileDeleteDlq())
                .to(fileDeleteDlxExchange())
                .with(FILE_DELETE_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue fileDeleteQueue() {
        return QueueBuilder
                .durable(FILE_DELETE_QUEUE)
                .deadLetterExchange(FILE_DELETE_DLX)
                .deadLetterRoutingKey(FILE_DELETE_DLQ_ROUTING_KEY)
                .ttl(3 * 24 * 60 * 60 * 1000)
                .build();
    }

    @Bean
    public DirectExchange fileDeleteExchange() {
        return new DirectExchange(FILE_DELETE_EXCHANGE);
    }

    @Bean
    public Binding fileDeleteBinding() {
        return BindingBuilder
                .bind(fileDeleteQueue())
                .to(fileDeleteExchange())
                .with(FILE_DELETE_ROUTING_KEY);
    }

    // ==================== 配额更新队列 ====================

    @Bean
    public Queue quotaUpdateQueue() {
        return QueueBuilder
                .durable(QUOTA_UPDATE_QUEUE)
                .withArgument("x-message-ttl", 60 * 60 * 1000L)
                .build();
    }

    @Bean
    public DirectExchange quotaUpdateExchange() {
        return new DirectExchange(QUOTA_UPDATE_EXCHANGE);
    }

    @Bean
    public Binding quotaUpdateBinding() {
        return BindingBuilder
                .bind(quotaUpdateQueue())
                .to(quotaUpdateExchange())
                .with(QUOTA_UPDATE_ROUTING_KEY);
    }

    // ==================== 文件事件交换机及队列 ====================

    /**
     * 文件事件主交换机（Topic）
     * <p>发布文件生命周期事件：file.available, file.merge.failed, file.scan.failed
     */
    @Bean
    public TopicExchange fileEventExchange() {
        return ExchangeBuilder
                .topicExchange(FILE_EVENT_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 文件事件死信交换机（Topic）
     */
    @Bean
    public TopicExchange fileEventDlxExchange() {
        return ExchangeBuilder
                .topicExchange(FILE_EVENT_DLX)
                .durable(true)
                .build();
    }

    /**
     * 文件事件死信队列（集中存放所有失败的文件事件消息）
     */
    @Bean
    public Queue fileEventDlq() {
        return QueueBuilder
                .durable(FILE_EVENT_DLQ)
                .build();
    }

    @Bean
    public Binding fileEventDlqBinding() {
        return BindingBuilder
                .bind(fileEventDlq())
                .to(fileEventDlxExchange())
                .with(FILE_EVENT_DLQ_ROUTING_KEY);
    }

    /**
     * 文件可获得事件队列（消费者：主业务服务 → 提交配额）
     */
    @Bean
    public Queue fileAvailableQueue() {
        return QueueBuilder
                .durable(QUEUE_FILE_AVAILABLE)
                .deadLetterExchange(FILE_EVENT_DLX)
                .deadLetterRoutingKey(FILE_EVENT_DLQ_ROUTING_KEY)
                .ttl(7 * 24 * 60 * 60 * 1000) // 7 天
                .build();
    }

    @Bean
    public Binding fileAvailableBinding() {
        return BindingBuilder
                .bind(fileAvailableQueue())
                .to(fileEventExchange())
                .with(ROUTING_FILE_AVAILABLE);
    }

    /**
     * 文件合并失败事件队列（消费者：主业务服务 → 回滚配额）
     */
    @Bean
    public Queue fileMergeFailedQueue() {
        return QueueBuilder
                .durable(QUEUE_FILE_MERGE_FAILED)
                .deadLetterExchange(FILE_EVENT_DLX)
                .deadLetterRoutingKey(FILE_EVENT_DLQ_ROUTING_KEY)
                .ttl(7 * 24 * 60 * 60 * 1000)
                .build();
    }

    @Bean
    public Binding fileMergeFailedBinding() {
        return BindingBuilder
                .bind(fileMergeFailedQueue())
                .to(fileEventExchange())
                .with(ROUTING_FILE_MERGE_FAILED);
    }

    /**
     * 文件扫毒失败事件队列（消费者：主业务服务 → 回滚配额）
     */
    @Bean
    public Queue fileScanFailedQueue() {
        return QueueBuilder
                .durable(QUEUE_FILE_SCAN_FAILED)
                .deadLetterExchange(FILE_EVENT_DLX)
                .deadLetterRoutingKey(FILE_EVENT_DLQ_ROUTING_KEY)
                .ttl(7 * 24 * 60 * 60 * 1000)
                .build();
    }

    @Bean
    public Binding fileScanFailedBinding() {
        return BindingBuilder
                .bind(fileScanFailedQueue())
                .to(fileEventExchange())
                .with(ROUTING_FILE_SCAN_FAILED);
    }

    // ==================== 上传会话事件交换机及队列 ====================

    /**
     * 上传会话事件主交换机（Topic）
     * <p>发布上传会话生命周期事件：uploads.session.delete, uploads.session.deleted
     */
    @Bean
    public TopicExchange uploadsEventExchange() {
        return ExchangeBuilder
                .topicExchange(UPLOADS_EVENT_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 上传会话事件死信交换机（Topic）
     */
    @Bean
    public TopicExchange uploadsEventDlxExchange() {
        return ExchangeBuilder
                .topicExchange(UPLOADS_EVENT_DLX)
                .durable(true)
                .build();
    }

    /**
     * 上传会话事件死信队列
     */
    @Bean
    public Queue uploadsEventDlq() {
        return QueueBuilder
                .durable(UPLOADS_EVENT_DLQ)
                .build();
    }

    @Bean
    public Binding uploadsEventDlqBinding() {
        return BindingBuilder
                .bind(uploadsEventDlq())
                .to(uploadsEventDlxExchange())
                .with(UPLOADS_EVENT_DLQ_ROUTING_KEY);
    }

    /**
     * 上传会话删除事件队列（消费者：文件存储服务 → 删除物理分块文件）
     * <p>TTL 较短，因为删除操作时效性要求高
     */
    @Bean
    public Queue uploadsSessionDeleteQueue() {
        return QueueBuilder
                .durable(QUEUE_UPLOADS_SESSION_DELETE)
                .deadLetterExchange(UPLOADS_EVENT_DLX)
                .deadLetterRoutingKey(UPLOADS_EVENT_DLQ_ROUTING_KEY)
                .ttl(1 * 24 * 60 * 60 * 1000) // 1 天
                .build();
    }

    @Bean
    public Binding uploadsSessionDeleteBinding() {
        return BindingBuilder
                .bind(uploadsSessionDeleteQueue())
                .to(uploadsEventExchange())
                .with(ROUTING_UPLOADS_SESSION_DELETE);
    }

    /**
     * 上传会话已删除事件队列（消费者：主业务服务 → 释放配额）
     */
    @Bean
    public Queue uploadsSessionDeletedQueue() {
        return QueueBuilder
                .durable(QUEUE_UPLOADS_SESSION_DELETED)
                .deadLetterExchange(UPLOADS_EVENT_DLX)
                .deadLetterRoutingKey(UPLOADS_EVENT_DLQ_ROUTING_KEY)
                .ttl(3 * 24 * 60 * 60 * 1000) // 3 天
                .build();
    }

    @Bean
    public Binding uploadsSessionDeletedBinding() {
        return BindingBuilder
                .bind(uploadsSessionDeletedQueue())
                .to(uploadsEventExchange())
                .with(ROUTING_UPLOADS_SESSION_DELETED);
    }

    // ==================== 绑定关系（主交换机 -> 业务队列） ====================

    @Bean
    public Binding bindWelcomeEmailQueue() {
        return BindingBuilder
                .bind(welcomeEmailQueue())
                .to(businessExchange())
                .with(ROUTING_WELCOME_EMAIL);
    }

    @Bean
    public Binding bindWelcomeSmsQueue() {
        return BindingBuilder
                .bind(welcomeSmsQueue())
                .to(businessExchange())
                .with(ROUTING_WELCOME_SMS);
    }

    @Bean
    public Binding bindEmailVerificationQueue() {
        return BindingBuilder
                .bind(emailVerificationQueue())
                .to(businessExchange())
                .with(ROUTING_EMAIL_VERIFICATION);
    }

    @Bean
    public Binding bindPhoneVerificationQueue() {
        return BindingBuilder
                .bind(phoneVerificationQueue())
                .to(businessExchange())
                .with(ROUTING_PHONE_VERIFICATION);
    }

    @Bean
    public Binding bindAvatarReviewQueue() {
        return BindingBuilder
                .bind(avatarReviewQueue())
                .to(businessExchange())
                .with(ROUTING_AVATAR_REVIEW);
    }

    // ==================== 绑定关系（死信交换机 -> 死信队列） ====================

    @Bean
    public Binding bindBusinessDlq() {
        return BindingBuilder
                .bind(businessDlq())
                .to(businessDlxExchange())
                .with(ROUTING_DLQ);
    }

    // ==================== 消息序列化配置 ====================

    /**
     * JSON消息转换器
     * <p>使用Jackson将Java对象序列化为JSON消息体，
     * 这样消费者（无论使用什么语言）都能轻松解析，
     * 同时避免了Java原生序列化的安全和版本问题。
     */
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 注册Java 8时间类型（LocalDateTime等）的序列化支持
        objectMapper.registerModule(new JavaTimeModule());
        // 禁用将日期写成时间戳
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * RabbitTemplate：用于发送消息
     * <p>使用JSON转换器，设置mandatory=true以便消息无法路由时能被生产者感知。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter
    ) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        // mandatory=true：消息无法路由到时触发ReturnCallback（便于调试和监控）
        rabbitTemplate.setMandatory(true);
        return rabbitTemplate;
    }

    /**
     * 手动确认消费者容器工厂
     * <p>
     * 关键配置：
     * <ul>
     *   <li>acknowledgeMode=MANUAL：业务代码必须显式调用channel.basicAck()/basicNack()</li>
     *   <li>prefetch=1：每个消费者一次只取1条消息，避免消息堆积在单个消费者</li>
     *   <li>concurrent=2，maxConcurrent=5：根据压力自动扩展/收缩消费者线程数</li>
     *   <li>defaultRequeueRejected=false：reject时消息不回队列，交给死信交换机</li>
     * </ul>
     */
    @Bean
    public SimpleRabbitListenerContainerFactory manualRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);

        // 手动确认模式
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);

        // 每个消费者一次只拿1条消息，适合邮件/短信这类外部IO调用
        factory.setPrefetchCount(1);

        // 并发消费者数量
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);

        // 被reject的消息不重新入队（转发到死信队列）
        factory.setDefaultRequeueRejected(false);

        return factory;
    }
}
