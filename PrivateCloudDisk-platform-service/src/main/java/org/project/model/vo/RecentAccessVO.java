package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 最近访问记录 VO
 */
@Data
public class RecentAccessVO {

    /** 记录ID */
    private Long ra_id;

    /** 目标ID（文件ID 或 文件夹节点ID） */
    private String target_id;

    /** 目标类型：file / folder */
    private String target_type;

    /** 访问类型：upload / download / open */
    private String access_type;

    /** 文件/文件夹名称 */
    private String target_name;

    /** 文件大小 */
    private Long target_size;

    /** 文件类型 */
    private String file_type;

    /** 访问时间 */
    private LocalDateTime accessed_at;
}