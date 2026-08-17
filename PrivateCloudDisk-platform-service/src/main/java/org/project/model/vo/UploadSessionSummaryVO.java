package org.project.model.vo;

import lombok.Data;
import org.project.model.entity.UploadsSessionEntity;

import java.time.LocalDateTime;

/**
 * 活跃上传会话的脱敏摘要。
 *
 * 需求：上传并发状态查询接口不得返回 checksum、用户内部字段或存储路径等敏感参数。
 */
@Data
public class UploadSessionSummaryVO {
    private String uploads_id;
    private String file_name;
    private Long file_size;
    private Integer total_chunks;
    private UploadsSessionEntity.UploadsSessionStatus status;
    private LocalDateTime starting_time;
    private LocalDateTime endding_time;
}
