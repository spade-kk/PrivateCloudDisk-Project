package org.project.control;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.project.control.result.JsonResult;
import org.project.model.dto.*;
import org.project.model.entity.*;
import org.project.service.SpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@Validated
@RequestMapping("/business")
public class SpaceController extends BaseController {

    @Autowired
    private SpaceService spaceService;

    // ============================================================
    // 空间 CRUD
    // ============================================================

    @PostMapping({"/spaces", "/spaces/"})
    public JsonResult<SpaceEntity> createSpace(
            @Valid @RequestBody CreateSpaceRequest request,
            @RequestHeader("X-User-Id") String userId) {
        SpaceEntity space = spaceService.createSpace(
                UUID.fromString(userId), request.getSpaceName(), request.getSpaceType(),
                request.getSpaceDescription(), request.getSpaceVisibility(), request.getJoinPolicy());
        // 公开仓库的权限开关由独立设置接口维护；创建接口保持旧请求体兼容，避免老客户端被迫升级。
        if ("public".equals(space.getSpaceType())) {
            space.setAllowPublicBrowse(request.getAllowPublicBrowse() == null || request.getAllowPublicBrowse());
            space.setAllowPublicDownload(request.getAllowPublicDownload() == null || request.getAllowPublicDownload());
            space.setAllowPublicUpload(Boolean.TRUE.equals(request.getAllowPublicUpload()));
            spaceService.updatePublicRepository(space.getSpaceId(), UUID.fromString(userId),
                    space.getSpaceName(), space.getSpaceDescription(), space.getAllowPublicBrowse(),
                    space.getAllowPublicDownload(), space.getAllowPublicUpload());
        }
        return new JsonResult<>(OK, space);
    }

    @GetMapping({"/spaces", "/spaces/"})
    public JsonResult<List<SpaceEntity>> listSpaces(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String type) {
        List<SpaceEntity> spaces;
        if (type != null) {
            spaces = spaceService.getUserSpacesByType(UUID.fromString(userId), type);
        } else {
            spaces = spaceService.getUserSpaces(UUID.fromString(userId));
        }
        return new JsonResult<>(OK, spaces);
    }

    @GetMapping({"/spaces/{spaceId}", "/spaces/{spaceId}/"})
    public JsonResult<SpaceEntity> getSpace(
            @PathVariable String spaceId,
            @RequestHeader("X-User-Id") String userId) {
        SpaceEntity space = spaceService.getSpaceById(UUID.fromString(spaceId), UUID.fromString(userId));
        return new JsonResult<>(OK, space);
    }

    @PutMapping({"/spaces/{spaceId}", "/spaces/{spaceId}/"})
    public JsonResult<SpaceEntity> updateSpace(
            @PathVariable String spaceId,
            @Valid @RequestBody UpdateSpaceRequest request,
            @RequestHeader("X-User-Id") String userId) {
        SpaceEntity space = spaceService.updateSpace(
                UUID.fromString(spaceId), UUID.fromString(userId),
                request.getSpaceName(), request.getSpaceDescription(),
                request.getSpaceVisibility(), request.getJoinPolicy(), request.getSpaceQuota());
        if ("public".equals(space.getSpaceType()) &&
                (request.getAllowPublicBrowse() != null || request.getAllowPublicDownload() != null || request.getAllowPublicUpload() != null)) {
            spaceService.updatePublicRepository(space.getSpaceId(), UUID.fromString(userId),
                    null, null, request.getAllowPublicBrowse(), request.getAllowPublicDownload(), request.getAllowPublicUpload());
            space = spaceService.getSpaceById(space.getSpaceId(), UUID.fromString(userId));
        }
        return new JsonResult<>(OK, space);
    }

    @DeleteMapping({"/spaces/{spaceId}", "/spaces/{spaceId}/"})
    public JsonResult<Void> deleteSpace(
            @PathVariable String spaceId,
            @RequestHeader("X-User-Id") String userId) {
        spaceService.deleteSpace(UUID.fromString(spaceId), UUID.fromString(userId));
        return new JsonResult<>(OK);
    }

