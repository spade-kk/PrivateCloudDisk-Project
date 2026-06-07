package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件数据类
 */
@Data
public class FileEntity implements Serializable {
    private String id;
    private String name;
    private String type;
    private Long size;
    private String user_id;
    private LocalDateTime uploaded_time;
    private String checksum;
    private String node_id;
    private Integer total_chunks;
    private String storage_path;
}
