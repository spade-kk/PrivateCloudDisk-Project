package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.AdminAuditLogMapper;
import org.project.model.entity.AdminAuditLogEntity;
import org.project.service.AdminAuditLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private final AdminAuditLogMapper adminAuditLogMapper;

    @Override
    public void recordLog(AdminAuditLogEntity log) {
        log.setAuditCreatedAt(LocalDateTime.now());
        adminAuditLogMapper.insertAuditLog(log);
    }

    @Override
    public List<AdminAuditLogEntity> getLogs(UUID adminId, String action, String resource,
                                             String status, LocalDateTime startDate,
                                             LocalDateTime endDate, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return adminAuditLogMapper.findByFilters(adminId, action, resource, status, startDate, endDate, offset, pageSize);
    }

    @Override
    public long getLogCount(UUID adminId, String action, String resource,
                            String status, LocalDateTime startDate, LocalDateTime endDate) {
        return adminAuditLogMapper.countByFilters(adminId, action, resource, status, startDate, endDate);
    }

    @Override
    public long getTodayLogCount() {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        return adminAuditLogMapper.countSince(today);
    }
}