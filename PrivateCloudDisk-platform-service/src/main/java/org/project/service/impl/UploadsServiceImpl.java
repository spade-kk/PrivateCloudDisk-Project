package org.project.service.impl;

import org.opensearch.search.builder.SearchSourceBuilderException;
import org.project.config.RabbitMQConifgure;
import org.project.mapper.FileMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.model.dto.message.UploadSessionDeleteEvent;
import org.project.model.dto.message.UploadSessionDeletedEvent;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.UploadsChunkEntity;
import org.project.model.entity.UploadsSessionEntity;
import org.project.mapper.ChunksMapper;
import org.project.mapper.UploadsMapper;
import org.project.security.ApiAbuseProtectionService;
import org.project.service.DirectoryTreeService;
import org.project.service.FileService;
import org.project.service.UploadsService;
import org.project.service.UserQuotaService;
import org.project.service.ex.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
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
    @Autowired
    private FileMapper fileMapper;
    @Autowired
    private FolderNodeMapper folderNodeMapper;
    @Autowired
    private ApiAbuseProtectionService apiAbuseProtectionService;
    @Autowired
    private UserQuotaService userQuotaService;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public UUID createUploadsSession(int total_chunks, long file_size, String file_checksum, int chunks_max_size, String file_name, String file_type, UUID user_id, UUID node_id, String clientIp) {
        // 防滥用检查
        apiAbuseProtectionService.checkUploadSessionCreate(
                user_id.toString(), node_id.toString(), clientIp);

        FolderNodeEntity folderNode = directoryTreeService.findUserFolderNodeIfExist(node_id, user_id);
        if(folderNode == null) {
            throw new NodeNotExistException("节点不存在");
        }

        // 检查同目录下是否已存在同名文件
        List<FileEntity> fileDataList = fileMapper.findUserExistFilesByNodeId(node_id, user_id);
        for (FileEntity fileData : fileDataList) {
            if(fileData.getName().equals(file_name)) {
                throw new FileNameDuplicatedException("同目录下已存在同名文件");
            }
        }

        //检查当前用户的上传会话活跃的是否超过 MAX 限制
        List<UploadsSessionEntity> activeUploads = uploadsMapper.findUserActiveUploadsSession(user_id);
        if(activeUploads.size() >= 12) {
            throw new ServiceException("超过同时最大并发上传文件限制");
        }

        // ==================== 配额预占 ====================
        // 在创建上传会话前预占配额容量（released += fileSize）
        userQuotaService.preCommitQuota(user_id, file_size);

        //Lock Parent Node
        folderNodeMapper.updateFolderNodeStatusByIdAndUserId(FolderNodeEntity.NodeStatus.lock, node_id, user_id);

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
    public UUID uploadsMerging(UUID uploads_id) {
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
        // 调用文件服务创建文件
        UUID file_id = fileService.createMergingFile(
                uploadsSessionData.getFile_name(),
                uploadsSessionData.getFile_type(),
                uploadsSessionData.getFile_size(),
                uploadsSessionData.getUser_id(),
                uploadsSessionData.getNode_id(),
                uploadsSessionData.getFile_checksum(),
                uploadsSessionData.getTotal_chunks()
        );

        // 更新上传会话的状态为合并中
        uploadsMapper.updateUploadsSessionStatusById(UploadsSessionEntity.UploadsSessionStatus.merging, uploads_id);

        return file_id;
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
    @Transactional
    public void completeUploads(UUID uploads_id, UUID file_id, String file_storage_path, UUID user_id) {
        // 实现完成上传的逻辑
        if(!isValidUploadsSession(uploads_id)) {
                throw new InvalidUploadsSessionException("上传会话无效");
        }
        // 检查上传会话的状态是否正确
        UploadsSessionEntity uploadsSessionData = queryUploadsSessionById(uploads_id);
        if(uploadsSessionData.getStatus() != UploadsSessionEntity.UploadsSessionStatus.merging) {
            throw new UploadsSessionStatusException("上传会话状态错误");
        }
        // 调用文件服务更新文件状态 合并成功
        fileService.mergedFile(file_id, file_storage_path, user_id);

        // 删除上传会话数据 会自动把关联的分块数据也删除
        uploadsMapper.deleteUploadsSessionById(uploads_id);
    }

    @Override
    @Transactional
    public void cancelUploadSession(UUID uploads_id, UUID user_id) {
        //取消上传会话
        UploadsSessionEntity uploadsSessionData = uploadsMapper.findUploadsSessionById(uploads_id);
        if (uploadsSessionData == null) {
            throw new InvalidUploadsSessionException("上传会话不存在");
        }

        // 检查上传会话状态：只有 uploading 状态才能取消
        if (uploadsSessionData.getStatus() != UploadsSessionEntity.UploadsSessionStatus.uploading) {
            throw new UploadsSessionStatusException("上传会话状态错误，当前状态: " + uploadsSessionData.getStatus());
        }

        // 验证用户权限
        if (!uploadsSessionData.getUser_id().equals(user_id)) {
            throw new OverstepAuthorityException("无权取消此上传会话");
        }

        // 步骤1: 更新上传会话状态为 canceled
        int rows = uploadsMapper.updateUploadsSessionStatusById(
                UploadsSessionEntity.UploadsSessionStatus.canceled, uploads_id);
        if (rows != 1) {
            throw new UpdateException("更新上传会话状态失败");
        }

        // 步骤2: 发布上传会话 delete 事件（文件存储服务监听，删除物理分块文件）
        UploadSessionDeleteEvent deleteEvent = UploadSessionDeleteEvent.builder()
                .eventId("EVT-DEL-" + uploads_id.toString())
                .uploadsSessionId(uploads_id)
                .userId(user_id)
                .fileName(uploadsSessionData.getFile_name())
                .fileSize(uploadsSessionData.getFile_size())
                .fileType(uploadsSessionData.getFile_type())
                .nodeId(uploadsSessionData.getNode_id())
                .eventTime(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConifgure.UPLOADS_EVENT_EXCHANGE,
                RabbitMQConifgure.ROUTING_UPLOADS_SESSION_DELETE,
                deleteEvent
        );

        log.info("上传会话取消，已发布delete事件: uploadsId={}, userId={}, fileName={}",
                uploads_id, user_id, uploadsSessionData.getFile_name());
    }

    /**
     * 文件存储服务完成物理文件删除后，同步调用此接口更新状态为 deleted
     * 并发布 uploads.session.deleted 事件通知主业务服务释放配额
     */
    @Transactional
    public void markUploadSessionDeleted(UUID uploads_id) {
        UploadsSessionEntity uploadsSessionData = uploadsMapper.findUploadsSessionById(uploads_id);
        if (uploadsSessionData == null) {
            log.warn("上传会话不存在，跳过: uploadsId={}", uploads_id);
            return;
        }

        if (uploadsSessionData.getStatus() == UploadsSessionEntity.UploadsSessionStatus.deleted) {
            log.warn("上传会话已标记为deleted，跳过: uploadsId={}", uploads_id);
            return;
        }

        // 更新状态为 deleted
        uploadsMapper.updateUploadsSessionStatusById(
                UploadsSessionEntity.UploadsSessionStatus.deleted, uploads_id);

        // 发布 uploads.session.deleted 事件 → 主业务服务监听并释放配额
        UploadSessionDeletedEvent deletedEvent = UploadSessionDeletedEvent.builder()
                .eventId("EVT-DELETED-" + uploads_id.toString())
                .uploadsSessionId(uploads_id)
                .userId(uploadsSessionData.getUser_id())
                .fileName(uploadsSessionData.getFile_name())
                .fileSize(uploadsSessionData.getFile_size())
                .eventTime(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConifgure.UPLOADS_EVENT_EXCHANGE,
                RabbitMQConifgure.ROUTING_UPLOADS_SESSION_DELETED,
                deletedEvent
        );

        log.info("上传会话标记为deleted，已发布deleted事件: uploadsId={}", uploads_id);
    }

    @Transactional
    public void activateFileStatus(UUID file_id, UUID user_id) {
        FileEntity fileData = fileMapper.findUserFileById(file_id, user_id);
        if (fileData == null || fileMapper.isFileDeleted(file_id, user_id)) {
            throw new FileNotExistException();
        }

        if(!fileData.getStatus().equals(FileEntity.FileStatus.merged)) {
            throw new FileStatusException("激活失败文件状态异常");
        }

        //Active Node Unlock
        folderNodeMapper.updateFolderNodeStatusByIdAndUserId(FolderNodeEntity.NodeStatus.active, fileData.getNode_id(), user_id);
        fileMapper.updateUserFileStatusById(file_id, FileEntity.FileStatus.active, user_id);
    }
}
