package org.project.im.client;

import org.project.im.common.dto.ConversationDTO;
import org.project.im.common.dto.GroupDTO;
import org.project.im.common.dto.MessageDTO;
import org.project.im.common.dto.Result;

import java.util.List;

/**
 * IM 客户端接口
 * <p>
 * 定义其他业务模块调用 IM 服务的主要入口。
 * 通过此接口，业务模块可以无障碍地集成即时通讯能力，
 * 无需关心底层通信协议（HTTP/RabbitMQ/WebSocket）。
 * </p>
 * <p>
 * 使用方式：
 * <pre>
 * &#064;Autowired
 * private ImClient imClient;
 *
 * // 发送消息
 * Result&lt;MessageDTO&gt; result = imClient.sendMessage(messageDTO);
 *
 * // 获取会话列表
 * Result&lt;List&lt;ConversationDTO&gt;&gt; conversations = imClient.getConversations(userId);
 * </pre>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
public interface ImClient {

    // ==================== 消息相关 ====================

    /**
     * 发送消息
     *
     * @param messageDTO 消息内容
     * @return 发送结果（含生成的消息 ID 和序列号）
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
     * 批量发送系统通知
     *
     * @param userIds  目标用户 ID 列表
     * @param title    通知标题
     * @param content  通知内容
     * @return 发送结果
     */
    Result<Void> sendSystemNotice(List<String> userIds, String title, String content);

    // ==================== 会话相关 ====================

    /**
     * 获取或创建会话
     *
     * @param userId           用户 ID
     * @param targetId         目标 ID
     * @param conversationType 会话类型：1-单聊 2-群聊
     * @return 会话信息
     */
    Result<ConversationDTO> getOrCreateConversation(String userId, String targetId, int conversationType);

    /**
     * 获取用户会话列表
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    Result<List<ConversationDTO>> getConversations(String userId);

    /**
     * 获取用户总未读消息数
     *
     * @param userId 用户 ID
     * @return 未读消息总数
     */
    Result<Integer> getTotalUnreadCount(String userId);

    // ==================== 群组相关 ====================

    /**
     * 创建群组
     *
     * @param ownerId   群主 ID
     * @param groupName 群组名称
     * @param avatar    群头像 URL
     * @return 创建的群组信息
     */
    Result<GroupDTO> createGroup(String ownerId, String groupName, String avatar);

    /**
     * 获取群组详情
     *
     * @param groupId 群组 ID
     * @return 群组信息
     */
    Result<GroupDTO> getGroupDetail(String groupId);

    /**
     * 获取用户加入的群组列表
     *
     * @param userId 用户 ID
     * @return 群组列表
     */
    Result<List<GroupDTO>> getUserGroups(String userId);

    /**
     * 加入群组
     *
     * @param groupId 群组 ID
     * @param userId  用户 ID
     * @return 操作结果
     */
    Result<Void> joinGroup(String groupId, String userId);

    /**
     * 退出群组
     *
     * @param groupId 群组 ID
     * @param userId  用户 ID
     * @return 操作结果
     */
    Result<Void> leaveGroup(String groupId, String userId);

    /**
     * 获取群成员列表
     *
     * @param groupId 群组 ID
     * @return 成员列表
     */
    Result<List<org.project.im.common.dto.GroupMemberDTO>> getGroupMembers(String groupId);

    // ==================== 在线状态 ====================

    /**
     * 判断用户是否在线
     *
     * @param userId 用户 ID
     * @return 是否在线
     */
    boolean isUserOnline(String userId);

    /**
     * 获取在线用户数
     *
     * @return 在线用户数
     */
    int getOnlineUserCount();
}