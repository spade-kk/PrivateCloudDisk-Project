package org.project.plugin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.project.plugin.client.PlatformAuthorizationClient;
import org.project.plugin.model.EntrypointCandidateRow;
import org.project.plugin.model.EntrypointMatchRequest;
import org.project.plugin.model.EntrypointMatchResponse;
import org.project.plugin.repository.PluginEntrypointMapper;
import org.springframework.stereotype.Service;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 云插件入口匹配、条件判断与权限求交。 */
@Service
@RequiredArgsConstructor
public class EntrypointMatchService {
    private static final String READY_EVENT = "pcd.file.content.ready.v1";
    private static final String PREPROCESS_WRITE = "file.content.write_pre_activation";

    private final PluginEntrypointMapper mapper;
    private final PlatformAuthorizationClient authorizationClient;
    private final ObjectMapper objectMapper;

    public List<EntrypointMatchResponse> match(EntrypointMatchRequest request) {
        if (!READY_EVENT.equals(request.eventType())
                && !"pcd.file.available.v1".equals(request.eventType())) {
            throw new IllegalArgumentException("不支持的插件触发事件");
        }
        String fileId = stringValue(request.file().get("file_id"));
        List<EntrypointCandidateRow> candidates = mapper.findCandidates(
                request.eventType(), request.actorUserId(), request.spaceId()
        );
        if (candidates.isEmpty()) {
            return List.of();
        }
        boolean authorized = READY_EVENT.equals(request.eventType())
                ? authorizationClient.canRunPreprocess(
                        request.actorUserId(), request.spaceId(), fileId)
                : authorizationClient.canRunAvailable(
                        request.actorUserId(), request.spaceId(), fileId);
        if (!authorized) {
            return List.of();
        }

        List<EntrypointMatchResponse> result = new ArrayList<>();
        for (EntrypointCandidateRow row : candidates) {
            Map<String, Object> conditions = readMap(row.conditionJson());
            if (!matchesConditions(conditions, request.file())) {
                continue;
            }
            List<String> declared = readList(row.permissionJson());
            Set<String> granted = new HashSet<>(readList(row.grantedPermissionsJson()));
            List<String> effective = declared.stream().filter(granted::contains).toList();
            if (READY_EVENT.equals(request.eventType())
                    && !effective.contains(PREPROCESS_WRITE)) {
                continue;
            }
            if (!READY_EVENT.equals(request.eventType())) {
                // 内容冻结：即使安装授权包含预处理写权限，available 入口也必须剥离。
                effective = effective.stream()
                        .filter(permission -> !PREPROCESS_WRITE.equals(permission))
                        .toList();
            }
            result.add(new EntrypointMatchResponse(
                    row.installationId(),
                    row.pluginId(),
                    row.versionId(),
                    row.runtime(),
                    row.modulePath(),
                    row.functionName(),
                    row.priority(),
                    effective,
                    readMap(row.configJson())
            ));
        }
        return result;
    }

    private boolean matchesConditions(
            Map<String, Object> conditions,
            Map<String, Object> file
    ) {
        List<String> mimeTypes = objectMapper.convertValue(
                conditions.getOrDefault("mime_types", List.of()),
                new TypeReference<List<String>>() {}
        );
        String mimeType = stringValue(file.get("mime_type"));
        if (!mimeTypes.isEmpty() && !mimeTypes.contains(mimeType)) {
            return false;
        }
        long size = longValue(file.get("size"));
        if (conditions.containsKey("min_size") && size < longValue(conditions.get("min_size"))) {
            return false;
        }
        if (conditions.containsKey("max_size") && size > longValue(conditions.get("max_size"))) {
            return false;
        }
        String nameGlob = stringValue(conditions.get("name_glob"));
        if (!nameGlob.isBlank()) {
            // 使用 JDK Glob 而非用户正则，避免灾难性回溯造成 ReDoS。
            if (!FileSystems.getDefault().getPathMatcher("glob:" + nameGlob)
                    .matches(Path.of(stringValue(file.get("name"))).getFileName())) {
                return false;
            }
        }
        String directoryId = stringValue(conditions.get("directory_id"));
        return directoryId.isBlank()
                || directoryId.equals(stringValue(file.get("node_id")));
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(
                    json == null || json.isBlank() ? "{}" : json,
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception exception) {
            throw new IllegalStateException("插件配置 JSON 非法", exception);
        }
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(
                    json == null || json.isBlank() ? "[]" : json,
                    new TypeReference<List<String>>() {}
            );
        } catch (Exception exception) {
            throw new IllegalStateException("插件权限 JSON 非法", exception);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(stringValue(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
