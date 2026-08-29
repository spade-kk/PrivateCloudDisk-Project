package org.project.workflow.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** CloudFlow Agent 能力调用幂等台账持久层。 */
@Mapper
public interface CapabilityInvocationMapper {
    @Insert("""
            INSERT IGNORE INTO pcd_capability_invocation(
                idempotency_key, execution_id, step_id, attempt, capability_key,
                user_id, space_id, trace_id
            ) VALUES (
                #{idempotencyKey}, UUID_TO_BIN(#{executionId}), #{stepId}, #{attempt},
                #{capabilityKey}, UUID_TO_BIN(#{userId}),
                IF(#{spaceId} IS NULL, NULL, UUID_TO_BIN(#{spaceId})), #{traceId}
            )
            """)
    int claim(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("executionId") String executionId,
            @Param("stepId") String stepId,
            @Param("attempt") int attempt,
            @Param("capabilityKey") String capabilityKey,
            @Param("userId") String userId,
            @Param("spaceId") String spaceId,
            @Param("traceId") String traceId
    );

    @Select("""
            SELECT CAST(result_json AS CHAR)
              FROM pcd_capability_invocation
             WHERE idempotency_key=#{idempotencyKey} AND status='COMPLETED'
            """)
    String completedResult(@Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE pcd_capability_invocation
               SET status='COMPLETED', result_json=CAST(#{resultJson} AS JSON),
                   completed_at=CURRENT_TIMESTAMP(3)
             WHERE idempotency_key=#{idempotencyKey} AND status='RUNNING'
            """)
    int complete(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("resultJson") String resultJson
    );
}
