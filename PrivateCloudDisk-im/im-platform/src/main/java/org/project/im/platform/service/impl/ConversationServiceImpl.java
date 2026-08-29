package org.project.im.platform.service.impl;

import lombok.RequiredArgsConstructor;
import org.project.im.common.dto.ConversationDTO;
import org.project.im.common.dto.Result;
import org.project.im.platform.client.PlatformUserDirectoryClient;
import org.project.im.platform.entity.ImConversation;
import org.project.im.platform.entity.ImGroup;
import org.project.im.platform.mapper.ImConversationMapper;
import org.project.im.platform.mapper.ImFriendshipMapper;
import org.project.im.platform.mapper.ImGroupMapper;
import org.project.im.platform.mapper.ImGroupMemberMapper;
import org.project.im.platform.service.ConversationService;
import org.project.im.platform.service.ConversationSummaryCache;
import org.project.im.platform.util.ConversationIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话元数据服务。
 *
 * <p>AUDIT FIX [5.1-5.8] / IM-EMOJI-SESSION-20260810：原实现可由任意前端接口
 * 创建或全局删除会话，且单条记录覆盖双方元数据。新行为只提供查询与个人置顶/免打扰；会话
 * 仅由好友接受和群组加入的事务内部创建，摘要由 Redis 组装。</p>
 */
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ImConversationMapper conversationMapper;
    private final ConversationSummaryCache summaryCache;
    private final ImFriendshipMapper friendshipMapper;
    private final ImGroupMapper groupMapper;
    private final ImGroupMemberMapper groupMemberMapper;
    private final PlatformUserDirectoryClient userDirectoryClient;

    @Override
    public Result<ConversationDTO> getExistingConversation(String userId, String peerId, int conversationType) {
        ImConversation conversation = conversationMapper.selectByUserIdAndPeerId(userId, peerId, conversationType);
        return conversation == null ? Result.error(1011, "会话不存在") : Result.success(toDTO(conversation));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureConversationForParticipants(String userId, String peerId, int conversationType) {
        String sessionId = ConversationIdGenerator.generate(userId, peerId, conversationType);
        ensureOne(sessionId, userId, peerId, conversationType);
        if (conversationType == ConversationIdGenerator.SINGLE) {
            ensureOne(sessionId, peerId, userId, conversationType);
        }
    }

    @Override
    public Result<List<ConversationDTO>> getConversations(String userId) {
        return Result.success(conversationMapper.selectByUserId(userId).stream().map(this::toDTO).toList());
    }

    @Override
    public Result<ConversationDTO> getConversationDetail(String conversationId, String userId) {
        ImConversation conversation = conversationMapper.selectBySessionIdAndUserId(conversationId, userId);
        return conversation == null ? Result.error(1011, "会话不存在") : Result.success(toDTO(conversation));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> topConversation(String conversationId, String userId, boolean isTop) {
        return conversationMapper.updatePinned(conversationId, userId, isTop) == 1
                ? Result.success(null) : Result.error(1011, "会话不存在");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> muteConversation(String conversationId, String userId, boolean isMuted) {
        return conversationMapper.updateMuted(conversationId, userId, isMuted) == 1
                ? Result.success(null) : Result.error(1011, "会话不存在");
    }

    @Override
    public Result<Integer> getTotalUnreadCount(String userId) {
        return Result.success(summaryCache.getTotalUnread(conversationMapper.selectByUserId(userId)));
    }

    private void ensureOne(String sessionId, String userId, String peerId, int sessionType) {
        if (conversationMapper.selectBySessionIdAndUserId(sessionId, userId) != null) return;
        LocalDateTime now = LocalDateTime.now();
        conversationMapper.insert(ImConversation.builder().sessionId(sessionId).sessionType(sessionType).userId(userId)
                .peerId(peerId).isPinned(false).isMuted(false).createdAt(now).updatedAt(now).build());
    }

    private ConversationDTO toDTO(ImConversation conversation) {
        ConversationSummaryCache.Summary summary = summaryCache.getOrLoad(conversation);
        org.project.im.platform.entity.ImFriendship friendship = conversation.getSessionType() == ConversationIdGenerator.SINGLE
                ? friendshipMapper.selectByUsers(conversation.getUserId(), conversation.getPeerId()) : null;
        boolean canSend = conversation.getSessionType() == ConversationIdGenerator.SINGLE
                ? friendship != null && friendship.getStatus() == 0
                // GROUP-CHAT-20260810 [2.18/3.21/5.14]：群解散后继续保留群会话和
                // 历史消息，但输入框必须置灰。旧行为只看成员表；新行为同时确认群仍为正常状态。
                : groupMapper.selectByGroupId(conversation.getPeerId()) != null
                && groupMemberMapper.existsByGroupIdAndUserId(conversation.getPeerId(), conversation.getUserId()) > 0;
        String status = canSend ? "ACTIVE" : conversation.getSessionType() == ConversationIdGenerator.SINGLE
                ? "FRIEND_REMOVED" : "GROUP_LEFT";
        // PRIVATE-CHAT-20260810 [2.1/3.1/7.4]：会话表只保存关系元数据，昵称和头像必须
        // 通过主业务用户目录补全，避免 IM 直接访问用户信息表；目录不可用时保留 peerId
        // 作为稳定降级展示值，不影响历史消息、未读数和发送链路。
        String conversationName = conversation.getPeerId();
        String avatar = null;
        if (conversation.getSessionType() == ConversationIdGenerator.SINGLE) {
            PlatformUserDirectoryClient.PublicProfile profile = userDirectoryClient
                    .findPublicProfile(conversation.getPeerId(), conversation.getUserId()).orElse(null);
            if (profile != null) {
                conversationName = profile.username() == null || profile.username().isBlank()
                        ? profile.account() : profile.username();
                avatar = profile.avatarPath();
            }
        } else {
            ImGroup group = groupMapper.selectByGroupId(conversation.getPeerId());
            if (group != null) {
                conversationName = group.getGroupName();
                avatar = group.getAvatar();
            }
        }
        return ConversationDTO.builder().conversationId(conversation.getSessionId()).conversationType(conversation.getSessionType())
                .conversationName(conversationName).avatar(avatar)
                .userId(conversation.getUserId()).targetId(conversation.getPeerId()).lastMessage(summary.lastMessage())
                .lastMessageType(summary.lastMessageType()).lastMessageTime(summary.lastMessageTime())
                .unreadCount(summary.unreadCount()).isTop(conversation.getIsPinned()).isMuted(conversation.getIsMuted())
                .canSend(canSend).sessionStatus(status).createTime(conversation.getCreatedAt()).updateTime(conversation.getUpdatedAt()).build();
    }
}
