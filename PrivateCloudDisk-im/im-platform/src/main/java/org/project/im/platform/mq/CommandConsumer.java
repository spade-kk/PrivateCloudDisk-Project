package org.project.im.platform.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.constant.ImConstants;
import org.project.im.common.dto.MessageDTO;
import org.project.im.common.dto.Result;
import org.project.im.common.mq.IMMQProto;
import org.project.im.common.protocol.v2.IMProtocolV2;
import org.project.im.platform.exception.ImBusinessException;
import org.project.im.platform.service.MessageService;
import org.project.im.platform.util.MessagePayloadCodec;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.project.im.platform.util.ConversationIdGenerator;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.Map;

import static org.project.im.common.constant.ImConstants.*;

// ============================================================
// IM Platform MQ 命令消费者（含重试与死信处理）
// ============================================================
// 消费来自 IM Server 的命令：
//   A. im.message.send.command — 消息发送命令
//
// 失败处理策略：
//   - 不可重试错误（权限校验失败、会话校验失败、消息格式错误）：
//     发布 im.message.send.failed.event → ACK，不进入死信队列。
//   - 可重试错误（数据库写入失败、系统异常）：
//     retry_count < 3：NACK(requeue=false) → DLX → retry queue(TTL 5s) → 原队列
//     retry_count >= 3：发布 im.message.send.failed.event → ACK（进入命令 DLQ 由 DLX 兜底）
// ============================================================

