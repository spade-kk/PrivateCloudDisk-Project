package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享公开信息 VO（访问分享链接时返回，不含敏感信息）
 */
@Data
public class ShareAccessInfoVO {
    /** 分享访问令牌 */
    private String share_token;

    /** 分享名称 */
    private String share_name;

    /** 分享目标类型 */
    private String share_target_type;

    /** 目标名称 */
    private String target_name;

    /** 目标大小 */
    private Long target_size;

    /** 文件类型 */
    private String file_type;

    /** 分享者名称 */
    private String owner_name;

    /** 是否有密码保护 */
    private Boolean has_password;

    /** 是否已过期 */
    private Boolean is_expired;

    /** 是否已撤销 */
    private Boolean is_revoked;

    /** 过期时间 */
    private LocalDateTime expires_at;

    /** 创建时间 */
    private LocalDateTime created_at;
}