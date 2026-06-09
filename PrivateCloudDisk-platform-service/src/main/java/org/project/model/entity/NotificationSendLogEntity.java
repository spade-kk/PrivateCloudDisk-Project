package org.project.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通知发送日志实体
 * <p>用于记录邮件、短信等通知的发送状态，核心目的是实现消息消费者的幂等性：
 * <ol>
 *   <li>当MQ消息重复投递时，查询此表判断是否已成功发送，避免重复发送</li>
 *   <li>记录发送状态（PENDING/SUCCESS/FAILED），便于问题排查和重试</li>
 *   <li>存储错误信息，便于死信消息的人工分析</li>
 * </ol>
 *
 * <p>唯一索引：(event_id, channel, receiver)
 * <br>即同一个事件ID、同一通道、同一接收者，最多有一条活跃记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSendLogEntity {

    /**
     * 主键自增ID
     */
    private Long id;

    /**
     * 事件唯一ID（由发布方生成）
     * 格式示例：user-registered:10001，或 email-verify:user@example.com:20260609120000
     */
    private String eventId;

    /**
     * 通知通道：EMAIL（邮件）、SMS（短信）、IN_APP（站内信，预留）
     */
    private String channel;

    /**
     * 接收者：邮箱地址或手机号
     */
    private String receiver;

    /**
     * 关联用户ID（可为空）
     */
    private String userId;

    /**
     * 状态：PENDING（处理中）、SUCCESS（成功）、FAILED（失败）
     */
    private String status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 错误信息（失败时记录）
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    // ============ 状态常量 ============

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    // ============ 通道常量 ============

    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_SMS = "SMS";
}
