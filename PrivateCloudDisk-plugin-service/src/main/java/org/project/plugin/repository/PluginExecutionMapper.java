package org.project.plugin.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.project.plugin.model.ExecutionRecordRequest;
import org.project.plugin.model.PluginExecutionRow;
import org.project.plugin.model.PluginExecutionStats;

import java.util.List;

/** 插件执行日志持久层；写入以 execution_id 幂等。 */
@Mapper
public interface PluginExecutionMapper {

    @Insert("""
            INSERT IGNORE INTO pcd_plugin_execution_log (
                execution_id, plugin_id, version_id, installation_id,
                user_id, space_id, client_id, trigger_event, trigger_source,
                execution_status, started_at, ended_at, duration_ms,
                output_summary, error_code, correlation_id, causation_id,
                idempotency_key
            ) VALUES (
                UUID_TO_BIN(#{item.executionId}), UUID_TO_BIN(#{item.pluginId}),
                UUID_TO_BIN(#{item.versionId}), UUID_TO_BIN(#{item.installationId}),
                UUID_TO_BIN(NULLIF(#{item.userId}, '')),
                UUID_TO_BIN(NULLIF(#{item.spaceId}, '')),
                NULLIF(#{item.clientId}, ''), #{item.triggerEvent},
                #{item.triggerSource}, #{item.status}, #{item.startedAt},
                #{item.endedAt},
                TIMESTAMPDIFF(MICROSECOND, #{item.startedAt}, #{item.endedAt}) DIV 1000,
                #{item.outputSummary}, NULLIF(#{item.errorCode}, ''),
                NULLIF(#{item.correlationId}, ''), NULLIF(#{item.causationId}, ''),
                CONCAT('execution:', #{item.executionId})
            )
            """)
    int insertIgnore(@Param("item") ExecutionRecordRequest request);

    @Select("""
            SELECT BIN_TO_UUID(l.execution_id) executionId,
                   BIN_TO_UUID(l.plugin_id) pluginId,
                   BIN_TO_UUID(l.version_id) versionId,
                   BIN_TO_UUID(l.installation_id) installationId,
                   BIN_TO_UUID(l.user_id) userId, BIN_TO_UUID(l.space_id) spaceId,
                   l.trigger_event triggerEvent, l.trigger_source triggerSource,
                   l.execution_status executionStatus, l.started_at startedAt,
                   l.ended_at endedAt, l.duration_ms durationMs,
                   l.output_summary outputSummary, l.error_code errorCode,
                   l.correlation_id correlationId
            FROM pcd_plugin_execution_log l
            JOIN pcd_plugin p ON p.plugin_id=l.plugin_id
            WHERE l.plugin_id=UUID_TO_BIN(#{pluginId})
              AND p.owner_user_id=UUID_TO_BIN(#{userId})
              AND (#{status}='' OR l.execution_status=#{status})
            ORDER BY l.started_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<PluginExecutionRow> listOwned(@Param("pluginId") String pluginId,
                                       @Param("userId") String userId,
                                       @Param("status") String status,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) totalExecutions,
                   COALESCE(SUM(l.execution_status='SUCCESS'), 0) successfulExecutions,
                   COALESCE(SUM(l.execution_status IN ('FAILED','TIMEOUT')), 0) failedExecutions,
                   COALESCE(
                     ROUND(100.0 * SUM(l.execution_status='SUCCESS') / NULLIF(COUNT(*), 0), 2),
                     0
                   ) successRate,
                   MAX(l.started_at) lastExecutedAt
            FROM pcd_plugin_execution_log l
            JOIN pcd_plugin p ON p.plugin_id=l.plugin_id
            WHERE l.plugin_id=UUID_TO_BIN(#{pluginId})
              AND p.owner_user_id=UUID_TO_BIN(#{userId})
            """)
    PluginExecutionStats statsOwned(@Param("pluginId") String pluginId,
                                    @Param("userId") String userId);

    @Select("""
            SELECT COUNT(*)
              FROM pcd_plugin_version v
              JOIN pcd_plugin p ON p.plugin_id=v.plugin_id
              LEFT JOIN pcd_user_plugin up
                ON up.installation_id=UUID_TO_BIN(#{installationId})
               AND up.plugin_id=p.plugin_id AND up.version_id=v.version_id
               AND up.user_id=UUID_TO_BIN(#{userId})
               AND up.enabled=1 AND up.uninstalled_at IS NULL
              LEFT JOIN pcd_space_plugin sp
                ON sp.installation_id=UUID_TO_BIN(#{installationId})
               AND sp.plugin_id=p.plugin_id AND sp.version_id=v.version_id
               AND sp.space_id=IF(#{spaceId} IS NULL, NULL, UUID_TO_BIN(#{spaceId}))
               AND sp.enabled=1 AND sp.uninstalled_at IS NULL
             WHERE p.plugin_id=UUID_TO_BIN(#{pluginId})
               AND v.version_id=UUID_TO_BIN(#{versionId})
               AND p.plugin_type='LOCAL_PLUGIN' AND p.status='PUBLISHED'
               AND v.immutable=1 AND v.revoked_at IS NULL
               AND (up.installation_id IS NOT NULL OR sp.installation_id IS NOT NULL)
            """)
    int countAccessibleLocalInstallation(
            @Param("installationId") String installationId,
            @Param("pluginId") String pluginId,
            @Param("versionId") String versionId,
            @Param("userId") String userId,
            @Param("spaceId") String spaceId
    );
}
