package org.project.im.platform.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.dto.MessageDTO;
import org.project.im.common.dto.Result;
import org.project.im.common.enums.MessageStatus;
import org.project.im.platform.entity.ImMessage;
import org.project.im.platform.mapper.ImConversationMapper;
import org.project.im.platform.mapper.ImMessageMapper;
import org.project.im.platform.service.MessageService;
import org.project.im.platform.util.SnowflakeIdGenerator;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.project.im.common.constant.ImConstants.*;

/**
 * 消息服务实现
 * <p>
 * 消息的核心处理逻辑：
 * <ul>
 *   <li>发送消息：生成消息 ID → 持久化 → 投递到 RabbitMQ 异步推送</li>
 *   <li>撤回消息：校验 2 分钟时限 → 更新状态 → 通知客户端</li>
 *   <li>已读标记：批量更新消息状态 → 清零会话未读数</li>
 *   <li>历史消息：支持分页查询和增量拉取</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final ImMessageMapper messageMapper;
    private final ImConversationMapper conversationMapper;
    private final RabbitTemplate rabbitTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<MessageDTO> sendMessage(MessageDTO messageDTO) {
        // 1. 生成消息 ID
        String messageId = snowflakeIdGenerator.nextIdStr();
        long serverSeq = snowflakeIdGenerator.nextId();

        // 2. 构建消息实体
        ImMessage message = ImMessage.builder()
                .messageId(messageId)
                .conversationId(messageDTO.getConversationId())
                .conversationType(messageDTO.getConversationType())
                .messageType(messageDTO.getMessageType())
                .senderId(messageDTO.getSenderId())
                .receiverId(messageDTO.getReceiverId())
                .content(messageDTO.getContent())
                .extra(messageDTO.getExtra())
                .status(MessageStatus.SENT.getCode())
                .serverSeq(serverSeq)
                .replyTo(messageDTO.getReplyTo())
                .sendTime(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 3. 持久化消息
        messageMapper.insert(message);

        // 4. 更新会话最后一条消息和未读计数
        conversationMapper.updateLastMessage(
                messageDTO.getConversationId(),
                truncateContent(messageDTO.getContent()),
                messageDTO.getMessageType(),
                LocalDateTime.now());
        conversationMapper.incrementUnreadCount(messageDTO.getConversationId());

        // 5. 构建返回 DTO
        MessageDTO result = convertToDTO(message);

        // 6. 异步投递到 RabbitMQ 进行推送
        String routingKey = messageDTO.getConversationType() == 1
                ? MQ_ROUTING_PRIVATE : MQ_ROUTING_GROUP;
        rabbitTemplate.convertAndSend(MQ_EXCHANGE_MESSAGE, routingKey, result);

        log.info("消息发送成功: messageId={}, senderId={}, receiverId={}", messageId,
                messageDTO.getSenderId(), messageDTO.getReceiverId());

        return Result.success(result);
    }

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
        // 检查 2 分钟撤回时限
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
        conversationMapper.clearUnreadCount(conversationId);

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

    // ==================== 私有方法 ====================

    /**
     * 实体转 DTO
     */
    private MessageDTO convertToDTO(ImMessage message) {
        return MessageDTO.builder()
                .messageId(message.getMessageId())
                .conversationId(message.getConversationId())
                .conversationType(message.getConversationType())
                .messageType(message.getMessageType())
                .senderId(message.getSenderId())
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

    /**
     * 截取消息内容用于会话摘要
     */
    private String truncateContent(String content) {
        if (content == null) return "";
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }
}