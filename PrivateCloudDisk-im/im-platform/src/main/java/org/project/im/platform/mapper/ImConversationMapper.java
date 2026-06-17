package org.project.im.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.im.platform.entity.ImConversation;

import java.util.List;

/**
 * 会话 Mapper
 * <p>
 * IM 会话表（im_conversation）的数据访问层，提供会话的 CRUD 操作。
 * 会话是消息的容器，按用户和会话类型组织。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Mapper
public interface ImConversationMapper {

    /**
     * 插入会话
     */
    int insert(ImConversation conversation);

    /**
     * 根据会话 ID 查询
     */
    ImConversation selectByConversationId(@Param("conversationId") String conversationId);

    /**
     * 查询用户的所有会话列表（按最后消息时间倒序）
     */
    List<ImConversation> selectByUserId(@Param("userId") String userId);

    /**
     * 更新最后一条消息信息
     */
    int updateLastMessage(@Param("conversationId") String conversationId,
                          @Param("lastMessage") String lastMessage,
                          @Param("lastMessageType") Integer lastMessageType,
                          @Param("lastMessageTime") java.time.LocalDateTime lastMessageTime);

    /**
     * 递增未读消息数
     */
    int incrementUnreadCount(@Param("conversationId") String conversationId);

    /**
     * 清零未读消息数
     */
    int clearUnreadCount(@Param("conversationId") String conversationId);

    /**
     * 更新置顶状态
     */
    int updateTopStatus(@Param("conversationId") String conversationId,
                        @Param("isTop") Boolean isTop);

    /**
     * 更新免打扰状态
     */
    int updateMuteStatus(@Param("conversationId") String conversationId,
                         @Param("isMuted") Boolean isMuted);

    /**
     * 软删除会话
     */
    int softDelete(@Param("conversationId") String conversationId);

    /**
     * 根据用户 ID 和目标 ID 查找会话
     */
    ImConversation selectByUserIdAndTargetId(@Param("userId") String userId,
                                              @Param("targetId") String targetId,
                                              @Param("conversationType") Integer conversationType);

    /**
     * 获取用户未读消息总数
     */
    int selectTotalUnreadCount(@Param("userId") String userId);
}