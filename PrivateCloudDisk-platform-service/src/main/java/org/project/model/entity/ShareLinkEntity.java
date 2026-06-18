package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 分享链接实体
 */
@Data
public class ShareLinkEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum TargetType {
        file, folder
    }

    public enum ShareStatus {
        active, revoked, expired
    }

    /** 分享ID（内部主键） */
    private UUID share_id;

    /** 分享访问令牌（对外暴露） */
    private String share_token;

    /** 分享者用户ID */
    private UUID share_owner_id;

    /** 分享目标类型：file / folder */
    private TargetType share_target_type;

    /** 分享的文件ID */
    private UUID share_file_id;

    /** 分享的文件夹节点ID */
    private UUID share_node_id;

    /** 分享名称 */
    private String share_name;

    /** 提取码（BCrypt 哈希） */
    private String share_password;

    /** 是否有密码保护 */
    private Boolean share_has_password;

    /** 过期时间 */
    private LocalDateTime share_expires_at;

    /** 浏览次数 */
    private Integer share_view_count;

    /** 分享状态 */
    private ShareStatus share_status;

    /** 创建时间 */
    private LocalDateTime share_created_at;

    /** 更新时间 */
    private LocalDateTime share_updated_at;

    // ---- 关联查询字段 ----

    /** 目标名称（文件或文件夹名称） */
    private String target_name;

    /** 目标大小（字节） */
    private Long target_size;

    /** 文件类型 */
    private String file_type;

    /** 分享者名称 */
    private String owner_name;
}