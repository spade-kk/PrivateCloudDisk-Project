package org.project.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.project.mapper.QuotaMapper;
import org.project.model.entity.QuotaEntity;
import org.project.service.UserQuotaService;
import org.project.service.ex.QuotaExceededException;
import org.project.service.ex.ServiceException;
import org.project.util.RedisDistributedLock;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 用户配额服务实现
 * <p>
 * 采用「预占 + 提交」模式（Reserve-Commit Pattern）：
 * <pre>
 *   available = total - (used + released)
 *
 *   生命周期：
 *   1. 预占 (preCommit)  → released += size   （上传会话创建时）
 *   2. 提交 (commit)     → released -= size, used += size  （文件可获得时）
 *   3. 回滚 (rollback)   → released -= size   （合并/扫毒失败、取消、超时）
 * </pre>
 * <p>
 * 并发控制：Redis 分布式锁（主） + 乐观锁（兜底）。
 * <ul>
 *   <li>第一步：尝试获取 Redis 分布式锁（key=quota:lock:{userId}），锁超时 5s</li>
 *   <li>第二步：Redis 不可用时，降级为纯乐观锁重试（最多 3 次）</li>
 * </ul>
 * 幂等保证：由消费者层通过事件ID去重实现。
 */
@Slf4j
@Service
public class UserQuotaServiceImpl implements UserQuotaService {

    private static final int MAX_RETRY = 3;
    /** 锁 key 前缀 */
    private static final String LOCK_KEY_PREFIX = "quota:lock:";

    @Autowired
    private QuotaMapper quotaMapper;

    @Autowired
    private RedisDistributedLock redisDistributedLock;

    @Override
    public QuotaEntity findUserQuotaInfo(UUID user_id) {
        return quotaMapper.findQuotaByUserId(user_id);
    }

    @Override
    public Long getAvailableUserQuota(UUID user_id) {
        QuotaEntity quota = quotaMapper.findQuotaByUserId(user_id);
        if (quota == null) {
            return 0L;
        }
        return quota.getTotal_capacity() - quota.getUsed_capacity() - quota.getReleased_capacity();
    }

    // ==================== 预占+提交模式（Redis 分布式锁 + 乐观锁兜底） ====================

    @Override
    @Transactional
    public void preCommitQuota(UUID user_id, long fileSize) {
        log.info("配额预占开始: userId={}, fileSize={}", user_id, fileSize);

        String lockKey = LOCK_KEY_PREFIX + user_id;
        RLock lock = redisDistributedLock.tryLock(lockKey, 0);

        if (lock != null) {
            // Redis 锁获取成功，Redisson Watchdog 自动续期，单次乐观锁即可
            try {
                preCommitQuotaInternal(user_id, fileSize);
            } finally {
                redisDistributedLock.unlock(lock);
            }
        } else {
            // Redis 不可用或锁竞争，降级为纯乐观锁重试
            log.warn("Redis 分布式锁获取失败，降级为乐观锁重试: userId={}", user_id);
            preCommitQuotaOptimisticLock(user_id, fileSize);
        }
    }

    /**
     * 配额预占内部实现（单次乐观锁，无需重试——由 Redis 锁保证无并发冲突）
     */
    private void preCommitQuotaInternal(UUID user_id, long fileSize) {
        QuotaEntity quota = quotaMapper.findQuotaByUserId(user_id);
        if (quota == null) {
            throw new ServiceException("用户配额记录不存在: userId=" + user_id);
        }

        // 检查可用容量
        long available = quota.getTotal_capacity() - quota.getUsed_capacity() - quota.getReleased_capacity();
        if (available < fileSize) {
            log.warn("配额不足: userId={}, available={}, requested={}", user_id, available, fileSize);
            throw new QuotaExceededException(available, fileSize);
        }

        int rows = quotaMapper.increaseQuotaReleasedCapacity(fileSize, user_id, quota.getVersion());
        if (rows == 0) {
            throw new ServiceException("配额预占失败（乐观锁冲突）: userId=" + user_id);
        }
        log.info("配额预占成功: userId={}, fileSize={}", user_id, fileSize);
    }

