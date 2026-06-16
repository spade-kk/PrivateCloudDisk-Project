package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.FileEntity;

import java.util.List;
import java.util.UUID;

@Mapper
public interface FileMapper {
    /**
     * 插入用户数据
     * @param fileData
     * @return 插入了数据行数
     */
    int insertFile(FileEntity fileData);

    /**
     * 根据用户Uid节点ID查询节点下所有Active文件元数据
     * @param node_id 节点ID
     * @param user_id 用户Uid
     * @return 文件元数据列表
     */
    List<FileEntity> findUserActiveFilesByNodeId(@Param("node_id") UUID node_id, @Param("user_id") UUID user_id);

    /**
     * 根据用户Uid节点ID查询节点下所有Exist文件元数据 包括正在后处理的文件
     * @param node_id 节点ID
     * @param user_id 用户Uid
     * @return 文件元数据列表
     */
    List<FileEntity> findUserExistFilesByNodeId(@Param("node_id") UUID node_id, @Param("user_id") UUID user_id);

    /**
     * 根据文件ID查询文件元数据
     * @param file_id 文件ID
     * @param user_id 用户Uid
     * @return 文件元数据
     */
    FileEntity findUserFileById(@Param("file_id") UUID file_id, @Param("user_id") UUID user_id);
    /**
     * 根据用户Uid父目录节点ID和文件名字查询文件元数据
     * @param node_id 节点ID
     * @param name 文件名字
     * @param user_id 用户Uid
     * @return 文件元数据
     */
    FileEntity findUserFileByNodeIdAndName(@Param("node_id") UUID node_id, @Param("name") String name, @Param("user_id") UUID user_id);
    /**
     *
     * @param file_id
     * @param user_id
     * @return
     */
    boolean isFileDeleted(@Param("file_id") UUID file_id, @Param("user_id") UUID user_id);
    /**
     *
     * @param file_id
     * @param user_id
     * @return
     */
    String selectFileEffectiveStatus(@Param("file_id") UUID file_id, @Param("user_id") UUID user_id);
    /**
     * 更新用户文件名称
     * @param file_new_name 新文件名
     * @param user_id 用户Uid
     * @return 受变动行数
     */
    int updateUserFileNameById(@Param("file_id") UUID file_id, @Param("file_new_name") String file_new_name, @Param("user_id") UUID user_id);
    /**
     * 更新用户文件父目录节点ID
     * @param target_node_id 目标节点ID
     * @param user_id 用户Uid
     * @return 受变动行数
     */
    int updateUserFileParentNodeIdById(@Param("file_id") UUID file_id, @Param("target_node_id") UUID target_node_id, @Param("user_id") UUID user_id);

    /**
     * 更新文件状态为指定状态
     * @param file_id 文件ID
     * @param status 目标状态
     * @param user_id 用户Uid
     * @return 受变动行数
     */
    int updateUserFileStatusById(@Param("file_id") UUID file_id, @Param("status") FileEntity.FileStatus status, @Param("user_id") UUID user_id);

    /**
     * 递归查询文件夹下所有活跃文件（通过目录闭包表查询所有子孙节点下的文件）
     * @param nodeId 文件夹节点ID
     * @param userId 用户ID
     * @return 文件元数据列表
     */
    List<FileEntity> findActiveFilesByDescendantNodes(@Param("nodeId") UUID nodeId, @Param("userId") UUID userId);

    /**
     *
     * @param file_id
     * @param storage_path
     * @param user_id
     * @return
     */
    int updateUserFileStoragePath(@Param("file_id") UUID file_id, @Param("storage_path") String storage_path, @Param("user_id") UUID user_id);
    /**
     * 删除用户文件
     * @param file_id 文件ID
     * @param user_id 用户Uid
     * @return 受变动行数
     */
    int deleteUserFileById(@Param("file_id") UUID file_id, @Param("user_id") UUID user_id);
    /**
     *
     * @param user_id
     * @return
     */
    int cleanUserFailedStatusFiles(@Param("user_id") UUID user_id);

    /**
     * @return
     */
    int cleanFailedStatusFiles();
}
