package org.project.service;

import org.project.model.dto.LazyUploadSessionResponse;
import org.project.model.entity.UploadsChunkEntity;
import org.project.model.entity.UploadsSessionEntity;

import java.util.UUID;

public interface UploadsService {
    /**
     * 创建上传会话（含防滥用检查）
     * @param total_chunks 总块数
     * @param file_size 文件大小
     * @param file_checksum 文件校验和
     * @param chunks_max_size 每个块的最大大小
     * @param file_name 文件名称
     * @param file_type 文件类型
     * @param user_id 用户ID
     * @param node_id 目录节点ID
     * @param clientIp 客户端IP（用于防滥用检查）
     * @return 上传会话ID
     */
    UUID createUploadsSession(
                int total_chunks,
                long file_size,
                String file_checksum,
                int chunks_max_size,
                String file_name,
                String file_type,
                UUID user_id,
                UUID node_id,
                String clientIp
            );

    /**
     * 根据上传会话ID查询上传会话
     * @param uploads_id 上传会话ID
     * @return 上传会话数据
     */
    UploadsSessionEntity queryUploadsSessionById(UUID uploads_id);

    /**
     * 根据上传会话ID和块索引查询块数据
     * @param uploads_id 上传会话ID
     * @param chunk_index 块索引
     * @return 块数据
     */
    UploadsChunkEntity queryChunkByUploadsIdAndChunkIndex(UUID uploads_id, int chunk_index);

    /**
     * 完成块上传
     * @param uploads_id 上传会话ID
     * @param chunk_index 块索引
     * @param chunk_storage_path 块存储路径
     */
    void completeChunkUpload(
            UUID uploads_id,
            int chunk_index,
            String chunk_storage_path
        );

    /**
     * 完成上传会话
     * @param uploads_id 上传会话ID
     * @param file_id
     * @param file_storage_path 文件储存路径
     * @param user_id
     */
    void completeUploads(UUID uploads_id, UUID file_id, String file_storage_path, UUID user_id);

    /**
     * 取消上传会话
     * @param uploads_id
     * @param user_id
     */
    void cancelUploadSession(UUID uploads_id, UUID user_id);

    /**
     * 合并上传会话分块的通知
     * @param uploads_id 上传会话ID
     * @return  file_id 生成文件事务记录的id
     */
    UUID uploadsMerging(UUID uploads_id);

    /**
     * 检查上传会话是否有效
     * @param uploads_id 上传会话ID
     * @return 是否有效
     */
    boolean isValidUploadsSession(UUID uploads_id);

    /**
     *
     * @param file_id
     * @param user_id
     */
    void activateFileStatus(UUID file_id, UUID user_id);

    /**
     * 文件存储服务完成物理文件删除后，同步调用标记上传会话为 deleted
     * <p>内部会发布 uploads.session.deleted 事件，通知主业务服务释放配额。
     * @param uploads_id 上传会话ID
     */
    void markUploadSessionDeleted(UUID uploads_id);

    /**
     * 懒创建上传会话（混合模型）。
     * <p>
     * 支持两种懒创建模式：
     * <ul>
     *   <li><b>node_id + 相对路径：</b>parentNodeId + relativePath，自动创建路径中不存在的目录</li>
     *   <li><b>纯面包屑路径：</b>breadcrumbPath，从根节点开始逐级创建</li>
     * </ul>
     * <p>
     * 创建成功后返回 uploads_id + 最终文件所在的 node_id。
     *
     * @param total_chunks   总块数
     * @param file_size      文件大小
     * @param file_checksum  文件校验和
     * @param chunks_max_size 分块最大大小
     * @param file_name      文件名称
     * @param file_type      文件类型
     * @param user_id        用户ID
     * @param parentNodeId   父节点ID（node_id + 相对路径 模式）
     * @param relativePath   相对路径（可为 null）
     * @param breadcrumbPath 面包屑路径（可为 null）
     * @param clientIp       客户端IP
     * @return LazyUploadSessionResponse 包含 uploads_id 和 node_id
     */
    LazyUploadSessionResponse createLazyUploadSession(
            int total_chunks,
            long file_size,
            String file_checksum,
            int chunks_max_size,
            String file_name,
            String file_type,
            UUID user_id,
            UUID parentNodeId,
            String relativePath,
            String breadcrumbPath,
            String clientIp
    );
}
