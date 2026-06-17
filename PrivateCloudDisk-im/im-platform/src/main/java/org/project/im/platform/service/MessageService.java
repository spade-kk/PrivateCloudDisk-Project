package org.project.im.platform.service;

import org.project.im.common.dto.MessageDTO;
import org.project.im.common.dto.Result;

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
}