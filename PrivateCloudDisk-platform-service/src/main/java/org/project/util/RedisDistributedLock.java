package org.project.util;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁（基于 Redisson RLock）
 *
 * <p>Redisson 企业级特性：
 * <ul>
 *   <li><b>Watchdog 自动续期</b>：默认每 10s 续期一次，防止业务执行超时导致锁提前释放</li>
 *   <li><b>可重入锁</b>：同一线程可多次获取同一把锁（基于 Redis Hash + 线程ID）</li>
 *   <li><b>Pub/Sub 唤醒</b>：锁释放时通过 Redis Pub/Sub 通知等待线程，避免忙轮询</li>
 *   <li><b>自动续期</b>：不指定 leaseTime 时，watchdog 每 lockWatchdogTimeout/3 续期一次</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 *   RLock lock = redisDistributedLock.tryLock("quota:lock:" + userId, 100);
 *   if (lock == null) {
 *       // 获取锁失败，降级处理
 *   }
 *   try {
 *       // 业务逻辑
 *   } finally {
 *       redisDistributedLock.unlock(lock);
 *   }
 * </pre>
 */
@Slf4j
@Component
public class RedisDistributedLock {

    private final RedissonClient redissonClient;

    public RedisDistributedLock(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 尝试获取分布式锁（非阻塞，立即返回）
     *
     * <p>不指定 leaseTime，启用 Redisson Watchdog 自动续期。
     * 锁默认超时 30s（lockWatchdogTimeout），每 10s 自动续期一次。
     *
     * @param lockKey     锁的 key
     * @param waitMillis  最大等待时间（毫秒），0 表示立即返回
     * @return RLock 对象（获取成功），null（获取失败）
     */
    public RLock tryLock(String lockKey, long waitMillis) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(waitMillis, TimeUnit.MILLISECONDS);
            if (acquired) {
                log.debug("获取分布式锁成功: key={}", lockKey);
                return lock;
            }
            log.debug("获取分布式锁失败（已被占用）: key={}", lockKey);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取分布式锁被中断: key={}", lockKey);
            return null;
        } catch (Exception e) {
            log.error("获取分布式锁异常: key={}, error={}", lockKey, e.getMessage());
            return null;
        }
    }

    /**
     * 释放分布式锁
     *
     * <p>Redisson RLock.unlock() 内部会校验当前线程是否为锁持有者，
     * 防止误释放其他线程的锁。
     *
     * @param lock tryLock 返回的 RLock 对象
     */
    public void unlock(RLock lock) {
        if (lock == null) {
            return;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放分布式锁: key={}", lock.getName());
            } else {
                log.debug("锁不由当前线程持有，跳过释放: key={}", lock.getName());
            }
        } catch (Exception e) {
            log.error("释放分布式锁异常: key={}, error={}", lock.getName(), e.getMessage());
        }
    }
}