package org.project.grpc.service;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.project.grpc.internal.storage.*;
import org.project.grpc.internal.storage.InternalStorageServiceGrpc.InternalStorageServiceImplBase;
import org.project.model.entity.FileEntity;
import org.project.model.entity.UploadsChunkEntity;
import org.project.model.entity.UploadsSessionEntity;
import org.project.model.vo.InternalFileMetadataVO;
import org.project.model.vo.UploadsChunkInternalVO;
import org.project.model.vo.UploadsSessionInternalVO;
import org.project.model.vo.VoMapper;
import org.project.service.FileService;
import org.project.service.UploadsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.UUID;

/**
 * InternalStorageService gRPC 服务实现。
 *
 * <p>将 {@link org.project.control.InternalStorageController} 的 HTTP REST 端点
 * 一对一映射为 gRPC RPC 方法，直接委托给现有业务服务层。
 *
 * <p>架构层次：
 * <pre>
 *   gRPC Client (storage-service)
 *         |
 *   [GrpcServerInterceptor]  ← 认证 + 日志 + 异常转换
 *         |
 *   InternalStorageGrpcService (本类)  ← 协议适配层
 *         |
 *   UploadsService / FileService  ← 现有业务服务层（零改动）
 * </pre>
 *
 * <p>设计原则：
 * <ul>
 *   <li>本类只做协议转换（Proto ↔ 业务对象），不包含业务逻辑</li>
 *   <li>所有业务逻辑复用现有 Service 层</li>
 *   <li>字符串类型的 UUID 在入口处统一转换为 java.util.UUID</li>
 *   <li>时间戳统一转换为 epoch millis（Long）</li>
 * </ul>
 */
@Slf4j
@Component
public class InternalStorageGrpcService extends InternalStorageServiceImplBase {

    @Autowired
    private UploadsService uploadsService;

    @Autowired
    private FileService fileService;

    // =====================================================================
    // 分片上传完成
    // =====================================================================

