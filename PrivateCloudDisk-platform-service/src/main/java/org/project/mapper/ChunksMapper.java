package org.project.mapper;

import org.project.model.entity.UploadsChunkEntity;

import java.util.List;

public interface ChunksMapper {
    /**
     * 根据上传会话ID和分块索引查询分块数据
     * @param uploads_id 上传会话ID
     * @param chunk_index 分块索引
     * @return 分块数据
     */
    UploadsChunkEntity findChunkByUploadsIdAndChunkIndex(String uploads_id, int chunk_index);

    /**
     * 根据上传会话ID查询所有分块数据
     * @param uploads_id 上传会话ID
     * @return 分块数据列表
     */
    List<UploadsChunkEntity> findChunkByUploadsId(String uploads_id);

    /**
     * 插入上传分块数据
     * @param chunkData 分块数据
     * @return
     */
    int insertUploadsChunk(UploadsChunkEntity chunkData);
}
