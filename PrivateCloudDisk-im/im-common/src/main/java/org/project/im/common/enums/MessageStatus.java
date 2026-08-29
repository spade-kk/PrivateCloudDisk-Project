package org.project.im.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息状态枚举（精简为四种核心投递状态）
 * <p>
 * 追踪消息从入库到最终消费的完整生命周期：
 * <ul>
 *   <li>PREPARING：消息已持久化到数据库，尚未推送到接收方客户端，也未拉取</li>
 *   <li>DELIVERED：消息已通过 WebSocket 推送到接收方客户端，或接收方已通过离线消息拉取接口获取</li>
 *   <li>READ：接收方已查看消息（进入聊天页面），客户端上报已读回执</li>
 *   <li>FAILED：消息推送失败或权限校验失败等异常情况，无法送达</li>
 * </ul>
 * 撤回（RECALLED=5）与删除（DELETED=6）属于可见性状态，不参与投递生命周期，
 * 由 {@code ImConstants} 中的常量表示，避免与核心四态混淆。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 3.0.0
 */
@Getter
@AllArgsConstructor
public enum MessageStatus {

    /** 准备中（已入库，尚未送达/拉取） */
    PREPARING(0, "准备中"),

    /** 已送达（已推送或已拉取） */
    DELIVERED(1, "已送达"),

    /** 已读（接收方已查看） */
    READ(2, "已读"),

    /** 失败（推送失败或权限校验失败） */
    FAILED(3, "失败");

    private final int code;
    private final String description;

    public static MessageStatus fromCode(int code) {
        for (MessageStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return PREPARING;
    }
}
