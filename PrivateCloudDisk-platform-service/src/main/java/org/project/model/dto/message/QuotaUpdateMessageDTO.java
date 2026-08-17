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
    private String message_id;
    
    /** 用户ID */
    private String user_id;

    /** 空间管理能力全量集成（需求五-9/10）：为空时兼容历史个人配额消息。 */
    private String space_id;
    
    /** 更新类型：FILE_UPLOAD-文件上传, FILE_DELETE-文件删除, RECALCULATE-重新计算 */
    private String update_id;
    
    /** 变更字节数（正为增加，负为减少） */
    private Long change_bytes;
    
    /** 文件数量变更（正为增加，负为减少） */
    private Integer change_file_count;
    
    /** 创建时间 */
    private LocalDateTime create_at;
    
    /** 额外参数（JSON格式） */
    private String extra_params;
}
