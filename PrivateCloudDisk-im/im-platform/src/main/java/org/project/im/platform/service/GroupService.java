package org.project.im.platform.service;

import org.project.im.common.dto.GroupDTO;
import org.project.im.common.dto.GroupMemberDTO;
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
     * 踢出成员
     *
     * @param groupId   群组 ID
     * @param ownerId   操作者 ID
     * @param targetUid 被踢用户 ID
     * @return 操作结果
     */
    Result<Void> kickMember(String groupId, String ownerId, String targetUid);

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
}