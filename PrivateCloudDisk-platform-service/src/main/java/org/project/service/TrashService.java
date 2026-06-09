package org.project.service;

import org.project.model.entity.TrashFileEntity;

import java.util.List;

/**
 * 回收站服务接口
 */
public interface TrashService {
    
    /**
     * 将文件移动到回收站
     * @param file_id 文件ID
     * @param user_id 用户ID
     */
    void moveToTrash(String file_id, String user_id);
    
    /**
     * 从回收站恢复文件
     * @param trash_id 回收站记录ID
     * @param user_id 用户ID
     */
    void restoreFromTrash(Long trash_id, String user_id);
    
    /**
     * 彻底删除回收站文件
     * @param trash_id 回收站记录ID
     * @param user_id 用户ID
     */
    void permanentDelete(Long trash_id, String user_id);
    
    /**
     * 清空回收站
     * @param user_id 用户ID
     */
    void emptyTrash(String user_id);
    
    /**
     * 获取回收站文件列表（分页）
     * @param user_id 用户ID
     * @param page 页码
     * @param pageSize 每页数量
     * @return 回收站文件列表
     */
    List<TrashFileEntity> getTrashFiles(String user_id, Integer page, Integer pageSize);
    
    /**
     * 统计回收站文件数量
     * @param user_id 用户ID
     * @return 文件数量
     */
    Integer countTrashFiles(String user_id);
    
    /**
     * 获取回收站文件详情
     * @param trash_id 回收站记录ID
     * @param user_id 用户ID
     * @return 回收站文件
     */
    TrashFileEntity getTrashFileById(Long trash_id, String user_id);
}
