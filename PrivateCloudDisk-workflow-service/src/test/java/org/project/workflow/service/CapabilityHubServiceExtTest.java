package org.project.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.project.workflow.client.PlatformAuthorizationClient;
import org.project.workflow.config.WorkflowProperties;
import org.project.workflow.exception.WorkflowApiException;
import org.project.workflow.model.WorkflowModels.CapabilityInvocation;
import org.project.workflow.model.WorkflowModels.CapabilityResult;
import org.project.workflow.model.WorkflowModels.CapabilityRow;
import org.project.workflow.model.WorkflowModels.McpCapabilityListRequest;
import org.project.workflow.repository.CapabilityAuditMapper;
import org.project.workflow.repository.CapabilityInvocationMapper;
import org.project.workflow.repository.CapabilityMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityHubServiceExtTest {
    private static final String USER = "00000000-0000-0000-0000-000000000002";
    private static final String SPACE = "00000000-0000-0000-0000-000000000004";
    private static final String FILE = "00000000-0000-0000-0000-000000000003";

    @Test
    void invokeRejectsMalformedCapabilityKey() {
        CapabilityMapper mapper = mock(CapabilityMapper.class);
        CapabilityHubService service = service(mapper, mock(CapabilityInvocationMapper.class), authority(List.of("file.read")));

        CapabilityResult result = service.invoke(invocation("api:file metadata.get", Map.of("file_id", FILE)));

        assertFalse(result.success());
        assertEquals("WF-CAPABILITY-KEY", result.errorCode());
        verify(mapper, never()).findByKey(anyString());
    }

    @Test
    void builtinInvokeSkipsPermissionServiceWhenNoPermissionsDeclared() {
        CapabilityMapper mapper = mock(CapabilityMapper.class);
        PlatformAuthorizationClient authority = mock(PlatformAuthorizationClient.class);
        when(mapper.findByKey("builtin:text.transform")).thenReturn(capability(
                "builtin:text.transform", "[]"));
        CapabilityHubService service = service(mapper, mock(CapabilityInvocationMapper.class), authority);

        CapabilityResult result = service.invoke(
                invocation("builtin:text.transform", Map.of("text", "hi", "operation", "upper")));

        assertTrue(result.success());
        assertEquals("HI", result.output().get("text"));
        verify(authority, never()).resolveGrantedPermissions(anyString(), anyString());
    }

    @Test
    void invokeRejectsWhenRequiredPermissionNotGranted() {
        CapabilityMapper mapper = mock(CapabilityMapper.class);
        when(mapper.findByKey("api:file.metadata.get")).thenReturn(capability(
                "api:file.metadata.get", "[\"file.read\"]"));
        CapabilityHubService service = service(mapper, mock(CapabilityInvocationMapper.class), authority(List.of()));

        CapabilityResult result = service.invoke(
                invocation("api:file.metadata.get", Map.of("file_id", FILE)));

        assertFalse(result.success());
        assertEquals("WF-CAPABILITY-FORBIDDEN", result.errorCode());
    }

    @Test
    void invokePassesPermissionGateAndRoutesToDataPlane() {
        CapabilityMapper mapper = mock(CapabilityMapper.class);
        when(mapper.findByKey("api:file.metadata.get")).thenReturn(capability(
                "api:file.metadata.get", "[\"file.read\"]"));
        // 授权已满足，随后数据面不可达应表现为可重试的数据面错误，而不是权限被拒。
        CapabilityHubService service = service(mapper, mock(CapabilityInvocationMapper.class), authority(List.of("file.read")));

        CapabilityResult result = service.invoke(
                invocation("api:file.metadata.get", Map.of("file_id", FILE)));

        assertFalse(result.success());
        assertEquals("WF-CAPABILITY-DATAPLANE-UNAVAILABLE", result.errorCode());
    }

    @Test
    void invokeRejectsSchemaViolationBeforePermissionCheck() {
        CapabilityMapper mapper = mock(CapabilityMapper.class);
        when(mapper.findByKey("api:file.metadata.get")).thenReturn(capability(
                "api:file.metadata.get", "[\"file.read\"]"));
        CapabilityHubService service = service(mapper, mock(CapabilityInvocationMapper.class), authority(List.of("file.read")));

        CapabilityResult result = service.invoke(
                invocation("api:file.metadata.get", Map.of("file_id", "not-a-uuid")));

        assertFalse(result.success());
        assertEquals("WF-CAPABILITY-INPUT", result.errorCode());
    }

    @Test
    void invokeRecordsAuditForEveryResult() {
        CapabilityMapper mapper = mock(CapabilityMapper.class);
        CapabilityAuditMapper auditMapper = mock(CapabilityAuditMapper.class);
        CapabilityInvocationMapper invocationMapper = mock(CapabilityInvocationMapper.class);
        when(mapper.findByKey("builtin:date.now")).thenReturn(capability("builtin:date.now", "[]"));
        CapabilityHubService service = serviceWithAudit(
                mapper, invocationMapper, authority(List.of()), auditMapper);

        CapabilityResult result = service.invoke(invocation("builtin:date.now", Map.of()));

        assertTrue(result.success());
        verify(auditMapper).insert(any());
    }

    @Test
    void discoveryFiltersPermissionsAndTenantSpacePolicyBeforeReturningCapability() {
        CapabilityMapper mapper = mock(CapabilityMapper.class);
        CapabilityRow allowed = capabilityWithPolicy(
                "api:file.metadata.get", "[\"file.read\"]",
                "{\"tenant_ids\":[\"tenant-a\"],\"space_ids\":[\"" + SPACE + "\"]}"
        );
        CapabilityRow missingPermission = capability("api:file.content.get", "[\"file.write\"]");
        CapabilityRow wrongTenant = capabilityWithPolicy(
                "builtin:date.now", "[]", "{\"tenantIds\":[\"tenant-b\"]}"
        );
        when(mapper.search(null, null, 50, 0)).thenReturn(List.of(allowed, missingPermission, wrongTenant));
        PlatformAuthorizationClient authority = authority(List.of("file.read"));
        CapabilityHubService service = service(mapper, mock(CapabilityInvocationMapper.class), authority);

        List<CapabilityRow> visible = service.searchVisibleTo(USER, "tenant-a", SPACE, null, null, 1, 50);

        assertEquals(List.of("api:file.metadata.get"), visible.stream().map(CapabilityRow::capabilityKey).toList());
        verify(authority).resolveGrantedPermissions(USER, SPACE);
    }

    @Test
    void discoveryRejectsMissingPrincipalAndMalformedAvailabilityPolicy() {
        CapabilityMapper mapper = mock(CapabilityMapper.class);
        CapabilityHubService service = service(mapper, mock(CapabilityInvocationMapper.class), authority(List.of()));
        WorkflowApiException missingPrincipal = assertThrows(
                WorkflowApiException.class,
                () -> service.searchVisibleTo("", null, null, null, null, 1, 50)
        );
        assertEquals("WF-CAPABILITY-UNAUTHENTICATED", missingPrincipal.code());

        CapabilityRow malformed = capabilityWithPolicy("builtin:date.now", "[]", "not-json");
        when(mapper.search(null, null, 50, 0)).thenReturn(List.of(malformed));
        assertTrue(service.searchVisibleTo(USER, null, null, null, null, 1, 50).isEmpty());
    }

    @Test
    void mcpDiscoveryExportsOnlyReviewedActiveAndAuthorizedCapabilitySubset() {
        CapabilityMapper mapper = mock(CapabilityMapper.class);
        CapabilityRow allowed = capability("api:file.metadata.get", "[\"file.read\"]");
        CapabilityRow sensitive = capability("api:share.create", "[\"file.share\"]");
        CapabilityRow disabled = new CapabilityRow(
                "api:file.list", "API", null, null, "disabled", "", "{\"type\":\"object\"}",
                "{}", "[]", "{}", "DISABLED", 1
        );
        when(mapper.search(null, null, 25, 0)).thenReturn(List.of(allowed, sensitive, disabled));
        CapabilityHubService service = service(mapper, mock(CapabilityInvocationMapper.class), authority(List.of("file.read")));

        var page = service.listMcpVisible(new McpCapabilityListRequest(USER, "tenant-a", SPACE, 0, 25));

        assertEquals(List.of("api:file.metadata.get"), page.capabilities().stream()
                .map(CapabilityRow::capabilityKey).toList());
        assertEquals(null, page.nextOffset());
    }

    @Test
    void mcpPolicyRejectsUnreviewedDestructiveCapabilityBeforeIdempotencyClaim() {
        CapabilityInvocationMapper invocationMapper = mock(CapabilityInvocationMapper.class);
        CapabilityHubService service = service(mock(CapabilityMapper.class), invocationMapper, authority(List.of("file.share")));

        var result = service.invokeMcp(new org.project.workflow.model.WorkflowModels.McpCapabilityInvocation(
                "api:share.create", USER, "tenant-a", SPACE, Map.of("share_name", "x"),
                "trace-1", "mcp-test-key", "test-agent"
        ));

        assertFalse(result.success());
        assertEquals("WF-CAPABILITY-NOT-FOUND", result.errorCode());
        verify(invocationMapper, never()).claim(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
    }

    private static CapabilityInvocation invocation(String key, Map<String, Object> input) {
        return new CapabilityInvocation(key, "00000000-0000-0000-0000-000000000001", "step-1", USER, SPACE, input);
    }

    private static CapabilityRow capability(String key, String permissions) {
        // 内置能力使用宽松 schema（只校验对象本身），API 能力才要求 file_id（UUID）字段。
        String inputSchema = key.startsWith("api")
                ? "{\"type\":\"object\",\"required\":[\"file_id\"],\"properties\":{\"file_id\":{\"type\":\"string\",\"maxLength\":36,\"pattern\":\"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$\"}}}"
                : "{\"type\":\"object\",\"properties\":{}}";
        return new CapabilityRow(
                key, key.startsWith("api") ? "API" : "BUILTIN", null, null,
                "测试能力", "", inputSchema,
                "{}", permissions, "{}", "ACTIVE", 1
        );
    }

    private static CapabilityRow capabilityWithPolicy(String key, String permissions, String policy) {
        CapabilityRow base = capability(key, permissions);
        return new CapabilityRow(
                base.capabilityKey(), base.sourceType(), base.sourceId(), base.sourceVersion(),
                base.displayName(), base.description(), base.inputSchemaJson(), base.outputSchemaJson(),
                base.requiredPermissionsJson(), policy, base.status(), base.revision()
        );
    }

    private static PlatformAuthorizationClient authority(List<String> granted) {
        PlatformAuthorizationClient client = mock(PlatformAuthorizationClient.class);
        when(client.resolveGrantedPermissions(anyString(), anyString())).thenReturn(granted);
        return client;
    }

    private static CapabilityHubService service(
            CapabilityMapper mapper,
            CapabilityInvocationMapper invocationMapper,
            PlatformAuthorizationClient authority
    ) {
        return serviceWithAudit(mapper, invocationMapper, authority, mock(CapabilityAuditMapper.class));
    }

    private static CapabilityHubService serviceWithAudit(
            CapabilityMapper mapper,
            CapabilityInvocationMapper invocationMapper,
            PlatformAuthorizationClient authority,
            CapabilityAuditMapper auditMapper
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        WorkflowProperties properties = new WorkflowProperties(
                "http://platform", "http://plugin-service", "http://plugin-runtime",
                "http://cloudflow-runtime", "http://storage", "http://scheduler",
                "test-token", new WorkflowProperties.Worker(false, 1000, 180, 16384)
        );
        ApiCapabilityInvoker invoker = new ApiCapabilityInvoker(
                properties, objectMapper, rabbitTemplate, RestClient.builder(), new SimpleCapabilityBreaker()
        );
        return new CapabilityHubService(
                mapper, objectMapper, rabbitTemplate, mock(PluginCapabilityClient.class),
                invocationMapper, authority, new CapabilitySchemaValidator(objectMapper),
                invoker, auditMapper
        );
    }
}
