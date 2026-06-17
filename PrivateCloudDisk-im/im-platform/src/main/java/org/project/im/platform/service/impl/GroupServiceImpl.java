package org.project.im.platform.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.dto.GroupDTO;
import org.project.im.common.dto.GroupMemberDTO;
import org.project.im.common.dto.Result;
import org.project.im.common.enums.GroupRole;
import org.project.im.platform.entity.ImGroup;
import org.project.im.platform.entity.ImGroupMember;
import org.project.im.platform.mapper.ImGroupMapper;
import org.project.im.platform.mapper.ImGroupMemberMapper;
import org.project.im.platform.service.GroupService;
import org.project.im.platform.util.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final ImGroupMapper groupMapper;
    private final ImGroupMemberMapper groupMemberMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<GroupDTO> createGroup(String ownerId, String groupName, String avatar) {
        String groupId = snowflakeIdGenerator.nextIdStr();
        LocalDateTime now = LocalDateTime.now();

        // 创建群组
        ImGroup group = ImGroup.builder()
                .groupId(groupId)
                .groupName(groupName)
                .avatar(avatar)
                .ownerId(ownerId)
                .memberCount(1)
                .maxMembers(MAX_GROUP_MEMBERS)
                .joinMode(0)
                .isAllMuted(false)
                .status(0)
                .createTime(now)
                .updateTime(now)
                .build();
        groupMapper.insert(group);

        // 群主自动加入
        ImGroupMember ownerMember = ImGroupMember.builder()
                .groupId(groupId)
                .userId(ownerId)
                .role(GroupRole.OWNER.getCode())
                .lastReadSeq(0L)
                .joinTime(now)
                .createTime(now)
                .build();
        groupMemberMapper.insert(ownerMember);

        log.info("群组创建成功: groupId={}, groupName={}, ownerId={}", groupId, groupName, ownerId);
        return Result.success(convertToDTO(group));
    }

    @Override
    public Result<GroupDTO> getGroupDetail(String groupId) {
        ImGroup group = groupMapper.selectByGroupId(groupId);
        if (group == null) {
            return Result.error(1003, "群组不存在");
        }
        return Result.success(convertToDTO(group));
    }

    @Override
    public Result<List<GroupDTO>> getUserGroups(String userId) {
        List<ImGroup> groups = groupMapper.selectByUserId(userId);
        List<GroupDTO> dtoList = groups.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return Result.success(dtoList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> joinGroup(String groupId, String userId) {
        ImGroup group = groupMapper.selectByGroupId(groupId);
        if (group == null) {
            return Result.error(1003, "群组不存在");
        }
        if (group.getStatus() == 1) {
            return Result.error(1003, "群组已解散");
        }
        if (groupMemberMapper.existsByGroupIdAndUserId(groupId, userId) > 0) {
            return Result.error(1005, "已是群组成员");
        }
        if (group.getMemberCount() >= group.getMaxMembers()) {
            return Result.error(1006, "群组已满");
        }
        if (group.getJoinMode() == 2) {
            return Result.error(1007, "该群组禁止加入");
        }

        // 加入群组
        ImGroupMember member = ImGroupMember.builder()
                .groupId(groupId)
                .userId(userId)
                .role(GroupRole.MEMBER.getCode())
                .lastReadSeq(0L)
                .joinTime(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        groupMemberMapper.insert(member);
        groupMapper.incrementMemberCount(groupId);

        log.info("加入群组成功: groupId={}, userId={}", groupId, userId);
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> leaveGroup(String groupId, String userId) {
        ImGroup group = groupMapper.selectByGroupId(groupId);
        if (group == null) {
            return Result.error(1003, "群组不存在");
        }
        if (group.getOwnerId().equals(userId)) {
            return Result.error(1007, "群主不可退出，请先转让群主");
        }
        if (groupMemberMapper.existsByGroupIdAndUserId(groupId, userId) == 0) {
            return Result.error(1004, "不是群组成员");
        }

        groupMemberMapper.deleteByGroupIdAndUserId(groupId, userId);
        groupMapper.decrementMemberCount(groupId);

        log.info("退出群组成功: groupId={}, userId={}", groupId, userId);
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> kickMember(String groupId, String operatorId, String targetUid) {
        ImGroup group = groupMapper.selectByGroupId(groupId);
        if (group == null) {
            return Result.error(1003, "群组不存在");
        }
        ImGroupMember operator = groupMemberMapper.selectByGroupIdAndUserId(groupId, operatorId);
        if (operator == null || (operator.getRole() != GroupRole.OWNER.getCode()
                && operator.getRole() != GroupRole.ADMIN.getCode())) {
            return Result.error(1007, "无权限操作");
        }
        ImGroupMember target = groupMemberMapper.selectByGroupIdAndUserId(groupId, targetUid);
        if (target == null) {
            return Result.error(1004, "目标用户不是群成员");
        }
        // 管理员不能踢群主和其他管理员
        if (operator.getRole() == GroupRole.ADMIN.getCode()
                && (target.getRole() == GroupRole.OWNER.getCode()
                || target.getRole() == GroupRole.ADMIN.getCode())) {
            return Result.error(1007, "管理员不能踢群主或其他管理员");
        }

        groupMemberMapper.deleteByGroupIdAndUserId(groupId, targetUid);
        groupMapper.decrementMemberCount(groupId);

        log.info("踢出成员成功: groupId={}, operatorId={}, targetUid={}", groupId, operatorId, targetUid);
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> muteMember(String groupId, String operatorId, String targetUid,
                                    int durationMinutes) {
        ImGroupMember operator = groupMemberMapper.selectByGroupIdAndUserId(groupId, operatorId);
        if (operator == null || (operator.getRole() != GroupRole.OWNER.getCode()
                && operator.getRole() != GroupRole.ADMIN.getCode())) {
            return Result.error(1007, "无权限操作");
        }
        ImGroupMember target = groupMemberMapper.selectByGroupIdAndUserId(groupId, targetUid);
        if (target == null) {
            return Result.error(1004, "目标用户不是群成员");
        }
        if (target.getRole() == GroupRole.OWNER.getCode()) {
            return Result.error(1007, "不能禁言群主");
        }

        LocalDateTime muteUntil = durationMinutes == -1
                ? LocalDateTime.of(2099, 12, 31, 23, 59, 59)
                : LocalDateTime.now().plusMinutes(durationMinutes);
        groupMemberMapper.updateMute(groupId, targetUid, muteUntil);

        log.info("禁言成员成功: groupId={}, targetUid={}, durationMinutes={}", groupId, targetUid, durationMinutes);
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unmuteMember(String groupId, String operatorId, String targetUid) {
        groupMemberMapper.updateMute(groupId, targetUid, null);
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> muteAll(String groupId, String operatorId, boolean isAllMuted) {
        ImGroup group = groupMapper.selectByGroupId(groupId);
        if (group == null) {
            return Result.error(1003, "群组不存在");
        }
        if (!group.getOwnerId().equals(operatorId)) {
            return Result.error(1007, "仅群主可以设置全员禁言");
        }
        groupMapper.updateAllMuted(groupId, isAllMuted);

        log.info("全员禁言设置: groupId={}, isAllMuted={}", groupId, isAllMuted);
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> dissolveGroup(String groupId, String ownerId) {
        ImGroup group = groupMapper.selectByGroupId(groupId);
        if (group == null) {
            return Result.error(1003, "群组不存在");
        }
        if (!group.getOwnerId().equals(ownerId)) {
            return Result.error(1007, "仅群主可以解散群组");
        }
        groupMapper.dissolve(groupId);

        log.info("群组解散: groupId={}, ownerId={}", groupId, ownerId);
        return Result.success(null);
    }

    @Override
    public Result<List<GroupMemberDTO>> getGroupMembers(String groupId) {
        List<ImGroupMember> members = groupMemberMapper.selectByGroupId(groupId);
        List<GroupMemberDTO> dtoList = members.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return Result.success(dtoList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateAnnouncement(String groupId, String operatorId, String announcement) {
        ImGroupMember member = groupMemberMapper.selectByGroupIdAndUserId(groupId, operatorId);
        if (member == null || (member.getRole() != GroupRole.OWNER.getCode()
                && member.getRole() != GroupRole.ADMIN.getCode())) {
            return Result.error(1007, "无权限修改公告");
        }
        groupMapper.updateAnnouncement(groupId, announcement);
        return Result.success(null);
    }

    // ==================== 私有方法 ====================

    private GroupDTO convertToDTO(ImGroup group) {
        return GroupDTO.builder()
                .groupId(group.getGroupId())
                .groupName(group.getGroupName())
                .avatar(group.getAvatar())
                .ownerId(group.getOwnerId())
                .announcement(group.getAnnouncement())
                .description(group.getDescription())
                .memberCount(group.getMemberCount())
                .maxMembers(group.getMaxMembers())
                .joinMode(group.getJoinMode())
                .isAllMuted(group.getIsAllMuted())
                .status(group.getStatus())
                .createTime(group.getCreateTime())
                .updateTime(group.getUpdateTime())
                .build();
    }

    private GroupMemberDTO convertToDTO(ImGroupMember member) {
        return GroupMemberDTO.builder()
                .id(member.getId())
                .groupId(member.getGroupId())
                .userId(member.getUserId())
                .role(member.getRole())
                .alias(member.getAlias())
                .muteUntil(member.getMuteUntil())
                .lastReadSeq(member.getLastReadSeq())
                .joinTime(member.getJoinTime())
                .build();
    }
}