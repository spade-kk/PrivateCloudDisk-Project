package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.TagEntity;
import org.project.model.entity.FileTagEntity;

import java.util.List;
import java.util.UUID;

/**
 * 标签 Mapper
 */
@Mapper
public interface TagMapper {

    // ==================== 标签 CRUD ====================

    /** 创建标签 */
    int insertTag(TagEntity tag);

    /** 删除标签（级联删除关联） */
    int deleteTag(@Param("tag_id") Long tag_id, @Param("user_id") UUID user_id);

    /** 更新标签（名称/颜色） */
    int updateTag(TagEntity tag);

    /** 获取用户所有标签（含统计数量） */
    List<TagEntity> findTagsByUserId(@Param("user_id") UUID user_id);

    /** 按名称查找标签 */
    TagEntity findTagByName(@Param("user_id") UUID user_id, @Param("tag_name") String tag_name);

    /** 按ID查找标签 */
    TagEntity findTagById(@Param("tag_id") Long tag_id);

    // ==================== 文件标签关联 ====================

    /** 为文件打标签 */
    int insertFileTag(FileTagEntity fileTag);

    /** 移除文件标签 */
    int deleteFileTag(@Param("ft_id") Long ft_id);

    /** 按标签+文件移除 */
    int deleteFileTagByTagAndFile(@Param("tag_id") Long tag_id,
                                   @Param("user_id") UUID user_id,
                                   @Param("file_id") UUID file_id);

    /** 按标签+文件夹移除 */
    int deleteFileTagByTagAndNode(@Param("tag_id") Long tag_id,
                                   @Param("user_id") UUID user_id,
                                   @Param("node_id") UUID node_id);

    /** 获取文件的所有标签 */
    List<TagEntity> findTagsByFileId(@Param("user_id") UUID user_id, @Param("file_id") UUID file_id);

    /** 获取文件夹的所有标签 */
    List<TagEntity> findTagsByNodeId(@Param("user_id") UUID user_id, @Param("node_id") UUID node_id);

    /** 批量获取文件标签（用于文件列表渲染） */
    List<FileTagEntity> findTagsByFileIds(@Param("user_id") UUID user_id, @Param("file_ids") List<UUID> file_ids);

    /** 批量获取文件夹标签 */
    List<FileTagEntity> findTagsByNodeIds(@Param("user_id") UUID user_id, @Param("node_ids") List<UUID> node_ids);

    /** 按标签获取文件列表（分页） */
    List<FileTagEntity> findFilesByTagId(@Param("tag_id") Long tag_id,
                                          @Param("user_id") UUID user_id,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    /** 按标签获取文件夹列表（分页） */
    List<FileTagEntity> findFoldersByTagId(@Param("tag_id") Long tag_id,
                                            @Param("user_id") UUID user_id,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    /** 统计标签下文件数量 */
    int countFilesByTagId(@Param("tag_id") Long tag_id, @Param("user_id") UUID user_id);

    /** 统计标签下文件夹数量 */
    int countFoldersByTagId(@Param("tag_id") Long tag_id, @Param("user_id") UUID user_id);

    /** 检查文件是否已打某标签 */
    FileTagEntity findFileTagByTagAndFile(@Param("tag_id") Long tag_id,
                                           @Param("user_id") UUID user_id,
                                           @Param("file_id") UUID file_id);

    /** 检查文件夹是否已打某标签 */
    FileTagEntity findFileTagByTagAndNode(@Param("tag_id") Long tag_id,
                                           @Param("user_id") UUID user_id,
                                           @Param("node_id") UUID node_id);

    /** 预设标签（新用户注册时批量创建） */
    int insertDefaultTags(@Param("user_id") UUID user_id);
}