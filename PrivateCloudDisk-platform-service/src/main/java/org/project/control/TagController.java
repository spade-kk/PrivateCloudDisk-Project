package org.project.control;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.project.control.result.JsonResult;
import org.project.model.dto.FileTagRequest;
import org.project.model.dto.TagCreateRequest;
import org.project.model.dto.TagBatchRequest;
import org.project.model.vo.TagVO;
import org.project.model.vo.TaggedFileVO;
import org.project.service.TagService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 标签控制器
 *
 * <p>API 设计：
 * <ul>
 *   <li>POST   /business/tags                    — 创建标签</li>
 *   <li>GET    /business/tags                    — 获取用户所有标签（含统计）</li>
 *   <li>PUT    /business/tags/{tag_id}           — 更新标签</li>
 *   <li>DELETE /business/tags/{tag_id}           — 删除标签</li>
 *   <li>POST   /business/tags/files             — 为文件/文件夹打标签（批量）</li>
 *   <li>DELETE /business/tags/files             — 移除标签</li>
 *   <li>GET    /business/tags/files/{target_id} — 获取文件/文件夹的标签</li>
 *   <li>GET    /business/tags/{tag_id}/files    — 按标签查文件</li>
 *   <li>GET    /business/tags/{tag_id}/folders  — 按标签查文件夹</li>
 * </ul>
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/business/tags")
public class TagController extends BaseController {

    private final TagService tagService;

    private static final String UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    // ═══════════════════════════════════════════════
    // 标签 CRUD
    // ═══════════════════════════════════════════════

    /**
     * 创建标签
     */
    @PostMapping
    public JsonResult<TagVO> createTag(
            @Valid @RequestBody TagCreateRequest request,
            @RequestHeader("X-User-Id") String user_id) {
        TagVO tag = tagService.createTag(UUID.fromString(user_id), request.getTag_name(), request.getTag_color());
        return new JsonResult<>(OK, tag);
    }

    /**
     * 获取用户所有标签（含文件/文件夹统计数量）
     */
    @GetMapping
    public JsonResult<List<TagVO>> getUserTags(@RequestHeader("X-User-Id") String user_id) {
        List<TagVO> tags = tagService.getUserTags(UUID.fromString(user_id));
        return new JsonResult<>(OK, tags);
    }

    /**
     * 更新标签
     */
    @PutMapping("/{tag_id}")
    public JsonResult<TagVO> updateTag(
            @PathVariable Long tag_id,
            @Valid @RequestBody TagCreateRequest request,
            @RequestHeader("X-User-Id") String user_id) {
        TagVO tag = tagService.updateTag(UUID.fromString(user_id), tag_id, request.getTag_name(), request.getTag_color());
        return new JsonResult<>(OK, tag);
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{tag_id}")
    public JsonResult<Void> deleteTag(
            @PathVariable Long tag_id,
            @RequestHeader("X-User-Id") String user_id) {
        tagService.deleteTag(UUID.fromString(user_id), tag_id);
        return new JsonResult<>(OK);
    }

    // ═══════════════════════════════════════════════
    // 文件标签关联
    // ═══════════════════════════════════════════════

    /**
     * 为文件/文件夹打标签（支持批量打多个标签）
     *
     * <p>请求体示例：
     * <pre>
     * {
     *   "tag_ids": [1, 3, 5],
     *   "target_type": "file",
     *   "target_id": "550e8400-e29b-41d4-a716-446655440000"
     * }
     * </pre>
     */
    @PostMapping("/files")
    public JsonResult<Void> tagFile(
            @Valid @RequestBody FileTagRequest request,
            @RequestHeader("X-User-Id") String user_id) {
        tagService.tagFile(UUID.fromString(user_id), request.getTarget_id(),
                request.getTarget_type(), request.getTag_ids());
        return new JsonResult<>(OK);
    }

    /**
     * 移除文件/文件夹的指定标签
     */
    @DeleteMapping("/files")
    public JsonResult<Void> untagFile(
            @RequestParam Long tag_id,
            @RequestParam @Pattern(regexp = "^(file|folder)$") String target_type,
            @RequestParam @Pattern(regexp = UUID_REGEX) String target_id,
            @RequestHeader("X-User-Id") String user_id) {
        tagService.untagFile(UUID.fromString(user_id), target_id, target_type, tag_id);
        return new JsonResult<>(OK);
    }

    /**
     * 获取文件/文件夹的所有标签
     */
    @GetMapping("/files/{target_id}")
    public JsonResult<List<TagVO>> getFileTags(
            @Pattern(regexp = UUID_REGEX) @PathVariable String target_id,
            @RequestParam @Pattern(regexp = "^(file|folder)$") String target_type,
            @RequestHeader("X-User-Id") String user_id) {
        List<TagVO> tags = tagService.getFileTags(UUID.fromString(user_id), target_id, target_type);
        return new JsonResult<>(OK, tags);
    }

    /**
     * 文件列表批量查询标签，避免网格/列表渲染产生 N+1 请求。
     */
    @PostMapping("/files/batch")
    public JsonResult<Map<String, List<TagVO>>> getTagsBatch(
            @Valid @RequestBody TagBatchRequest request,
            @RequestHeader("X-User-Id") String user_id) {
        // AUDIT FIX [4.2]: 一个目录只需一次批量标签查询，结果按目标 ID 返回。
        Map<String, List<TagVO>> tags = tagService.getTagsBatch(
                UUID.fromString(user_id), request.getFile_ids(), request.getFolder_ids());
        return new JsonResult<>(OK, tags);
    }

    // ═══════════════════════════════════════════════
    // 按标签查询
    // ═══════════════════════════════════════════════

    /**
     * 按标签获取文件列表
     */
    @GetMapping("/{tag_id}/files")
    public JsonResult<List<TaggedFileVO>> getFilesByTag(
            @PathVariable Long tag_id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestHeader("X-User-Id") String user_id) {
        List<TaggedFileVO> files = tagService.getFilesByTag(tag_id, UUID.fromString(user_id), page, pageSize);
        return new JsonResult<>(OK, files);
    }

    /**
     * 按标签获取文件夹列表
     */
    @GetMapping("/{tag_id}/folders")
    public JsonResult<List<TaggedFileVO>> getFoldersByTag(
            @PathVariable Long tag_id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestHeader("X-User-Id") String user_id) {
        List<TaggedFileVO> folders = tagService.getFoldersByTag(tag_id, UUID.fromString(user_id), page, pageSize);
        return new JsonResult<>(OK, folders);
    }
}
