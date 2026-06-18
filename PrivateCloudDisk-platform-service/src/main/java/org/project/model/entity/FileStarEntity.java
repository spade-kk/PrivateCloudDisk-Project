package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文件/文件夹收藏实体
 */
@Data
public class FileStarEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum TargetType {
        file, folder
    }

    /** 收藏ID */
    private Long star_id;

    /** 用户ID */
    private UUID user_id;

    /** 收藏目标类型：file / folder */
    private TargetType target_type;

    /** 文件ID（收藏文件时） */
    private UUID file_id;

    /** 文件夹节点ID（收藏文件夹时） */
    private UUID node_id;

    /** 收藏时间 */
    private LocalDateTime starred_at;

    // ---- 关联查询字段（JOIN 文件/文件夹信息） ----

    /** 目标名称（文件或文件夹名称） */
    private String target_name;

    /** 目标大小（文件大小或文件夹累计大小） */
    private Long target_size;

    /** 文件类型（仅文件） */
    private String file_type;

    /** 文件状态（仅文件） */
    private String file_status;
}