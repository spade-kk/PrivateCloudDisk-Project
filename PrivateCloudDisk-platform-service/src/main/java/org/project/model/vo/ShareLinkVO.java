package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享链接 VO（管理端列表返回，v2 — 不含资源列表和密码）
 *
 * <p>列表接口仅返回分享链接的基本信息，如需查看资源列表和提取码，
 * 需调用详情接口 GET /business/shares/{share_id}。
 */
@Data
public class ShareLinkVO {
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

    /** 是否有密码 */
    private Boolean share_has_password;

    /** 是否允许通过分享授权获取实际文件内容。 */
    private Boolean share_allow_download;

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
}
