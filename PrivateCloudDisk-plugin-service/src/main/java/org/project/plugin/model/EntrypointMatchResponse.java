package org.project.plugin.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** 已完成安装、版本、条件和权限求交的入口。 */
public record EntrypointMatchResponse(
        @JsonProperty("installation_id") String installationId,
        @JsonProperty("plugin_id") String pluginId,
        @JsonProperty("version_id") String versionId,
        String runtime,
        @JsonProperty("module_path") String modulePath,
        @JsonProperty("function_name") String functionName,
        int priority,
        List<String> permissions,
        Map<String, Object> config
) {
}
