package org.project.im.platform.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话实体
 * <p>
 * 对应数据库表 im_conversation，存储用户会话信息。
 * 每个会话关联两个用户（单聊）或一个用户和一个群组（群聊）。
 * 会话 ID 由 {@code userId + targetId} 生成，确保唯一性。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImConversation {

    /** 主键 ID */
    private Long id;

    /** 会话唯一 ID */
    private String conversationId;

    /** 会话类型：1-单聊 2-群聊 */
    private Integer conversationType;

    /** 当前用户 ID */
    private String userId;

    /** 对方 ID（单聊为对方 userId，群聊为 groupId） */
    private String targetId;

    /** 最后一条消息内容 */
    private String lastMessage;

    /** 最后一条消息类型 */
    private Integer lastMessageType;

    /** 最后一条消息时间 */
    private LocalDateTime lastMessageTime;

    /** 未读消息数 */
    private Integer unreadCount;

    /** 是否置顶：0-否 1-是 */
    private Boolean isTop;

    /** 是否免打扰：0-否 1-是 */
    private Boolean isMuted;

    /** 状态：0-正常 1-已删除 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}