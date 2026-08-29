package org.project.im.platform.service.impl;

import lombok.RequiredArgsConstructor;
import org.project.im.common.dto.FriendDTO;
import org.project.im.common.dto.FriendRequestDTO;
import org.project.im.common.dto.PageResult;
import org.project.im.common.dto.Result;
import org.project.im.platform.client.PlatformUserDirectoryClient;
import org.project.im.platform.entity.ImBlacklist;
import org.project.im.platform.entity.ImFriendRequest;
import org.project.im.platform.entity.ImFriendship;
import org.project.im.platform.mapper.ImBlacklistMapper;
import org.project.im.platform.mapper.ImFriendRequestBlockMapper;
import org.project.im.platform.mapper.ImFriendRequestMapper;
import org.project.im.platform.mapper.ImFriendshipMapper;
import org.project.im.platform.service.ConversationService;
import org.project.im.platform.service.FriendshipService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 好友关系服务实现。
 *
 * <p>AUDIT FIX [5.1] / IM-EMOJI-SESSION-20260810：接受好友申请时，原项目已经在
 * 同一个本地事务中完成申请状态、双方关系和会话元数据创建。此实现保留该同步设计，不引入
 * MQ；新增的备注、星标、黑名单与申请分页不改变消息收发或会话生成主流程。</p>
 */
@Service
@RequiredArgsConstructor
public class FriendshipServiceImpl implements FriendshipService {

    private static final int REQUEST_PENDING = 0;
    private static final int RELATION_ACTIVE = 0;
    private static final String BLACKLIST_KEY_PREFIX = "im:blacklist:";

