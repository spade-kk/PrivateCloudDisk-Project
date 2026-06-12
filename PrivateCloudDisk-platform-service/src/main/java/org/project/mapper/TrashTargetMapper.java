package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.TrashTargetEntity;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TrashTargetMapper {
    
    /**
     * 添加目标到回收站
     */
    int insertTrashTarget(TrashTargetEntity trashTarget);
    
    /**
     * 从回收站恢复目标
     */
    int deleteTrashTarget(@Param("trash_id") Long trash_id, @Param("user_id") UUID user_id);
    
    /**
     * 查询回收站目标
     */
    TrashTargetEntity findTrashTargetById(@Param("trash_id") Long trash_id, @Param("user_id") UUID user_id);
    
    /**
     * 查询用户回收站目标列表（分页）
     */
    List<TrashTargetEntity> findTrashTargetsByUserId(@Param("user_id") UUID user_id, @Param("offset") Integer offset, @Param("limit") Integer limit);
    
    /**
     * 统计用户回收站目标数量
     */
    Integer countTrashTargetsByUserId(@Param("user_id") UUID user_id);
    
    /**
     * 查询过期的回收站目标（用于自动清理）
     */
    List<TrashTargetEntity> findExpiredTrashTargets();
    
    /**
     * 根据原目标ID查询回收站目标记录
     */
    TrashTargetEntity findTrashTargetByTargetId(@Param("target_id") UUID target_id, @Param("user_id") UUID user_id);
}
