package org.project.workflow.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.project.workflow.model.ApiResponse;
import org.project.workflow.model.WorkflowMarketplaceModels.ImportRequest;
import org.project.workflow.model.WorkflowMarketplaceModels.ReviewRequest;
import org.project.workflow.service.WorkflowMarketplaceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 工作流模板市场公开 API。 */
@Validated
@RestController
@RequestMapping("/marketplace/workflows")
public class WorkflowMarketplaceController {
    private final WorkflowMarketplaceService service;

    public WorkflowMarketplaceController(WorkflowMarketplaceService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                service.list(category, query, page, size), requestId(request)
        );
    }

    @PostMapping("/{workflowId}/submit")
    public ApiResponse<?> submit(
            @PathVariable String workflowId,
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request
    ) {
        service.submit(workflowId, userId);
        return ApiResponse.ok(Map.of("review_status", "PENDING"), requestId(request));
    }

    @PostMapping("/{workflowId}/import")
    public ApiResponse<?> importTemplate(
            @PathVariable String workflowId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @Valid @RequestBody ImportRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                service.importTemplate(workflowId, userId, spaceId, body),
                requestId(request)
        );
    }

    @PostMapping("/{workflowId}/reviews")
    public ApiResponse<?> rate(
            @PathVariable String workflowId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody ReviewRequest body,
            HttpServletRequest request
    ) {
        service.rate(workflowId, userId, body);
        return ApiResponse.ok(Map.of("saved", true), requestId(request));
    }

    @GetMapping("/{workflowId}/reviews")
    public ApiResponse<?> reviews(
            @PathVariable String workflowId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.reviews(workflowId, page, size), requestId(request));
    }

    private static String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value == null ? "" : value;
    }
}
