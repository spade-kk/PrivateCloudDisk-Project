package org.project.plugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.project.plugin.client.PlatformAuthorizationClient;
import org.project.plugin.model.EntrypointCandidateRow;
import org.project.plugin.model.EntrypointMatchRequest;
import org.project.plugin.repository.PluginEntrypointMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntrypointMatchServiceTest {
    @Test
    void 内容预处理入口缺少写权限时必须过滤() {
        PluginEntrypointMapper mapper = mock(PluginEntrypointMapper.class);
        PlatformAuthorizationClient authorization = mock(PlatformAuthorizationClient.class);
        EntrypointMatchService service = new EntrypointMatchService(
                mapper, authorization, new ObjectMapper()
        );
        EntrypointMatchRequest request = new EntrypointMatchRequest(
                "pcd.file.content.ready.v1",
                "11111111-1111-1111-1111-111111111111",
                null,
                Map.of(
                        "file_id", "22222222-2222-2222-2222-222222222222",
                        "name", "test.txt",
                        "mime_type", "text/plain",
                        "size", 12
                )
        );
        when(mapper.findCandidates(
                request.eventType(), request.actorUserId(), request.spaceId()
        )).thenReturn(List.of(new EntrypointCandidateRow(
                "installation", "plugin", "version",
                "PYTHON_3_11", "src/main.py", "preprocess", 100,
                "{}", "[\"file.content.read_staging\"]",
                "[\"file.content.read_staging\"]", "{}",
                java.time.LocalDateTime.now()
        )));
        when(authorization.canRunPreprocess(
                request.actorUserId(), request.spaceId(),
                "22222222-2222-2222-2222-222222222222"
        )).thenReturn(true);

        assertEquals(0, service.match(request).size());
    }
}
