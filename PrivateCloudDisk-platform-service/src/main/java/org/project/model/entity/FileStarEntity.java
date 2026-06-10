package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文件收藏实体
 */
@Data
public class FileStarEntity implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 收藏ID */
    private Long star_id;
    
    /** 用户ID */
    private UUID user_id;

    /** 文件ID */
    private UUID file_id;
    
    /** 收藏时间 */
    private LocalDateTime starred_at;
}
