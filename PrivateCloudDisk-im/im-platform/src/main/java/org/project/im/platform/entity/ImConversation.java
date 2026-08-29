package org.project.im.platform.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话实体
 * <p>
 * 对应数据库表 pcd_im_conversation，存储用户会话元数据。
 * 每个会话关联两个用户（单聊）或一个用户和一个群组（群聊）。
 * 共享 sessionId 由 {@code minUserId*maxUserId} 或 {@code group*groupId} 生成；
 * 每位参与者各有一行元数据，使置顶、免打扰及 Redis 未读摘要互不覆盖。
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

    /** 共享会话 ID（同时对应消息表 conversation_id） */
    private String sessionId;

    /** 会话类型：1-单聊 2-群聊 */
    private Integer sessionType;

    /** 当前用户 ID */
    private String userId;

    /** 对端 ID（单聊为对方 userId，群聊为 groupId） */
    private String peerId;

    /** 是否置顶：0-否 1-是 */
    private Boolean isPinned;

    /** 是否免打扰：0-否 1-是 */
    private Boolean isMuted;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
