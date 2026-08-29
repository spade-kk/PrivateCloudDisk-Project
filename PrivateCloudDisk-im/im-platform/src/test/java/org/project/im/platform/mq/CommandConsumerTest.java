package org.project.im.platform.mq;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.project.im.common.constant.ImConstants;
import org.project.im.common.dto.MessageDTO;
import org.project.im.common.dto.Result;
import org.project.im.common.mq.IMMQProto;
import org.project.im.common.protocol.v2.IMProtocolV2;
import org.project.im.platform.exception.ImBusinessException;
import org.project.im.platform.service.MessageService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// ============================================================
// CommandConsumer 单元测试 — 重试与死信逻辑
// ============================================================
// 测试场景：
//   1. 正常处理 → ACK
//   2. 业务校验失败（ImBusinessException）→ 发布发送失败事件 + ACK
//   3. 系统异常（retryCount < 3）→ NACK(requeue=false) → DLX
//   4. 系统异常（retryCount >= 3）→ 发布发送失败事件 + ACK
//   5. Result.code != 200 → 发布发送失败事件 + ACK
// ============================================================

@ExtendWith(MockitoExtension.class)
@DisplayName("CommandConsumer 单元测试")
class CommandConsumerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private Channel channel;

    @InjectMocks
    private CommandConsumer commandConsumer;

    private byte[] validSendCommand;
    private static final String SENDER_ID = "user_sender";
    private static final String RECEIVER_ID = "user_receiver";
    private static final long DELIVERY_TAG = 1L;

    @BeforeEach
    void setUp() throws Exception {
        // 构造一个合法的 SendMessageCommand 消息体
        IMMQProto.SendMessageCommand cmd = IMMQProto.SendMessageCommand.newBuilder()
                .setHeader(IMMQProto.MQMessageHeader.newBuilder()
                        .setEventType("im.message.send.command")
                        .setMessageId("msg-001")
                        .setTimestamp(System.currentTimeMillis())
                        .setTraceId("trace-001")
                        .setSourceNode("im-server-1")
                        .setRetryCount(0)
                        .build())
                .setSenderId(SENDER_ID)
                .setReceiverId(RECEIVER_ID)
                .setConversationType(1) // 单聊
                .setMessageType(IMProtocolV2.IMMessageType.TEXT.getNumber())
                .setPayloadBytes(IMProtocolV2.TextPayload.newBuilder()
                        .setContent("Hello").build().toByteString())
                .setClientSeq(1)
                .build();
        validSendCommand = cmd.toByteArray();
    }

    // ============================================================
    // 场景 1: 正常处理 → ACK
    // ============================================================

    @Nested
    @DisplayName("正常处理场景")
    class NormalProcessing {

        @Test
        @DisplayName("消息发送成功时应 ACK 确认")
        void shouldAckOnSuccess() throws Exception {
            // 模拟 MessageService 返回成功
            Result<MessageDTO> successResult = Result.success(
                    MessageDTO.builder().messageId("msg-001").build());
            when(messageService.sendMessage(any(MessageDTO.class))).thenReturn(successResult);

            // 调用消费者
            commandConsumer.onSendMessageCommand(validSendCommand, channel, DELIVERY_TAG, 0,
                    ImConstants.MQ_ROUTING_SEND_COMMAND);

            // 验证 ACK 被调用
            verify(channel).basicAck(DELIVERY_TAG, false);
            // 验证没有发布失败事件
            verify(eventPublisher, never()).publishMessageSendFailedEvent(
                    anyString(), anyString(), anyString(), anyInt(), anyString(), anyInt(), anyString());
        }
    }

    // ============================================================
    // 场景 2: ImBusinessException → 发布发送失败事件 + ACK
    // ============================================================

    @Nested
    @DisplayName("业务校验失败场景（不可重试）")
    class BusinessValidationFailure {

        @Test
        @DisplayName("权限校验失败（ImBusinessException）应发布发送失败事件并 ACK")
        void shouldPublishSendFailedEventAndAckOnImBusinessException() throws Exception {
            int errorCode = 1103; // NOT_FRIEND
            when(messageService.sendMessage(any(MessageDTO.class)))
                    .thenThrow(new ImBusinessException(errorCode, "非好友关系"));

            commandConsumer.onSendMessageCommand(validSendCommand, channel, DELIVERY_TAG, 0,
                    ImConstants.MQ_ROUTING_SEND_COMMAND);

            // 验证：不应 NACK
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
            // 验证：应 ACK 丢弃
            verify(channel).basicAck(DELIVERY_TAG, false);
            // 验证：应发布发送失败事件
            verify(eventPublisher).publishMessageSendFailedEvent(
                    eq("msg-001"), eq(SENDER_ID), eq(RECEIVER_ID),
                    eq(errorCode), contains("非好友关系"),
                    eq(1), anyString());
        }

        @Test
        @DisplayName("黑名单校验失败应发布发送失败事件并 ACK")
        void shouldPublishOnBlacklist() throws Exception {
            when(messageService.sendMessage(any(MessageDTO.class)))
                    .thenThrow(new ImBusinessException(1104, "已被对方拉黑"));

            commandConsumer.onSendMessageCommand(validSendCommand, channel, DELIVERY_TAG, 0,
                    ImConstants.MQ_ROUTING_SEND_COMMAND);

            verify(channel).basicAck(DELIVERY_TAG, false);
            verify(eventPublisher).publishMessageSendFailedEvent(
                    anyString(), anyString(), anyString(), eq(1104), anyString(), anyInt(), anyString());
        }

        @Test
        @DisplayName("禁言校验失败应发布发送失败事件并 ACK")
        void shouldPublishOnMuted() throws Exception {
            when(messageService.sendMessage(any(MessageDTO.class)))
                    .thenThrow(new ImBusinessException(1105, "已被全局禁言"));

            commandConsumer.onSendMessageCommand(validSendCommand, channel, DELIVERY_TAG, 0,
                    ImConstants.MQ_ROUTING_SEND_COMMAND);

            verify(channel).basicAck(DELIVERY_TAG, false);
            verify(eventPublisher).publishMessageSendFailedEvent(
                    anyString(), anyString(), anyString(), eq(1105), anyString(), anyInt(), anyString());
        }
    }

    // ============================================================
    // 场景 3: Result.code != 200 → 发布发送失败事件 + ACK
    // ============================================================

    @Nested
    @DisplayName("业务返回失败场景（Result.code != 200）")
    class ResultFailure {

        @Test
        @DisplayName("Result.code != 200 应发布发送失败事件并 ACK")
        void shouldPublishOnResultFailure() throws Exception {
            int errorCode = 1103; // NOT_FRIEND
            Result<MessageDTO> failResult = Result.error(errorCode, "非好友关系");
            when(messageService.sendMessage(any(MessageDTO.class))).thenReturn(failResult);

            commandConsumer.onSendMessageCommand(validSendCommand, channel, DELIVERY_TAG, 0,
                    ImConstants.MQ_ROUTING_SEND_COMMAND);

            verify(channel).basicAck(DELIVERY_TAG, false);
            verify(eventPublisher).publishMessageSendFailedEvent(
                    anyString(), anyString(), anyString(), eq(errorCode), anyString(), anyInt(), anyString());
        }
    }

    // ============================================================
    // 场景 4: 系统异常，retryCount < 3 → NACK(requeue=false) → DLX
    // ============================================================

    @Nested
    @DisplayName("系统异常重试场景")
    class SystemExceptionRetry {

        @Test
        @DisplayName("retryCount=0 时系统异常应 NACK(requeue=false) 进入 DLX")
        void shouldNackOnSystemExceptionWithRetryCount0() throws Exception {
            when(messageService.sendMessage(any(MessageDTO.class)))
                    .thenThrow(new RuntimeException("数据库连接超时"));

            commandConsumer.onSendMessageCommand(validSendCommand, channel, DELIVERY_TAG, 0,
                    ImConstants.MQ_ROUTING_SEND_COMMAND);

            // 验证：NACK(requeue=false) → DLX → retry queue
            verify(channel).basicNack(DELIVERY_TAG, false, false);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
            verify(eventPublisher, never()).publishMessageSendFailedEvent(
                    anyString(), anyString(), anyString(), anyInt(), anyString(), anyInt(), anyString());
        }

        @Test
        @DisplayName("retryCount=1 时系统异常应 NACK(requeue=false) 进入 DLX")
        void shouldNackOnSystemExceptionWithRetryCount1() throws Exception {
            when(messageService.sendMessage(any(MessageDTO.class)))
                    .thenThrow(new RuntimeException("数据库死锁"));

            commandConsumer.onSendMessageCommand(validSendCommand, channel, DELIVERY_TAG, 1,
                    ImConstants.MQ_ROUTING_SEND_COMMAND);

            verify(channel).basicNack(DELIVERY_TAG, false, false);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }

        @Test
        @DisplayName("retryCount=2 时系统异常应 NACK(requeue=false) 进入 DLX")
        void shouldNackOnSystemExceptionWithRetryCount2() throws Exception {
            when(messageService.sendMessage(any(MessageDTO.class)))
                    .thenThrow(new RuntimeException("临时网络故障"));

            commandConsumer.onSendMessageCommand(validSendCommand, channel, DELIVERY_TAG, 2,
                    ImConstants.MQ_ROUTING_SEND_COMMAND);

            verify(channel).basicNack(DELIVERY_TAG, false, false);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }
    }

    // ============================================================
    // 场景 5: 系统异常，retryCount >= 3 → 发布发送失败事件 + ACK
    // ============================================================

    @Nested
    @DisplayName("重试耗尽场景")
    class RetryExhausted {

        @Test
        @DisplayName("retryCount=3 时系统异常应发布 DB_ERROR 事件并 ACK")
        void shouldPublishDbErrorAndAckOnRetryExhausted() throws Exception {
            when(messageService.sendMessage(any(MessageDTO.class)))
                    .thenThrow(new RuntimeException("数据库写入失败（重试耗尽）"));

            commandConsumer.onSendMessageCommand(validSendCommand, channel, DELIVERY_TAG, 3,
                    ImConstants.MQ_ROUTING_SEND_COMMAND);

            // 验证：ACK 丢弃，消息进入 DLQ
            verify(channel).basicAck(DELIVERY_TAG, false);
            // 验证：发布 DB_ERROR 事件
            verify(eventPublisher).publishMessageSendFailedEvent(
                    anyString(), anyString(), anyString(),
                    eq(IMMQProto.SendFailedErrorCode.SEND_FAILED_DB_ERROR.getNumber()),
                    contains("重试耗尽"),
                    anyInt(), anyString());
        }

        @Test
        @DisplayName("retryCount=5 时（超过阈值）系统异常应发布 DB_ERROR 事件并 ACK")
        void shouldPublishDbErrorAndAckOnRetryExceeded() throws Exception {
            when(messageService.sendMessage(any(MessageDTO.class)))
                    .thenThrow(new RuntimeException("反复失败"));

            commandConsumer.onSendMessageCommand(validSendCommand, channel, DELIVERY_TAG, 5,
                    ImConstants.MQ_ROUTING_SEND_COMMAND);

            verify(channel).basicAck(DELIVERY_TAG, false);
            verify(eventPublisher).publishMessageSendFailedEvent(
                    anyString(), anyString(), anyString(),
                    eq(IMMQProto.SendFailedErrorCode.SEND_FAILED_DB_ERROR.getNumber()),
                    anyString(), anyInt(), anyString());
        }
    }

    // ============================================================
    // 场景 6: 边界条件
    // ============================================================

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCases {

        @Test
        @DisplayName("retryCount=null 时系统异常应视为 retryCount=0 处理")
        void shouldTreatNullRetryCountAsZero() throws Exception {
            when(messageService.sendMessage(any(MessageDTO.class)))
                    .thenThrow(new RuntimeException("未知异常"));

            // retryCount 为 null（defaultValue="0" 由 Spring 处理，但直接传 null 测试边界）
            commandConsumer.onSendMessageCommand(validSendCommand, channel, DELIVERY_TAG, null,
                    ImConstants.MQ_ROUTING_SEND_COMMAND);

            // NACK(requeue=false) → DLX，因为 null 被视为 0 < 3
            verify(channel).basicNack(DELIVERY_TAG, false, false);
        }

        @Test
        @DisplayName("空消息体应抛出解析异常，走系统异常重试逻辑")
        void shouldHandleEmptyMessageBody() throws Exception {
            commandConsumer.onSendMessageCommand(new byte[0], channel, DELIVERY_TAG, 0,
                    ImConstants.MQ_ROUTING_SEND_COMMAND);

            // 解析失败，走系统异常重试
            verify(channel).basicNack(DELIVERY_TAG, false, false);
        }
    }
}