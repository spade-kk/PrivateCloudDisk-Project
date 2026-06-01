package org.project.control;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.project.config.RabbitMQConifgure;
import org.project.control.result.JsonResult;
import org.project.data.*;
import org.project.service.FileService;
import org.project.service.UploadsService;
import org.project.service.UserService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
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

    /**
     * 测试消息中间件发送消息
     * @return JsonResult data Void
     */
    @GetMapping("test")
    public JsonResult<Void> test() {
        rabbitTemplate.convertAndSend(
                RabbitMQConifgure.REGISTER_EXCHANGE,
                RabbitMQConifgure.REGISTER_ROUTING_KEY,
                "test");

        return new JsonResult<Void>(OK);
    }
    /**
     * 第二个测试 测试redis缓存
     * @return JsonResult data Void
     */
    @GetMapping("test2")
    public JsonResult<Void> test2() {
        userService.findRootFolderNodeByUserId("415d3064-a465-4813-8f42-d6f1aa9b87c0");

        return new JsonResult<Void>(OK);
    }

    /**
     * 处理上传会话完成上传的请求
     * @param uploads_id 上传会话ID
     * @param chunk_index 分块索引
     * @param storage_path 分块存储路径
     * @return JsonResult data Void
     */
    @PostMapping("uploads/{uploads_id}/chunks/{chunk_index}/complete")
    public JsonResult<Void> chunk_complete( @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                                            message = "uploads_id必须是有效的UUID格式")
                                            @PathVariable String uploads_id,
                                            @Pattern(regexp = "^[0-9]+$",
                                            message = "chunk_index必须是一个非负的正整数且从1开始")
                                            @PathVariable String chunk_index,
                                            @RequestParam String storage_path )
    {
        uploadsService.completeChunkUpload(uploads_id, Integer.parseInt(chunk_index), storage_path);

        return new JsonResult<Void>(OK);
    }

    /**
     * 处理上传会话查询的请求
     * @param uploads_id 上传会话ID
     * @return JsonResult data UploadsSessionData
     */
    @PostMapping("uploads/{uploads_id}/query")
    public JsonResult<UploadsSessionData> uploads_query( @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                                                         message = "uploads_id必须是有效的UUID格式")
                                                         @PathVariable String uploads_id )
    {
        UploadsSessionData uploadsSessionData = uploadsService.queryUploadsSessionById(uploads_id);

        return new JsonResult<UploadsSessionData>(OK, uploadsSessionData);
    }

    /**
     * 处理上传会话分块查询的请求
     * @param uploads_id 上传会话ID
     * @param chunk_index 分块索引
     * @return JsonResult data ChunkData
     */
    @PostMapping("uploads/{uploads_id}/chunks/{chunk_index}/query")
    public JsonResult<UploadsChunkData> chunk_query(@Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                                              message = "uploads_id必须是有效的UUID格式")
                                              @PathVariable String uploads_id,
                                                    @NotNull(message = "chunk_index 不能为空")
                                                    @Min(value = 0, message = "chunk_index 不能为负数")
                                              @PathVariable Integer chunk_index )
    {
        UploadsChunkData chunkData = uploadsService.queryChunkByUploadsIdAndChunkIndex(uploads_id, chunk_index);

        return new JsonResult<UploadsChunkData>(OK, chunkData);
    }

    /**
     * 处理上传会话分块合并的通知请求
     * @param uploads_id 上传会话ID
     * @return JsonResult data Void
     */
    @PostMapping("uploads/{uploads_id}/merging")
    public JsonResult<Void> uploads_merging( @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                                             message = "uploads_id必须是有效的UUID格式")
                                             @PathVariable String uploads_id )
    {
        uploadsService.uploadsMerging(uploads_id);

        return new JsonResult<Void>(OK);
    }

    /**
     * 处理文件上传完成的请求
     * @param uploads_id 上传会话ID
     * @param file_storage_path 文件储存路径
     * @return JsonResult data Void
     */
    @PostMapping("file/complete")
    public JsonResult<Void> file_complete( @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                                           message = "uploads_id必须是有效的UUID格式")
                                           @RequestParam String uploads_id,
                                           @RequestParam String file_storage_path )
    {
        uploadsService.completeUploads(uploads_id, file_storage_path);
        return new JsonResult<Void>(OK);
    }

    /**
     * 处理文件信息查询请求
     * @param node_id 文件的存放路径
     * @param file_name 文件的名称(包括完整后缀)
     * @param uid 查询文件用户的uid
     * @return JsonResult data FileData
     */
    @GetMapping("file/{node_id}/{file_name}/info")
    public JsonResult<FileData> file_metadata_query( @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                                                            message = "node_id必须是有效的UUID格式")
                                                     @PathVariable String node_id,
                                                     @PathVariable String file_name,
                                                     @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                                                             message = "uid必须是有效的UUID格式")
                                                     @RequestParam String uid )
    {
        FileData fileData = fileService.queryUserFileByNodeIdAndName(node_id, file_name, uid);

        return new JsonResult<FileData>(OK, fileData);
    }
}
