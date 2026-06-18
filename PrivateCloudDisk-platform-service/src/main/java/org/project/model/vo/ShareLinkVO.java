package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享链接 VO（管理端返回）
 */
@Data
public class ShareLinkVO {
    /** 分享ID */
    private String share_id;

    /** 分享访问令牌 */
    private String share_token;

    /** 分享链接完整 URL */
    private String share_url;

    /** 分享目标类型 */
    private String share_target_type;

    /** 分享名称 */
    private String share_name;

    /** 目标名称 */
    private String target_name;

    /** 目标大小 */
    private Long target_size;

    /** 文件类型 */
    private String file_type;

    /** 是否有密码 */
    private Boolean share_has_password;

    /** 过期时间 */
    private LocalDateTime share_expires_at;

    /** 浏览次数 */
    private Integer share_view_count;

    /** 分享状态 */
    private String share_status;

    /** 创建时间 */
    private LocalDateTime share_created_at;
}