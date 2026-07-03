package org.project.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.project.validation.ValidPassword;

/**
 * 管理员登录请求 DTO。
 */
@Data
public class AdminLoginRequest {

    /** 管理员账号，4-16 位字母/数字/下划线 */
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,16}$", message = "账号格式不正确")
    private String account;

    /** 密码，支持原始密码和 PBKDF2-SHA256 预哈希密码两种格式 */
    @NotBlank(message = "密码不能为空")
    @ValidPassword
    private String password;

    /** 人机验证 token */
    @NotBlank(message = "人机验证码不能为空")
    @Size(max = 2048, message = "人机验证码长度不正确")
    @Pattern(regexp = "^[A-Za-z0-9+/=._\\-:]+$",
            message = "人机验证码格式不正确")
    private String captchaToken;

    /** 人机验证动作标识 */
    @Size(max = 32, message = "人机验证码动作长度不正确")
    @Pattern(regexp = "^[a-z_]+$", message = "人机验证码动作格式不正确")
    private String captchaAction;
}