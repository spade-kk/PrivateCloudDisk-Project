package org.project.model.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * IP黑名单数据类
 */
@Data
public class IpBlacklistEntity implements Serializable {
    private Long blacklistId;
    private String blacklistIp;
    private String blacklistReason;
    private UUID blacklistAddedBy;
    private UUID blacklistRemovedBy;
    private LocalDateTime blacklistRemovedAt;
    private String blacklistStatus;     // ACTIVE / REMOVED
    private LocalDateTime blacklistExpiresAt;
    private LocalDateTime blacklistCreatedAt;
}