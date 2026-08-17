package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户标签实体
 *
 * <p>用户可自定义标签，用于分类管理文件/文件夹。
 * 常见标签：合同、设计稿、项目A、财务报告、机密文件 等。
 */
@Data
public class TagEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 标签ID */
    private Long tag_id;

    /** 所属用户ID */
    private UUID tag_user_id;

    /** 需求：空间管理能力全量集成（五-6），标签定义所属空间。 */
    private UUID tag_space_id;

    /** 标签名称 */
    private String tag_name;

    /** 标签颜色（HEX，如 #3B82F6） */
    private String tag_color;

    /** 创建时间 */
    private LocalDateTime tag_created_at;

    // ---- 统计字段（关联查询） ----

    /** 该标签关联的文件数量 */
    private Integer file_count;

    /** 该标签关联的文件夹数量 */
    private Integer folder_count;
}
