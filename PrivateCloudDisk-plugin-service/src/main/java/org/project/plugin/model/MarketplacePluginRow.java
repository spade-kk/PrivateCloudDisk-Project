package org.project.plugin.model;

import java.time.LocalDateTime;

/** 插件市场公开投影；不包含所有者内部配置、源码路径或安装授权。 */
public record MarketplacePluginRow(
        String pluginId,
        String name,
        String slug,
        String description,
        String pluginType,
        String categoryCode,
        String authorDisplayName,
        String latestVersion,
        String permissionConfig,
        String supportedPlatformsJson,
        String clientTypesJson,
        String capabilitiesJson,
        double averageRating,
        long ratingCount,
        long installationCount,
        LocalDateTime publishedAt
) {
}
