package org.project.model.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class QuotaEntity {
    private Long id;
    private UUID user_id;
    private Long total_capacity;
    private Long used_capacity;
    /** 预占容量（字节）：正在上传中尚未提交的文件容量，available = total - (used + released) */
    private Long released_capacity;
    private Integer file_count;
    private Integer version;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
