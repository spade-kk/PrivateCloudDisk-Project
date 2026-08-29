package org.project.plugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.project.plugin.client.PlatformAuthorizationClient;
import org.project.plugin.model.CursorPage;
import org.project.plugin.model.ExecutionAuditInput;
import org.project.plugin.model.ExecutionLogInput;
import org.project.plugin.model.ExecutionObservabilityRequest;
import org.project.plugin.model.PluginExecutionAccessScope;
import org.project.plugin.model.PluginExecutionLogLine;
import org.project.plugin.model.PluginExecutionRow;
import org.project.plugin.repository.PluginExecutionMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [PLUGIN-EXEC-OBS-001] 服务层的分页、授权和二次脱敏回归测试。
 *
 * <p>此处不替代 MySQL/Flyway 集成测试；它锁定了控制器以下最容易造成跨空间泄漏或重复写入的
 * 决策点，数据库索引与迁移由部署环境的迁移校验覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class PluginExecutionServiceTest {
    @Mock private PluginExecutionMapper mapper;
    @Mock private PlatformAuthorizationClient authorizationClient;

    private PluginExecutionService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new PluginExecutionService(
                mapper, authorizationClient, new ExecutionObservabilitySanitizer(objectMapper), objectMapper
        );
    }

    @Test
    void ownerCanReadAscLogsWithStableCursor() {
        when(mapper.findAccessScope("execution-1"))
                .thenReturn(new PluginExecutionAccessScope("execution-1", "owner-1", "space-1"));
        when(mapper.listLogsAsc(eq("execution-1"), isNull(), eq(3), isNull(), isNull(), eq(""), eq("")))
                .thenReturn(List.of(log(1), log(2), log(3)));

        CursorPage<PluginExecutionLogLine> page = service.logs(
                "execution-1", "owner-1", null, 2, "asc", null, null, "", ""
        );

        assertEquals(List.of(1L, 2L), page.items().stream().map(PluginExecutionLogLine::sequenceNo).toList());
        assertEquals("2", page.nextCursor());
        assertTrue(page.hasMore());
        verify(authorizationClient, never()).canManagePlugins(any(), any());
    }

    @Test
    void spacePluginManagerCanReadButUnrelatedUserCannotBeTreatedAsOwner() {
        when(mapper.findAccessScope("execution-2"))
                .thenReturn(new PluginExecutionAccessScope("execution-2", "owner-2", "space-2"));
        when(authorizationClient.canManagePlugins("manager-2", "space-2")).thenReturn(true);
        when(mapper.listLogsAsc(eq("execution-2"), isNull(), eq(2), isNull(), isNull(), eq(""), eq("")))
                .thenReturn(List.of(log(1)));

        CursorPage<PluginExecutionLogLine> page = service.logs(
                "execution-2", "manager-2", null, 1, "asc", null, null, "", ""
        );

        assertEquals(1, page.items().size());
        verify(authorizationClient).canManagePlugins("manager-2", "space-2");
    }

    @Test
    void spaceManagerListsOnlyTheCurrentSpaceInsteadOfAllPluginExecutions() {
        when(mapper.findPluginOwner("plugin-4")).thenReturn("owner-4");
        when(authorizationClient.canManagePlugins("manager-4", "space-4")).thenReturn(true);
        when(mapper.listManagedSpace("plugin-4", "space-4", "SUCCESS", 0, 20)).thenReturn(List.of());

        List<PluginExecutionRow> executions = service.list(
                "plugin-4", "manager-4", "space-4", "SUCCESS", 1, 20
        );

        assertTrue(executions.isEmpty());
        verify(mapper).listManagedSpace("plugin-4", "space-4", "SUCCESS", 0, 20);
        verify(mapper, never()).listOwned(eq("plugin-4"), eq("manager-4"), any(), anyInt(), anyInt());
    }

    @Test
    void observationWritesSanitizedFactsAndDoesNotAdvanceOnDuplicateDelivery() {
        when(mapper.findAccessScope("execution-3"))
                .thenReturn(new PluginExecutionAccessScope("execution-3", "owner-3", "space-3"));
        when(mapper.registerObservation("execution-3", "delivery-3")).thenReturn(1);
        when(mapper.lockNextLogSequence("execution-3")).thenReturn(1L);
        when(mapper.lockNextAuditSequence("execution-3")).thenReturn(1L);
        ExecutionObservabilityRequest request = new ExecutionObservabilityRequest(
                "execution-3", "delivery-3",
                List.of(new ExecutionLogInput(Instant.parse("2026-08-23T00:00:00Z"), "INFO", "STDOUT",
                        "Bearer a-very-secret-token /var/lib/pcd/data.txt", 0L)),
                List.of(new ExecutionAuditInput(
                        null, null, "api.file.list", "PLATFORM_API", "file.list",
                        Map.of("path", "/var/lib/pcd/data.txt"), Map.of("token", "secret"), Map.of(),
                        "SUCCESS", 12L, 0, null, null, Instant.parse("2026-08-23T00:00:00Z")
                ))
        );

        service.recordObservability(request);

        verify(mapper).insertLogLine(eq("execution-3"), eq(1L), any(), eq("INFO"), eq("STDOUT"),
                org.mockito.ArgumentMatchers.argThat(value -> !value.contains("a-very-secret-token")
                        && !value.contains("/var/lib/pcd")), eq(0L));
        verify(mapper).insertAuditTrail(any(), eq("execution-3"), isNull(), eq(1L), eq("api.file.list"),
                eq("PLATFORM_API"), eq("file.list"), any(),
                org.mockito.ArgumentMatchers.argThat(value -> !value.contains("/var/lib/pcd")),
                org.mockito.ArgumentMatchers.argThat(value -> !value.contains("secret")), any(), any(), any(),
                eq("SUCCESS"), eq(12L), eq(0), eq(""), eq(""), any());

        when(mapper.registerObservation("execution-3", "delivery-3")).thenReturn(0);
        service.recordObservability(request);
        verify(mapper, org.mockito.Mockito.times(1)).insertLogLine(any(), any(Long.class), any(), any(), any(), any(), any(Long.class));
        assertFalse(request.logs().isEmpty());
    }

    private static PluginExecutionLogLine log(long sequence) {
        return new PluginExecutionLogLine(sequence, LocalDateTime.of(2026, 8, 23, 8, 0), "INFO", "STDOUT", "line-" + sequence, 0);
    }
}
