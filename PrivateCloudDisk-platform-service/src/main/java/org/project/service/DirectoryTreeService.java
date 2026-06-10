package org.project.service;

import org.project.model.dto.NodeQueryDTO;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.NodeEntity;
import org.project.model.vo.PageResultVO;

import java.util.List;
import java.util.UUID;

public interface DirectoryTreeService {
    /**
     * 创建文件夹节点
     * @param user_id 用户ID
     * @param parent_id 父节点ID
     * @param name 文件夹名称
     */
    void createFolderNode(UUID user_id, UUID parent_id, String name);

    /**
     * 激活文件夹节点
     * @param node_id 节点ID
     * @param user_id 用户ID
     */
    void activeFolderNode(UUID node_id, UUID user_id);
    /**
     *
     * @param node_id
     * @param user_id
     * @return
     */
    FolderNodeEntity queryUserFolderNodeById(UUID node_id, UUID user_id);
    /**
     * 根据节点ID查询文件夹节点
     * @param node_id 节点ID
     * @param user_id 用户ID
     * @return 文件夹节点数据
     */
    FolderNodeEntity findUserFolderNodeIfExist(UUID node_id, UUID user_id);
    /**
    /* 实际状态
     * @param node_id 节点ID
     * @param user_id 用户ID
     * @return 状态
     */
    FolderNodeEntity.NodeStatus getFolderNodeActualStatus(UUID node_id, UUID user_id);
    /**
     * 有效状态
     * @param node_id 节点ID
     * @param user_id 用户ID
     * @return 状态
     */
    FolderNodeEntity.NodeStatus getFolderNodeValidStatus(UUID node_id, UUID user_id);
    /**
     * 根据节点ID查询节点下所有节点元数据
     * @param node_id 节点ID
     * @param user_id 用户ID
     * @return 节点元数据列表
     */
    List<NodeEntity> findUserNodesByNodeId(UUID node_id, UUID user_id);
    
    /**
     * 分页查询节点下所有节点元数据（支持搜索、过滤、排序）
     * @param query 查询条件
     * @param user_id 用户ID
     * @return 分页结果
     */
    PageResultVO<NodeEntity> findUserNodesByNodeIdPaged(NodeQueryDTO query, UUID user_id);
    
    /**
     * 移动节点
     * @param node_id 节点ID
     * @param target_position 目标位置
     * @param user_id 用户ID
     */
    void moveNodeByNodeId(UUID node_id, UUID target_position, UUID user_id);
    /**
     * 更新节点名称
     * @param node_id 节点ID
     * @param new_node_name 新节点名称
     * @param user_id 用户ID
     */
    void updateNodeNameByNodeId(UUID node_id, String new_node_name, UUID user_id);

    /**
     * 删除文件夹节点
     * @param node_id 节点ID
     * @param user_id 用户ID
     */
    void deleteFolderNodeByNodeId(UUID node_id, UUID user_id);
    /**
     * 删除文件夹到回收站节点
     * @param node_id 节点ID
     * @param user_id 用户ID
     */
    void deleteFolderNodeToTrashByNodeId(UUID node_id, UUID user_id);
}
