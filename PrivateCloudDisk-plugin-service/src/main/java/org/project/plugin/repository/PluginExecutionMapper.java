package org.project.plugin.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.project.plugin.model.ExecutionRecordRequest;
import org.project.plugin.model.PluginExecutionAccessScope;
import org.project.plugin.model.PluginExecutionAuditTrailRow;
import org.project.plugin.model.PluginExecutionDetailRow;
import org.project.plugin.model.PluginExecutionLogLine;
import org.project.plugin.model.PluginExecutionRow;
import org.project.plugin.model.PluginExecutionStats;

import java.time.Instant;
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

    /** [PLUGIN-EXEC-OBS-001] 摘要落库后创建可观测性游标；历史记录按需懒创建。 */
    @Insert("""
            INSERT IGNORE INTO pcd_plugin_execution_observability_cursor(execution_id)
            VALUES (UUID_TO_BIN(#{executionId}))
            """)
    int ensureObservabilityCursor(@Param("executionId") String executionId);

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

    /**
     * [PLUGIN-EXEC-OBS-001] 空间管理员只读取当前空间的执行，不因插件为他人创建就看到
     * 其他空间或个人安装的历史。授权判定仍在 Service 层实时委托 Platform Service。
     */
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
             WHERE l.plugin_id=UUID_TO_BIN(#{pluginId})
               AND l.space_id=UUID_TO_BIN(#{spaceId})
               AND (#{status}='' OR l.execution_status=#{status})
             ORDER BY l.started_at DESC
             LIMIT #{limit} OFFSET #{offset}
            """)
    List<PluginExecutionRow> listManagedSpace(@Param("pluginId") String pluginId,
                                              @Param("spaceId") String spaceId,
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
            SELECT COUNT(*) totalExecutions,
                   COALESCE(SUM(l.execution_status='SUCCESS'), 0) successfulExecutions,
                   COALESCE(SUM(l.execution_status IN ('FAILED','TIMEOUT')), 0) failedExecutions,
                   COALESCE(
                     ROUND(100.0 * SUM(l.execution_status='SUCCESS') / NULLIF(COUNT(*), 0), 2),
                     0
                   ) successRate,
                   MAX(l.started_at) lastExecutedAt
              FROM pcd_plugin_execution_log l
             WHERE l.plugin_id=UUID_TO_BIN(#{pluginId})
               AND l.space_id=UUID_TO_BIN(#{spaceId})
            """)
    PluginExecutionStats statsManagedSpace(@Param("pluginId") String pluginId,
                                           @Param("spaceId") String spaceId);

    @Select("""
            SELECT BIN_TO_UUID(owner_user_id)
              FROM pcd_plugin
             WHERE plugin_id=UUID_TO_BIN(#{pluginId})
            """)
    String findPluginOwner(@Param("pluginId") String pluginId);

    @Select("""
            SELECT BIN_TO_UUID(l.execution_id) executionId,
                   BIN_TO_UUID(p.owner_user_id) ownerUserId,
                   BIN_TO_UUID(l.space_id) spaceId
              FROM pcd_plugin_execution_log l
              JOIN pcd_plugin p ON p.plugin_id=l.plugin_id
             WHERE l.execution_id=UUID_TO_BIN(#{executionId})
            """)
    PluginExecutionAccessScope findAccessScope(@Param("executionId") String executionId);

    @Select("""
            SELECT BIN_TO_UUID(l.execution_id) executionId,
                   BIN_TO_UUID(p.owner_user_id) ownerUserId,
                   BIN_TO_UUID(l.space_id) spaceId
              FROM pcd_plugin_execution_audit_trail a
              JOIN pcd_plugin_execution_log l ON l.execution_id=a.execution_id
              JOIN pcd_plugin p ON p.plugin_id=l.plugin_id
             WHERE a.audit_id=UUID_TO_BIN(#{auditId})
            """)
    PluginExecutionAccessScope findAccessScopeForAudit(@Param("auditId") String auditId);

    @Select("""
            SELECT BIN_TO_UUID(l.execution_id) executionId,
                   BIN_TO_UUID(l.plugin_id) pluginId, p.name pluginName,
                   BIN_TO_UUID(l.version_id) versionId, v.version version,
                   v.runtime runtime, v.entrypoint entrypoint,
                   BIN_TO_UUID(l.installation_id) installationId,
                   BIN_TO_UUID(l.user_id) userId, BIN_TO_UUID(l.space_id) spaceId,
                   l.trigger_event triggerEvent, l.trigger_source triggerSource,
                   l.execution_status executionStatus, l.started_at startedAt,
                   l.ended_at endedAt, l.duration_ms durationMs,
                   l.output_summary outputSummary, l.error_code errorCode,
                   l.correlation_id correlationId,
                   (SELECT COUNT(*) FROM pcd_plugin_execution_log_line ll
                     WHERE ll.execution_id=l.execution_id) logLineCount,
                   (SELECT COUNT(*) FROM pcd_plugin_execution_audit_trail audit_count
                     WHERE audit_count.execution_id=l.execution_id) auditCallCount,
                   CAST(v.manifest_json AS CHAR) manifestJson
              FROM pcd_plugin_execution_log l
              JOIN pcd_plugin p ON p.plugin_id=l.plugin_id
              JOIN pcd_plugin_version v ON v.version_id=l.version_id
             WHERE l.execution_id=UUID_TO_BIN(#{executionId})
            """)
    PluginExecutionDetailRow findDetail(@Param("executionId") String executionId);

    @Select("""
            SELECT sequence_no sequenceNo, occurred_at timestamp, log_level level,
                   log_source source, message_text content, byte_offset byteOffset
              FROM pcd_plugin_execution_log_line
             WHERE execution_id=UUID_TO_BIN(#{executionId})
               AND (#{cursor} IS NULL OR sequence_no > #{cursor})
               AND (#{startAt} IS NULL OR occurred_at >= #{startAt})
               AND (#{endAt} IS NULL OR occurred_at <= #{endAt})
               AND (#{level}='' OR log_level=#{level})
               AND (#{source}='' OR log_source=#{source})
             ORDER BY sequence_no ASC
             LIMIT #{limit}
            """)
    List<PluginExecutionLogLine> listLogsAsc(
            @Param("executionId") String executionId, @Param("cursor") Long cursor,
            @Param("limit") int limit, @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt, @Param("level") String level,
            @Param("source") String source
    );

    @Select("""
            SELECT sequence_no sequenceNo, occurred_at timestamp, log_level level,
                   log_source source, message_text content, byte_offset byteOffset
              FROM pcd_plugin_execution_log_line
             WHERE execution_id=UUID_TO_BIN(#{executionId})
               AND (#{cursor} IS NULL OR sequence_no < #{cursor})
               AND (#{startAt} IS NULL OR occurred_at >= #{startAt})
               AND (#{endAt} IS NULL OR occurred_at <= #{endAt})
               AND (#{level}='' OR log_level=#{level})
               AND (#{source}='' OR log_source=#{source})
             ORDER BY sequence_no DESC
             LIMIT #{limit}
            """)
    List<PluginExecutionLogLine> listLogsDesc(
            @Param("executionId") String executionId, @Param("cursor") Long cursor,
            @Param("limit") int limit, @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt, @Param("level") String level,
            @Param("source") String source
    );

    @Select("""
            SELECT BIN_TO_UUID(a.audit_id) auditId, BIN_TO_UUID(a.parent_audit_id) parentAuditId,
                   a.sequence_no sequenceNo, a.capability_key capabilityKey,
                   a.capability_type capabilityType, a.summary_template summaryTemplate,
                   a.summary_text summary, CAST(a.target_context AS CHAR) targetContextJson,
                   CAST(a.input_params AS CHAR) inputParamsJson, a.input_summary inputSummary,
                   CAST(a.output_result AS CHAR) outputResultJson, a.output_summary outputSummary,
                   a.audit_status status, a.duration_ms durationMs, a.retry_count retryCount,
                   a.error_code errorCode, a.error_summary errorSummary, a.occurred_at timestamp
              FROM pcd_plugin_execution_audit_trail a
             WHERE a.execution_id=UUID_TO_BIN(#{executionId})
               AND (#{cursor} IS NULL OR a.sequence_no < #{cursor})
               AND (#{capabilityType}='' OR a.capability_type=#{capabilityType})
               AND (#{status}='' OR a.audit_status=#{status})
             ORDER BY a.sequence_no DESC
             LIMIT #{limit}
            """)
    List<PluginExecutionAuditTrailRow> listAudits(
            @Param("executionId") String executionId, @Param("cursor") Long cursor,
            @Param("limit") int limit, @Param("capabilityType") String capabilityType,
            @Param("status") String status
    );

    @Select("""
            SELECT BIN_TO_UUID(a.audit_id) auditId, BIN_TO_UUID(a.parent_audit_id) parentAuditId,
                   a.sequence_no sequenceNo, a.capability_key capabilityKey,
                   a.capability_type capabilityType, a.summary_template summaryTemplate,
                   a.summary_text summary, CAST(a.target_context AS CHAR) targetContextJson,
                   CAST(a.input_params AS CHAR) inputParamsJson, a.input_summary inputSummary,
                   CAST(a.output_result AS CHAR) outputResultJson, a.output_summary outputSummary,
                   a.audit_status status, a.duration_ms durationMs, a.retry_count retryCount,
                   a.error_code errorCode, a.error_summary errorSummary, a.occurred_at timestamp
              FROM pcd_plugin_execution_audit_trail a
             WHERE a.audit_id=UUID_TO_BIN(#{auditId})
            """)
    PluginExecutionAuditTrailRow findAudit(@Param("auditId") String auditId);

    @Insert("""
            INSERT IGNORE INTO pcd_plugin_execution_observation_ingest(execution_id, observation_id)
            VALUES (UUID_TO_BIN(#{executionId}), #{observationId})
            """)
    int registerObservation(@Param("executionId") String executionId,
                            @Param("observationId") String observationId);

    @Select("""
            SELECT next_log_sequence
              FROM pcd_plugin_execution_observability_cursor
             WHERE execution_id=UUID_TO_BIN(#{executionId})
             FOR UPDATE
            """)
    Long lockNextLogSequence(@Param("executionId") String executionId);

    @Select("""
            SELECT next_audit_sequence
              FROM pcd_plugin_execution_observability_cursor
             WHERE execution_id=UUID_TO_BIN(#{executionId})
             FOR UPDATE
            """)
    Long lockNextAuditSequence(@Param("executionId") String executionId);

    @Update("""
            UPDATE pcd_plugin_execution_observability_cursor
               SET next_log_sequence=#{next}
             WHERE execution_id=UUID_TO_BIN(#{executionId})
            """)
    int updateNextLogSequence(@Param("executionId") String executionId, @Param("next") long next);

    @Update("""
            UPDATE pcd_plugin_execution_observability_cursor
               SET next_audit_sequence=#{next}
             WHERE execution_id=UUID_TO_BIN(#{executionId})
            """)
    int updateNextAuditSequence(@Param("executionId") String executionId, @Param("next") long next);

    @Insert("""
            INSERT INTO pcd_plugin_execution_log_line(
                execution_id, sequence_no, occurred_at, log_level, log_source, message_text, byte_offset
            ) VALUES (
                UUID_TO_BIN(#{executionId}), #{sequenceNo}, #{occurredAt}, #{level}, #{source},
                #{message}, #{byteOffset}
            )
            """)
    int insertLogLine(
            @Param("executionId") String executionId, @Param("sequenceNo") long sequenceNo,
            @Param("occurredAt") Instant occurredAt, @Param("level") String level,
            @Param("source") String source, @Param("message") String message,
            @Param("byteOffset") long byteOffset
    );

    @Insert("""
            INSERT INTO pcd_plugin_execution_audit_trail(
                audit_id, execution_id, parent_audit_id, sequence_no, capability_key, capability_type,
                summary_template, summary_text, target_context, input_params, input_summary,
                output_result, output_summary, audit_status, duration_ms, retry_count,
                error_code, error_summary, occurred_at
            ) VALUES (
                UUID_TO_BIN(#{auditId}), UUID_TO_BIN(#{executionId}), UUID_TO_BIN(NULLIF(#{parentAuditId}, '')),
                #{sequenceNo}, #{capabilityKey}, #{capabilityType}, NULLIF(#{summaryTemplate}, ''),
                #{summary}, CAST(#{targetContextJson} AS JSON), CAST(#{inputParamsJson} AS JSON),
                #{inputSummary}, CAST(#{outputResultJson} AS JSON), #{outputSummary}, #{status},
                #{durationMs}, #{retryCount}, NULLIF(#{errorCode}, ''), NULLIF(#{errorSummary}, ''), #{occurredAt}
            )
            """)
    int insertAuditTrail(
            @Param("auditId") String auditId, @Param("executionId") String executionId,
            @Param("parentAuditId") String parentAuditId, @Param("sequenceNo") long sequenceNo,
            @Param("capabilityKey") String capabilityKey, @Param("capabilityType") String capabilityType,
            @Param("summaryTemplate") String summaryTemplate, @Param("summary") String summary,
            @Param("targetContextJson") String targetContextJson, @Param("inputParamsJson") String inputParamsJson,
            @Param("inputSummary") String inputSummary, @Param("outputResultJson") String outputResultJson,
            @Param("outputSummary") String outputSummary, @Param("status") String status,
            @Param("durationMs") Long durationMs, @Param("retryCount") int retryCount,
            @Param("errorCode") String errorCode, @Param("errorSummary") String errorSummary,
            @Param("occurredAt") Instant occurredAt
    );

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
