package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InternalFileMetadataVO {
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
