package org.project.im.platform.service;

import org.project.im.common.dto.MessageDTO;
import org.project.im.common.dto.Result;
import org.project.im.common.enums.MessageStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息服务接口
 * <p>
 * 提供消息的发送、查询、撤回、已读等核心业务功能。
 * 消息发送后通过 RabbitMQ 异步投递到 im-server 进行推送。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
public interface MessageService {

    /**
     * 发送消息
     *
     * @param messageDTO 消息内容
     * @return 发送结果（含生成的消息 ID）
     */
    Result<MessageDTO> sendMessage(MessageDTO messageDTO);

    /**
     * 撤回消息
     *
     * @param messageId 消息 ID
     * @param userId    操作用户 ID
     * @return 撤回结果
     */
    Result<Void> recallMessage(String messageId, String userId);

    /**
     * 标记消息已读
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @return 操作结果
     */
    Result<Void> markAsRead(String conversationId, String userId);

    /**
     * 分页查询历史消息
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param page           页码（从 1 开始）
     * @param size           每页大小
     * @return 消息列表
     */
    Result<List<MessageDTO>> getHistory(String conversationId, String userId, int page, int size);

    /**
     * 增量拉取消息（从指定序号之后的消息）
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param serverSeq      上次拉取的最大序号
     * @param limit          拉取条数
     * @return 消息列表
     */
    Result<List<MessageDTO>> getMessagesAfter(String conversationId, String userId,
                                               Long serverSeq, int limit);

    /**
     * 根据消息 ID 查询消息
     *
     * @param messageId 消息 ID
     * @return 消息详情
     */
    Result<MessageDTO> getMessageById(String messageId);

    /**
     * 更新消息状态（消费送达/失败事件时调用）
     *
     * @param messageId 消息 ID
     * @param status    目标状态（DELIVERED / FAILED）
     * @return 操作结果
     */
    Result<Void> updateStatus(String messageId, MessageStatus status);

    /**
     * 拉取当前用户的离线消息（状态为 PREPARING）
     * <p>
     * 采用多级缓存：优先从 Redis 离线队列读取，未命中时降级查询数据库。
     * 拉取后将消息状态批量更新为 DELIVERED，并清空对应 Redis 缓存。
     * </p>
     *
     * @param userId 当前用户 ID
     * @param limit  最大拉取条数（默认 100）
     * @return 离线消息列表
     */
    Result<List<MessageDTO>> getOfflineMessages(String userId, int limit);

    /**
     * 游标分页查询会话历史消息
     * <p>
     * 仅返回已送达 / 已读 / 失败等终态消息，不含 PREPARING 未送达消息
     * （未送达消息应通过离线拉取接口或 WebSocket 实时推送获取）。
     * 使用 server_seq 游标向前翻页，避免 offset 深分页性能问题。
     * </p>
     *
     * @param conversationId 会话 ID
     * @param userId         当前用户 ID
     * @param limit          每页条数（默认 20，最大 100）
     * @param cursor         上一页最小 server_seq（首次传 null）
     * @param before         可选，拉取该时间之前的消息
     * @return 历史消息列表（按时间倒序）
     */
    Result<List<MessageDTO>> getHistoryByCursor(String conversationId, String userId,
                                                int limit, Long cursor, LocalDateTime before);
}