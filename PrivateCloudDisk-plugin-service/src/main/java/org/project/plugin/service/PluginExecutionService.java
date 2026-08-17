package org.project.plugin.service;

import lombok.RequiredArgsConstructor;
import org.project.plugin.model.ExecutionRecordRequest;
import org.project.plugin.model.PluginExecutionRow;
import org.project.plugin.model.PluginExecutionStats;
import org.project.plugin.repository.PluginExecutionMapper;
import org.project.plugin.exception.PluginApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/** 执行历史、统计和内部幂等记录服务。 */
@Service
@RequiredArgsConstructor
public class PluginExecutionService {
    private final PluginExecutionMapper mapper;

    public void record(List<ExecutionRecordRequest> requests) {
        for (ExecutionRecordRequest request : requests) {
            mapper.insertIgnore(request);
        }
    }

    /** 本地 Runtime 上报时以网关注入身份覆盖客户端自报身份，防止替其他用户写日志。 */
    public void recordLocal(
            ExecutionRecordRequest request,
            String authenticatedUserId,
            String authenticatedClientId,
            String spaceId
    ) {
        if (!"LOCAL".equals(request.triggerSource())
                || mapper.countAccessibleLocalInstallation(
                        request.installationId(),
                        request.pluginId(),
                        request.versionId(),
                        authenticatedUserId,
                        blankToNull(spaceId)
                ) != 1) {
            throw new PluginApiException(
                    "PLG-LOCAL-EXECUTION-FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "本地插件执行记录与当前安装不匹配"
            );
        }
        ExecutionRecordRequest trusted = new ExecutionRecordRequest(
                request.executionId(),
                request.pluginId(),
                request.versionId(),
                request.installationId(),
                authenticatedUserId,
                blankToNull(spaceId),
                authenticatedClientId,
                request.triggerEvent(),
                "LOCAL",
                request.status(),
                request.startedAt(),
                request.endedAt(),
                request.outputSummary(),
                request.errorCode(),
                request.correlationId(),
                request.causationId()
        );
        mapper.insertIgnore(trusted);
    }

    public List<PluginExecutionRow> list(
            String pluginId, String userId, String status, int page, int size
    ) {
        return mapper.listOwned(
                pluginId, userId, status == null ? "" : status,
                (page - 1) * size, size
        );
    }

    public PluginExecutionStats stats(String pluginId, String userId) {
        return mapper.statsOwned(pluginId, userId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
