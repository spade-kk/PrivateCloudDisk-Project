package org.project.scheduler.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Map;

/** Scheduler 内部契约与数据库投影。 */
public final class SchedulerModels {
    private SchedulerModels() {
    }

    public record CreateScheduleRequest(
            @NotBlank @JsonProperty("workflow_id") String workflowId,
            @NotBlank @JsonProperty("version_id") String versionId,
            @NotBlank @JsonProperty("user_id") String userId,
            @JsonProperty("space_id") String spaceId,
            @NotBlank @Size(max = 128) String cron,
            @NotBlank @Size(max = 64) String timezone,
            @NotBlank @Pattern(regexp = "SKIP|FIRE_ONCE|CATCH_UP_LIMITED")
            @JsonProperty("misfire_policy") String misfirePolicy,
            Map<String, Object> inputs
    ) {
    }

    public record ScheduleRow(
            String scheduleId,
            String workflowId,
            String versionId,
            String ownerUserId,
            String spaceId,
            String cronExpression,
            String timezone,
            String misfirePolicy,
            String inputsJson,
            String status,
            LocalDateTime nextFireAt,
            LocalDateTime lastScheduledAt,
            long rowVersion
    ) {
    }

    public record OutboxRow(
            String eventId,
            String payloadJson,
            int attempt
    ) {
    }
}
