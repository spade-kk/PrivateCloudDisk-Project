package org.project.im.platform.mq;

import lombok.extern.slf4j.Slf4j;
import org.project.im.common.constant.ImConstants;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;
import java.io.IOException;

import static org.project.im.common.constant.ImConstants.*;

/**
 * 死信队列消费者（DLQ Consumer）
 * <p>
 * 监听所有命令死信队列，将死信消息记录到日志系统并触发告警通知。
 * 死信消息不再自动重试，需运维人员手动分析处理。
 * </p>
 * <p>
 * 监听队列：
 * <ul>
 *   <li>dlq.send.command — 消息发送命令死信</li>
 *   <li>dlq.push.command — 消息推送命令死信</li>
 * </ul>
 * 事件死信队列由 IM Router 侧监控（Go 服务），此处仅处理 Java 侧可访问的命令死信。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class DeadLetterConsumer {

    // ============================================================
    // 命令死信队列 — 消息发送命令
    // ============================================================

    /**
     * 消费消息发送命令死信
     * <p>
     * 重试耗尽后进入此队列的消息，记录详细日志并触发告警。
     * 运维人员需根据日志中的 messageId 和 senderId 定位问题。
     * </p>
     */
    @RabbitListener(
            queues = ImConstants.MQ_DLQ_SEND_COMMAND,
            ackMode = "MANUAL"
    )
    public void onSendCommandDeadLetter(
            @Payload byte[] message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(value = "x-death", required = false) Object xDeath) throws IOException {
        try {
            log.error("========== 死信告警：消息发送命令进入死信队列 ==========");
            log.error("死信队列: {}", MQ_DLQ_SEND_COMMAND);
            log.error("消息体大小: {} bytes", message != null ? message.length : 0);
            log.error("x-death 信息: {}", xDeath);
            log.error("处理建议: 请检查 IM Business 日志，定位 messageId 对应的发送命令处理失败原因");
            log.error("================================================================");

            // 死信消息仅记录日志，ACK 确认
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理命令死信消息失败: {}", e.getMessage(), e);
            channel.basicAck(deliveryTag, false);
        }
    }

    // ============================================================
    // 命令死信队列 — 消息推送命令
    // ============================================================

    /**
     * 消费消息推送命令死信
     * <p>
     * IM Router 推送命令失败进入此队列，需检查 IM Router 与 IM Server 的连通性。
     * </p>
     */
    @RabbitListener(
            queues = ImConstants.MQ_DLQ_PUSH_COMMAND,
            ackMode = "MANUAL"
    )
    public void onPushCommandDeadLetter(
            @Payload byte[] message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(value = "x-death", required = false) Object xDeath) throws IOException {
        try {
            log.error("========== 死信告警：消息推送命令进入死信队列 ==========");
            log.error("死信队列: {}", MQ_DLQ_PUSH_COMMAND);
            log.error("消息体大小: {} bytes", message != null ? message.length : 0);
            log.error("x-death 信息: {}", xDeath);
            log.error("处理建议: 请检查 IM Router 与 IM Server 的 gRPC 连通性");
            log.error("================================================================");

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理推送命令死信消息失败: {}", e.getMessage(), e);
            channel.basicAck(deliveryTag, false);
        }
    }
}