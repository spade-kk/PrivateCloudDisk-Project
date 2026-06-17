package org.project.im.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息状态枚举
 * <p>
 * 追踪消息从发送到最终消费的完整生命周期。
 * 支持离线消息场景：消息先持久化，用户上线后通过同步协议拉取。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum MessageStatus {

    /** 发送中（客户端已提交，服务端尚未确认） */
    SENDING(0, "发送中"),

    /** 已发送（服务端已接收并持久化，但目标用户未在线） */
    SENT(1, "已发送"),

    /** 已送达（消息已推送到目标用户客户端） */
    DELIVERED(2, "已送达"),

    /** 已读（目标用户已查看消息） */
    READ(3, "已读"),

    /** 发送失败 */
    FAILED(4, "发送失败"),

    /** 已撤回（2 分钟内可撤回） */
    RECALLED(5, "已撤回"),

    /** 已删除（仅对删除者可见性变化） */
    DELETED(6, "已删除");

    private final int code;
    private final String description;

    public static MessageStatus fromCode(int code) {
        for (MessageStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return SENDING;
    }
}