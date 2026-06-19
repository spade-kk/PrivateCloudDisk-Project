package org.project.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 验证码发送请求 DTO（首次发送和重新发送共用）。
 *
 * <p>首次发送（/send）：需提供 captchaToken（人机验证码）
 * <p>重新发送（/resend）：无需 captchaToken，需在 Header 携带 X-Resend-Token
 *
 * <p>设计原则：
 * <ul>
 *   <li>邮箱和手机号至少提供一个</li>
 *   <li>captchaToken 仅首次发送时必填，重新发送时可选</li>
 *   <li>purpose 区分注册（REGISTER）、绑定（BIND）、重置密码（RESET）</li>
 * </ul>
 */
@Data
public class RegisterVerificationSendRequest {

    /**
     * 接收邮箱（与手机号二选一）
     */
    @Size(max = 254, message = "邮箱长度不能超过254个字符")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
            message = "邮箱格式不正确")
    private String email;

    /**
     * 接收手机号（与邮箱二选一）
     */
    @Pattern(regexp = "^1[3-9]\\d{9}$",
            message = "手机号格式不正确")
    private String phone;

    /**
     * Cloudflare Turnstile 人机验证 token（首次发送时必填，重新发送时可选）
     */
    @Size(max = 2048, message = "人机验证码 token 长度不正确")
    private String captchaToken;

    /**
     * Turnstile 人机验证动作标识（可选）
     */
    @Size(max = 32, message = "人机验证码动作长度不正确")
    private String captchaAction;

    /**
     * 自定义校验：邮箱和手机号至少提供一个
     */
    @AssertTrue(message = "邮箱和手机号至少提供一个")
    public boolean isValidTarget() {
        return (email != null && !email.isBlank()) || (phone != null && !phone.isBlank());
    }
}