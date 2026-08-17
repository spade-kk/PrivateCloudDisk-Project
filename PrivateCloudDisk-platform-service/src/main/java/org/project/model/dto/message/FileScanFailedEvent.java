package org.project.model.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件扫毒失败事件消息
 * <p>文件扫毒发现恶意文件后发布。
 * 消费者：主业务服务 → 回滚配额（released -= fileSize）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileScanFailedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件ID（幂等去重） */
    private String eventId;

    /** 文件ID */
    private String fileId;

    /** 文件名称 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型 */
    private String fileType;

    /** 用户ID */
    private String userId;

    /** 空间管理能力全量集成（需求五-9）：用于失败回滚恢复空间配额上下文。 */
    private String spaceId;

    /** 上传会话ID */
    private String uploadsSessionId;

    /** 事件发生时间 */
    private LocalDateTime eventTime;
}
