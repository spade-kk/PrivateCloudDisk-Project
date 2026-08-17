package org.project.plugin.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.project.plugin.model.PluginRow;
import org.project.plugin.model.PluginVersionRow;
import org.project.plugin.model.CapabilityProjectionRow;
import org.project.plugin.model.CapabilityResolutionRow;
import org.project.plugin.model.PluginOutboxRow;
import org.project.plugin.model.LocalPluginDistributionRow;
import org.project.plugin.model.PluginDownloadGrantRow;
import org.project.plugin.model.MarketplacePluginRow;
import org.project.plugin.model.PluginRatingRow;
import org.project.plugin.model.PluginInstallationRow;

import java.util.List;

/** Plugin Service 管理域持久层；所有资源查询必须带所有者或安装作用域。 */
@Mapper
public interface PluginManagementMapper {
    @Insert("""
            INSERT INTO pcd_plugin (
                plugin_id, owner_user_id, name, slug, description,
                plugin_type, visibility, status
            ) VALUES (
                UUID_TO_BIN(#{pluginId}), UUID_TO_BIN(#{ownerUserId}), #{name}, #{slug},
                #{description}, #{pluginType}, #{visibility}, 'DRAFT'
            )
            """)
    int insertPlugin(
            @Param("pluginId") String pluginId,
            @Param("ownerUserId") String ownerUserId,
            @Param("name") String name,
            @Param("slug") String slug,
            @Param("description") String description,
            @Param("pluginType") String pluginType,
            @Param("visibility") String visibility
    );

    @Select("""
            SELECT BIN_TO_UUID(plugin_id) AS plugin_id,
                   BIN_TO_UUID(owner_user_id) AS owner_user_id,
                   name, slug, description, plugin_type, visibility, status,
                   BIN_TO_UUID(latest_version_id) AS latest_version_id,
                   row_version, created_at, updated_at
            FROM pcd_plugin
            WHERE plugin_id = UUID_TO_BIN(#{pluginId})
              AND owner_user_id = UUID_TO_BIN(#{ownerUserId})
              AND deleted_at IS NULL
            """)
    PluginRow findOwned(
            @Param("pluginId") String pluginId,
            @Param("ownerUserId") String ownerUserId
    );

