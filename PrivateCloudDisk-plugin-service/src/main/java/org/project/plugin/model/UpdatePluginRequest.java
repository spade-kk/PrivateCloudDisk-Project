package org.project.plugin.model;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 插件草稿可修改字段；发布版本与包内容不可修改。 */
public record UpdatePluginRequest(
        @Size(min = 1, max = 120) String name,
        @Size(max = 5000) String description,
        @Pattern(regexp = "^(PRIVATE|SPACE|PUBLIC)$") String visibility
) {
}
