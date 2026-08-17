package org.project.automation.service;

import lombok.RequiredArgsConstructor;
import org.project.automation.config.AutomationProperties;
import org.project.automation.model.InboxRow;
import org.project.automation.repository.AutomationDispatchMapper;
import org.project.automation.repository.AutomationInboxMapper;
import org.project.automation.repository.AutomationOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** Inbox/Dispatch/Outbox 的本地事务边界。 */
@Service
@RequiredArgsConstructor
public class AutomationPersistenceService {
    private final AutomationInboxMapper inboxMapper;
    private final AutomationDispatchMapper dispatchMapper;
    private final AutomationOutboxMapper outboxMapper;
    private final AutomationProperties properties;

    @Transactional
    public ClaimOutcome claim(String eventId,
                              String eventType,
                              String payloadSha256,
                              String payloadJson) {
        int inserted = inboxMapper.insertIfAbsent(
                eventId,
                eventType,
                payloadSha256,
                payloadJson,
                properties.inboxLeaseSeconds()
        );
        if (inserted == 1) {
            return ClaimOutcome.CLAIMED;
        }
        InboxRow existing = inboxMapper.findById(eventId);
        if (existing == null) {
            throw new IllegalStateException("Inbox 唯一键冲突后无法读取事件");
        }
        if (!payloadSha256.equals(existing.payloadSha256())) {
            throw new SecurityException("相同 event_id 携带了不同消息体");
        }
        if ("COMPLETED".equals(existing.status())) {
            return ClaimOutcome.ALREADY_COMPLETED;
        }
        if (existing.leaseUntil().isAfter(LocalDateTime.now())) {
            return ClaimOutcome.LEASE_BUSY;
        }
        return inboxMapper.reclaimExpired(eventId, properties.inboxLeaseSeconds()) == 1
                ? ClaimOutcome.CLAIMED
                : ClaimOutcome.LEASE_BUSY;
    }

    @Transactional
    public void complete(String sourceEventId,
                         String gateId,
                         String backendTaskId,
                         String userId,
                         String spaceId,
                         int matched,
                         int completed,
                         String resultStatus,
                         String resultSummary,
                         String processedPayload) {
        dispatchMapper.insertSummary(
                UUID.randomUUID().toString(),
                sourceEventId,
                gateId,
                backendTaskId,
                userId,
                spaceId == null ? "" : spaceId,
                "pcd.file.content.ready.v1",
                matched,
                completed,
                resultStatus,
                resultSummary
        );
        outboxMapper.insert(
                UUID.randomUUID().toString(),
                sourceEventId,
                "pcd.file.content.processed.v1",
                "pcd.file.lifecycle.exchange",
                "file.content.processed",
                processedPayload
        );
        if (inboxMapper.markCompleted(sourceEventId) != 1) {
            throw new IllegalStateException("Inbox 完成状态更新失败");
        }
    }

    @Transactional
    public void completeWithoutOutbox(String sourceEventId,
                                      String userId,
                                      String spaceId,
                                      String triggerType,
                                      int matched,
                                      int completed,
                                      String resultStatus,
                                      String resultSummary) {
        dispatchMapper.insertSummary(
                UUID.randomUUID().toString(),
                sourceEventId,
                "",
                "",
                userId,
                spaceId == null ? "" : spaceId,
                triggerType,
                matched,
                completed,
                resultStatus,
                resultSummary
        );
        if (inboxMapper.markCompleted(sourceEventId) != 1) {
            throw new IllegalStateException("Inbox 完成状态更新失败");
        }
    }
}
