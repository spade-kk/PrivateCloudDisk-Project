package org.project.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁服务 (基于 Redisson)
 *
 * 企业级用法:
 *   - 防止文件重复上传
 *   - 防止并发创建同名文件夹
 *   - 分布式定时任务互斥执行
 *   - 缓存击穿保护 (缓存重建锁)
 *
 * 使用示例:
 *   distributedLockService.executeWithLock("file:upload:123", 10, 30, () -> {
 *       // 临界区代码
 *       return processUpload();
 *   });
 */
@Slf4j
@Service
public class DistributedLockService {

    private final RedissonClient redissonClient;

    public DistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 获取分布式锁并执行任务
     *
     * @param lockKey    锁的 key
     * @param waitTime   等待获取锁的超时时间 (秒)
     * @param leaseTime  锁的自动释放时间 (秒)，-1 表示看门狗自动续期
     * @param supplier   要执行的业务逻辑
     * @return 业务执行结果
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock("pcd:lock:" + lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("获取分布式锁失败: lockKey={}, waitTime={}s", lockKey, waitTime);
                throw new RuntimeException("操作过于频繁，请稍后再试");
            }
            log.debug("获取分布式锁成功: lockKey={}", lockKey);
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断: " + lockKey, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放分布式锁: lockKey={}", lockKey);
            }
        }
    }

    /**
     * 尝试获取锁 (非阻塞)
     *
     * @param lockKey 锁的 key
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey) {
        RLock lock = redissonClient.getLock("pcd:lock:" + lockKey);
        return lock.tryLock();
    }

    /**
     * 释放锁
     */
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock("pcd:lock:" + lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}