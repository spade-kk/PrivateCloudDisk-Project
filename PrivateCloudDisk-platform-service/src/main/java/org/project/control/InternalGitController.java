package org.project.control;

import org.project.context.SpaceContextHolder;
import org.project.mapper.SpaceMapper;
import org.project.mapper.SpaceMemberMapper;
import org.project.model.entity.SpaceEntity;
import org.project.model.entity.SpaceMemberEntity;
import org.project.model.vo.InternalGitSpaceAuthorizationVO;
import org.project.model.vo.InternalGitTeamMembershipVO;
import org.project.service.SpaceOperation;
import org.project.service.SpacePermissionService;
import org.project.service.ex.InsertException;
import org.project.service.ex.OverstepAuthorityException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * [REQ-GIT-SPACE-1.1/9.6] Git Service 私网授权接口。
 * 该路径受 InternalApiIpInterceptor 的服务令牌保护，并被公网 Gateway 显式拒绝。
 */
@RestController
@RequestMapping("/business/internal/git")
public class InternalGitController {
    private final SpaceMapper spaceMapper;
    private final SpaceMemberMapper spaceMemberMapper;
    private final SpacePermissionService permissionService;

    public InternalGitController(SpaceMapper spaceMapper, SpaceMemberMapper spaceMemberMapper,
                                 SpacePermissionService permissionService) {
        this.spaceMapper = spaceMapper;
        this.spaceMemberMapper = spaceMemberMapper;
        this.permissionService = permissionService;
    }

    @GetMapping("/spaces/{spaceId}/authorization")
    public InternalGitSpaceAuthorizationVO authorize(
            @PathVariable String spaceId,
            @RequestParam(required = false) String userId) {
        UUID parsedSpaceId = UUID.fromString(spaceId);
        SpaceEntity space = spaceMapper.findById(parsedSpaceId);
        String resourceType = space == null || space.getResourceType() == null ? "file" : space.getResourceType();
        if (space == null || !"active".equals(space.getSpaceStatus()) || !"public".equals(space.getSpaceType())
                || !"public".equals(space.getSpaceVisibility()) || !"git".equals(resourceType)) {
            throw new InsertException("Git 公开空间不存在或不可用");
        }

        String permissionLevel = "NONE";
        if (userId != null && !userId.isBlank()) {
            UUID parsedUserId = UUID.fromString(userId);
            if (space.getSpaceOwnerId().equals(parsedUserId)) {
                permissionLevel = "ADMIN";
            } else {
                SpaceContextHolder.SpaceContext context = permissionService.resolveContext(parsedUserId, spaceId);
                SpaceContextHolder.set(context);
                try {
                    if (allowed(context, SpaceOperation.MANAGE_SETTINGS)) permissionLevel = "ADMIN";
                    else if (allowed(context, SpaceOperation.UPLOAD)) permissionLevel = "WRITE";
                    else if (allowed(context, SpaceOperation.READ)) permissionLevel = "READ";
                } finally {
                    SpaceContextHolder.clear();
                }
            }
        } else if (Boolean.TRUE.equals(space.getAllowPublicBrowse())) {
            permissionLevel = "READ";
        }

        return new InternalGitSpaceAuthorizationVO(
                true,
                space.getSpaceId().toString(),
                space.getSpaceOwnerId().toString(),
                resourceType,
                permissionLevel,
                Boolean.TRUE.equals(space.getAllowPublicBrowse()),
                Boolean.TRUE.equals(space.getAllowPublicDownload()),
                Boolean.TRUE.equals(space.getAllowPublicUpload()));
    }

    /**
     * [REQ-GIT-PERM-9.4] TEAM 授权主体映射到现有团队/企业空间，而不是在 Git Service 重复维护成员表。
     * 只有 active 的 team/enterprise 空间成员才会被判定为团队成员；个人/公开空间不能充当团队主体。
     */
    @GetMapping("/teams/{teamId}/members/{userId}")
    public InternalGitTeamMembershipVO teamMembership(
            @PathVariable String teamId,
            @PathVariable String userId) {
        UUID parsedTeamId = UUID.fromString(teamId);
        UUID parsedUserId = UUID.fromString(userId);
        SpaceEntity team = spaceMapper.findById(parsedTeamId);
        if (team == null || !"active".equals(team.getSpaceStatus())
                || !("team".equals(team.getSpaceType()) || "enterprise".equals(team.getSpaceType()))) {
            return new InternalGitTeamMembershipVO(false, teamId, userId, null);
        }
        SpaceMemberEntity member = spaceMemberMapper.findBySpaceAndUser(parsedTeamId, parsedUserId);
        return new InternalGitTeamMembershipVO(
                member != null, teamId, userId, member == null ? null : member.getRole());
    }

    private boolean allowed(SpaceContextHolder.SpaceContext context, SpaceOperation operation) {
        try {
            permissionService.requireOperation(context, operation);
            return true;
        } catch (OverstepAuthorityException ignored) {
            return false;
        }
    }
}
