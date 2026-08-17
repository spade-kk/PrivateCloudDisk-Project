package org.project.plugin.model;

/** 本地插件分发内部投影；objectKey 永不直接返回客户端。 */
public record LocalPluginDistributionRow(
        String installationId,
        String installationScope,
        String pluginId,
        String pluginName,
        String pluginDescription,
        String versionId,
        String version,
        String runtime,
        String entrypoint,
        String permissionConfig,
        String configJson,
        String packageObjectKey,
        String packageSha256,
        long packageSize,
        String signature,
        String signingKeyId
) {
}
