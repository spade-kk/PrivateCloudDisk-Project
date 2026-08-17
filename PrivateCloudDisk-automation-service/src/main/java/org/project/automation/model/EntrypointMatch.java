package org.project.automation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Plugin Service 返回的已授权、已启用、按确定顺序排序的入口快照。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EntrypointMatch(
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
