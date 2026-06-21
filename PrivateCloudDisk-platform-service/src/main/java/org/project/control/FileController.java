package org.project.control;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.project.control.result.JsonResult;
import org.project.model.dto.MoveFileRequest;
import org.project.model.dto.RenameFileRequest;
import org.project.model.entity.FileEntity;
import org.project.model.vo.FileVO;
import org.project.model.vo.VoMapper;
import org.project.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/business")
public class FileController extends BaseController {
    @Autowired
    private FileService fileService;

    @GetMapping({"/files/{file_id}", "/files/{file_id}/"})
    public JsonResult<FileVO> queryFileEntityByFileId(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "file_id必须是有效的UUID格式")
            @PathVariable String file_id,
            @RequestHeader("X-User-Id") String user_id ) {
        FileEntity fileData = fileService.queryUserFileById(UUID.fromString(file_id), UUID.fromString(user_id));
        return new JsonResult<>(OK, VoMapper.toFileVO(fileData));
    }

    @PatchMapping({"/files/{file_id}/name", "/files/{file_id}/name/"})
    public JsonResult<Void> updateFileName(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "file_id必须是有效的UUID格式")
            @PathVariable String file_id,
            @Valid @RequestBody RenameFileRequest request,
            @RequestHeader("X-User-Id") String user_id ) {
        fileService.updateFileName(UUID.fromString(file_id), request.getFile_new_name(), UUID.fromString(user_id));
        return new JsonResult<>(OK);
    }

    @PatchMapping({"/files/{file_id}/position", "/files/{file_id}/position/"})
    public JsonResult<Void> moveFileByFileId(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "file_id必须是有效的UUID格式")
            @PathVariable String file_id,
            @Valid @RequestBody MoveFileRequest request,
            @RequestHeader("X-User-Id") String user_id ) {
        fileService.moveFileByFileId(UUID.fromString(file_id), UUID.fromString(request.getTarget_node_id()), UUID.fromString(user_id));
        return new JsonResult<>(OK);
    }

    @DeleteMapping({"/files/{file_id}", "/files/{file_id}/"})
    @SentinelResource(value = "deleteFiles",
            blockHandler = "deleteFilesBlockHandler",
            fallback = "deleteFilesFallback")
    public JsonResult<Void> deleteFileByFileId(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "file_id必须是有效的UUID格式")
            @PathVariable String file_id,
            @RequestHeader("X-User-Id") String user_id ) {
        fileService.deleteFileByFileId(UUID.fromString(file_id), UUID.fromString(user_id));
        return new JsonResult<>(OK);
    }

    /**
     * Sentinel 限流/熔断 BlockHandler（仅 BlockException 触发）。
     */
    public JsonResult<Void> deleteFilesBlockHandler(
            String file_id, String user_id, BlockException ex) {
        return JsonResult.error(42902, "文件删除操作过于频繁，系统限流已触发，请稍后重试");
    }

    /**
     * Sentinel 熔断降级 Fallback（业务异常触发）。
     */
    public JsonResult<Void> deleteFilesFallback(
            String file_id, String user_id, Throwable ex) {
        return JsonResult.error(50301, "文件删除服务暂时不可用，请稍后重试");
    }
}
