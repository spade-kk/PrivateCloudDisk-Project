package org.project.config;

import io.seata.spring.annotation.GlobalTransactionScanner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seata 分布式事务配置
 *
 * 企业级用法:
 *   - AT 模式: 自动生成回滚 SQL，零侵入
 *   - TCC 模式: 两阶段提交，适用于高并发场景
 *   - SAGA 模式: 长事务补偿，适用于流程编排
 *
 * 使用方式:
 *   在需要分布式事务的 Service 方法上添加 @GlobalTransactional 注解
 *   例如:
 *     @GlobalTransactional(name = "create-file-and-update-quota", timeoutMills = 30000)
 *     public void createFileAndUpdateQuota(...) { ... }
 */
@Configuration
public class SeataConfig {

    @Value("${seata.tx-service-group}")
    private String txServiceGroup;

    @Value("${spring.application.name}")
    private String applicationId;

    /**
     * 全局事务扫描器
     * 自动扫描带有 @GlobalTransactional 注解的方法
     */
    @Bean
    public GlobalTransactionScanner globalTransactionScanner() {
        return new GlobalTransactionScanner(applicationId, txServiceGroup);
    }
}