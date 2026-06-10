package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 回收站文件实体
 */
@Data
public class TrashFileEntity implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 回收站记录ID */
    private Long trash_id;
    
    /** 原文件ID */
    private UUID file_id;
    
    /** 用户ID */
    private UUID user_id;
    
    /** 文件名称 */
    private String file_name;
    
    /** 文件类型 */
    private String file_type;
    
    /** 文件大小 */
    private Long file_size;
    
    /** 原节点ID */
    private UUID original_node_id;
    
    /** 文件存储路径 */
    private String storage_path;
    
    /** 文件校验和 */
    private String file_checksum;
    
    /** 删除时间 */
    private LocalDateTime deleted_at;
    
    /** 过期时间（自动彻底删除时间） */
    private LocalDateTime expires_at;
}
