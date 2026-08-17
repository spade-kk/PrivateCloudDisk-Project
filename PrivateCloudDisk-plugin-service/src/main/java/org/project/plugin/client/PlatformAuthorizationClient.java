package org.project.plugin.client;

import lombok.RequiredArgsConstructor;
import org.project.plugin.config.PluginProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 执行前实时调用 Platform 权限服务，禁止复制空间角色判断。 */
@Component
@RequiredArgsConstructor
public class PlatformAuthorizationClient {
    private final RestClient.Builder restClientBuilder;
    private final PluginProperties properties;

    public boolean canRunPreprocess(String userId, String spaceId, String fileId) {
        return authorize(userId, spaceId, fileId, "FILE_CONTENT_PREPROCESS");
    }

    /** 激活后入口只能读取最终内容或提交元数据意图，权限强度低于预处理写入。 */
    public boolean canRunAvailable(String userId, String spaceId, String fileId) {
        return authorize(userId, spaceId, fileId, "FILE_AVAILABLE_METADATA");
    }

    /** 空间插件安装/解绑前实时校验管理权限。 */
    public boolean canManagePlugins(String userId, String spaceId) {
        return authorize(userId, spaceId, "", "PLUGIN_MANAGE");
    }

    /** 工作流调用插件能力前重新校验当前用户仍能访问该空间。 */
    public boolean canExecuteWorkflowCapability(String userId, String spaceId) {
        return authorize(userId, spaceId, "", "WORKFLOW_EXECUTE");
    }

    private boolean authorize(
            String userId,
            String spaceId,
            String fileId,
            String operation
    ) {
        Map<String, Object> response = restClientBuilder.clone()
                .baseUrl(properties.platformUrl())
                .build()
                .post()
                .uri("/business/internal/automation/authorize")
                .body(Map.of(
                        "user_id", userId,
                        "space_id", spaceId == null ? "" : spaceId,
                        "file_id", fileId,
                        "operation", operation
                ))
                .retrieve()
                .body(Map.class);
        return response != null && Boolean.TRUE.equals(response.get("allowed"));
    }
}
