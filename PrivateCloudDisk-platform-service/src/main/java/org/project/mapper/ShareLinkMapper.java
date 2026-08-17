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

    /**
     * 更新分享链接（用于修改密码等）
     */
    int updateShare(ShareLinkEntity share);

    /** 更新分享是否允许获取实际文件内容。 */
    int updateShareDownloadPermission(@Param("share_id") UUID shareId,
                                      @Param("owner_id") UUID ownerId,
                                      @Param("allow_download") boolean allowDownload);

    /**
     * 删除指定候选集合中已不包含任何资源的分享链接。
     *
     * <p>需求三-2：多资源分享只移除被永久删除的文件，保留仍有效的其他资源；
     * 仅在分享已为空时删除链接，避免误删同一分享中的有效资源。</p>
     */
    int deleteEmptySharesByIds(@Param("share_ids") List<UUID> shareIds);
}
