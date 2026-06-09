package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件收藏VO
 */
@Data
public class FileStarVO {
    private Long star_id;
    private String file_id;
    private LocalDateTime starred_at;
}
