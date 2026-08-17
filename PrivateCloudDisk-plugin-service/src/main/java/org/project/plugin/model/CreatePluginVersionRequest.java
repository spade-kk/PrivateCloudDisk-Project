package org.project.plugin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** 创建可编辑版本；源代码上传、校验和发布必须按状态机顺序执行。 */
public record CreatePluginVersionRequest(
        @NotBlank
        @Pattern(regexp = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-[0-9A-Za-z.-]+)?$")
        String version,
        @NotBlank
        @Pattern(regexp = "^(PYTHON_3_11|JAVASCRIPT_ES2022|PCD_WORKFLOW_V1)$")
        String runtime,
        @NotBlank
        @Pattern(regexp = "^(?!/)(?!.*\\.\\.)[A-Za-z0-9_./-]{1,255}$")
        String entrypoint,
        @NotEmpty @Size(max = 64)
        @JsonProperty("permissions")
        List<@NotBlank String> permissions,
        @NotEmpty @Size(max = 16)
        @JsonProperty("supported_platforms")
        List<@NotBlank String> supportedPlatforms,
        @NotEmpty @Size(max = 8)
        @JsonProperty("client_types")
        List<@NotBlank String> clientTypes,
        @Valid @Size(max = 32)
        List<PluginEntrypointSpec> entrypoints,
        @Valid @Size(max = 64)
        List<PluginCapabilitySpec> capabilities,
        Map<String, Object> manifest
) {
}
