package org.project.plugin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** Automation Service 的内部入口匹配请求。 */
public record EntrypointMatchRequest(
        @NotBlank @JsonProperty("event_type") String eventType,
        @NotBlank @JsonProperty("actor_user_id") String actorUserId,
        @JsonProperty("space_id") String spaceId,
        @NotNull Map<String, Object> file
) {
}