/**
 * IM Platform 命令消费者
 * <p>
 * 消费 IM Server 通过 WebSocket 通道提交的消息发送命令，
 * 执行与 HTTP REST API 相同的权限校验和业务处理逻辑。
 * 集成重试机制和死信队列处理。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandConsumer {

    private final MessageService messageService;
    private final EventPublisher eventPublisher;
    private final RabbitTemplate rabbitTemplate;

    // ============================================================
    // A. 消息发送命令 — IM Server → IM Business
    // ============================================================

    /**
     * 消费消息发送命令
     * <p>
     * 处理流程：
     * <ol>
     *   <li>解析 SendMessageCommand，提取发送者、接收者、消息内容等</li>
     *   <li>执行权限校验（好友关系、黑名单、禁言等，与 HTTP 接口一致）</li>
     *   <li>消息持久化到数据库，状态为 SENDING</li>
     *   <li>产生 PushMessageCommand，发布到 im.message.push.command 队列</li>
     * </ol>
     * </p>
     * <p>
     * 失败处理：
     * <ul>
     *   <li>ImBusinessException（权限/会话校验失败）→ 不可重试，发布发送失败事件</li>
     *   <li>其他异常 → 可重试，通过 DLX 延迟重试，超过 3 次发布发送失败事件</li>
     * </ul>
     * </p>
     */
    @RabbitListener(
            queues = ImConstants.MQ_QUEUE_SEND_COMMAND,
            ackMode = "MANUAL"
    )
    public void onSendMessageCommand(
            @Payload byte[] message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(value = "x-retry-count", required = false, defaultValue = "0") Integer retryCount,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) throws IOException {

        String senderId = null;
        String receiverId = null;
        int conversationType = 0;
        String messageId = null;
        String conversationId = null;

        try {
            IMMQProto.SendMessageCommand command = IMMQProto.SendMessageCommand.parseFrom(message);
            senderId = command.getSenderId();
            receiverId = command.getReceiverId();
            conversationType = command.getConversationType();
            messageId = command.getHeader().getMessageId();
            conversationId = ConversationIdGenerator.generate(senderId, receiverId, conversationType);

            log.info("收到消息发送命令: sender={}, receiver={}, retryCount={}",
                    senderId, receiverId, retryCount);

            // AUDIT FIX [5.4/14.5/14.25]：原行为只解析 TEXT，所有 WebSocket 富媒体消息
            // 在持久化前都会变成 content=null。新行为统一解析权威 Protobuf payload，
            // 并把结构化字段保存到 extra.payload，供历史接口与后续 Router 推送重建负载。
            MessagePayloadCodec.Decoded decoded;
            try {
                decoded = MessagePayloadCodec.decode(
                        command.getMessageType(), command.getPayloadBytes().toByteArray(), command.getExtraJson());
            } catch (Exception malformedPayload) {
                // 格式错误由同一客户端重复投递不会自行恢复，必须按不可重试业务错误处理，
                // 避免占满 retry/DLQ；外层 ImBusinessException 分支会发布明确失败回执。
                throw new ImBusinessException(400, "Protobuf 消息负载无效: " + malformedPayload.getMessage());
            }

            // 构建 MessageDTO，复用 HTTP 接口的业务逻辑
            MessageDTO messageDTO = MessageDTO.builder()
                    .senderId(senderId)
                    .receiverId(receiverId)
                    .conversationType(conversationType)
                    .messageType(command.getMessageType())
                    .content(decoded.content())
                    .replyTo(command.getReplyTo().isEmpty() ? null : command.getReplyTo())
                    .extra(decoded.extra())
                    .clientSeq(command.getClientSeq())
                    .conversationId(conversationId)
                    .build();

            // 调用 MessageService 执行权限校验 + 持久化 + 推送
            Result<MessageDTO> result = messageService.sendMessage(messageDTO);

            if (result.getCode() == 200) {
                log.info("消息发送命令处理成功: messageId={}", result.getData().getMessageId());
                channel.basicAck(deliveryTag, false);
            } else {
                // 业务校验失败（如非好友、被拉黑等）—— 不可重试
                log.warn("消息发送命令业务校验失败: code={}, message={}, sender={}, receiver={}",
                        result.getCode(), result.getMessage(), senderId, receiverId);
                publishSendFailedEvent(messageId, senderId, receiverId,
                        result.getCode(), result.getMessage(), conversationType, conversationId);
                // 不可重试错误，直接 ACK 丢弃
                channel.basicAck(deliveryTag, false);
            }
        } catch (ImBusinessException e) {
            // 权限校验失败、会话校验失败等 —— 不可重试
            log.warn("消息发送命令权限校验失败: code={}, message={}, sender={}, receiver={}",
                    e.getCode(), e.getMessage(), senderId, receiverId);
            publishSendFailedEvent(messageId, senderId, receiverId,
                    e.getCode(), e.getMessage(), conversationType, conversationId);
            // 不可重试错误，直接 ACK 丢弃
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 系统异常（数据库写入失败、网络故障等）—— 可重试
            int currentRetry = retryCount != null ? retryCount : 0;

            if (currentRetry < COMMAND_MAX_RETRY_COUNT) {
                log.warn("消息发送命令处理异常，将重试: retryCount={}/{}, sender={}, receiver={}, error={}",
                        currentRetry, COMMAND_MAX_RETRY_COUNT, senderId, receiverId, e.getMessage());
                // NACK(requeue=false) → DLX → retry queue(TTL 5s) → 原队列
                // 重试次数在重发布时通过消息头递增
                channel.basicNack(deliveryTag, false, false);
            } else {
                log.error("消息发送命令已达最大重试次数，发布发送失败事件: retryCount={}, sender={}, receiver={}",
                        currentRetry, senderId, receiverId, e);
                publishSendFailedEvent(messageId, senderId, receiverId,
                        IMMQProto.SendFailedErrorCode.SEND_FAILED_DB_ERROR.getNumber(),
                        "系统处理异常，重试耗尽: " + e.getMessage(),
                        conversationType, conversationId);
                // ACK 丢弃，消息进入 DLQ 由 DLX 兜底（如果 DLX 未配置，消息丢失）
                channel.basicAck(deliveryTag, false);
            }
        }
    }

    /**
     * 发布消息发送失败事件到事件总线
     */
    private void publishSendFailedEvent(String messageId, String senderId, String receiverId,
                                         int errorCode, String errorMessage,
                                         int conversationType, String conversationId) {
        eventPublisher.publishMessageSendFailedEvent(
                messageId, senderId, receiverId, errorCode, errorMessage,
                conversationType, conversationId);
    }

}
