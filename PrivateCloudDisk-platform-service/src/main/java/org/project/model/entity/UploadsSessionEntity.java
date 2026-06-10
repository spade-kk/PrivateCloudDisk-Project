package org.project.model.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 上传会话数据类
 */
@Data
public class UploadsSessionEntity implements Serializable {
    public enum UploadsSessionStatus {
        uploading,
        merging,
        merge_failed,
        completed,
        canceled,
        calculating,
        calculating_failed,
        scanning,
        scanning_failed,
        processing,
        processing_failed
    }
    public enum UploadsSessionEvent {
        Merge,
        MergeFailed,
        Complete,
        CalculateFailed,
        Cancel,
        Calculate,
        Scan,
        ScanFailed,
        Process,
        ProcessFailed
    }

    private UUID uploads_id;
    private UUID user_id;
    private String file_name;
    private LocalDateTime starting_time;
    private LocalDateTime endding_time;
    private Long file_size;
    private Integer chunks_max_size;
    private Integer total_chunks;
    private String file_checksum;
    private String file_type;
    private UUID node_id;
    private UploadsSessionStatus status;
}
