package org.project.im.platform.service;

import org.project.im.common.dto.ConversationDTO;
import org.project.im.common.dto.Result;

import java.util.List;

/**
 * 会话服务接口
 * <p>
 * 提供会话的创建、查询、删除、置顶、免打扰等核心功能。
 * 会话是消息的容器，管理用户与另一方（个人或群组）的聊天窗口。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
public interface ConversationService {

    /**
     * 创建或获取会话
     * <p>
     * 若会话已存在则直接返回，否则创建新会话。
     * 单聊会话 ID 由双方用户 ID 生成（确保唯一性）。
     * </p>
     *
     * @param userId           当前用户 ID
     * @param targetId         目标 ID（单聊为对方 userId，群聊为 groupId）
     * @param conversationType 会话类型
     * @return 会话信息
     */
    Result<ConversationDTO> getOrCreateConversation(String userId, String targetId, int conversationType);

    /**
     * 获取用户会话列表
     *
     * @param userId 用户 ID
     * @return 会话列表（按最后消息时间倒序）
     */
    Result<List<ConversationDTO>> getConversations(String userId);

    /**
     * 获取会话详情
     *
     * @param conversationId 会话 ID
     * @return 会话信息
     */
    Result<ConversationDTO> getConversationDetail(String conversationId);

    /**
     * 删除会话（软删除）
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @return 操作结果
     */
    Result<Void> deleteConversation(String conversationId, String userId);

    /**
     * 置顶/取消置顶会话
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param isTop          是否置顶
     * @return 操作结果
     */
    Result<Void> topConversation(String conversationId, String userId, boolean isTop);

    /**
     * 设置/取消免打扰
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param isMuted        是否免打扰
     * @return 操作结果
     */
    Result<Void> muteConversation(String conversationId, String userId, boolean isMuted);

    /**
     * 获取用户总未读消息数
     *
     * @param userId 用户 ID
     * @return 未读消息总数
     */
    Result<Integer> getTotalUnreadCount(String userId);
}