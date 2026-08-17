package org.project.workflow.service;

import org.project.workflow.config.WorkflowProperties;
import org.project.workflow.model.WorkflowModels.CapabilityInvocation;
import org.project.workflow.model.WorkflowModels.CapabilityResult;
import org.project.workflow.model.WorkflowModels.CapabilityRow;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/** 云插件能力必须经过 Plugin Runtime 沙箱执行，Workflow 不读取插件包或运行用户代码。 */
@Component
public class PluginCapabilityClient {
    private final RestClient runtimeClient;
    private final RestClient pluginClient;

    public PluginCapabilityClient(RestClient.Builder builder, WorkflowProperties properties) {
        this.runtimeClient = builder.clone().baseUrl(properties.pluginRuntimeUrl()).build();
        this.pluginClient = builder.clone().baseUrl(properties.pluginServiceUrl()).build();
    }

    public CapabilityResult invoke(CapabilityInvocation invocation, CapabilityRow capability) {
        try {
            Map<?, ?> resolution = pluginClient.post()
                    .uri("/internal/v1/capabilities/resolve")
                    .body(Map.of(
                            "capability_key", capability.capabilityKey(),
                            "user_id", invocation.userId(),
                            "space_id", invocation.spaceId() == null ? "" : invocation.spaceId()
                    ))
                    .retrieve()
                    .body(Map.class);
            if (resolution == null) {
                return CapabilityResult.failure(
                        "WF-PLUGIN-CAPABILITY-UNAVAILABLE", "插件服务未返回能力解析结果"
                );
            }
            Map<String, Object> entrypoint = new java.util.LinkedHashMap<>();
            entrypoint.put("installation_id", resolution.get("installation_id"));
            entrypoint.put("plugin_id", resolution.get("plugin_id"));
            entrypoint.put("version_id", resolution.get("version_id"));
            entrypoint.put("runtime", resolution.get("runtime"));
            entrypoint.put("module_path", resolution.get("module_path"));
            entrypoint.put("function_name", resolution.get("function_name"));
            entrypoint.put("priority", 100);
            entrypoint.put("permissions", resolution.get("permissions"));
            entrypoint.put("config", resolution.get("config"));
            Map<?, ?> response = runtimeClient.post()
                    .uri("/internal/v1/executions/capability")
                    .body(Map.of(
                            "execution_id", invocation.executionId(),
                            "step_id", invocation.stepId(),
                            "user_id", invocation.userId(),
                            "space_id", invocation.spaceId() == null ? "" : invocation.spaceId(),
                            "input", invocation.input(),
                            "entrypoint", entrypoint
                    ))
                    .retrieve()
                    .body(Map.class);
            if (response != null && "success".equals(response.get("status"))) {
                Object output = response.get("output");
                return CapabilityResult.success(output instanceof Map<?, ?> map
                        ? map.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()), Map.Entry::getValue
                )) : Map.of());
            }
            Object errorCode = response == null ? null : response.get("error_code");
            Object errorSummary = response == null ? null : response.get("error_summary");
            return CapabilityResult.failure(
                    errorCode == null ? "WF-PLUGIN-RUNTIME" : String.valueOf(errorCode),
                    response == null ? "插件运行时未返回结果" :
                            (errorSummary == null ? "插件能力执行失败" : String.valueOf(errorSummary))
            );
        } catch (RestClientException exception) {
            return CapabilityResult.failure("WF-PLUGIN-RUNTIME-UNAVAILABLE", "插件运行时暂时不可用");
        }
    }
}
