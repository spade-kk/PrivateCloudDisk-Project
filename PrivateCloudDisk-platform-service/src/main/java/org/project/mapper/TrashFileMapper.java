package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.TrashFileEntity;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TrashFileMapper {
    
    /**
     * 添加文件到回收站
     */
    int insertTrashFile(TrashFileEntity trashFile);
    
    /**
     * 从回收站恢复文件
     */
    int deleteTrashFile(@Param("trash_id") Long trash_id, @Param("user_id") UUID user_id);
    
    /**
     * 查询回收站文件
     */
    TrashFileEntity findTrashFileById(@Param("trash_id") Long trash_id, @Param("user_id") UUID user_id);
    
    /**
     * 查询用户回收站文件列表（分页）
     */
    List<TrashFileEntity> findTrashFilesByUserId(@Param("user_id") UUID user_id, @Param("offset") Integer offset, @Param("limit") Integer limit);
    
    /**
     * 统计用户回收站文件数量
     */
    Integer countTrashFilesByUserId(@Param("user_id") UUID user_id);
    
    /**
     * 查询过期的回收站文件（用于自动清理）
     */
    List<TrashFileEntity> findExpiredTrashFiles();
    
    /**
     * 根据原文件ID查询回收站记录
     */
    TrashFileEntity findTrashFileByFileId(@Param("file_id") UUID file_id, @Param("user_id") UUID user_id);
}
