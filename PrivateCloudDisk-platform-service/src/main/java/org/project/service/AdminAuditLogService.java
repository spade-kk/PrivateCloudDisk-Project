package org.project.service;

import org.project.model.entity.AdminAuditLogEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AdminAuditLogService {

    void recordLog(AdminAuditLogEntity log);

    List<AdminAuditLogEntity> getLogs(UUID adminId, String action, String resource,
                                      String status, LocalDateTime startDate,
                                      LocalDateTime endDate, int page, int pageSize);

    long getLogCount(UUID adminId, String action, String resource,
                     String status, LocalDateTime startDate, LocalDateTime endDate);

    long getTodayLogCount();
}