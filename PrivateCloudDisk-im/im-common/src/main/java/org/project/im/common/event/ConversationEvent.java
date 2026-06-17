package org.project.im.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话变更事件
 * <p>
 * 会话创建、更新、删除时触发，用于：
 * <ul>
 *   <li>通知客户端更新会话列表</li>
 *   <li>更新搜索引擎索引</li>
 *   <li>触发会话相关的业务逻辑</li>
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
public class ConversationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    private String conversationId;

    /** 事件类型：CREATE / UPDATE / DELETE / TOP / MUTE */
    private String eventType;

    /** 会话类型 */
    private Integer conversationType;

    /** 相关用户 ID */
    private String userId;

    /** 目标 ID */
    private String targetId;

    /** 事件时间 */
    private LocalDateTime eventTime;
}