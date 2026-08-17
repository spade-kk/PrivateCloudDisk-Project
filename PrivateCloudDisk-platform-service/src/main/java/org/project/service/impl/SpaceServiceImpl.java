package org.project.service.impl;

import org.project.context.SpaceContextHolder;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
    private SpaceInvitationMapper spaceInvitationMapper;
    @Autowired
    private FolderNodeMapper folderNodeMapper;
    @Autowired
    private DirectoryClosureMapper directoryClosureMapper;
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
                                    String spaceDescription, String spaceVisibility, String joinPolicy) {
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
        space.setJoinPolicy(joinPolicy != null ? joinPolicy : ("enterprise".equals(spaceType) || "team".equals(spaceType) ? "approval_required" : "invite_only"));
        // 公开空间在产品层定义为仓库：固定 visible/invite_only 语义，默认可浏览可下载、禁止公开上传。
        if ("public".equals(spaceType)) {
            space.setSpaceVisibility("public");
            space.setJoinPolicy("invite_only");
            space.setAllowPublicBrowse(true);
            space.setAllowPublicDownload(true);
            space.setAllowPublicUpload(false);
        }
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
        perm.setCanView(true);
        perm.setCanDownload(true);
        perm.setCanUpload(true);
        perm.setCanEdit(true);
        perm.setCanManageMembers(true);
        perm.setCanManagePlugins(true);
        perm.setCanManageSettings(true);
        perm.setGrantedBy(userId);
        spacePermissionMapper.upsert(perm);

        /*
         * 空间管理能力全量集成（需求四-2/五-1）：
         * 原行为创建空间时只有空间/成员记录，没有根目录，切换后文件浏览器无法加载；
         * 新行为在同一事务中创建独立根节点与闭包自引用，既有空间 CRUD 返回结构不变。
         */
        FolderNodeEntity rootNode = new FolderNodeEntity();
        rootNode.setNode_id(UUID.randomUUID());
        rootNode.setUser_id(userId);
        rootNode.setParent_id(null);
        rootNode.setName(spaceName);
        rootNode.setCreate_time(LocalDateTime.now().toString());
        rootNode.setStatus(FolderNodeEntity.NodeStatus.active);
        rootNode.setSpace_id(space.getSpaceId());
        if (folderNodeMapper.insertFolderNode(rootNode) != 1) {
            throw new InsertException("空间根目录创建失败");
        }

        SpaceContextHolder.SpaceContext previousContext = SpaceContextHolder.get();
        SpaceContextHolder.set(new SpaceContextHolder.SpaceContext(
                space.getSpaceId(), userId, spaceName, "owner", true, "personal".equals(spaceType)));
        try {
            directoryClosureMapper.insertSelf(rootNode.getNode_id(), userId);
        } finally {
            if (previousContext == null) {
                SpaceContextHolder.clear();
            } else {
                SpaceContextHolder.set(previousContext);
            }
        }

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
            if ("public".equals(space.getSpaceType()) && "public".equals(space.getSpaceVisibility())
                    && Boolean.TRUE.equals(space.getAllowPublicBrowse())) {
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
    public List<SpaceEntity> discoverCollaborationSpaces(String keyword) {
        return spaceMapper.findDiscoverableSpaces(keyword);
    }

    @Override
    public SpaceEntity getSpacePreview(UUID spaceId, UUID userId) {
        SpaceEntity space = spaceMapper.findById(spaceId);
        if (space == null || "deleted".equals(space.getSpaceStatus())) {
            throw new InsertException("空间不存在");
        }
        SpaceMemberEntity member = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (member == null && ("personal".equals(space.getSpaceType())
                || "hidden".equals(space.getSpaceVisibility())
                || "private".equals(space.getSpaceVisibility()))) {
            throw new OverstepAuthorityException("该空间不可被发现");
        }
        return space;
    }

    @Override
    public List<SpaceJoinRequestEntity> getMyJoinRequests(UUID userId) {
        return spaceJoinRequestMapper.findByUserId(userId);
    }

    @Override
    @Transactional
    public void cancelJoinRequest(Long requestId, UUID userId) {
        if (spaceJoinRequestMapper.deletePending(requestId, userId) != 1) {
            throw new InsertException("申请不存在、已处理或无权取消");
        }
    }

    @Override
    @Transactional
    public String createInvitation(UUID spaceId, UUID userId, int expiresHours, int maxUses) {
        SpaceMemberEntity operator = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (operator == null || (!"owner".equals(operator.getRole()) && !"admin".equals(operator.getRole())
                && !hasPermission(spaceId, userId, SpacePermissionEntity::getCanManageMembers))) {
            throw new OverstepAuthorityException("无权创建邀请链接");
        }
        int safeHours = Math.min(Math.max(expiresHours, 1), 24 * 30);
        int safeUses = Math.min(Math.max(maxUses, 1), 1000);
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        SpaceInvitationEntity invitation = new SpaceInvitationEntity();
        invitation.setSpaceId(spaceId);
        invitation.setCreatedBy(userId);
        invitation.setTokenHash(sha256(token));
        invitation.setExpiresAt(LocalDateTime.now().plusHours(safeHours));
        invitation.setMaxUses(safeUses);
        invitation.setStatus("active");
        spaceInvitationMapper.insert(invitation);
        return token;
    }

    @Override
    @Transactional
    public void redeemInvitation(String token, UUID userId) {
        if (token == null || token.isBlank()) throw new OverstepAuthorityException("邀请令牌不能为空");
        SpaceInvitationEntity invitation = spaceInvitationMapper.findActiveByHash(sha256(token.trim()));
        if (invitation == null) throw new OverstepAuthorityException("邀请链接已失效");
        if (spaceMemberMapper.findBySpaceAndUser(invitation.getSpaceId(), userId) != null) return;
        SpaceMemberEntity member = new SpaceMemberEntity();
        member.setSpaceId(invitation.getSpaceId()); member.setUserId(userId); member.setRole("editor"); member.setInvitedBy(invitation.getCreatedBy());
        spaceMemberMapper.insert(member);
        SpacePermissionEntity permission = new SpacePermissionEntity();
        permission.setSpaceId(invitation.getSpaceId()); permission.setUserId(userId); permission.setCanRead(true); permission.setCanView(true); permission.setCanDownload(true); permission.setCanUpload(true); permission.setCanEdit(true); permission.setCanDelete(true); permission.setCanShare(true); permission.setGrantedBy(invitation.getCreatedBy());
        spacePermissionMapper.upsert(permission);
        if (spaceInvitationMapper.consume(invitation.getInvitationId()) != 1) throw new OverstepAuthorityException("邀请链接已达到使用上限");
    }

    @Override
    public void revokeInvitation(UUID spaceId, UUID userId, Long invitationId) {
        SpaceMemberEntity operator = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (operator == null || (!"owner".equals(operator.getRole()) && !"admin".equals(operator.getRole())
                && !hasPermission(spaceId, userId, SpacePermissionEntity::getCanManageMembers))) throw new OverstepAuthorityException("无权撤销邀请");
        if (spaceInvitationMapper.revoke(invitationId, spaceId) != 1) throw new InsertException("邀请不存在");
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
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
                                    String spaceDescription, String spaceVisibility, String joinPolicy, Long spaceQuota) {
        SpaceEntity space = spaceMapper.findById(spaceId);
        if (space == null || "deleted".equals(space.getSpaceStatus())) {
            throw new InsertException("空间不存在");
        }

        SpaceMemberEntity member = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (member == null || (!"owner".equals(member.getRole()) && !"admin".equals(member.getRole())
                && !hasPermission(spaceId, userId, SpacePermissionEntity::getCanManageSettings))) {
            throw new OverstepAuthorityException("无权修改空间信息");
        }

        if (spaceName != null) space.setSpaceName(spaceName);
        if (spaceDescription != null) space.setSpaceDescription(spaceDescription);
        if ("public".equals(space.getSpaceType())) {
            // 公开仓库的 visibility 固定为 public；保留旧 DTO 字段但禁止通过通用接口改成成员空间。
            space.setSpaceVisibility("public");
            space.setJoinPolicy("invite_only");
        } else if (spaceVisibility != null) {
            space.setSpaceVisibility(spaceVisibility);
            if ("personal".equals(space.getSpaceType())) space.setSpaceVisibility("hidden");
        }
        if (joinPolicy != null && !"personal".equals(space.getSpaceType()) && !"public".equals(space.getSpaceType())) {
            space.setJoinPolicy(joinPolicy);
        }
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
    public SpaceEntity updatePublicRepository(UUID spaceId, UUID userId, String spaceName, String description,
                                               Boolean allowBrowse, Boolean allowDownload, Boolean allowUpload) {
        SpaceEntity space = spaceMapper.findById(spaceId);
        if (space == null || "deleted".equals(space.getSpaceStatus()) || !"public".equals(space.getSpaceType())) {
            throw new InsertException("公开仓库不存在");
        }
        if (!space.getSpaceOwnerId().equals(userId)) {
            throw new OverstepAuthorityException("仅公开仓库所有者可以修改设置");
        }
        if (spaceName != null) space.setSpaceName(spaceName);
        if (description != null) space.setSpaceDescription(description);
        if (allowBrowse != null) space.setAllowPublicBrowse(allowBrowse);
        if (allowDownload != null) space.setAllowPublicDownload(allowDownload);
        if (allowUpload != null) space.setAllowPublicUpload(allowUpload);
        // 公开仓库不允许被更新为普通空间可见性，防止通过旧接口绕过产品边界。
        space.setSpaceVisibility("public");
        spaceMapper.update(space);
        return spaceMapper.findById(spaceId);
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
        if (!"owner".equals(operator.getRole()) && !"admin".equals(operator.getRole())
                && !hasPermission(spaceId, userId, SpacePermissionEntity::getCanManageMembers)) {
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
        boolean editor = "editor".equals(role) || "admin".equals(role);
        boolean admin = "admin".equals(role);
        perm.setCanRead(true);
        perm.setCanWrite(editor);
        perm.setCanDelete(editor);
        perm.setCanShare(editor);
        perm.setCanInvite(admin);
        perm.setCanManage(admin);
        perm.setCanView(true);
        perm.setCanDownload(true);
        perm.setCanUpload(editor);
        perm.setCanEdit(editor);
        perm.setCanManageMembers(admin);
        perm.setCanManagePlugins(admin);
        perm.setCanManageSettings(admin);
        perm.setGrantedBy(userId);
        spacePermissionMapper.upsert(perm);
    }

    @Override
    public List<SpaceMemberEntity> getMembers(UUID spaceId, UUID userId, String keyword) {
        SpaceMemberEntity member = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (member == null) {
            throw new OverstepAuthorityException("无权查看成员列表");
        }
        return keyword == null || keyword.isBlank()
                ? spaceMemberMapper.findBySpaceId(spaceId)
                : spaceMemberMapper.findBySpaceIdAndKeyword(spaceId, keyword.trim());
    }

    @Override
    public void updateMemberRole(UUID spaceId, UUID userId, UUID targetUserId, String role) {
        SpaceMemberEntity operator = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (operator == null || (!"owner".equals(operator.getRole()) && !"admin".equals(operator.getRole())
                && !hasPermission(spaceId, userId, SpacePermissionEntity::getCanManageMembers))) {
            throw new OverstepAuthorityException("无权修改成员角色");
        }

        SpaceMemberEntity target = spaceMemberMapper.findBySpaceAndUser(spaceId, targetUserId);
        if (target == null) {
            throw new InsertException("成员不存在");
        }
        if ("owner".equals(target.getRole())) {
            throw new OverstepAuthorityException("不可修改空间所有者的角色");
        }
        if ("admin".equals(target.getRole()) && !"owner".equals(operator.getRole())) {
            throw new OverstepAuthorityException("管理员角色只能由空间所有者调整");
        }

        spaceMemberMapper.updateRole(spaceId, targetUserId, role);
    }

    @Override
    public void removeMember(UUID spaceId, UUID userId, UUID targetUserId) {
        boolean isSelf = userId.equals(targetUserId);
        if (!isSelf) {
            SpaceMemberEntity operator = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
            if (operator == null || (!"owner".equals(operator.getRole()) && !"admin".equals(operator.getRole())
                    && !hasPermission(spaceId, userId, SpacePermissionEntity::getCanManageMembers))) {
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
        if (operator == null || (!"owner".equals(operator.getRole()) && !"admin".equals(operator.getRole())
                && !hasPermission(spaceId, userId, SpacePermissionEntity::getCanManageMembers))) {
            throw new OverstepAuthorityException("无权修改权限");
        }
        SpaceMemberEntity target = spaceMemberMapper.findBySpaceAndUser(spaceId, targetUserId);
        if (target == null) throw new InsertException("成员不存在");
        if ("owner".equals(target.getRole()) || "admin".equals(target.getRole())) {
            throw new OverstepAuthorityException("所有者/管理员权限不可直接修改，请先完成角色转移确认");
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
        perm.setCanView(request.getCanView() != null ? request.getCanView() : perm.getCanRead());
        perm.setCanDownload(request.getCanDownload() != null ? request.getCanDownload() : perm.getCanRead());
        perm.setCanUpload(request.getCanUpload() != null ? request.getCanUpload() : perm.getCanWrite());
        perm.setCanEdit(request.getCanEdit() != null ? request.getCanEdit() : perm.getCanWrite());
        perm.setCanManageMembers(request.getCanManageMembers() != null ? request.getCanManageMembers() : perm.getCanInvite());
        perm.setCanManagePlugins(request.getCanManagePlugins() != null ? request.getCanManagePlugins() : perm.getCanManage());
        perm.setCanManageSettings(request.getCanManageSettings() != null ? request.getCanManageSettings() : perm.getCanManage());
        perm.setGrantedBy(userId);
        spacePermissionMapper.upsert(perm);
    }

    @Override
    public SpacePermissionEntity getPermission(UUID spaceId, UUID targetUserId) {
        return spacePermissionMapper.findBySpaceUserNode(spaceId, targetUserId, null);
    }

    // ==================== 加入申请 ====================

    @Override
    public void requestJoin(UUID spaceId, UUID userId, String message, String inviteToken) {
        SpaceEntity space = spaceMapper.findById(spaceId);
        if (space == null) {
            throw new InsertException("空间不存在");
        }
        if (!"team".equals(space.getSpaceType()) && !"enterprise".equals(space.getSpaceType()) && !"private".equals(space.getSpaceType())) {
            throw new InsertException("仅团队/企业空间支持加入申请");
        }
        if (!"visible".equals(space.getSpaceVisibility()) && !"public".equals(space.getSpaceVisibility())) {
            throw new OverstepAuthorityException("该空间未开放发现和加入");
        }

        SpaceMemberEntity existing = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (existing != null) {
            throw new InsertException("已是空间成员");
        }

        // [SPACE-COLLAB-JOIN-01] open 直接加入；旧客户端仍走同一接口，默认策略保持 approval。
        if ("open".equals(space.getJoinPolicy())) {
            SpaceMemberEntity member = new SpaceMemberEntity();
            member.setSpaceId(spaceId);
            member.setUserId(userId);
            member.setRole("editor");
            spaceMemberMapper.insert(member);
            SpacePermissionEntity permission = new SpacePermissionEntity();
            permission.setSpaceId(spaceId);
            permission.setUserId(userId);
            permission.setCanRead(true);
            permission.setCanView(true);
            permission.setCanDownload(true);
            permission.setCanUpload(true);
            permission.setCanEdit(true);
            permission.setCanDelete(true);
            permission.setCanShare(true);
            permission.setGrantedBy(space.getSpaceOwnerId());
            spacePermissionMapper.upsert(permission);
            return;
        }
        if (inviteToken != null && !inviteToken.isBlank()) {
            // [SPACE-COLLAB-INVITE-02] 邀请令牌走统一哈希/次数校验，并在同一事务中加入成员。
            redeemInvitation(inviteToken, userId);
            return;
        }
        if ("invite_only".equals(space.getJoinPolicy())) {
            throw new OverstepAuthorityException("当前空间仅限邀请加入");
        }

        SpaceJoinRequestEntity latest = spaceJoinRequestMapper.findBySpaceAndUser(spaceId, userId);
        if (latest != null && "pending".equals(latest.getStatus())) {
            throw new InsertException("已提交申请，请等待审批");
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
        if (member == null || (!"owner".equals(member.getRole()) && !"admin".equals(member.getRole())
                && !hasPermission(spaceId, userId, SpacePermissionEntity::getCanManageMembers))) {
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
        if (member == null || (!"owner".equals(member.getRole()) && !"admin".equals(member.getRole())
                && !hasPermission(spaceId, reviewerId, SpacePermissionEntity::getCanManageMembers))) {
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

    @Override
    @Transactional
    public void reviewJoinRequestById(Long requestId, UUID reviewerId, String action) {
        SpaceJoinRequestEntity request = spaceJoinRequestMapper.findById(requestId);
        if (request == null) throw new InsertException("申请不存在");
        reviewJoinRequest(request.getSpaceId(), request.getUserId(), reviewerId, action);
    }

    // ==================== 可见性管理 ====================

    @Override
    @Transactional
    public void updateVisibilityList(UUID spaceId, UUID userId, List<UUID> targetUserIds, String listType) {
        SpaceMemberEntity member = spaceMemberMapper.findBySpaceAndUser(spaceId, userId);
        if (member == null || (!"owner".equals(member.getRole()) && !"admin".equals(member.getRole())
                && !hasPermission(spaceId, userId, SpacePermissionEntity::getCanManageSettings))) {
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

    /** [SPACE-COLLAB-PERM-03] 自定义角色的权限查询统一从空间级权限记录读取。 */
    private boolean hasPermission(UUID spaceId, UUID userId,
                                  java.util.function.Function<SpacePermissionEntity, Boolean> getter) {
        SpacePermissionEntity permission = spacePermissionMapper.findBySpaceUserNode(spaceId, userId, null);
        return permission != null && Boolean.TRUE.equals(getter.apply(permission));
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