    @Select("""
            <script>
            SELECT DISTINCT BIN_TO_UUID(p.plugin_id) AS plugin_id,
                   BIN_TO_UUID(p.owner_user_id) AS owner_user_id,
                   p.name, p.slug, p.description, p.plugin_type, p.visibility, p.status,
                   BIN_TO_UUID(p.latest_version_id) AS latest_version_id,
                   p.row_version, p.created_at, p.updated_at
            FROM pcd_plugin p
            LEFT JOIN pcd_user_plugin up
              ON up.plugin_id = p.plugin_id
             AND up.user_id = UUID_TO_BIN(#{userId})
             AND up.uninstalled_at IS NULL
            LEFT JOIN pcd_space_plugin sp
              ON sp.plugin_id = p.plugin_id
             <if test="spaceId != null and spaceId != ''">
             AND sp.space_id = UUID_TO_BIN(#{spaceId})
             </if>
             AND sp.uninstalled_at IS NULL
            WHERE p.deleted_at IS NULL
              AND (
                p.owner_user_id = UUID_TO_BIN(#{userId})
                OR p.visibility = 'PUBLIC'
                OR up.installation_id IS NOT NULL
                OR sp.installation_id IS NOT NULL
              )
            ORDER BY p.updated_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<PluginRow> listAccessible(
            @Param("userId") String userId,
            @Param("spaceId") String spaceId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Update("""
            UPDATE pcd_plugin
               SET name = COALESCE(#{name}, name),
                   description = COALESCE(#{description}, description),
                   visibility = COALESCE(#{visibility}, visibility),
                   row_version = row_version + 1
             WHERE plugin_id = UUID_TO_BIN(#{pluginId})
               AND owner_user_id = UUID_TO_BIN(#{ownerUserId})
               AND row_version = #{expectedVersion}
               AND status IN ('DRAFT', 'READY')
               AND deleted_at IS NULL
            """)
    int updateDraft(
            @Param("pluginId") String pluginId,
            @Param("ownerUserId") String ownerUserId,
            @Param("name") String name,
            @Param("description") String description,
            @Param("visibility") String visibility,
            @Param("expectedVersion") long expectedVersion
    );

    @Update("""
            UPDATE pcd_plugin
               SET status = 'DELETED', deleted_at = CURRENT_TIMESTAMP(3),
                   row_version = row_version + 1
             WHERE plugin_id = UUID_TO_BIN(#{pluginId})
               AND owner_user_id = UUID_TO_BIN(#{ownerUserId})
               AND deleted_at IS NULL
            """)
    int softDelete(@Param("pluginId") String pluginId, @Param("ownerUserId") String ownerUserId);

    @Insert("""
            INSERT INTO pcd_plugin_version (
                version_id, plugin_id, version, runtime, entrypoint,
                manifest_json, permission_config, supported_platforms, client_types
            ) VALUES (
                UUID_TO_BIN(#{versionId}), UUID_TO_BIN(#{pluginId}), #{version}, #{runtime},
                #{entrypoint}, CAST(#{manifestJson} AS JSON), CAST(#{permissionsJson} AS JSON),
                CAST(#{platformsJson} AS JSON), CAST(#{clientTypesJson} AS JSON)
            )
            """)
    int insertVersion(
            @Param("versionId") String versionId,
            @Param("pluginId") String pluginId,
            @Param("version") String version,
            @Param("runtime") String runtime,
            @Param("entrypoint") String entrypoint,
            @Param("manifestJson") String manifestJson,
            @Param("permissionsJson") String permissionsJson,
            @Param("platformsJson") String platformsJson,
            @Param("clientTypesJson") String clientTypesJson
    );

    @Insert("""
            INSERT INTO pcd_plugin_entrypoint (
                entrypoint_id, version_id, event_type, function_name, priority,
                condition_json, permission_json
            ) VALUES (
                UUID_TO_BIN(#{entrypointId}), UUID_TO_BIN(#{versionId}), #{eventType},
                #{functionName}, #{priority}, CAST(#{conditionJson} AS JSON),
                CAST(#{permissionJson} AS JSON)
            )
            """)
    int insertEntrypoint(
            @Param("entrypointId") String entrypointId,
            @Param("versionId") String versionId,
            @Param("eventType") String eventType,
            @Param("functionName") String functionName,
            @Param("priority") int priority,
            @Param("conditionJson") String conditionJson,
            @Param("permissionJson") String permissionJson
    );

    @Insert("""
            INSERT INTO pcd_plugin_capability (
                capability_id, version_id, capability_name, description,
                input_schema_json, output_schema_json, permission_json
            ) VALUES (
                UUID_TO_BIN(#{capabilityId}), UUID_TO_BIN(#{versionId}), #{name}, #{description},
                CAST(#{inputSchemaJson} AS JSON), CAST(#{outputSchemaJson} AS JSON),
                CAST(#{permissionJson} AS JSON)
            ) ON DUPLICATE KEY UPDATE
                description=VALUES(description),
                input_schema_json=VALUES(input_schema_json),
                output_schema_json=VALUES(output_schema_json),
                permission_json=VALUES(permission_json),
                status='ACTIVE'
            """)
    int insertCapability(
            @Param("capabilityId") String capabilityId,
            @Param("versionId") String versionId,
            @Param("name") String name,
            @Param("description") String description,
            @Param("inputSchemaJson") String inputSchemaJson,
            @Param("outputSchemaJson") String outputSchemaJson,
            @Param("permissionJson") String permissionJson
    );

    @Insert("""
            INSERT IGNORE INTO pcd_plugin_test_entrypoint(
                test_entrypoint_id, version_id, function_name, metadata_json
            ) VALUES (
                UUID_TO_BIN(#{entrypointId}), UUID_TO_BIN(#{versionId}),
                #{functionName}, CAST(#{metadataJson} AS JSON)
            )
            """)
    int insertTestEntrypoint(@Param("entrypointId") String entrypointId,
                             @Param("versionId") String versionId,
                             @Param("functionName") String functionName,
                             @Param("metadataJson") String metadataJson);

    @Select("""
            SELECT COUNT(*) FROM pcd_plugin_test_entrypoint
             WHERE version_id=UUID_TO_BIN(#{versionId}) AND function_name=#{functionName}
            """)
    int countTestEntrypoint(@Param("versionId") String versionId,
                            @Param("functionName") String functionName);

    @Select("""
            SELECT BIN_TO_UUID(v.version_id) AS version_id,
                   BIN_TO_UUID(v.plugin_id) AS plugin_id,
                   v.version, v.runtime, v.entrypoint, CAST(v.manifest_json AS CHAR) AS manifest_json,
                   CAST(v.permission_config AS CHAR) AS permission_config,
                   v.package_object_key,
                   CASE WHEN v.package_sha256 IS NULL THEN NULL ELSE HEX(v.package_sha256) END AS package_sha256,
                   v.package_size, v.validation_status,
                   CAST(v.validation_report_json AS CHAR) AS validation_report_json,
                   v.immutable, v.published_at, v.created_at
            FROM pcd_plugin_version v
            JOIN pcd_plugin p ON p.plugin_id = v.plugin_id
            WHERE p.plugin_id = UUID_TO_BIN(#{pluginId})
              AND p.owner_user_id = UUID_TO_BIN(#{ownerUserId})
              AND v.version = #{version}
              AND p.deleted_at IS NULL
            """)
    PluginVersionRow findOwnedVersion(
            @Param("pluginId") String pluginId,
            @Param("ownerUserId") String ownerUserId,
            @Param("version") String version
    );

    @Select("""
            SELECT BIN_TO_UUID(v.version_id) AS version_id,
                   BIN_TO_UUID(v.plugin_id) AS plugin_id,
                   v.version, v.runtime, v.entrypoint, CAST(v.manifest_json AS CHAR) AS manifest_json,
                   CAST(v.permission_config AS CHAR) AS permission_config,
                   v.package_object_key,
                   CASE WHEN v.package_sha256 IS NULL THEN NULL ELSE HEX(v.package_sha256) END AS package_sha256,
                   v.package_size, v.validation_status,
                   CAST(v.validation_report_json AS CHAR) AS validation_report_json,
                   v.immutable, v.published_at, v.created_at
            FROM pcd_plugin_version v
            WHERE v.plugin_id = UUID_TO_BIN(#{pluginId})
            ORDER BY v.created_at DESC
            """)
    List<PluginVersionRow> listVersions(@Param("pluginId") String pluginId);

    @Select("""
            SELECT BIN_TO_UUID(v.version_id) AS version_id,
                   BIN_TO_UUID(v.plugin_id) AS plugin_id,
                   v.version, v.runtime, v.entrypoint, CAST(v.manifest_json AS CHAR) AS manifest_json,
                   CAST(v.permission_config AS CHAR) AS permission_config,
                   v.package_object_key,
                   CASE WHEN v.package_sha256 IS NULL THEN NULL ELSE HEX(v.package_sha256) END AS package_sha256,
                   v.package_size, v.validation_status,
                   CAST(v.validation_report_json AS CHAR) AS validation_report_json,
                   v.immutable, v.published_at, v.created_at
            FROM pcd_plugin_version v
            JOIN pcd_plugin p ON p.plugin_id = v.plugin_id
            WHERE p.plugin_id = UUID_TO_BIN(#{pluginId})
              AND v.version = #{version}
              AND v.immutable = 1
              AND v.validation_status = 'PASSED'
              AND p.status = 'PUBLISHED'
              AND p.deleted_at IS NULL
              AND (
                p.owner_user_id = UUID_TO_BIN(#{userId})
                OR p.visibility = 'PUBLIC'
                OR EXISTS (
                    SELECT 1 FROM pcd_user_plugin up
                     WHERE up.plugin_id = p.plugin_id
                       AND up.user_id = UUID_TO_BIN(#{userId})
                       AND up.uninstalled_at IS NULL
                )
                OR (
                    #{spaceId} IS NOT NULL AND #{spaceId} != ''
                    AND EXISTS (
                        SELECT 1 FROM pcd_space_plugin sp
                         WHERE sp.plugin_id = p.plugin_id
                           AND sp.space_id = UUID_TO_BIN(#{spaceId})
                           AND sp.uninstalled_at IS NULL
                    )
                )
              )
            """)
    PluginVersionRow findInstallableVersion(
            @Param("pluginId") String pluginId,
            @Param("version") String version,
            @Param("userId") String userId,
            @Param("spaceId") String spaceId
    );

    @Select("""
            SELECT BIN_TO_UUID(v.version_id) AS version_id,
                   BIN_TO_UUID(v.plugin_id) AS plugin_id,
                   v.version, v.runtime, v.entrypoint, CAST(v.manifest_json AS CHAR) AS manifest_json,
                   CAST(v.permission_config AS CHAR) AS permission_config,
                   v.package_object_key,
                   HEX(v.package_sha256) AS package_sha256,
                   v.package_size, v.validation_status,
                   CAST(v.validation_report_json AS CHAR) AS validation_report_json,
                   v.immutable, v.published_at, v.created_at
            FROM pcd_plugin_version v
            JOIN pcd_plugin p ON p.plugin_id = v.plugin_id
            WHERE v.version_id = UUID_TO_BIN(#{versionId})
              AND v.immutable = 1
              AND v.validation_status = 'PASSED'
              AND v.revoked_at IS NULL
              AND p.status = 'PUBLISHED'
              AND p.deleted_at IS NULL
            """)
    PluginVersionRow findRunnableVersion(@Param("versionId") String versionId);

    @Update("""
            UPDATE pcd_plugin_version
               SET package_object_key = #{objectKey},
                   package_sha256 = UNHEX(#{sha256}),
                   package_size = #{packageSize},
                   validation_status = 'PENDING',
                   validation_report_json = NULL
             WHERE version_id = UUID_TO_BIN(#{versionId})
               AND immutable = 0
               AND package_object_key IS NULL
            """)
    int attachPackage(
            @Param("versionId") String versionId,
            @Param("objectKey") String objectKey,
            @Param("sha256") String sha256,
            @Param("packageSize") long packageSize
    );

    /** Web IDE 保存草稿时替换未发布源码包，重新进入待校验状态。 */
    @Update("""
            UPDATE pcd_plugin_version
               SET package_object_key = #{objectKey},
                   package_sha256 = UNHEX(#{sha256}),
                   package_size = #{packageSize},
                   validation_status = 'PENDING',
                   validation_report_json = NULL
             WHERE version_id = UUID_TO_BIN(#{versionId})
               AND immutable = 0
            """)
    int replacePackage(
            @Param("versionId") String versionId,
            @Param("objectKey") String objectKey,
            @Param("sha256") String sha256,
            @Param("packageSize") long packageSize
    );

    @Update("""
            UPDATE pcd_plugin_version
               SET validation_status = #{status},
                   validation_report_json = CAST(#{reportJson} AS JSON)
             WHERE version_id = UUID_TO_BIN(#{versionId})
               AND immutable = 0
            """)
    int updateValidation(
            @Param("versionId") String versionId,
            @Param("status") String status,
            @Param("reportJson") String reportJson
    );

    @Update("""
            UPDATE pcd_plugin_version
               SET immutable = 1, published_at = CURRENT_TIMESTAMP(3)
             WHERE version_id = UUID_TO_BIN(#{versionId})
               AND validation_status = 'PASSED'
               AND package_object_key IS NOT NULL
               AND immutable = 0
            """)
    int publishVersion(@Param("versionId") String versionId);

    @Update("""
            UPDATE pcd_plugin_version
               SET signature = FROM_BASE64(#{signatureBase64}),
                   signing_key_id = #{signingKeyId}
             WHERE version_id = UUID_TO_BIN(#{versionId})
               AND immutable = 0
               AND package_sha256 IS NOT NULL
            """)
    int attachPackageSignature(
            @Param("versionId") String versionId,
            @Param("signatureBase64") String signatureBase64,
            @Param("signingKeyId") String signingKeyId
    );

    @Update("""
            UPDATE pcd_plugin
               SET latest_version_id = UUID_TO_BIN(#{versionId}),
                   status = 'PUBLISHED',
                   row_version = row_version + 1
             WHERE plugin_id = UUID_TO_BIN(#{pluginId})
               AND owner_user_id = UUID_TO_BIN(#{ownerUserId})
               AND deleted_at IS NULL
            """)
    int markPluginPublished(
            @Param("pluginId") String pluginId,
            @Param("ownerUserId") String ownerUserId,
            @Param("versionId") String versionId
    );

    @Insert("""
            INSERT INTO pcd_user_plugin (
                installation_id, user_id, plugin_id, version_id,
                config_json, granted_permissions_json, auto_update_policy
            ) VALUES (
                UUID_TO_BIN(#{installationId}), UUID_TO_BIN(#{userId}),
                UUID_TO_BIN(#{pluginId}), UUID_TO_BIN(#{versionId}),
                CAST(#{configJson} AS JSON), CAST(#{permissionsJson} AS JSON), #{policy}
            )
            ON DUPLICATE KEY UPDATE
                version_id = VALUES(version_id),
                config_json = VALUES(config_json),
                granted_permissions_json = VALUES(granted_permissions_json),
                auto_update_policy = VALUES(auto_update_policy),
                enabled = 1,
                uninstalled_at = NULL,
                row_version = row_version + 1
            """)
    int installForUser(
            @Param("installationId") String installationId,
            @Param("userId") String userId,
            @Param("pluginId") String pluginId,
            @Param("versionId") String versionId,
            @Param("configJson") String configJson,
            @Param("permissionsJson") String permissionsJson,
            @Param("policy") String policy
    );

    @Insert("""
            INSERT INTO pcd_space_plugin (
                installation_id, space_id, plugin_id, version_id, config_json,
                granted_permissions_json, auto_update_policy, installed_by
            ) VALUES (
                UUID_TO_BIN(#{installationId}), UUID_TO_BIN(#{spaceId}),
                UUID_TO_BIN(#{pluginId}), UUID_TO_BIN(#{versionId}),
                CAST(#{configJson} AS JSON), CAST(#{permissionsJson} AS JSON), #{policy},
                UUID_TO_BIN(#{installedBy})
            )
            ON DUPLICATE KEY UPDATE
                version_id = VALUES(version_id),
                config_json = VALUES(config_json),
                granted_permissions_json = VALUES(granted_permissions_json),
                auto_update_policy = VALUES(auto_update_policy),
                installed_by = VALUES(installed_by),
                enabled = 1,
                uninstalled_at = NULL,
                row_version = row_version + 1
            """)
    int installForSpace(
            @Param("installationId") String installationId,
            @Param("spaceId") String spaceId,
            @Param("installedBy") String installedBy,
            @Param("pluginId") String pluginId,
            @Param("versionId") String versionId,
            @Param("configJson") String configJson,
            @Param("permissionsJson") String permissionsJson,
            @Param("policy") String policy
    );

    @Update("""
            UPDATE pcd_user_plugin
               SET enabled = #{enabled}, row_version = row_version + 1
             WHERE installation_id = UUID_TO_BIN(#{installationId})
               AND user_id = UUID_TO_BIN(#{userId})
               AND uninstalled_at IS NULL
            """)
    int updateUserInstallation(
            @Param("installationId") String installationId,
            @Param("userId") String userId,
            @Param("enabled") boolean enabled
    );

    @Update("""
            UPDATE pcd_user_plugin
               SET enabled = 0, uninstalled_at = CURRENT_TIMESTAMP(3),
                   row_version = row_version + 1
             WHERE installation_id = UUID_TO_BIN(#{installationId})
               AND user_id = UUID_TO_BIN(#{userId})
               AND uninstalled_at IS NULL
            """)
    int uninstallForUser(
            @Param("installationId") String installationId,
            @Param("userId") String userId
    );

    @Select("""
            <script>
            SELECT BIN_TO_UUID(i.installation_id) installation_id,
                   i.scope_type, BIN_TO_UUID(i.scope_id) scope_id,
                   BIN_TO_UUID(p.plugin_id) plugin_id, p.name plugin_name,
                   p.plugin_type, BIN_TO_UUID(v.version_id) version_id, v.version,
                   i.enabled, i.config_json, i.granted_permissions_json,
                   i.auto_update_policy, i.installed_at, i.updated_at
              FROM (
                SELECT installation_id, 'USER' scope_type, user_id scope_id,
                       plugin_id, version_id, enabled,
                       CAST(config_json AS CHAR) config_json,
                       CAST(granted_permissions_json AS CHAR) granted_permissions_json,
                       auto_update_policy, installed_at, updated_at
                  FROM pcd_user_plugin
                 WHERE user_id=UUID_TO_BIN(#{userId}) AND uninstalled_at IS NULL
                <if test="spaceId != null and spaceId != ''">
                UNION ALL
                SELECT installation_id, 'SPACE' scope_type, space_id scope_id,
                       plugin_id, version_id, enabled,
                       CAST(config_json AS CHAR) config_json,
                       CAST(granted_permissions_json AS CHAR) granted_permissions_json,
                       auto_update_policy, installed_at, updated_at
                  FROM pcd_space_plugin
                 WHERE space_id=UUID_TO_BIN(#{spaceId}) AND uninstalled_at IS NULL
                </if>
              ) i
              JOIN pcd_plugin p ON p.plugin_id=i.plugin_id
              JOIN pcd_plugin_version v ON v.version_id=i.version_id
             WHERE p.deleted_at IS NULL
             ORDER BY i.scope_type DESC, i.updated_at DESC
            </script>
            """)
    List<PluginInstallationRow> listInstallations(
            @Param("userId") String userId,
            @Param("spaceId") String spaceId
    );

    @Update("""
            UPDATE pcd_space_plugin
               SET enabled=#{enabled}, row_version=row_version+1
             WHERE installation_id=UUID_TO_BIN(#{installationId})
               AND space_id=UUID_TO_BIN(#{spaceId}) AND uninstalled_at IS NULL
            """)
    int updateSpaceInstallation(
            @Param("installationId") String installationId,
            @Param("spaceId") String spaceId,
            @Param("enabled") boolean enabled
    );

    @Update("""
            UPDATE pcd_space_plugin
               SET enabled=0, uninstalled_at=CURRENT_TIMESTAMP(3), row_version=row_version+1
             WHERE installation_id=UUID_TO_BIN(#{installationId})
               AND space_id=UUID_TO_BIN(#{spaceId}) AND uninstalled_at IS NULL
            """)
    int uninstallForSpace(
            @Param("installationId") String installationId,
            @Param("spaceId") String spaceId
    );

    @Select("""
            <script>
            SELECT COALESCE(BIN_TO_UUID(sp.installation_id), BIN_TO_UUID(up.installation_id))
                       AS installation_id,
                   CASE WHEN sp.installation_id IS NULL THEN 'USER' ELSE 'SPACE' END
                       AS installation_scope,
                   BIN_TO_UUID(p.plugin_id) AS plugin_id,
                   p.name AS plugin_name,
                   p.description AS plugin_description,
                   BIN_TO_UUID(v.version_id) AS version_id,
                   v.version, v.runtime, v.entrypoint,
                   CAST(v.permission_config AS CHAR) AS permission_config,
                   COALESCE(CAST(sp.config_json AS CHAR), CAST(up.config_json AS CHAR), '{}')
                       AS config_json,
                   v.package_object_key,
                   HEX(v.package_sha256) AS package_sha256,
                   v.package_size,
                   TO_BASE64(v.signature) AS signature,
                   v.signing_key_id
              FROM pcd_plugin p
              JOIN pcd_plugin_version v ON v.version_id=p.latest_version_id
              LEFT JOIN pcd_space_plugin sp
                ON sp.plugin_id=p.plugin_id
               <if test="spaceId != null and spaceId != ''">
               AND sp.space_id=UUID_TO_BIN(#{spaceId})
               </if>
               <if test="spaceId == null or spaceId == ''">
               AND 1=0
               </if>
               AND sp.enabled=1 AND sp.uninstalled_at IS NULL
              LEFT JOIN pcd_user_plugin up
                ON up.plugin_id=p.plugin_id
               AND up.user_id=UUID_TO_BIN(#{userId})
               AND up.enabled=1 AND up.uninstalled_at IS NULL
             WHERE p.plugin_type='LOCAL_PLUGIN'
               AND p.status='PUBLISHED' AND p.deleted_at IS NULL
               AND v.immutable=1 AND v.validation_status='PASSED'
               AND v.revoked_at IS NULL AND v.signature IS NOT NULL
               AND (sp.installation_id IS NOT NULL OR up.installation_id IS NOT NULL)
               AND JSON_CONTAINS(v.supported_platforms, JSON_QUOTE(#{platform}))
               AND JSON_CONTAINS(v.client_types, JSON_QUOTE(#{clientType}))
             ORDER BY CASE WHEN sp.installation_id IS NULL THEN 1 ELSE 0 END,
                      p.name, v.version
            </script>
            """)
    List<LocalPluginDistributionRow> listLocalDistributions(
            @Param("userId") String userId,
            @Param("spaceId") String spaceId,
            @Param("platform") String platform,
            @Param("clientType") String clientType
    );

    @Insert("""
            INSERT INTO pcd_plugin_download_grant (
                grant_id, token_sha256, version_id, user_id, space_id,
                client_id, expires_at
            ) VALUES (
                UUID_TO_BIN(#{grantId}), UNHEX(#{tokenSha256}),
                UUID_TO_BIN(#{versionId}), UUID_TO_BIN(#{userId}),
                IF(#{spaceId} IS NULL, NULL, UUID_TO_BIN(#{spaceId})),
                #{clientId}, #{expiresAt}
            )
            """)
    int insertDownloadGrant(
            @Param("grantId") String grantId,
            @Param("tokenSha256") String tokenSha256,
            @Param("versionId") String versionId,
            @Param("userId") String userId,
            @Param("spaceId") String spaceId,
            @Param("clientId") String clientId,
            @Param("expiresAt") java.time.LocalDateTime expiresAt
    );

    @Update("""
            UPDATE pcd_plugin_download_grant
               SET download_count=download_count+1,
                   consumed_at=CURRENT_TIMESTAMP(3)
             WHERE token_sha256=UNHEX(#{tokenSha256})
               AND user_id=UUID_TO_BIN(#{userId})
               AND client_id=#{clientId}
               AND expires_at>CURRENT_TIMESTAMP(3)
               AND download_count<max_downloads
            """)
    int consumeDownloadGrant(
            @Param("tokenSha256") String tokenSha256,
            @Param("userId") String userId,
            @Param("clientId") String clientId
    );

    @Select("""
            SELECT BIN_TO_UUID(g.version_id) AS version_id,
                   BIN_TO_UUID(g.user_id) AS user_id,
                   g.client_id,
                   v.package_object_key AS object_key,
                   HEX(v.package_sha256) AS package_sha256,
                   v.package_size,
                   TO_BASE64(v.signature) AS signature,
                   v.signing_key_id
              FROM pcd_plugin_download_grant g
              JOIN pcd_plugin_version v ON v.version_id=g.version_id
             WHERE g.token_sha256=UNHEX(#{tokenSha256})
               AND g.user_id=UUID_TO_BIN(#{userId})
               AND g.client_id=#{clientId}
               AND g.consumed_at IS NOT NULL
               AND v.immutable=1 AND v.revoked_at IS NULL
            """)
    PluginDownloadGrantRow findConsumedDownloadGrant(
            @Param("tokenSha256") String tokenSha256,
            @Param("userId") String userId,
            @Param("clientId") String clientId
    );

    @Insert("""
            INSERT INTO pcd_plugin_marketplace_listing(
                plugin_id, review_status, pricing_model, published_by
            ) VALUES (
                UUID_TO_BIN(#{pluginId}), 'PENDING', 'FREE', UUID_TO_BIN(#{ownerUserId})
            )
            ON DUPLICATE KEY UPDATE
                review_status='PENDING', published_by=VALUES(published_by),
                published_at=NULL
            """)
    int submitMarketplaceReview(
            @Param("pluginId") String pluginId,
            @Param("ownerUserId") String ownerUserId
    );

    @Update("""
            UPDATE pcd_plugin_marketplace_listing l
            JOIN pcd_plugin p ON p.plugin_id=l.plugin_id
               SET l.review_status=#{status},
                   l.published_at=IF(#{status}='APPROVED', CURRENT_TIMESTAMP(3), NULL)
             WHERE l.plugin_id=UUID_TO_BIN(#{pluginId})
               AND p.status='PUBLISHED' AND p.visibility='PUBLIC'
            """)
    int reviewMarketplace(
            @Param("pluginId") String pluginId,
            @Param("status") String status
    );

    @Select("""
            <script>
            SELECT BIN_TO_UUID(p.plugin_id) plugin_id,
                   p.name, p.slug, p.description, p.plugin_type,
                   COALESCE(p.category_code, 'other') category_code,
                   COALESCE(p.author_display_name, '平台开发者') author_display_name,
                   v.version latest_version,
                   CAST(v.permission_config AS CHAR) permission_config,
                   CAST(v.supported_platforms AS CHAR) supported_platforms_json,
                   CAST(v.client_types AS CHAR) client_types_json,
                   COALESCE((
                     SELECT JSON_ARRAYAGG(c.capability_name)
                       FROM pcd_plugin_capability c
                      WHERE c.version_id=v.version_id AND c.status='ACTIVE'
                   ), JSON_ARRAY()) capabilities_json,
                   COALESCE(AVG(r.rating), 0) average_rating,
                   COUNT(DISTINCT r.user_id) rating_count,
                   (
                     SELECT COUNT(*) FROM (
                       SELECT up.installation_id, up.plugin_id FROM pcd_user_plugin up
                        WHERE up.uninstalled_at IS NULL
                       UNION ALL
                       SELECT sp.installation_id, sp.plugin_id FROM pcd_space_plugin sp
                        WHERE sp.uninstalled_at IS NULL
                     ) installs WHERE installs.plugin_id=p.plugin_id
                   ) installation_count,
                   l.published_at
              FROM pcd_plugin_marketplace_listing l
              JOIN pcd_plugin p ON p.plugin_id=l.plugin_id
              JOIN pcd_plugin_version v ON v.version_id=p.latest_version_id
              LEFT JOIN pcd_plugin_rating r
                ON r.plugin_id=p.plugin_id AND r.status='VISIBLE'
             WHERE l.review_status='APPROVED'
               AND p.status='PUBLISHED' AND p.visibility='PUBLIC'
               AND p.deleted_at IS NULL AND v.revoked_at IS NULL
               <if test="type != null and type != ''">AND p.plugin_type=#{type}</if>
               <if test="category != null and category != ''">AND p.category_code=#{category}</if>
               <if test="query != null and query != ''">
                 AND (p.name LIKE CONCAT('%', #{query}, '%')
                      OR p.description LIKE CONCAT('%', #{query}, '%')
                      OR p.slug LIKE CONCAT('%', #{query}, '%'))
               </if>
             GROUP BY p.plugin_id, p.name, p.slug, p.description, p.plugin_type,
                      p.category_code, p.author_display_name, v.version,
                      v.permission_config, v.supported_platforms, v.client_types,
                      v.version_id, l.published_at
             ORDER BY l.published_at DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<MarketplacePluginRow> listMarketplace(
            @Param("type") String type,
            @Param("category") String category,
            @Param("query") String query,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Insert("""
            INSERT INTO pcd_plugin_rating(plugin_id, user_id, rating, comment_text)
            VALUES(UUID_TO_BIN(#{pluginId}), UUID_TO_BIN(#{userId}), #{rating}, #{comment})
            ON DUPLICATE KEY UPDATE
                rating=VALUES(rating), comment_text=VALUES(comment_text),
                status='VISIBLE', updated_at=CURRENT_TIMESTAMP(3)
            """)
    int upsertRating(
            @Param("pluginId") String pluginId,
            @Param("userId") String userId,
            @Param("rating") int rating,
            @Param("comment") String comment
    );

    @Select("""
            SELECT BIN_TO_UUID(user_id) user_id, rating, comment_text,
                   created_at, updated_at
              FROM pcd_plugin_rating
             WHERE plugin_id=UUID_TO_BIN(#{pluginId}) AND status='VISIBLE'
             ORDER BY updated_at DESC
             LIMIT #{limit} OFFSET #{offset}
            """)
    List<PluginRatingRow> listRatings(
            @Param("pluginId") String pluginId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            SELECT COUNT(*)
              FROM pcd_plugin_marketplace_listing l
              JOIN pcd_plugin p ON p.plugin_id=l.plugin_id
             WHERE l.plugin_id=UUID_TO_BIN(#{pluginId})
               AND l.review_status='APPROVED'
               AND p.status='PUBLISHED' AND p.visibility='PUBLIC'
               AND p.deleted_at IS NULL
            """)
    int countApprovedMarketplacePlugin(@Param("pluginId") String pluginId);

    @Select("""
            SELECT BIN_TO_UUID(v.plugin_id) plugin_id,
                   BIN_TO_UUID(v.version_id) version_id,
                   v.version, v.runtime, v.entrypoint module_path,
                   c.capability_name, c.description,
                   CAST(c.input_schema_json AS CHAR) input_schema_json,
                   CAST(c.output_schema_json AS CHAR) output_schema_json,
                   CAST(c.permission_json AS CHAR) permission_json
              FROM pcd_plugin_capability c
              JOIN pcd_plugin_version v ON v.version_id=c.version_id
             WHERE c.version_id=UUID_TO_BIN(#{versionId}) AND c.status='ACTIVE'
             ORDER BY c.capability_name
            """)
    List<CapabilityProjectionRow> listCapabilities(@Param("versionId") String versionId);

    @Select("""
            SELECT COALESCE(BIN_TO_UUID(sp.installation_id), BIN_TO_UUID(up.installation_id))
                       installation_id,
                   BIN_TO_UUID(p.plugin_id) plugin_id,
                   BIN_TO_UUID(v.version_id) version_id,
                   v.runtime, v.entrypoint module_path,
                   c.capability_name function_name,
                   CAST(c.permission_json AS CHAR) capability_permissions_json,
                   COALESCE(CAST(sp.granted_permissions_json AS CHAR),
                            CAST(up.granted_permissions_json AS CHAR)) granted_permissions_json,
                   COALESCE(CAST(sp.config_json AS CHAR), CAST(up.config_json AS CHAR)) config_json
              FROM pcd_plugin p
              JOIN pcd_plugin_version v ON v.plugin_id=p.plugin_id
              JOIN pcd_plugin_capability c ON c.version_id=v.version_id
              LEFT JOIN pcd_space_plugin sp
                ON sp.plugin_id=p.plugin_id AND sp.version_id=v.version_id
               AND sp.space_id=IF(#{spaceId} IS NULL, NULL, UUID_TO_BIN(#{spaceId}))
               AND sp.enabled=1 AND sp.uninstalled_at IS NULL
              LEFT JOIN pcd_user_plugin up
                ON up.plugin_id=p.plugin_id AND up.version_id=v.version_id
               AND up.user_id=UUID_TO_BIN(#{userId})
               AND up.enabled=1 AND up.uninstalled_at IS NULL
             WHERE p.plugin_id=UUID_TO_BIN(#{pluginId})
               AND c.capability_name=#{capabilityName}
               AND CAST(SUBSTRING_INDEX(v.version, '.', 1) AS UNSIGNED)=#{major}
               AND p.status='PUBLISHED' AND p.deleted_at IS NULL
               AND v.immutable=1 AND v.validation_status='PASSED' AND v.revoked_at IS NULL
               AND c.status='ACTIVE'
               AND (
                    (#{spaceId} IS NOT NULL AND sp.installation_id IS NOT NULL)
                    OR up.installation_id IS NOT NULL
               )
             ORDER BY (sp.installation_id IS NOT NULL) DESC, v.published_at DESC
             LIMIT 1
            """)
    CapabilityResolutionRow resolveCapability(
            @Param("pluginId") String pluginId,
            @Param("capabilityName") String capabilityName,
            @Param("major") int major,
            @Param("userId") String userId,
            @Param("spaceId") String spaceId
    );

    @Insert("""
            INSERT INTO pcd_plugin_outbox(
                event_id, aggregate_id, event_type, payload_json
            ) VALUES (
                UUID_TO_BIN(#{eventId}), UUID_TO_BIN(#{aggregateId}),
                #{eventType}, CAST(#{payloadJson} AS JSON)
            )
            """)
    int insertOutbox(
            @Param("eventId") String eventId,
            @Param("aggregateId") String aggregateId,
            @Param("eventType") String eventType,
            @Param("payloadJson") String payloadJson
    );

    @Select("""
            SELECT BIN_TO_UUID(event_id)
              FROM pcd_plugin_outbox
             WHERE status IN ('PENDING','FAILED')
               AND next_retry_at <= CURRENT_TIMESTAMP(3)
             ORDER BY created_at, event_id
             LIMIT 1
             FOR UPDATE SKIP LOCKED
            """)
    String selectOutboxForUpdate();

    @Update("""
            UPDATE pcd_plugin_outbox SET status='PUBLISHING'
             WHERE event_id=UUID_TO_BIN(#{eventId})
               AND status IN ('PENDING','FAILED')
            """)
    int claimOutbox(@Param("eventId") String eventId);

    @Select("""
            SELECT BIN_TO_UUID(event_id) event_id,
                   CAST(payload_json AS CHAR) payload_json, attempt
              FROM pcd_plugin_outbox
             WHERE event_id=UUID_TO_BIN(#{eventId})
            """)
    PluginOutboxRow findOutbox(@Param("eventId") String eventId);

    @Update("""
            UPDATE pcd_plugin_outbox
               SET status='SENT', published_at=CURRENT_TIMESTAMP(3)
             WHERE event_id=UUID_TO_BIN(#{eventId}) AND status='PUBLISHING'
            """)
    int markOutboxSent(@Param("eventId") String eventId);

    @Update("""
            UPDATE pcd_plugin_outbox
               SET status='FAILED', attempt=attempt+1,
                   next_retry_at=DATE_ADD(
                       CURRENT_TIMESTAMP(3),
                       INTERVAL LEAST(300, POW(2, LEAST(attempt+1, 8))) SECOND
                   )
             WHERE event_id=UUID_TO_BIN(#{eventId}) AND status='PUBLISHING'
            """)
    int markOutboxFailed(@Param("eventId") String eventId);

    @Update("""
            UPDATE pcd_plugin_outbox SET status='FAILED'
             WHERE status='PUBLISHING'
               AND created_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 5 MINUTE)
            """)
    int recoverOutboxPublishing();

}
