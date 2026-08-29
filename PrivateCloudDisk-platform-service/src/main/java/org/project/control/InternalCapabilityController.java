package org.project.control;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.project.config.RabbitMQConifgure;
import org.project.context.SpaceContextHolder;
import org.project.control.result.JsonResult;
import org.project.mapper.UserMapper;
import org.project.model.dto.ShareCreateRequest;
import org.project.model.dto.ShareResourceItem;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.NodeEntity;
import org.project.model.entity.SpaceEntity;
import org.project.model.entity.SpaceMemberEntity;
import org.project.model.entity.ShareLinkEntity;
import org.project.model.vo.FileSearchVO;
import org.project.model.vo.PageResultVO;
import org.project.model.vo.TagVO;
import org.project.model.vo.VoMapper;
import org.project.service.*;
import org.project.model.entity.UserEntity;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 能力中心数据面内部接口（需求四 4.x / 五 5.19-5.20 / 六 6.13-6.15）。
 *
 * <p>仅允许内部服务网络调用（Gateway 不透出 /business/internal/*），调用方（CloudFlow
 * Runtime 能力中心）必须透传用户/空间上下文（uid / X-Space-Id）；本控制器在真实服务层
 * 重新解析空间上下文并复核资源权限（4.15 防横向越权），返回统一 JsonResult 信封。</p>
 */
@RestController
@RequestMapping("/business/internal/capability")
@Validated
@RequiredArgsConstructor
public class InternalCapabilityController extends BaseController {
    private static final String UUID_REGEX =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final SpacePermissionService spacePermissionService;
    private final SpaceService spaceService;
    private final DirectoryTreeService directoryTreeService;
    private final FileSearchService searchService;
    private final FileService fileService;
    private final TagService tagService;
    private final UserService userService;
    private final UserMapper userMapper;
    private final ShareService shareService;
    private final RabbitTemplate rabbitTemplate;

