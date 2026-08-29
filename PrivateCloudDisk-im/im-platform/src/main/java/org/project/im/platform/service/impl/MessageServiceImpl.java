package org.project.im.platform.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.constant.ImConstants;
import org.project.im.common.dto.MessageDTO;
import org.project.im.common.dto.Result;
import org.project.im.common.enums.MessageStatus;
import org.project.im.common.enums.ResponseCode;
import org.project.im.common.mq.IMMQProto;
import org.project.im.common.protocol.v2.IMProtocolV2;
import org.project.im.platform.entity.ImMessage;
import org.project.im.platform.exception.ImBusinessException;
import org.project.im.platform.client.PlatformUserDirectoryClient;
import org.project.im.platform.mapper.ImConversationMapper;
import org.project.im.platform.mapper.ImFriendshipMapper;
import org.project.im.platform.mapper.ImGroupMapper;
import org.project.im.platform.mapper.ImGroupMemberMapper;
import org.project.im.platform.mapper.ImMessageMapper;
import org.project.im.platform.service.ConversationSummaryCache;
import org.project.im.platform.service.MessageService;
import org.project.im.platform.util.SnowflakeIdGenerator;
import org.project.im.platform.util.MessagePayloadCodec;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.google.protobuf.ByteString;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.project.im.common.constant.ImConstants.*;

// ============================================================
// 消息服务实现 — 分布式架构 v2.0
// ============================================================
// 重构要点：
//   1. 新增消息发送前置权限校验（发送方/接收方存在性、好友关系、黑名单、禁言）
//   2. 消息初始状态改为 SENDING（原为 SENT），由送达事件驱动状态流转
//   3. 推送方式改为发布 PushMessageCommand 到命令队列（原为直接推送到旧交换机）
//   4. 离线消息补偿由 IM Router 负责存储，IM Business 负责触发补偿流程
// ============================================================

