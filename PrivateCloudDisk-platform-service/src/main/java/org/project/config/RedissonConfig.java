package org.project.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁配置
 *
 * <p>Redisson 是一个企业级 Redis 客户端，提供：
 * <ul>
 *   <li>RLock：可重入分布式锁（基于 Redis Hash + Pub/Sub）</li>
 *   <li>Watchdog：自动续期，防止业务执行超时导致锁提前释放</li>
 *   <li>FairLock：公平锁（按请求顺序获取）</li>
 *   <li>RedLock：多节点红锁算法（高可用）</li>
 *   <li>RSemaphore：分布式信号量</li>
 * </ul>
 *
 * <p>本配置使用 application.properties 中的 spring.data.redis.* 自动构建连接。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    /**
     * Redisson 客户端 Bean
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        String address = String.format("redis://%s:%d", redisHost, redisPort);

        config.useSingleServer()
                .setAddress(address)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setDatabase(redisDatabase)
                .setConnectionPoolSize(16)
                .setConnectionMinimumIdleSize(4)
                .setConnectTimeout(5000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        // 锁的看门狗超时时间（默认 30s），超时自动续期
        // 设置为 10s：业务超过 10s 未执行完，watchdog 自动续期
        config.setLockWatchdogTimeout(10000);

        return Redisson.create(config);
    }

    /**
     * RedissonConnectionFactory
     * <p>使 Spring Data Redis 的 RedisTemplate 也能复用 Redisson 连接。
     * 如需同时使用两者，取消注释。
     */
    // @Bean
    // public RedissonConnectionFactory redissonConnectionFactory(RedissonClient redissonClient) {
    //     return new RedissonConnectionFactory(redissonClient);
    // }
}