    // ---------------------------------------------------------------- 文件元数据
    @GetMapping("/files/{file_id}/metadata")
    public JsonResult<Map<String, Object>> fileMetadata(
            @Pattern(regexp = UUID_REGEX, message = "file_id 必须是有效的UUID格式") @PathVariable String file_id,
            @RequestParam @Pattern(regexp = UUID_REGEX, message = "uid 必须是有效的UUID格式") String uid,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId
    ) {
        UUID userId = UUID.fromString(uid);
        SpaceContextHolder.SpaceContext context = spacePermissionService.resolveContext(userId, spaceId);
        SpaceContextHolder.set(context);
        try {
            spacePermissionService.requireOperation(context, SpaceOperation.READ);
            spacePermissionService.requireFileInCurrentSpace(UUID.fromString(file_id));
            FileEntity file = fileService.queryUserFileById(UUID.fromString(file_id), userId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", file.getId().toString());
            data.put("name", file.getName());
            data.put("type", file.getType());
            data.put("size", file.getSize());
            data.put("uploaded_time", file.getUploaded_time());
            data.put("node_id", file.getNode_id() == null ? null : file.getNode_id().toString());
            data.put("space_id", file.getSpace_id() == null ? null : file.getSpace_id().toString());
            return new JsonResult<>(OK, data);
        } finally {
            SpaceContextHolder.clear();
        }
    }

    // ---------------------------------------------------------------- 文件列表
    @GetMapping("/files/list")
    public JsonResult<Map<String, Object>> fileList(
            @RequestParam @Pattern(regexp = UUID_REGEX, message = "uid 必须是有效的UUID格式") String uid,
            @RequestParam(required = false) String space_id,
            @RequestParam(required = false) @Pattern(regexp = UUID_REGEX, message = "parent_id 必须是有效的UUID格式") String parent_id,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) Integer size
    ) {
        UUID userId = UUID.fromString(uid);
        SpaceContextHolder.SpaceContext context = spacePermissionService.resolveContext(userId, space_id);
        SpaceContextHolder.set(context);
        try {
            spacePermissionService.requireOperation(context, SpaceOperation.READ);
            UUID folderId = resolveFolderId(userId, parent_id, space_id);
            spacePermissionService.requireNodeInCurrentSpace(folderId);
            PageResultVO<NodeEntity> result = directoryTreeService.findUserNodesByNodeIdPaged(
                    folderId.toString(), keyword, null, "name", "asc", page, size, userId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", result.getTotal());
            data.put("page", result.getPage());
            data.put("page_size", result.getPageSize());
            data.put("items", VoMapper.toNodeVOList(result.getItems()));
            return new JsonResult<>(OK, data);
        } finally {
            SpaceContextHolder.clear();
        }
    }

    // ---------------------------------------------------------------- 文件搜索
    @GetMapping("/files/search")
    public JsonResult<FileSearchVO> fileSearch(
            @RequestParam @Pattern(regexp = UUID_REGEX, message = "uid 必须是有效的UUID格式") String uid,
            @RequestParam(required = false) String space_id,
            @RequestParam @NotBlank(message = "keyword 不能为空") String keyword,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size
    ) {
        UUID userId = UUID.fromString(uid);
        SpaceContextHolder.SpaceContext context = spacePermissionService.resolveContext(userId, space_id);
        SpaceContextHolder.set(context);
        try {
            spacePermissionService.requireOperation(context, SpaceOperation.READ);
            return new JsonResult<>(OK, searchService.search(
                    keyword, page, size, "name", true, uid, Map.of(), List.of(), null));
        } finally {
            SpaceContextHolder.clear();
        }
    }

    // ---------------------------------------------------------------- 文件标签
    @GetMapping("/files/{file_id}/tags")
    public JsonResult<List<TagVO>> fileTags(
            @Pattern(regexp = UUID_REGEX, message = "file_id 必须是有效的UUID格式") @PathVariable String file_id,
            @RequestParam @Pattern(regexp = UUID_REGEX, message = "uid 必须是有效的UUID格式") String uid,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId
    ) {
        UUID userId = UUID.fromString(uid);
        SpaceContextHolder.SpaceContext context = spacePermissionService.resolveContext(userId, spaceId);
        SpaceContextHolder.set(context);
        try {
            spacePermissionService.requireOperation(context, SpaceOperation.READ);
            spacePermissionService.requireFileInCurrentSpace(UUID.fromString(file_id));
            return new JsonResult<>(OK, tagService.getFileTags(userId, file_id, "file"));
        } finally {
            SpaceContextHolder.clear();
        }
    }

    // ---------------------------------------------------------------- 触发安全扫描（异步）
    @PostMapping("/files/{file_id}/scan")
    public JsonResult<Map<String, Object>> triggerScan(
            @Pattern(regexp = UUID_REGEX, message = "file_id 必须是有效的UUID格式") @PathVariable String file_id,
            @RequestParam @Pattern(regexp = UUID_REGEX, message = "uid 必须是有效的UUID格式") String uid,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        UUID userId = UUID.fromString(uid);
        SpaceContextHolder.SpaceContext context = spacePermissionService.resolveContext(userId, spaceId);
        SpaceContextHolder.set(context);
        try {
            spacePermissionService.requireOperation(context, SpaceOperation.READ);
            spacePermissionService.requireFileInCurrentSpace(UUID.fromString(file_id));
            String taskId = UUID.randomUUID().toString();
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("task_id", taskId);
            event.put("file_id", file_id);
            event.put("user_id", uid);
            event.put("space_id", context.spaceId() == null ? null : context.spaceId().toString());
            event.put("reason", body == null ? null : body.get("reason"));
            rabbitTemplate.convertAndSend(
                    RabbitMQConifgure.FILE_EVENT_EXCHANGE,
                    RabbitMQConifgure.ROUTING_FILE_SCAN_REQUESTED,
                    event
            );
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("task_id", taskId);
            data.put("status", "queued");
            data.put("file_id", file_id);
            return new JsonResult<>(OK, data);
        } finally {
            SpaceContextHolder.clear();
        }
    }

    // ---------------------------------------------------------------- 空间信息
    @GetMapping("/spaces/{space_id}/info")
    public JsonResult<Map<String, Object>> spaceInfo(
            @PathVariable String space_id,
            @RequestParam @Pattern(regexp = UUID_REGEX, message = "uid 必须是有效的UUID格式") String uid
    ) {
        UUID userId = UUID.fromString(uid);
        SpaceContextHolder.SpaceContext context = spacePermissionService.resolveContext(userId, space_id);
        SpaceContextHolder.set(context);
        try {
            spacePermissionService.requireOperation(context, SpaceOperation.READ);
            SpaceEntity space = spaceService.getSpaceById(UUID.fromString(space_id), userId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("space_id", space.getSpaceId() == null ? null : space.getSpaceId().toString());
            data.put("space_name", space.getSpaceName());
            data.put("space_type", space.getSpaceType());
            data.put("resource_type", space.getResourceType());
            data.put("space_owner_id", space.getSpaceOwnerId() == null ? null : space.getSpaceOwnerId().toString());
            data.put("space_quota", space.getSpaceQuota());
            data.put("space_used", space.getSpaceUsed());
            data.put("space_file_count", space.getSpaceFileCount());
            data.put("space_visibility", space.getSpaceVisibility());
            data.put("join_policy", space.getJoinPolicy());
            data.put("space_status", space.getSpaceStatus());
            data.put("space_created_at", space.getSpaceCreatedAt());
            data.put("space_updated_at", space.getSpaceUpdatedAt());
            return new JsonResult<>(OK, data);
        } finally {
            SpaceContextHolder.clear();
        }
    }

    // ---------------------------------------------------------------- 空间成员列表
    @GetMapping("/spaces/{space_id}/members")
    public JsonResult<Map<String, Object>> spaceMembers(
            @PathVariable String space_id,
            @RequestParam @Pattern(regexp = UUID_REGEX, message = "uid 必须是有效的UUID格式") String uid,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) Integer size
    ) {
        UUID userId = UUID.fromString(uid);
        SpaceContextHolder.SpaceContext context = spacePermissionService.resolveContext(userId, space_id);
        SpaceContextHolder.set(context);
        try {
            spacePermissionService.requireOperation(context, SpaceOperation.READ);
            List<SpaceMemberEntity> members = spaceService.getMembers(UUID.fromString(space_id), userId, keyword);
            List<Map<String, Object>> items = new ArrayList<>();
            int from = Math.min((page - 1) * size, members.size());
            int to = Math.min(from + size, members.size());
            for (SpaceMemberEntity member : members.subList(from, to)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("user_id", member.getUserId() == null ? null : member.getUserId().toString());
                item.put("role", member.getRole());
                item.put("joined_at", member.getJoinedAt());
                items.add(item);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", members.size());
            data.put("page", page);
            data.put("page_size", size);
            data.put("items", items);
            return new JsonResult<>(OK, data);
        } finally {
            SpaceContextHolder.clear();
        }
    }

    // ---------------------------------------------------------------- 用户信息（脱敏）
    @GetMapping("/users/{user_id}/info")
    public JsonResult<Map<String, Object>> userInfo(
            @PathVariable String user_id,
            @RequestParam @Pattern(regexp = UUID_REGEX, message = "uid 必须是有效的UUID格式") String uid
    ) {
        UUID userId = UUID.fromString(uid);
        SpaceContextHolder.SpaceContext context = spacePermissionService.resolveContext(userId, null);
        SpaceContextHolder.set(context);
        try {
            spacePermissionService.requireOperation(context, SpaceOperation.READ);
            UserEntity entity = userMapper.findUserById(UUID.fromString(user_id));
            Map<String, Object> data = new LinkedHashMap<>();
            if (entity != null) {
                data.put("user_id", entity.getId() == null ? null : entity.getId().toString());
                data.put("username", entity.getName());
                data.put("account", entity.getAccount());
                data.put("avatar_path", entity.getImage_path());
            }
            return new JsonResult<>(OK, data);
        } finally {
            SpaceContextHolder.clear();
        }
    }

    // ---------------------------------------------------------------- 创建分享
    @PostMapping("/shares")
    public JsonResult<Map<String, Object>> createShare(
            @RequestParam @Pattern(regexp = UUID_REGEX, message = "uid 必须是有效的UUID格式") String uid,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @Valid @RequestBody ShareCreateRequest request
    ) {
        UUID userId = UUID.fromString(uid);
        SpaceContextHolder.SpaceContext context = spacePermissionService.resolveContext(userId, spaceId);
        SpaceContextHolder.set(context);
        try {
            spacePermissionService.requireOperation(context, SpaceOperation.SHARE);
            for (ShareResourceItem resource : request.getResources()) {
                if ("file".equals(resource.getType()) && resource.getId() != null) {
                    spacePermissionService.requireFileInCurrentSpace(UUID.fromString(resource.getId()));
                }
            }
            List<ShareService.ResourceItem> resources = request.getResources().stream()
                    .map(r -> ShareService.ResourceItem.of(r.getType(), r.getId())).toList();
            ShareLinkEntity entity = shareService.createShare(
                    uid,
                    request.getShare_name(),
                    request.getShare_description(),
                    resources,
                    request.getPassword(),
                    request.getExpires_in_days(),
                    request.getAllow_download());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("share_id", entity.getShare_id() == null ? null : entity.getShare_id().toString());
            data.put("share_token", entity.getShare_token());
            data.put("share_name", entity.getShare_name());
            data.put("share_has_password", entity.getShare_has_password());
            data.put("share_expires_at", entity.getShare_expires_at());
            data.put("resource_count", resources.size());
            return new JsonResult<>(OK, data);
        } finally {
            SpaceContextHolder.clear();
        }
    }

    private UUID resolveFolderId(UUID userId, String parentId, String spaceId) {
        if (parentId != null && !parentId.isBlank()) {
            return UUID.fromString(parentId);
        }
        FolderNodeEntity root = userService.findRootFolderNodeByUserId(userId);
        if (root == null || root.getNode_id() == null) {
            throw new IllegalArgumentException("无法解析用户的根目录");
        }
        return root.getNode_id();
    }
}
