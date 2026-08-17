package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享公开信息 VO（访问分享链接时返回，不含资源列表，不含密码，v2）
 *
 * <p>安全设计：此接口仅返回分享链接的基本信息（名称、创建者、是否需要密码等），
 * 不返回资源列表。资源列表需要通过提取码验证后获取访问令牌才能查看。
 */
@Data
public class ShareAccessInfoVO {
    /** 分享访问令牌 */
    private String share_token;

    /** 分享名称 */
    private String share_name;

    /** 分享说明（公开展示，客户端需净化富文本） */
    private String share_description;

    /** 分享者名称 */
    private String owner_name;

    /** 是否有密码保护 */
    private Boolean has_password;

    /** 公开分享是否允许获取实际文件内容。 */
    private Boolean allow_download;

    /** 是否已过期 */
    private Boolean is_expired;

    /** 是否已撤销 */
    private Boolean is_revoked;

    /** 过期时间 */
    private LocalDateTime expires_at;

    /** 创建时间 */
    private LocalDateTime created_at;

    /** 资源数量 */
    private Integer resource_count;
}