    // ============================================================
    // 成员管理
    // ============================================================

    @PostMapping({"/spaces/{spaceId}/members", "/spaces/{spaceId}/members/"})
    public JsonResult<Void> addMember(
            @PathVariable String spaceId,
            @Valid @RequestBody AddMemberRequest request,
            @RequestHeader("X-User-Id") String userId) {
        spaceService.addMember(UUID.fromString(spaceId), UUID.fromString(userId),
                UUID.fromString(request.getUserId()), request.getRole());
        return new JsonResult<>(OK);
    }

    @GetMapping({"/spaces/{spaceId}/members", "/spaces/{spaceId}/members/"})
    public JsonResult<List<SpaceMemberEntity>> listMembers(
            @PathVariable String spaceId,
            @RequestHeader("X-User-Id") String userId) {
        List<SpaceMemberEntity> members = spaceService.getMembers(
                UUID.fromString(spaceId), UUID.fromString(userId));
        return new JsonResult<>(OK, members);
    }

    @PutMapping({"/spaces/{spaceId}/members/{targetUserId}/role", "/spaces/{spaceId}/members/{targetUserId}/role/"})
    public JsonResult<Void> updateMemberRole(
            @PathVariable String spaceId,
            @PathVariable String targetUserId,
            @Valid @RequestBody UpdateMemberRoleRequest request,
            @RequestHeader("X-User-Id") String userId) {
        spaceService.updateMemberRole(UUID.fromString(spaceId), UUID.fromString(userId),
                UUID.fromString(targetUserId), request.getRole());
        return new JsonResult<>(OK);
    }

    @DeleteMapping({"/spaces/{spaceId}/members/{targetUserId}", "/spaces/{spaceId}/members/{targetUserId}/"})
    public JsonResult<Void> removeMember(
            @PathVariable String spaceId,
            @PathVariable String targetUserId,
            @RequestHeader("X-User-Id") String userId) {
        spaceService.removeMember(UUID.fromString(spaceId), UUID.fromString(userId),
                UUID.fromString(targetUserId));
        return new JsonResult<>(OK);
    }

    // ============================================================
    // 权限管理
    // ============================================================

    @PutMapping({"/spaces/{spaceId}/permissions/{targetUserId}", "/spaces/{spaceId}/permissions/{targetUserId}/"})
    public JsonResult<Void> updatePermission(
            @PathVariable String spaceId,
            @PathVariable String targetUserId,
            @Valid @RequestBody UpdatePermissionRequest request,
            @RequestHeader("X-User-Id") String userId) {
        spaceService.updatePermission(UUID.fromString(spaceId), UUID.fromString(userId),
                UUID.fromString(targetUserId), request);
        return new JsonResult<>(OK);
    }

    @GetMapping({"/spaces/{spaceId}/permissions/{targetUserId}", "/spaces/{spaceId}/permissions/{targetUserId}/"})
    public JsonResult<SpacePermissionEntity> getPermission(
            @PathVariable String spaceId,
            @PathVariable String targetUserId,
            @RequestHeader("X-User-Id") String userId) {
        SpacePermissionEntity perm = spaceService.getPermission(
                UUID.fromString(spaceId), UUID.fromString(targetUserId));
        return new JsonResult<>(OK, perm);
    }

    // ============================================================
    // 加入申请
    // ============================================================

    @PostMapping({"/spaces/{spaceId}/join-requests", "/spaces/{spaceId}/join-requests/"})
    public JsonResult<Void> requestJoin(
            @PathVariable String spaceId,
            @RequestParam(required = false) String message,
            @RequestHeader("X-User-Id") String userId) {
        spaceService.requestJoin(UUID.fromString(spaceId), UUID.fromString(userId), message);
        return new JsonResult<>(OK);
    }

