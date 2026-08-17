package org.project.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/** 插件包存储、Runtime 与 Platform 内部调用配置。 */
@ConfigurationProperties(prefix = "pcd")
public record PluginProperties(
        Path storagePath,
        String storageBackend,
        long packageMaxBytes,
        int packageMaxFiles,
        long packageMaxExpandedBytes,
        String runtimeUrl,
        String platformUrl,
        String workflowUrl,
        String clientRegistrationUrl,
        String signingPrivateKeyBase64,
        String signingPublicKeyBase64,
        String signingKeyId,
        long localDownloadGrantTtlSeconds,
        String internalServiceToken
) {
}
