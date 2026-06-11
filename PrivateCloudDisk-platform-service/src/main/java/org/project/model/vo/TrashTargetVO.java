package org.project.model.vo;

import lombok.Data;
import org.project.model.entity.TrashTargetEntity;

import java.time.LocalDateTime;

/**
 * 回收站文件VO
 */
@Data
public class TrashTargetVO {
    private Long trash_id;
    private String target_id;
    private String target_name;
    private String file_type;
    private Long target_size;
    private TrashTargetEntity.TargetType target_type;
    private String original_node_id;
    private LocalDateTime deleted_at;
    private LocalDateTime expires_at;
}
