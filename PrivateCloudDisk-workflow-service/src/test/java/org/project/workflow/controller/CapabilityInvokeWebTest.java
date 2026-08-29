package org.project.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.project.workflow.config.InternalServiceFilter;
import org.project.workflow.config.WorkflowProperties;
import org.project.workflow.model.WorkflowModels.AgentCapabilityInvocation;
import org.project.workflow.model.WorkflowModels.CapabilityResult;
import org.project.workflow.model.WorkflowModels.McpCapabilityListRequest;
import org.project.workflow.model.WorkflowModels.McpCapabilityPage;
import org.project.workflow.service.CapabilityHubService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 能力中心统一调用接口 HTTP/安全测试（需求六 6.1-6.4 / 七 7.13）。
 * 覆盖：缺少/伪造服务凭证拒绝（401）、合法凭证走通 invoke，以及能力发现只能使用
 * Gateway 注入的已认证用户身份获取（CloudFlow LS/Web IDE 安全边界）。
 *
 * <p>使用 standalone MockMvc 而非 @WebMvcTest：Controller 位于内网能力投影域，
 * 用 Mock 服务 + 真实 InternalServiceFilter 验证服务身份认证边界，
 * 不加载 @MapperScan 切片（避免 MyBatis mapper 在 web 切片中构造失败）。</p>
 */
class CapabilityInvokeWebTest {
    private static final String UNIT_TOKEN = "unit-token";

    private MockMvc mockMvc;
    private CapabilityHubService service;

    @BeforeEach
    void setUp() {
        service = mock(CapabilityHubService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        WorkflowProperties properties = new WorkflowProperties(
                "http://platform", "http://plugin-service", "http://plugin-runtime",
                "http://cloudflow-runtime", "http://storage", "http://scheduler",
                UNIT_TOKEN, new WorkflowProperties.Worker(false, 1000, 180, 16384)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new CapabilityController(service))
                .addFilters(new InternalServiceFilter(properties, objectMapper))
                .build();
    }

    @Test
    void invokeWithoutServiceTokenIsRejectedAsUnauthenticated() throws Exception {
        mockMvc.perform(post("/internal/v1/capabilities/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInvokeBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-UNAUTHENTICATED"));
    }

    @Test
    void invokeWithForgedTokenIsAlsoRejected() throws Exception {
        mockMvc.perform(post("/internal/v1/capabilities/invoke")
                        .header("X-PCD-Service-Token", "forged-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInvokeBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-UNAUTHENTICATED"));
    }

    @Test
    void invokeWithValidTokenReachesCapabilityHub() throws Exception {
        when(service.invokeAgent(any(AgentCapabilityInvocation.class)))
                .thenReturn(CapabilityResult.success(Map.of("text", "HELLO")));

        mockMvc.perform(post("/internal/v1/capabilities/invoke")
                        .header("X-PCD-Service-Token", UNIT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInvokeBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.output.text").value("HELLO"));
    }

    @Test
    void capabilityDiscoveryRequiresGatewayAuthenticatedPrincipal() throws Exception {
        when(service.searchVisibleTo(any(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of());
        mockMvc.perform(get("/capabilities").header("X-User-Id", "user-1").param("query", "file"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void mcpPrivateEndpointsRejectMissingServiceTokenAndAcceptTrustedService() throws Exception {
        mockMvc.perform(post("/internal/v1/capabilities/mcp/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mcpToolListBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-UNAUTHENTICATED"));

        when(service.listMcpVisible(any(McpCapabilityListRequest.class)))
                .thenReturn(new McpCapabilityPage(List.of(), null));
        mockMvc.perform(post("/internal/v1/capabilities/mcp/tools")
                        .header("X-PCD-Service-Token", UNIT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mcpToolListBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.capabilities").isArray());
    }

    private static String validInvokeBody() {
        return """
                {
                  "capabilityKey": "builtin:text.transform",
                  "executionId": "00000000-0000-0000-0000-000000000001",
                  "stepId": "transform",
                  "attempt": 1,
                  "userId": "00000000-0000-0000-0000-000000000002",
                  "spaceId": "00000000-0000-0000-0000-000000000004",
                  "input": {"text": "hello", "operation": "upper"},
                  "declaredPermissions": ["file.read"],
                  "grantedPermissions": ["file.read"],
                  "traceId": "trace-1",
                  "idempotencyKey": "00000000-0000-0000-0000-000000000001:transform:1"
                }
                """;
    }

    private static String mcpToolListBody() {
        return """
                {
                  "userId": "00000000-0000-0000-0000-000000000002",
                  "tenantId": "tenant-a",
                  "spaceId": "00000000-0000-0000-0000-000000000004",
                  "offset": 0,
                  "limit": 25
                }
                """;
    }
}
