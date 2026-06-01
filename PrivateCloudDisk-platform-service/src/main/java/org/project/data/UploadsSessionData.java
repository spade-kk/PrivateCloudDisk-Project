package org.project.data;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 上传会话数据类
 */
@Data
public class UploadsSessionData implements Serializable {
    public enum UploadsSessionStatus {
        uploading,
        merging,
        completed,
        failed
    }
    public enum UploadsSessionEvent {
        Merge,
        Complete,
        Fail
    }

    private String uploads_id;
    private String user_id;
    private String file_name;
    private LocalDateTime starting_time;
    private LocalDateTime endding_time;
    private Long file_size;
    private Integer chunks_max_size;
    private Integer total_chunks;
    private String file_checksum;
    private String file_type;
    private String node_id;
    private UploadsSessionStatus status;
}
