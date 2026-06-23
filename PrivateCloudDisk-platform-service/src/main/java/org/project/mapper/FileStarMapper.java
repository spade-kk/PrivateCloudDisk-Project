package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.FileStarEntity;

import java.util.List;
import java.util.UUID;

@Mapper
public interface FileStarMapper {

    /**
     * 添加文件收藏
     */
    int insertFileStar(FileStarEntity fileStar);

    /**
     * 添加文件夹收藏
     */
    int insertFolderStar(FileStarEntity fileStar);

    /**
     * 取消文件收藏（按 user_id + file_id）
     */
    int deleteFileStar(@Param("user_id") UUID user_id, @Param("file_id") UUID file_id);

    /**
     * 取消文件夹收藏（按 user_id + node_id）
     */
    int deleteFolderStar(@Param("user_id") UUID user_id, @Param("node_id") UUID node_id);

    /**
     * 查询用户是否收藏了文件
     */
    FileStarEntity findFileStarByUserIdAndFileId(@Param("user_id") UUID user_id, @Param("file_id") UUID file_id);

    /**
     * 查询用户是否收藏了文件夹
     */
    FileStarEntity findFolderStarByUserIdAndNodeId(@Param("user_id") UUID user_id, @Param("node_id") UUID node_id);

    /**
     * 查询用户所有收藏的文件ID
     */
    List<String> findStarredFileIdsByUserId(@Param("user_id") UUID user_id);

    /**
     * 查询用户所有收藏的文件夹节点ID
     */
    List<String> findStarredNodeIdsByUserId(@Param("user_id") UUID user_id);

    /**
     * 查询用户收藏列表（含文件/文件夹详情，分页）
     */
    List<FileStarEntity> findStarredItemsByUserId(@Param("user_id") UUID user_id,
                                                   @Param("offset") Integer offset,
                                                   @Param("limit") Integer limit);

    /**
     * 统计用户收藏总数（文件+文件夹）
     */
    Integer countStarredItemsByUserId(@Param("user_id") UUID user_id);
}