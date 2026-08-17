package org.project.plugin.model;

import java.time.LocalDateTime;

/** 用户或空间插件安装投影。 */
public record PluginInstallationRow(
        String installationId,
        String scopeType,
        String scopeId,
        String pluginId,
        String pluginName,
        String pluginType,
        String versionId,
        String version,
        boolean enabled,
        String configJson,
        String grantedPermissionsJson,
        String autoUpdatePolicy,
        LocalDateTime installedAt,
        LocalDateTime updatedAt
) {
}
