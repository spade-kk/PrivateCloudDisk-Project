package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileVO {
    private String id;
    private String name;
    private String type;
    private Long size;
    private LocalDateTime uploaded_time;
    private String node_id;
    private Integer total_chunks;
}
