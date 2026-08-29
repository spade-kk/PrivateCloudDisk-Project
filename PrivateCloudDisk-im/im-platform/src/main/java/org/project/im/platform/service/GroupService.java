package org.project.im.platform.service;

import org.project.im.common.dto.GroupDTO;
import org.project.im.common.dto.GroupMemberDTO;
import org.project.im.common.dto.PageResult;
import org.project.im.common.dto.Result;

import java.util.List;

/**
 * 群组服务接口
 * <p>
 * 提供群组的创建、加入、退出、踢人、禁言、解散等核心功能。
 * 群组是多人聊天的载体，支持权限管理和全员禁言。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
public interface GroupService {

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
     * 创建群并同步创建所有初始成员的群会话。
     *
     * <p>GROUP-CHAT-20260810 [4.11-4.13]：旧方法只确保群主会话存在；新方法在同一个
     * 本地事务中加入选中成员并写入各自的 {@code group*{groupId}} 会话元数据，不引入
     * MQ 编排，避免创建完成后成员无法立即进入群聊。</p>
     */
    Result<GroupDTO> createGroup(String ownerId, String groupName, String avatar, List<String> memberIds, Integer joinMode);

    /**
     * 获取群组详情
     *
     * @param groupId 群组 ID
     * @return 群组信息
     */
    Result<GroupDTO> getGroupDetail(String groupId);

    /** 按查看者权限返回群详情及其群内角色。 */
    Result<GroupDTO> getGroupDetail(String groupId, String viewerId);

    /**
     * 获取用户加入的群组列表
     *
     * @param userId 用户 ID
     * @return 群组列表
     */
    Result<List<GroupDTO>> getUserGroups(String userId);

    /** 分页获取当前用户的正常群组。 */
    Result<PageResult<GroupDTO>> getUserGroups(String userId, int page, int size);

    /**
     * 加入群组
     *
     * @param groupId 群组 ID
     * @param userId  用户 ID
     * @return 操作结果
     */
    Result<Void> joinGroup(String groupId, String userId);

    /** 管理员/群主邀请成员；每位新成员会话同步创建。 */
    Result<Void> inviteMembers(String groupId, String operatorId, List<String> userIds);

    /**
     * 退出群组
     *
     * @param groupId 群组 ID
     * @param userId  用户 ID
     * @return 操作结果
     */
    Result<Void> leaveGroup(String groupId, String userId);

    /**
     * 踢出成员
     *
     * @param groupId   群组 ID
     * @param ownerId   操作者 ID
     * @param targetUid 被踢用户 ID
     * @return 操作结果
     */
    Result<Void> kickMember(String groupId, String ownerId, String targetUid);

    /** 群主设置或取消管理员，角色仅允许 ADMIN/MEMBER。 */
    Result<Void> updateMemberRole(String groupId, String operatorId, String targetUid, int role);

    /**
     * 禁言成员
     *
     * @param groupId       群组 ID
     * @param operatorId    操作者 ID
     * @param targetUid     被禁言用户 ID
     * @param durationMinutes 禁言时长（分钟，-1 表示永久禁言）
     * @return 操作结果
     */
    Result<Void> muteMember(String groupId, String operatorId, String targetUid, int durationMinutes);

    /**
     * 取消禁言
     *
     * @param groupId    群组 ID
     * @param operatorId 操作者 ID
     * @param targetUid  被取消禁言用户 ID
     * @return 操作结果
     */
    Result<Void> unmuteMember(String groupId, String operatorId, String targetUid);

    /**
     * 全员禁言/取消全员禁言
     *
     * @param groupId    群组 ID
     * @param operatorId 操作者 ID
     * @param isAllMuted 是否全员禁言
     * @return 操作结果
     */
    Result<Void> muteAll(String groupId, String operatorId, boolean isAllMuted);

    /**
     * 解散群组
     *
     * @param groupId 群组 ID
     * @param ownerId 群主 ID
     * @return 操作结果
     */
    Result<Void> dissolveGroup(String groupId, String ownerId);

    /**
     * 获取群成员列表
     *
     * @param groupId 群组 ID
     * @return 成员列表
     */
    Result<List<GroupMemberDTO>> getGroupMembers(String groupId);

    /**
     * 更新群公告
     *
     * @param groupId      群组 ID
     * @param operatorId   操作者 ID
     * @param announcement 公告内容
     * @return 操作结果
     */
    Result<Void> updateAnnouncement(String groupId, String operatorId, String announcement);

    /** 更新群名称、头像、公告、简介和入群策略；未给出的字段保持原值。 */
    Result<GroupDTO> updateGroup(String groupId, String operatorId, String groupName, String avatar,
                                 String announcement, String description, Integer joinMode);

    /** 群成员分页查询，非成员不得读取成员资料。 */
    Result<PageResult<GroupMemberDTO>> getGroupMembers(String groupId, String viewerId, int page, int size);
}
