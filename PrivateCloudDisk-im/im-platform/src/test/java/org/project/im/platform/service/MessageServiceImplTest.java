package org.project.im.platform.service;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.project.im.common.dto.MessageDTO;
import org.project.im.common.dto.Result;
import org.project.im.common.enums.MessageStatus;
import org.project.im.common.mq.IMMQProto;
import org.project.im.common.protocol.v2.IMProtocolV2;
import org.project.im.platform.entity.ImMessage;
import org.project.im.platform.mapper.ImConversationMapper;
import org.project.im.platform.mapper.ImMessageMapper;
import org.project.im.platform.service.impl.MessageServiceImpl;
import org.project.im.platform.util.SnowflakeIdGenerator;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageServiceImpl 单元测试（离线拉取与游标历史）")
class MessageServiceImplTest {

    @Mock private ImMessageMapper messageMapper;
    @Mock private ImConversationMapper conversationMapper;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private SnowflakeIdGenerator snowflakeIdGenerator;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private RedisTemplate<String, byte[]> byteArrayRedisTemplate;
    @Mock private ListOperations<String, byte[]> byteArrayListOps;

    @InjectMocks private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        lenient().when(byteArrayRedisTemplate.opsForList()).thenReturn(byteArrayListOps);
    }

    private ImMessage preparingMessage(String mid, String receiver) {
        return ImMessage.builder()
                .messageId(mid)
                .conversationId("00000000-0000-0000-0000-000000000001")
                .conversationType(1)
                .messageType(1)
                .senderId("00000000-0000-0000-0000-0000000000aa")
                .receiverId(receiver)
                .content("hello")
                .status(MessageStatus.PREPARING.getCode())
                .serverSeq(100L)
                .sendTime(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
    }

    // ============================================================
    // 离线消息拉取
    // ============================================================

    @Test
    @DisplayName("Redis 未命中时降级查询数据库，并批量标记为 DELIVERED")
    void getOfflineMessages_redisMiss_fallbackDb() {
        String userId = "00000000-0000-0000-0000-0000000000bb";
        when(byteArrayListOps.range(eq(String.format("im:offline:%s", userId)), eq(0L), eq(99L)))
                .thenReturn(null);
        ImMessage m1 = preparingMessage("9001", userId);
        when(messageMapper.selectOfflineMessages(userId, MessageStatus.PREPARING.getCode(), 100))
                .thenReturn(Arrays.asList(m1));

        Result<List<MessageDTO>> result = messageService.getOfflineMessages(userId, 100);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("9001", result.getData().get(0).getMessageId());
        // 拉取即标记为 DELIVERED
        verify(messageMapper).batchUpdateStatus(Collections.singletonList("9001"),
                MessageStatus.DELIVERED.getCode());
    }

    @Test
    @DisplayName("Redis 命中时解析 Protobuf 返回消息，并清空缓存、批量标记 DELIVERED")
    void getOfflineMessages_redisHit() {
        String userId = "00000000-0000-0000-0000-0000000000bb";
        // 构造 PushMessageCommand（含 IMEnvelope + TextPayload）
        byte[] payload = IMProtocolV2.TextPayload.newBuilder().setContent("离线文本").build().toByteArray();
        IMProtocolV2.IMEnvelope env = IMProtocolV2.IMEnvelope.newBuilder()
                .setVersion(2)
                .setMessageId("9100")
                .setCommand(IMProtocolV2.IMCommandType.SEND_MESSAGE)
                .setMessageType(IMProtocolV2.IMMessageType.TEXT)
                .setSenderId("00000000-0000-0000-0000-0000000000aa")
                .setReceiverId(userId)
                .setConversationId("00000000-0000-0000-0000-000000000001")
                .setConversationType(IMProtocolV2.IMConversationType.PRIVATE)
                .setServerSeq(200L)
                .setTimestamp(System.currentTimeMillis())
                .setEncryptedPayload(ByteString.copyFrom(payload))
                .build();
        IMMQProto.PushMessageCommand cmd = IMMQProto.PushMessageCommand.newBuilder()
                .setMessageId("9100")
                .setReceiverId(userId)
                .setEnvelopeBytes(ByteString.copyFrom(env.toByteArray()))
                .build();

        String key = String.format("im:offline:%s", userId);
        when(byteArrayListOps.range(eq(key), eq(0L), eq(99L)))
                .thenReturn(Collections.singletonList(cmd.toByteArray()));

        Result<List<MessageDTO>> result = messageService.getOfflineMessages(userId, 100);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        MessageDTO dto = result.getData().get(0);
        assertEquals("9100", dto.getMessageId());
        assertEquals("离线文本", dto.getContent());
        // 清空缓存 + 批量标记 DELIVERED
        verify(byteArrayRedisTemplate).delete(key);
        verify(messageMapper).batchUpdateStatus(Collections.singletonList("9100"),
                MessageStatus.DELIVERED.getCode());
    }

    // ============================================================
    // 游标历史查询
    // ============================================================

    @Test
    @DisplayName("游标历史仅查询已送达/已读/失败终态状态白名单")
    void getHistoryByCursor_onlyTerminalStatuses() {
        ImMessage delivered = preparingMessage("9201", "receiver");
        delivered.setStatus(MessageStatus.DELIVERED.getCode());
        when(messageMapper.selectHistoryByCursor(eq("conv-1"), anyList(), isNull(), isNull(), eq(20)))
                .thenReturn(Collections.singletonList(delivered));

        Result<List<MessageDTO>> result =
                messageService.getHistoryByCursor("conv-1", "user-1", 20, null, null);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("9201", result.getData().get(0).getMessageId());
        // 验证只传入终态白名单（不含 PREPARING=0）
        verify(messageMapper).selectHistoryByCursor(eq("conv-1"),
                eq(Arrays.asList(1, 2, 3)), isNull(), isNull(), eq(20));
    }
}
