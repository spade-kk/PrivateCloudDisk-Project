package org.project.control;

import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.project.control.result.JsonResult;
import org.project.model.entity.FileStarEntity;
import org.project.model.vo.FileStarVO;
import org.project.model.vo.VoMapper;
import org.project.service.FileStarService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件/文件夹收藏控制器
 *
 * <p>API 路径设计：
 * <ul>
 *   <li>POST   /business/stars/files/{file_id}     — 添加文件收藏</li>
 *   <li>DELETE /business/stars/files/{file_id}     — 取消文件收藏</li>
 *   <li>GET    /business/stars/files/{file_id}/status — 检查文件是否已收藏</li>
 *   <li>POST   /business/stars/folders/{node_id}   — 添加文件夹收藏</li>
 *   <li>DELETE /business/stars/folders/{node_id}   — 取消文件夹收藏</li>
 *   <li>GET    /business/stars/folders/{node_id}/status — 检查文件夹是否已收藏</li>
 *   <li>GET    /business/stars/                    — 获取收藏列表（分页）</li>
 *   <li>GET    /business/stars/count               — 统计收藏总数</li>
 *   <li>GET    /business/stars/file-ids            — 获取收藏的文件ID列表</li>
 *   <li>GET    /business/stars/folder-ids          — 获取收藏的文件夹ID列表</li>
 * </ul>
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/business/stars")
public class FileStarController extends BaseController {

    private final FileStarService fileStarService;

    private static final String UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    // ═══════════════════════════════════════════════
    // 文件收藏
    // ═══════════════════════════════════════════════

    /**
     * 添加文件收藏
     */
    @PostMapping("/files/{file_id}")
    public JsonResult<Void> addFileStar(
            @Pattern(regexp = UUID_REGEX, message = "file_id 必须是有效的UUID格式")
            @PathVariable String file_id,
            @RequestHeader("X-User-Id") String user_id) {
        fileStarService.addFileStar(user_id, file_id);
        return new JsonResult<>(OK);
    }

    /**
     * 取消文件收藏
     */
    @DeleteMapping("/files/{file_id}")
    public JsonResult<Void> removeFileStar(
            @Pattern(regexp = UUID_REGEX, message = "file_id 必须是有效的UUID格式")
            @PathVariable String file_id,
            @RequestHeader("X-User-Id") String user_id) {
        fileStarService.removeFileStar(user_id, file_id);
        return new JsonResult<>(OK);
    }

    /**
     * 检查文件是否已收藏
     */
    @GetMapping("/files/{file_id}/status")
    public JsonResult<Boolean> checkFileStarred(
            @Pattern(regexp = UUID_REGEX, message = "file_id 必须是有效的UUID格式")
            @PathVariable String file_id,
            @RequestHeader("X-User-Id") String user_id) {
        boolean starred = fileStarService.isFileStarred(user_id, file_id);
        return new JsonResult<>(OK, starred);
    }

    // ═══════════════════════════════════════════════
    // 文件夹收藏
    // ═══════════════════════════════════════════════

    /**
     * 添加文件夹收藏
     */
    @PostMapping("/folders/{node_id}")
    public JsonResult<Void> addFolderStar(
            @Pattern(regexp = UUID_REGEX, message = "node_id 必须是有效的UUID格式")
            @PathVariable String node_id,
            @RequestHeader("X-User-Id") String user_id) {
        fileStarService.addFolderStar(user_id, node_id);
        return new JsonResult<>(OK);
    }

    /**
     * 取消文件夹收藏
     */
    @DeleteMapping("/folders/{node_id}")
    public JsonResult<Void> removeFolderStar(
            @Pattern(regexp = UUID_REGEX, message = "node_id 必须是有效的UUID格式")
            @PathVariable String node_id,
            @RequestHeader("X-User-Id") String user_id) {
        fileStarService.removeFolderStar(user_id, node_id);
        return new JsonResult<>(OK);
    }

    /**
     * 检查文件夹是否已收藏
     */
    @GetMapping("/folders/{node_id}/status")
    public JsonResult<Boolean> checkFolderStarred(
            @Pattern(regexp = UUID_REGEX, message = "node_id 必须是有效的UUID格式")
            @PathVariable String node_id,
            @RequestHeader("X-User-Id") String user_id) {
        boolean starred = fileStarService.isFolderStarred(user_id, node_id);
        return new JsonResult<>(OK, starred);
    }

    // ═══════════════════════════════════════════════
    // 收藏列表 & 统计
    // ═══════════════════════════════════════════════

    /**
     * 获取收藏列表（含文件/文件夹详情，分页）
     *
     * <p>返回的数据包含关联的文件名/文件夹名、大小等信息，
     * 前端可直接渲染为节点卡片。
     */
    @GetMapping("/")
    public JsonResult<List<FileStarVO>> getStarredItems(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer pageSize,
            @RequestHeader("X-User-Id") String user_id) {
        List<FileStarEntity> items = fileStarService.getStarredItems(user_id, page, pageSize);
        return new JsonResult<>(OK, VoMapper.toFileStarVOList(items));
    }

    /**
     * 统计收藏总数
     */
    @GetMapping("/count")
    public JsonResult<Integer> countStarredItems(@RequestHeader("X-User-Id") String user_id) {
        Integer count = fileStarService.countStarredItems(user_id);
        return new JsonResult<>(OK, count);
    }

    /**
     * 获取收藏的文件ID列表（用于前端批量判断收藏状态）
     */
    @GetMapping("/file-ids")
    public JsonResult<List<String>> getStarredFileIds(@RequestHeader("X-User-Id") String user_id) {
        List<String> ids = fileStarService.getStarredFileIds(user_id);
        return new JsonResult<>(OK, ids);
    }

    /**
     * 获取收藏的文件夹ID列表（用于前端批量判断收藏状态）
     */
    @GetMapping("/folder-ids")
    public JsonResult<List<String>> getStarredNodeIds(@RequestHeader("X-User-Id") String user_id) {
        List<String> ids = fileStarService.getStarredNodeIds(user_id);
        return new JsonResult<>(OK, ids);
    }
}