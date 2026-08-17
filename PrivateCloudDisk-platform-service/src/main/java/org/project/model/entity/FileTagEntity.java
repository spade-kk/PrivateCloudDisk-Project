package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文件/文件夹标签关联实体（多对多）
 *
 * <p>一个文件/文件夹可以打多个标签，一个标签可以关联多个文件/文件夹。
 */
@Data
public class FileTagEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联ID */
    private Long ft_id;

    /** 用户ID（冗余，加速查询） */
    private UUID ft_user_id;

    /** 需求：空间管理能力全量集成（五-6），标签关联所属空间。 */
    private UUID ft_space_id;

    /** 标签ID */
    private Long ft_tag_id;

    /** 目标类型：file / folder */
    private String ft_target_type;

    /** 文件ID（target_type=file 时） */
    private UUID ft_file_id;

    /** 文件夹节点ID（target_type=folder 时） */
    private UUID ft_node_id;

    /** 打标签时间 */
    private LocalDateTime ft_tagged_at;

    // ---- 关联查询字段 ----

    /** 标签名称（JOIN 查询） */
    private String tag_name;

    /** 标签颜色（JOIN 查询） */
    private String tag_color;

    /** 文件名称（JOIN 查询，按标签查文件时使用） */
    private String file_name;

    /** 文件大小（JOIN 查询） */
    private Long file_size;

    /** 文件类型（JOIN 查询） */
    private String file_type;
}
