package org.project.control;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.opensearch.common.recycler.Recycler;
import org.project.config.RabbitMQConifgure;
import org.project.control.result.JsonResult;
import org.project.context.SpaceContextHolder;
import org.project.model.entity.FileEntity;
import org.project.model.entity.UploadsChunkEntity;
import org.project.model.entity.UploadsSessionEntity;
import org.project.model.dto.InternalFileActivateRequest;
import org.project.model.vo.InternalFileMetadataVO;
import org.project.model.vo.UploadsChunkInternalVO;
import org.project.model.vo.UploadsSessionInternalVO;
import org.project.model.vo.VoMapper;
import org.project.service.FileService;
import org.project.service.UploadsService;
import org.project.service.UserService;
import org.project.service.SpaceOperation;
import org.project.service.SpacePermissionService;
import org.project.service.ShareService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/business/internal/storage")
public class InternalStorageController extends BaseController {
    @Autowired
    private UploadsService uploadsService;
    @Autowired
    private FileService fileService;
    @Autowired
    private UserService userService;
    @Autowired
    private SpacePermissionService spacePermissionService;
    @Autowired
    private ShareService shareService;

    @PostMapping("uploads/{uploads_id}/chunks/{chunk_index}/complete")
    public JsonResult<Void> chunk_complete(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uploads_id必须是有效的UUID格式")
            @PathVariable String uploads_id,
            @Pattern(regexp = "^[0-9]+$", message = "chunk_index必须是一个非负的正整数且从1开始")
            @PathVariable String chunk_index,
            @RequestParam String storage_path ) {
        uploadsService.completeChunkUpload(UUID.fromString(uploads_id), Integer.parseInt(chunk_index), storage_path);
        return new JsonResult<>(OK);
    }

    @GetMapping({"uploads/{uploads_id}", "uploads/{uploads_id}/"})
    public JsonResult<UploadsSessionInternalVO> uploads_query(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uploads_id必须是有效的UUID格式")
            @PathVariable String uploads_id ) {
        UploadsSessionEntity uploadsSessionData = uploadsService.queryUploadsSessionById(UUID.fromString(uploads_id));
        return new JsonResult<>(OK, VoMapper.toUploadsSessionInternalVO(uploadsSessionData));
    }


    @GetMapping({"uploads/{uploads_id}/chunks/{chunk_index}", "uploads/{uploads_id}/chunks/{chunk_index}/"})
    public JsonResult<UploadsChunkInternalVO> chunk_query(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uploads_id必须是有效的UUID格式")
            @PathVariable String uploads_id,
            @NotNull(message = "chunk_index 不能为空")
            @Min(value = 0, message = "chunk_index 不能为负数")
            @PathVariable Integer chunk_index ) {
        UploadsChunkEntity chunkData = uploadsService.queryChunkByUploadsIdAndChunkIndex(UUID.fromString(uploads_id), chunk_index);
        return new JsonResult<>(OK, VoMapper.toUploadsChunkInternalVO(chunkData));
    }

    /**
     * REQ-UPLOAD-SESSION-STATE-2026-07：主路径统一为 /merge；保留旧 /merging 路由别名仅用于
     * 滚动部署期间的请求兼容，两个路径都执行同一套新逻辑，绝不再写入 merging 状态。
     */
    @PostMapping({ "uploads/{uploads_id}/merge", "uploads/{uploads_id}/merging"})
    public JsonResult<String> uploads_merge(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uploads_id必须是有效的UUID格式")
            @PathVariable String uploads_id ) {
        UUID file_id = uploadsService.uploadsMerging(UUID.fromString(uploads_id));
        return new JsonResult<>(OK, file_id.toString());
    }

    @PostMapping({"files", "files/", "file/complete"})
    public JsonResult<Void> file_complete(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uploads_id必须是有效的UUID格式")
            @RequestParam String uploads_id,
            @RequestParam String file_storage_path,
            @RequestParam String file_id,
            @RequestParam String uid ) {
        uploadsService.completeUploads(UUID.fromString(uploads_id), UUID.fromString(file_id), file_storage_path, UUID.fromString(uid));
        return new JsonResult<>(OK);
    }

