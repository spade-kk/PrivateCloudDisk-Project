package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.project.model.entity.TrashFileEntity;

import java.util.List;

@Mapper
public interface TrashFileMapper {
    
    /**
     * 添加文件到回收站
     */
    int insertTrashFile(TrashFileEntity trashFile);
    
    /**
     * 从回收站恢复文件
     */
    int deleteTrashFile(Long trash_id, String user_id);
    
    /**
     * 查询回收站文件
     */
    TrashFileEntity findTrashFileById(Long trash_id, String user_id);
    
    /**
     * 查询用户回收站文件列表（分页）
     */
    List<TrashFileEntity> findTrashFilesByUserId(String user_id, Integer offset, Integer limit);
    
    /**
     * 统计用户回收站文件数量
     */
    Integer countTrashFilesByUserId(String user_id);
    
    /**
     * 查询过期的回收站文件（用于自动清理）
     */
    List<TrashFileEntity> findExpiredTrashFiles();
    
    /**
     * 根据原文件ID查询回收站记录
     */
    TrashFileEntity findTrashFileByFileId(String file_id, String user_id);
}
