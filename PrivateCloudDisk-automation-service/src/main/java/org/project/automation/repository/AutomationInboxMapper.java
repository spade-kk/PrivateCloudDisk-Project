package org.project.automation.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.project.automation.model.InboxRow;

import java.util.List;

/** Automation Inbox Mapper；Redis 不参与幂等判定。 */
public interface AutomationInboxMapper {

    @Insert("""
            INSERT IGNORE INTO pcd_automation_inbox (
                event_id, event_type, payload_sha256, payload_json, status, lease_until
            ) VALUES (
                UUID_TO_BIN(#{eventId}), #{eventType}, #{payloadSha256},
                CAST(#{payloadJson} AS JSON), 'PROCESSING',
                DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL #{leaseSeconds} SECOND)
            )
            """)
    int insertIfAbsent(@Param("eventId") String eventId,
                       @Param("eventType") String eventType,
                       @Param("payloadSha256") String payloadSha256,
                       @Param("payloadJson") String payloadJson,
                       @Param("leaseSeconds") int leaseSeconds);

    @Select("""
            SELECT BIN_TO_UUID(event_id) eventId, event_type eventType, status, lease_until leaseUntil,
                   payload_sha256 payloadSha256,
                   JSON_UNQUOTE(payload_json) payloadJson
            FROM pcd_automation_inbox
            WHERE event_id=UUID_TO_BIN(#{eventId})
            """)
    InboxRow findById(@Param("eventId") String eventId);

    @Update("""
            UPDATE pcd_automation_inbox
            SET status='PROCESSING',
                lease_until=DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL #{leaseSeconds} SECOND),
                attempt=attempt+1, failure_code=NULL
            WHERE event_id=UUID_TO_BIN(#{eventId})
              AND status <> 'COMPLETED'
              AND lease_until <= CURRENT_TIMESTAMP(3)
            """)
    int reclaimExpired(@Param("eventId") String eventId,
                       @Param("leaseSeconds") int leaseSeconds);

    @Select("""
            SELECT BIN_TO_UUID(event_id) eventId, event_type eventType, status, lease_until leaseUntil,
                   payload_sha256 payloadSha256,
                   JSON_UNQUOTE(payload_json) payloadJson
            FROM pcd_automation_inbox
            WHERE status='PROCESSING' AND lease_until <= CURRENT_TIMESTAMP(3)
            ORDER BY lease_until
            LIMIT #{limit}
            """)
    List<InboxRow> findExpired(@Param("limit") int limit);

    @Update("""
            UPDATE pcd_automation_inbox
            SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP(3), failure_code=NULL
            WHERE event_id=UUID_TO_BIN(#{eventId}) AND status='PROCESSING'
            """)
    int markCompleted(@Param("eventId") String eventId);
}
