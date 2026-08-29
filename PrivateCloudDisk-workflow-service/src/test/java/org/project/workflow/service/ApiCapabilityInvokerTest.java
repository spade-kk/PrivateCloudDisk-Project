package org.project.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.project.workflow.config.WorkflowProperties;
import org.project.workflow.model.WorkflowModels.CapabilityInvocation;
import org.project.workflow.model.WorkflowModels.CapabilityResult;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ApiCapabilityInvokerTest {
    private RestClient.Builder builder;
    private RabbitTemplate rabbitTemplate;
    private static final String USER = "00000000-0000-0000-0000-000000000002";
    private static final String FILE = "00000000-0000-0000-0000-000000000003";

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        // 生产侧服务凭证由 WorkflowConfig 在 RestClient.Builder 上设置默认头，测试保持一致。
        builder = RestClient.builder().defaultHeader("X-PCD-Service-Token", "test-token");
    }

    /**
     * 绑定 MockRestServiceServer 后再从同一 builder 构建客户端：bindTo(RestClient.Builder)
     * 会把 mock 工厂写入该 builder，后续 build() 出的客户端才能被拦截（否则 clone 会覆盖工厂）。
     */
    private ApiCapabilityInvoker invokerAfterBind(MockRestServiceServer server) {
        return new ApiCapabilityInvoker(
                properties(), new ObjectMapper(), rabbitTemplate, new SimpleCapabilityBreaker(),
                builder.build(), builder.build());
    }

    private static WorkflowProperties properties() {
        return new WorkflowProperties(
                "http://platform", "http://plugin-service", "http://plugin-runtime",
                "http://cloudflow-runtime", "http://storage", "http://scheduler",
                "test-token", new WorkflowProperties.Worker(false, 1000, 180, 16384)
        );
    }

    private static CapabilityInvocation invocation(String key, Map<String, Object> input) {
        return new CapabilityInvocation(
                key, "00000000-0000-0000-0000-000000000001", "step-1",
                USER, "00000000-0000-0000-0000-000000000004", input);
    }

    @Test
    void routesFileMetadataToPlatformInternalWithContextHeaders() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiCapabilityInvoker invoker = invokerAfterBind(server);
        server.expect(requestTo("http://platform/business/internal/storage/files/" + FILE + "?uid=" + USER))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-PCD-Service-Token", "test-token"))
                .andExpect(header("X-PCD-User-Id", USER))
                .andExpect(header("X-PCD-Space-Id", "00000000-0000-0000-0000-000000000004"))
                .andExpect(header("X-Space-Id", "00000000-0000-0000-0000-000000000004"))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"id\":\"" + FILE + "\",\"name\":\"报表.xlsx\",\"type\":\"xlsx\",\"size\":1024}}",
                        MediaType.APPLICATION_JSON));

        CapabilityResult result = invoker.invoke(
                invocation("api:file.metadata.get", Map.of("file_id", FILE)), "{\"timeout_seconds\":10}");

        assertTrue(result.success());
        assertEquals("报表.xlsx", result.output().get("name"));
        server.verify();
    }

    @Test
    void mapsDownstreamBusinessErrorToCapabilityError() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiCapabilityInvoker invoker = invokerAfterBind(server);
        server.expect(requestTo("http://platform/business/internal/storage/files/" + FILE + "?uid=" + USER))
                .andRespond(withSuccess(
                        "{\"code\":404,\"message\":\"文件不存在或已删除\"}", MediaType.APPLICATION_JSON));

        CapabilityResult result = invoker.invoke(
                invocation("api:file.metadata.get", Map.of("file_id", FILE)), "{}");

        assertFalse(result.success());
        assertEquals("WF-CAPABILITY-DATAPLANE-ERROR", result.errorCode());
    }

    @Test
    void contentGetRejectsNonTextFileTypeWithoutDownstreamContentCall() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiCapabilityInvoker invoker = invokerAfterBind(server);
        server.expect(requestTo("http://platform/business/internal/storage/files/" + FILE + "?uid=" + USER))
                .andRespond(withSuccess(
                        "{\"code\":200,\"data\":{\"id\":\"" + FILE + "\",\"name\":\"photo.png\",\"type\":\"png\",\"size\":2048}}",
                        MediaType.APPLICATION_JSON));

        CapabilityResult result = invoker.invoke(
                invocation("api:file.content.get", Map.of("file_id", FILE)), "{}");

        assertFalse(result.success());
        assertEquals("WF-CAPABILITY-CONTENT-TYPE", result.errorCode());
        server.verify();
    }

    @Test
    void retriesThenFailsOnPersistentTransportErrorForIdempotentGet() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiCapabilityInvoker invoker = invokerAfterBind(server);
        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(requestTo("http://platform/business/internal/capability/files/list?space_id=s-1&uid=" + USER))
                    .andRespond(withServerError());
        }

        CapabilityResult result = invoker.invoke(
                invocation("api:file.list", Map.of("space_id", "s-1")), "{}");

        assertFalse(result.success());
        assertEquals("WF-CAPABILITY-DATAPLANE-UNAVAILABLE", result.errorCode());
        assertTrue(result.retryable());
        server.verify();
    }

    @Test
    void notificationSendPublishesRabbitEventWithoutHttpCall() {
        ApiCapabilityInvoker invoker = new ApiCapabilityInvoker(
                properties(), new ObjectMapper(), rabbitTemplate, new SimpleCapabilityBreaker(),
                null, null);

        CapabilityResult result = invoker.invoke(
                invocation("api:notification.send", Map.of("title", "标题", "body", "正文")), "{}");

        assertTrue(result.success());
        verify(rabbitTemplate).convertAndSend(
                eq("pcd.notification.exchange"), eq("notification.push"), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void unknownKeyIsNotHandled() {
        ApiCapabilityInvoker invoker = new ApiCapabilityInvoker(
                properties(), new ObjectMapper(), rabbitTemplate, new SimpleCapabilityBreaker(),
                null, null);
        CapabilityResult result = invoker.invoke(
                invocation("api:not.invoker", Map.of()), "{}");
        assertEquals(null, result);
    }

    @Test
    void routesAgentWorkflowListThroughDomainServiceWithoutPublicHttpHop() {
        WorkflowService workflowService = mock(WorkflowService.class);
        when(workflowService.list(USER, "00000000-0000-0000-0000-000000000004", 2, 25))
                .thenReturn(java.util.List.of());
        ApiCapabilityInvoker invoker = new ApiCapabilityInvoker(
                properties(), new ObjectMapper(), rabbitTemplate, new SimpleCapabilityBreaker(),
                null, null, workflowService);

        CapabilityResult result = invoker.invoke(
                invocation("api:workflow.list", Map.of("page", 2, "size", 25)), "{}");

        assertTrue(result.success());
        assertEquals(java.util.List.of(), result.output().get("items"));
        verify(workflowService).list(USER, "00000000-0000-0000-0000-000000000004", 2, 25);
    }
}
