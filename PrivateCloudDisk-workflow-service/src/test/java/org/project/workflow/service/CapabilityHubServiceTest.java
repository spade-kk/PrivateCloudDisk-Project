package org.project.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.project.workflow.client.PlatformAuthorizationClient;
import org.project.workflow.model.WorkflowModels.AgentCapabilityInvocation;
import org.project.workflow.model.WorkflowModels.CapabilityResult;
import org.project.workflow.model.WorkflowModels.CapabilityRow;
import org.project.workflow.repository.CapabilityInvocationMapper;
import org.project.workflow.repository.CapabilityMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityHubServiceTest {
    @Test
    void agentInvocationChecksPermissionIntersectionAndPersistsResult() {
        CapabilityMapper capabilityMapper = mock(CapabilityMapper.class);
        CapabilityInvocationMapper invocationMapper = mock(CapabilityInvocationMapper.class);
        when(invocationMapper.claim(
                anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyInt(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.isNull(), anyString()
        )).thenReturn(1);
        when(capabilityMapper.findByKey("builtin:text.transform"))
                .thenReturn(capability("[\"file.read\"]"));
        CapabilityHubService service = service(capabilityMapper, invocationMapper);

        CapabilityResult result = service.invokeAgent(command(
                List.of("file.read"), List.of("file.read")
        ));

        assertTrue(result.success());
        assertEquals("HELLO", result.output().get("text"));
        verify(invocationMapper).complete(anyString(), anyString());
    }

    @Test
    void agentInvocationRejectsMissingGrantWithoutExecutingCapability() {
        CapabilityMapper capabilityMapper = mock(CapabilityMapper.class);
        CapabilityInvocationMapper invocationMapper = mock(CapabilityInvocationMapper.class);
        when(invocationMapper.claim(
                anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyInt(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.isNull(), anyString()
        )).thenReturn(1);
        when(capabilityMapper.findByKey("builtin:text.transform"))
                .thenReturn(capability("[\"file.read\"]"));
        CapabilityHubService service = service(capabilityMapper, invocationMapper);

        CapabilityResult result = service.invokeAgent(command(List.of("file.read"), List.of()));

        assertFalse(result.success());
        assertEquals("WF-CAPABILITY-FORBIDDEN", result.errorCode());
    }

    @Test
    void agentInvocationUsesPermissionIntersectionInsteadOfRequiringIdenticalSnapshots() {
        CapabilityMapper capabilityMapper = mock(CapabilityMapper.class);
        CapabilityInvocationMapper invocationMapper = mock(CapabilityInvocationMapper.class);
        when(invocationMapper.claim(
                anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyInt(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.isNull(), anyString()
        )).thenReturn(1);
        when(capabilityMapper.findByKey("builtin:text.transform"))
                .thenReturn(capability("[\"file.read\"]"));
        CapabilityHubService service = service(capabilityMapper, invocationMapper);

        CapabilityResult result = service.invokeAgent(command(
                List.of("file.read", "notification.send"), List.of("file.read")
        ));

        assertTrue(result.success());
        assertEquals("HELLO", result.output().get("text"));
    }

    private static CapabilityHubService service(
            CapabilityMapper capabilityMapper,
            CapabilityInvocationMapper invocationMapper
    ) {
        return new CapabilityHubService(
                capabilityMapper,
                new ObjectMapper(),
                mock(RabbitTemplate.class),
                mock(PluginCapabilityClient.class),
                invocationMapper,
                mock(PlatformAuthorizationClient.class)
        );
    }

    private static CapabilityRow capability(String permissions) {
        return new CapabilityRow(
                "builtin:text.transform", "BUILTIN", null, null,
                "文本转换", "", "{\"type\":\"object\"}", "{}",
                permissions, "{}", "ACTIVE", 1
        );
    }

    private static AgentCapabilityInvocation command(
            List<String> declared,
            List<String> granted
    ) {
        return new AgentCapabilityInvocation(
                "builtin:text.transform",
                "00000000-0000-0000-0000-000000000001",
                "transform",
                1,
                "00000000-0000-0000-0000-000000000002",
                null,
                Map.of("text", "hello", "operation", "upper"),
                declared,
                granted,
                "trace-1",
                "00000000-0000-0000-0000-000000000001:transform:1"
        );
    }
}
