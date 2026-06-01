package org.project.control;


import org.project.control.result.JsonResult;
import org.project.data.FileData;
import org.project.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/business/files")
public class FileController extends BaseController {
    @Autowired
    private FileService fileService;

    /**
     * 查询文件信息
     * @param position
     * @param file_name
     * @param user_id
     * @return
     */
    @GetMapping("/{position}/{file_name}/info")
    public JsonResult<FileData> queryFileData( @PathVariable String position,
                                               @PathVariable String file_name,
                                               @RequestHeader("X-User-Id") String user_id ) {
        FileData fileData = fileService.queryUserFileByNodeIdAndName(position, file_name, user_id);

        return new JsonResult<>(OK, fileData);
    }

    /**
     * 根据ID查询文件信息
     * @param file_id 文件ID
     * @param user_id 查询用户Uid
     * @return
     */
    @GetMapping("/{file_id}")
    public JsonResult<FileData> queryFileDataByFileId( @PathVariable String file_id,
                                                       @RequestHeader("X-User-Id") String user_id ) {
        FileData fileData = fileService.queryUserFileById(file_id, user_id);
        return new JsonResult<>(OK, fileData);
    }

    /**
     * 文件重命名
     * @param file_new_name
     * @param file_id
     * @param user_id
     * @return
     */
    @PatchMapping("/{file_id}/name")
    public JsonResult<Void> updateFileName(@PathVariable String file_id,
                                           String file_new_name,
                                           @RequestHeader("X-User-Id") String user_id ) {
        fileService.updateFileName(file_id, file_new_name, user_id);
        return new JsonResult<>(OK);
    }

    /**
     * 文件移动
     * @param file_id 文件ID
     * @param user_id 用户ID
     * @param target_node_id 目标节点ID
     * @return
     */
    @PatchMapping("/{file_id}/position")
    public JsonResult<Void> moveFileByFileId( @PathVariable String file_id,
                                              String target_node_id,
                                              @RequestHeader("X-User-Id") String user_id ) {
        fileService.moveFileByFileId(file_id, target_node_id, user_id);
        return new JsonResult<>(OK);
    }

    /**
     * 删除文件
     * @param file_id
     * @param user_id
     * @return
     */
    @DeleteMapping("/{file_id}")
    public JsonResult<Void> deleteFileByFileId( @PathVariable String file_id,
                                              @RequestHeader("X-User-Id") String user_id ) {
        fileService.deleteFileByFileId(file_id, user_id);
        return new JsonResult<>(OK);
    }
}
