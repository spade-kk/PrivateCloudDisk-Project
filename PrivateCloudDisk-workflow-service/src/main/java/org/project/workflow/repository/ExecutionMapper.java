package org.project.workflow.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.project.workflow.model.WorkflowModels.ExecutionRow;

import java.util.List;

/** 工作流执行和逐步骤检查点持久层。 */
@Mapper
public interface ExecutionMapper {
    @Insert("""
            INSERT INTO pcd_workflow_execution(
                execution_id, workflow_id, version_id, user_id, space_id,
                trigger_type, trigger_ref, status, input_summary_json,
                trace_id, correlation_id, causation_id, idempotency_key,
                retry_of_execution_id
            ) VALUES (
                UUID_TO_BIN(#{executionId}), UUID_TO_BIN(#{workflowId}),
                UUID_TO_BIN(#{versionId}), UUID_TO_BIN(#{userId}),
                IF(#{spaceId} IS NULL, NULL, UUID_TO_BIN(#{spaceId})),
                #{triggerType}, #{triggerRef}, 'QUEUED', CAST(#{inputJson} AS JSON),
                #{traceId}, #{correlationId}, #{causationId}, #{idempotencyKey},
                IF(#{retryOf} IS NULL, NULL, UUID_TO_BIN(#{retryOf}))
            )
            """)
    int insertExecution(
            @Param("executionId") String executionId,
            @Param("workflowId") String workflowId,
            @Param("versionId") String versionId,
            @Param("userId") String userId,
            @Param("spaceId") String spaceId,
            @Param("triggerType") String triggerType,
            @Param("triggerRef") String triggerRef,
            @Param("inputJson") String inputJson,
            @Param("traceId") String traceId,
            @Param("correlationId") String correlationId,
            @Param("causationId") String causationId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("retryOf") String retryOf
    );

    @Select("""
            SELECT BIN_TO_UUID(execution_id) execution_id,
                   BIN_TO_UUID(workflow_id) workflow_id,
                   BIN_TO_UUID(version_id) version_id,
                   BIN_TO_UUID(user_id) user_id,
                   BIN_TO_UUID(space_id) space_id,
                   trigger_type, status, current_step,
                   CAST(input_summary_json AS CHAR) input_summary_json,
                   CAST(output_summary_json AS CHAR) output_summary_json,
                   error_code, error_summary, trace_id,
                   BIN_TO_UUID(retry_of_execution_id) retry_of_execution_id,
                   cancel_requested, started_at, ended_at, created_at
              FROM pcd_workflow_execution
             WHERE execution_id=UUID_TO_BIN(#{executionId})
            """)
    ExecutionRow findById(@Param("executionId") String executionId);

