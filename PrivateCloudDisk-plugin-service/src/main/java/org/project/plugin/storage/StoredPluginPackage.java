package org.project.plugin.storage;

/** 插件包封存结果；objectKey 是逻辑键，绝不向客户端暴露物理绝对路径。 */
public record StoredPluginPackage(
        String objectKey,
        String sha256,
        long packageBytes,
        int fileCount,
        long expandedBytes,
        String manifestYaml,
        String entrypointSource
) {
}
