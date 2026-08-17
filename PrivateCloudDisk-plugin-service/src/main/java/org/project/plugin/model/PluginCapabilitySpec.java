package org.project.plugin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** 插件导出能力函数声明，供 Capability Hub 建立动态注册表。 */
public record PluginCapabilitySpec(
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9_]{0,127}$")
        String name,
        @Size(max = 1000) String description,
        @JsonProperty("input_schema") Map<String, Object> inputSchema,
        @JsonProperty("output_schema") Map<String, Object> outputSchema,
        @JsonProperty("permissions") List<String> permissions
) {
}
