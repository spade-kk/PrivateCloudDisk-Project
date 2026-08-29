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

    /** Redis 会话摘要未命中时回查单聊未读数。 */
    int countUnreadByConversationIdAndReceiver(@Param("conversationId") String conversationId,
                                               @Param("receiverId") String receiverId);

    /**
     * 查询用户离线消息（receiver_id + status，按 server_seq 倒序，分页）
     *
     * @param receiverId 接收者用户 ID
     * @param status     消息状态（离线消息为 PREPARING）
     * @param limit      拉取条数
     * @return 离线消息列表
     */
    List<ImMessage> selectOfflineMessages(
            @Param("receiverId") String receiverId,
            @Param("status") int status,
            @Param("limit") int limit);

    /**
     * 批量更新消息状态（拉取离线消息后标记为 DELIVERED）
     *
     * @param messageIds 消息 ID 列表
     * @param status     目标状态
     * @return 更新条数
     */
    int batchUpdateStatus(@Param("messageIds") List<String> messageIds,
                          @Param("status") int status);

    /**
     * 游标分页查询会话历史消息（仅返回已送达/已读/失败等终态消息）
     * <p>
     * 使用 server_seq < cursor 游标方式向前翻页，避免 offset 深分页性能问题。
     * 只返回 {@code statuses} 白名单内的消息（不含 PREPARING 未送达消息）。
     * </p>
     *
     * @param conversationId 会话 ID
     * @param statuses       允许返回的消息状态白名单
     * @param cursor         上一页最小 server_seq（首次传 null）
     * @param limit          每页条数
     * @return 历史消息列表
     */
    List<ImMessage> selectHistoryByCursor(
            @Param("conversationId") String conversationId,
            @Param("statuses") List<Integer> statuses,
            @Param("cursor") Long cursor,
            @Param("before") java.time.LocalDateTime before,
            @Param("limit") int limit);
}
