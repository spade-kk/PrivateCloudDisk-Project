package org.project.plugin.model;

/** 发布版本中的能力投影，用于 Capability Hub 动态注册。 */
public record CapabilityProjectionRow(
        String pluginId,
        String versionId,
        String version,
        String runtime,
        String modulePath,
        String capabilityName,
        String description,
        String inputSchemaJson,
        String outputSchemaJson,
        String permissionJson
) {
}
