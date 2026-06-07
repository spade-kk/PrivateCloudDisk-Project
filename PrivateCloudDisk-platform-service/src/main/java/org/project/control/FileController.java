package org.project.control;

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

@RestController
@Validated
@RequestMapping("/business")
public class FileController extends BaseController {
    @Autowired
    private FileService fileService;

    @GetMapping({"/nodes/{node_id}/files/{file_name}", "/nodes/{node_id}/files/{file_name}/"})
    public JsonResult<FileVO> queryFileEntity(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "node_id必须是有效的UUID格式")
            @PathVariable String node_id,
            @PathVariable String file_name,
            @RequestHeader("X-User-Id") String user_id ) {
        FileEntity fileData = fileService.queryUserFileByNodeIdAndName(node_id, file_name, user_id);
        return new JsonResult<>(OK, VoMapper.toFileVO(fileData));
    }

    @GetMapping({"/files/{file_id}", "/files/{file_id}/"})
    public JsonResult<FileVO> queryFileEntityByFileId(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "file_id必须是有效的UUID格式")
            @PathVariable String file_id,
            @RequestHeader("X-User-Id") String user_id ) {
        FileEntity fileData = fileService.queryUserFileById(file_id, user_id);
        return new JsonResult<>(OK, VoMapper.toFileVO(fileData));
    }

    @PatchMapping({"/files/{file_id}/name", "/files/{file_id}/name/"})
    public JsonResult<Void> updateFileName(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "file_id必须是有效的UUID格式")
            @PathVariable String file_id,
            @Valid @RequestBody RenameFileRequest request,
            @RequestHeader("X-User-Id") String user_id ) {
        fileService.updateFileName(file_id, request.getFile_new_name(), user_id);
        return new JsonResult<>(OK);
    }

    @PatchMapping({"/files/{file_id}/position", "/files/{file_id}/position/"})
    public JsonResult<Void> moveFileByFileId(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "file_id必须是有效的UUID格式")
            @PathVariable String file_id,
            @Valid @RequestBody MoveFileRequest request,
            @RequestHeader("X-User-Id") String user_id ) {
        fileService.moveFileByFileId(file_id, request.getTarget_node_id(), user_id);
        return new JsonResult<>(OK);
    }

    @DeleteMapping({"/files/{file_id}", "/files/{file_id}/"})
    public JsonResult<Void> deleteFileByFileId(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "file_id必须是有效的UUID格式")
            @PathVariable String file_id,
            @RequestHeader("X-User-Id") String user_id ) {
        fileService.deleteFileByFileId(file_id, user_id);
        return new JsonResult<>(OK);
    }
}
