package org.project.plugin.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 市场评分与短评；内容以纯文本存储并由前端转义展示。 */
public record PluginRatingRequest(
        @Min(1) @Max(5) int rating,
        @Size(max = 2000) String comment
) {
}
