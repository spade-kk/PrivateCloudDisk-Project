package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 回收站文件实体
 */
@Data
public class TrashTargetEntity implements Serializable {
    
    private static final long serialVersionUID = 1L;

    public enum TargetType {
        FILE,
        FOLDER
    }

    /** 回收站记录ID */
    private Long trash_id;
    
    /** 原文件ID */
    private UUID target_id;
    
    /** 用户ID */
    private UUID user_id;
    
    /** 文件名称 */
    private String target_name;

    /** 目标类型 */
    private TargetType target_type;

    /** 文件类型 */
    private String file_type;

    /** 文件大小 文件夹大小为0 */
    private Long target_size;
    
    /** 原节点ID */
    private UUID original_node_id;

    /** 删除时间 */
    private LocalDateTime deleted_at;
    
    /** 过期时间（自动彻底删除时间） */
    private LocalDateTime expires_at;
}
