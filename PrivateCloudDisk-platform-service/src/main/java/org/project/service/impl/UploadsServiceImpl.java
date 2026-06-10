package org.project.service.impl;

import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.UploadsChunkEntity;
import org.project.model.entity.UploadsSessionEntity;
import org.project.mapper.ChunksMapper;
import org.project.mapper.UploadsMapper;
import org.project.service.DirectoryTreeService;
import org.project.service.FileService;
import org.project.service.UploadsService;
import org.project.service.ex.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UploadsServiceImpl implements UploadsService {
    @Autowired
    private UploadsMapper uploadsMapper;
    @Autowired
    private ChunksMapper chunksMapper;
    @Autowired
    private DirectoryTreeService directoryTreeService;
    @Autowired
    private FileService fileService;

    @Override
    public UUID createUploadsSession(int total_chunks, long file_size, String file_checksum, int chunks_max_size, String file_name, String file_type, UUID user_id, UUID node_id) {
        FolderNodeEntity folderNode = directoryTreeService.findUserFolderNodeIfExist(node_id, user_id);
        if(folderNode == null) {
            throw new NodeNotExistException("节点不存在");
        }

        // 检查同目录下是否已存在同名文件
        List<FileEntity> fileDataList = fileService.queryUserFilesByNodeId(node_id, user_id);
        for (FileEntity fileData : fileDataList) {
            if(fileData.getName().equals(file_name)) {
                throw new FileNameDuplicatedException("同目录下已存在同名文件");
            }
        }

        // 实现创建上传会话的逻辑
        UploadsSessionEntity uploadsSessionData = new UploadsSessionEntity();
        uploadsSessionData.setTotal_chunks(total_chunks);
        uploadsSessionData.setFile_size(file_size);
        uploadsSessionData.setFile_checksum(file_checksum);
        uploadsSessionData.setChunks_max_size(chunks_max_size);
        uploadsSessionData.setFile_name(file_name);
        uploadsSessionData.setFile_type(file_type);
        uploadsSessionData.setNode_id(node_id);
        uploadsSessionData.setUser_id(user_id);

        uploadsSessionData.setStatus(UploadsSessionEntity.UploadsSessionStatus.uploading);
        // 生成上传会话的ID
        UUID uploads_id = UUID.randomUUID();
        uploadsSessionData.setUploads_id(uploads_id);
        //设置创建上传会话的时间 失效时间为30分钟后
        uploadsSessionData.setStarting_time(LocalDateTime.now());
        uploadsSessionData.setEndding_time(LocalDateTime.now().plusMinutes(30));
        // 调用Mapper插入数据
        Integer rows = uploadsMapper.insertUploadsSession(uploadsSessionData);
        if(rows != 1) {
            throw new InsertException();
        }
        // 返回上传会话的ID
        return uploads_id;
    }

    @Override
    public boolean isValidUploadsSession(UUID uploads_id) {
        UploadsSessionEntity uploadsSessionData = uploadsMapper.findUploadsSessionById(uploads_id);
        if(uploadsSessionData == null) {
            return false;
        }
        if(uploadsSessionData.getEndding_time().isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }

    @Override
    @Cacheable(cacheNames = "uploadsSession", key = "#uploads_id")
    public UploadsSessionEntity queryUploadsSessionById(UUID uploads_id) {
        if(!isValidUploadsSession(uploads_id)) {
            throw new InvalidUploadsSessionException("上传会话无效");
        }

        return uploadsMapper.findUploadsSessionById(uploads_id);
    }

    @Override
    public UploadsChunkEntity queryChunkByUploadsIdAndChunkIndex(UUID uploads_id, int chunk_index) {
        if(!isValidUploadsSession(uploads_id)) {
            throw new InvalidUploadsSessionException("上传会话无效");
        }
        if(chunk_index <= 0 || chunk_index > uploadsMapper.findUploadsSessionById(uploads_id).getTotal_chunks()) {
            throw new InvalidChunkIndexException("分块索引无效");
        }
        return chunksMapper.findChunkByUploadsIdAndChunkIndex(uploads_id, chunk_index);
    }

    @Override
    public void uploadsMerging(UUID uploads_id) {
        if(!isValidUploadsSession(uploads_id)) {
            throw new InvalidUploadsSessionException("上传会话无效");
        }
        UploadsSessionEntity uploadsSessionData = uploadsMapper.findUploadsSessionById(uploads_id);
        // 检查上传会话的状态是否正确
        if(uploadsSessionData.getStatus() != UploadsSessionEntity.UploadsSessionStatus.uploading) {
            throw new UploadsSessionStatusException("上传会话状态错误");
        }
        // 检查上传会话的分块是否全部上传完成
        List<UploadsChunkEntity> chunkDataList = chunksMapper.findChunkByUploadsId(uploads_id);
        if(chunkDataList.size() != uploadsSessionData.getTotal_chunks()) {
            throw new UploadsSessionNotCompleteException("上传会话分块未全部上传完成");
        }
        // 检查上传会话的分块状态是否全部完成
        for (UploadsChunkEntity chunk : chunkDataList) {
            if(chunk.getChunk_status() != UploadsChunkEntity.ChunkStatus.uploaded) {
                throw new UploadsSessionNotCompleteException("上传会话分块未全部上传完成");
            }
        }
        // 更新上传会话的状态为合并中
        uploadsMapper.updateUploadsSessionStatusById(UploadsSessionEntity.UploadsSessionStatus.merging, uploads_id);
    }

    @Override
    public void completeChunkUpload(UUID uploads_id, int chunk_index, String chunk_storage_path) {
        if(!isValidUploadsSession(uploads_id)) {
            throw new InvalidUploadsSessionException("上传会话无效");
        }
        if(chunk_index <= 0 || chunk_index > uploadsMapper.findUploadsSessionById(uploads_id).getTotal_chunks()) {
            throw new InvalidChunkIndexException("分块索引无效");
        }
        if(chunksMapper.findChunkByUploadsIdAndChunkIndex(uploads_id, chunk_index) != null) {
            throw new ChunkDuplicatedException("分块已上传");
        }

        UploadsChunkEntity chunkData = new UploadsChunkEntity();
        chunkData.setUploads_id(uploads_id);
        chunkData.setChunk_index(chunk_index);
        chunkData.setChunk_status(UploadsChunkEntity.ChunkStatus.uploaded);
        chunkData.setChunk_storage_path(chunk_storage_path);
        chunkData.setChunk_uploaded_time(LocalDateTime.now());
        // 调用Mapper插入数据
        Integer rows = chunksMapper.insertUploadsChunk(chunkData);
        if(rows!= 1) {
            throw new InsertException();
        }
    }

    @Override
    public void completeUploads(UUID uploads_id, String file_storage_path) {
        // 实现完成上传的逻辑
        if(!isValidUploadsSession(uploads_id)) {
                throw new InvalidUploadsSessionException("上传会话无效");
        }
        // 检查上传会话的状态是否正确
        UploadsSessionEntity uploadsSessionData = queryUploadsSessionById(uploads_id);
        if(uploadsSessionData.getStatus() != UploadsSessionEntity.UploadsSessionStatus.merging) {
            throw new UploadsSessionStatusException("上传会话状态错误");
        }
        // 调用文件服务创建文件
        UUID file_id = fileService.createFile(
                uploadsSessionData.getFile_name(),
                uploadsSessionData.getFile_type(),
                uploadsSessionData.getFile_size(),
                uploadsSessionData.getUser_id(),
                uploadsSessionData.getNode_id(),
                uploadsSessionData.getFile_checksum(),
                uploadsSessionData.getTotal_chunks(),
                file_storage_path
        );

        // 删除上传会话数据
        uploadsMapper.deleteUploadsSessionById(uploads_id);
    }
}
