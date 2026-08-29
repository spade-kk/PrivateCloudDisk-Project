package org.project.im.platform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.project.im.common.dto.ConversationDTO;
import org.project.im.common.dto.Result;
import org.project.im.platform.service.ConversationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话管理控制器
 * <p>
 * 提供会话相关的 REST API：
 * <ul>
 *   <li>查询既有会话</li>
 *   <li>会话列表</li>
 *   <li>会话详情</li>
 *   <li>置顶/免打扰</li>
 *   <li>未读消息数</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/im/conversations")
@RequiredArgsConstructor
@Validated
@Tag(name = "会话管理", description = "既有会话查询、列表、置顶、免打扰")
public class ConversationController {

    private final ConversationService conversationService;

    private static final String UUID_REGEX =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    // AUDIT FIX [5.1/5.5] / IM-EMOJI-SESSION-20260810：移除外部创建端点。
    // 会话只在好友接受/群成员加入事务内部创建，客户端只允许按 peer 查询既有会话。
    @GetMapping("/peer")
    @Operation(summary = "查询已存在会话")
    public Result<ConversationDTO> getExistingConversation(
            @Parameter(description = "用户 ID")
            @RequestParam @NotBlank(message = "用户ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "用户ID必须是有效的UUID格式") String userId,
            @Parameter(description = "对端 ID（单聊为对方 userId，群聊为 groupId）")
            @RequestParam @NotBlank(message = "对端ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "对端ID必须是有效的UUID格式") String peerId,
            @Parameter(description = "会话类型：1-单聊 2-群聊")
            @RequestParam @Min(value = 1, message = "会话类型最小为1")
            @Max(value = 2, message = "会话类型最大为2") int conversationType) {
        return conversationService.getExistingConversation(userId, peerId, conversationType);
    }

    @GetMapping("/list")
    @Operation(summary = "获取用户会话列表")
    public Result<List<ConversationDTO>> getConversations(
            @Parameter(description = "用户 ID")
            @RequestParam @NotBlank(message = "用户ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "用户ID必须是有效的UUID格式") String userId) {
        return conversationService.getConversations(userId);
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "获取会话详情")
    public Result<ConversationDTO> getConversationDetail(
            @Parameter(description = "会话 ID")
            @PathVariable @NotBlank(message = "会话ID不能为空") String conversationId,
            @Parameter(description = "用户 ID")
            @RequestParam @NotBlank(message = "用户ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "用户ID必须是有效的UUID格式") String userId) {
        return conversationService.getConversationDetail(conversationId, userId);
    }

    @PutMapping("/{conversationId}/top")
    @Operation(summary = "置顶/取消置顶会话")
    public Result<Void> topConversation(
            @Parameter(description = "会话 ID")
            // AUDIT FIX [5.2/5.5] / IM-EMOJI-SESSION-20260810：旧行为将会话 ID 固定为 UUID。
            // 新行为使用 minUserId*maxUserId 或 group*groupId；仅校验非空，实际归属仍由 Service
            // 按「sessionId + userId」限制，避免新 ID 规则导致置顶/免打扰接口被错误拒绝。
            @PathVariable @NotBlank(message = "会话ID不能为空") String conversationId,
            @Parameter(description = "用户 ID")
            @RequestParam @NotBlank(message = "用户ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "用户ID必须是有效的UUID格式") String userId,
            @Parameter(description = "是否置顶") @RequestParam boolean isTop) {
        return conversationService.topConversation(conversationId, userId, isTop);
    }

    @PutMapping("/{conversationId}/mute")
    @Operation(summary = "设置/取消免打扰")
    public Result<Void> muteConversation(
            @Parameter(description = "会话 ID")
            // AUDIT FIX [5.2/5.5] / IM-EMOJI-SESSION-20260810：同上，允许新的稳定会话 ID。
            @PathVariable @NotBlank(message = "会话ID不能为空") String conversationId,
            @Parameter(description = "用户 ID")
            @RequestParam @NotBlank(message = "用户ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "用户ID必须是有效的UUID格式") String userId,
            @Parameter(description = "是否免打扰") @RequestParam boolean isMuted) {
        return conversationService.muteConversation(conversationId, userId, isMuted);
    }

    @GetMapping("/unread/count")
    @Operation(summary = "获取用户总未读消息数")
    public Result<Integer> getTotalUnreadCount(
            @Parameter(description = "用户 ID")
            @RequestParam @NotBlank(message = "用户ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "用户ID必须是有效的UUID格式") String userId) {
        return conversationService.getTotalUnreadCount(userId);
    }
}
