package org.project.service;

import org.project.data.FileData;
import org.project.data.FolderNodeData;
import org.project.data.NodeData;

import java.util.List;

public interface DirectoryTreeService {
    /**
     * 创建文件夹节点
     * @param user_id 用户ID
     * @param parent_id 父节点ID
     * @param name 文件夹名称
     */
    void createFolderNode(String user_id, String parent_id, String name);

    /**
     * 激活文件夹节点
     * @param node_id 节点ID
     */
    void activeFolderNode(String node_id);

    /**
     * 删除文件夹节点
     * @param node_id 节点ID
     */
    void deleteFolderNode(String node_id);
    /**
     * 根据节点ID查询文件夹节点
     * @param node_id 节点ID
     * @return 文件夹节点数据
     */
    FolderNodeData queryFolderNodeById(String node_id);
    /**
     * 根据节点ID查询节点下所有节点元数据
     * @param node_id 节点ID
     * @return 节点元数据列表
     */
    List<NodeData> findUserNodesByNodeId(String node_id, String user_id);
    /**
     * 移动节点
     * @param node_id 节点ID
     * @param target_position 目标位置
     * @param user_id 用户ID
     */
    void moveNodeByNodeId(String node_id, String target_position, String user_id);
    /**
     * 更新节点名称
     * @param node_id 节点ID
     * @param new_node_name 新节点名称
     * @param user_id 用户ID
     */
    void updateNodeNameByNodeId(String node_id, String new_node_name, String user_id);
    /**
     * 删除节点
     * @param node_id 节点ID
     * @param user_id 用户ID
     */
    void deleteNodeByNodeId(String node_id, String user_id);
}
