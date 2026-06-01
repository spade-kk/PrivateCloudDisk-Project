package org.project.data;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分块数据类
 */
@Data
public class UploadsChunkData implements Serializable {
    /**
     * 分块状态枚举类
     */
    public enum ChunkStatus {
        pending,
        uploading,
        uploaded,
        failed
    }

    private String uploads_id;
    private Integer chunk_index;
    private ChunkStatus chunk_status;
    private String chunk_storage_path;
    private LocalDateTime chunk_uploaded_time;
}
