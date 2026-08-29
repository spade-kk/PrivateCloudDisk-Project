package org.project.im.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应码枚举
 * <p>
 * 统一 IM 系统的响应码，覆盖成功、参数错误、认证失败、
 * 业务异常等场景。前后端统一使用此枚举进行错误处理。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum ResponseCode {

    // ==================== 成功 ====================
    SUCCESS(200, "操作成功"),

    // ==================== 客户端错误 4xx ====================
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证，请先登录"),
    FORBIDDEN(403, "无权限执行此操作"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),

    // ==================== 服务端错误 5xx ====================
    INTERNAL_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂时不可用"),

    // ==================== 业务错误 1xxx ====================
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_OFFLINE(1002, "用户不在线"),
    GROUP_NOT_FOUND(1003, "群组不存在"),
    NOT_GROUP_MEMBER(1004, "不是群组成员"),
    ALREADY_GROUP_MEMBER(1005, "已是群组成员"),
    GROUP_FULL(1006, "群组已满"),
    NO_PERMISSION(1007, "权限不足"),
    MESSAGE_TOO_LONG(1008, "消息内容过长"),
    RECALL_TIMEOUT(1009, "消息已超过撤回时限"),
    MUTED_IN_GROUP(1010, "您已被禁言"),
    CONVERSATION_NOT_FOUND(1011, "会话不存在"),
    DUPLICATE_MESSAGE(1012, "重复消息"),

    // ==================== 消息发送权限校验错误 11xx ====================
    RECEIVER_NOT_FOUND(1101, "接收方用户不存在"),
    RECEIVER_STATUS_ABNORMAL(1102, "接收方账号状态异常"),
    NOT_FRIEND(1103, "非好友关系，无法发送消息"),
    BLACKLISTED(1104, "您已被对方拉黑，无法发送消息"),
    GLOBAL_MUTED(1105, "您已被全局禁言"),
    SESSION_NOT_EXIST(1106, "会话不存在，无法发送消息"),
    MESSAGE_EMPTY(1107, "消息内容不能为空"),
    MESSAGE_TYPE_NOT_SUPPORTED(1108, "不支持的消息类型"),

    // ==================== 连接错误 2xxx ====================
    CONNECTION_LIMIT_EXCEEDED(2001, "超出最大连接数限制"),
    TOKEN_EXPIRED(2002, "Token 已过期"),
    TOKEN_INVALID(2003, "Token 无效"),
    KICKED_OUT(2004, "被踢下线"),
    PROTOCOL_ERROR(2005, "协议解析错误");

    private final int code;
    private final String message;

    public static ResponseCode fromCode(int code) {
        for (ResponseCode rc : values()) {
            if (rc.code == code) {
                return rc;
            }
        }
        return INTERNAL_ERROR;
    }
}