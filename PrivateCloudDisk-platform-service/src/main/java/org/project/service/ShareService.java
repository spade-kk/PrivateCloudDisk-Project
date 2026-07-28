package org.project.service;

import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.ShareLinkEntity;
import org.project.model.entity.ShareResourceEntity;

import java.util.List;

/**
 * 分享链接服务接口（v2 — 多资源分享模型）
 */
public interface ShareService {

    // ==================== 管理端 ====================

    /**
     * 创建分享链接（密码明文传入，服务端 AES 加密存储）
     */
    ShareLinkEntity createShare(String userId, String shareName, String shareDescription,
                                List<ResourceItem> resources,
                                String password, Integer expireDays);

    /**
     * 获取用户的所有分享链接（列表，不含资源列表）
     * @param user_id 用户ID
     * @return 分享链接列表
     */
    List<ShareLinkEntity> getMyShares(String userId);

    /**
     * 获取单个分享链接的详细信息（含资源列表 + 解密后的提取码）
     */
    ShareLinkDetail getShareDetail(String userId, String shareId);

    /**
     * 撤销分享链接
     * @param user_id 用户ID
     * @param share_id 分享ID
     */
    void revokeShare(String userId, String shareId);

    // ==================== 公开访问端 ====================

    /**
     * 获取分享公开信息（不含资源列表）
     * @param share_token 分享令牌
     * @return 分享公开信息
     */
    ShareLinkEntity getShareAccessInfo(String shareToken);

    /**
     * 验证提取码并返回短期访问令牌
     * @param share_token 分享令牌
     * @param password 明文提取码
     * @return 短期访问令牌（JWT，15分钟有效）
     */
    String verifyPasswordAndGetToken(String shareToken, String password);

    /**
     * 通过访问令牌获取分享内容（含资源列表）
     * @param access_token 访问令牌
     * @return 分享实体
     */
    ShareLinkEntity getShareByAccessToken(String accessToken);

    /**
     * 获取分享资源列表
     * @param share_token 分享令牌
     */
    List<ShareResourceEntity> getShareResources(String shareToken);

    /**
     * 通过分享资源ID浏览文件夹子内容
     * <p>安全：通过 share_resource_id 校验资源是否在分享范围内，不暴露内部 node_id</p>
     * @param share_token 分享令牌
     */
    List<SharedItem> getShareResourceChildren(String shareToken, String shareResourceId);

    /**
     * 通过分享资源ID下载文件（支持真实和虚拟 share_resource_id）
     * <p>安全：通过 share_resource_id 校验资源是否在分享范围内</p>
     * @param share_token 分享令牌
     */
    FileEntity getSharedFileByResourceId(String shareToken, String shareResourceId);

    /**
     * 修改分享链接提取码
     * @param newPassword 新提取码明文，传 null 或空字符串表示移除密码
     */
    void updateSharePassword(String userId, String shareId, String newPassword);

    /**
     * 生成虚拟 share_resource_id（AES 加密内部 ID）
     * <p>用于子节点浏览，不暴露原始 file_id/node_id</p>
     */
    String encodeVirtualResourceId(String shareToken, String type, String internalId);

    /**
     * 解析虚拟 share_resource_id（AES 解密）
     * @return [shareToken, type, internalId]，解密失败返回 null
     */
    String[] decodeVirtualResourceId(String virtualId);

    /**
     * 通过虚拟 share_resource_id 浏览文件夹子内容
     * <p>先尝试解析为真实 share_resource_id，再尝试解析为虚拟 ID</p>
     */
    List<SharedItem> getShareResourceChildrenByVirtualId(String shareToken, String virtualResourceId);

    // ==================== 内部类型 ====================

    record ResourceItem(String type, String id) {
        public static ResourceItem of(String type, String id) {
            return new ResourceItem(type, id);
        }
    }

    /**
     * 分享文件夹内容项 —— 统一封装文件和文件夹实体
     */

    record SharedItem(String itemType, FileEntity file, FolderNodeEntity folder) {
        public static SharedItem ofFile(FileEntity file) {
            return new SharedItem("file", file, null);
        }

        public static SharedItem ofFolder(FolderNodeEntity folder) {
            return new SharedItem("folder", null, folder);
        }
    }

    /**
     * 分享链接详情（含资源列表 + 解密后的提取码）
     */
    record ShareLinkDetail(ShareLinkEntity share, String decryptedPassword) {
    }
}
