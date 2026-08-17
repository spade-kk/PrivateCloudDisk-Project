package org.project.model.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 上传会话删除事件消息
 * <p>上传会话取消后发布，或定时任务扫描过期会话后发布。
 * 消费者：文件存储服务（FastAPI Worker） → 删除物理分块文件 → 调用内部接口更新状态为 deleted
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadSessionDeleteEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件ID（幂等去重） */
    private String eventId;

    /** 上传会话ID */
    private UUID uploadsSessionId;

    /** 用户ID */
    private UUID userId;

    /** 空间管理能力全量集成（需求五-9）：上传会话所属空间。 */
    private UUID spaceId;

    /** 文件名称 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型 */
    private String fileType;

    /** 节点ID */
    private UUID nodeId;

    /** 事件发生时间 */
    private LocalDateTime eventTime;
}
