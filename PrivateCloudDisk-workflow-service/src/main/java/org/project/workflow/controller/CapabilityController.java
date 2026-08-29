package org.project.workflow.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.project.workflow.exception.WorkflowApiException;
import org.project.workflow.model.ApiResponse;
import org.project.workflow.model.WorkflowModels.CapabilityRow;
import org.project.workflow.model.WorkflowModels.AgentCapabilityInvocation;
import org.project.workflow.model.WorkflowModels.McpCapabilityInvocation;
import org.project.workflow.model.WorkflowModels.McpCapabilityListRequest;
import org.project.workflow.model.WorkflowModels.McpProtocolAuditEntry;
import org.project.workflow.service.CapabilityHubService;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            HttpServletRequest request
    ) {
        // [CLOUDFLOW-LS-AUTH-001] 语言服务能力补全必须从网关已认证身份派生，
        // 不能把完整 Capability Registry 当作公开目录返回。Gateway 覆盖客户端伪造的
        // X-User-Id；本服务只消费其可信投影并再次按空间权限/租户策略过滤。
        return ApiResponse.ok(
                service.searchVisibleTo(userId, tenantId, spaceId, sourceType, query, page, size),
                requestId(request)
        );
    }

    @GetMapping("/capabilities/{key}")
    public ApiResponse<?> get(
            @PathVariable String key,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Space-Id", required = false) String spaceId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            HttpServletRequest request
    ) {
        CapabilityRow capability = service.getVisibleTo(userId, tenantId, spaceId, key);
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

    /** 仅供 CloudFlow gRPC Agent 代理调用；InternalServiceFilter 负责服务身份认证。 */
    @PostMapping("/internal/v1/capabilities/invoke")
    public ApiResponse<?> invoke(
            @Valid @RequestBody AgentCapabilityInvocation invocation,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.invokeAgent(invocation), requestId(request));
    }

    /**
     * 私网 MCP Adapter 专用发现入口。服务凭证由 InternalServiceFilter 校验，用户上下文
     * 来自 Gateway 签名链而非外部 Agent 自报；该端点不会被 Gateway 的公开工作流路由暴露。
     */
    @PostMapping("/internal/v1/capabilities/mcp/tools")
    public ApiResponse<?> mcpTools(
            @Valid @RequestBody McpCapabilityListRequest query,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.listMcpVisible(query), requestId(request));
    }

    /** MCP tools/call 专用调用入口，复用 Hub 的 schema、权限、分发和持久化幂等管线。 */
    @PostMapping("/internal/v1/capabilities/mcp/invoke")
    public ApiResponse<?> mcpInvoke(
            @Valid @RequestBody McpCapabilityInvocation invocation,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.invokeMcp(invocation), requestId(request));
    }

    /** MCP 协议发现和只读资源调用的审计入口；MCP 服务不直连审计数据库。 */
    @PostMapping("/internal/v1/capabilities/mcp/audit")
    public ApiResponse<?> mcpAudit(
            @Valid @RequestBody McpProtocolAuditEntry entry,
            HttpServletRequest request
    ) {
        service.recordMcpProtocolAudit(entry);
        return ApiResponse.ok(Map.of("accepted", true), requestId(request));
    }

    private static String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value == null ? "" : value;
    }
}
