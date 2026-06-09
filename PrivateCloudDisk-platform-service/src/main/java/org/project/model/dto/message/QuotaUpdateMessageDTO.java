package org.project.model.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 配额更新消息DTO
 * 用于处理配额更新任务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaUpdateMessageDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 消息ID */
    private String messageId;
    
    /** 用户ID */
    private String userId;
    
    /** 更新类型：FILE_UPLOAD-文件上传, FILE_DELETE-文件删除, RECALCULATE-重新计算 */
    private String updateType;
    
    /** 变更字节数（正为增加，负为减少） */
    private Long changeBytes;
    
    /** 文件数量变更（正为增加，负为减少） */
    private Integer changeFileCount;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 额外参数（JSON格式） */
    private String extraParams;
}
