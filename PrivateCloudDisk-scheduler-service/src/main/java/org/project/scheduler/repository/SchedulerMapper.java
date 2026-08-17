package org.project.scheduler.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.project.scheduler.model.SchedulerModels.OutboxRow;
import org.project.scheduler.model.SchedulerModels.ScheduleRow;

import java.time.LocalDateTime;
import java.util.List;

/** 定时计划、分布式租约和 fire Outbox 持久层。 */
@Mapper
public interface SchedulerMapper {
    @Insert("""
            INSERT INTO pcd_workflow_schedule(
                schedule_id, workflow_id, version_id, owner_user_id, space_id,
                cron_expression, timezone, misfire_policy, inputs_json, next_fire_at
            ) VALUES (
                UUID_TO_BIN(#{scheduleId}), UUID_TO_BIN(#{workflowId}),
                UUID_TO_BIN(#{versionId}), UUID_TO_BIN(#{userId}),
                IF(#{spaceId} IS NULL, NULL, UUID_TO_BIN(#{spaceId})),
                #{cron}, #{timezone}, #{misfirePolicy}, CAST(#{inputsJson} AS JSON),
                #{nextFireAt}
            )
            """)
    int insert(
            @Param("scheduleId") String scheduleId,
            @Param("workflowId") String workflowId,
            @Param("versionId") String versionId,
            @Param("userId") String userId,
            @Param("spaceId") String spaceId,
            @Param("cron") String cron,
            @Param("timezone") String timezone,
            @Param("misfirePolicy") String misfirePolicy,
            @Param("inputsJson") String inputsJson,
            @Param("nextFireAt") LocalDateTime nextFireAt
    );

    @Select("""
            SELECT BIN_TO_UUID(schedule_id) schedule_id,
                   BIN_TO_UUID(workflow_id) workflow_id,
                   BIN_TO_UUID(version_id) version_id,
                   BIN_TO_UUID(owner_user_id) owner_user_id,
                   BIN_TO_UUID(space_id) space_id,
                   cron_expression, timezone, misfire_policy,
                   CAST(inputs_json AS CHAR) inputs_json,
                   status, next_fire_at, last_scheduled_at, row_version
              FROM pcd_workflow_schedule
             WHERE schedule_id=UUID_TO_BIN(#{scheduleId})
            """)
    ScheduleRow findById(@Param("scheduleId") String scheduleId);

    @Select("""
            SELECT BIN_TO_UUID(schedule_id) schedule_id,
                   BIN_TO_UUID(workflow_id) workflow_id,
                   BIN_TO_UUID(version_id) version_id,
                   BIN_TO_UUID(owner_user_id) owner_user_id,
                   BIN_TO_UUID(space_id) space_id,
                   cron_expression, timezone, misfire_policy,
                   CAST(inputs_json AS CHAR) inputs_json,
                   status, next_fire_at, last_scheduled_at, row_version
              FROM pcd_workflow_schedule
             WHERE workflow_id=UUID_TO_BIN(#{workflowId}) AND status <> 'DELETED'
             ORDER BY created_at DESC
            """)
    List<ScheduleRow> listByWorkflow(@Param("workflowId") String workflowId);

    @Select("""
            SELECT BIN_TO_UUID(schedule_id)
              FROM pcd_workflow_schedule
             WHERE status='ACTIVE' AND next_fire_at <= CURRENT_TIMESTAMP(3)
               AND (lease_expires_at IS NULL OR lease_expires_at < CURRENT_TIMESTAMP(3))
             ORDER BY next_fire_at, schedule_id
             LIMIT 1
             FOR UPDATE SKIP LOCKED
            """)
    String selectDueForUpdate();

    @Update("""
            UPDATE pcd_workflow_schedule
               SET lease_owner=#{nodeId},
                   lease_expires_at=DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL #{leaseSeconds} SECOND)
             WHERE schedule_id=UUID_TO_BIN(#{scheduleId})
               AND status='ACTIVE'
               AND (lease_expires_at IS NULL OR lease_expires_at < CURRENT_TIMESTAMP(3))
            """)
    int claim(@Param("scheduleId") String scheduleId,
              @Param("nodeId") String nodeId,
              @Param("leaseSeconds") int leaseSeconds);

