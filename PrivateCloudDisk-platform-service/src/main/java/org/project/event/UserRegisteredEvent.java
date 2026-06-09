package org.project.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户注册事件
 * <p>
 * 用户成功注册后发布此事件，由多个消费者异步处理：
 * - 欢迎邮件消费者：发送欢迎邮件
 * - 欢迎短信消费者：发送欢迎短信
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件唯一ID，用于幂等性检查
     * 格式示例：user-registered:10001
     */
    private String eventId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户名（显示名）
     */
    private String userName;

    /**
     * 用户邮箱（可能为空）
     */
    private String email;

    /**
     * 用户手机号（注册时填写的）
     */
    private String phone;

    /**
     * 事件发布时间
     */
    private LocalDateTime registeredAt;
}
