package org.project.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "admin|editor|viewer", message = "角色无效")
    private String role;
}