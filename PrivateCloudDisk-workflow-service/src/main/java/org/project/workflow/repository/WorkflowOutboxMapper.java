package org.project.workflow.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.project.workflow.model.WorkflowModels.WorkflowOutboxRow;

import java.util.List;

/** Workflow Service → CloudFlow Runtime 事务 Outbox 持久层。 */
@Mapper
public interface WorkflowOutboxMapper {
    @Insert("""
            INSERT INTO pcd_workflow_outbox(
                event_id, aggregate_id, event_type, routing_key, payload_json
            ) VALUES (
                UUID_TO_BIN(#{eventId}), UUID_TO_BIN(#{aggregateId}),
                #{eventType}, #{routingKey}, CAST(#{payloadJson} AS JSON)
            )
            """)
    int insert(
            @Param("eventId") String eventId,
            @Param("aggregateId") String aggregateId,
            @Param("eventType") String eventType,
            @Param("routingKey") String routingKey,
            @Param("payloadJson") String payloadJson
    );

    @Update("""
            UPDATE pcd_workflow_outbox
               SET status='PENDING', claimed_at=NULL
             WHERE status='PUBLISHING'
               AND claimed_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 60 SECOND)
            """)
    int recoverStale();

    @Select("""
            SELECT BIN_TO_UUID(event_id) event_id,
                   BIN_TO_UUID(aggregate_id) aggregate_id,
                   event_type, routing_key, CAST(payload_json AS CHAR) payload_json, attempt
              FROM pcd_workflow_outbox
             WHERE status='PENDING' AND next_retry_at <= CURRENT_TIMESTAMP(3)
             ORDER BY created_at
             LIMIT #{limit}
             FOR UPDATE SKIP LOCKED
            """)
    List<WorkflowOutboxRow> selectPendingForUpdate(@Param("limit") int limit);

    @Update("""
            UPDATE pcd_workflow_outbox
               SET status='PUBLISHING', claimed_at=CURRENT_TIMESTAMP(3), attempt=attempt+1
             WHERE event_id=UUID_TO_BIN(#{eventId}) AND status='PENDING'
            """)
    int claim(@Param("eventId") String eventId);

    @Update("""
            UPDATE pcd_workflow_outbox
               SET status='PUBLISHED', published_at=CURRENT_TIMESTAMP(3), claimed_at=NULL
             WHERE event_id=UUID_TO_BIN(#{eventId}) AND status='PUBLISHING'
            """)
    int published(@Param("eventId") String eventId);

    @Update("""
            UPDATE pcd_workflow_outbox
               SET status='PENDING', claimed_at=NULL,
                   next_retry_at=DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL #{delaySeconds} SECOND)
             WHERE event_id=UUID_TO_BIN(#{eventId}) AND status='PUBLISHING'
            """)
    int retry(
            @Param("eventId") String eventId,
            @Param("delaySeconds") int delaySeconds
    );
}
