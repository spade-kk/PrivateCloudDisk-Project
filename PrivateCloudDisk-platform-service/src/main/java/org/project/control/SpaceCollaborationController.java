package org.project.control;

import jakarta.validation.Valid;
import lombok.Data;
import org.project.control.result.JsonResult;
import org.project.model.dto.UpdatePermissionRequest;
import org.project.model.dto.UpdateSpaceRequest;
import org.project.model.entity.SpaceEntity;
import org.project.model.entity.SpaceJoinRequestEntity;
import org.project.model.entity.SpaceMemberEntity;
import org.project.service.SpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 空间协作中心接口。
 *
 * [SPACE-COLLAB-API-03] 新能力独立于旧 SpaceController，旧路径和响应体保持不变；
 * 所有空间写操作仍由 SpaceService 的成员校验和 X-Space-Id 拦截器共同保护。
 */
@RestController
@RequestMapping("/business")
public class SpaceCollaborationController extends BaseController {

    @Autowired
    private SpaceService spaceService;

    @GetMapping("/teamwork/spaces/search")
    public JsonResult<List<SpaceEntity>> search(@RequestParam(required = false) String keyword,
                                                @RequestHeader("X-User-Id") UUID userId) {
        return new JsonResult<>(OK, spaceService.discoverCollaborationSpaces(keyword));
    }

    @GetMapping("/teamwork/spaces/{spaceId}")
    public JsonResult<SpaceEntity> preview(@PathVariable UUID spaceId,
                                           @RequestHeader("X-User-Id") UUID userId) {
        return new JsonResult<>(OK, spaceService.getSpacePreview(spaceId, userId));
    }

    @PostMapping("/teamwork/spaces/{spaceId}/join")
    public JsonResult<Void> join(@PathVariable UUID spaceId,
                                 @RequestHeader("X-User-Id") UUID userId,
                                 @RequestBody(required = false) JoinBody body) {
        JoinBody safe = body == null ? new JoinBody() : body;
        spaceService.requestJoin(spaceId, userId, safe.getMessage(), safe.getInviteToken());
        return new JsonResult<>(OK);
    }

    @GetMapping("/teamwork/my-spaces")
    public JsonResult<List<SpaceEntity>> mySpaces(@RequestHeader("X-User-Id") UUID userId) {
        return new JsonResult<>(OK, spaceService.getUserSpaces(userId));
    }

    @GetMapping("/teamwork/my-requests")
    public JsonResult<List<SpaceJoinRequestEntity>> myRequests(@RequestHeader("X-User-Id") UUID userId) {
        return new JsonResult<>(OK, spaceService.getMyJoinRequests(userId));
    }

    @DeleteMapping("/teamwork/requests/{requestId}")
    public JsonResult<Void> cancelRequest(@PathVariable Long requestId,
                                          @RequestHeader("X-User-Id") UUID userId) {
        spaceService.cancelJoinRequest(requestId, userId);
        return new JsonResult<>(OK);
    }

    @GetMapping("/space/{spaceId}/members")
    public JsonResult<List<SpaceMemberEntity>> members(@PathVariable UUID spaceId,
                                                       @RequestHeader("X-User-Id") UUID userId,
                                                       @RequestParam(required = false) String keyword) {
        return new JsonResult<>(OK, spaceService.getMembers(spaceId, userId, keyword));
    }

    @PutMapping("/space/{spaceId}/members/{targetUserId}/permissions")
    public JsonResult<Void> permissions(@PathVariable UUID spaceId,
                                       @PathVariable UUID targetUserId,
                                       @RequestHeader("X-User-Id") UUID userId,
                                       @Valid @RequestBody UpdatePermissionRequest request) {
        if (request.getRole() != null) {
            spaceService.updateMemberRole(spaceId, userId, targetUserId, request.getRole());
        }
        // 管理员/所有者权限由角色保护；仅自定义成员或显式权限字段进入细粒度更新。
        if (!"admin".equals(request.getRole())) {
            spaceService.updatePermission(spaceId, userId, targetUserId, request);
        }
        return new JsonResult<>(OK);
    }

