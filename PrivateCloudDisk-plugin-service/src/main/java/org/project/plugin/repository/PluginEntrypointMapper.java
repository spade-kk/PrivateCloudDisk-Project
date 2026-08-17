package org.project.plugin.repository;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.project.plugin.model.EntrypointCandidateRow;

import java.util.List;

/** 插件入口检索 Mapper；只返回已发布、未撤销且安装已启用的版本。 */
public interface PluginEntrypointMapper {

    @Select("""
            <script>
            SELECT
                BIN_TO_UUID(i.installation_id) installationId,
                BIN_TO_UUID(i.plugin_id) pluginId,
                BIN_TO_UUID(i.version_id) versionId,
                v.runtime runtime,
                v.entrypoint modulePath,
                e.function_name functionName,
                e.priority priority,
                JSON_UNQUOTE(e.condition_json) conditionJson,
                JSON_UNQUOTE(e.permission_json) permissionJson,
                JSON_UNQUOTE(i.granted_permissions_json) grantedPermissionsJson,
                JSON_UNQUOTE(i.config_json) configJson,
                i.installed_at installedAt
            FROM
            <choose>
                <when test="spaceId != null and spaceId != ''">
                    pcd_space_plugin i
                </when>
                <otherwise>
                    pcd_user_plugin i
                </otherwise>
            </choose>
            JOIN pcd_plugin p ON p.plugin_id=i.plugin_id
            JOIN pcd_plugin_version v ON v.version_id=i.version_id
            JOIN pcd_plugin_entrypoint e ON e.version_id=v.version_id
            WHERE i.enabled=1
              AND p.plugin_type='CLOUD_PLUGIN'
              AND p.status='PUBLISHED'
              AND v.validation_status='PASSED'
              AND v.immutable=1
              AND v.revoked_at IS NULL
              AND e.enabled=1
              AND e.event_type=#{eventType}
            <choose>
                <when test="spaceId != null and spaceId != ''">
                    AND i.space_id=UUID_TO_BIN(#{spaceId})
                </when>
                <otherwise>
                    AND i.user_id=UUID_TO_BIN(#{userId})
                </otherwise>
            </choose>
            ORDER BY e.priority ASC, i.installed_at ASC, i.installation_id ASC
            </script>
            """)
    List<EntrypointCandidateRow> findCandidates(
            @Param("eventType") String eventType,
            @Param("userId") String userId,
            @Param("spaceId") String spaceId
    );
}
