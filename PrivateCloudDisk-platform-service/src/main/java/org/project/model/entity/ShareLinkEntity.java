package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 分享链接实体（v2 — 多资源分享模型）
 *
 * <p>设计变更：v1 模型为单目标分享（share_target_type + share_file_id / share_node_id），
 * 仅支持分享一个文件或一个文件夹。v2 模型将分享目标抽象为独立的资源关系表
 * {@link ShareResourceEntity}，一个分享链接可包含多个文件和文件夹的组合。
 *
 * <p>分享链接本身只负责：生命周期管理、访问控制、密码保护、过期时间。
 * 分享的具体内容由 {@link ShareResourceEntity} 定义。
 */
@Data
public class ShareLinkEntity implements Serializable {

    private static final long serialVersionUID = 2L;

    public enum ShareStatus {
        active, revoked, expired
    }

    /** 分享ID（内部主键） */
    private UUID share_id;

    /** 分享访问令牌（对外暴露） */
    private String share_token;

    /** 分享者用户ID */
    private UUID share_owner_id;

    /** 分享名称 */
    private String share_name;

    /** 分享说明（可包含受限富文本，展示端必须净化后渲染） */
    private String share_description;

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

    /** 分享者名称 */
    private String owner_name;

    /** 分享资源列表（关联查询时填充） */
    private List<ShareResourceEntity> resources;

    /** 资源数量（关联查询时填充） */
    private Integer resource_count;
}
