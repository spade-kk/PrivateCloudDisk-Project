package org.project.im.platform.mq;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

// ============================================================
// DeadLetterConsumer 单元测试 — 死信队列消费
// ============================================================
// 测试场景：
//   1. 消息发送命令死信 → 记录日志 + ACK
//   2. 消息推送命令死信 → 记录日志 + ACK
//   3. 处理异常时仍 ACK，避免阻塞死信队列
// ============================================================

@ExtendWith(MockitoExtension.class)
@DisplayName("DeadLetterConsumer 单元测试")
class DeadLetterConsumerTest {

    @Mock
    private Channel channel;

    @InjectMocks
    private DeadLetterConsumer deadLetterConsumer;

    private static final long DELIVERY_TAG = 1L;

    @Test
    @DisplayName("消息发送命令死信应记录日志并 ACK")
    void shouldAckSendCommandDeadLetter() throws Exception {
        byte[] deadMessage = new byte[]{0x01, 0x02, 0x03};
        Map<String, Object> xDeath = new HashMap<>();
        xDeath.put("count", 3L);
        xDeath.put("reason", "rejected");

        deadLetterConsumer.onSendCommandDeadLetter(deadMessage, channel, DELIVERY_TAG, xDeath);

        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("消息推送命令死信应记录日志并 ACK")
    void shouldAckPushCommandDeadLetter() throws Exception {
        byte[] deadMessage = new byte[]{0x04, 0x05, 0x06};

        deadLetterConsumer.onPushCommandDeadLetter(deadMessage, channel, DELIVERY_TAG, null);

        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("处理死信异常时仍应 ACK，避免阻塞")
    void shouldAckEvenOnException() throws Exception {
        // 模拟 ACK 时抛出异常（如 channel 已关闭）
        doThrow(new RuntimeException("Channel 已关闭"))
                .when(channel).basicAck(anyLong(), anyBoolean());

        // 不应抛出异常，内部已捕获
        deadLetterConsumer.onSendCommandDeadLetter(
                new byte[]{0x01}, channel, DELIVERY_TAG, null);

        // 验证至少尝试了 ACK
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("x-death 为 null 时正常处理")
    void shouldHandleNullXDeath() throws Exception {
        deadLetterConsumer.onSendCommandDeadLetter(
                new byte[]{0x01}, channel, DELIVERY_TAG, null);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("空消息体应正常处理")
    void shouldHandleEmptyMessage() throws Exception {
        deadLetterConsumer.onPushCommandDeadLetter(
                new byte[0], channel, DELIVERY_TAG, null);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }
}