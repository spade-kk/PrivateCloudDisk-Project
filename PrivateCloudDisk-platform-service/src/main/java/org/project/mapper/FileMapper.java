package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.project.model.entity.FileEntity;

import java.util.List;

@Mapper
public interface FileMapper {
    /**
     * 插入用户数据
     * @param fileData
     * @return 插入了数据行数
     */
    int insertFile(FileEntity fileData);

    /**
     * 根据用户Uid节点ID查询节点下所有文件元数据
     * @param node_id 节点ID
     * @param user_id 用户Uid
     * @return 文件元数据列表
     */
    List<FileEntity> findUserFilesByNodeId(String node_id, String user_id);

    /**
     * 根据文件ID查询文件元数据
     * @param file_id 文件ID
     * @return 文件元数据
     */
    FileEntity findFileById(String file_id);

    /**
     * 根据用户Uid父目录节点ID和文件名字查询文件元数据
     * @param node_id 节点ID
     * @param name 文件名字
     * @param user_id 用户Uid
     * @return 文件元数据
     */
    FileEntity findUserFileByNodeIdAndName(String node_id, String name, String user_id);
    /**
     * 更新用户文件名称
     * @param file_new_name 新文件名
     * @param user_id 用户Uid
     * @return 受变动行数
     */
    int updateUserFileNameById(String file_id, String file_new_name, String user_id);
    /**
     * 更新用户文件父目录节点ID
     * @param target_node_id 目标节点ID
     * @param user_id 用户Uid
     * @return 受变动行数
     */
    int updateUserFileParentNodeIdById(String file_id, String target_node_id, String user_id);
    /**
     * 删除用户文件
     * @param file_id 文件ID
     * @param user_id 用户Uid
     * @return 受变动行数
     */
    int deleteUserFileById(String file_id, String user_id);
}
