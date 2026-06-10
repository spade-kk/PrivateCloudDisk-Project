package org.project.mapper;

import org.apache.ibatis.annotations.Param;
import org.project.model.entity.UploadsChunkEntity;

import java.util.List;
import java.util.UUID;

public interface ChunksMapper {
    /**
     * 根据上传会话ID和分块索引查询分块数据
     * @param uploads_id 上传会话ID
     * @param chunk_index 分块索引
     * @return 分块数据
     */
    UploadsChunkEntity findChunkByUploadsIdAndChunkIndex(@Param("uploads_id") UUID uploads_id, int chunk_index);

    /**
     * 根据上传会话ID查询所有分块数据
     * @param uploads_id 上传会话ID
     * @return 分块数据列表
     */
    List<UploadsChunkEntity> findChunkByUploadsId(@Param("uploads_id") UUID uploads_id);

    /**
     * 插入上传分块数据
     * @param chunkData 分块数据
     * @return
     */
    int insertUploadsChunk(UploadsChunkEntity chunkData);
}
