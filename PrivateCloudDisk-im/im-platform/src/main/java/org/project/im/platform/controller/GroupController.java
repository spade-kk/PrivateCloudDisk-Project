package org.project.im.platform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.project.im.common.dto.GroupDTO;
import org.project.im.common.dto.GroupMemberDTO;
import org.project.im.common.dto.PageResult;
import org.project.im.common.dto.Result;
import org.project.im.platform.dto.GroupCreateCommand;
import org.project.im.platform.dto.GroupMemberInviteCommand;
import org.project.im.platform.dto.GroupMemberMuteCommand;
import org.project.im.platform.dto.GroupMemberRoleCommand;
import org.project.im.platform.dto.GroupUpdateCommand;
import org.project.im.platform.service.GroupService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 群组管理控制器
 * <p>
 * 提供群组相关的 REST API：
 * <ul>
 *   <li>创建群组</li>
 *   <li>加入/退出群组</li>
 *   <li>踢人/禁言/解除禁言</li>
 *   <li>全员禁言</li>
 *   <li>解散群组</li>
 *   <li>群成员列表</li>
 *   <li>群公告</li>
 * </ul>
 * </p>
 *
 * <p>GROUP-CHAT-20260810 [6.1-6.15]：旧实现将群 ID 按 UUID 校验，但群 ID 实际由
 * Snowflake 生成，造成所有详情与成员接口在参数校验阶段失败。新接口移除该错误约束，并
 * 增加 JSON 管理接口；旧版 Query 参数路由仍保留以兼容已发布客户端。</p>
 */
@RestController
@RequestMapping("/im/groups")
@RequiredArgsConstructor
@Validated
@Tag(name = "群组管理", description = "群组创建、成员管理、禁言、解散")
public class GroupController {

    private final GroupService groupService;

