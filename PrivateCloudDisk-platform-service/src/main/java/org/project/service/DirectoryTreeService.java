package org.project.service;

import org.project.model.dto.NodeQueryDTO;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.NodeEntity;
import org.project.model.vo.PageResultVO;

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
     * @param user_id 用户ID
     */
    void activeFolderNode(String node_id, String user_id);

    /**
     * 删除文件夹节点
     * @param node_id 节点ID
     * @param user_id 用户ID
     */
    void deleteFolderNode(String node_id, String user_id);
    /**
     * 根据节点ID查询文件夹节点
     * @param node_id 节点ID
     * @param user_id 用户ID
     * @return 文件夹节点数据
     */
    FolderNodeEntity queryFolderNodeById(String node_id, String user_id);
    /**
     * 根据节点ID查询节点下所有节点元数据
     * @param node_id 节点ID
     * @param user_id 用户ID
     * @return 节点元数据列表
     */
    List<NodeEntity> findUserNodesByNodeId(String node_id, String user_id);
    
    /**
     * 分页查询节点下所有节点元数据（支持搜索、过滤、排序）
     * @param query 查询条件
     * @param user_id 用户ID
     * @return 分页结果
     */
    PageResultVO<NodeEntity> findUserNodesByNodeIdPaged(NodeQueryDTO query, String user_id);
    
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
