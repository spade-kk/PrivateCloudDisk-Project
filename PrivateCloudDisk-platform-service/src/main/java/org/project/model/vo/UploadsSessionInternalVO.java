package org.project.model.vo;

import lombok.Data;
import org.project.model.entity.UploadsSessionEntity;

import java.time.LocalDateTime;

@Data
public class UploadsSessionInternalVO {
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
    private UploadsSessionEntity.UploadsSessionStatus status;
}
