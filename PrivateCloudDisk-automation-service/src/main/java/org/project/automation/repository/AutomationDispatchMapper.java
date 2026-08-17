package org.project.automation.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** 每个 ready 事件对应一条可审计调度摘要。 */
public interface AutomationDispatchMapper {

    @Insert("""
            INSERT IGNORE INTO pcd_automation_dispatch (
                dispatch_id, source_event_id, gate_id, backend_task_id,
                user_id, space_id, trigger_type, matched_count, completed_count,
                dispatch_status, result_summary, completed_at
            ) VALUES (
                UUID_TO_BIN(#{dispatchId}), UUID_TO_BIN(#{sourceEventId}),
                UUID_TO_BIN(NULLIF(#{gateId}, '')), #{backendTaskId},
                UUID_TO_BIN(NULLIF(#{userId}, '')), UUID_TO_BIN(NULLIF(#{spaceId}, '')),
                #{triggerType}, #{matchedCount}, #{completedCount},
                #{dispatchStatus}, #{resultSummary}, CURRENT_TIMESTAMP(3)
            )
            """)
    int insertSummary(@Param("dispatchId") String dispatchId,
                      @Param("sourceEventId") String sourceEventId,
                      @Param("gateId") String gateId,
                      @Param("backendTaskId") String backendTaskId,
                      @Param("userId") String userId,
                      @Param("spaceId") String spaceId,
                      @Param("triggerType") String triggerType,
                      @Param("matchedCount") int matchedCount,
                      @Param("completedCount") int completedCount,
                      @Param("dispatchStatus") String dispatchStatus,
                      @Param("resultSummary") String resultSummary);
}