    /**
     * 配额预占（纯乐观锁重试，Redis 不可用时的降级方案）
     */
    private void preCommitQuotaOptimisticLock(UUID user_id, long fileSize) {
        QuotaEntity quota = quotaMapper.findQuotaByUserId(user_id);
        if (quota == null) {
            throw new ServiceException("用户配额记录不存在: userId=" + user_id);
        }

        long available = quota.getTotal_capacity() - quota.getUsed_capacity() - quota.getReleased_capacity();
        if (available < fileSize) {
            throw new QuotaExceededException(available, fileSize);
        }

        int rows = 0;
        for (int retry = 0; retry < MAX_RETRY; retry++) {
            rows = quotaMapper.increaseQuotaReleasedCapacity(fileSize, user_id, quota.getVersion());
            if (rows > 0) {
                log.info("配额预占成功（乐观锁）: userId={}, fileSize={}, retry={}", user_id, fileSize, retry);
                return;
            }
            // 版本冲突或容量不足，重新查询
            if (retry < MAX_RETRY - 1) {
                quota = quotaMapper.findQuotaByUserId(user_id);
                available = quota.getTotal_capacity() - quota.getUsed_capacity() - quota.getReleased_capacity();
                if (available < fileSize) {
                    throw new QuotaExceededException(available, fileSize);
                }
                log.warn("配额预占乐观锁冲突，重试: userId={}, retry={}", user_id, retry + 1);
            }
        }
        throw new ServiceException("配额预占失败：乐观锁重试耗尽: userId=" + user_id);
    }

    @Override
    @Transactional
    public void commitQuota(UUID user_id, long fileSize) {
        log.info("配额提交开始: userId={}, fileSize={}", user_id, fileSize);

        String lockKey = LOCK_KEY_PREFIX + user_id;
        RLock lock = redisDistributedLock.tryLock(lockKey, 0);

        if (lock != null) {
            try {
                commitQuotaInternal(user_id, fileSize);
            } finally {
                redisDistributedLock.unlock(lock);
            }
        } else {
            log.warn("Redis 分布式锁获取失败，降级为乐观锁重试: userId={}", user_id);
            commitQuotaOptimisticLock(user_id, fileSize);
        }
    }

    private void commitQuotaInternal(UUID user_id, long fileSize) {
        QuotaEntity quota = quotaMapper.findQuotaByUserId(user_id);
        if (quota == null) {
            throw new ServiceException("用户配额记录不存在: userId=" + user_id);
        }

        int rows = quotaMapper.commitQuotaReleasedToUsed(fileSize, user_id, quota.getVersion());
        if (rows == 0) {
            throw new ServiceException("配额提交失败（乐观锁冲突）: userId=" + user_id);
        }
        log.info("配额提交成功: userId={}, fileSize={}", user_id, fileSize);
    }

    private void commitQuotaOptimisticLock(UUID user_id, long fileSize) {
        QuotaEntity quota = quotaMapper.findQuotaByUserId(user_id);
        if (quota == null) {
            throw new ServiceException("用户配额记录不存在: userId=" + user_id);
        }

        // 乐观锁重试
        int rows = 0;
        for (int retry = 0; retry < MAX_RETRY; retry++) {
            rows = quotaMapper.commitQuotaReleasedToUsed(fileSize, user_id, quota.getVersion());
            if (rows > 0) {
                log.info("配额提交成功（乐观锁）: userId={}, fileSize={}, retry={}", user_id, fileSize, retry);
                return;
            }
            if (retry < MAX_RETRY - 1) {
                quota = quotaMapper.findQuotaByUserId(user_id);
                log.warn("配额提交乐观锁冲突，重试: userId={}, retry={}", user_id, retry + 1);
            }
        }
        throw new ServiceException("配额提交失败：乐观锁重试耗尽: userId=" + user_id);
    }

    @Override
    @Transactional
    public void rollbackQuota(UUID user_id, long fileSize) {
        log.info("配额回滚开始: userId={}, fileSize={}", user_id, fileSize);

        String lockKey = LOCK_KEY_PREFIX + user_id;
        RLock lock = redisDistributedLock.tryLock(lockKey, 0);

        if (lock != null) {
            try {
                rollbackQuotaInternal(user_id, fileSize);
            } finally {
                redisDistributedLock.unlock(lock);
            }
        } else {
            log.warn("Redis 分布式锁获取失败，降级为乐观锁重试: userId={}", user_id);
            rollbackQuotaOptimisticLock(user_id, fileSize);
        }
    }

