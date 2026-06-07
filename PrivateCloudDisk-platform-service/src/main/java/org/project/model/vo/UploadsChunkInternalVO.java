package org.project.model.vo;

import lombok.Data;
import org.project.model.entity.UploadsChunkEntity;

import java.time.LocalDateTime;

@Data
public class UploadsChunkInternalVO {
    private String uploads_id;
    private Integer chunk_index;
    private UploadsChunkEntity.ChunkStatus chunk_status;
    private String chunk_storage_path;
    private LocalDateTime chunk_uploaded_time;
}
