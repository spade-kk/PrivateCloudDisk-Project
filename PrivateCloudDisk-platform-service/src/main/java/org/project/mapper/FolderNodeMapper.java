package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.project.data.FolderNodeData;

import java.util.List;

@Mapper
public interface FolderNodeMapper {
    /**
     * 插入文件夹节点数据
     * @param folder_node_data 文件夹节点数据
     * @return
     */
    int insertFolderNode(FolderNodeData folder_node_data);

    /**
     * 根据节点ID查询文件夹节点数据
     * @param node_id 节点ID
     * @return 文件夹节点数据
     */
    FolderNodeData findFolderNodeById(String node_id);

    /**
     * 根据用户ID查询用户根目录节点数据
     * @param user_id 用户ID
     * @return 用户根目录节点数据
     */
    FolderNodeData findRootFolderNodeByUserId(String user_id);

     /**
     * 根据节点ID查询文件夹节点下的子文件夹节点数据
     * @param node_id 节点ID
     * @return 子文件夹节点数据列表
     */
    List<FolderNodeData> findFolderNodesById(String node_id);

    /**
     * 根据节点ID更新文件夹节点名称
     * @param new_name 新名称
     * @param node_id 节点ID
     * @return 更新影响的行数
     */
    int updateFolderNodeNameById(String new_name, String node_id);

    /**
     * 根据节点ID更新文件夹节点状态
     * @param new_status 新状态
     * @param node_id 节点ID
     * @return 更新影响的行数
     */
    int updateFolderNodeStatusById(FolderNodeData.NodeStatus new_status, String node_id);

    /**
     * 根据节点ID更新文件夹节点父节点ID
     * @param new_parent_id 新父节点ID
     * @param node_id 节点ID
     * @return 更新影响的行数
     */
    int updateFolderNodeParentIdById(String new_parent_id, String node_id);
    /**
     * 根据节点ID删除文件夹节点数据
     * @param node_id 节点ID
     * @return 删除影响的行数
     */
    int deleteFolderNodeById(String node_id);
}
