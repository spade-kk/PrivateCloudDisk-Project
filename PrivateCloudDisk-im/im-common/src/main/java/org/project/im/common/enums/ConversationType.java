package org.project.im.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会话类型枚举
 * <p>
 * 支持单聊和群聊两种会话模式，未来可扩展为频道、机器人等。
 * 会话是消息的容器，所有消息都归属于某个会话。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum ConversationType {

    /** 单聊（一对一私聊） */
    PRIVATE(1, "单聊"),

    /** 群聊（多人群组） */
    GROUP(2, "群聊"),

    /** 系统会话（系统通知、公告等） */
    SYSTEM(3, "系统会话");

    private final int code;
    private final String description;

    public static ConversationType fromCode(int code) {
        for (ConversationType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return PRIVATE;
    }
}