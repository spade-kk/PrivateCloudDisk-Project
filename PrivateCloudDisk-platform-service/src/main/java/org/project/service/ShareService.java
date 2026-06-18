package org.project.service;

import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.ShareLinkEntity;

import java.util.List;

/**
 * 分享链接服务接口
 *
 * <p><b>分层原则</b>：本接口不接收任何 Request DTO，不返回任何 VO。
 * 所有方法参数由接口层从 Request DTO 提取后传入，所有返回值均为 Entity，
 * Entity → VO 的转换由接口层通过 {@link org.project.model.vo.VoMapper} 完成。
 */
public interface ShareService {

    /**
     * 创建分享链接
     * @param userId 用户ID
     * @param shareName 分享名称
     * @param targetType 目标类型（"file" 或 "folder"）
     * @param fileId 文件ID（targetType=file 时必填）
     * @param nodeId 文件夹节点ID（targetType=folder 时必填）
     * @param password 密码（可选）
     * @param expireDays 过期天数（可选）
     * @return 创建的分享链接实体
     */
    ShareLinkEntity createShare(String userId, String shareName, String targetType,
                              String fileId, String nodeId, String password, Integer expireDays);

    /**
     * 获取用户的所有分享链接
     * @param user_id 用户ID
     * @return 分享链接列表
     */
    List<ShareLinkEntity> getMyShares(String user_id);

    /**
     * 撤销分享链接
     * @param user_id 用户ID
     * @param share_id 分享ID
     */
    void revokeShare(String user_id, String share_id);

    /**
     * 获取分享公开信息（无需鉴权，用于展示分享页面）
     * @param share_token 分享令牌
     * @return 分享公开信息
     */
    ShareLinkEntity getShareAccessInfo(String shareToken);

    /**
     * 验证分享密码并返回短期访问令牌
     * @param share_token 分享令牌
     * @param password 用户输入的密码
     * @return 短期访问令牌（JWT，15分钟有效）
     */
    String verifyPasswordAndGetToken(String share_token, String password);

    /**
     * 通过访问令牌验证并获取分享内容
     * @param access_token 访问令牌
     * @return 分享实体
     */
    ShareLinkEntity getShareByAccessToken(String access_token);

    /**
     * 获取分享的文件实体（用于文件下载）
     * @param share_token 分享令牌
     * @param file_id 文件ID
     * @return 文件实体
     */
    org.project.model.entity.FileEntity getSharedFile(String share_token, String file_id);

    /**
     * 获取分享文件夹的子内容（文件和子文件夹实体列表）
     * <p>仅支持文件夹分享，且 node_id 必须在分享范围内
     * @param share_token 分享令牌
     * @param node_id 要浏览的节点ID（null 或 分享的根节点ID 表示浏览分享根目录）
     * @return 子内容列表（含文件和文件夹）
     */
    List<SharedItem> getSharedFolderContents(String shareToken, String nodeId);

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
}