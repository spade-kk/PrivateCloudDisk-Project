package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.ShareLinkEntity;

import java.util.List;
import java.util.UUID;

/**
 * 分享链接 Mapper
 */
@Mapper
public interface ShareLinkMapper {

    /**
     * 插入分享链接
     */
    int insertShare(ShareLinkEntity share);

    /**
     * 根据 ID 查询分享
     */
    ShareLinkEntity findById(@Param("share_id") UUID share_id);

    /**
     * 根据分享令牌查询分享（含关联文件/文件夹信息）
     */
    ShareLinkEntity findByToken(@Param("share_token") String share_token);

    /**
     * 查询用户的所有分享（含关联文件/文件夹信息）
     */
    List<ShareLinkEntity> findByOwnerId(@Param("owner_id") UUID owner_id);

    /**
     * 撤销分享
     */
    int revokeShare(@Param("share_id") UUID share_id, @Param("owner_id") UUID owner_id);

    /**
     * 增加浏览次数
     */
    int incrementViewCount(@Param("share_id") UUID share_id);

    /**
     * 将过期的分享标记为 expired
     */
    int expireOutdatedShares();
}