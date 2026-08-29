package org.project.im.platform.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.constant.ImConstants;
import org.project.im.common.mq.IMMQProto;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static org.project.im.common.constant.ImConstants.*;

/**
 * IM 事件发布器
 * <p>
 * 统一管理 IM Business 服务向事件交换机发布各类事件消息。
 * 包括消息发送失败事件、消息送达事件、消息失败事件等。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布消息发送失败事件
     * <p>
     * 当 IM Business 消费 im.message.send.command 时，若权限校验失败、
     * 会话校验失败、数据库写入失败等，则产生此事件。
     * IM Router 消费后向发送方推送失败通知。
     * </p>
     *
     * @param messageId        原始消息 ID
     * @param senderId         发送方用户 ID
     * @param receiverId       接收方 ID
     * @param errorCode        失败原因代码（对应 SendFailedErrorCode 枚举值）
     * @param errorMessage     失败原因描述
     * @param conversationType 会话类型
     * @param conversationId   会话 ID
     */
    public void publishMessageSendFailedEvent(String messageId, String senderId, String receiverId,
                                               int errorCode, String errorMessage,
                                               int conversationType, String conversationId) {
        try {
            IMMQProto.MessageSendFailedEvent event = IMMQProto.MessageSendFailedEvent.newBuilder()
                    .setHeader(IMMQProto.MQMessageHeader.newBuilder()
                            .setEventType("im.message.send.failed.event")
                            .setMessageId(UUID.randomUUID().toString().replace("-", ""))
                            .setTimestamp(System.currentTimeMillis())
                            .setTraceId(UUID.randomUUID().toString().replace("-", ""))
                            .setSourceNode("im-platform")
                            .setRetryCount(0)
                            .build())
                    .setMessageId(messageId != null ? messageId : "")
                    .setSenderId(senderId != null ? senderId : "")
                    .setReceiverId(receiverId != null ? receiverId : "")
                    .setErrorCode(errorCode)
                    .setErrorMessage(errorMessage != null ? errorMessage : "")
                    .setTimestamp(System.currentTimeMillis())
                    .setConversationType(conversationType)
                    .setConversationId(conversationId != null ? conversationId : "")
                    .build();

            Message mqMessage = MessageBuilder
                    .withBody(event.toByteArray())
                    .setContentType(MessageProperties.CONTENT_TYPE_BYTES)
                    .setHeader("event_type", "im.message.send.failed.event")
                    .build();

            rabbitTemplate.send(MQ_EXCHANGE_EVENT, MQ_ROUTING_SEND_FAILED_EVENT, mqMessage);

            log.info("消息发送失败事件已发布: messageId={}, senderId={}, errorCode={}, reason={}",
                    messageId, senderId, errorCode, errorMessage);
        } catch (Exception e) {
            log.error("发布消息发送失败事件失败: messageId={}, senderId={}", messageId, senderId, e);
        }
    }
}