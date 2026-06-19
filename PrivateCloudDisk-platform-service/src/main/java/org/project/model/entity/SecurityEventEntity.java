package org.project.model.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 安全事件数据类
 */
@Data
public class SecurityEventEntity implements Serializable {
    private Long eventId;
    private String eventType;          // LOGIN_FAILURE / BRUTE_FORCE / IP_BLOCKED / CONFIG_CHANGE / ADMIN_ACTION
    private String eventSeverity;      // LOW / MEDIUM / HIGH / CRITICAL
    private UUID eventUserId;
    private UUID eventAdminId;
    private String eventIp;
    private String eventDescription;
    private boolean eventHandled;
    private UUID eventHandledBy;
    private LocalDateTime eventHandledAt;
    private String eventResolution;
    private LocalDateTime eventCreatedAt;
}