package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标签 VO（含统计信息）
 */
@Data
public class TagVO {

    /** 标签ID */
    private Long tag_id;

    /** 标签名称 */
    private String tag_name;

    /** 标签颜色 */
    private String tag_color;

    /** 关联文件数量 */
    private Integer file_count;

    /** 关联文件夹数量 */
    private Integer folder_count;

    /** 创建时间 */
    private LocalDateTime tag_created_at;
}