package org.project.service;

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
     * <p>接口层负责从 Request DTO 提取参数后传入，业务层不依赖任何 Request DTO。</p>
     * @param parentId 父节点ID
     * @param keyword 搜索关键词
     * @param fileType 文件类型过滤
     * @param sortBy 排序字段
     * @param sortOrder 排序方向
     * @param page 页码
     * @param pageSize 每页数量
     * @param userId 用户ID
     * @return 分页结果
     */
    PageResultVO<NodeEntity> findUserNodesByNodeIdPaged(
            String parentId, String keyword, String fileType,
            String sortBy, String sortOrder, Integer page, Integer pageSize, UUID userId);
    
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

    /**
     * 递归查询文件夹下所有活跃文件（用于文件夹下载）
     * @param nodeId 文件夹节点ID
     * @param userId 用户ID
     * @return 文件元数据列表
     */
    List<FileEntity> findActiveFilesRecursive(UUID nodeId, UUID userId);

    /**
     * 懒创建文件夹路径（node_id + 相对路径模式）。
     * 从 parentNodeId 开始，逐级 ensure 相对路径中的每一级文件夹存在。
     * 幂等：已存在的文件夹直接返回，不存在则创建。
     * <p>
     * 校验：路径长度 ≤ 1024、深度 ≤ MAX_DIRECTORY_DEPTH、每级子节点 ≤ 1000、
     * 用户总文件夹 ≤ 100,000、创建速率 ≤ 100/min/user。
     *
     * @param userId       用户 ID
     * @param parentNodeId 父节点 ID
     * @param relativePath 相对路径（如 "subfolder1/subfolder2"）
     * @return 最终文件夹节点 ID
     */
    UUID ensureFolderPath(UUID userId, UUID parentNodeId, String relativePath);

    /**
     * 懒创建文件夹路径（纯面包屑路径模式）。
     * 从根节点开始，逐级 ensure 面包屑路径中的每一级文件夹存在。
     * 幂等：已存在的文件夹直接返回，不存在则创建。
     * <p>
     * 校验：路径长度 ≤ 1024、深度 ≤ MAX_DIRECTORY_DEPTH、每级子节点 ≤ 1000、
     * 用户总文件夹 ≤ 100,000、创建速率 ≤ 100/min/user。
     *
     * @param userId         用户 ID
     * @param breadcrumbPath 面包屑路径（如 "/root/folder1/subfolder2"）
     * @return 最终文件夹节点 ID
     */
    UUID ensureFolderPath(UUID userId, String breadcrumbPath);

    // ==================== 路径 → node_id 解析 ====================

    /**
     * 通过绝对路径（面包屑路径）解析目标文件夹节点 ID。
     * 从用户根节点开始，逐级按名称查找子节点。
     * 路径格式："/root_folder/subfolder1/subfolder2"（根节点名称可省略）
     * <p>若路径中任何一级不存在，抛出 NodeNotExistException。
     *
     * @param userId       用户 ID
     * @param absolutePath 绝对路径（面包屑路径）
     * @return 目标节点 ID
     */
    UUID resolveAbsolutePathToNodeId(UUID userId, String absolutePath);

    /**
     * 通过 node_id + 相对路径解析目标文件夹节点 ID。
     * 从 parentNodeId 开始，逐级按名称查找子节点。
     * 相对路径格式："subfolder1/subfolder2"
     * <p>若路径中任何一级不存在，抛出 NodeNotExistException。
     *
     * @param userId       用户 ID
     * @param parentNodeId 父节点 ID
     * @param relativePath 相对路径
     * @return 目标节点 ID
     */
    UUID resolveRelativePathToNodeId(UUID userId, UUID parentNodeId, String relativePath);
}