    /** 新 Web 客户端：创建群时一次提交初始成员，服务端同步建立成员会话。 */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "创建群组（含初始成员）")
    public Result<GroupDTO> createGroupJson(@Valid @RequestBody GroupCreateCommand command) {
        return groupService.createGroup(command.getOwnerId(), command.getGroupName(), command.getAvatarFileId(),
                command.getMemberIds(), command.getJoinMode());
    }

    /** 旧客户端兼容接口。 */
    @PostMapping("/create")
    @Operation(summary = "创建群组（兼容接口）")
    public Result<GroupDTO> createGroup(@RequestParam @NotBlank String ownerId,
                                        @RequestParam @NotBlank @Size(max = 100) String groupName,
                                        @RequestParam(required = false) String avatar) {
        return groupService.createGroup(ownerId, groupName, avatar);
    }

    @GetMapping
    @Operation(summary = "获取当前用户加入的群组列表")
    public Result<PageResult<GroupDTO>> getUserGroups(@RequestParam @NotBlank String userId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "50") int size) {
        return groupService.getUserGroups(userId, page, size);
    }

    /** 旧路径兼容；新客户端使用分页根路径。 */
    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户加入的群组列表（兼容接口）")
    public Result<List<GroupDTO>> getUserGroupsLegacy(@PathVariable @NotBlank String userId) {
        return groupService.getUserGroups(userId);
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "获取群组详情")
    public Result<GroupDTO> getGroupDetail(@PathVariable @NotBlank String groupId,
                                            @RequestParam(required = false) String userId) {
        return userId == null ? groupService.getGroupDetail(groupId) : groupService.getGroupDetail(groupId, userId);
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "修改群资料")
    public Result<GroupDTO> updateGroup(@PathVariable @NotBlank String groupId,
                                        @Valid @RequestBody GroupUpdateCommand command) {
        return groupService.updateGroup(groupId, command.getOperatorId(), command.getName(), command.getAvatarFileId(),
                command.getAnnouncement(), command.getDescription(), command.getJoinMode());
    }

    @PostMapping("/{groupId}/members")
    @Operation(summary = "邀请群成员")
    public Result<Void> inviteMembers(@PathVariable @NotBlank String groupId,
                                      @Valid @RequestBody GroupMemberInviteCommand command) {
        return groupService.inviteMembers(groupId, command.getOperatorId(), command.getUserIds());
    }

    @DeleteMapping("/{groupId}/members/self")
    @Operation(summary = "退出群聊")
    public Result<Void> leaveGroupRest(@PathVariable @NotBlank String groupId, @RequestParam @NotBlank String userId) {
        return groupService.leaveGroup(groupId, userId);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @Operation(summary = "移除群成员")
    public Result<Void> removeMember(@PathVariable @NotBlank String groupId, @PathVariable @NotBlank String userId,
                                     @RequestParam @NotBlank String operatorId) {
        return groupService.kickMember(groupId, operatorId, userId);
    }

    @PutMapping("/{groupId}/members/{userId}/role")
    @Operation(summary = "设置群成员角色")
    public Result<Void> setMemberRole(@PathVariable @NotBlank String groupId, @PathVariable @NotBlank String userId,
                                      @Valid @RequestBody GroupMemberRoleCommand command) {
        return groupService.updateMemberRole(groupId, command.getOperatorId(), userId, command.getRole());
    }

    @PostMapping("/{groupId}/members/{userId}/mute")
    @Operation(summary = "禁言群成员")
    public Result<Void> muteMemberRest(@PathVariable @NotBlank String groupId, @PathVariable @NotBlank String userId,
                                       @Valid @RequestBody GroupMemberMuteCommand command) {
        return groupService.muteMember(groupId, command.getOperatorId(), userId, command.getDurationMinutes());
    }

    @DeleteMapping("/{groupId}/members/{userId}/mute")
    @Operation(summary = "取消群成员禁言")
    public Result<Void> unmuteMemberRest(@PathVariable @NotBlank String groupId, @PathVariable @NotBlank String userId,
                                         @RequestParam @NotBlank String operatorId) {
        return groupService.unmuteMember(groupId, operatorId, userId);
    }

    @GetMapping(value = "/{groupId}/members", params = "userId")
    @Operation(summary = "分页获取群成员")
    public Result<PageResult<GroupMemberDTO>> getGroupMembers(@PathVariable @NotBlank String groupId,
                                                               @RequestParam @NotBlank String userId,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "100") int size) {
        return groupService.getGroupMembers(groupId, userId, page, size);
    }

    /** 旧客户端兼容：未带 userId 时保持原有列表响应形态。 */
    @GetMapping(value = "/{groupId}/members", params = "!userId")
    @Operation(summary = "获取群成员列表（兼容接口）")
    public Result<List<GroupMemberDTO>> getGroupMembersLegacy(@PathVariable @NotBlank String groupId) {
        return groupService.getGroupMembers(groupId);
    }

    @DeleteMapping("/{groupId}")
    @Operation(summary = "解散群聊")
    public Result<Void> dissolveGroupRest(@PathVariable @NotBlank String groupId, @RequestParam @NotBlank String ownerId) {
        return groupService.dissolveGroup(groupId, ownerId);
    }

    // 以下为旧 Query 参数路由，保留以避免移动端或脚本客户端回归。
    @PostMapping("/{groupId}/join")
    public Result<Void> joinGroup(@PathVariable @NotBlank String groupId, @RequestParam @NotBlank String userId) { return groupService.joinGroup(groupId, userId); }
    @PostMapping("/{groupId}/leave")
    public Result<Void> leaveGroup(@PathVariable @NotBlank String groupId, @RequestParam @NotBlank String userId) { return groupService.leaveGroup(groupId, userId); }
    @PostMapping("/{groupId}/kick")
    public Result<Void> kickMember(@PathVariable @NotBlank String groupId, @RequestParam @NotBlank String operatorId, @RequestParam @NotBlank String targetUid) { return groupService.kickMember(groupId, operatorId, targetUid); }
    @PostMapping("/{groupId}/mute")
    public Result<Void> muteMember(@PathVariable @NotBlank String groupId, @RequestParam @NotBlank String operatorId, @RequestParam @NotBlank String targetUid, @RequestParam int durationMinutes) { return groupService.muteMember(groupId, operatorId, targetUid, durationMinutes); }
    @PostMapping("/{groupId}/unmute")
    public Result<Void> unmuteMember(@PathVariable @NotBlank String groupId, @RequestParam @NotBlank String operatorId, @RequestParam @NotBlank String targetUid) { return groupService.unmuteMember(groupId, operatorId, targetUid); }
    @PutMapping("/{groupId}/mute-all")
    public Result<Void> muteAll(@PathVariable @NotBlank String groupId, @RequestParam @NotBlank String operatorId, @RequestParam boolean isAllMuted) { return groupService.muteAll(groupId, operatorId, isAllMuted); }
    @DeleteMapping("/{groupId}/dissolve")
    public Result<Void> dissolveGroup(@PathVariable @NotBlank String groupId, @RequestParam @NotBlank String ownerId) { return groupService.dissolveGroup(groupId, ownerId); }
    @PutMapping("/{groupId}/announcement")
    public Result<Void> updateAnnouncement(@PathVariable @NotBlank String groupId, @RequestParam @NotBlank String operatorId, @RequestParam @NotBlank @Size(max = 500) String announcement) { return groupService.updateAnnouncement(groupId, operatorId, announcement); }
}
