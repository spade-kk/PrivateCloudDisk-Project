package org.project.plugin.model;

import java.time.LocalDateTime;
/** 插件版本状态投影。 */
public record PluginVersionRow(
        String versionId,
        String pluginId,
        String version,
        String runtime,
        String entrypoint,
        String manifestJson,
        String permissionConfig,
        String packageObjectKey,
        String packageSha256,
        long packageSize,
        String validationStatus,
        String validationReportJson,
        boolean immutable,
        LocalDateTime publishedAt,
        LocalDateTime createdAt
) {
}
