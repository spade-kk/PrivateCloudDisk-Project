package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.SecurityEventEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface SecurityEventMapper {
    int insertSecurityEvent(SecurityEventEntity entity);

    SecurityEventEntity findByEventId(@Param("event_id") Long eventId);

    List<SecurityEventEntity> findByFilters(
            @Param("type") String type,
            @Param("severity") String severity,
            @Param("handled") Boolean handled,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countByFilters(
            @Param("type") String type,
            @Param("severity") String severity,
            @Param("handled") Boolean handled,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    long countByHandled(@Param("handled") boolean handled);

    int updateHandled(@Param("event_id") Long eventId,
                      @Param("handled_by") UUID handledBy,
                      @Param("handled_at") LocalDateTime handledAt,
                      @Param("resolution") String resolution);
}