package org.project.im.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话 DTO
 * <p>
 * 会话是消息的容器，代表用户与另一方（个人或群组）的聊天窗口。
 * 包含未读数、最后一条消息摘要、会话状态等信息。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话唯一 ID */
    private String conversationId;

    /** 会话类型：1-单聊 2-群聊 3-系统 */
    private Integer conversationType;

    /** 会话名称（单聊为对方昵称，群聊为群名） */
    private String conversationName;

    /** 会话头像 URL */
    private String avatar;

    /** 当前用户 ID */
    private String userId;

    /** 对方 ID（单聊为对方 userId，群聊为 groupId） */
    private String targetId;

    /** 最后一条消息内容摘要 */
    private String lastMessage;

    /** 最后一条消息类型 */
    private Integer lastMessageType;

    /** 最后一条消息发送时间 */
    private LocalDateTime lastMessageTime;

    /** 未读消息数 */
    private Integer unreadCount;

    /** 是否置顶 */
    private Boolean isTop;

    /** 是否免打扰 */
    private Boolean isMuted;

    /** 当前用户是否仍具备发送权限（好友已解除/已退出群组时为 false）。 */
    private Boolean canSend;

    /** 会话可用状态：ACTIVE、FRIEND_REMOVED、GROUP_LEFT。 */
    private String sessionStatus;

    /** 总消息数 */
    private Long totalMessages;

    /** 会话状态：0-正常 1-已删除 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
