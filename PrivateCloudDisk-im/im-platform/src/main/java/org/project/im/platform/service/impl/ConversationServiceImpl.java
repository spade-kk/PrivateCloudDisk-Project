package org.project.im.platform.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.dto.ConversationDTO;
import org.project.im.common.dto.Result;
import org.project.im.platform.entity.ImConversation;
import org.project.im.platform.mapper.ImConversationMapper;
import org.project.im.platform.service.ConversationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话服务实现
 * <p>
 * 会话的核心处理逻辑：
 * <ul>
 *   <li>创建/获取会话：单聊会话 ID 由双方 userId 排序后生成，确保唯一性</li>
 *   <li>会话列表：按最后消息时间倒序排列</li>
 *   <li>置顶/免打扰：更新会话元数据</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ImConversationMapper conversationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<ConversationDTO> getOrCreateConversation(String userId, String targetId,
                                                            int conversationType) {
        // 生成确定性的会话 ID（单聊用双方 userId 排序生成）
        String conversationId = generateConversationId(userId, targetId, conversationType);

        ImConversation conversation = conversationMapper.selectByConversationId(conversationId);
        if (conversation != null) {
            return Result.success(convertToDTO(conversation));
        }
        // 创建新会话
        LocalDateTime now = LocalDateTime.now();
        conversation = ImConversation.builder()
                .conversationId(conversationId)
                .conversationType(conversationType)
                .userId(userId)
                .targetId(targetId)
                .unreadCount(0)
                .isTop(false)
                .isMuted(false)
                .status(0)
                .createTime(now)
                .updateTime(now)
                .build();
        conversationMapper.insert(conversation);

        log.info("会话创建成功: conversationId={}, userId={}, targetId={}", conversationId, userId, targetId);
        return Result.success(convertToDTO(conversation));
    }

    @Override
    public Result<List<ConversationDTO>> getConversations(String userId) {
        List<ImConversation> conversations = conversationMapper.selectByUserId(userId);
        List<ConversationDTO> dtoList = conversations.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return Result.success(dtoList);
    }

    @Override
    public Result<ConversationDTO> getConversationDetail(String conversationId) {
        ImConversation conversation = conversationMapper.selectByConversationId(conversationId);
        if (conversation == null) {
            return Result.error(1011, "会话不存在");
        }
        return Result.success(convertToDTO(conversation));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteConversation(String conversationId, String userId) {
        ImConversation conversation = conversationMapper.selectByConversationId(conversationId);
        if (conversation == null) {
            return Result.error(1011, "会话不存在");
        }
        conversationMapper.softDelete(conversationId);

        log.info("会话删除: conversationId={}, userId={}", conversationId, userId);
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> topConversation(String conversationId, String userId, boolean isTop) {
        conversationMapper.updateTopStatus(conversationId, isTop);
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> muteConversation(String conversationId, String userId, boolean isMuted) {
        conversationMapper.updateMuteStatus(conversationId, isMuted);
        return Result.success(null);
    }

    @Override
    public Result<Integer> getTotalUnreadCount(String userId) {
        int count = conversationMapper.selectTotalUnreadCount(userId);
        return Result.success(count);
    }

    // ==================== 私有方法 ====================

    /**
     * 生成会话 ID
     * <p>
     * 单聊：双方 userId 排序后拼接，确保 A-B 和 B-A 的会话 ID 一致
     * 群聊：使用 groupId 直接作为会话 ID
     * </p>
     */
    private String generateConversationId(String userId, String targetId, int conversationType) {
        if (conversationType == 1) {
            // 单聊：userId 排序后拼接
            return userId.compareTo(targetId) < 0
                    ? userId + "_" + targetId
                    : targetId + "_" + userId;
        }
        // 群聊
        return targetId;
    }

    /**
     * 实体转 DTO
     */
    private ConversationDTO convertToDTO(ImConversation conversation) {
        return ConversationDTO.builder()
                .conversationId(conversation.getConversationId())
                .conversationType(conversation.getConversationType())
                .userId(conversation.getUserId())
                .targetId(conversation.getTargetId())
                .lastMessage(conversation.getLastMessage())
                .lastMessageType(conversation.getLastMessageType())
                .lastMessageTime(conversation.getLastMessageTime())
                .unreadCount(conversation.getUnreadCount())
                .isTop(conversation.getIsTop())
                .isMuted(conversation.getIsMuted())
                .createTime(conversation.getCreateTime())
                .updateTime(conversation.getUpdateTime())
                .build();
    }
}