package org.project.plugin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/** 插件开发阶段测试请求；测试入口必须在 Runtime 静态校验报告中声明为 @test。 */
public record PluginTestRequest(
        @NotBlank @JsonProperty("test_entrypoint") String testEntrypoint,
        @JsonProperty("script_entry") String scriptEntry,
        @Size(max = 100) Map<String, Object> parameters
) {
}
