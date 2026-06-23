package org.project.model.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件合并失败事件消息
 * <p>文件分块合并失败后发布。
 * 消费者：主业务服务 → 回滚配额（released -= fileSize）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMergeFailedEvent implements Serializable {

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

    /** 上传会话ID */
    private String uploadsSessionId;

    /** 失败原因 */
    private String failReason;

    /** 事件发生时间 */
    private LocalDateTime eventTime;
}