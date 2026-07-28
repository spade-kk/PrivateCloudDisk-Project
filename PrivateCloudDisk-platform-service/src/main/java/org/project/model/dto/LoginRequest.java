package org.project.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.project.validation.AtLeastOneNotNull;
import org.project.validation.ValidPassword;

/**
 * 用户登录请求 DTO。
 * <p>
 * 密码字段支持两种格式：
 * <ol>
 *   <li><b>原始密码</b>（8-128 位，含字母+数字+特殊字符）— 用于非 Web 客户端或旧版前端</li>
 *   <li><b>PBKDF2-SHA256 预哈希密码</b>（64 位十六进制字符串）— 用于 Web 前端安全传输</li>
 * </ol>
 * 前端使用 PBKDF2-SHA256（60 万次迭代）预哈希后传输，后端接收后进行 BCrypt 二次哈希验证。
 * 通过 {@link ValidPassword} 自定义校验器同时支持两种格式。
 */
@AtLeastOneNotNull(fieldNames = {"phone_number", "account", "email"}, message = "账号、手机号和邮箱至少一个不能为空")
@Data
public class LoginRequest {

    @Pattern(regexp = "^[a-zA-Z0-9_]{4,16}$",
            message = "账号格式不正确")
    private String account;

    @Pattern(regexp = "^1[3-9]\\d{9}$",
            message = "手机号格式不正确")
    private String phone_number;

    /** 需求九：密码登录支持标准邮箱地址，与账号、手机号共用同一输入框。 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱长度不能超过254个字符")
    private String email;

    @NotBlank(message = "密码不能为空")
    @ValidPassword
    private String password;

    @Size(max = 2048, message = "人机验证码长度不正确")
    private String captcha_token;

    @Size(max = 32, message = "人机验证码动作长度不正确")
    private String captcha_action;
}
