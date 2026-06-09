package org.project.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 邮箱验证码事件
 * <p>
 * 当用户需要邮箱验证时发布此事件：
 * - 注册后的邮箱验证
 * - 绑定新邮箱的验证
 * - 找回密码的邮箱验证
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件唯一ID，用于幂等性检查
     * 格式示例：email-verify:user@example.com:20260609120000
     */
    private String eventId;

    /**
     * 收件人邮箱
     */
    private String email;

    /**
     * 验证码
     */
    private String verificationCode;

    /**
     * 验证码有效期（秒）
     */
    private Integer expireSeconds;

    /**
     * 验证用途：REGISTER（注册）、BIND（绑定）、RESET（重置密码）
     */
    private String purpose;

    /**
     * 相关用户ID（如果已有）
     */
    private String userId;

    /**
     * 事件发布时间
     */
    private LocalDateTime createdAt;
}
