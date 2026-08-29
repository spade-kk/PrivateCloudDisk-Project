package org.project.workflow.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.workflow.model.WorkflowModels.CapabilityAuditEntry;

/** 能力调用审计台账持久层（需求五 5.16-5.17 / 四 4.20）。 */
@Mapper
public interface CapabilityAuditMapper {
    @Insert("""
            INSERT INTO pcd_capability_audit(
                capability_key, caller_service, execution_id, step_id, user_id, space_id,
                trace_id, param_summary_json, success, result_code, target_service, duration_ms
            ) VALUES (
                #{entry.capabilityKey}, #{entry.callerService},
                IF(#{entry.executionId} IS NULL, NULL, UUID_TO_BIN(#{entry.executionId})),
                #{entry.stepId},
                IF(#{entry.userId} IS NULL, NULL, UUID_TO_BIN(#{entry.userId})),
                IF(#{entry.spaceId} IS NULL, NULL, UUID_TO_BIN(#{entry.spaceId})),
                #{entry.traceId},
                CAST(#{entry.paramSummaryJson} AS JSON),
                #{entry.success}, #{entry.resultCode}, #{entry.targetService}, #{entry.durationMs}
            )
            """)
    int insert(@Param("entry") CapabilityAuditEntry entry);
}
