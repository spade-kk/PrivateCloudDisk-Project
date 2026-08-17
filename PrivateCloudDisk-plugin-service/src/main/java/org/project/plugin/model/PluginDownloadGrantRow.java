package org.project.plugin.model;

/** 已消费下载授权对应的不可变包投影。 */
public record PluginDownloadGrantRow(
        String versionId,
        String userId,
        String clientId,
        String objectKey,
        String packageSha256,
        long packageSize,
        String signature,
        String signingKeyId
) {
}
