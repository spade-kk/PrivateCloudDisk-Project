package org.project.plugin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** 用户或空间安装请求；授权集合必须是版本声明权限的子集。 */
public record PluginInstallRequest(
        @NotBlank
        @Pattern(regexp = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-[0-9A-Za-z.-]+)?$")
        String version,
        @NotEmpty @Size(max = 64)
        @JsonProperty("granted_permissions")
        List<@NotBlank String> grantedPermissions,
        Map<String, Object> config,
        @JsonProperty("auto_update_policy")
        @Pattern(regexp = "^(PINNED|PATCH|MINOR|MANUAL)$")
        String autoUpdatePolicy
) {
}
