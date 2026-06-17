package org.project.im.server.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.dto.MessageDTO;
import org.project.im.server.service.MessagePushService;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.project.im.common.constant.ImConstants.MQ_QUEUE_MESSAGE_PUSH;

/**
 * RabbitMQ 消息消费者
 * <p>
 * 监听消息推送队列，收到消息后通过 WebSocket 推送给目标用户。
 * 支持手动 ACK 模式，确保消息不丢失。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageConsumer {

    private final MessagePushService messagePushService;
    private final ObjectMapper objectMapper;

    /**
     * 消费消息推送队列
     * <p>
     * 收到消息后判断目标用户是否在线：
     * <ul>
     *   <li>在线：通过 WebSocket 直接推送</li>
     *   <li>离线：存储到 Redis 离线队列，等待用户上线同步</li>
     * </ul>
     * </p>
     *
     * @param message 消息内容（JSON 格式的 MessageDTO）
     * @param channel RabbitMQ Channel（用于手动 ACK）
     * @param deliveryTag 投递标签
     */
    @RabbitListener(
            queuesToDeclare = @Queue(name = "im.message.push.queue", durable = "true"),
            ackMode = "MANUAL"
    )
    public void onMessage(String message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            MessageDTO messageDTO = objectMapper.readValue(message, MessageDTO.class);
            messagePushService.pushToUser(messageDTO);
            // 手动确认
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("消息消费失败: deliveryTag={}, error={}", deliveryTag, e.getMessage());
            try {
                // 重新入队
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ex) {
                log.error("消息 NACK 失败", ex);
            }
        }
    }
}