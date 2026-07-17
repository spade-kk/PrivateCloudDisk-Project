package org.project.im.platform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.project.im.common.dto.GroupDTO;
import org.project.im.common.dto.GroupMemberDTO;
import org.project.im.common.dto.Result;
import org.project.im.platform.service.GroupService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/im/groups")
@RequiredArgsConstructor
@Validated
@Tag(name = "群组管理", description = "群组创建、成员管理、禁言、解散")
public class GroupController {

    private final GroupService groupService;

    private static final String UUID_REGEX =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @PostMapping("/create")
    @Operation(summary = "创建群组")
    public Result<GroupDTO> createGroup(
            @Parameter(description = "群主 ID")
            @RequestParam @NotBlank(message = "群主ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "群主ID必须是有效的UUID格式") String ownerId,
            @Parameter(description = "群组名称")
            @RequestParam @NotBlank(message = "群组名称不能为空")
            @Size(max = 100, message = "群组名称长度不能超过100个字符") String groupName,
            @Parameter(description = "群头像 URL") @RequestParam(required = false) String avatar) {
        return groupService.createGroup(ownerId, groupName, avatar);
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "获取群组详情")
    public Result<GroupDTO> getGroupDetail(
            @Parameter(description = "群组 ID")
            @PathVariable @NotBlank(message = "群组ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "群组ID必须是有效的UUID格式") String groupId) {
        return groupService.getGroupDetail(groupId);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户加入的群组列表")
    public Result<List<GroupDTO>> getUserGroups(
            @Parameter(description = "用户 ID")
            @PathVariable @NotBlank(message = "用户ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "用户ID必须是有效的UUID格式") String userId) {
        return groupService.getUserGroups(userId);
    }

    @PostMapping("/{groupId}/join")
    @Operation(summary = "加入群组")
    public Result<Void> joinGroup(
            @Parameter(description = "群组 ID")
            @PathVariable @NotBlank(message = "群组ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "群组ID必须是有效的UUID格式") String groupId,
            @Parameter(description = "用户 ID")
            @RequestParam @NotBlank(message = "用户ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "用户ID必须是有效的UUID格式") String userId) {
        return groupService.joinGroup(groupId, userId);
    }

    @PostMapping("/{groupId}/leave")
    @Operation(summary = "退出群组")
    public Result<Void> leaveGroup(
            @Parameter(description = "群组 ID")
            @PathVariable @NotBlank(message = "群组ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "群组ID必须是有效的UUID格式") String groupId,
            @Parameter(description = "用户 ID")
            @RequestParam @NotBlank(message = "用户ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "用户ID必须是有效的UUID格式") String userId) {
        return groupService.leaveGroup(groupId, userId);
    }

    @PostMapping("/{groupId}/kick")
    @Operation(summary = "踢出成员（群主/管理员操作）")
    public Result<Void> kickMember(
            @Parameter(description = "群组 ID")
            @PathVariable @NotBlank(message = "群组ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "群组ID必须是有效的UUID格式") String groupId,
            @Parameter(description = "操作者 ID")
            @RequestParam @NotBlank(message = "操作者ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "操作者ID必须是有效的UUID格式") String operatorId,
            @Parameter(description = "被踢用户 ID")
            @RequestParam @NotBlank(message = "被踢用户ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "被踢用户ID必须是有效的UUID格式") String targetUid) {
        return groupService.kickMember(groupId, operatorId, targetUid);
    }

    @PostMapping("/{groupId}/mute")
    @Operation(summary = "禁言成员（群主/管理员操作）")
    public Result<Void> muteMember(
            @Parameter(description = "群组 ID")
            @PathVariable @NotBlank(message = "群组ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "群组ID必须是有效的UUID格式") String groupId,
            @Parameter(description = "操作者 ID")
            @RequestParam @NotBlank(message = "操作者ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "操作者ID必须是有效的UUID格式") String operatorId,
            @Parameter(description = "被禁言用户 ID")
            @RequestParam @NotBlank(message = "被禁言用户ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "被禁言用户ID必须是有效的UUID格式") String targetUid,
            @Parameter(description = "禁言时长（分钟，-1 表示永久）")
            @RequestParam @Min(value = -1, message = "禁言时长不能小于-1") int durationMinutes) {
        return groupService.muteMember(groupId, operatorId, targetUid, durationMinutes);
    }

    @PostMapping("/{groupId}/unmute")
    @Operation(summary = "解除禁言")
    public Result<Void> unmuteMember(
            @Parameter(description = "群组 ID")
            @PathVariable @NotBlank(message = "群组ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "群组ID必须是有效的UUID格式") String groupId,
            @Parameter(description = "操作者 ID")
            @RequestParam @NotBlank(message = "操作者ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "操作者ID必须是有效的UUID格式") String operatorId,
            @Parameter(description = "被解除禁言用户 ID")
            @RequestParam @NotBlank(message = "被解除禁言用户ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "被解除禁言用户ID必须是有效的UUID格式") String targetUid) {
        return groupService.unmuteMember(groupId, operatorId, targetUid);
    }

    @PutMapping("/{groupId}/mute-all")
    @Operation(summary = "全员禁言/取消（仅群主）")
    public Result<Void> muteAll(
            @Parameter(description = "群组 ID")
            @PathVariable @NotBlank(message = "群组ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "群组ID必须是有效的UUID格式") String groupId,
            @Parameter(description = "操作者 ID（群主）")
            @RequestParam @NotBlank(message = "操作者ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "操作者ID必须是有效的UUID格式") String operatorId,
            @Parameter(description = "是否全员禁言") @RequestParam boolean isAllMuted) {
        return groupService.muteAll(groupId, operatorId, isAllMuted);
    }

    @DeleteMapping("/{groupId}/dissolve")
    @Operation(summary = "解散群组（仅群主）")
    public Result<Void> dissolveGroup(
            @Parameter(description = "群组 ID")
            @PathVariable @NotBlank(message = "群组ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "群组ID必须是有效的UUID格式") String groupId,
            @Parameter(description = "群主 ID")
            @RequestParam @NotBlank(message = "群主ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "群主ID必须是有效的UUID格式") String ownerId) {
        return groupService.dissolveGroup(groupId, ownerId);
    }

    @GetMapping("/{groupId}/members")
    @Operation(summary = "获取群成员列表")
    public Result<List<GroupMemberDTO>> getGroupMembers(
            @Parameter(description = "群组 ID")
            @PathVariable @NotBlank(message = "群组ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "群组ID必须是有效的UUID格式") String groupId) {
        return groupService.getGroupMembers(groupId);
    }

    @PutMapping("/{groupId}/announcement")
    @Operation(summary = "更新群公告（群主/管理员操作）")
    public Result<Void> updateAnnouncement(
            @Parameter(description = "群组 ID")
            @PathVariable @NotBlank(message = "群组ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "群组ID必须是有效的UUID格式") String groupId,
            @Parameter(description = "操作者 ID")
            @RequestParam @NotBlank(message = "操作者ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "操作者ID必须是有效的UUID格式") String operatorId,
            @Parameter(description = "公告内容")
            @RequestParam @NotBlank(message = "公告内容不能为空")
            @Size(max = 500, message = "公告内容长度不能超过500个字符") String announcement) {
        return groupService.updateAnnouncement(groupId, operatorId, announcement);
    }
}