    @Override
    public void chunkComplete(ChunkCompleteRequest request,
                              StreamObserver<ChunkCompleteResponse> responseObserver) {
        try {
            uploadsService.completeChunkUpload(
                    UUID.fromString(request.getUploadsId()),
                    request.getChunkIndex(),
                    request.getStoragePath());

            ChunkCompleteResponse response = ChunkCompleteResponse.newBuilder()
                    .setCode(200)
                    .setMessage("OK")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] ChunkComplete 失败 | uploadsId={} | chunkIndex={}",
                    request.getUploadsId(), request.getChunkIndex(), e);
            responseObserver.onError(e);
        }
    }

    // =====================================================================
    // 查询上传会话
    // =====================================================================

    @Override
    public void getUploadSession(GetUploadSessionRequest request,
                                 StreamObserver<GetUploadSessionResponse> responseObserver) {
        try {
            UploadsSessionEntity entity = uploadsService.queryUploadsSessionById(
                    UUID.fromString(request.getUploadsId()));
            UploadsSessionInternalVO vo = VoMapper.toUploadsSessionInternalVO(entity);

            UploadSessionInfo info = UploadSessionInfo.newBuilder()
                    .setUploadsId(vo.getUploads_id() != null ? vo.getUploads_id() : "")
                    .setUserId(vo.getUser_id() != null ? vo.getUser_id() : "")
                    .setFileName(vo.getFile_name() != null ? vo.getFile_name() : "")
                    .setStartingTime(vo.getStarting_time() != null
                            ? vo.getStarting_time().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0)
                    .setEnddingTime(vo.getEndding_time() != null
                            ? vo.getEndding_time().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0)
                    .setFileSize(vo.getFile_size() != null ? vo.getFile_size() : 0)
                    .setChunksMaxSize(vo.getChunks_max_size() != null ? vo.getChunks_max_size() : 0)
                    .setTotalChunks(vo.getTotal_chunks() != null ? vo.getTotal_chunks() : 0)
                    .setFileChecksum(vo.getFile_checksum() != null ? vo.getFile_checksum() : "")
                    .setFileType(vo.getFile_type() != null ? vo.getFile_type() : "")
                    .setNodeId(vo.getNode_id() != null ? vo.getNode_id() : "")
                    .setStatus(mapUploadSessionStatus(vo.getStatus()))
                    .build();

            GetUploadSessionResponse response = GetUploadSessionResponse.newBuilder()
                    .setCode(200)
                    .setMessage("OK")
                    .setData(info)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] GetUploadSession 失败 | uploadsId={}", request.getUploadsId(), e);
            responseObserver.onError(e);
        }
    }

    // =====================================================================
    // 查询分片
    // =====================================================================

    @Override
    public void getChunk(GetChunkRequest request,
                         StreamObserver<GetChunkResponse> responseObserver) {
        try {
            UploadsChunkEntity entity = uploadsService.queryChunkByUploadsIdAndChunkIndex(
                    UUID.fromString(request.getUploadsId()), request.getChunkIndex());
            UploadsChunkInternalVO vo = VoMapper.toUploadsChunkInternalVO(entity);

            ChunkInfo info = ChunkInfo.newBuilder()
                    .setUploadsId(vo.getUploads_id() != null ? vo.getUploads_id() : "")
                    .setChunkIndex(vo.getChunk_index() != null ? vo.getChunk_index() : 0)
                    .setChunkStatus(mapChunkStatus(vo.getChunk_status()))
                    .setChunkStoragePath(vo.getChunk_storage_path() != null ? vo.getChunk_storage_path() : "")
                    .setChunkUploadedTime(vo.getChunk_uploaded_time() != null
                            ? vo.getChunk_uploaded_time().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0)
                    .build();

            GetChunkResponse response = GetChunkResponse.newBuilder()
                    .setCode(200)
                    .setMessage("OK")
                    .setData(info)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] GetChunk 失败 | uploadsId={} | chunkIndex={}",
                    request.getUploadsId(), request.getChunkIndex(), e);
            responseObserver.onError(e);
        }
    }

    // =====================================================================
    // 合并完成
    // =====================================================================

    @Override
    public void mergingComplete(MergingCompleteRequest request,
                                StreamObserver<MergingCompleteResponse> responseObserver) {
        try {
            UUID fileId = uploadsService.uploadsMerging(UUID.fromString(request.getUploadsId()));

            MergingCompleteResponse response = MergingCompleteResponse.newBuilder()
                    .setCode(200)
                    .setMessage("OK")
                    .setFileId(fileId.toString())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] MergingComplete 失败 | uploadsId={}", request.getUploadsId(), e);
            responseObserver.onError(e);
        }
    }

    // =====================================================================
    // 文件上传完成
    // =====================================================================

    @Override
    public void fileComplete(FileCompleteRequest request,
                             StreamObserver<FileCompleteResponse> responseObserver) {
        try {
            uploadsService.completeUploads(
                    UUID.fromString(request.getUploadsId()),
                    UUID.fromString(request.getFileId()),
                    request.getFileStoragePath(),
                    UUID.fromString(request.getUid()));

            FileCompleteResponse response = FileCompleteResponse.newBuilder()
                    .setCode(200)
                    .setMessage("OK")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] FileComplete 失败 | uploadsId={} | fileId={}",
                    request.getUploadsId(), request.getFileId(), e);
            responseObserver.onError(e);
        }
    }

    // =====================================================================
    // 查询文件元数据
    // =====================================================================

    @Override
    public void getFileMetadata(GetFileMetadataRequest request,
                                StreamObserver<GetFileMetadataResponse> responseObserver) {
        try {
            FileEntity entity = fileService.queryUserFileById(
                    UUID.fromString(request.getFileId()),
                    UUID.fromString(request.getUid()));
            InternalFileMetadataVO vo = VoMapper.toInternalFileMetadataVO(entity);

            FileMetadataInfo info = FileMetadataInfo.newBuilder()
                    .setId(vo.getId() != null ? vo.getId() : "")
                    .setName(vo.getName() != null ? vo.getName() : "")
                    .setType(vo.getType() != null ? vo.getType() : "")
                    .setSize(vo.getSize() != null ? vo.getSize() : 0)
                    .setUserId(vo.getUser_id() != null ? vo.getUser_id() : "")
                    .setUploadedTime(vo.getUploaded_time() != null
                            ? vo.getUploaded_time().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0)
                    .setChecksum(vo.getChecksum() != null ? vo.getChecksum() : "")
                    .setNodeId(vo.getNode_id() != null ? vo.getNode_id() : "")
                    .setTotalChunks(vo.getTotal_chunks() != null ? vo.getTotal_chunks() : 0)
                    .setStoragePath(vo.getStorage_path() != null ? vo.getStorage_path() : "")
                    .build();

            GetFileMetadataResponse response = GetFileMetadataResponse.newBuilder()
                    .setCode(200)
                    .setMessage("OK")
                    .setData(info)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] GetFileMetadata 失败 | fileId={}", request.getFileId(), e);
            responseObserver.onError(e);
        }
    }

    // =====================================================================
    // 激活文件
    // =====================================================================

    @Override
    public void activateFile(ActivateFileRequest request,
                             StreamObserver<ActivateFileResponse> responseObserver) {
        try {
            uploadsService.activateFileStatus(
                    UUID.fromString(request.getFileId()),
                    UUID.fromString(request.getUid()));

            ActivateFileResponse response = ActivateFileResponse.newBuilder()
                    .setCode(200)
                    .setMessage("OK")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] ActivateFile 失败 | fileId={}", request.getFileId(), e);
            responseObserver.onError(e);
        }
    }

    // =====================================================================
    // 更新文件状态
    // =====================================================================

    @Override
    public void updateFileStatus(UpdateFileStatusRequest request,
                                 StreamObserver<UpdateFileStatusResponse> responseObserver) {
        try {
            fileService.updateFileStatus(
                    UUID.fromString(request.getFileId()),
                    request.getStatus(),
                    UUID.fromString(request.getUid()));

            UpdateFileStatusResponse response = UpdateFileStatusResponse.newBuilder()
                    .setCode(200)
                    .setMessage("OK")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] UpdateFileStatus 失败 | fileId={} | status={}",
                    request.getFileId(), request.getStatus(), e);
            responseObserver.onError(e);
        }
    }

    // =====================================================================
    // 文件删除完成
    // =====================================================================

    @Override
    public void fileDeleteComplete(FileDeleteCompleteRequest request,
                                   StreamObserver<FileDeleteCompleteResponse> responseObserver) {
        try {
            fileService.completeDeleteFileByFileId(
                    UUID.fromString(request.getFileId()),
                    UUID.fromString(request.getUid()));

            FileDeleteCompleteResponse response = FileDeleteCompleteResponse.newBuilder()
                    .setCode(200)
                    .setMessage("OK")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] FileDeleteComplete 失败 | fileId={}", request.getFileId(), e);
            responseObserver.onError(e);
        }
    }

    // =====================================================================
    // 上传会话删除完成
    // =====================================================================

    @Override
    public void uploadSessionDeleteComplete(UploadSessionDeleteCompleteRequest request,
                                            StreamObserver<UploadSessionDeleteCompleteResponse> responseObserver) {
        try {
            uploadsService.markUploadSessionDeleted(UUID.fromString(request.getUploadsId()));

            UploadSessionDeleteCompleteResponse response = UploadSessionDeleteCompleteResponse.newBuilder()
                    .setCode(200)
                    .setMessage("OK")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] UploadSessionDeleteComplete 失败 | uploadsId={}",
                    request.getUploadsId(), e);
            responseObserver.onError(e);
        }
    }

    // =====================================================================
    // 状态枚举映射（实体 → Proto）
    // =====================================================================

    private UploadSessionStatus mapUploadSessionStatus(UploadsSessionEntity.UploadsSessionStatus status) {
        if (status == null) return UploadSessionStatus.UPLOAD_SESSION_STATUS_UNSPECIFIED;
        return switch (status) {
            case uploading -> UploadSessionStatus.UPLOAD_SESSION_STATUS_UPLOADING;
            case merging -> UploadSessionStatus.UPLOAD_SESSION_STATUS_MERGING;
            case completed -> UploadSessionStatus.UPLOAD_SESSION_STATUS_COMPLETED;
            case failed -> UploadSessionStatus.UPLOAD_SESSION_STATUS_FAILED;
            case canceled -> UploadSessionStatus.UPLOAD_SESSION_STATUS_CANCELLED;
            case deleted -> UploadSessionStatus.UPLOAD_SESSION_STATUS_DELETED;
        };
    }

    private ChunkStatus mapChunkStatus(UploadsChunkEntity.ChunkStatus status) {
        if (status == null) return ChunkStatus.CHUNK_STATUS_UNSPECIFIED;
        return switch (status) {
            case pending -> ChunkStatus.CHUNK_STATUS_PENDING;
            case uploading -> ChunkStatus.CHUNK_STATUS_PENDING;  // 上传中 → 待处理
            case uploaded -> ChunkStatus.CHUNK_STATUS_UPLOADED;
            case failed -> ChunkStatus.CHUNK_STATUS_FAILED;
        };
    }
}