    @Insert("""
            INSERT IGNORE INTO pcd_schedule_fire_outbox(
                event_id, schedule_id, scheduled_at, payload_json
            ) VALUES (
                UUID_TO_BIN(#{eventId}), UUID_TO_BIN(#{scheduleId}),
                #{scheduledAt}, CAST(#{payloadJson} AS JSON)
            )
            """)
    int insertFire(
            @Param("eventId") String eventId,
            @Param("scheduleId") String scheduleId,
            @Param("scheduledAt") LocalDateTime scheduledAt,
            @Param("payloadJson") String payloadJson
    );

    @Update("""
            UPDATE pcd_workflow_schedule
               SET next_fire_at=#{nextFireAt}, last_scheduled_at=#{lastScheduledAt},
                   lease_owner=NULL, lease_expires_at=NULL, row_version=row_version+1
             WHERE schedule_id=UUID_TO_BIN(#{scheduleId}) AND lease_owner=#{nodeId}
            """)
    int advance(
            @Param("scheduleId") String scheduleId,
            @Param("nodeId") String nodeId,
            @Param("lastScheduledAt") LocalDateTime lastScheduledAt,
            @Param("nextFireAt") LocalDateTime nextFireAt
    );

    @Update("""
            UPDATE pcd_workflow_schedule
               SET status=#{status}, lease_owner=NULL, lease_expires_at=NULL,
                   row_version=row_version+1
             WHERE schedule_id=UUID_TO_BIN(#{scheduleId})
               AND owner_user_id=UUID_TO_BIN(#{userId})
               AND status <> 'DELETED'
            """)
    int setStatus(@Param("scheduleId") String scheduleId,
                  @Param("userId") String userId,
                  @Param("status") String status);

    @Select("""
            SELECT BIN_TO_UUID(event_id)
              FROM pcd_schedule_fire_outbox
             WHERE status IN ('PENDING','FAILED')
               AND next_retry_at <= CURRENT_TIMESTAMP(3)
             ORDER BY created_at, event_id
             LIMIT 1
             FOR UPDATE SKIP LOCKED
            """)
    String selectOutboxForUpdate();

    @Update("""
            UPDATE pcd_schedule_fire_outbox
               SET status='PUBLISHING'
             WHERE event_id=UUID_TO_BIN(#{eventId})
               AND status IN ('PENDING','FAILED')
            """)
    int claimOutbox(@Param("eventId") String eventId);

    @Select("""
            SELECT BIN_TO_UUID(event_id) event_id,
                   CAST(payload_json AS CHAR) payload_json, attempt
              FROM pcd_schedule_fire_outbox
             WHERE event_id=UUID_TO_BIN(#{eventId})
            """)
    OutboxRow findOutbox(@Param("eventId") String eventId);

    @Update("""
            UPDATE pcd_schedule_fire_outbox
               SET status='SENT', published_at=CURRENT_TIMESTAMP(3)
             WHERE event_id=UUID_TO_BIN(#{eventId}) AND status='PUBLISHING'
            """)
    int markSent(@Param("eventId") String eventId);

    @Update("""
            UPDATE pcd_schedule_fire_outbox
               SET status='FAILED', attempt=attempt+1,
                   next_retry_at=DATE_ADD(
                       CURRENT_TIMESTAMP(3),
                       INTERVAL LEAST(300, POW(2, LEAST(attempt+1, 8))) SECOND
                   )
             WHERE event_id=UUID_TO_BIN(#{eventId}) AND status='PUBLISHING'
            """)
    int markFailed(@Param("eventId") String eventId);

    @Update("""
            UPDATE pcd_schedule_fire_outbox
               SET status='FAILED'
             WHERE status='PUBLISHING'
               AND created_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 5 MINUTE)
            """)
    int recoverPublishing();
}
