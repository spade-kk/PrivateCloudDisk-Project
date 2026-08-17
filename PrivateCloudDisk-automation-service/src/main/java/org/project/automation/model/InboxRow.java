package org.project.automation.model;

import java.time.LocalDateTime;

/** Automation Inbox 持久化快照。 */
public record InboxRow(
        String eventId,
        String eventType,
        String status,
        LocalDateTime leaseUntil,
        String payloadSha256,
        String payloadJson
) {
}
