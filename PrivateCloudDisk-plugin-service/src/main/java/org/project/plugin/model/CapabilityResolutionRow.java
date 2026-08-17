package org.project.plugin.model;

/** 工作流能力调用前的安装、包入口与授权投影。 */
public record CapabilityResolutionRow(
        String installationId,
        String pluginId,
        String versionId,
        String runtime,
        String modulePath,
        String functionName,
        String capabilityPermissionsJson,
        String grantedPermissionsJson,
        String configJson
) {
}
