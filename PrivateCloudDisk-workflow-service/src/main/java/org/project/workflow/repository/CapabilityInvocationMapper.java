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

    /**
     * 返回幂等键第一次绑定的能力键。
     *
     * <p>幂等键是一次调用的身份，不是可跨能力复用的缓存键。重试时如果调用方
     * 错把同一个 key 配给了另一能力，服务必须拒绝而不能回放旧结果。</p>
     */
    @Select("""
            SELECT capability_key
              FROM pcd_capability_invocation
             WHERE idempotency_key=#{idempotencyKey}
             LIMIT 1
            """)
    String claimedCapabilityKey(@Param("idempotencyKey") String idempotencyKey);

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
