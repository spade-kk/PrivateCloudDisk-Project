package org.project.workflow.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.project.workflow.exception.WorkflowApiException;
import org.project.workflow.model.ApiResponse;
import org.project.workflow.model.WorkflowModels.CapabilityRow;
import org.project.workflow.service.CapabilityHubService;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 能力发现 API 与服务间动态能力投影入口。 */
@Validated
@RestController
public class CapabilityController {
    private final CapabilityHubService service;

    public CapabilityController(CapabilityHubService service) {
        this.service = service;
    }

    @GetMapping("/capabilities")
    public ApiResponse<?> search(
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.search(sourceType, query, page, size), requestId(request));
    }

    @GetMapping("/capabilities/{key}")
    public ApiResponse<?> get(@PathVariable String key, HttpServletRequest request) {
        CapabilityRow capability = service.get(key);
        if (capability == null) {
            throw new WorkflowApiException(
                    "WF-CAPABILITY-NOT-FOUND", HttpStatus.NOT_FOUND, "能力不存在"
            );
        }
        return ApiResponse.ok(capability, requestId(request));
    }

    @PostMapping("/internal/v1/capabilities/projections")
    public ApiResponse<?> project(
            @Valid @RequestBody CapabilityRow capability,
            HttpServletRequest request
    ) {
        service.upsertProjection(capability);
        return ApiResponse.ok(capability, requestId(request));
    }

    private static String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value == null ? "" : value;
    }
}
