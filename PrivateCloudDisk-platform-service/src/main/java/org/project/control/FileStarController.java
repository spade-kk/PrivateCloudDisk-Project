package org.project.control;

import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.project.control.result.JsonResult;
import org.project.model.entity.FileStarEntity;
import org.project.service.FileStarService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件收藏控制器
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/business/stars")
public class FileStarController extends BaseController {
    
    private final FileStarService fileStarService;
    
    /**
     * 添加文件收藏
     */
    @PostMapping("/{file_id}")
    public JsonResult<Void> addStar(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "file_id必须是有效的UUID格式")
            @PathVariable String file_id,
            @RequestHeader("X-User-Id") String user_id) {
        fileStarService.addFileStar(user_id, file_id);
        return new JsonResult<>(OK);
    }
    
    /**
     * 取消文件收藏
     */
    @DeleteMapping("/{file_id}")
    public JsonResult<Void> removeStar(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "file_id必须是有效的UUID格式")
            @PathVariable String file_id,
            @RequestHeader("X-User-Id") String user_id) {
        fileStarService.removeFileStar(user_id, file_id);
        return new JsonResult<>(OK);
    }
    
    /**
     * 检查是否已收藏
     */
    @GetMapping("/{file_id}/status")
    public JsonResult<Boolean> checkStarred(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "file_id必须是有效的UUID格式")
            @PathVariable String file_id,
            @RequestHeader("X-User-Id") String user_id) {
        boolean starred = fileStarService.isFileStarred(user_id, file_id);
        return new JsonResult<>(OK, starred);
    }
    
    /**
     * 获取收藏列表
     */
    @GetMapping("/")
    public JsonResult<List<FileStarEntity>> getStarredFiles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader("X-User-Id") String user_id) {
        List<FileStarEntity> stars = fileStarService.getStarredFiles(user_id, page, pageSize);
        return new JsonResult<>(OK, stars);
    }
    
    /**
     * 统计收藏数量
     */
    @GetMapping("/count")
    public JsonResult<Integer> countStarredFiles(@RequestHeader("X-User-Id") String user_id) {
        Integer count = fileStarService.countStarredFiles(user_id);
        return new JsonResult<>(OK, count);
    }
}
