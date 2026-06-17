package org.project.im.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.im.platform.entity.ImMessage;

import java.util.List;

/**
 * 消息 Mapper
 * <p>
 * IM 消息表（im_message）的数据访问层，提供消息的 CRUD 操作。
 * 支持分页查询历史消息、按消息 ID 查询、批量更新状态等。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Mapper
public interface ImMessageMapper {

    /**
     * 插入消息
     */
    int insert(ImMessage message);

    /**
     * 根据消息 ID 查询
     */
    ImMessage selectByMessageId(@Param("messageId") String messageId);

    /**
     * 分页查询会话历史消息（按 server_seq 倒序）
     */
    List<ImMessage> selectHistoryByConversationId(
            @Param("conversationId") String conversationId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 查询会话中从某条消息开始的历史消息（用于增量拉取）
     */
    List<ImMessage> selectMessagesAfterSeq(
            @Param("conversationId") String conversationId,
            @Param("serverSeq") Long serverSeq,
            @Param("limit") int limit);

    /**
     * 更新消息状态
     */
    int updateStatus(@Param("messageId") String messageId, @Param("status") int status);

    /**
     * 批量更新消息为已读
     */
    int batchUpdateRead(@Param("conversationId") String conversationId,
                        @Param("receiverId") String receiverId);

    /**
     * 撤回消息（更新状态为已撤回）
     */
    int recallMessage(@Param("messageId") String messageId);

    /**
     * 获取会话最大序列号
     */
    Long selectMaxSeqByConversationId(@Param("conversationId") String conversationId);

    /**
     * 获取用户某个会话的最后一条消息
     */
    ImMessage selectLastMessage(@Param("conversationId") String conversationId);
}