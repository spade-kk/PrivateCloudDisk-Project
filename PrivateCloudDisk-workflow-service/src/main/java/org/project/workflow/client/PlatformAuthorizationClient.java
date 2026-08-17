package org.project.workflow.client;

import org.project.workflow.config.WorkflowProperties;
import org.project.workflow.exception.WorkflowApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

/** 工作流创建、发布和运行前实时复用 Platform 空间权限，不复制角色规则。 */
@Component
public class PlatformAuthorizationClient {
    private final RestClient client;

    public PlatformAuthorizationClient(RestClient.Builder builder, WorkflowProperties properties) {
        this.client = builder.clone().baseUrl(properties.platformUrl()).build();
    }

    public void requireManage(String userId, String spaceId) {
        require(userId, spaceId, "WORKFLOW_MANAGE",
                "当前用户没有管理该空间工作流的权限");
    }

    /** 每次异步执行前仅要求仍拥有空间读取/使用权限，不错误提升到管理权限。 */
    public void requireExecute(String userId, String spaceId) {
        require(userId, spaceId, "WORKFLOW_EXECUTE",
                "当前用户已失去该空间的工作流执行权限");
    }

    private void require(String userId, String spaceId, String operation, String deniedMessage) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("space_id", spaceId == null ? "" : spaceId);
        body.put("file_id", "");
        body.put("operation", operation);
        try {
            Map<?, ?> response = client.post()
                    .uri("/business/internal/automation/authorize")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !Boolean.TRUE.equals(response.get("allowed"))) {
                throw forbidden(deniedMessage);
            }
        } catch (WorkflowApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new WorkflowApiException(
                    "WF-AUTH-UNAVAILABLE",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "空间权限服务暂时不可用，请稍后重试"
            );
        }
    }

    private static WorkflowApiException forbidden(String message) {
        return new WorkflowApiException(
                "WF-FORBIDDEN", HttpStatus.FORBIDDEN, message
        );
    }
}
