package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件/文件夹收藏 VO
 */
@Data
public class FileStarVO {
    /** 收藏ID */
    private Long star_id;

    /** 收藏目标类型：file / folder */
    private String target_type;

    /** 目标ID（文件ID或文件夹节点ID） */
    private String target_id;

    /** 目标名称 */
    private String target_name;

    /** 目标大小（字节） */
    private Long target_size;

    /** 文件类型（仅文件时有值） */
    private String file_type;

    /** 文件状态（仅文件时有值） */
    private String file_status;

    /** 收藏时间 */
    private LocalDateTime starred_at;
}