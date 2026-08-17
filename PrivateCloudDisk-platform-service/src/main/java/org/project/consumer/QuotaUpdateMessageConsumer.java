package org.project.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.mapper.QuotaMapper;
import org.project.mapper.SpaceQuotaReservationMapper;
import org.project.model.dto.message.QuotaUpdateMessageDTO;
import org.project.model.entity.QuotaEntity;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 配额更新消息消费者
 * 负责处理配额更新任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaUpdateMessageConsumer {
    
    private final QuotaMapper quotaMapper;
    private final SpaceQuotaReservationMapper spaceQuotaReservationMapper;
    
    /**
     * 消费配额更新消息
     */
    @RabbitListener(queues = RabbitMQConifgure.QUOTA_UPDATE_QUEUE)
    public void consumeQuotaUpdateMessage(QuotaUpdateMessageDTO message) {
        log.info("收到配额更新消息: messageId={}, spaceId={}, userId={}, updateType={}",
                message.getMessage_id(), message.getSpace_id(),
                message.getUser_id(), message.getUpdate_id());
        
        try {
            /*
             * 空间管理能力全量集成（需求五-9/10）：
             * 新消息携带 space_id 时直接更新空间账本；历史消息为空时完整保留原用户配额逻辑。
             */
            if (message.getSpace_id() != null && !message.getSpace_id().isBlank()) {
                if ("FILE_UPLOAD".equals(message.getUpdate_id())
                        || "FILE_DELETE".equals(message.getUpdate_id())) {
                    spaceQuotaReservationMapper.adjustUsage(
                            UUID.fromString(message.getSpace_id()),
                            message.getChange_bytes() == null ? 0L : message.getChange_bytes(),
                            message.getChange_file_count() == null ? 0 : message.getChange_file_count());
                } else {
                    log.info("空间配额重新计算由实际文件聚合接口负责: spaceId={}", message.getSpace_id());
                }
                return;
            }
            QuotaEntity quota = quotaMapper.findQuotaByUserId(UUID.fromString(message.getUser_id()));
            if (quota == null) {
                log.warn("用户配额不存在: userId={}", message.getUser_id());
                return;
            }
            
            switch (message.getUpdate_id()) {
                case "FILE_UPLOAD":
                    handleFileUpload(quota, message);
                    break;
                case "FILE_DELETE":
                    handleFileDelete(quota, message);
                    break;
                case "RECALCULATE":
                    handleRecalculate(quota, message);
                    break;
                default:
                    log.warn("未知的配额更新类型: {}", message.getUpdate_id());
            }
            
        } catch (Exception e) {
            log.error("处理配额更新消息失败: messageId={}, error={}", 
                    message.getMessage_id(), e.getMessage(), e);
        }
    }
    
    /**
     * 处理文件上传的配额更新
     */
    private void handleFileUpload(QuotaEntity quota, QuotaUpdateMessageDTO message) {
        Long newUsedCapacity = quota.getUsed_capacity() + message.getChange_bytes();
        Integer newFileCount = quota.getFile_count() + message.getChange_file_count();
        
        quota.setUsed_capacity(newUsedCapacity);
        quota.setFile_count(newFileCount);
        
        quotaMapper.updateQuota(quota);
        
        log.info("配额更新完成（文件上传）: userId={}, usedCapacity={}, fileCount={}", 
                quota.getUser_id(), newUsedCapacity, newFileCount);
    }
    
    /**
     * 处理文件删除的配额更新
     */
    private void handleFileDelete(QuotaEntity quota, QuotaUpdateMessageDTO message) {
        Long newUsedCapacity = quota.getUsed_capacity() + message.getChange_bytes();
        Integer newFileCount = quota.getFile_count() + message.getChange_file_count();
        
        // 确保不会出现负数
        newUsedCapacity = Math.max(0, newUsedCapacity);
        newFileCount = Math.max(0, newFileCount);
        
        quota.setUsed_capacity(newUsedCapacity);
        quota.setFile_count(newFileCount);
        
        quotaMapper.updateQuota(quota);
        
        log.info("配额更新完成（文件删除）: userId={}, usedCapacity={}, fileCount={}", 
                quota.getUser_id(), newUsedCapacity, newFileCount);
    }
    
    /**
     * 处理配额重新计算
     */
    private void handleRecalculate(QuotaEntity quota, QuotaUpdateMessageDTO message) {
        // TODO: 实现配额重新计算逻辑
        // 1. 查询用户所有文件的总大小
        // 2. 查询用户所有文件的数量
        // 3. 更新配额表
        
        log.info("配额重新计算: userId={}", quota.getUser_id());
    }
}
