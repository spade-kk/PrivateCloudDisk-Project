package org.project.workflow.controller;

import org.project.workflow.service.WorkflowMarketplaceService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 工作流模板市场内部审核入口，由统一 InternalServiceFilter 校验服务凭证。 */
@RestController
@RequestMapping("/internal/v1/marketplace/workflows")
public class InternalWorkflowMarketplaceController {
    private final WorkflowMarketplaceService service;

    public InternalWorkflowMarketplaceController(WorkflowMarketplaceService service) {
        this.service = service;
    }

    @PostMapping("/{workflowId}/review")
    public Map<String, Object> review(
            @PathVariable String workflowId,
            @RequestParam String status
    ) {
        service.review(workflowId, status);
        return Map.of("workflow_id", workflowId, "review_status", status);
    }
}
