package org.project.service.impl;

import org.project.mapper.*;
import org.project.model.dto.UpdatePermissionRequest;
import org.project.model.entity.*;
import org.project.service.SpaceService;
import org.project.service.ex.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SpaceServiceImpl implements SpaceService {

    @Autowired
    private SpaceMapper spaceMapper;
    @Autowired
    private SpaceMemberMapper spaceMemberMapper;
    @Autowired
    private SpacePermissionMapper spacePermissionMapper;
    @Autowired
    private SpaceJoinRequestMapper spaceJoinRequestMapper;
    @Autowired
    private SpaceVisibilityMapper spaceVisibilityMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String CURRENT_SPACE_KEY = "user:current_space:";
    private static final long SPACE_QUOTA_DEFAULT = 10L * 1024 * 1024 * 1024; // 10GB
    private static final long SPACE_QUOTA_ENTERPRISE = 100L * 1024 * 1024 * 1024; // 100GB
    private static final long SPACE_QUOTA_TEAM = 50L * 1024 * 1024 * 1024; // 50GB
    private static final long SPACE_QUOTA_PUBLIC = 20L * 1024 * 1024 * 1024; // 20GB

    @Override
    @Transactional
    public SpaceEntity createSpace(UUID userId, String spaceName, String spaceType,
                                    String spaceDescription, String spaceVisibility) {
        // 个人空间重复检查
        if ("personal".equals(spaceType)) {
            List<SpaceEntity> existing = spaceMapper.findByOwnerId(userId);
            for (SpaceEntity s : existing) {
                if ("personal".equals(s.getSpaceType())) {
                    throw new InsertException("每个用户只能拥有一个人空间");
                }
            }
        }

        SpaceEntity space = new SpaceEntity();
        space.setSpaceId(UUID.randomUUID());
        space.setSpaceName(spaceName);
        space.setSpaceType(spaceType);
        space.setSpaceOwnerId(userId);
        space.setSpaceVisibility(spaceVisibility != null ? spaceVisibility : "private");
        space.setSpaceDescription(spaceDescription);
        space.setSpaceStatus("active");
        space.setSpaceFileCount(0);

        // 根据类型设置配额
        switch (spaceType) {
            case "enterprise":
                space.setSpaceQuota(SPACE_QUOTA_ENTERPRISE);
                break;
            case "team":
                space.setSpaceQuota(SPACE_QUOTA_TEAM);
                break;
            case "public":
                space.setSpaceQuota(SPACE_QUOTA_PUBLIC);
                break;
            default:
                space.setSpaceQuota(SPACE_QUOTA_DEFAULT);
        }
        space.setSpaceUsed(0L);

        int rows = spaceMapper.insert(space);
        if (rows != 1) {
            throw new InsertException("创建空间失败");
        }

        // 添加创建者为所有者
        SpaceMemberEntity member = new SpaceMemberEntity();
        member.setSpaceId(space.getSpaceId());
        member.setUserId(userId);
        member.setRole("owner");
        spaceMemberMapper.insert(member);

        // 设置默认权限
        SpacePermissionEntity perm = new SpacePermissionEntity();
        perm.setSpaceId(space.getSpaceId());
        perm.setUserId(userId);
        perm.setCanRead(true);
        perm.setCanWrite(true);
        perm.setCanDelete(true);
        perm.setCanShare(true);
        perm.setCanInvite(true);
        perm.setCanManage(true);
        perm.setGrantedBy(userId);
        spacePermissionMapper.upsert(perm);

        return space;
    }

    @Override
    public SpaceEntity getSpaceById(UUID spaceId, UUID userId) {
        SpaceEntity space = spaceMapper.findById(spaceId);
        if (space == null || "deleted".equals(space.getSpaceStatus())) {
            throw new InsertException("空间不存在");
        }

        // 检查是否为成员或公开空间
        SpaceMemberEntity member = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (member == null) {
            if ("public".equals(space.getSpaceType()) && "public".equals(space.getSpaceVisibility())) {
                return space;
            }
            throw new OverstepAuthorityException("无权访问该空间");
        }
        return space;
    }

    @Override
    public List<SpaceEntity> getUserSpaces(UUID userId) {
        return spaceMapper.findByMemberUserId(userId);
    }

    @Override
    public List<SpaceEntity> getUserSpacesByType(UUID userId, String spaceType) {
        List<SpaceEntity> all = spaceMapper.findByMemberUserId(userId);
        return all.stream().filter(s -> spaceType.equals(s.getSpaceType())).toList();
    }

    @Override
    public List<SpaceEntity> discoverPublicSpaces(String keyword) {
        return spaceMapper.findPublicSpaces(keyword);
    }

    @Override
    public SpaceEntity getPublicSpaceByName(String spaceName) {
        SpaceEntity space = spaceMapper.findPublicByName(spaceName);
        if (space == null) {
            throw new InsertException("公共空间不存在");
        }
        return space;
    }

    @Override
    @Transactional
    public SpaceEntity updateSpace(UUID spaceId, UUID userId, String spaceName,
                                    String spaceDescription, String spaceVisibility, Long spaceQuota) {
        SpaceEntity space = spaceMapper.findById(spaceId);
        if (space == null || "deleted".equals(space.getSpaceStatus())) {
            throw new InsertException("空间不存在");
        }

        SpaceMemberEntity member = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (member == null || (!"owner".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new OverstepAuthorityException("无权修改空间信息");
        }

        if (spaceName != null) space.setSpaceName(spaceName);
        if (spaceDescription != null) space.setSpaceDescription(spaceDescription);
        if (spaceVisibility != null) space.setSpaceVisibility(spaceVisibility);
        if (spaceQuota != null) {
            if (!"owner".equals(member.getRole())) {
                throw new OverstepAuthorityException("仅空间所有者可以修改配额");
            }
            space.setSpaceQuota(spaceQuota);
        }

        spaceMapper.update(space);
        return space;
    }

    @Override
    @Transactional
    public void deleteSpace(UUID spaceId, UUID userId) {
        SpaceEntity space = spaceMapper.findById(spaceId);
        if (space == null) {
            throw new InsertException("空间不存在");
        }
        if (!space.getSpaceOwnerId().equals(userId)) {
            throw new OverstepAuthorityException("仅空间所有者可以删除空间");
        }
        if ("personal".equals(space.getSpaceType())) {
            throw new DeleteException("个人空间不可删除");
        }
        spaceMapper.softDelete(spaceId);
    }

    // ==================== 成员管理 ====================

    @Override
    @Transactional
    public void addMember(UUID spaceId, UUID userId, UUID targetUserId, String role) {
        SpaceMemberEntity operator = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (operator == null) {
            throw new OverstepAuthorityException("无权操作");
        }
        if (!"owner".equals(operator.getRole()) && !"admin".equals(operator.getRole())) {
            throw new OverstepAuthorityException("无权邀请成员");
        }

        SpaceMemberEntity existing = spaceMemberMapper.findBySpaceAndUser(spaceId, targetUserId);
        if (existing != null) {
            throw new InsertException("用户已是空间成员");
        }

        SpaceMemberEntity member = new SpaceMemberEntity();
        member.setSpaceId(spaceId);
        member.setUserId(targetUserId);
        member.setRole(role);
        member.setInvitedBy(userId);
        spaceMemberMapper.insert(member);

        // 设置默认权限
        SpacePermissionEntity perm = new SpacePermissionEntity();
        perm.setSpaceId(spaceId);
        perm.setUserId(targetUserId);
        perm.setCanRead(true);
        perm.setCanWrite("editor".equals(role) || "admin".equals(role));
        perm.setCanDelete("editor".equals(role) || "admin".equals(role));
        perm.setCanShare("editor".equals(role) || "admin".equals(role));
        perm.setCanInvite("admin".equals(role));
        perm.setCanManage("admin".equals(role));
        perm.setGrantedBy(userId);
        spacePermissionMapper.upsert(perm);
    }

    @Override
    public List<SpaceMemberEntity> getMembers(UUID spaceId, UUID userId) {
        SpaceMemberEntity member = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (member == null) {
            throw new OverstepAuthorityException("无权查看成员列表");
        }
        return spaceMemberMapper.findBySpaceId(spaceId);
    }

    @Override
    public void updateMemberRole(UUID spaceId, UUID userId, UUID targetUserId, String role) {
        SpaceMemberEntity operator = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (operator == null || (!"owner".equals(operator.getRole()) && !"admin".equals(operator.getRole()))) {
            throw new OverstepAuthorityException("无权修改成员角色");
        }

        SpaceMemberEntity target = spaceMemberMapper.findBySpaceAndUser(spaceId, targetUserId);
        if (target == null) {
            throw new InsertException("成员不存在");
        }
        if ("owner".equals(target.getRole())) {
            throw new OverstepAuthorityException("不可修改空间所有者的角色");
        }

        spaceMemberMapper.updateRole(spaceId, targetUserId, role);
    }

    @Override
    public void removeMember(UUID spaceId, UUID userId, UUID targetUserId) {
        boolean isSelf = userId.equals(targetUserId);
        if (!isSelf) {
            SpaceMemberEntity operator = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
            if (operator == null || (!"owner".equals(operator.getRole()) && !"admin".equals(operator.getRole()))) {
                throw new OverstepAuthorityException("无权移除成员");
            }
        }

        SpaceMemberEntity target = spaceMemberMapper.findBySpaceAndUser(spaceId, targetUserId);
        if (target == null) {
            throw new InsertException("成员不存在");
        }
        if ("owner".equals(target.getRole())) {
            throw new OverstepAuthorityException("不可移除空间所有者");
        }

        spaceMemberMapper.delete(spaceId, targetUserId);
        spacePermissionMapper.delete(spaceId, targetUserId);
    }

    // ==================== 权限管理 ====================

    @Override
    public void updatePermission(UUID spaceId, UUID userId, UUID targetUserId, UpdatePermissionRequest request) {
        SpaceMemberEntity operator = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (operator == null || (!"owner".equals(operator.getRole()) && !"admin".equals(operator.getRole()))) {
            throw new OverstepAuthorityException("无权修改权限");
        }

        SpacePermissionEntity perm = new SpacePermissionEntity();
        perm.setSpaceId(spaceId);
        perm.setUserId(targetUserId);
        perm.setCanRead(request.getCanRead() != null ? request.getCanRead() : true);
        perm.setCanWrite(request.getCanWrite() != null ? request.getCanWrite() : false);
        perm.setCanDelete(request.getCanDelete() != null ? request.getCanDelete() : false);
        perm.setCanShare(request.getCanShare() != null ? request.getCanShare() : false);
        perm.setCanInvite(request.getCanInvite() != null ? request.getCanInvite() : false);
        perm.setCanManage(request.getCanManage() != null ? request.getCanManage() : false);
        perm.setGrantedBy(userId);
        spacePermissionMapper.upsert(perm);
    }

    @Override
    public SpacePermissionEntity getPermission(UUID spaceId, UUID targetUserId) {
        return spacePermissionMapper.findBySpaceUserNode(spaceId, targetUserId, null);
    }

    // ==================== 加入申请 ====================

    @Override
    public void requestJoin(UUID spaceId, UUID userId, String message) {
        SpaceEntity space = spaceMapper.findById(spaceId);
        if (space == null) {
            throw new InsertException("空间不存在");
        }
        if (!"team".equals(space.getSpaceType()) && !"enterprise".equals(space.getSpaceType())) {
            throw new InsertException("仅团队/企业空间支持加入申请");
        }

        SpaceMemberEntity existing = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (existing != null) {
            throw new InsertException("已是空间成员");
        }

        SpaceJoinRequestEntity request = new SpaceJoinRequestEntity();
        request.setSpaceId(spaceId);
        request.setUserId(userId);
        request.setRequestMessage(message);
        request.setStatus("pending");
        spaceJoinRequestMapper.insert(request);
    }

    @Override
    public List<SpaceJoinRequestEntity> getJoinRequests(UUID spaceId, UUID userId, String status) {
        SpaceMemberEntity member = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (member == null || (!"owner".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new OverstepAuthorityException("无权查看申请列表");
        }
        if (status != null) {
            return spaceJoinRequestMapper.findBySpaceIdAndStatus(spaceId, status);
        }
        return spaceJoinRequestMapper.findBySpaceId(spaceId);
    }

    @Override
    @Transactional
    public void reviewJoinRequest(UUID spaceId, UUID reqUserId, UUID reviewerId, String action) {
        SpaceMemberEntity member = spaceMemberMapper.findBySpaceAndUser(spaceId, reviewerId);
        if (member == null || (!"owner".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new OverstepAuthorityException("无权审批申请");
        }

        SpaceJoinRequestEntity request = spaceJoinRequestMapper.findBySpaceAndUser(spaceId, reqUserId);
        if (request == null || !"pending".equals(request.getStatus())) {
            throw new InsertException("申请不存在或已处理");
        }

        if ("approved".equals(action)) {
            addMember(spaceId, reviewerId, reqUserId, "editor");
        }

        spaceJoinRequestMapper.updateStatus(request.getRequestId(), action, reviewerId);
    }

    // ==================== 可见性管理 ====================

    @Override
    @Transactional
    public void updateVisibilityList(UUID spaceId, UUID userId, List<UUID> targetUserIds, String listType) {
        SpaceMemberEntity member = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (member == null || (!"owner".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new OverstepAuthorityException("无权修改可见性");
        }

        spaceVisibilityMapper.deleteBySpaceIdAndType(spaceId, listType);
        for (UUID uid : targetUserIds) {
            SpaceVisibilityEntity entity = new SpaceVisibilityEntity();
            entity.setSpaceId(spaceId);
            entity.setUserId(uid);
            entity.setListType(listType);
            spaceVisibilityMapper.insert(entity);
        }
    }

    @Override
    public List<SpaceVisibilityEntity> getVisibilityList(UUID spaceId, UUID userId) {
        SpaceMemberEntity member = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (member == null) {
            throw new OverstepAuthorityException("无权查看");
        }
        return spaceVisibilityMapper.findBySpaceIdAndType(spaceId, "whitelist");
    }

    // ==================== 当前空间选择 ====================

    @Override
    public String getCurrentSpaceId(UUID userId) {
        String key = CURRENT_SPACE_KEY + userId.toString();
        return stringRedisTemplate.opsForValue().get(key);
    }

    @Override
    public void setCurrentSpaceId(UUID userId, UUID spaceId) {
        String key = CURRENT_SPACE_KEY + userId.toString();
        stringRedisTemplate.opsForValue().set(key, spaceId.toString(), 7, TimeUnit.DAYS);
    }
}