package org.project.service.impl;

import org.project.context.SpaceContextHolder;
import org.project.mapper.SpaceMapper;
import org.project.mapper.SpaceMemberMapper;
import org.project.mapper.SpacePermissionMapper;
import org.project.mapper.SpaceResourceScopeMapper;
import org.project.model.entity.SpaceEntity;
import org.project.model.entity.SpaceMemberEntity;
import org.project.model.entity.SpacePermissionEntity;
import org.project.service.SpaceOperation;
import org.project.service.SpacePermissionService;
import org.project.service.ex.FileNotExistException;
import org.project.service.ex.InvalidUploadsSessionException;
import org.project.service.ex.NodeNotExistException;
import org.project.service.ex.OverstepAuthorityException;
import org.project.service.ex.ServiceException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

/**
 * 公共空间权限校验实现。
 *
 * <p>需求：空间管理能力全量集成（二、三）。
 * 原行为：每个空间接口各自判断 owner/admin，文件业务完全依赖 user_id。
 * 新行为：请求头为空时解析个人空间；请求头存在时校验空间、成员与细粒度权限，
 * 并将结果交给 SpaceContextHolder，后续业务继续复用原调用链。</p>
 */
@Service
public class SpacePermissionServiceImpl implements SpacePermissionService {

    private final SpaceMapper spaceMapper;
    private final SpaceMemberMapper spaceMemberMapper;
    private final SpacePermissionMapper spacePermissionMapper;
    private final SpaceResourceScopeMapper resourceScopeMapper;

    public SpacePermissionServiceImpl(
            SpaceMapper spaceMapper,
            SpaceMemberMapper spaceMemberMapper,
            SpacePermissionMapper spacePermissionMapper,
            SpaceResourceScopeMapper resourceScopeMapper) {
        this.spaceMapper = spaceMapper;
        this.spaceMemberMapper = spaceMemberMapper;
        this.spacePermissionMapper = spacePermissionMapper;
        this.resourceScopeMapper = resourceScopeMapper;
    }

    @Override
    public SpaceContextHolder.SpaceContext resolveContext(UUID userId, String requestedSpaceId) {
        boolean explicit = requestedSpaceId != null && !requestedSpaceId.isBlank();
        SpaceEntity space;
        if (explicit) {
            UUID spaceId;
            try {
                spaceId = UUID.fromString(requestedSpaceId.trim());
            } catch (IllegalArgumentException ex) {
                throw new ServiceException("X-Space-Id 格式无效");
            }
            space = spaceMapper.findById(spaceId);
        } else {
            space = spaceMapper.findPersonalByOwnerId(userId);
        }

        if (space == null || !"active".equalsIgnoreCase(space.getSpaceStatus())) {
            throw new ServiceException(explicit ? "空间不存在或不可用" : "默认个人空间不存在，请先执行空间数据迁移");
        }

        SpaceMemberEntity member = spaceMemberMapper.findBySpaceAndUser(space.getSpaceId(), userId);
        if (member == null) {
            // 公开仓库没有成员概念：登录用户按仓库开关获得只读/上传上下文。
            if ("public".equalsIgnoreCase(space.getSpaceType()) && "public".equalsIgnoreCase(space.getSpaceVisibility())) {
                String publicRole = Boolean.TRUE.equals(space.getAllowPublicUpload()) ? "public_uploader" : "public_viewer";
                return new SpaceContextHolder.SpaceContext(
                        space.getSpaceId(), userId, space.getSpaceName(), publicRole, explicit, false);
            }
            throw new OverstepAuthorityException("您不是该空间成员，无权访问");
        }

        return new SpaceContextHolder.SpaceContext(
                space.getSpaceId(),
                userId,
                space.getSpaceName(),
                member.getRole(),
                explicit,
                "personal".equalsIgnoreCase(space.getSpaceType()));
    }

