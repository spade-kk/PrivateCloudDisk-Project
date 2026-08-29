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
    SYNC_OFFLINE_MESSAGES(1001, "同步离线消息"),

    // ==================== WebRTC 视频通话信令 ====================
    // 通话控制
    /** 发起视频通话邀请 */
    CALL_INVITE(2001, "发起视频通话邀请"),
    /** 接受通话邀请 */
    CALL_ACCEPT(2002, "接受通话邀请"),
    /** 拒绝通话邀请 */
    CALL_REJECT(2003, "拒绝通话邀请"),
    /** 取消通话邀请 */
    CALL_CANCEL(2004, "取消通话邀请"),
    /** 挂断通话 */
    CALL_HANGUP(2005, "挂断通话"),
    /** 通话忙线 */
    CALL_BUSY(2006, "通话忙线"),
    /** 通话超时 */
    CALL_TIMEOUT(2007, "通话超时"),

    // WebRTC SDP 信令
    /** 发送 Offer SDP */
    SIGNALING_OFFER(2101, "发送 Offer SDP"),
    /** 发送 Answer SDP */
    SIGNALING_ANSWER(2102, "发送 Answer SDP"),
    /** 发送 ICE Candidate */
    SIGNALING_ICE_CANDIDATE(2103, "发送 ICE Candidate"),
    /** SDP 重协商 */
    SIGNALING_RENEGOTIATE(2104, "SDP 重协商"),

    // 通话质量控制
    /** 网络质量报告 */
    CALL_QUALITY_REPORT(2201, "网络质量报告"),
    /** 编码参数调整指令 */
    CALL_ENCODER_ADJUST(2202, "编码参数调整指令"),
    /** 分辨率切换请求 */
    CALL_RESOLUTION_SWITCH(2203, "分辨率切换请求"),
    /** 码率调整请求 */
    CALL_BITRATE_ADJUST(2204, "码率调整请求"),
    /** 帧率调整请求 */
    CALL_FRAMERATE_ADJUST(2205, "帧率调整请求"),

    // 通话扩展功能
    /** 屏幕共享开始 */
    CALL_SCREEN_SHARE_START(2301, "屏幕共享开始"),
    /** 屏幕共享结束 */
    CALL_SCREEN_SHARE_STOP(2302, "屏幕共享结束"),
    /** 静音/取消静音 */
    CALL_MUTE_TOGGLE(2303, "静音/取消静音"),
    /** 摄像头开关 */
    CALL_CAMERA_TOGGLE(2304, "摄像头开关"),
    /** 切换到语音通话 */
    CALL_SWITCH_TO_VOICE(2305, "切换到语音通话"),
    /** 切换到视频通话 */
    CALL_SWITCH_TO_VIDEO(2306, "切换到视频通话"),

    // 群组通话
    /** 创建群组通话房间 */
    CALL_ROOM_CREATE(2401, "创建群组通话房间"),
    /** 加入群组通话 */
    CALL_ROOM_JOIN(2402, "加入群组通话"),
    /** 离开群组通话 */
    CALL_ROOM_LEAVE(2403, "离开群组通话"),
    /** 邀请用户加入通话 */
    CALL_ROOM_INVITE(2404, "邀请用户加入通话"),
    /** 获取通话房间成员列表 */
    CALL_ROOM_MEMBERS(2405, "获取通话房间成员列表"),
    /** 获取通话房间信息 */
    CALL_ROOM_INFO(2406, "获取通话房间信息"),

    // 通话记录
    /** 获取通话历史 */
    CALL_HISTORY(2501, "获取通话历史"),
    /** 通话记录详情 */
    CALL_RECORD_DETAIL(2502, "通话记录详情"),

    // TURN/STUN 服务器配置
    /** 获取 ICE 服务器配置 */
    CALL_ICE_SERVERS(2601, "获取 ICE 服务器配置");

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
