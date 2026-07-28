package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.ShareResourceEntity;

import java.util.List;
import java.util.UUID;

/**
 * 分享资源 Mapper（v2 新增）
 */
@Mapper
public interface ShareResourceMapper {

    /**
     * 批量插入分享资源
     */
    int insertBatch(@Param("resources") List<ShareResourceEntity> resources);

    /**
     * 根据分享ID查询所有资源（含关联的文件/文件夹名称和大小）
     */
    List<ShareResourceEntity> findByShareId(@Param("share_id") UUID shareId);

    /**
     * 根据分享资源ID查询单个资源
     */
    ShareResourceEntity findById(@Param("share_resource_id") UUID shareResourceId);

    /**
     * 查询引用指定文件的分享链接 ID，用于永久删除时判断并清理空分享。
     */
    List<UUID> findShareIdsByFileId(@Param("file_id") UUID fileId);

    /**
     * 永久删除文件时移除所有文件型分享资源引用。
     */
    int deleteByFileId(@Param("file_id") UUID fileId);

    /**
     * 根据分享ID删除所有资源
     */
    int deleteByShareId(@Param("share_id") UUID shareId);
}
