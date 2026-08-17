package org.project.automation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/** CloudEvents 1.0 兼容的文件生命周期事件信封。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LifecycleEvent(
        String specversion,
        String id,
        String source,
        String type,
        String subject,
        String time,
        @JsonProperty("actor_user_id") String actorUserId,
        @JsonProperty("space_id") String spaceId,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("causation_id") String causationId,
        JsonNode data
) {
}

