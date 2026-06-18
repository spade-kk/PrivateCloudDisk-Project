package org.project.service;

import org.project.model.dto.ShareCreateRequest;
import org.project.model.entity.ShareLinkEntity;
import org.project.model.vo.ShareAccessInfoVO;
import org.project.model.vo.ShareLinkVO;

import java.util.List;

/**
 * 分享链接服务接口
 */
public interface ShareService {

    /**
     * 创建分享链接
     * @param user_id 用户ID
     * @param request 创建请求
     * @return 创建的分享链接 VO
     */
    ShareLinkVO createShare(String user_id, ShareCreateRequest request);

    /**
     * 获取用户的所有分享链接
     * @param user_id 用户ID
     * @return 分享链接列表
     */
    List<ShareLinkVO> getMyShares(String user_id);

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
    ShareAccessInfoVO getShareAccessInfo(String share_token);

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
     * 获取分享文件夹的子内容（文件和子文件夹）
     * <p>仅支持文件夹分享，且 node_id 必须在分享范围内
     * @param share_token 分享令牌
     * @param node_id 要浏览的节点ID（null 或 分享的根节点ID 表示浏览分享根目录）
     * @return 子内容列表（含文件和文件夹）
     */
    List<org.project.model.vo.ShareContentItemVO> getSharedFolderContents(String share_token, String node_id);
}