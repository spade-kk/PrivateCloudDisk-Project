package org.project.model.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件删除消息DTO
 * 用于处理文件彻底删除的异步任务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDeleteMessageDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 消息ID */
    private String message_id;
    
    /** 文件ID */
    private String file_id;
    
    /** 用户ID */
    private String user_id;

    /**
     * 需求：空间管理能力全量集成（五-9）。
     * 文件永久删除消费者据此隔离预览资源、日志和物理文件定位。
     */
    private String space_id;
    
    /** 文件存储路径 */
    private String storage_path;
    
    /** 文件大小 */
    private Long file_size;
    
    /** 是否为回收站删除 */
    private Boolean from_trash;
    
    /** 创建时间 */
    private LocalDateTime create_at;
    
    /** 额外参数（JSON格式） */
    private String extra_param;
}