    private void rollbackQuotaInternal(UUID user_id, long fileSize) {
        QuotaEntity quota = quotaMapper.findQuotaByUserId(user_id);
        if (quota == null) {
            log.warn("用户配额记录不存在，跳过回滚: userId={}", user_id);
            return;
        }

        if (quota.getReleased_capacity() < fileSize) {
            log.warn("配额已回滚或无预占记录，跳过: userId={}, released={}, fileSize={}",
                    user_id, quota.getReleased_capacity(), fileSize);
            return;
        }

        int rows = quotaMapper.decreaseQuotaReleasedCapacity(fileSize, user_id, quota.getVersion());
        if (rows == 0) {
            // 乐观锁冲突时重查一次（Redis 锁持有期间极少发生）
            quota = quotaMapper.findQuotaByUserId(user_id);
            if (quota.getReleased_capacity() < fileSize) {
                log.warn("配额已回滚（幂等），跳过: userId={}", user_id);
                return;
            }
            rows = quotaMapper.decreaseQuotaReleasedCapacity(fileSize, user_id, quota.getVersion());
            if (rows == 0) {
                throw new ServiceException("配额回滚失败（乐观锁冲突）: userId=" + user_id);
            }
        }
        log.info("配额回滚成功: userId={}, fileSize={}", user_id, fileSize);
    }

    private void rollbackQuotaOptimisticLock(UUID user_id, long fileSize) {
        QuotaEntity quota = quotaMapper.findQuotaByUserId(user_id);
        if (quota == null) {
            log.warn("用户配额记录不存在，跳过回滚: userId={}", user_id);
            return;
        }

        // 如果 released_capacity 为 0，说明已经回滚过了，幂等跳过
        if (quota.getReleased_capacity() < fileSize) {
            log.warn("配额已回滚或无预占记录，跳过: userId={}, released={}, fileSize={}",
                    user_id, quota.getReleased_capacity(), fileSize);
            return;
        }

        // 乐观锁重试
        int rows = 0;
        for (int retry = 0; retry < MAX_RETRY; retry++) {
            rows = quotaMapper.decreaseQuotaReleasedCapacity(fileSize, user_id, quota.getVersion());
            if (rows > 0) {
                log.info("配额回滚成功（乐观锁）: userId={}, fileSize={}, retry={}", user_id, fileSize, retry);
                return;
            }
            if (retry < MAX_RETRY - 1) {
                quota = quotaMapper.findQuotaByUserId(user_id);
                if (quota.getReleased_capacity() < fileSize) {
                    log.warn("配额已回滚（幂等），跳过: userId={}", user_id);
                    return;
                }
                log.warn("配额回滚乐观锁冲突，重试: userId={}, retry={}", user_id, retry + 1);
            }
        }
        throw new ServiceException("配额回滚失败：乐观锁重试耗尽: userId=" + user_id);
    }

    // ==================== 兼容旧接口 ====================

    @Override
    public void increaseUserUsedQuotaByFileId(UUID file_id, UUID user_id) {
        // 兼容旧接口，由 MQ 消费者调用
        log.info("increaseUserUsedQuotaByFileId: fileId={}, userId={}", file_id, user_id);
    }

    @Override
    public void decreaseUserUsedQuotaByFileId(UUID file_id, UUID user_id) {
        log.info("decreaseUserUsedQuotaByFileId: fileId={}, userId={}", file_id, user_id);
    }

    @Override
    public void increaseUserUsedQuotaBySize(Long size, UUID user_id) {
        log.info("increaseUserUsedQuotaBySize: size={}, userId={}", size, user_id);
        QuotaEntity quota = quotaMapper.findQuotaByUserId(user_id);
        if (quota != null) {
            quotaMapper.increaseQuotaUsedCapacity(size, user_id);
        }
    }

    @Override
    public void decreaseUserUsedQuotaBySize(Long size, UUID user_id) {
        log.info("decreaseUserUsedQuotaBySize: size={}, userId={}", size, user_id);
        QuotaEntity quota = quotaMapper.findQuotaByUserId(user_id);
        if (quota != null) {
            quotaMapper.decreaseQuotaUsedCapacity(size, user_id);
        }
    }
}