/**
 * 消息服务实现
 * <p>
 * 消息的核心处理逻辑：
 * <ul>
 *   <li>发送消息：权限校验 → 生成消息 ID → 持久化（SENDING）→ 发布 PushMessageCommand</li>
 *   <li>撤回消息：校验 2 分钟时限 → 更新状态 → 通知客户端</li>
 *   <li>已读标记：批量更新消息状态 → 清零会话未读数</li>
 *   <li>历史消息：支持分页查询和增量拉取</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final ImMessageMapper messageMapper;
    private final ImConversationMapper conversationMapper;
    private final ImFriendshipMapper friendshipMapper;
    private final ImGroupMapper groupMapper;
    private final ImGroupMemberMapper groupMemberMapper;
    private final PlatformUserDirectoryClient userDirectoryClient;
    private final ConversationSummaryCache conversationSummaryCache;
    private final RabbitTemplate rabbitTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final StringRedisTemplate redisTemplate;

    /** 字节数组 RedisTemplate，用于读取 Router 写入的离线消息队列（原始 Protobuf 二进制） */
    private final org.springframework.data.redis.core.RedisTemplate<String, byte[]> byteArrayRedisTemplate;

    // ============================================================
    // 消息发送 — 完整权限校验 + 持久化 + 推送命令发布
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<MessageDTO> sendMessage(MessageDTO messageDTO) {
        // ==================== 1. 参数基础校验 ====================
        validateMessageContent(messageDTO);

        // ==================== 2. 权限校验 ====================
        // 2a. 校验发送方用户是否存在且状态正常
        validateSender(messageDTO.getSenderId());

        // 2b. 校验接收方用户是否存在且状态正常（单聊场景）
        if (messageDTO.getConversationType() == 1) {
            validateReceiver(messageDTO.getSenderId(), messageDTO.getReceiverId());
        }

        // 2c. 校验会话归属、目标与群成员资格，不能只凭一个可猜测的会话 ID 发送消息。
        validateConversation(messageDTO.getConversationId(), messageDTO.getSenderId(),
                messageDTO.getReceiverId(), messageDTO.getConversationType());

        // ==================== 3. 生成消息 ID ====================
        String messageId = snowflakeIdGenerator.nextIdStr();
        long serverSeq = snowflakeIdGenerator.nextId();

        // ==================== 4. 构建消息实体并持久化 ====================
        ImMessage message = ImMessage.builder()
                .messageId(messageId)
                .conversationId(messageDTO.getConversationId())
                .conversationType(messageDTO.getConversationType())
                .messageType(messageDTO.getMessageType())
                .senderId(messageDTO.getSenderId())
                .receiverId(messageDTO.getReceiverId())
                .content(messageDTO.getContent())
                .extra(messageDTO.getExtra())
                .status(MessageStatus.PREPARING.getCode())  // 初始状态为 PREPARING（已入库未送达）
                .serverSeq(serverSeq)
                .replyTo(messageDTO.getReplyTo())
                .sendTime(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        messageMapper.insert(message);

        // ==================== 5. 更新 Redis 会话摘要 ====================
        // AUDIT FIX [5.3/5.4] / IM-EMOJI-SESSION-20260810：原逻辑写会话表的最后
        // 消息和全局未读数，会覆盖双方视图。新逻辑按参与者写 Redis Hash；缓存未命中会回查。
        conversationSummaryCache.onMessagePersisted(message);

        // ==================== 6. 发布 PushMessageCommand 到命令队列 ====================
        // IM Business → IM Router（通过 MQ 命令队列）
        publishPushMessageCommand(message);

        log.info("消息发送成功: messageId={}, senderId={}, receiverId={}",
                messageId, messageDTO.getSenderId(), messageDTO.getReceiverId());

        return Result.success(convertToDTO(message));
    }

    // ============================================================
    // 权限校验方法
    // ============================================================

    /**
     * 校验消息内容基础合法性
     */
    private void validateMessageContent(MessageDTO messageDTO) {
        if (messageDTO == null) {
            throw new ImBusinessException(ResponseCode.BAD_REQUEST.getCode(), "消息内容不能为空");
        }
        if (!StringUtils.hasText(messageDTO.getSenderId())) {
            throw new ImBusinessException(ResponseCode.BAD_REQUEST.getCode(), "发送者 ID 不能为空");
        }
        if (!StringUtils.hasText(messageDTO.getReceiverId())) {
            throw new ImBusinessException(ResponseCode.BAD_REQUEST.getCode(), "接收者 ID 不能为空");
        }
        IMProtocolV2.IMMessageType messageType = messageDTO.getMessageType() == null
                ? null : IMProtocolV2.IMMessageType.forNumber(messageDTO.getMessageType());
        if (messageType == null || switch (messageType) {
            case TEXT, IMAGE, FILE, VOICE, VIDEO, STICKER, LOCATION, REPLY, SYSTEM_NOTICE, CUSTOM -> false;
            default -> true;
        }) {
            throw new ImBusinessException(ResponseCode.BAD_REQUEST.getCode(),
                    "不支持持久化的消息类型: " + messageDTO.getMessageType());
        }
        if (!StringUtils.hasText(messageDTO.getContent())) {
            throw new ImBusinessException(ResponseCode.MESSAGE_EMPTY.getCode(),
                    ResponseCode.MESSAGE_EMPTY.getMessage());
        }
        if (messageDTO.getContent().length() > MAX_MESSAGE_LENGTH) {
            throw new ImBusinessException(ResponseCode.MESSAGE_TOO_LONG.getCode(),
                    ResponseCode.MESSAGE_TOO_LONG.getMessage());
        }
    }

    /**
     * 校验发送方用户是否存在且状态正常
     * <p>
     * 通过 Redis 缓存查询用户状态，避免频繁访问数据库。
     * 缓存 Key: im:user:status:{userId} → "normal" | "frozen" | "banned"
     * </p>
     */
    private void validateSender(String senderId) {
        // 预留：实际实现需对接用户中心或用户表查询
        // 当前阶段仅校验 Redis 中的用户状态缓存
        String statusKey = String.format("im:user:status:%s", senderId);
        String status = redisTemplate.opsForValue().get(statusKey);

        if (status != null && ("frozen".equals(status) || "banned".equals(status))) {
            throw new ImBusinessException(ResponseCode.NO_PERMISSION.getCode(),
                    "发送方账号状态异常: " + status);
        }

        // 校验全局禁言
        String muteKey = String.format("im:mute:global:%s", senderId);
        String muteValue = redisTemplate.opsForValue().get(muteKey);
        if (muteValue != null) {
            throw new ImBusinessException(ResponseCode.GLOBAL_MUTED.getCode(),
                    ResponseCode.GLOBAL_MUTED.getMessage());
        }
    }

    /**
     * 校验接收方用户是否存在、状态正常、好友关系、黑名单
     * <p>
     * 单聊场景下的完整校验链：
     * 1. 接收方用户存在且状态正常
     * 2. 发送方未被接收方拉黑
     * 3. 好友关系校验（根据业务规则）
     * </p>
     */
    private void validateReceiver(String senderId, String receiverId) {
        // 1. 校验接收方用户状态
        String receiverStatusKey = String.format("im:user:status:%s", receiverId);
        String receiverStatus = redisTemplate.opsForValue().get(receiverStatusKey);
        if (receiverStatus != null && "banned".equals(receiverStatus)) {
            throw new ImBusinessException(ResponseCode.RECEIVER_STATUS_ABNORMAL.getCode(),
                    ResponseCode.RECEIVER_STATUS_ABNORMAL.getMessage());
        }

        // 2. 校验黑名单（接收方是否拉黑了发送方）
        String blacklistKey = String.format("im:blacklist:%s", receiverId);
        Boolean isBlacklisted = redisTemplate.opsForSet().isMember(blacklistKey, senderId);
        if (Boolean.TRUE.equals(isBlacklisted)) {
            throw new ImBusinessException(ResponseCode.BLACKLISTED.getCode(),
                    ResponseCode.BLACKLISTED.getMessage());
        }

        // 3. 好友关系校验。好友解除后保留会话和历史，但禁止继续发送。
        org.project.im.platform.entity.ImFriendship friendship = friendshipMapper.selectByUsers(senderId, receiverId);
        if (friendship == null || friendship.getStatus() != 0) {
            throw new ImBusinessException(ResponseCode.NOT_FRIEND.getCode(),
                    ResponseCode.NOT_FRIEND.getMessage());
        }
    }

    /**
     * 校验会话归属和路由目标。
     *
     * <p>AUDIT FIX [5.5/5.7] / IM-EMOJI-SESSION-20260810：旧行为仅确认发送方有同名会话，
     * 无法阻止把单聊会话伪装成群聊或在退群后继续发送。新行为同时核验会话类型、对端 ID，
     * 并在群聊中确认发送方仍是成员；不改变后续消息持久化和 Router 推送编排。</p>
     */
    private void validateConversation(String conversationId, String senderId, String receiverId, Integer conversationType) {
        if (!StringUtils.hasText(conversationId)) {
            throw new ImBusinessException(ResponseCode.SESSION_NOT_EXIST.getCode(),
                    ResponseCode.SESSION_NOT_EXIST.getMessage());
        }
        org.project.im.platform.entity.ImConversation session =
                conversationMapper.selectBySessionIdAndUserId(conversationId, senderId);
        if (session == null) {
            throw new ImBusinessException(ResponseCode.SESSION_NOT_EXIST.getCode(),
                    ResponseCode.SESSION_NOT_EXIST.getMessage());
        }
        if (!java.util.Objects.equals(session.getSessionType(), conversationType)
                || !java.util.Objects.equals(session.getPeerId(), receiverId)) {
            throw new ImBusinessException(ResponseCode.NO_PERMISSION.getCode(),
                    "消息目标与当前会话不匹配");
        }
        if (conversationType != null && conversationType == 2) {
            // GROUP-CHAT-20260810 [2.18/3.21/5.14]：旧行为仅检查成员关系，群被解散
            // 后成员记录仍在时可继续投递。新行为把群状态加入发送前校验；会话和历史仍保留
            // 只读，后续持久化与 MQ Router 推送编排不变。
            if (groupMapper.selectByGroupId(receiverId) == null) {
                throw new ImBusinessException(ResponseCode.GROUP_NOT_FOUND.getCode(), "群组已解散或不存在");
            }
            if (groupMemberMapper.existsByGroupIdAndUserId(receiverId, senderId) == 0) {
                throw new ImBusinessException(ResponseCode.NOT_GROUP_MEMBER.getCode(),
                        ResponseCode.NOT_GROUP_MEMBER.getMessage());
            }
        }
    }

    // ============================================================
    // PushMessageCommand 发布
    // ============================================================

    /**
     * 发布 PushMessageCommand 到命令队列
     * <p>
     * IM Business 完成消息持久化后，将推送命令发布到 im.message.push.command 队列，
     * 由 IM Router 消费并路由到目标 IM Server。
     * </p>
     */
    private void publishPushMessageCommand(ImMessage message) {
        // 构建 IMEnvelope（WebSocket 下发的二进制帧）
        // encrypted_payload 暂存"明文"负载（如 TextPayload），由 IM Server 推送前
        // 依据 message_type 完成 Layer 2 加密，客户端可据此解码出消息内容。
        IMProtocolV2.IMEnvelope.Builder envelopeBuilder = IMProtocolV2.IMEnvelope.newBuilder()
                .setVersion(2)
                .setMessageId(message.getMessageId())
                .setCommand(IMProtocolV2.IMCommandType.SEND_MESSAGE)
                .setMessageType(IMProtocolV2.IMMessageType.forNumber(message.getMessageType()))
                .setSenderId(message.getSenderId())
                .setReceiverId(message.getReceiverId())
                .setConversationId(message.getConversationId())
                .setConversationType(IMProtocolV2.IMConversationType.forNumber(message.getConversationType()))
                .setServerSeq(message.getServerSeq())
                .setTimestamp(System.currentTimeMillis())
                .setStatus(IMProtocolV2.IMMessageStatus.SENDING);

        // 附加明文负载；IM Server 会使用接收者连接的会话密钥重新加密。
        byte[] rawPayload = buildRawPayload(message);
        if (rawPayload != null && rawPayload.length > 0) {
            envelopeBuilder.setEncryptedPayload(
                    com.google.protobuf.ByteString.copyFrom(rawPayload));
        }
        IMProtocolV2.IMEnvelope envelope = envelopeBuilder.build();

        // 构建 PushMessageCommand（Protobuf 序列化）
        IMMQProto.PushMessageCommand cmd = IMMQProto.PushMessageCommand.newBuilder()
                .setHeader(IMMQProto.MQMessageHeader.newBuilder()
                        .setEventType("im.message.push.command")
                        .setMessageId(message.getMessageId())
                        .setTimestamp(System.currentTimeMillis())
                        .setTraceId(UUID.randomUUID().toString().replace("-", ""))
                        .setSourceNode("im-platform")
                        .setRetryCount(0)
                        .build())
                .setMessageId(message.getMessageId())
                .setReceiverId(message.getReceiverId())
                .setSenderId(message.getSenderId())
                .setConversationId(message.getConversationId())
                .setConversationType(message.getConversationType())
                .setContentType(message.getMessageType())
                .setMessageType(IMMQProto.MessageType.CHAT_MESSAGE) // 普通聊天消息
                .setServerSeq(message.getServerSeq())
                .setMessageTimestamp(System.currentTimeMillis())
                .setIsOfflineCompensation(false)
                .setEnvelopeBytes(ByteString.copyFrom(envelope.toByteArray()))
                .build();

        // 发送到命令交换机，路由到 im.message.push.command 队列
        // 失败时抛出异常，由上层 CommandConsumer 统一处理重试逻辑
        Message mqMessage = MessageBuilder
                .withBody(cmd.toByteArray())
                .setContentType(MessageProperties.CONTENT_TYPE_BYTES)
                .setHeader("event_type", "im.message.push.command")
                .setMessageId(message.getMessageId())
                .build();
        rabbitTemplate.send(MQ_EXCHANGE_COMMAND, MQ_ROUTING_PUSH_COMMAND, mqMessage);

        log.debug("PushMessageCommand 已发布: messageId={}, receiverId={}",
                message.getMessageId(), message.getReceiverId());
    }
    /**
     * 构建消息的明文负载（后续由 IM Server 做 Layer 2 加密）。
     * AUDIT FIX [5.4/14.4/14.25]：从 content + extra.payload 重建所有已支持富媒体负载。
     */
    private byte[] buildRawPayload(ImMessage message) {
        if (message == null) {
            return null;
        }
        try {
            return MessagePayloadCodec.encode(message);
        } catch (Exception e) {
            // 编码失败必须中断发布并回滚事务，不能推送一个内容为空但已入库的“成功”消息。
            throw new ImBusinessException(ResponseCode.BAD_REQUEST.getCode(),
                    "消息负载编码失败: " + e.getMessage());
        }
    }


    // ============================================================
    // 其他业务方法（撤回、已读、历史查询）
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> recallMessage(String messageId, String userId) {
        ImMessage message = messageMapper.selectByMessageId(messageId);
        if (message == null) {
            return Result.error(1011, "消息不存在");
        }
        if (!message.getSenderId().equals(userId)) {
            return Result.error(1007, "只能撤回自己的消息");
        }
        if (LocalDateTime.now().isAfter(message.getSendTime().plusSeconds(RECALL_TIMEOUT_SECONDS))) {
            return Result.error(1009, "已超过撤回时限（" + RECALL_TIMEOUT_SECONDS + "秒）");
        }
        messageMapper.recallMessage(messageId);

        log.info("消息撤回成功: messageId={}, userId={}", messageId, userId);
        return Result.success("消息已撤回", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> markAsRead(String conversationId, String userId) {
        messageMapper.batchUpdateRead(conversationId, userId);
        // 群聊消息的 receiver_id 是 groupId，未读游标属于群成员记录；保留原单聊批量
        // 状态更新，同时在群聊时推进成员游标，使 Redis 缓存失效回查也保持正确。
        org.project.im.platform.entity.ImConversation session = conversationMapper
                .selectBySessionIdAndUserId(conversationId, userId);
        if (session != null && session.getSessionType() == 2) {
            Long maxSeq = messageMapper.selectMaxSeqByConversationId(conversationId);
            groupMemberMapper.updateLastReadSeq(session.getPeerId(), userId, maxSeq == null ? 0L : maxSeq);
        }
        conversationSummaryCache.markRead(userId, conversationId);

        log.info("消息已读: conversationId={}, userId={}", conversationId, userId);
        return Result.success(null);
    }

    @Override
    public Result<List<MessageDTO>> getHistory(String conversationId, String userId, int page, int size) {
        if (size > MAX_HISTORY_SIZE) {
            size = MAX_HISTORY_SIZE;
        }
        int offset = (page - 1) * size;
        List<ImMessage> messages = messageMapper.selectHistoryByConversationId(conversationId, offset, size);
        List<MessageDTO> dtoList = messages.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return Result.success(dtoList);
    }

    @Override
    public Result<List<MessageDTO>> getMessagesAfter(String conversationId, String userId,
                                                      Long serverSeq, int limit) {
        if (limit > MAX_HISTORY_SIZE) {
            limit = MAX_HISTORY_SIZE;
        }
        List<ImMessage> messages = messageMapper.selectMessagesAfterSeq(conversationId, serverSeq, limit);
        List<MessageDTO> dtoList = messages.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return Result.success(dtoList);
    }

    @Override
    public Result<MessageDTO> getMessageById(String messageId) {
        ImMessage message = messageMapper.selectByMessageId(messageId);
        if (message == null) {
            return Result.error(1011, "消息不存在");
        }
        return Result.success(convertToDTO(message));
    }

    // ============================================================
    // 状态更新 — 消费送达 / 失败事件
    // ============================================================

    @Override
    public Result<Void> updateStatus(String messageId, MessageStatus status) {
        if (!StringUtils.hasText(messageId)) {
            return Result.error(ResponseCode.BAD_REQUEST.getCode(), "消息ID不能为空");
        }
        ImMessage message = messageMapper.selectByMessageId(messageId);
        if (message == null) {
            log.warn("更新消息状态失败：消息不存在 messageId={}", messageId);
            return Result.error(1011, "消息不存在");
        }
        messageMapper.updateStatus(messageId, status.getCode());
        log.info("消息状态已更新: messageId={}, status={}", messageId, status);
        return Result.success(null);
    }

    // ============================================================
    // 离线消息拉取 — 客户端主动 HTTP 拉取（多级缓存：Redis → DB）
    // ============================================================

    @Override
    public Result<List<MessageDTO>> getOfflineMessages(String userId, int limit) {
        if (limit <= 0 || limit > MAX_OFFLINE_PULL_SIZE) {
            limit = DEFAULT_OFFLINE_PULL_SIZE;
        }
        List<MessageDTO> result = new ArrayList<>();
        List<String> pulledMessageIds = new ArrayList<>();

        // 1. 优先从 Redis 离线队列读取（Router 维护的多级缓存第一层）
        String offlineKey = String.format(REDIS_OFFLINE_LIST, userId);
        List<byte[]> cached = byteArrayRedisTemplate.opsForList().range(offlineKey, 0, limit - 1);
        if (cached != null && !cached.isEmpty()) {
            // 读取即消费：从队列移除已读取部分，清空缓存，避免与 DB 重复
            byteArrayRedisTemplate.opsForList().trim(offlineKey, cached.size(), -1);
            byteArrayRedisTemplate.delete(offlineKey);
            for (byte[] bytes : cached) {
                MessageDTO dto = parseOfflineProto(bytes);
                if (dto != null) {
                    result.add(dto);
                    pulledMessageIds.add(dto.getMessageId());
                }
            }
        } else {
            // 2. Redis 未命中 → 降级查询数据库（状态为 PREPARING 的离线消息）
            List<ImMessage> messages = messageMapper.selectOfflineMessages(
                    userId, MessageStatus.PREPARING.getCode(), limit);
            for (ImMessage m : messages) {
                result.add(convertToDTO(m));
                pulledMessageIds.add(m.getMessageId());
            }
        }

        // 3. 拉取即标记为 DELIVERED（幂等：重复拉取不会重复更新）
        if (!pulledMessageIds.isEmpty()) {
            messageMapper.batchUpdateStatus(pulledMessageIds, MessageStatus.DELIVERED.getCode());
        }

        log.info("离线消息拉取: userId={}, count={}", userId, result.size());
        return Result.success(result);
    }

    // ============================================================
    // 游标分页历史消息 — 仅返回已送达 / 已读 / 失败终态
    // ============================================================

    @Override
    public Result<List<MessageDTO>> getHistoryByCursor(String conversationId, String userId,
                                                       int limit, Long cursor, LocalDateTime before) {
        if (limit <= 0 || limit > MAX_HISTORY_SIZE) {
            limit = DEFAULT_HISTORY_PULL_SIZE;
        }
        // 未送达(PREPARING)消息不进入历史接口：应由离线拉取或 WebSocket 实时推送获取
        List<Integer> statuses = Arrays.asList(
                MessageStatus.DELIVERED.getCode(),
                MessageStatus.READ.getCode(),
                MessageStatus.FAILED.getCode());
        List<ImMessage> messages = messageMapper.selectHistoryByCursor(
                conversationId, statuses, cursor, before, limit);
        List<MessageDTO> dtoList = messages.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return Result.success(dtoList);
    }


    // ============================================================
    // 私有工具方法
    // ============================================================

    /**
     * 将 Router 写入离线队列的 PushMessageCommand（Protobuf 二进制）解析为 MessageDTO。
     * 内容及 extra.payload 取自信封中的明文 Protobuf 负载。
     */
    private MessageDTO parseOfflineProto(byte[] bytes) {
        try {
            IMMQProto.PushMessageCommand cmd = IMMQProto.PushMessageCommand.parseFrom(bytes);
            byte[] envBytes = cmd.getEnvelopeBytes().toByteArray();
            IMProtocolV2.IMEnvelope env = IMProtocolV2.IMEnvelope.parseFrom(envBytes);

            MessageDTO.MessageDTOBuilder builder = MessageDTO.builder()
                    .messageId(env.getMessageId())
                    .conversationId(env.getConversationId())
                    .conversationType(env.getConversationType().getNumber())
                    .messageType(env.getMessageType().getNumber())
                    .senderId(env.getSenderId())
                    .receiverId(env.getReceiverId())
                    .serverSeq(env.getServerSeq())
                    .status(MessageStatus.DELIVERED.getCode())
                    .sendTime(LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(env.getTimestamp()), ZoneId.systemDefault()));

            if (env.getEncryptedPayload() != null && env.getEncryptedPayload().size() > 0) {
                MessagePayloadCodec.Decoded decoded = MessagePayloadCodec.decode(
                        env.getMessageTypeValue(), env.getEncryptedPayload().toByteArray(), env.getExtraJson());
                builder.content(decoded.content()).extra(decoded.extra());
            }
            return builder.build();
        } catch (Exception e) {
            log.warn("解析离线消息 Protobuf 失败: {}", e.getMessage());
            return null;
        }
    }

    private MessageDTO convertToDTO(ImMessage message) {
        // GROUP-CHAT-20260810 [3.8/3.16]：历史群消息原只返回 senderId，客户端无法
        // 呈现发送者昵称/头像。这里复用仅含公开资料的用户目录；不返回邮箱、手机号等字段。
        // 后续可将该映射批量化为 SQL join/缓存，当前保持 DTO 和历史查询 API 向后兼容。
        PlatformUserDirectoryClient.PublicProfile sender = userDirectoryClient
                .findPublicProfile(message.getSenderId(), message.getSenderId()).orElse(null);
        return MessageDTO.builder()
                .messageId(message.getMessageId())
                .conversationId(message.getConversationId())
                .conversationType(message.getConversationType())
                .messageType(message.getMessageType())
                .senderId(message.getSenderId())
                .senderName(sender == null ? message.getSenderId() : sender.username())
                .senderAvatar(sender == null ? null : sender.avatarPath())
                .receiverId(message.getReceiverId())
                .content(message.getContent())
                .extra(message.getExtra())
                .status(message.getStatus())
                .serverSeq(message.getServerSeq())
                .replyTo(message.getReplyTo())
                .sendTime(message.getSendTime())
                .createTime(message.getCreateTime())
                .updateTime(message.getUpdateTime())
                .build();
    }

    private String truncateContent(String content) {
        if (content == null) return "";
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }
}
