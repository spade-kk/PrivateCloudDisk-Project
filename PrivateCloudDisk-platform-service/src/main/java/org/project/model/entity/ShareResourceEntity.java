package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 分享资源实体（v2 新增）
 *
 * <p>表示一个分享链接中包含的单个资源（文件或文件夹）。
 * 一个分享链接可以有多个 ShareResourceEntity，支持分享多个文件和文件夹的组合。
 *
 * <p>设计：
 * <ul>
 *   <li>{@code resource_type} 区分文件（file）和文件夹（folder）</li>
 *   <li>{@code file_id} 和 {@code node_id} 根据 resource_type 二选一，通过 CHECK 约束保证</li>
 *   <li>未来可扩展 resource_type 支持更多类型（相册、标签、搜索结果等）</li>
 * </ul>
 */
@Data
public class ShareResourceEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum ResourceType {
        file, folder
    }

    /** 分享资源ID（主键） */
    private UUID share_resource_id;

    /** 所属分享链接ID */
    private UUID share_id;

    /** 需求：空间管理能力全量集成（五-4），资源关系所属空间。 */
    private UUID space_id;

    /** 资源类型：file / folder */
    private ResourceType resource_type;

    /** 文件ID（resource_type=file 时有值） */
    private UUID file_id;

    /** 文件夹节点ID（resource_type=folder 时有值） */
    private UUID node_id;

    /** 创建时间 */
    private LocalDateTime created_at;

    // ---- 关联查询字段 ----

    /** 资源名称（文件/文件夹名称） */
    private String resource_name;

    /** 资源大小（字节，文件时有值，文件夹为 0） */
    private Long resource_size;

    /** 文件类型（MIME 或扩展名，仅文件时有值） */
    private String file_type;
}
