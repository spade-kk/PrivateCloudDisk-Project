package org.project.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 插件/工作流执行前的内部实时授权请求。
 *
 * <p>需求：异步任务不能沿用触发时的权限快照，必须在执行前重新校验成员关系、
 * 空间状态、操作权限和文件归属。</p>
 */
public record InternalAutomationAuthorizeRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        @JsonProperty("user_id")
        String userId,
        @JsonProperty("space_id")
        String spaceId,
        @Pattern(regexp = "^$|^[0-9a-fA-F-]{36}$")
        @JsonProperty("file_id")
        String fileId,
        @NotBlank
        String operation
) {
}
