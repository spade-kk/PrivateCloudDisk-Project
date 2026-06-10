package org.project.service;

import org.project.model.entity.FileEntity;

import java.util.List;
import java.util.UUID;

public interface FileService {
    /**
     * 创建文件元数据
     * @param file_name 文件名称
     * @param file_type 文件类型
     * @param file_size 文件大小
     * @param user_id 用户ID
     * @param node_id 节点ID
     * @param file_checksum 文件校验和
     * @param file_total_chunks 文件总切片数量
     * @param file_storage_path 文件储存路径
     * @return file_id 创建文件的id
     */
    UUID createFile(
                String file_name,
                String file_type,
                long file_size,
                UUID user_id,
                UUID node_id,
                String file_checksum,
                int file_total_chunks,
                String file_storage_path
        );

    /**
     * 根据节点ID查询节点用户云盘下所有文件元数据
     * @param node_id 节点ID
     * @param user_id 查询用户Uid
     * @return 文件元数据列表
     */
    List<FileEntity> queryUserFilesByNodeId(UUID node_id, UUID user_id);

    /**
     * 根据文件ID查询用户云盘下文件元数据
     * @param file_id 文件ID
     * @param user_id 查询用户Uid
     * @return 文件元数据
     */
    FileEntity queryUserFileById(UUID file_id, UUID user_id);

    /**
     *
     * @param file_id
     * @param user_id
     * @return
     */
    FileEntity findUserFileByIdIfExist(UUID file_id, UUID user_id);

    /**
     *
     * @param file_name
     * @param user_id
     * @return
     */
    FileEntity findUserFileByNameAndNodeIdIfExist(String file_name, UUID node_id, UUID user_id);

    /**
     *
     * @param file_id
     * @param user_id
     * @return
     */
    FileEntity.FileStatus getFileValidStatus(UUID file_id, UUID user_id);
    /**
     * 更新文件名称
     * @param file_id 文件ID
     * @param file_new_name 新文件名称
     * @param user_id 用户ID
     */
    void updateFileName(UUID file_id, String file_new_name, UUID user_id);

    /**
     * 文件移动
     * @param file_id 文件ID
     * @param target_node_id 目标节点ID
     * @param user_id 用户ID
     */
    void moveFileByFileId(UUID file_id, UUID target_node_id, UUID user_id);

    /**
     * 删除文件
     * @param file_id 文件ID
     * @param user_id 用户ID
     */
    void deleteFileByFileId(UUID file_id, UUID user_id);

    /**
     * 删除文件 回收站模式
     * @param file_id 文件ID
     * @param user_id 用户ID
     */
    void deleteFileToTrash(UUID file_id, UUID user_id);
}
