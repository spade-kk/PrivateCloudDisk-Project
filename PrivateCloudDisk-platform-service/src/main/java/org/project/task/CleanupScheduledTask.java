package org.project.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.TrashTargetMapper;
import org.project.mapper.UploadsMapper;
import org.project.model.entity.TrashTargetEntity;
import org.project.service.TrashService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时任务：清理过期数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupScheduledTask {
    
    private final UploadsMapper uploadsMapper;
    private final TrashTargetMapper trashTargetMapper;
    private final TrashService trashService;
    
    /**
     * 清理过期的上传会话
     * 每小时执行一次
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupExpiredUploadsSessions() {
        log.info("开始清理过期的上传会话...");
        try {
            // TODO: 实现清理过期上传会话的逻辑
            // 1. 查询所有过期的上传会话
            // 2. 删除临时分块文件
            // 3. 删除上传会话记录
            log.info("过期上传会话清理完成");
        } catch (Exception e) {
            log.error("清理过期上传会话失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 清理过期的回收站文件
     * 每天凌晨3点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredTrashFiles() {
        log.info("开始清理过期的回收站目标...");
        try {
            List<TrashTargetEntity> expiredFiles = trashTargetMapper.findExpiredTrashTargets();
            log.info("发现{}个过期的回收站目标", expiredFiles.size());
            
            for (TrashTargetEntity trashFile : expiredFiles) {
                try {
                    trashService.permanentDelete(trashFile.getTrash_id(), trashFile.getUser_id());
                    log.info("已删除过期回收站目标: trashId={}, targetId={}",
                            trashFile.getTrash_id(), trashFile.getTarget_id());
                } catch (Exception e) {
                    log.error("删除过期回收站目标失败: trashId={}, error={}",
                            trashFile.getTrash_id(), e.getMessage());
                }
            }
            
            log.info("过期回收站目标清理完成");
        } catch (Exception e) {
            log.error("清理过期回收站目标失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 清理临时分块文件
     * 每6小时执行一次
     */
    @Scheduled(cron = "0 0 */6 * * ?")
    public void cleanupTempChunkFiles() {
        log.info("开始清理临时分块文件...");
        try {
            // TODO: 实现清理临时分块文件的逻辑
            // 1. 扫描临时存储目录
            // 2. 找出超过一定时间未更新的分块文件
            // 3. 删除这些临时文件
            log.info("临时分块文件清理完成");
        } catch (Exception e) {
            log.error("清理临时分块文件失败: {}", e.getMessage(), e);
        }
    }
}
