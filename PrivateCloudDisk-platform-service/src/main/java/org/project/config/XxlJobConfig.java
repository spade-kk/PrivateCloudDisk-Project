package org.project.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-Job 分布式任务调度执行器配置
 *
 * 企业级用法:
 *   - 定时任务统一管理，支持失败重试、分片广播、任务依赖
 *   - 任务场景:
 *     - 过期文件清理 (每天凌晨2点)
 *     - 回收站自动清理 (每天凌晨3点)
 *     - 存储配额每日重置 (每天凌晨4点)
 *     - 用户活跃度统计 (每天凌晨5点)
 *     - 系统健康检查 (每5分钟)
 *     - 数据库备份触发 (每天凌晨1点)
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "xxl.job.enabled", havingValue = "true", matchIfMissing = true)
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken}")
    private String accessToken;

    @Value("${xxl.job.executor.appname}")
    private String appname;

    @Value("${xxl.job.executor.port}")
    private int port;

    @Value("${xxl.job.executor.logpath}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info(">>> XXL-Job Executor 初始化: admin={}, app={}", adminAddresses, appname);
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appname);
        executor.setAccessToken(accessToken);
        executor.setPort(port);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }
}