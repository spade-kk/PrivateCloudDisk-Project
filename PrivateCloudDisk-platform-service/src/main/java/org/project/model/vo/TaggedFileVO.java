package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 带标签的文件/文件夹 VO
 */
@Data
public class TaggedFileVO {

    /** 目标ID */
    private String target_id;

    /** 目标类型 */
    private String target_type;

    /** 文件/文件夹名称 */
    private String target_name;

    /** 文件大小 */
    private Long target_size;

    /** 文件类型 */
    private String file_type;

    /** 打标签时间 */
    private LocalDateTime tagged_at;

    /** 关联的标签列表 */
    private List<TagVO> tags;
}