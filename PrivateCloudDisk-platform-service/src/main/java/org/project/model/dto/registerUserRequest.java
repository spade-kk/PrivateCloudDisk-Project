package org.project.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.project.validation.ValidPassword;

/**
 * 用户注册请求 DTO。
 * <p>
 * 密码字段支持两种格式：
 * <ol>
 *   <li><b>原始密码</b>（8-128 位，含字母+数字+特殊字符）</li>
 *   <li><b>PBKDF2-SHA256 预哈希密码</b>（64 位十六进制字符串）— Web 前端安全传输</li>
 * </ol>
 */
@Data
public class registerUserRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern( regexp = "^1[3-9]\\d{9}$",
              message = "手机号格式不正确")
    String phone_number;

    @NotBlank(message = "密码不能为空")
    @ValidPassword
    String password;

    @NotBlank(message = "验证码不能为空")
    @Pattern( regexp = "^[a-zA-Z0-9]{6,16}$",
              message = "验证码必须是6-16位数字或字母")
    String code;

    @NotBlank(message = "用户名不能为空")
    @Pattern( regexp = "^[a-zA-Z0-9]{2,10}$",
              message = "用户名必须是2-10位数字或字母")
    String name;

    @Size(max = 2048, message = "人机验证码长度不正确")
    String captcha_token;

    @Size(max = 32, message = "人机验证码动作长度不正确")
    String captcha_action;
}
