package org.project.automation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.project.automation.client.PluginCatalogClient;
import org.project.automation.client.PluginRuntimeClient;
import org.project.automation.client.PluginExecutionClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 无匹配入口必须快速 skipped，并持久化 processed Outbox。 */
class AutomationExecutionServiceTest {

    @Test
    void noMatchingPluginPublishesSkipped() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AutomationPersistenceService persistence = mock(AutomationPersistenceService.class);
        PluginCatalogClient catalog = mock(PluginCatalogClient.class);
        PluginRuntimeClient runtime = mock(PluginRuntimeClient.class);
        PluginExecutionClient executionClient = mock(PluginExecutionClient.class);
        when(persistence.claim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(ClaimOutcome.CLAIMED);
        when(catalog.matchPreprocess(any())).thenReturn(List.of());
        AutomationExecutionService service = new AutomationExecutionService(
                objectMapper, persistence, catalog, runtime, executionClient
        );

        String payload = """
                {
                  "specversion":"1.0",
                  "id":"11111111-1111-4111-8111-111111111111",
                  "type":"pcd.file.content.ready.v1",
                  "subject":"spaces/personal/files/f",
                  "actor_user_id":"22222222-2222-4222-8222-222222222222",
                  "correlation_id":"corr",
                  "data":{
                    "gate_id":"33333333-3333-4333-8333-333333333333",
                    "backend_task_id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "content_lease_ref":"opaque"
                  }
                }
                """;
        assertEquals(ClaimOutcome.CLAIMED, service.processRaw(payload));
        verifyNoInteractions(runtime);
        verify(persistence).complete(
                eq("11111111-1111-4111-8111-111111111111"),
                eq("33333333-3333-4333-8333-333333333333"),
                eq("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                anyString(), isNull(), eq(0), eq(0), eq("skipped"),
                anyString(), contains("\"status\":\"skipped\"")
        );
    }

    @Test
    void legacyAvailableEventUsesIndependentPostActivationPath() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AutomationPersistenceService persistence = mock(AutomationPersistenceService.class);
        PluginCatalogClient catalog = mock(PluginCatalogClient.class);
        PluginRuntimeClient runtime = mock(PluginRuntimeClient.class);
        PluginExecutionClient executionClient = mock(PluginExecutionClient.class);
        when(persistence.claim(anyString(), eq("pcd.file.available.v1"), anyString(), anyString()))
                .thenReturn(ClaimOutcome.CLAIMED);
        when(catalog.matchAvailable(any())).thenReturn(List.of());
        AutomationExecutionService service = new AutomationExecutionService(
                objectMapper, persistence, catalog, runtime, executionClient
        );

        String payload = """
                {
                  "eventId":"0123456789abcdef0123456789abcdef",
                  "fileId":"33333333-3333-4333-8333-333333333333",
                  "fileName":"report.txt",
                  "fileSize":12,
                  "fileType":"text/plain",
                  "userId":"22222222-2222-4222-8222-222222222222",
                  "eventTime":"2026-07-27T00:00:00Z"
                }
                """;
        assertEquals(ClaimOutcome.CLAIMED, service.processAvailableRaw(payload));
        verifyNoInteractions(runtime);
        verify(persistence).completeWithoutOutbox(
                anyString(),
                eq("22222222-2222-4222-8222-222222222222"),
                isNull(),
                eq("pcd.file.available.v1"),
                eq(0),
                eq(0),
                eq("skipped"),
                anyString()
        );
    }
}
