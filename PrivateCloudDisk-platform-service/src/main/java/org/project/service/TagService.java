package org.project.service;

import org.project.model.entity.TagEntity;
import org.project.model.entity.FileTagEntity;
import org.project.model.vo.TagVO;
import org.project.model.vo.TaggedFileVO;

import java.util.List;
import java.util.UUID;

/**
 * 标签服务接口
 */
public interface TagService {

    // ==================== 标签 CRUD ====================

    /** 创建标签 */
    TagVO createTag(UUID user_id, String tag_name, String tag_color);

    /** 删除标签（级联删除关联） */
    void deleteTag(UUID user_id, Long tag_id);

    /** 更新标签 */
    TagVO updateTag(UUID user_id, Long tag_id, String tag_name, String tag_color);

    /** 获取用户所有标签（含统计） */
    List<TagVO> getUserTags(UUID user_id);

    /** 获取标签详情 */
    TagVO getTagById(Long tag_id);

    // ==================== 文件标签关联 ====================

    /** 为文件/文件夹打标签（批量） */
    void tagFile(UUID user_id, String target_id, String target_type, List<Long> tag_ids);

    /** 移除文件/文件夹的指定标签 */
    void untagFile(UUID user_id, String target_id, String target_type, Long tag_id);

    /** 获取文件/文件夹的所有标签 */
    List<TagVO> getFileTags(UUID user_id, String target_id, String target_type);

    /** 批量获取文件标签（用于文件列表渲染） */
    List<FileTagEntity> getFileTagsBatch(UUID user_id, List<UUID> file_ids);

    /** 批量获取文件夹标签 */
    List<FileTagEntity> getFolderTagsBatch(UUID user_id, List<UUID> node_ids);

    // ==================== 按标签查询 ====================

    /** 按标签获取文件列表（分页） */
    List<TaggedFileVO> getFilesByTag(Long tag_id, UUID user_id, int page, int pageSize);

    /** 按标签获取文件夹列表（分页） */
    List<TaggedFileVO> getFoldersByTag(Long tag_id, UUID user_id, int page, int pageSize);

    /** 预设标签（新用户注册时自动创建） */
    void createDefaultTags(UUID user_id);
}