    private final ImFriendRequestMapper requestMapper;
    private final ImFriendshipMapper friendshipMapper;
    private final ImBlacklistMapper blacklistMapper;
    private final ImFriendRequestBlockMapper requestBlockMapper;
    private final PlatformUserDirectoryClient userDirectoryClient;
    private final ConversationService conversationService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<FriendRequestDTO> requestFriend(String requesterId, String recipientId, String verificationMessage) {
        if (!validPair(requesterId, recipientId)) return Result.error(400, "好友申请参与方无效");
        if (!userDirectoryClient.exists(requesterId, requesterId) || !userDirectoryClient.exists(recipientId, requesterId)) return Result.error(404, "目标用户不存在");
        if (isBlacklisted(recipientId, requesterId) || requestBlockMapper.exists(recipientId, requesterId)) {
            return Result.error(403, "对方暂不接收你的好友申请");
        }
        if (isActiveFriend(requesterId, recipientId)) return Result.error(409, "已经是好友关系");
        if (requestMapper.selectPendingBetween(requesterId, recipientId) != null) return Result.error(409, "好友申请已发送，请等待验证");
        String message = verificationMessage == null ? "" : verificationMessage.trim();
        if (message.length() > 50) return Result.error(400, "验证信息不能超过 50 个字符");
        LocalDateTime now = LocalDateTime.now();
        ImFriendRequest request = ImFriendRequest.builder().requestId(UUID.randomUUID().toString())
                .requesterId(requesterId).recipientId(recipientId).verificationMessage(message)
                .status(REQUEST_PENDING).createdAt(now).updatedAt(now).build();
        requestMapper.insert(request);
        return Result.success(toRequestDTO(request, requesterId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> acceptRequest(String requestId, String recipientId) {
        ImFriendRequest request = requestMapper.selectByRequestId(requestId);
        if (request == null || !recipientId.equals(request.getRecipientId())) return Result.error(404, "好友申请不存在");
        if (request.getStatus() != REQUEST_PENDING) return Result.error(409, "好友申请已处理");
        if (requestMapper.acceptIfPending(requestId, recipientId) != 1) return Result.error(409, "好友申请已被并发处理");
        upsertFriendship(request.getRequesterId(), request.getRecipientId());
        upsertFriendship(request.getRecipientId(), request.getRequesterId());
        // [3.6] 原行为与新行为一致：会话在申请接受事务内同步创建，避免前端跳转到不存在会话。
        conversationService.ensureConversationForParticipants(request.getRequesterId(), request.getRecipientId(), 1);
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> rejectRequest(String requestId, String recipientId) {
        return rejectRequest(requestId, recipientId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> rejectRequest(String requestId, String recipientId, boolean blockFuture) {
        ImFriendRequest request = requestMapper.selectByRequestId(requestId);
        if (request == null || !recipientId.equals(request.getRecipientId())) return Result.error(404, "好友申请不存在");
        if (requestMapper.rejectIfPending(requestId, recipientId) != 1) return Result.error(409, "好友申请不存在或已处理");
        if (blockFuture) requestBlockMapper.insertIgnore(recipientId, request.getRequesterId());
        return Result.success(null);
    }

    @Override
    public Result<List<FriendDTO>> getFriends(String userId) {
        return Result.success(friendshipMapper.selectActiveByUserId(userId).stream().map(item -> toFriendDTO(userId, item)).toList());
    }

    @Override
    public Result<PageResult<FriendRequestDTO>> getIncomingRequests(String recipientId, int page, int size) {
        int safePage = normalizePage(page), safeSize = normalizeSize(size);
        List<FriendRequestDTO> items = requestMapper.selectByRecipientId(recipientId, (safePage - 1) * safeSize, safeSize).stream().map(item -> toRequestDTO(item, recipientId)).toList();
        return Result.success(PageResult.of(items, safePage, safeSize, requestMapper.countByRecipientId(recipientId)));
    }

    @Override
    public Result<PageResult<FriendRequestDTO>> getOutgoingRequests(String requesterId, int page, int size) {
        int safePage = normalizePage(page), safeSize = normalizeSize(size);
        List<FriendRequestDTO> items = requestMapper.selectByRequesterId(requesterId, (safePage - 1) * safeSize, safeSize).stream().map(item -> toRequestDTO(item, requesterId)).toList();
        return Result.success(PageResult.of(items, safePage, safeSize, requestMapper.countByRequesterId(requesterId)));
    }

    /** 旧端点兼容：仅保留“收到且待处理”的语义。 */
    @Override
    public Result<List<FriendRequestDTO>> getPendingRequests(String recipientId) {
        return Result.success(requestMapper.selectPendingByRecipientId(recipientId).stream().map(item -> toRequestDTO(item, recipientId)).toList());
    }

    @Override
    public Result<Long> getPendingRequestCount(String recipientId) {
        return Result.success((long) requestMapper.selectPendingByRecipientId(recipientId).size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> cancelRequest(String requestId, String requesterId) {
        return requestMapper.cancelIfPending(requestId, requesterId) == 1 ? Result.success(null) : Result.error(409, "好友申请不存在或无法取消");
    }

    @Override
    public Result<FriendDTO> getFriendDetail(String userId, String friendId) {
        ImFriendship friendship = friendshipMapper.selectByUsers(userId, friendId);
        if (friendship == null || friendship.getStatus() == null || friendship.getStatus() != RELATION_ACTIVE) return Result.error(404, "好友关系不存在");
        return Result.success(toFriendDTO(userId, friendship));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateRemark(String userId, String friendId, String remark) {
        String normalized = remark == null ? "" : remark.trim();
        if (normalized.length() > 64) return Result.error(400, "备注不能超过 64 个字符");
        return friendshipMapper.updateRemark(userId, friendId, normalized) == 1 ? Result.success(null) : Result.error(404, "好友关系不存在");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> setStarred(String userId, String friendId, boolean starred) {
        return friendshipMapper.updateStarred(userId, friendId, starred) == 1 ? Result.success(null) : Result.error(404, "好友关系不存在");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> removeFriend(String userId, String friendId) {
        return friendshipMapper.releaseSymmetric(userId, friendId) > 0 ? Result.success(null) : Result.error(404, "好友关系不存在");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> blockUser(String userId, String friendId) {
        if (!validPair(userId, friendId) || !userDirectoryClient.exists(friendId, userId)) return Result.error(400, "拉黑对象无效");
        blacklistMapper.insertIgnore(ImBlacklist.builder().userId(userId).blockedUserId(friendId).createdAt(LocalDateTime.now()).build());
        // 黑名单持久化成功后同步刷新运行时校验缓存，Redis 故障不会影响数据库事务。
        try { stringRedisTemplate.opsForSet().add(BLACKLIST_KEY_PREFIX + userId, friendId); } catch (Exception ignored) { }
        friendshipMapper.releaseSymmetric(userId, friendId);
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unblockUser(String userId, String friendId) {
        blacklistMapper.delete(userId, friendId);
        try { stringRedisTemplate.opsForSet().remove(BLACKLIST_KEY_PREFIX + userId, friendId); } catch (Exception ignored) { }
        return Result.success(null);
    }

    @Override
    public Result<List<FriendDTO>> getBlacklist(String userId) {
        return Result.success(blacklistMapper.selectByUserId(userId).stream().map(entry -> {
            PlatformUserDirectoryClient.PublicProfile profile = userDirectoryClient
                    .findPublicProfile(entry.getBlockedUserId(), userId).orElse(null);
            return FriendDTO.builder().friendId(entry.getBlockedUserId())
                    .username(profile == null ? entry.getBlockedUserId() : profile.username())
                    .account(profile == null ? null : profile.account()).avatarPath(profile == null ? null : profile.avatarPath())
                    .status(1).createdAt(entry.getCreatedAt()).build();
        }).toList());
    }

    @Override
    public boolean isActiveFriend(String userId, String friendId) {
        ImFriendship friendship = friendshipMapper.selectByUsers(userId, friendId);
        return friendship != null && friendship.getStatus() != null && friendship.getStatus() == RELATION_ACTIVE;
    }

    private boolean validPair(String userId, String peerId) {
        return StringUtils.hasText(userId) && StringUtils.hasText(peerId) && !userId.equals(peerId);
    }

    private boolean isBlacklisted(String userId, String peerId) {
        try {
            Boolean cached = stringRedisTemplate.opsForSet().isMember(BLACKLIST_KEY_PREFIX + userId, peerId);
            if (Boolean.TRUE.equals(cached)) return true;
        } catch (Exception ignored) { }
        return blacklistMapper.exists(userId, peerId);
    }

    private String relationshipStatus(String viewerId, String targetId) {
        if (isBlacklisted(viewerId, targetId) || isBlacklisted(targetId, viewerId)) return "BLOCKED";
        if (isActiveFriend(viewerId, targetId)) return "FRIEND";
        if (requestMapper.selectPendingBetween(viewerId, targetId) != null) return "PENDING_OUTGOING";
        if (requestMapper.selectPendingBetween(targetId, viewerId) != null) return "PENDING_INCOMING";
        return "NONE";
    }

    private int normalizePage(int page) { return Math.max(1, page); }
    private int normalizeSize(int size) { return Math.min(100, Math.max(1, size)); }

    private void upsertFriendship(String userId, String friendId) {
        ImFriendship existing = friendshipMapper.selectByUsers(userId, friendId);
        if (existing == null) {
            LocalDateTime now = LocalDateTime.now();
            friendshipMapper.insert(ImFriendship.builder().userId(userId).friendId(friendId).status(RELATION_ACTIVE)
                    .starred(false).createdAt(now).updatedAt(now).build());
        } else if (existing.getStatus() != RELATION_ACTIVE) {
            friendshipMapper.reactivate(userId, friendId);
        }
    }

    private FriendDTO toFriendDTO(String ownerId, ImFriendship friendship) {
        PlatformUserDirectoryClient.PublicProfile profile = userDirectoryClient
                .findPublicProfile(friendship.getFriendId(), ownerId).orElse(null);
        return FriendDTO.builder().friendId(friendship.getFriendId()).status(friendship.getStatus())
                .username(profile == null ? friendship.getFriendId() : profile.username())
                .account(profile == null ? null : profile.account()).avatarPath(profile == null ? null : profile.avatarPath())
                .remark(friendship.getRemark()).starred(Boolean.TRUE.equals(friendship.getStarred()))
                // 共同空间/群组统计不再通过用户表 Mapper 反查；后续由各自领域聚合接口提供。
                .online(false).commonSpaceCount(0).commonGroupCount(0)
                .createdAt(friendship.getCreatedAt()).build();
    }

    private FriendRequestDTO toRequestDTO(ImFriendRequest request, String viewerId) {
        PlatformUserDirectoryClient.PublicProfile requester = userDirectoryClient
                .findPublicProfile(request.getRequesterId(), viewerId).orElse(null);
        PlatformUserDirectoryClient.PublicProfile recipient = userDirectoryClient
                .findPublicProfile(request.getRecipientId(), viewerId).orElse(null);
        return FriendRequestDTO.builder().requestId(request.getRequestId()).requesterId(request.getRequesterId())
                .recipientId(request.getRecipientId()).requesterName(requester == null ? request.getRequesterId() : requester.username())
                .requesterAccount(requester == null ? null : requester.account()).requesterAvatarPath(requester == null ? null : requester.avatarPath())
                .recipientName(recipient == null ? request.getRecipientId() : recipient.username())
                .recipientAccount(recipient == null ? null : recipient.account()).recipientAvatarPath(recipient == null ? null : recipient.avatarPath())
                .verificationMessage(request.getVerificationMessage()).status(request.getStatus())
                .createdAt(request.getCreatedAt()).updatedAt(request.getUpdatedAt()).build();
    }
}
