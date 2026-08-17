package org.project.model.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 上传会话已删除事件消息
 * <p>文件存储服务完成物理文件删除后，同步调用内部接口更新状态为deleted并发布此事件。
 * 消费者：主业务服务 → 释放配额（released -= fileSize）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadSessionDeletedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件ID（幂等去重） */
    private String eventId;

    /** 上传会话ID */
    private UUID uploadsSessionId;

    /** 用户ID */
    private UUID userId;

    /** 空间管理能力全量集成（需求五-9）：用于最终释放正确空间的预占额度。 */
    private UUID spaceId;

    /** 文件名称 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 事件发生时间 */
    private LocalDateTime eventTime;
}
