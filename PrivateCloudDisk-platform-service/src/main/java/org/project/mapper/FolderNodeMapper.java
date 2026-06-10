package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.FolderNodeEntity;

import java.util.List;
import java.util.UUID;

@Mapper
public interface FolderNodeMapper {
    /**
     * 插入文件夹节点数据
     * @param folder_node_data 文件夹节点数据
     * @return
     */
    int insertFolderNode(FolderNodeEntity folder_node_data);

    /**
     * 根据节点ID查询文件夹节点数据
     * @param node_id 节点ID
     * @param user_id 用户ID
     * @return 文件夹节点数据
     */
    FolderNodeEntity findFolderNodeByIdAndUserId(@Param("node_id") UUID node_id, @Param("user_id") UUID user_id);

    /**
     * 根据用户ID查询用户根目录节点数据
     * @param user_id 用户ID
     * @return 用户根目录节点数据
     */
    FolderNodeEntity findRootFolderNodeByUserId(@Param("user_id") UUID user_id);

     /**
     * 根据节点ID查询文件夹节点下的子文件夹节点数据
     * @param node_id 节点ID
     * @param user_id 用户ID
     * @return 子文件夹节点数据列表
     */
    List<FolderNodeEntity> findFolderNodesByIdAndUserId(@Param("node_id") UUID node_id, @Param("user_id") UUID user_id);

    /**
     *
     * @param node_id
     * @param user_id
     * @return
     */
    boolean isFolderDeleted(@Param("node_id") UUID node_id, @Param("user_id") UUID user_id);

    /**
     *
     * @param node_id
     * @param user_id
     * @return
     */
    String selectFolderEffectiveStatus(@Param("node_id") UUID node_id, @Param("user_id") UUID user_id);

    /**
     * 根据节点ID更新文件夹节点名称
     * @param new_name 新名称
     * @param node_id 节点ID
     * @param user_id 用户ID
     * @return 更新影响的行数
     */
    int updateFolderNodeNameByIdAndUserId(@Param("new_name") String new_name, @Param("node_id") UUID node_id, @Param("user_id") UUID user_id);

    /**
     * 根据节点ID更新文件夹节点状态
     * @param new_status 新状态
     * @param node_id 节点ID
     * @param user_id 用户ID
     * @return 更新影响的行数
     */
    int updateFolderNodeStatusByIdAndUserId(@Param("new_status") FolderNodeEntity.NodeStatus new_status, @Param("node_id") UUID node_id, @Param("user_id") UUID user_id);

    /**
     * 根据节点ID更新文件夹节点父节点ID
     * @param new_parent_id 新父节点ID
     * @param node_id 节点ID
     * @param user_id 用户ID
     * @return 更新影响的行数
     */
    int updateFolderNodeParentIdByIdAndUserId(@Param("new_parent_id") UUID new_parent_id, @Param("node_id") UUID node_id, @Param("user_id") UUID user_id);
    /**
     * 根据节点ID删除文件夹节点数据
     * @param node_id 节点ID
     * @param user_id 用户ID
     * @return 删除影响的行数
     */
    int deleteFolderNodeByIdAndUserId(@Param("node_id") UUID node_id, @Param("user_id") UUID user_id);
}
