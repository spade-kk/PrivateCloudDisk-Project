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
        completed,
        canceled
    }
    public enum UploadsSessionEvent {
        Merge,
        Cancel
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
    /**
     * 需求：空间管理能力全量集成（五-2/六）。
     * 上传会话从创建到合并、增强事件全程携带该空间 ID。
     */
    private UUID space_id;
    /** 仅内部流水线查询使用，用于决定物理文件命名是否增加空间前缀。 */
    private String space_type;
}
