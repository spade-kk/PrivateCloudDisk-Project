package org.project.service;

import org.project.model.entity.IpBlacklistEntity;
import org.project.model.entity.SecurityEventEntity;
import org.project.model.entity.SystemConfigEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AdminSecurityService {

    List<SecurityEventEntity> getSecurityEvents(String type, String severity, Boolean handled,
                                                 LocalDateTime startDate, LocalDateTime endDate,
                                                 int page, int pageSize);

    long getSecurityEventCount(String type, String severity, Boolean handled,
                               LocalDateTime startDate, LocalDateTime endDate);

    void handleSecurityEvent(Long eventId, UUID handledBy, String resolution);

    void recordSecurityEvent(SecurityEventEntity event);

    List<IpBlacklistEntity> getIpBlacklist();

    void addIpBlacklist(String ip, String reason, UUID addedBy, LocalDateTime expiresAt);

    void removeIpBlacklist(String ip);

    boolean isIpBlocked(String ip);

    List<SystemConfigEntity> getAllConfigs();

    void updateConfig(String configKey, String configValue);
}