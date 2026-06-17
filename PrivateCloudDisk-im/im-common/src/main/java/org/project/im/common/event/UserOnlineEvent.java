package org.project.im.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户在线状态变更事件
 * <p>
 * 用户上线/下线时触发，用于：
 * <ul>
 *   <li>更新 Redis 在线状态</li>
 *   <li>通知好友在线状态变化</li>
 *   <li>触发离线消息推送</li>
 *   <li>记录在线时长统计</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOnlineEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private String userId;

    /** 事件类型：ONLINE / OFFLINE / KICKED */
    private String eventType;

    /** 客户端类型：WEB / ANDROID / IOS / DESKTOP */
    private String clientType;

    /** 客户端 IP */
    private String clientIp;

    /** WebSocket 通道 ID */
    private String channelId;

    /** 事件时间 */
    private LocalDateTime eventTime;
}