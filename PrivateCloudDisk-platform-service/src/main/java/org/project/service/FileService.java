package org.project.service;

import org.project.model.entity.FileEntity;

import java.util.List;

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
    String createFile(
                String file_name,
                String file_type,
                long file_size,
                String user_id,
                String node_id,
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
    List<FileEntity> queryUserFilesByNodeId(String node_id, String user_id);

    /**
     * 根据父节点ID和文件名字查询用户云盘节点下文件元数据
     * @param node_id 父节点ID
     * @param file_name 文件名字
     * @param user_id 查询用户Uid
     * @return 文件元数据
     */
    FileEntity queryUserFileByNodeIdAndName(String node_id, String file_name, String user_id);

    /**
     * 根据文件ID查询用户云盘下文件元数据
     * @param file_id 文件ID
     * @param user_id 查询用户Uid
     * @return 文件元数据
     */
    FileEntity queryUserFileById(String file_id, String user_id);

    /**
     * 更新文件名称
     * @param file_id 文件ID
     * @param file_new_name 新文件名称
     * @param user_id 用户ID
     */
    void updateFileName(String file_id, String file_new_name, String user_id);

    /**
     * 文件移动
     * @param file_id 文件ID
     * @param target_node_id 目标节点ID
     * @param user_id 用户ID
     */
    void moveFileByFileId(String file_id, String target_node_id, String user_id);

    /**
     * 删除文件
     * @param file_id 文件ID
     * @param user_id 用户ID
     */
    void deleteFileByFileId(String file_id, String user_id);
}
