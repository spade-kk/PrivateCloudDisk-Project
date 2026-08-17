package org.project.automation.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.project.automation.model.OutboxRow;

import java.util.List;

/** Automation Outbox Mapper。 */
public interface AutomationOutboxMapper {

    @Insert("""
            INSERT IGNORE INTO pcd_automation_outbox (
                outbox_id, aggregate_id, event_type, exchange_name, routing_key, payload_json
            ) VALUES (
                UUID_TO_BIN(#{outboxId}), UUID_TO_BIN(#{aggregateId}), #{eventType},
                #{exchangeName}, #{routingKey}, CAST(#{payloadJson} AS JSON)
            )
            """)
    int insert(@Param("outboxId") String outboxId,
               @Param("aggregateId") String aggregateId,
               @Param("eventType") String eventType,
               @Param("exchangeName") String exchangeName,
               @Param("routingKey") String routingKey,
               @Param("payloadJson") String payloadJson);

    @Select("""
            SELECT BIN_TO_UUID(outbox_id) outboxId, event_type eventType,
                   exchange_name exchangeName, routing_key routingKey,
                   JSON_UNQUOTE(payload_json) payloadJson, retry_count retryCount
            FROM pcd_automation_outbox
            WHERE status IN ('PENDING', 'FAILED')
              AND available_at <= CURRENT_TIMESTAMP(3)
              AND retry_count < 20
            ORDER BY created_at
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<OutboxRow> lockPending(@Param("limit") int limit);

    @Update("""
            UPDATE pcd_automation_outbox
            SET status='PUBLISHING'
            WHERE outbox_id=UUID_TO_BIN(#{outboxId})
              AND status IN ('PENDING', 'FAILED')
            """)
    int markPublishing(@Param("outboxId") String outboxId);

    @Update("""
            UPDATE pcd_automation_outbox
            SET status='SENT', published_at=CURRENT_TIMESTAMP(3), last_error=NULL
            WHERE outbox_id=UUID_TO_BIN(#{outboxId}) AND status='PUBLISHING'
            """)
    int markSent(@Param("outboxId") String outboxId);

    @Update("""
            UPDATE pcd_automation_outbox
            SET status='FAILED', retry_count=retry_count+1, last_error=#{error},
                available_at=DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL #{delaySeconds} SECOND)
            WHERE outbox_id=UUID_TO_BIN(#{outboxId})
            """)
    int markFailed(@Param("outboxId") String outboxId,
                   @Param("error") String error,
                   @Param("delaySeconds") int delaySeconds);

    @Update("""
            UPDATE pcd_automation_outbox
            SET status='FAILED', last_error='PUBLISH_LEASE_EXPIRED',
                available_at=CURRENT_TIMESTAMP(3)
            WHERE status='PUBLISHING'
              AND updated_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 60 SECOND)
            """)
    int recoverPublishing();
}

