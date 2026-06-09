package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站文件VO
 */
@Data
public class TrashFileVO {
    private Long trash_id;
    private String file_id;
    private String file_name;
    private String file_type;
    private Long file_size;
    private String original_node_id;
    private LocalDateTime deleted_at;
    private LocalDateTime expires_at;
}
