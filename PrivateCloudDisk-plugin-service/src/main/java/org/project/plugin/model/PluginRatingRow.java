package org.project.plugin.model;

import java.time.LocalDateTime;

/** 插件市场可见评分投影。 */
public record PluginRatingRow(
        String userId,
        int rating,
        String commentText,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
