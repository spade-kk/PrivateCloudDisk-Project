package org.project.workflow.client;

import org.project.workflow.config.WorkflowProperties;
import org.project.workflow.exception.WorkflowApiException;
import org.project.workflow.model.WorkflowModels.CreateScheduleRequest;
import org.project.workflow.model.WorkflowModels.WorkflowRow;
import org.project.workflow.model.WorkflowModels.WorkflowVersionRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/** Workflow 控制面代理 Scheduler，浏览器不会接触 Scheduler 内部 API。 */
@Component
public class SchedulerClient {
    private final RestClient client;

    public SchedulerClient(RestClient.Builder builder, WorkflowProperties properties) {
        this.client = builder.clone().baseUrl(properties.schedulerUrl()).build();
    }

    public Object create(
            WorkflowRow workflow,
            WorkflowVersionRow version,
            String userId,
            String spaceId,
            CreateScheduleRequest request
    ) {
        try {
            return client.post().uri("/internal/v1/schedules")
                    .body(Map.of(
                            "workflow_id", workflow.workflowId(),
                            "version_id", version.versionId(),
                            "user_id", userId,
                            "space_id", spaceId == null ? "" : spaceId,
                            "cron", request.cron(),
                            "timezone", request.timezone(),
                            "misfire_policy", request.misfirePolicy(),
                            "inputs", request.inputs() == null ? Map.of() : request.inputs()
                    ))
                    .retrieve().body(Object.class);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    public Object list(String workflowId) {
        try {
            Object body = client.get()
                    .uri("/internal/v1/schedules/workflows/{workflowId}", workflowId)
                    .retrieve().body(Object.class);
            return body == null ? List.of() : body;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    public Object setEnabled(String scheduleId, String userId, boolean enabled) {
        try {
            return client.patch()
                    .uri(builder -> builder.path("/internal/v1/schedules/{scheduleId}")
                            .queryParam("userId", userId)
                            .queryParam("enabled", enabled)
                            .build(scheduleId))
                    .retrieve().body(Object.class);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private static WorkflowApiException unavailable() {
        return new WorkflowApiException(
                "WF-SCHEDULER-UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                "调度服务暂时不可用，请稍后重试"
        );
    }
}
