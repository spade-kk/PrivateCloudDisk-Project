package org.project.plugin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Workflow Service 请求解析当前用户/空间中可执行的插件能力。 */
public record CapabilityResolveRequest(
        @NotBlank @JsonProperty("capability_key") String capabilityKey,
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        @JsonProperty("user_id") String userId,
        @JsonProperty("space_id") String spaceId
) {
}
