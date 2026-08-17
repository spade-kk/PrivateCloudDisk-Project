package org.project.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.mapper.FileMapper;
import org.project.mapper.QuotaMapper;
import org.project.mapper.TrashTargetMapper;
import org.project.mapper.UploadsMapper;
import org.project.model.dto.message.UploadSessionDeleteEvent;
import org.project.model.entity.QuotaEntity;
import org.project.model.entity.TrashTargetEntity;
import org.project.model.entity.UploadsSessionEntity;
import org.project.service.TrashService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
    private final FileMapper fileMapper;
    private final QuotaMapper quotaMapper;
    private final RabbitTemplate rabbitTemplate;

    /** 上传会话过期时间（分钟） */
    private static final long UPLOADS_SESSION_EXPIRE_MINUTES = 30;

    /**
     * 清理过期的上传会话
     * <p>
     * 每 5 分钟执行一次，扫描所有 uploading 状态且超过过期时间的上传会话，
     * 发布 uploads.session.delete 事件通知文件存储服务删除物理分块文件。
     * <p>
     * 文件存储服务删除物理文件后，会同步调用 /business/internal/storage/uploads/{id}/delete-complete
     * 将 canceled 会话记录删除，并发布 uploads.session.deleted 事件 → 释放配额。
     * 合并完成后的 completed 会话由 merge-cleanup 接口独立删除，不进入取消/过期配额回滚链路。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void cleanupExpiredUploadsSessions() {
        log.info("开始扫描过期上传会话...");
        try {
            LocalDateTime expireTime = LocalDateTime.now().minusMinutes(UPLOADS_SESSION_EXPIRE_MINUTES);
            List<UploadsSessionEntity> expiredSessions = uploadsMapper.findExpiredUploadsSessions(expireTime);

            if (expiredSessions.isEmpty()) {
                log.debug("无过期上传会话");
                return;
            }

            log.info("发现 {} 个过期上传会话，开始发布delete事件", expiredSessions.size());

            for (UploadsSessionEntity session : expiredSessions) {
                try {
                    UUID uploadsId = session.getUploads_id();
                    UUID userId = session.getUser_id();

                    // 发布 delete 事件通知文件存储服务删除物理分块文件
                    UploadSessionDeleteEvent deleteEvent = UploadSessionDeleteEvent.builder()
                            .eventId("EVT-EXPIRED-" + uploadsId.toString())
                            .uploadsSessionId(uploadsId)
                            .userId(userId)
                            .spaceId(session.getSpace_id())
                            .fileName(session.getFile_name())
                            .fileSize(session.getFile_size())
                            .fileType(session.getFile_type())
                            .nodeId(session.getNode_id())
                            .eventTime(LocalDateTime.now())
                            .build();

                    rabbitTemplate.convertAndSend(
                            RabbitMQConifgure.UPLOADS_EVENT_EXCHANGE,
                            RabbitMQConifgure.ROUTING_UPLOADS_SESSION_DELETE,
                            deleteEvent
                    );

                    log.info("已发布过期上传会话delete事件: uploadsId={}, fileName={}, fileSize={}",
                            uploadsId, session.getFile_name(), session.getFile_size());

                } catch (Exception e) {
                    log.error("发布过期上传会话delete事件失败: uploadsId={}, error={}",
                            session.getUploads_id(), e.getMessage());
                }
            }

            log.info("过期上传会话扫描完成，共发布 {} 个delete事件", expiredSessions.size());
        } catch (Exception e) {
            log.error("扫描过期上传会话失败: {}", e.getMessage(), e);
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

    @Scheduled(fixedRate = 30000)
    public void cleanupFailStatusFiles() {
        log.debug("开始清理失败状态文件...");
        try {
            /* 一次最多删除1000行数据 防止锁表 */
            Integer rows = fileMapper.cleanFailedStatusFiles();
            if(rows == 0) log.debug("清理失败状态文件成功 但是没有失败的文件数据记录可以删除");
            else log.info("清理失败状态文件成功 已经删除[" + rows + "]行数据");
        } catch (Exception e) {
            log.error("理失败状态文件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 配额对账任务
     * <p>
     * 每天凌晨 2:00 执行，逐用户对比：
     * <ul>
     *   <li>配额表中的 used_capacity（已用容量）</li>
     *   <li>文件表中所有 status='active' 的文件实际大小之和</li>
     * </ul>
     * <p>
     * 对账策略：
     * <ul>
     *   <li>差异 < 1KB：忽略（浮点/舍入误差）</li>
     *   <li>差异 >= 1KB：打印 WARN 日志，记录差异值</li>
     *   <li>差异 >= 10MB：打印 ERROR 日志，需要人工介入</li>
     * </ul>
     * <p>
     * 注意：此任务仅做对账告警，不自动修正数据，避免误操作。
     * 自动修正需在充分验证后由运维手动执行。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void reconcileQuotaUsage() {
        log.info("========== 配额对账任务开始 ==========");
        long startTime = System.currentTimeMillis();

        try {
            List<UUID> userIds = quotaMapper.findAllUserIds();
            log.info("对账用户总数: {}", userIds.size());

            int reconciled = 0;
            int mismatched = 0;
            long totalDiff = 0;

            for (UUID userId : userIds) {
                try {
                    QuotaEntity quota = quotaMapper.findQuotaByUserId(userId);
                    if (quota == null) {
                        continue;
                    }

                    Long actualFileSize = fileMapper.sumActiveFileSizeByUserId(userId);
                    if (actualFileSize == null) {
                        actualFileSize = 0L;
                    }

                    long quotaUsed = quota.getUsed_capacity() != null ? quota.getUsed_capacity() : 0L;
                    long diff = Math.abs(quotaUsed - actualFileSize);

                    reconciled++;

                    if (diff >= 10 * 1024 * 1024) {
                        // 差异 >= 10MB，需要人工介入
                        log.error(
                                "配额严重偏差: userId={}, quotaUsed={}, actualFileSize={}, diff={} bytes ({} MB), "
                                        + "released={}, total={}",
                                userId, quotaUsed, actualFileSize, diff, diff / (1024 * 1024),
                                quota.getReleased_capacity(), quota.getTotal_capacity()
                        );
                        mismatched++;
                        totalDiff += diff;
                    } else if (diff >= 1024) {
                        // 差异 >= 1KB，记录警告
                        log.warn(
                                "配额偏差: userId={}, quotaUsed={}, actualFileSize={}, diff={} bytes ({} KB), "
                                        + "released={}",
                                userId, quotaUsed, actualFileSize, diff, diff / 1024,
                                quota.getReleased_capacity()
                        );
                        mismatched++;
                        totalDiff += diff;
                    } else {
                        log.debug("配额一致: userId={}, used={}, actual={}", userId, quotaUsed, actualFileSize);
                    }

                } catch (Exception e) {
                    log.error("对账用户失败: userId={}, error={}", userId, e.getMessage());
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info(
                    "========== 配额对账任务完成: 对账用户={}, 偏差用户={}, 总偏差={} bytes, 耗时={} ms ==========",
                    reconciled, mismatched, totalDiff, elapsed
            );

        } catch (Exception e) {
            log.error("配额对账任务失败: {}", e.getMessage(), e);
        }
    }
}
