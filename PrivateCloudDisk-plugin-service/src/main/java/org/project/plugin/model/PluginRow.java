package org.project.plugin.model;

import java.time.LocalDateTime;
/** 插件列表/详情的只读投影。 */
public record PluginRow(
        String pluginId,
        String ownerUserId,
        String name,
        String slug,
        String description,
        String pluginType,
        String visibility,
        String status,
        String latestVersionId,
        long rowVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
