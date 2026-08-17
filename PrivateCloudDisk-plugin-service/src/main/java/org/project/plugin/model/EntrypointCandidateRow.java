package org.project.plugin.model;

import java.time.LocalDateTime;

/** Mapper 返回的入口、版本和安装授权联合快照。 */
public record EntrypointCandidateRow(
        String installationId,
        String pluginId,
        String versionId,
        String runtime,
        String modulePath,
        String functionName,
        int priority,
        String conditionJson,
        String permissionJson,
        String grantedPermissionsJson,
        String configJson,
        LocalDateTime installedAt
) {
}
