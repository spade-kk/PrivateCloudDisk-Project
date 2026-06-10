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
    private Integer file_count;
    private Integer version;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
