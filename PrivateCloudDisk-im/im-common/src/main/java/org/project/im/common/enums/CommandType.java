package org.project.im.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 命令类型枚举
 * <p>
 * WebSocket 通信协议中的命令字，用于客户端与服务端之间的消息路由。
 * 客户端发送请求时携带命令字，服务端根据命令字分发到对应的 Handler 处理。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum CommandType {

    // ==================== 连接管理 ====================
    /** 登录认证 */
    LOGIN(101, "登录认证"),
    /** 登出 */
    LOGOUT(102, "登出"),
    /** 心跳 */
    HEARTBEAT(103, "心跳"),

    // ==================== 消息收发 ====================
    /** 发送消息 */
    SEND_MESSAGE(201, "发送消息"),
    /** 消息确认（ACK） */
    MESSAGE_ACK(202, "消息确认"),
    /** 撤回消息 */
    RECALL_MESSAGE(203, "撤回消息"),
    /** 已读消息 */
    READ_MESSAGE(204, "已读消息"),
    /** 正在输入 */
    TYPING(205, "正在输入"),

    // ==================== 会话管理 ====================
    /** 创建会话 */
    CREATE_CONVERSATION(301, "创建会话"),
    /** 获取会话列表 */
    GET_CONVERSATIONS(302, "获取会话列表"),
    /** 删除会话 */
    DELETE_CONVERSATION(303, "删除会话"),
    /** 置顶会话 */
    TOP_CONVERSATION(304, "置顶会话"),
    /** 获取历史消息 */
    GET_HISTORY(305, "获取历史消息"),

    // ==================== 群组管理 ====================
    /** 创建群组 */
    CREATE_GROUP(401, "创建群组"),
    /** 加入群组 */
    JOIN_GROUP(402, "加入群组"),
    /** 退出群组 */
    LEAVE_GROUP(403, "退出群组"),
    /** 踢出成员 */
    KICK_MEMBER(404, "踢出成员"),
    /** 禁言成员 */
    MUTE_MEMBER(405, "禁言成员"),
    /** 解散群组 */
    DISSOLVE_GROUP(406, "解散群组"),
    /** 获取群成员列表 */
    GET_GROUP_MEMBERS(407, "获取群成员列表"),

    // ==================== 系统通知 ====================
    /** 系统通知 */
    SYSTEM_NOTIFY(901, "系统通知"),
    /** 错误通知 */
    ERROR_NOTIFY(902, "错误通知"),

    // ==================== 离线同步 ====================
    /** 同步离线消息 */
    SYNC_OFFLINE_MESSAGES(1001, "同步离线消息");

    private final int code;
    private final String description;

    public static CommandType fromCode(int code) {
        for (CommandType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}