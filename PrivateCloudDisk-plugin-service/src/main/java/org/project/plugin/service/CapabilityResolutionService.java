package org.project.plugin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.project.plugin.client.PlatformAuthorizationClient;
import org.project.plugin.exception.PluginApiException;
import org.project.plugin.model.CapabilityResolutionRow;
import org.project.plugin.model.CapabilityResolveRequest;
import org.project.plugin.repository.PluginManagementMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 解析工作流可调用能力，并计算“能力声明 ∩ 安装授权”的有效权限。 */
@Service
@RequiredArgsConstructor
public class CapabilityResolutionService {
    private static final Pattern KEY = Pattern.compile(
            "^plugin:([0-9a-fA-F-]{36}):([a-z][a-z0-9_.-]{0,127})@([1-9][0-9]*)$"
    );
    private final PluginManagementMapper mapper;
    private final PlatformAuthorizationClient authorizationClient;
    private final ObjectMapper objectMapper;

    public Map<String, Object> resolve(CapabilityResolveRequest request) {
        Matcher matcher = KEY.matcher(request.capabilityKey());
        if (!matcher.matches()) {
            throw invalid("插件能力标识格式无效");
        }
        requireUuid(request.userId(), "用户标识无效");
        String spaceId = blank(request.spaceId());
        if (spaceId != null) {
            requireUuid(spaceId, "空间标识无效");
        }
        if (!authorizationClient.canExecuteWorkflowCapability(request.userId(), spaceId)) {
            throw new PluginApiException(
                    "PLG-CAPABILITY-FORBIDDEN", HttpStatus.FORBIDDEN,
                    "当前用户不能在该空间执行插件能力"
            );
        }
        CapabilityResolutionRow row = mapper.resolveCapability(
                matcher.group(1), matcher.group(2), Integer.parseInt(matcher.group(3)),
                request.userId(), spaceId
        );
        if (row == null) {
            throw new PluginApiException(
                    "PLG-CAPABILITY-NOT-INSTALLED", HttpStatus.NOT_FOUND,
                    "该能力未安装、未启用或版本不兼容"
            );
        }
        List<String> required = readList(row.capabilityPermissionsJson());
        Set<String> granted = new HashSet<>(readList(row.grantedPermissionsJson()));
        if (!granted.containsAll(required)) {
            throw new PluginApiException(
                    "PLG-CAPABILITY-PERMISSION", HttpStatus.FORBIDDEN,
                    "当前安装实例没有授予该能力所需权限"
            );
        }
        return Map.of(
                "installation_id", row.installationId(),
                "plugin_id", row.pluginId(),
                "version_id", row.versionId(),
                "runtime", row.runtime(),
                "module_path", row.modulePath(),
                "function_name", row.functionName(),
                "permissions", required,
                "config", readMap(row.configJson())
        );
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void requireUuid(String value, String message) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw invalid(message);
        }
    }

    private static PluginApiException invalid(String message) {
        return new PluginApiException("PLG-REQUEST-INVALID", HttpStatus.BAD_REQUEST, message);
    }
}
