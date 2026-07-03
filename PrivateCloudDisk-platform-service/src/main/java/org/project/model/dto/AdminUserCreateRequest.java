package org.project.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.project.validation.ValidPassword;

/**
 * 创建管理员请求 DTO。
 */
@Data
public class AdminUserCreateRequest {

    /** 管理员账号，4-16 位字母/数字/下划线 */
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,16}$", message = "账号格式不正确")
    private String account;

    /** 管理员名称，2-16 位 */
    @NotBlank(message = "名称不能为空")
    @Size(min = 2, max = 16, message = "名称长度必须为2-16个字符")
    private String name;

    /** 邮箱，最大 254 字符 */
    @NotBlank(message = "邮箱不能为空")
    @Size(max = 254, message = "邮箱长度不能超过254个字符")
    @Email(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
            message = "邮箱格式不正确")
    private String email;

    /** 密码，支持原始密码和 PBKDF2-SHA256 预哈希密码两种格式 */
    @NotBlank(message = "密码不能为空")
    @ValidPassword
    private String password;

    /** 角色：super_admin / admin */
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(super_admin|admin)$", message = "角色必须是 super_admin 或 admin")
    private String role;
}