    @Select("""
            SELECT BIN_TO_UUID(execution_id) execution_id,
                   BIN_TO_UUID(workflow_id) workflow_id,
                   BIN_TO_UUID(version_id) version_id,
                   BIN_TO_UUID(user_id) user_id,
                   BIN_TO_UUID(space_id) space_id,
                   trigger_type, status, current_step,
                   CAST(input_summary_json AS CHAR) input_summary_json,
                   CAST(output_summary_json AS CHAR) output_summary_json,
                   error_code, error_summary, trace_id,
                   BIN_TO_UUID(retry_of_execution_id) retry_of_execution_id,
                   cancel_requested, started_at, ended_at, created_at
              FROM pcd_workflow_execution
             WHERE workflow_id=UUID_TO_BIN(#{workflowId})
             ORDER BY created_at DESC
             LIMIT #{limit} OFFSET #{offset}
            """)
    List<ExecutionRow> list(
            @Param("workflowId") String workflowId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            SELECT BIN_TO_UUID(execution_id)
              FROM pcd_workflow_execution
             WHERE status='QUEUED'
             ORDER BY created_at, execution_id
             LIMIT 1
             FOR UPDATE SKIP LOCKED
            """)
    String selectNextQueuedForUpdate();

    @Update("""
            UPDATE pcd_workflow_execution
               SET status='RUNNING',
                   started_at=COALESCE(started_at, CURRENT_TIMESTAMP(3)),
                   heartbeat_at=CURRENT_TIMESTAMP(3)
             WHERE execution_id=UUID_TO_BIN(#{executionId}) AND status='QUEUED'
            """)
    int claim(@Param("executionId") String executionId);

    @Update("""
            UPDATE pcd_workflow_execution
               SET heartbeat_at=CURRENT_TIMESTAMP(3), current_step=#{stepId}
             WHERE execution_id=UUID_TO_BIN(#{executionId}) AND status='RUNNING'
            """)
    int heartbeat(@Param("executionId") String executionId, @Param("stepId") String stepId);

    @Update("""
            UPDATE pcd_workflow_execution
               SET status='QUEUED', heartbeat_at=NULL
             WHERE status='RUNNING'
               AND heartbeat_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL #{staleSeconds} SECOND)
               AND cancel_requested=0
            """)
    int recoverStale(@Param("staleSeconds") int staleSeconds);

    @Update("""
            UPDATE pcd_workflow_execution
               SET cancel_requested=1
             WHERE execution_id=UUID_TO_BIN(#{executionId})
               AND user_id=UUID_TO_BIN(#{userId})
               AND status IN ('QUEUED','RUNNING')
            """)
    int requestCancel(@Param("executionId") String executionId, @Param("userId") String userId);

    @Update("""
            UPDATE pcd_workflow_execution
               SET status=#{status}, ended_at=CURRENT_TIMESTAMP(3),
                   output_summary_json=CAST(#{outputJson} AS JSON),
                   error_code=#{errorCode}, error_summary=#{errorSummary},
                   heartbeat_at=CURRENT_TIMESTAMP(3)
             WHERE execution_id=UUID_TO_BIN(#{executionId}) AND status='RUNNING'
            """)
    int finish(
            @Param("executionId") String executionId,
            @Param("status") String status,
            @Param("outputJson") String outputJson,
            @Param("errorCode") String errorCode,
            @Param("errorSummary") String errorSummary
    );

    @Select("""
            SELECT COUNT(*)
              FROM pcd_workflow_step_execution
             WHERE workflow_execution_id=UUID_TO_BIN(#{executionId})
               AND step_id=#{stepId} AND status='SUCCEEDED'
            """)
    int completedStep(@Param("executionId") String executionId, @Param("stepId") String stepId);

    @Select("""
            SELECT CAST(output_summary_json AS CHAR)
              FROM pcd_workflow_step_execution
             WHERE workflow_execution_id=UUID_TO_BIN(#{executionId})
               AND step_id=#{stepId} AND status='SUCCEEDED'
             ORDER BY attempt DESC
             LIMIT 1
            """)
    String completedStepOutput(@Param("executionId") String executionId,
                               @Param("stepId") String stepId);

    @Select("""
            SELECT COALESCE(MAX(attempt), 0)
              FROM pcd_workflow_step_execution
             WHERE workflow_execution_id=UUID_TO_BIN(#{executionId}) AND step_id=#{stepId}
            """)
    int maxStepAttempt(@Param("executionId") String executionId,
                       @Param("stepId") String stepId);

    @Insert("""
            INSERT INTO pcd_workflow_step_execution(
                step_execution_id, workflow_execution_id, step_id, step_name,
                capability_key, attempt, status, input_summary_json, started_at
            ) VALUES (
                UUID_TO_BIN(#{stepExecutionId}), UUID_TO_BIN(#{executionId}),
                #{stepId}, #{stepName}, #{capabilityKey}, #{attempt}, 'RUNNING',
                CAST(#{inputJson} AS JSON), CURRENT_TIMESTAMP(3)
            )
            """)
    int insertStep(
            @Param("stepExecutionId") String stepExecutionId,
            @Param("executionId") String executionId,
            @Param("stepId") String stepId,
            @Param("stepName") String stepName,
            @Param("capabilityKey") String capabilityKey,
            @Param("attempt") int attempt,
            @Param("inputJson") String inputJson
    );

    @Update("""
            UPDATE pcd_workflow_step_execution
               SET status=#{status}, output_summary_json=CAST(#{outputJson} AS JSON),
                   error_code=#{errorCode}, error_summary=#{errorSummary},
                   ended_at=CURRENT_TIMESTAMP(3),
                   duration_ms=TIMESTAMPDIFF(MICROSECOND, started_at, CURRENT_TIMESTAMP(3)) DIV 1000
             WHERE step_execution_id=UUID_TO_BIN(#{stepExecutionId})
            """)
    int finishStep(
            @Param("stepExecutionId") String stepExecutionId,
            @Param("status") String status,
            @Param("outputJson") String outputJson,
            @Param("errorCode") String errorCode,
            @Param("errorSummary") String errorSummary
    );
}
