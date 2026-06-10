package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文件数据类
 */
@Data
public class FileEntity implements Serializable {
    public enum FileStatus {
        active,
        deleted,
        trashed
    }
    private UUID id;
    private String name;
    private String type;
    private Long size;
    private UUID user_id;
    private LocalDateTime uploaded_time;
    private String checksum;
    private UUID node_id;
    private Integer total_chunks;
    private String storage_path;
    private FileStatus status;
}