    @DeleteMapping("/space/{spaceId}/members/{targetUserId}")
    public JsonResult<Void> remove(@PathVariable UUID spaceId,
                                   @PathVariable UUID targetUserId,
                                   @RequestHeader("X-User-Id") UUID userId) {
        spaceService.removeMember(spaceId, userId, targetUserId);
        return new JsonResult<>(OK);
    }

    @PostMapping("/space/{spaceId}/invitations")
    public JsonResult<String> createInvitation(@PathVariable UUID spaceId,
                                               @RequestHeader("X-User-Id") UUID userId,
                                               @RequestBody(required = false) InvitationBody body) {
        InvitationBody safe = body == null ? new InvitationBody() : body;
        return new JsonResult<>(OK, spaceService.createInvitation(spaceId, userId, safe.getExpiresHours(), safe.getMaxUses()));
    }

    @DeleteMapping("/space/{spaceId}/invitations/{invitationId}")
    public JsonResult<Void> revokeInvitation(@PathVariable UUID spaceId,
                                             @PathVariable Long invitationId,
                                             @RequestHeader("X-User-Id") UUID userId) {
        spaceService.revokeInvitation(spaceId, userId, invitationId);
        return new JsonResult<>(OK);
    }

    @PostMapping("/space/invitations/redeem")
    public JsonResult<Void> redeemInvitation(@RequestHeader("X-User-Id") UUID userId,
                                             @RequestBody InvitationTokenBody body) {
        spaceService.redeemInvitation(body == null ? null : body.getToken(), userId);
        return new JsonResult<>(OK);
    }

    @GetMapping("/space/{spaceId}/settings")
    public JsonResult<SpaceEntity> settings(@PathVariable UUID spaceId,
                                            @RequestHeader("X-User-Id") UUID userId) {
        return new JsonResult<>(OK, spaceService.getSpaceById(spaceId, userId));
    }

    @PutMapping("/space/{spaceId}/settings")
    public JsonResult<SpaceEntity> updateSettings(@PathVariable UUID spaceId,
                                                  @RequestHeader("X-User-Id") UUID userId,
                                                  @Valid @RequestBody UpdateSpaceRequest request) {
        return new JsonResult<>(OK, spaceService.updateSpace(spaceId, userId, request.getSpaceName(),
                request.getSpaceDescription(), request.getSpaceVisibility(), request.getJoinPolicy(), request.getSpaceQuota()));
    }

    @GetMapping("/space/{spaceId}/members/approvals")
    public JsonResult<List<SpaceJoinRequestEntity>> approvals(@PathVariable UUID spaceId,
                                                              @RequestHeader("X-User-Id") UUID userId,
                                                              @RequestParam(required = false) String status) {
        return new JsonResult<>(OK, spaceService.getJoinRequests(spaceId, userId, status));
    }

    @PutMapping("/space/{spaceId}/members/approvals/{requestId}")
    public JsonResult<Void> review(@PathVariable UUID spaceId,
                                   @PathVariable Long requestId,
                                   @RequestHeader("X-User-Id") UUID userId,
                                   @RequestBody ReviewBody body) {
        if (body == null || body.getAction() == null) throw new IllegalArgumentException("审批动作不能为空");
        spaceService.reviewJoinRequestById(requestId, userId, body.getAction());
        return new JsonResult<>(OK);
    }

    @Data
    public static class JoinBody {
        private String message;
        private String inviteToken;
    }

    @Data
    public static class ReviewBody {
        private String action;
        private String reason;
    }

    @Data
    public static class InvitationBody {
        private int expiresHours = 72;
        private int maxUses = 10;
    }

    @Data
    public static class InvitationTokenBody {
        private String token;
    }
}
