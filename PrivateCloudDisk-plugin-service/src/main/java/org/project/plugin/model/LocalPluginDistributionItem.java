package org.project.plugin.model;

import java.time.Instant;

/** 返回客户端的本地插件版本清单；下载地址只含短时、一次性不透明授权。 */
public record LocalPluginDistributionItem(
        String installationId,
        String installationScope,
        String pluginId,
        String name,
        String description,
        String versionId,
        String version,
        String runtime,
        String entrypoint,
        String permissionConfig,
        String configJson,
        String packageSha256,
        long packageSize,
        String signature,
        String signingKeyId,
        String downloadUrl,
        Instant expiresAt
) {
}
