package org.project.plugin.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 创建插件草稿请求。 */
public record CreatePluginRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9-]{2,119}$", message = "只能使用小写字母、数字和连字符")
        String slug,
        @Size(max = 5000) String description,
        @NotBlank
        @Pattern(regexp = "^(CLOUD_PLUGIN|LOCAL_PLUGIN|WORKFLOW_PLUGIN)$")
        String type,
        @NotBlank
        @Pattern(regexp = "^(PRIVATE|SPACE|PUBLIC)$")
        String visibility
) {
}
