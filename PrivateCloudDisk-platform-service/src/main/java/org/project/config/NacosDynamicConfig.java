package org.project.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * Nacos 动态配置示例
 *
 * 企业级用法:
 *   - 在 Nacos 控制台修改配置后，自动热更新到应用
 *   - 无需重启服务即可调整业务参数
 *   - 结合 @RefreshScope 实现 Bean 动态刷新
 *
 * 在 Nacos 中创建 common-config.yaml:
 *   app:
 *     storage:
 *       max-file-size: 1048576000
 *       chunk-size: 5242880
 *     upload:
 *       max-concurrent: 5
 *       expiration-minutes: 120
 */
@Slf4j
@Getter
@Setter
@Component
@RefreshScope
@ConfigurationProperties(prefix = "app")
public class NacosDynamicConfig {

    private StorageConfig storage = new StorageConfig();
    private UploadConfig upload = new UploadConfig();

    @Getter
    @Setter
    public static class StorageConfig {
        /** 最大文件大小 (字节)，默认 1GB */
        private long maxFileSize = 1048576000L;
        /** 分片大小 (字节)，默认 5MB */
        private int chunkSize = 5242880;
    }

    @Getter
    @Setter
    public static class UploadConfig {
        /** 最大并发上传数 */
        private int maxConcurrent = 5;
        /** 上传会话过期时间 (分钟) */
        private int expirationMinutes = 120;
    }
}