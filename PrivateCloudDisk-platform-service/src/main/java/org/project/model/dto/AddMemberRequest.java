package org.project.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 添加空间成员请求 DTO。
 */
@Data
public class AddMemberRequest {

    /** 用户 ID，UUID 格式 */
    @NotBlank(message = "用户ID不能为空")
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "用户ID必须是有效的UUID格式")
    private String userId;

    /** 角色：admin / editor / viewer */
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(admin|editor|viewer)$", message = "角色无效")
    private String role = "viewer";
}