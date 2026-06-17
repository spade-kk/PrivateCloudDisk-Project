package org.project.im.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息类型枚举
 * <p>
 * 定义 IM 系统中所有支持的消息类型，包含文本、图片、文件、
 * 语音、视频、系统通知等。通信模块通过此枚举区分消息处理逻辑。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum MessageType {

    /** 普通文本消息 */
    TEXT(1, "文本消息"),

    /** 图片消息（含缩略图 URL） */
    IMAGE(2, "图片消息"),

    /** 文件消息（含文件名、大小、下载链接） */
    FILE(3, "文件消息"),

    /** 语音消息（含音频 URL、时长） */
    VOICE(4, "语音消息"),

    /** 视频消息（含视频 URL、封面图、时长） */
    VIDEO(5, "视频消息"),

    /** 位置消息（经纬度、地址描述） */
    LOCATION(6, "位置消息"),

    /** 系统通知（如：xxx 加入了群聊） */
    SYSTEM_NOTICE(7, "系统通知"),

    /** 自定义消息（业务扩展，JSON 格式） */
    CUSTOM(8, "自定义消息"),

    /** 引用/回复消息 */
    REPLY(9, "引用消息"),

    /** 已读回执 */
    READ_RECEIPT(10, "已读回执"),

    /** 正在输入状态 */
    TYPING(11, "正在输入"),

    /** 心跳包 */
    HEARTBEAT(99, "心跳");

    /** 消息类型编码 */
    private final int code;

    /** 消息类型描述 */
    private final String description;

    /**
     * 根据编码获取消息类型枚举
     *
     * @param code 消息类型编码
     * @return 对应的枚举值，未匹配则返回 TEXT
     */
    public static MessageType fromCode(int code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return TEXT;
    }
}