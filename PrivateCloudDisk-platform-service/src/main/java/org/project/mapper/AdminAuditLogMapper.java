package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.AdminAuditLogEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface AdminAuditLogMapper {
    int insertAuditLog(AdminAuditLogEntity entity);

    List<AdminAuditLogEntity> findByAdminId(@Param("admin_id") UUID adminId);

    List<AdminAuditLogEntity> findByFilters(
            @Param("adminId") UUID adminId,
            @Param("action") String action,
            @Param("resource") String resource,
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countByFilters(
            @Param("adminId") UUID adminId,
            @Param("action") String action,
            @Param("resource") String resource,
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    long countSince(@Param("since") LocalDateTime since);
}