package org.project.mapper;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface DirectoryClosureMapper {

    /**
     * 插入自引用关系
     * @param nodeId 节点ID
     * @param userId 用户ID
     */
    void insertSelf(@Param("nodeId") UUID nodeId, @Param("userId") UUID userId);
    /**
     * 插入移动节点的子节点关系
     * @param movingNodeId 移动节点ID
     * @param newParentId 新父节点ID
     * @param userId 用户ID
     */
    void insertRelationsForMove(@Param("movingNodeId") UUID movingNodeId, @Param("newParentId") UUID newParentId, @Param("userId") UUID userId);
    /**
     * 删除子树的闭包关系
     * @param rootNodeId 根节点ID
     * @param userId 用户ID
     */
    void deleteClosureRowsBySubtree(@Param("rootNodeId") UUID rootNodeId, @Param("userId") UUID userId);
    /**
     * 判断是否是祖先节点
      * @param descendantId 子节点ID
      * @param ancestorId 祖先ID
      * @param userId 用户ID
      * @return 是否是祖先节点，1是，0否
      */
    int isDescendant(@Param("descendantId") UUID descendantId, @Param("ancestorId") UUID ancestorId, @Param("userId") UUID userId);
    /**
     * 获取节点的最大深度
     * @param nodeId 节点ID
     * @param userId 用户ID
     * @return 节点的最大深度
      */
    int getMaxDepthToNode(@Param("nodeId") UUID nodeId, @Param("userId") UUID userId);
    /**
     * 查询节点的子节点ID
     * @param nodeId 节点ID
     * @param userId 用户ID
     * @return 子节点ID列表
     */
    List<UUID> selectDescendantIds(@Param("nodeId") UUID nodeId, @Param("userId") UUID userId);
    /**
     * 删除移动节点的外部关系
     * @param movingNodeId 移动节点ID
     * @param newParentId 新父节点ID
     * @param userId 用户ID
     */
    void deleteExternalRelationsForMove(@Param("movingNodeId") UUID movingNodeId, @Param("newParentId") UUID newParentId, @Param("userId") UUID userId);
    /**
     * 插入父节点的子节点关系
     * @param nodeId 父节点ID
     * @param userId 用户ID
     */
    void insertRelationsFromParent(@Param("nodeId") UUID nodeId, @Param("userId") UUID userId, @Param("parentId") UUID parentId);

    /**
     * 查询多个节点的祖先链信息（含节点名称），用于计算相对路径
     * @param descendantIds 后代节点ID列表
     * @param userId 用户ID
     * @param rootNodeId 根节点ID（排除此节点之前的路径）
     * @return 祖先链信息列表（sorted by descendant, depth DESC）
     */
    List<PathNodeInfo> selectAncestorPaths(
            @Param("descendantIds") List<UUID> descendantIds,
            @Param("userId") UUID userId,
            @Param("rootNodeId") UUID rootNodeId);
}
