package org.project.data;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuotaData {
    private Long id;
    private String user_id;
    private Long total_capacity;
    private Long used_capacity;
    private Integer file_count;
    private Integer version;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
