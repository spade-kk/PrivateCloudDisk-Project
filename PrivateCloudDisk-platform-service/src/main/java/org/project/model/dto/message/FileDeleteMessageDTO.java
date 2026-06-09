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
    private String messageId;
    
    /** 文件ID */
    private String fileId;
    
    /** 用户ID */
    private String userId;
    
    /** 文件存储路径 */
    private String storagePath;
    
    /** 文件大小 */
    private Long fileSize;
    
    /** 是否为回收站删除 */
    private Boolean fromTrash;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 额外参数（JSON格式） */
    private String extraParams;
}
