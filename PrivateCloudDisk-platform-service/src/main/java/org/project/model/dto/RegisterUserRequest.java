package org.project.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.project.validation.ValidPassword;

/**
 * 注册请求 DTO。
 *
 * <p>设计说明：
 * <ul>
 *   <li>邮箱和手机号至少提供一个作为注册账号</li>
 *   <li>需要验证码（从 /business/verification/send 获取）</li>
 *   <li><b>不需要人机验证码</b>：因为获取验证码时已通过人机验证，
 *       验证码本身就是第二因子，企业级做法不在此处再次人机验证</li>
 * </ul>
 */
@Data
public class RegisterUserRequest {

    /**
     * 手机号（与邮箱二选一作为注册账号）
     */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone_number;

    /**
     * 邮箱（与手机号二选一作为注册账号）
     */
    @Size(max = 254, message = "邮箱长度不能超过254个字符")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
             message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @ValidPassword
    String password;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^[0-9]{6}$", message = "验证码必须是6位数字")
    String code;

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{2,10}$", message = "用户名必须是2-10位数字或字母")
    String name;


    /**
     * 自定义校验：邮箱和手机号至少提供一个
     */
    @AssertTrue(message = "邮箱和手机号至少提供一个")
    public boolean isValidTarget() {
        boolean hasPhone = phone_number != null && !phone_number.isBlank();
        boolean hasEmail = email != null && !email.isBlank();
        return hasPhone || hasEmail;
    }
}