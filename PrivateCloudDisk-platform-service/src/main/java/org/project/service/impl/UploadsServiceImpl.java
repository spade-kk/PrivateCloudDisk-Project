package org.project.service.impl;

import org.project.context.SpaceContextHolder;
import org.opensearch.search.builder.SearchSourceBuilderException;
import org.project.config.RabbitMQConifgure;
import org.project.mapper.FileMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.mapper.SpaceMapper;
import org.project.model.entity.SpaceEntity;
import org.project.model.dto.LazyUploadSessionResponse;
import org.project.model.dto.message.UploadSessionDeleteEvent;
import org.project.model.dto.message.UploadSessionDeletedEvent;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.UploadsChunkEntity;
import org.project.model.entity.UploadsSessionEntity;
import org.project.model.vo.UploadSessionConcurrencyVO;
import org.project.model.vo.UploadSessionSummaryVO;
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
    /** 上传会话并发上限；前端只把服务端返回值作为观测，不替代服务端校验。 */
    private static final int MAX_ACTIVE_UPLOAD_SESSIONS = 12;
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
    @Autowired
    private SpaceMapper spaceMapper;

    @Override
    @Transactional
    public UUID createPublicUploadsSession(UUID spaceId, int totalChunks, long fileSize, String checksum,
                                            int chunkMaxSize, String fileName, String fileType,
                                            UUID userId, UUID nodeId, String clientIp) {
        SpaceEntity space = spaceMapper.findById(spaceId);
        if (space == null || !"public".equals(space.getSpaceType()) || !"active".equals(space.getSpaceStatus())
                || !Boolean.TRUE.equals(space.getAllowPublicUpload())) {
            throw new OverstepAuthorityException("该公开仓库未开放上传");
        }
        SpaceContextHolder.SpaceContext previous = SpaceContextHolder.get();
        SpaceContextHolder.set(new SpaceContextHolder.SpaceContext(spaceId, userId, space.getSpaceName(), "public_uploader", true, false));
        try {
            // 原有分片校验、配额预占、父目录锁定和 MQ 事件均不改动，仅替换空间上下文。
            return createUploadsSession(totalChunks, fileSize, checksum, chunkMaxSize, fileName, fileType, userId, nodeId, clientIp);
        } finally {
            if (previous == null) SpaceContextHolder.clear(); else SpaceContextHolder.set(previous);
        }
    }

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
        if(activeUploads.size() >= MAX_ACTIVE_UPLOAD_SESSIONS) {
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
        /*
         * 需求：空间管理能力全量集成（五-2/9）。
         * 上传会话是后续合并和 MQ 流水线恢复空间上下文的权威来源。
         */
        uploadsSessionData.setSpace_id(SpaceContextHolder.getSpaceId());

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
        /*
         * 空间管理能力全量集成（需求五-2/9）：
         * 原行为内部合并回调是新请求线程，HTTP 空间上下文已经丢失，创建文件会写入 NULL；
         * 新行为从上传会话恢复空间上下文，仅包裹原 createMergingFile 调用，其他上传逻辑不变。
         */
        SpaceContextHolder.SpaceContext previousContext = SpaceContextHolder.get();
        if (uploadsSessionData.getSpace_id() != null) {
            SpaceContextHolder.set(new SpaceContextHolder.SpaceContext(
                    uploadsSessionData.getSpace_id(),
                    uploadsSessionData.getUser_id(),
                    "",
                    "owner",
                    true,
                    "personal".equalsIgnoreCase(uploadsSessionData.getSpace_type())
            ));
        }
        UUID file_id;
        try {
            file_id = fileService.createMergingFile(
                    uploadsSessionData.getFile_name(),
                    uploadsSessionData.getFile_type(),
                    uploadsSessionData.getFile_size(),
                    uploadsSessionData.getUser_id(),
                    uploadsSessionData.getNode_id(),
                    uploadsSessionData.getFile_checksum(),
                    uploadsSessionData.getTotal_chunks()
            );
        } finally {
            if (previousContext == null) {
                SpaceContextHolder.clear();
            } else {
                SpaceContextHolder.set(previousContext);
            }
        }

        /*
         * REQ-UPLOAD-SESSION-STATE-2026-07：
         * 原行为：创建文件记录后把上传会话置为 merging，并由后处理回调继续修改/删除会话。
         * 新行为：所有分块校验通过且合并任务已触发的边界立即置为 completed；后续文件状态
         * （merged/scanning/active/failed）只属于文件后处理流水线，不再回写上传会话。
         * 影响范围：上传会话统计、Storage Worker 合并回调和前端会话状态；文件业务状态不变。
         */
        uploadsMapper.updateUploadsSessionStatusById(UploadsSessionEntity.UploadsSessionStatus.completed, uploads_id);

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
        /*
         * REQ-UPLOAD-SESSION-STATE-2026-07：
         * 原行为：只接受 merging，并在文件状态回调中删除上传会话。
         * 新行为：会话在 uploadsMerging 返回前已是 completed；本接口只更新文件的 merged
         * 状态，不负责会话生命周期。分块清理完成后由 deleteUploadsSessionAfterMerge 删除会话。
         * 这样文件后处理状态与上传传输状态彻底分离，同时兼容旧 gRPC/HTTP 回调入口。
         */
        UploadsSessionEntity uploadsSessionData = uploadsMapper.findUploadsSessionById(uploads_id);
        if (uploadsSessionData != null
                && uploadsSessionData.getStatus() != UploadsSessionEntity.UploadsSessionStatus.completed) {
            throw new UploadsSessionStatusException("上传会话状态错误");
        }

        // 调用文件服务更新文件状态 合并成功（文件后处理状态，不修改上传会话状态）
        fileService.mergedFile(file_id, file_storage_path, user_id);
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
                .spaceId(uploadsSessionData.getSpace_id())
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
     * 文件存储服务完成取消/过期分块删除后，发布配额回滚事件并删除会话记录。
     * 上传会话不再写入 deleted 状态；删除记录本身即表示清理完成。
     */
    @Transactional
    public void markUploadSessionDeleted(UUID uploads_id) {
        UploadsSessionEntity uploadsSessionData = uploadsMapper.findUploadsSessionById(uploads_id);
        if (uploadsSessionData == null) {
            log.warn("上传会话不存在，跳过: uploadsId={}", uploads_id);
            return;
        }

        if (uploadsSessionData.getStatus() == UploadsSessionEntity.UploadsSessionStatus.completed) {
            log.info("上传会话已完成，不走取消清理配额回滚: uploadsId={}", uploads_id);
            return;
        }

        // 取消/过期清理统一落到 canceled，随后删除记录；不再产生 deleted 状态。
        if (uploadsSessionData.getStatus() == UploadsSessionEntity.UploadsSessionStatus.uploading) {
            uploadsMapper.updateUploadsSessionStatusById(
                    UploadsSessionEntity.UploadsSessionStatus.canceled, uploads_id);
        }

        // 发布 uploads.session.deleted 事件 → 主业务服务监听并释放配额
        UploadSessionDeletedEvent deletedEvent = UploadSessionDeletedEvent.builder()
                .eventId("EVT-DELETED-" + uploads_id.toString())
                .uploadsSessionId(uploads_id)
                .userId(uploadsSessionData.getUser_id())
                .spaceId(uploadsSessionData.getSpace_id())
                .fileName(uploadsSessionData.getFile_name())
                .fileSize(uploadsSessionData.getFile_size())
                .eventTime(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConifgure.UPLOADS_EVENT_EXCHANGE,
                RabbitMQConifgure.ROUTING_UPLOADS_SESSION_DELETED,
                deletedEvent
        );

        // 删除会话时由数据库外键级联删除分块元数据；物理分块已由 Storage Worker 清理。
        uploadsMapper.deleteUploadsSessionById(uploads_id);
        log.info("上传会话清理完成，已发布配额回滚事件: uploadsId={}", uploads_id);
    }

    @Override
    @Transactional
    public void deleteUploadsSessionAfterMerge(UUID uploads_id) {
        UploadsSessionEntity session = uploadsMapper.findUploadsSessionById(uploads_id);
        if (session == null) {
            return;
        }
        if (session.getStatus() != UploadsSessionEntity.UploadsSessionStatus.completed) {
            throw new UploadsSessionStatusException("仅完成状态的上传会话允许合并清理");
        }
        // 成功合并不回滚配额；文件可用事件负责提交预占配额，当前操作只删除会话和分块元数据。
        uploadsMapper.deleteUploadsSessionById(uploads_id);
        log.info("文件合并后上传会话清理完成: uploadsId={}", uploads_id);
    }

    @Override
    public UploadSessionConcurrencyVO queryUploadConcurrency(UUID user_id) {
        List<UploadsSessionEntity> activeUploads = uploadsMapper.findUserActiveUploadsSession(user_id);
        UploadSessionConcurrencyVO result = new UploadSessionConcurrencyVO();
        result.setMax_concurrent_sessions(MAX_ACTIVE_UPLOAD_SESSIONS);
        result.setActive_session_count(activeUploads.size());
        result.setRemaining_concurrent_sessions(
                Math.max(0, MAX_ACTIVE_UPLOAD_SESSIONS - activeUploads.size()));
        result.setSessions(activeUploads.stream().map(session -> {
            UploadSessionSummaryVO summary = new UploadSessionSummaryVO();
            summary.setUploads_id(session.getUploads_id().toString());
            summary.setFile_name(session.getFile_name());
            summary.setFile_size(session.getFile_size());
            summary.setTotal_chunks(session.getTotal_chunks());
            summary.setStatus(session.getStatus());
            summary.setStarting_time(session.getStarting_time());
            summary.setEndding_time(session.getEndding_time());
            return summary;
        }).toList());
        return result;
    }

    @Transactional
    public void activateFileStatus(UUID file_id, UUID user_id) {
        activateFileStatusWithFinalContent(file_id, user_id, null, null, null);
    }

    @Override
    @Transactional
    public void activateFileStatusWithFinalContent(
            UUID file_id,
            UUID user_id,
            String storagePath,
            String checksum,
            Long fileSize) {
        FileEntity fileData = fileMapper.findUserFileById(file_id, user_id);
        if (fileData == null || fileMapper.isFileDeleted(file_id, user_id)) {
            throw new FileNotExistException();
        }

        /*
         * 插件生态生命周期：
         * 原行为重试已激活文件会抛状态异常；新行为允许相同最终内容幂等重试，
         * 但拒绝用不同 checksum/path 覆盖 active 文件，从而落实“激活后内容冻结”。
         */
        if (fileData.getStatus().equals(FileEntity.FileStatus.active)) {
            boolean checksumMatches = checksum == null || checksum.equalsIgnoreCase(fileData.getChecksum());
            boolean pathMatches = storagePath == null || storagePath.equals(fileData.getStorage_path());
            boolean sizeMatches = fileSize == null || fileSize.equals(fileData.getSize());
            if (checksumMatches && pathMatches && sizeMatches) {
                return;
            }
            throw new FileStatusException("文件已激活，禁止修改原始内容");
        }

        if(!fileData.getStatus().equals(FileEntity.FileStatus.merged)) {
            throw new FileStatusException("激活失败文件状态异常");
        }

        //Active Node Unlock
        folderNodeMapper.updateFolderNodeStatusByIdAndUserId(FolderNodeEntity.NodeStatus.active, fileData.getNode_id(), user_id);
        if (fileMapper.activateWithFinalContent(
                file_id, user_id, storagePath, checksum, fileSize) != 1) {
            throw new FileStatusException("文件最终内容提交失败");
        }
    }

    @Override
    @Transactional
    public LazyUploadSessionResponse createLazyUploadSession(
            int total_chunks, long file_size, String file_checksum, int chunks_max_size,
            String file_name, String file_type, UUID user_id,
            UUID parentNodeId, String relativePath, String breadcrumbPath, String clientIp) {

        UUID targetNodeId;

        // 确定目标节点：优先 relative_path > breadcrumb_path > parentNodeId 直接使用
        if (relativePath != null && !relativePath.isBlank()) {
            // 模式1：node_id + 相对路径
            if (parentNodeId == null) {
                throw new IllegalArgumentException("node_id + 相对路径模式需要 parent_node_id");
            }
            targetNodeId = directoryTreeService.ensureFolderPath(user_id, parentNodeId, relativePath);
        } else if (breadcrumbPath != null && !breadcrumbPath.isBlank()) {
            // 模式2：纯面包屑路径
            targetNodeId = directoryTreeService.ensureFolderPath(user_id, breadcrumbPath);
        } else {
            // 模式3：普通单文件上传（使用已有 node_id）
            if (parentNodeId == null) {
                throw new IllegalArgumentException("必须提供 parent_node_id、relative_path 或 breadcrumb_path");
            }
            targetNodeId = parentNodeId;
        }

        // 创建上传会话（复用现有逻辑）
        UUID uploadsId = createUploadsSession(
                total_chunks, file_size, file_checksum, chunks_max_size,
                file_name, file_type, user_id, targetNodeId, clientIp);

        UploadSessionConcurrencyVO concurrency = queryUploadConcurrency(user_id);
        return LazyUploadSessionResponse.of(
                uploadsId.toString(),
                targetNodeId.toString(),
                concurrency.getMax_concurrent_sessions(),
                concurrency.getActive_session_count(),
                concurrency.getRemaining_concurrent_sessions());
    }
}
