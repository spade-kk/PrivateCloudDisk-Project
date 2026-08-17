package org.project.scheduler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.project.scheduler.config.SchedulerProperties;
import org.project.scheduler.model.SchedulerModels.CreateScheduleRequest;
import org.project.scheduler.model.SchedulerModels.ScheduleRow;
import org.project.scheduler.repository.SchedulerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 定时计划应用服务；所有 fire 与计划推进在同一数据库事务完成。 */
@Service
public class SchedulerService {
    private final SchedulerMapper mapper;
    private final CronScheduleService cronService;
    private final SchedulerProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public SchedulerService(
            SchedulerMapper mapper,
            CronScheduleService cronService,
            SchedulerProperties properties,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.cronService = cronService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ScheduleRow create(CreateScheduleRequest request) {
        requireUuid(request.workflowId(), "workflow_id");
        requireUuid(request.versionId(), "version_id");
        requireUuid(request.userId(), "user_id");
        String spaceId = blank(request.spaceId());
        if (spaceId != null) {
            requireUuid(spaceId, "space_id");
        }
        cronService.validate(request.cron(), request.timezone());
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime next = cronService.next(request.cron(), request.timezone(), now);
        String id = UUID.randomUUID().toString();
        mapper.insert(
                id, request.workflowId(), request.versionId(), request.userId(), spaceId,
                request.cron().trim(), request.timezone(), request.misfirePolicy(),
                json(request.inputs() == null ? Map.of() : request.inputs()), next
        );
        return mapper.findById(id);
    }

    public List<ScheduleRow> list(String workflowId) {
        requireUuid(workflowId, "workflow_id");
        return mapper.listByWorkflow(workflowId);
    }

    public void setEnabled(String scheduleId, String userId, boolean enabled) {
        requireUuid(scheduleId, "schedule_id");
        requireUuid(userId, "user_id");
        if (mapper.setStatus(scheduleId, userId, enabled ? "ACTIVE" : "PAUSED") != 1) {
            throw new IllegalArgumentException("定时计划不存在或无权修改");
        }
    }

    @Transactional
    public boolean processOneDue() {
        String scheduleId = mapper.selectDueForUpdate();
        if (scheduleId == null || mapper.claim(
                scheduleId, properties.scheduler().nodeId(), properties.scheduler().leaseSeconds()
        ) != 1) {
            return false;
        }
        ScheduleRow row = mapper.findById(scheduleId);
        LocalDateTime now = LocalDateTime.now(clock);
        List<LocalDateTime> fireTimes = calculateFireTimes(row, now);
        for (LocalDateTime scheduledAt : fireTimes) {
            mapper.insertFire(
                    UUID.randomUUID().toString(),
                    row.scheduleId(),
                    scheduledAt,
                    eventJson(row, scheduledAt)
            );
        }
        LocalDateTime base = fireTimes.isEmpty() ? now : fireTimes.get(fireTimes.size() - 1);
        LocalDateTime next = cronService.next(row.cronExpression(), row.timezone(), base);
        if (!next.isAfter(now)) {
            // CATCH_UP_LIMITED 达到上限后丢弃更早的积压，避免一次故障触发无界洪峰。
            next = cronService.next(row.cronExpression(), row.timezone(), now);
        }
        mapper.advance(
                row.scheduleId(), properties.scheduler().nodeId(),
                fireTimes.isEmpty() ? row.lastScheduledAt() : fireTimes.get(fireTimes.size() - 1),
                next
        );
        return true;
    }

    private List<LocalDateTime> calculateFireTimes(ScheduleRow row, LocalDateTime now) {
        if (row.nextFireAt().isAfter(now)) {
            return List.of();
        }
        if ("SKIP".equals(row.misfirePolicy()) && row.nextFireAt().isBefore(now.minusMinutes(1))) {
            return List.of();
        }
        if (!"CATCH_UP_LIMITED".equals(row.misfirePolicy())) {
            return List.of(row.nextFireAt());
        }
        int limit = Math.max(1, Math.min(properties.scheduler().catchUpLimit(), 20));
        List<LocalDateTime> fires = new ArrayList<>();
        LocalDateTime cursor = row.nextFireAt();
        while (!cursor.isAfter(now) && fires.size() < limit) {
            fires.add(cursor);
            cursor = cronService.next(row.cronExpression(), row.timezone(), cursor);
        }
        return fires;
    }

    private String eventJson(ScheduleRow row, LocalDateTime scheduledAt) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> data = Map.of(
                "schedule_id", row.scheduleId(),
                "workflow_id", row.workflowId(),
                "version_id", row.versionId(),
                "user_id", row.ownerUserId(),
                "space_id", row.spaceId() == null ? "" : row.spaceId(),
                "scheduled_at", scheduledAt.atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "inputs", readMap(row.inputsJson())
        );
        return json(Map.of(
                "specversion", "1.0",
                "id", eventId,
                "source", "pcd.scheduler-service",
                "type", "pcd.workflow.schedule.fire.v1",
                "time", LocalDateTime.now(clock).atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "data", data
        ));
    }

    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 Scheduler 数据", exception);
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " 格式无效");
        }
    }
}
