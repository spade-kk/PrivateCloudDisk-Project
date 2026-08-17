package org.project.control;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.project.control.result.JsonResult;
import org.project.model.vo.PublicSpaceDetailVO;
import org.project.model.vo.PublicSpaceNodeVO;
import org.project.model.vo.PublicUserProfileVO;
import org.project.service.PublicSpaceService;
import org.project.service.UploadsService;
import org.project.model.dto.CreateUploadsSessionRequest;
import org.project.model.vo.UploadSessionConcurrencyVO;
import org.project.model.vo.UploadSessionCreateVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 公开空间（仓库）接口。
 * 需求边界：所有仓库页面必须登录；与 ShareController 的匿名分享令牌完全隔离。
 */
@RestController
@Validated
@RequestMapping("/business/public-spaces")
public class PublicSpaceController extends BaseController {
    private static final String UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final PublicSpaceService publicSpaceService;
    private final UploadsService uploadsService;

    public PublicSpaceController(PublicSpaceService publicSpaceService, UploadsService uploadsService) {
        this.publicSpaceService = publicSpaceService;
        this.uploadsService = uploadsService;
    }

    @GetMapping("/{spaceId}")
    public JsonResult<PublicSpaceDetailVO> getRepository(
            @PathVariable @Pattern(regexp = UUID_REGEX) String spaceId,
            @RequestHeader("X-User-Id") String userId) {
        return new JsonResult<>(OK, publicSpaceService.getRepository(UUID.fromString(spaceId), UUID.fromString(userId)));
    }

    @GetMapping("/{spaceId}/root")
    public JsonResult<PublicSpaceNodeVO> getRoot(
            @PathVariable @Pattern(regexp = UUID_REGEX) String spaceId,
            @RequestHeader("X-User-Id") String userId) {
        return new JsonResult<>(OK, publicSpaceService.getRoot(UUID.fromString(spaceId), UUID.fromString(userId)));
    }

    @GetMapping("/{spaceId}/nodes/{nodeId}/children")
    public JsonResult<List<PublicSpaceNodeVO>> getChildren(
            @PathVariable @Pattern(regexp = UUID_REGEX) String spaceId,
            @PathVariable @Pattern(regexp = UUID_REGEX) String nodeId,
            @RequestHeader("X-User-Id") String userId) {
        return new JsonResult<>(OK, publicSpaceService.getChildren(UUID.fromString(spaceId), UUID.fromString(nodeId), UUID.fromString(userId)));
    }

    /** 返回 README 文件 ID，前端随后通过已有预览内容接口获取原文，避免泄漏物理路径。 */
    @GetMapping("/{spaceId}/readme")
    public JsonResult<String> getReadme(
            @PathVariable @Pattern(regexp = UUID_REGEX) String spaceId,
            @RequestHeader("X-User-Id") String userId) {
        return new JsonResult<>(OK, publicSpaceService.getReadme(UUID.fromString(spaceId), UUID.fromString(userId)));
    }

    @PostMapping("/{spaceId}/uploads")
    public JsonResult<UploadSessionCreateVO> createPublicUpload(
            @PathVariable @Pattern(regexp = UUID_REGEX) String spaceId,
            @Valid @RequestBody CreateUploadsSessionRequest request,
            @RequestHeader("X-User-Id") String userId,
            jakarta.servlet.http.HttpServletRequest servletRequest) {
        UUID uploadId = uploadsService.createPublicUploadsSession(UUID.fromString(spaceId), request.getTotal_chunks(),
                request.getFile_size(), request.getFile_checksum(), request.getChunks_max_size(), request.getFile_name(),
                request.getFile_type(), UUID.fromString(userId), UUID.fromString(request.getNode_id()),
                org.project.util.ClientIpUtil.resolveClientIp(servletRequest));
        UploadSessionConcurrencyVO concurrency = uploadsService.queryUploadConcurrency(UUID.fromString(userId));
        UploadSessionCreateVO response = new UploadSessionCreateVO();
        response.setUploads_id(uploadId.toString());
        response.setMax_concurrent_sessions(concurrency.getMax_concurrent_sessions());
        response.setActive_session_count(concurrency.getActive_session_count());
        response.setRemaining_concurrent_sessions(concurrency.getRemaining_concurrent_sessions());
        return new JsonResult<>(OK, response);
    }

    @PatchMapping("/{spaceId}")
    public JsonResult<PublicSpaceDetailVO> updateRepository(
            @PathVariable @Pattern(regexp = UUID_REGEX) String spaceId,
            @Valid @RequestBody UpdatePublicRepositoryRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return new JsonResult<>(OK, publicSpaceService.updateRepository(UUID.fromString(spaceId), UUID.fromString(userId),
                request.getName(), request.getDescription(), request.getAllowPublicBrowse(),
                request.getAllowPublicDownload(), request.getAllowPublicUpload()));
    }

    @GetMapping("/users/{username}")
    public JsonResult<PublicUserProfileVO> getUserProfile(
            @PathVariable @Size(min = 1, max = 100) String username,
            @RequestHeader("X-User-Id") String userId) {
        return new JsonResult<>(OK, publicSpaceService.getUserProfile(username, UUID.fromString(userId)));
    }

    @GetMapping("/explore")
    public JsonResult<List<PublicSpaceDetailVO>> explore(
            @RequestParam(required = false) String keyword,
            @RequestHeader("X-User-Id") String userId) {
        return new JsonResult<>(OK, publicSpaceService.explore(keyword, UUID.fromString(userId)));
    }

    @GetMapping("/search")
    public JsonResult<List<PublicSpaceDetailVO>> search(
            @RequestParam(required = false) String keyword,
            @RequestHeader("X-User-Id") String userId) {
        return new JsonResult<>(OK, publicSpaceService.explore(keyword, UUID.fromString(userId)));
    }

    @Data
    public static class UpdatePublicRepositoryRequest {
        @Size(min = 1, max = 64, message = "仓库名称长度必须为1-64个字符")
        @Pattern(regexp = "^[^\\\\/:*?\"<>|]+$", message = "仓库名称不能包含非法字符")
        private String name;
        @Size(max = 500, message = "仓库描述长度不能超过500个字符")
        private String description;
        private Boolean allowPublicBrowse;
        private Boolean allowPublicDownload;
        private Boolean allowPublicUpload;
    }
}
