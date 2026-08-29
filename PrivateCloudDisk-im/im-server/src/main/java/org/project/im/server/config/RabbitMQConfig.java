package org.project.im.server.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.project.im.common.constant.ImConstants.*;

// ============================================================
// RabbitMQ 配置 v2.0 — 命令队列 + 事件队列
// ============================================================
// 重构要点：
//   1. 移除旧版 TopicExchange（im.message.exchange）
//   2. 声明命令交换机（DirectExchange）— 精确路由命令到单一消费者
//   3. 声明事件交换机（TopicExchange）— 按路由键订阅，避免广播
//   4. RabbitTemplate 不使用 Jackson 转换器，直接发送 byte[]（Protobuf）
//      MQ 消息体统一使用 Protobuf 二进制序列化，与 Go IM Router 兼容
// ============================================================

/**
 * RabbitMQ 配置
 * <p>
 * 定义 IM 系统的 MQ 拓扑结构：
 * </p>
 *
 * <h3>命令交换机（im.command.exchange — DirectExchange）</h3>
 * <pre>
 * Producer → [im.command.exchange] → routingKey → Queue
 *   IM Server  → im.message.send.command → [im.message.send.command] → IM Business
 *   IM Business → im.message.push.command → [im.message.push.command] → IM Router
 * </pre>
 *
 * <h3>事件交换机（im.event.exchange — TopicExchange）</h3>
 * <pre>
 * Producer → [im.event.exchange] → routingKey → 匹配的消费者队列
 *   IM Server → UserOnlineEvent     (rk: im.user.online.event)       → [im.user.online.event.platform]
 *   IM Server → UserOfflineEvent    (rk: im.user.offline.event)      → [im.user.offline.event.platform]
 *   IM Server → MessageDeliveredEvent (rk: im.message.delivered.event) → [im.message.delivered.event.platform]
 *   IM Server → MessageFailedEvent    (rk: im.message.failed.event)    → [im.message.failed.event.platform]
 *   IM Server → MessageReadEvent      (rk: im.message.read.event)      → [im.message.read.event.platform]
 * </pre>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 命令交换机（Direct 类型，精确路由）
     * <p>
     * 用于命令队列：消息发送命令、消息推送命令。
     * 每条命令只路由到一个消费者执行。
     * </p>
     */
    @Bean
    public DirectExchange commandExchange() {
        return new DirectExchange(MQ_EXCHANGE_COMMAND, true, false);
    }

    /**
     * 事件交换机（Topic 类型，按路由键订阅）
     * <p>
     * 用于事件队列：用户上下线、消息送达/失败、消息已读。
     * 生产者按事件类型路由键发布，消费者队列按路由键订阅，避免不相关事件广播。
     * </p>
     */
    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(MQ_EXCHANGE_EVENT, true, false);
    }

    /**
     * RabbitTemplate
     * <p>
     * 使用默认的 SimpleMessageConverter，支持直接发送 byte[]（Protobuf 二进制）。
     * MQ 消息体统一使用 Protobuf 序列化，不使用 Jackson JSON 转换。
     * </p>
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // 不设置 MessageConverter，使用默认 SimpleMessageConverter
        // 直接发送 byte[] 时，SimpleMessageConverter 会以 application/octet-stream 传输
        return template;
    }
}
