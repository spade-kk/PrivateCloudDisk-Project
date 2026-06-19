package org.project.service;

import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.FileMapper;
import org.project.mapper.QuotaMapper;
import org.project.model.entity.FileEntity;
import org.project.model.entity.QuotaEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seata 分布式事务示例 Service
 *
 * 企业级场景:
 *   - 创建文件 + 更新用户配额 (跨库操作)
 *   - 分享文件 + 创建分享链接 + 发送通知 (跨服务操作)
 *   - 订单创建 + 扣减库存 + 发送邮件 (多服务编排)
 *
 * 使用 @GlobalTransactional 替代 @Transactional 实现分布式事务
 * Seata AT 模式自动生成回滚 SQL，业务代码零侵入
 */
@Slf4j
@Service
public class DistributedTransactionExampleService {

    private final FileMapper fileMapper;
    private final QuotaMapper quotaMapper;

    public DistributedTransactionExampleService(FileMapper fileMapper, QuotaMapper quotaMapper) {
        this.fileMapper = fileMapper;
        this.quotaMapper = quotaMapper;
    }

    /**
     * 创建文件并更新用户存储配额
     * 这两个操作需要保证原子性: 要么都成功，要么都回滚
     *
     * @GlobalTransactional 确保跨数据库操作的事务一致性
     */
    @GlobalTransactional(
            name = "create-file-and-update-quota",
            timeoutMills = 30000,
            rollbackFor = Exception.class
    )
    @Transactional(rollbackFor = Exception.class)
    public void createFileAndUpdateQuota(FileEntity file, long fileSize) {
        // 1. 插入文件记录
        fileMapper.insert(file);
        log.info("文件记录已创建: fileId={}", file.getFileId());

        // 2. 更新用户存储配额
        QuotaEntity quota = quotaMapper.selectByUserId(file.getUserId());
        if (quota != null) {
            long newUsed = quota.getUsedStorage() + fileSize;
            quotaMapper.updateUsedStorage(file.getUserId(), newUsed);
            log.info("用户配额已更新: userId={}, usedStorage={}", file.getUserId(), newUsed);
        }

        // 如果这里抛出异常，Seata 会自动回滚文件插入和配额更新
        // 无需手动编写补偿逻辑
    }
}