    @GetMapping({"/spaces/{spaceId}/join-requests", "/spaces/{spaceId}/join-requests/"})
    public JsonResult<List<SpaceJoinRequestEntity>> listJoinRequests(
            @PathVariable String spaceId,
            @RequestParam(required = false) String status,
            @RequestHeader("X-User-Id") String userId) {
        List<SpaceJoinRequestEntity> requests = spaceService.getJoinRequests(
                UUID.fromString(spaceId), UUID.fromString(userId), status);
        return new JsonResult<>(OK, requests);
    }

    @PutMapping({"/spaces/{spaceId}/join-requests/{reqUserId}", "/spaces/{spaceId}/join-requests/{reqUserId}/"})
    public JsonResult<Void> reviewJoinRequest(
            @PathVariable String spaceId,
            @PathVariable String reqUserId,
            @RequestParam String action,
            @RequestHeader("X-User-Id") String userId) {
        spaceService.reviewJoinRequest(UUID.fromString(spaceId), UUID.fromString(reqUserId),
                UUID.fromString(userId), action);
        return new JsonResult<>(OK);
    }

    // ============================================================
    // 可见性管理
    // ============================================================

    @PutMapping({"/spaces/{spaceId}/visibility-list", "/spaces/{spaceId}/visibility-list/"})
    public JsonResult<Void> updateVisibilityList(
            @PathVariable String spaceId,
            @RequestParam String listType,
            @RequestBody List<String> userIds,
            @RequestHeader("X-User-Id") String userId) {
        List<UUID> uuids = userIds.stream().map(UUID::fromString).collect(Collectors.toList());
        spaceService.updateVisibilityList(UUID.fromString(spaceId), UUID.fromString(userId), uuids, listType);
        return new JsonResult<>(OK);
    }

    @GetMapping({"/spaces/{spaceId}/visibility-list", "/spaces/{spaceId}/visibility-list/"})
    public JsonResult<List<SpaceVisibilityEntity>> getVisibilityList(
            @PathVariable String spaceId,
            @RequestHeader("X-User-Id") String userId) {
        List<SpaceVisibilityEntity> list = spaceService.getVisibilityList(
                UUID.fromString(spaceId), UUID.fromString(userId));
        return new JsonResult<>(OK, list);
    }

    // ============================================================
    // 公共空间发现
    // ============================================================

    @GetMapping({"/spaces/public/discover", "/spaces/public/discover/"})
    public JsonResult<List<SpaceEntity>> discoverPublicSpaces(
            @RequestParam(required = false) String keyword,
            @RequestHeader("X-User-Id") String userId) {
        // 旧发现接口保留路径以兼容客户端，但公开仓库发现统一要求登录，避免匿名枚举仓库元数据。
        List<SpaceEntity> spaces = spaceService.discoverPublicSpaces(keyword);
        return new JsonResult<>(OK, spaces);
    }

    @GetMapping({"/spaces/public/by-name/{spaceName}", "/spaces/public/by-name/{spaceName}/"})
    public JsonResult<SpaceEntity> getPublicSpaceByName(
            @PathVariable String spaceName,
            @RequestHeader("X-User-Id") String userId) {
        // 兼容旧路径但沿用公开浏览开关；匿名访问请使用分享链接，而非仓库入口。
        SpaceEntity space = spaceService.getPublicSpaceByName(spaceName);
        return new JsonResult<>(OK, space);
    }

    // ============================================================
    // 当前空间切换
    // ============================================================

    @GetMapping({"/spaces/current", "/spaces/current/"})
    public JsonResult<String> getCurrentSpace(
            @RequestHeader("X-User-Id") String userId) {
        String spaceId = spaceService.getCurrentSpaceId(UUID.fromString(userId));
        return new JsonResult<>(OK, spaceId);
    }

    @PutMapping({"/spaces/current/{spaceId}", "/spaces/current/{spaceId}/"})
    public JsonResult<Void> setCurrentSpace(
            @PathVariable String spaceId,
            @RequestHeader("X-User-Id") String userId) {
        spaceService.setCurrentSpaceId(UUID.fromString(userId), UUID.fromString(spaceId));
        return new JsonResult<>(OK);
    }
}