    @GetMapping({"files/{file_id}"})
    public JsonResult<InternalFileMetadataVO> file_metadata_query(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "file_id必须是有效的UUID格式")
            @PathVariable String file_id,
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uid必须是有效的UUID格式")
            @RequestParam String uid,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestHeader(value = "X-Space-Operation", defaultValue = "READ") String spaceOperation) {
        /*
         * 需求三-4/五-8：跨微服务校验必须在实际处理文件的服务内部执行。
         * 原行为只按 uid 查询；新行为由内部接口重新解析空间并校验 READ + 资源归属。
         */
        UUID userId = UUID.fromString(uid);
        SpaceContextHolder.SpaceContext context =
                spacePermissionService.resolveContext(userId, spaceId);
        SpaceContextHolder.set(context);
        try {
            // 需求三-3/五-3/8：下载和预览复用元数据接口，但权限动作由存储服务明确声明。
            SpaceOperation operation = "DOWNLOAD".equalsIgnoreCase(spaceOperation)
                    ? SpaceOperation.DOWNLOAD
                    : SpaceOperation.READ;
            spacePermissionService.requireOperation(context, operation);
            spacePermissionService.requireFileInCurrentSpace(UUID.fromString(file_id));
            FileEntity fileData = fileService.queryUserFileById(UUID.fromString(file_id), userId);
            return new JsonResult<>(OK, VoMapper.toInternalFileMetadataVO(fileData));
        } finally {
            SpaceContextHolder.clear();
        }
    }

    /**
     * 分享资源内部解析接口。
     *
     * 需求二-1/2、三-4：仅供文件存储服务私网调用；客户端不得直接得到真实 file_id
     * 或 storage_path。分享访问令牌由主业务服务验证，文件服务据此签发自己的短期 Grant。
     */
    @GetMapping("shares/{share_token}/resources/{share_resource_id}")
    public JsonResult<Map<String, Object>> resolveShareResource(
            @PathVariable String share_token,
            @PathVariable String share_resource_id,
            @RequestParam String access_token,
            @RequestParam(defaultValue = "READ") String operation) {
        ShareService.ShareResourceAccess resolved =
                shareService.resolveShareResourceForStorage(
                        share_token, share_resource_id, access_token, operation);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("file_id", resolved.fileId());
        data.put("space_id", resolved.spaceId());
        data.put("file_name", resolved.fileName());
        data.put("file_size", resolved.fileSize());
        data.put("file_type", resolved.fileType());
        data.put("storage_path", resolved.storagePath());
        data.put("share_resource_id", resolved.shareResourceId());
        data.put("download_allowed", resolved.downloadAllowed());
        return new JsonResult<>(OK, data);
    }

    @PostMapping("files/{file_id}/activate")
    public JsonResult<Void> file_status_to_active(
            @PathVariable String file_id,
            @RequestParam String uid,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @Valid @org.springframework.web.bind.annotation.RequestBody(required = false)
            InternalFileActivateRequest body) {
        UUID userId = UUID.fromString(uid);
        SpaceContextHolder.SpaceContext context =
                spacePermissionService.resolveContext(userId, spaceId);
        SpaceContextHolder.set(context);
        try {
            spacePermissionService.requireFileInCurrentSpace(UUID.fromString(file_id));
            if (body == null) {
                uploadsService.activateFileStatus(UUID.fromString(file_id), userId);
            } else {
                uploadsService.activateFileStatusWithFinalContent(
                        UUID.fromString(file_id),
                        userId,
                        body.storagePath(),
                        body.checksum(),
                        body.size()
                );
            }
            return new JsonResult<>(OK);
        } finally {
            SpaceContextHolder.clear();
        }
    }

    @PatchMapping("files/{file_id}/status")
    public JsonResult<Void> update_file_state(@PathVariable String file_id, @RequestParam String status, @RequestParam String uid) {
        fileService.updateFileStatus(UUID.fromString(file_id), status, UUID.fromString(uid));
        return new JsonResult<>(OK);
    }

    @PostMapping("files/{file_id}/delete-complete")
    public JsonResult<Void> file_delete_complete(@PathVariable String file_id, @RequestParam String uid) {
        fileService.completeDeleteFileByFileId(UUID.fromString(file_id), UUID.fromString(uid));
        return new JsonResult<>(OK);
    }

    /**
     * 文件存储服务完成取消/过期物理文件删除后，同步调用此接口。
     * <p>内部删除 canceled 会话并发布配额回滚事件，不再写入 deleted 状态。
     */
    @PostMapping("uploads/{uploads_id}/delete-complete")
    public JsonResult<Void> uploads_session_delete_complete(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uploads_id必须是有效的UUID格式")
            @PathVariable String uploads_id) {
        uploadsService.markUploadSessionDeleted(UUID.fromString(uploads_id));
        return new JsonResult<>(OK);
    }

    /**
     * 合并流水线清理完物理分块后的会话清理回调。
     * 与取消清理分离，避免成功合并误触发配额回滚事件。
     */
    @PostMapping("uploads/{uploads_id}/merge-cleanup")
    public JsonResult<Void> uploads_session_merge_cleanup(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uploads_id必须是有效的UUID格式")
            @PathVariable String uploads_id) {
        uploadsService.deleteUploadsSessionAfterMerge(UUID.fromString(uploads_id));
        return new JsonResult<>(OK);
    }

}
