package org.project.workflow.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 工作流模板市场请求与只读投影。 */
public final class WorkflowMarketplaceModels {
    private WorkflowMarketplaceModels() {
    }

    public record MarketplaceRow(
            String workflowId,
            String name,
            String slug,
            String description,
            String categoryCode,
            String tagsJson,
            long installCount,
            double ratingAverage,
            long ratingCount,
            LocalDateTime publishedAt
    ) {
    }

    public record TemplateSourceRow(
            String workflowId,
            String name,
            String description,
            String dslText,
            String graphJson
    ) {
    }

    public record ImportRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,118}[a-z0-9]$") String slug
    ) {
    }

    public record ReviewRequest(
            @Min(1) @Max(5) int rating,
            @Size(max = 2000) String comment
    ) {
    }

    public record ReviewRow(
            String userId,
            int rating,
            String commentText,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
