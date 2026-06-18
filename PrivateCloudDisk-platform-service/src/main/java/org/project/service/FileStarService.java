package org.project.service;

import org.project.model.entity.FileStarEntity;

import java.util.List;

/**
 * 文件/文件夹收藏服务接口
 */
public interface FileStarService {

    /**
     * 添加文件收藏
     * @param user_id 用户ID
     * @param file_id 文件ID
     */
    void addFileStar(String user_id, String file_id);

    /**
     * 添加文件夹收藏
     */
    void addFolderStar(String user_id, String node_id);

    /**
     * 取消文件收藏
     * @param user_id 用户ID
     * @param file_id 文件ID
     */
    void removeFileStar(String user_id, String file_id);

    /**
     *
     * @param user_id
     * @param node_id
     */
    void removeFolderStar(String user_id, String node_id);

    /**
     * 检查是否已收藏
     * @param user_id 用户ID
     * @param file_id 文件ID
     * @return 是否已收藏
     */
    boolean isFileStarred(String user_id, String file_id);

    /**
     * 检查文件夹是否已收藏
     */
    boolean isFolderStarred(String user_id, String node_id);

    /**
     * 获取用户收藏的文件ID列表
     * @param user_id 用户ID
     * @return 文件ID列表
     */
    List<String> getStarredFileIds(String user_id);

    /**
     *
     * @param user_id
     * @return
     */
    List<String> getStarredNodeIds(String user_id);

    /**
     * 获取用户收藏的文件列表（分页）
     * @param user_id 用户ID
     * @param page 页码
     * @param pageSize 每页数量
     * @return 收藏列表
     */
    List<FileStarEntity> getStarredItems(String user_id, Integer page, Integer pageSize);

    /**
     * 统计用户收藏的文件数量
     * @param user_id 用户ID
     * @return 收藏数量
     */
    Integer countStarredItems(String user_id);
}