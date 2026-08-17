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
     * 根据空间ID查询空间根目录节点数据
     * @param space_id 空间ID
     * @return 空间根目录节点数据
     */
    // FolderNodeEntity findRootFolderNodeBySpaceId(@Param("space_id") UUID space_id);

    /** 公开仓库只读查询：显式绑定 space_id，不依赖当前工作空间 ThreadLocal。 */
    FolderNodeEntity findRootFolderNodeBySpaceId(@Param("space_id") UUID space_id);

    FolderNodeEntity findFolderNodeByIdAndSpaceId(@Param("node_id") UUID node_id,
                                                  @Param("space_id") UUID space_id);

    /** 分享场景的文件夹三元组查询，避免同一用户不同空间的 node_id 被混用。 */
    FolderNodeEntity findFolderNodeByIdAndUserAndSpace(
            @Param("node_id") UUID nodeId,
            @Param("user_id") UUID userId,
            @Param("space_id") UUID spaceId);

    List<FolderNodeEntity> findFolderNodesBySpaceId(@Param("parent_id") UUID parent_id,
                                                    @Param("space_id") UUID space_id);

    /** 分享目录子文件夹查询：父节点、分享者和空间必须全部匹配。 */
    List<FolderNodeEntity> findShareFolderNodesByParentId(
            @Param("parent_id") UUID parentId,
            @Param("space_id") UUID spaceId,
            @Param("user_id") UUID userId);

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

    /**
     * 根据父节点ID和名称查询子文件夹节点
     * @param name 文件夹名称
     * @param parent_id 父节点ID
     * @param user_id 用户ID
     * @return 文件夹节点数据，不存在返回null
     */
    FolderNodeEntity findFolderNodeByNameAndParentId(
            @Param("name") String name,
            @Param("parent_id") UUID parent_id,
            @Param("user_id") UUID user_id);

    /**
     * 根据父节点ID和名称查询子文件夹节点（别名为 findFolderNodeByNameAndParentId）
     */
    FolderNodeEntity findFolderNodeByParentIdAndName(
            @Param("name") String name,
            @Param("parent_id") UUID parent_id,
            @Param("user_id") UUID user_id);

    /**
     * 统计用户文件夹总数（active/pending状态）
     * @param user_id 用户ID
     * @return 文件夹总数
     */
    int countUserFolders(@Param("user_id") UUID user_id);

    /**
     * 统计指定父节点下子节点数（文件和文件夹）
     * @param parent_id 父节点ID
     * @param user_id 用户ID
     * @return 子节点数
     */
    int countChildrenByNodeId(@Param("parent_id") UUID parent_id, @Param("user_id") UUID user_id);

    /**
     * 根据父节点ID和名称查找文件夹节点（用于幂等检查）
     * @param parent_id 父节点ID
     * @param name 文件夹名称
     * @param user_id 用户ID
     * @return 文件夹节点，不存在返回null
     */
    FolderNodeEntity findFolderNodeByParentIdAndName(@Param("parent_id") UUID parent_id, @Param("name") String name, @Param("user_id") UUID user_id);
}
