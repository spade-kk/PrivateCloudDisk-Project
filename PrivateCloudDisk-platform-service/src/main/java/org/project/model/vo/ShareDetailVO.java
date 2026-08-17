package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分享链接详情 VO（管理端详情接口返回，含资源列表和明文提取码，v2）
 *
 * <p>与 ShareLinkVO（列表用）的区别：
 * <ul>
 *   <li>包含完整的资源列表 {@code resources}</li>
 *   <li>包含解密后的提取码 {@code share_password}（仅在管理端可见）</li>
 *   <li>包含分享者名称 {@code owner_name}</li>
 * </ul>
 *
 * <p>安全：此 VO 仅在管理端使用，需要用户登录认证，公开端无法获取。
 */
@Data
public class ShareDetailVO {
    /** 分享ID */
    private String share_id;

    /** 分享访问令牌 */
    private String share_token;

    /** 分享链接完整 URL */
    private String share_url;

    /** 分享名称 */
    private String share_name;

    /** 分享说明 */
    private String share_description;

    /** 分享者名称 */
    private String owner_name;

    /** 是否有密码 */
    private Boolean share_has_password;

    /** 是否允许下载/在线获取文件内容。 */
    private Boolean share_allow_download;

    /** 明文提取码（仅管理端可见，AES 解密后返回） */
    private String share_password;

    /** 过期时间 */
    private LocalDateTime share_expires_at;

    /** 浏览次数 */
    private Integer share_view_count;

    /** 分享状态 */
    private String share_status;

    /** 创建时间 */
    private LocalDateTime share_created_at;

    /** 资源数量 */
    private Integer resource_count;

    /** 分享资源列表 */
    private List<ShareResourceVO> resources;
}
