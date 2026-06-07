package org.project.control;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.project.config.RabbitMQConifgure;
import org.project.control.result.JsonResult;
import org.project.model.entity.FileEntity;
import org.project.model.entity.UploadsChunkEntity;
import org.project.model.entity.UploadsSessionEntity;
import org.project.model.vo.InternalFileMetadataVO;
import org.project.model.vo.UploadsChunkInternalVO;
import org.project.model.vo.UploadsSessionInternalVO;
import org.project.model.vo.VoMapper;
import org.project.service.FileService;
import org.project.service.UploadsService;
import org.project.service.UserService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
    private RabbitTemplate rabbitTemplate;

    @GetMapping("test")
    public JsonResult<Void> test() {
        rabbitTemplate.convertAndSend(
                RabbitMQConifgure.REGISTER_EXCHANGE,
                RabbitMQConifgure.REGISTER_ROUTING_KEY,
                "test");

        return new JsonResult<>(OK);
    }

    @GetMapping("test2")
    public JsonResult<Void> test2() {
        userService.findRootFolderNodeByUserId("415d3064-a465-4813-8f42-d6f1aa9b87c0");
        return new JsonResult<>(OK);
    }

    @PostMapping("uploads/{uploads_id}/chunks/{chunk_index}/complete")
    public JsonResult<Void> chunk_complete(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uploads_id必须是有效的UUID格式")
            @PathVariable String uploads_id,
            @Pattern(regexp = "^[0-9]+$", message = "chunk_index必须是一个非负的正整数且从1开始")
            @PathVariable String chunk_index,
            @RequestParam String storage_path ) {
        uploadsService.completeChunkUpload(uploads_id, Integer.parseInt(chunk_index), storage_path);
        return new JsonResult<>(OK);
    }

    @GetMapping({"uploads/{uploads_id}", "uploads/{uploads_id}/"})
    public JsonResult<UploadsSessionInternalVO> uploads_query(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uploads_id必须是有效的UUID格式")
            @PathVariable String uploads_id ) {
        UploadsSessionEntity uploadsSessionData = uploadsService.queryUploadsSessionById(uploads_id);
        return new JsonResult<>(OK, VoMapper.toUploadsSessionInternalVO(uploadsSessionData));
    }

    @PostMapping("uploads/{uploads_id}/query")
    public JsonResult<UploadsSessionInternalVO> uploads_query_legacy(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uploads_id必须是有效的UUID格式")
            @PathVariable String uploads_id) {
        return uploads_query(uploads_id);
    }

    @GetMapping({"uploads/{uploads_id}/chunks/{chunk_index}", "uploads/{uploads_id}/chunks/{chunk_index}/"})
    public JsonResult<UploadsChunkInternalVO> chunk_query(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uploads_id必须是有效的UUID格式")
            @PathVariable String uploads_id,
            @NotNull(message = "chunk_index 不能为空")
            @Min(value = 0, message = "chunk_index 不能为负数")
            @PathVariable Integer chunk_index ) {
        UploadsChunkEntity chunkData = uploadsService.queryChunkByUploadsIdAndChunkIndex(uploads_id, chunk_index);
        return new JsonResult<>(OK, VoMapper.toUploadsChunkInternalVO(chunkData));
    }

    @PostMapping("uploads/{uploads_id}/chunks/{chunk_index}/query")
    public JsonResult<UploadsChunkInternalVO> chunk_query_legacy(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uploads_id必须是有效的UUID格式")
            @PathVariable String uploads_id,
            @NotNull(message = "chunk_index 不能为空")
            @Min(value = 0, message = "chunk_index 不能为负数")
            @PathVariable Integer chunk_index) {
        return chunk_query(uploads_id, chunk_index);
    }

    @PostMapping({"uploads/{uploads_id}/merge", "uploads/{uploads_id}/merging"})
    public JsonResult<Void> uploads_merging(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uploads_id必须是有效的UUID格式")
            @PathVariable String uploads_id ) {
        uploadsService.uploadsMerging(uploads_id);
        return new JsonResult<>(OK);
    }

    @PostMapping({"files", "files/", "file/complete"})
    public JsonResult<Void> file_complete(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uploads_id必须是有效的UUID格式")
            @RequestParam String uploads_id,
            @RequestParam String file_storage_path ) {
        uploadsService.completeUploads(uploads_id, file_storage_path);
        return new JsonResult<>(OK);
    }

    @GetMapping({"files/{node_id}/{file_name}", "files/{node_id}/{file_name}/info", "file/{node_id}/{file_name}/info"})
    public JsonResult<InternalFileMetadataVO> file_metadata_query(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "node_id必须是有效的UUID格式")
            @PathVariable String node_id,
            @PathVariable String file_name,
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "uid必须是有效的UUID格式")
            @RequestParam String uid ) {
        FileEntity fileData = fileService.queryUserFileByNodeIdAndName(node_id, file_name, uid);
        return new JsonResult<>(OK, VoMapper.toInternalFileMetadataVO(fileData));
    }
}
