package org.project.im.platform.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.dto.GroupDTO;
import org.project.im.common.dto.GroupMemberDTO;
import org.project.im.common.dto.PageResult;
import org.project.im.common.dto.Result;
import org.project.im.common.enums.GroupRole;
import org.project.im.platform.entity.ImGroup;
import org.project.im.platform.entity.ImGroupMember;
import org.project.im.platform.client.PlatformUserDirectoryClient;
import org.project.im.platform.mapper.ImGroupMapper;
import org.project.im.platform.mapper.ImGroupMemberMapper;
import org.project.im.platform.service.ConversationService;
import org.project.im.platform.service.GroupService;
import org.project.im.platform.service.GroupSystemNoticePublisher;
import org.project.im.platform.util.ConversationIdGenerator;
import org.project.im.platform.util.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.project.im.common.constant.ImConstants.MAX_GROUP_MEMBERS;

/**
 * 群组服务实现
 * <p>
 * 群组管理核心逻辑：
 * <ul>
 *   <li>创建群组：群主自动加入，初始成员数 1</li>
 *   <li>加入群组：校验人数上限、加群方式</li>
 *   <li>退出群组：群主不可退出，需先转让</li>
 *   <li>踢人/禁言：仅群主和管理员可操作</li>
 *   <li>解散群组：仅群主可操作</li>
 * </ul>
 * </p>
 *
 * <p>GROUP-CHAT-20260810 [2-6]：旧实现只覆盖单成员入群和少量 Query 参数接口，
 * 且创建时仅为群主建立会话。新实现将初始成员、邀请、角色和资料更新收敛到同一事务边界；
 * 消息内容的持久化和 Router 推送仍由 MessageService 原链路处理，不在这里创建新的 MQ
 * 拓扑或伪造 WebSocket 帧。</p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private static final int GROUP_ACTIVE = 0;
    private static final int GROUP_DISSOLVED = 1;

    private final ImGroupMapper groupMapper;
    private final ImGroupMemberMapper groupMemberMapper;
    private final PlatformUserDirectoryClient userDirectoryClient;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ConversationService conversationService;
    private final GroupSystemNoticePublisher groupSystemNoticePublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<GroupDTO> createGroup(String ownerId, String groupName, String avatar) {
        // 兼容旧接口：原行为只传群主，仍委托给包含初始成员的事务实现。
        return createGroup(ownerId, groupName, avatar, List.of(), 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<GroupDTO> createGroup(String ownerId, String groupName, String avatar, List<String> memberIds, Integer joinMode) {
        if (!userDirectoryClient.exists(ownerId, ownerId)) return Result.error(1001, "群主用户不存在");
        String normalizedName = groupName == null ? "" : groupName.trim();
        if (!StringUtils.hasText(normalizedName) || normalizedName.length() > 30) return Result.error(400, "群名称长度应为 1 至 30 个字符");
        int safeJoinMode = normalizeJoinMode(joinMode);
        Set<String> members = new LinkedHashSet<>();
        members.add(ownerId);
        if (memberIds != null) {
            for (String memberId : memberIds) {
                if (StringUtils.hasText(memberId)) members.add(memberId.trim());
            }
        }
        if (members.size() > MAX_GROUP_MEMBERS) return Result.error(1006, "群成员数超过上限");
        for (String memberId : members) {
            if (!userDirectoryClient.exists(memberId, ownerId)) return Result.error(1001, "成员不存在: " + memberId);
        }

        String groupId = snowflakeIdGenerator.nextIdStr();
        LocalDateTime now = LocalDateTime.now();
        ImGroup group = ImGroup.builder()
                .groupId(groupId).groupName(normalizedName).avatar(normalizeOptional(avatar)).ownerId(ownerId)
                .memberCount(members.size()).maxMembers(MAX_GROUP_MEMBERS).joinMode(safeJoinMode)
                .isAllMuted(false).status(GROUP_ACTIVE).createTime(now).updateTime(now).build();
        groupMapper.insert(group);

        for (String memberId : members) {
            int role = ownerId.equals(memberId) ? GroupRole.OWNER.getCode() : GroupRole.MEMBER.getCode();
            groupMemberMapper.insert(ImGroupMember.builder().groupId(groupId).userId(memberId).role(role)
                    .lastReadSeq(0L).joinTime(now).createTime(now).build());
            // GROUP-CHAT-20260810 [4.13]：旧行为只确保群主会话存在；新行为为所有
            // 初始成员同步写入会话元数据，群创建成功即可以打开会话，不依赖异步消费时序。
            conversationService.ensureConversationForParticipants(memberId, groupId, ConversationIdGenerator.GROUP);
        }
        groupSystemNoticePublisher.publishAfterCommit(groupId, ownerId, "群主创建了群聊");
        log.info("群组创建成功: groupId={}, groupName={}, ownerId={}, initialMembers={}", groupId, normalizedName, ownerId, members.size());
        return Result.success(convertToDTO(group, ownerId));
    }

    @Override
    public Result<GroupDTO> getGroupDetail(String groupId) {
        ImGroup group = groupMapper.selectByGroupId(groupId);
        return group == null ? Result.error(1003, "群组不存在") : Result.success(convertToDTO(group, null));
    }

    @Override
    public Result<GroupDTO> getGroupDetail(String groupId, String viewerId) {
        ImGroup group = groupMapper.selectByGroupId(groupId);
        if (group == null) return Result.error(1003, "群组不存在");
        if (!isMember(groupId, viewerId)) return Result.error(1004, "不是群组成员");
        return Result.success(convertToDTO(group, viewerId));
    }

    @Override
    public Result<List<GroupDTO>> getUserGroups(String userId) {
        return Result.success(groupMapper.selectByUserId(userId).stream().map(group -> convertToDTO(group, userId)).toList());
    }

    @Override
    public Result<PageResult<GroupDTO>> getUserGroups(String userId, int page, int size) {
        int safePage = normalizePage(page), safeSize = normalizeSize(size);
        List<ImGroup> groups = groupMapper.selectByUserId(userId);
        int from = Math.min((safePage - 1) * safeSize, groups.size());
        int to = Math.min(from + safeSize, groups.size());
        return Result.success(PageResult.of(groups.subList(from, to).stream().map(group -> convertToDTO(group, userId)).toList(), safePage, safeSize, groups.size()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> joinGroup(String groupId, String userId) {
        ImGroup group = groupMapper.selectByGroupId(groupId);
        if (group == null) return Result.error(1003, "群组不存在或已解散");
        if (!userDirectoryClient.exists(userId, userId)) return Result.error(1001, "用户不存在");
        if (groupMemberMapper.existsByGroupIdAndUserId(groupId, userId) > 0) return Result.error(1005, "已是群组成员");
        if (group.getMemberCount() >= group.getMaxMembers()) return Result.error(1006, "群组已满");
        if (group.getJoinMode() == 2) return Result.error(1007, "该群组禁止加入");
        if (group.getJoinMode() == 1) return Result.error(1007, "该群组需要管理员审核");
        addMember(groupId, userId, GroupRole.MEMBER.getCode());
        log.info("加入群组成功: groupId={}, userId={}", groupId, userId);
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> inviteMembers(String groupId, String operatorId, List<String> userIds) {
        ImGroup group = requireActiveGroup(groupId);
        if (group == null) return Result.error(1003, "群组不存在或已解散");
        ImGroupMember operator = groupMemberMapper.selectByGroupIdAndUserId(groupId, operatorId);
        if (!isStaff(operator)) return Result.error(1007, "无权限邀请成员");
        Set<String> targets = new LinkedHashSet<>();
        if (userIds != null) userIds.stream().filter(StringUtils::hasText).map(String::trim).forEach(targets::add);
        if (targets.isEmpty()) return Result.error(400, "至少选择一位成员");
        List<String> pending = new ArrayList<>();
        for (String target : targets) {
            if (!userDirectoryClient.exists(target, operatorId)) return Result.error(1001, "成员不存在: " + target);
            if (!isMember(groupId, target)) pending.add(target);
        }
        if (group.getMemberCount() + pending.size() > group.getMaxMembers()) return Result.error(1006, "群组容量不足");
        for (String target : pending) addMember(groupId, target, GroupRole.MEMBER.getCode());
        if (!pending.isEmpty()) groupSystemNoticePublisher.publishAfterCommit(groupId, operatorId,
                "邀请 " + pending.size() + " 位成员加入群聊");
        log.info("邀请群成员完成: groupId={}, operatorId={}, added={}", groupId, operatorId, pending.size());
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> leaveGroup(String groupId, String userId) {
        ImGroup group = requireActiveGroup(groupId);
        if (group == null) return Result.error(1003, "群组不存在或已解散");
        if (group.getOwnerId().equals(userId)) return Result.error(1007, "群主不可退出，请先转让群主");
        if (!isMember(groupId, userId)) return Result.error(1004, "不是群组成员");
        groupMemberMapper.deleteByGroupIdAndUserId(groupId, userId);
        groupMapper.decrementMemberCount(groupId);
        groupSystemNoticePublisher.publishAfterCommit(groupId, group.getOwnerId(), "有成员退出了群聊");
        log.info("退出群组成功: groupId={}, userId={}", groupId, userId);
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> kickMember(String groupId, String operatorId, String targetUid) {
        ImGroup group = requireActiveGroup(groupId);
        if (group == null) return Result.error(1003, "群组不存在或已解散");
        ImGroupMember operator = groupMemberMapper.selectByGroupIdAndUserId(groupId, operatorId);
        ImGroupMember target = groupMemberMapper.selectByGroupIdAndUserId(groupId, targetUid);
        if (!isStaff(operator)) return Result.error(1007, "无权限操作");
        if (target == null) return Result.error(1004, "目标用户不是群成员");
        if (!canManageTarget(operator, target)) return Result.error(1007, "不能移除群主或同级管理员");
        groupMemberMapper.deleteByGroupIdAndUserId(groupId, targetUid);
        groupMapper.decrementMemberCount(groupId);
        groupSystemNoticePublisher.publishAfterCommit(groupId, operatorId, "有成员被移出群聊");
        log.info("踢出成员成功: groupId={}, operatorId={}, targetUid={}", groupId, operatorId, targetUid);
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateMemberRole(String groupId, String operatorId, String targetUid, int role) {
        ImGroup group = requireActiveGroup(groupId);
        if (group == null) return Result.error(1003, "群组不存在或已解散");
        if (!group.getOwnerId().equals(operatorId)) return Result.error(1007, "仅群主可以设置管理员");
        ImGroupMember target = groupMemberMapper.selectByGroupIdAndUserId(groupId, targetUid);
        if (target == null) return Result.error(1004, "目标用户不是群成员");
        if (target.getRole() == GroupRole.OWNER.getCode()) return Result.error(1007, "不能修改群主角色");
        if (role != GroupRole.ADMIN.getCode() && role != GroupRole.MEMBER.getCode()) return Result.error(400, "角色仅支持 admin 或 member");
        groupMemberMapper.updateRole(groupId, targetUid, role);
        groupSystemNoticePublisher.publishAfterCommit(groupId, operatorId,
                role == GroupRole.ADMIN.getCode() ? "已设置一位管理员" : "已取消一位管理员");
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> muteMember(String groupId, String operatorId, String targetUid, int durationMinutes) {
        ImGroup group = requireActiveGroup(groupId);
        if (group == null) return Result.error(1003, "群组不存在或已解散");
        ImGroupMember operator = groupMemberMapper.selectByGroupIdAndUserId(groupId, operatorId);
        ImGroupMember target = groupMemberMapper.selectByGroupIdAndUserId(groupId, targetUid);
        if (!isStaff(operator)) return Result.error(1007, "无权限操作");
        if (target == null) return Result.error(1004, "目标用户不是群成员");
        if (!canManageTarget(operator, target)) return Result.error(1007, "不能禁言群主或同级管理员");
        if (durationMinutes == 0 || durationMinutes < -1) return Result.error(400, "禁言时长应为正数或 -1");
        LocalDateTime muteUntil = durationMinutes == -1 ? LocalDateTime.of(2099, 12, 31, 23, 59, 59) : LocalDateTime.now().plusMinutes(durationMinutes);
        groupMemberMapper.updateMute(groupId, targetUid, muteUntil);
        groupSystemNoticePublisher.publishAfterCommit(groupId, operatorId, "已更新成员禁言状态");
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unmuteMember(String groupId, String operatorId, String targetUid) {
        ImGroup group = requireActiveGroup(groupId);
        if (group == null) return Result.error(1003, "群组不存在或已解散");
        ImGroupMember operator = groupMemberMapper.selectByGroupIdAndUserId(groupId, operatorId);
        ImGroupMember target = groupMemberMapper.selectByGroupIdAndUserId(groupId, targetUid);
        if (!isStaff(operator) || target == null || !canManageTarget(operator, target)) return Result.error(1007, "无权限操作");
        groupMemberMapper.updateMute(groupId, targetUid, null);
        groupSystemNoticePublisher.publishAfterCommit(groupId, operatorId, "已解除成员禁言");
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> muteAll(String groupId, String operatorId, boolean isAllMuted) {
        ImGroup group = requireActiveGroup(groupId);
        if (group == null) return Result.error(1003, "群组不存在或已解散");
        if (!group.getOwnerId().equals(operatorId)) return Result.error(1007, "仅群主可以设置全员禁言");
        groupMapper.updateAllMuted(groupId, isAllMuted);
        groupSystemNoticePublisher.publishAfterCommit(groupId, operatorId,
                isAllMuted ? "已开启全员禁言" : "已关闭全员禁言");
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> dissolveGroup(String groupId, String ownerId) {
        ImGroup group = requireActiveGroup(groupId);
        if (group == null) return Result.error(1003, "群组不存在或已解散");
        if (!group.getOwnerId().equals(ownerId)) return Result.error(1007, "仅群主可以解散群组");
        groupMapper.dissolve(groupId);
        // 旧行为只更新 status，消息校验仍可仅按成员关系放行。新行为在 MessageService /
        // ConversationService 同时检查该状态，保留成员与会话历史以便只读回溯但禁止继续发送。
        log.info("群组解散: groupId={}, ownerId={}", groupId, ownerId);
        return Result.success(null);
    }

    @Override
    public Result<List<GroupMemberDTO>> getGroupMembers(String groupId) {
        ImGroup group = groupMapper.selectByGroupId(groupId);
        String viewerId = group == null ? null : group.getOwnerId();
        return Result.success(groupMemberMapper.selectByGroupId(groupId).stream().map(member -> convertToDTO(member, viewerId)).toList());
    }

    @Override
    public Result<PageResult<GroupMemberDTO>> getGroupMembers(String groupId, String viewerId, int page, int size) {
        if (requireActiveGroup(groupId) == null) return Result.error(1003, "群组不存在或已解散");
        if (!isMember(groupId, viewerId)) return Result.error(1004, "不是群组成员");
        int safePage = normalizePage(page), safeSize = normalizeSize(size);
        List<ImGroupMember> members = groupMemberMapper.selectByGroupId(groupId);
        int from = Math.min((safePage - 1) * safeSize, members.size());
        int to = Math.min(from + safeSize, members.size());
        return Result.success(PageResult.of(members.subList(from, to).stream().map(member -> convertToDTO(member, viewerId)).toList(), safePage, safeSize, members.size()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateAnnouncement(String groupId, String operatorId, String announcement) {
        ImGroup group = requireActiveGroup(groupId);
        if (group == null) return Result.error(1003, "群组不存在或已解散");
        ImGroupMember operator = groupMemberMapper.selectByGroupIdAndUserId(groupId, operatorId);
        if (!isStaff(operator)) return Result.error(1007, "无权限修改公告");
        groupMapper.updateAnnouncement(groupId, announcement == null ? "" : announcement.trim());
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<GroupDTO> updateGroup(String groupId, String operatorId, String groupName, String avatar,
                                        String announcement, String description, Integer joinMode) {
        ImGroup group = requireActiveGroup(groupId);
        if (group == null) return Result.error(1003, "群组不存在或已解散");
        ImGroupMember operator = groupMemberMapper.selectByGroupIdAndUserId(groupId, operatorId);
        if (!isStaff(operator)) return Result.error(1007, "无权限修改群资料");
        if (groupName != null) {
            String normalizedName = groupName.trim();
            if (!StringUtils.hasText(normalizedName) || normalizedName.length() > 30) return Result.error(400, "群名称长度应为 1 至 30 个字符");
            group.setGroupName(normalizedName);
        }
        if (avatar != null) group.setAvatar(normalizeOptional(avatar));
        if (announcement != null) group.setAnnouncement(announcement.trim());
        if (description != null) group.setDescription(description.trim());
        if (joinMode != null) group.setJoinMode(normalizeJoinMode(joinMode));
        groupMapper.updateGroupInfo(group);
        // updateGroupInfo 未覆盖公告，沿用原有专用更新以免 SQL 行为产生隐式变化。
        if (announcement != null) groupMapper.updateAnnouncement(groupId, group.getAnnouncement());
        groupSystemNoticePublisher.publishAfterCommit(groupId, operatorId, "已更新群资料");
        return Result.success(convertToDTO(group, operatorId));
    }

    private void addMember(String groupId, String userId, int role) {
        LocalDateTime now = LocalDateTime.now();
        groupMemberMapper.insert(ImGroupMember.builder().groupId(groupId).userId(userId).role(role).lastReadSeq(0L)
                .joinTime(now).createTime(now).build());
        groupMapper.incrementMemberCount(groupId);
        conversationService.ensureConversationForParticipants(userId, groupId, ConversationIdGenerator.GROUP);
    }

    private ImGroup requireActiveGroup(String groupId) { return groupMapper.selectByGroupId(groupId); }
    private boolean isMember(String groupId, String userId) { return StringUtils.hasText(userId) && groupMemberMapper.existsByGroupIdAndUserId(groupId, userId) > 0; }
    private boolean isStaff(ImGroupMember member) { return member != null && (member.getRole() == GroupRole.OWNER.getCode() || member.getRole() == GroupRole.ADMIN.getCode()); }
    private boolean canManageTarget(ImGroupMember operator, ImGroupMember target) {
        if (target.getRole() == GroupRole.OWNER.getCode()) return false;
        return operator.getRole() == GroupRole.OWNER.getCode() || target.getRole() != GroupRole.ADMIN.getCode();
    }
    private int normalizeJoinMode(Integer value) { return value == null || value < 0 || value > 2 ? 0 : value; }
    private int normalizePage(int page) { return Math.max(1, page); }
    private int normalizeSize(int size) { return Math.min(100, Math.max(1, size)); }
    private String normalizeOptional(String value) { return value == null ? null : value.trim(); }

    private GroupDTO convertToDTO(ImGroup group, String viewerId) {
        ImGroupMember currentMember = StringUtils.hasText(viewerId) ? groupMemberMapper.selectByGroupIdAndUserId(group.getGroupId(), viewerId) : null;
        PlatformUserDirectoryClient.PublicProfile owner = userDirectoryClient
                .findPublicProfile(group.getOwnerId(), viewerId == null ? group.getOwnerId() : viewerId).orElse(null);
        return GroupDTO.builder().groupId(group.getGroupId()).groupName(group.getGroupName()).avatar(group.getAvatar())
                .ownerId(group.getOwnerId()).ownerName(owner == null ? group.getOwnerId() : owner.username())
                .currentUserRole(currentMember == null ? null : currentMember.getRole())
                .conversationId(StringUtils.hasText(viewerId) && currentMember != null ? ConversationIdGenerator.generate(viewerId, group.getGroupId(), ConversationIdGenerator.GROUP) : null)
                .announcement(group.getAnnouncement()).description(group.getDescription()).memberCount(group.getMemberCount())
                .maxMembers(group.getMaxMembers()).joinMode(group.getJoinMode()).isAllMuted(group.getIsAllMuted())
                .status(group.getStatus()).createTime(group.getCreateTime()).updateTime(group.getUpdateTime()).build();
    }

    private GroupMemberDTO convertToDTO(ImGroupMember member, String viewerId) {
        PlatformUserDirectoryClient.PublicProfile profile = userDirectoryClient
                .findPublicProfile(member.getUserId(), viewerId == null ? member.getUserId() : viewerId).orElse(null);
        return GroupMemberDTO.builder().id(member.getId()).groupId(member.getGroupId()).userId(member.getUserId())
                .nickname(profile == null ? member.getUserId() : profile.username()).avatar(profile == null ? null : profile.avatarPath())
                .role(member.getRole()).alias(member.getAlias()).muteUntil(member.getMuteUntil()).lastReadSeq(member.getLastReadSeq())
                .joinTime(member.getJoinTime()).build();
    }
}
