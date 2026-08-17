package org.project.plugin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** 同一插件版本可以声明多个生命周期入口函数。 */
public record PluginEntrypointSpec(
        @NotBlank
        @JsonProperty("event")
        @Pattern(regexp = "^(pcd\\.file\\.content\\.ready\\.v1|pcd\\.file\\.available\\.v1)$")
        String event,
        @NotBlank
        @JsonProperty("function")
        @Pattern(regexp = "^[A-Za-z_][A-Za-z0-9_]{0,127}$")
        String functionName,
        @Min(0) @Max(10000)
        Integer priority,
        Map<String, Object> conditions,
        @NotEmpty @Size(max = 64)
        List<@NotBlank String> permissions
) {
}
