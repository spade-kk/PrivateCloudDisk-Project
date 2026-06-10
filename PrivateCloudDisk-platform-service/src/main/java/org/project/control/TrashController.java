package org.project.control;

import lombok.RequiredArgsConstructor;
import org.project.control.result.JsonResult;
import org.project.model.entity.TrashFileEntity;
import org.project.model.vo.TrashFileVO;
import org.project.model.vo.VoMapper;
import org.project.service.DirectoryTreeService;
import org.project.service.TrashService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 回收站控制器
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/business/trash")
public class TrashController extends BaseController {
    
    private final TrashService trashService;
    private final DirectoryTreeService directoryTreeService;
    
    /**
     * 将文件移动到回收站
     */
    @PostMapping("/{file_id}")
    public JsonResult<Void> moveToTrash(
            @PathVariable String file_id,
            @RequestHeader("X-User-Id") String user_id) {
        directoryTreeService.deleteFolderNodeToTrashByNodeId(UUID.fromString(file_id), UUID.fromString(user_id));
        return new JsonResult<>(OK);
    }
    
    /**
     * 从回收站恢复文件
     */
    @PostMapping("/{trash_id}/restore")
    public JsonResult<Void> restoreFromTrash(
            @PathVariable Long trash_id,
            @RequestHeader("X-User-Id") String user_id) {
        trashService.restoreFromTrash(trash_id, UUID.fromString(user_id));
        return new JsonResult<>(OK);
    }
    
    /**
     * 彻底删除回收站文件
     */
    @DeleteMapping("/{trash_id}")
    public JsonResult<Void> permanentDelete(
            @PathVariable Long trash_id,
            @RequestHeader("X-User-Id") String user_id) {
        trashService.permanentDelete(trash_id, UUID.fromString(user_id));
        return new JsonResult<>(OK);
    }
    
    /**
     * 清空回收站
     */
    @DeleteMapping("/")
    public JsonResult<Void> emptyTrash(@RequestHeader("X-User-Id") String user_id) {
        trashService.emptyTrash(UUID.fromString(user_id));
        return new JsonResult<>(OK);
    }
    
    /**
     * 获取回收站文件列表
     */
    @GetMapping("/")
    public JsonResult<List<TrashFileVO>> getTrashFiles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader("X-User-Id") String user_id) {
        List<TrashFileEntity> trashFiles = trashService.getTrashFiles(UUID.fromString(user_id), page, pageSize);
        return new JsonResult<>(OK, VoMapper.toTrashFileVOList(trashFiles));
    }
    
    /**
     * 统计回收站文件数量
     */
    @GetMapping("/count")
    public JsonResult<Integer> countTrashFiles(@RequestHeader("X-User-Id") String user_id) {
        Integer count = trashService.countTrashFiles(UUID.fromString(user_id));
        return new JsonResult<>(OK, count);
    }
    
    /**
     * 获取回收站文件详情
     */
    @GetMapping("/{trash_id}")
    public JsonResult<TrashFileVO> getTrashFileById(
            @PathVariable Long trash_id,
            @RequestHeader("X-User-Id") String user_id) {
        TrashFileEntity trashFile = trashService.getTrashFileById(trash_id, UUID.fromString(user_id));
        return new JsonResult<>(OK, VoMapper.toTrashFileVO(trashFile));
    }
}