    @Override
    public void requireOperation(SpaceContextHolder.SpaceContext context, SpaceOperation operation) {
        String role = context.role() == null ? "" : context.role().toLowerCase(Locale.ROOT);
        if ("public_viewer".equals(role) || "public_uploader".equals(role)) {
            SpaceEntity publicSpace = spaceMapper.findById(context.spaceId());
            boolean allowed = switch (operation) {
                case VIEW, READ -> Boolean.TRUE.equals(publicSpace != null && publicSpace.getAllowPublicBrowse());
                case DOWNLOAD -> Boolean.TRUE.equals(publicSpace != null && publicSpace.getAllowPublicDownload());
                case UPLOAD -> "public_uploader".equals(role)
                        && Boolean.TRUE.equals(publicSpace != null && publicSpace.getAllowPublicUpload());
                default -> false;
            };
            if (!allowed) throw new OverstepAuthorityException("公开仓库当前未开放该操作: " + operation.name());
            return;
        }
        if ("owner".equals(role) || "admin".equals(role)) {
            return;
        }

        SpacePermissionEntity permission =
                spacePermissionMapper.findBySpaceUserNode(context.spaceId(), context.userId(), null);

        boolean allowed = switch (operation) {
            case VIEW -> hasPermission(permission, SpacePermissionEntity::getCanView)
                    || hasPermission(permission, SpacePermissionEntity::getCanRead)
                    || "viewer".equals(role) || "editor".equals(role);
            case READ -> hasPermission(permission, SpacePermissionEntity::getCanRead)
                    || "viewer".equals(role) || "editor".equals(role);
            case DOWNLOAD -> hasPermission(permission, SpacePermissionEntity::getCanDownload)
                    || hasPermission(permission, SpacePermissionEntity::getCanRead)
                    || "viewer".equals(role) || "editor".equals(role);
            case UPLOAD -> hasPermission(permission, SpacePermissionEntity::getCanUpload)
                    || hasPermission(permission, SpacePermissionEntity::getCanWrite)
                    || "editor".equals(role);
            case EDIT -> hasPermission(permission, SpacePermissionEntity::getCanEdit)
                    || hasPermission(permission, SpacePermissionEntity::getCanWrite)
                    || "editor".equals(role);
            case DELETE -> hasPermission(permission, SpacePermissionEntity::getCanDelete)
                    || "editor".equals(role);
            case SHARE -> hasPermission(permission, SpacePermissionEntity::getCanShare)
                    || "editor".equals(role);
            case MANAGE -> hasPermission(permission, SpacePermissionEntity::getCanManage);
            case MANAGE_MEMBERS -> hasPermission(permission, SpacePermissionEntity::getCanManageMembers)
                    || hasPermission(permission, SpacePermissionEntity::getCanInvite);
            case MANAGE_PLUGINS -> hasPermission(permission, SpacePermissionEntity::getCanManagePlugins)
                    || hasPermission(permission, SpacePermissionEntity::getCanManage);
            case MANAGE_SETTINGS -> hasPermission(permission, SpacePermissionEntity::getCanManageSettings)
                    || hasPermission(permission, SpacePermissionEntity::getCanManage);
        };

        if (!allowed) {
            throw new OverstepAuthorityException("当前空间角色无权执行该操作: " + operation.name());
        }
    }

    private boolean hasPermission(
            SpacePermissionEntity permission,
            java.util.function.Function<SpacePermissionEntity, Boolean> getter) {
        return permission != null && Boolean.TRUE.equals(getter.apply(permission));
    }

    @Override
    public void requireFileInCurrentSpace(UUID fileId) {
        SpaceContextHolder.SpaceContext context = requireContext();
        if (!validateFileInResolvedContext(context, fileId)) {
            throw new FileNotExistException("文件不存在或不属于当前空间");
        }
    }

    @Override
    public boolean validateFileInSpace(UUID userId, UUID spaceId, UUID fileId) {
        if (userId == null || fileId == null) return false;
        SpaceContextHolder.SpaceContext context = resolveContext(
                userId, spaceId == null ? null : spaceId.toString());
        return validateFileInResolvedContext(context, fileId);
    }

    private boolean validateFileInResolvedContext(
            SpaceContextHolder.SpaceContext context, UUID fileId) {
        return resourceScopeMapper.countFileInSpace(
                fileId, context.spaceId(), context.userId(), context.personalSpace()) == 1;
    }

    @Override
    public void requireNodeInCurrentSpace(UUID nodeId) {
        SpaceContextHolder.SpaceContext context = requireContext();
        if (resourceScopeMapper.countNodeInSpace(
                nodeId, context.spaceId(), context.userId(), context.personalSpace()) != 1) {
            throw new NodeNotExistException("目录不存在或不属于当前空间");
        }
    }

    @Override
    public void requireUploadSessionInCurrentSpace(UUID uploadsId) {
        SpaceContextHolder.SpaceContext context = requireContext();
        if (resourceScopeMapper.countUploadInSpace(
                uploadsId, context.spaceId(), context.userId(), context.personalSpace()) != 1) {
            throw new InvalidUploadsSessionException("上传会话不存在或不属于当前空间");
        }
    }

    private SpaceContextHolder.SpaceContext requireContext() {
        SpaceContextHolder.SpaceContext context = SpaceContextHolder.get();
        if (context == null) {
            throw new ServiceException("空间上下文未初始化");
        }
        return context;
    }
}
