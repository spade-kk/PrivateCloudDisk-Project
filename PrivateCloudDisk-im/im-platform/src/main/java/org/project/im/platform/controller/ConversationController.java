package org.project.im.platform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.project.im.common.dto.ConversationDTO;
import org.project.im.common.dto.Result;
import org.project.im.platform.service.ConversationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话管理控制器
 * <p>
 * 提供会话相关的 REST API：
 * <ul>
 *   <li>创建/获取会话</li>
 *   <li>会话列表</li>
 *   <li>会话详情</li>
 *   <li>删除会话</li>
 *   <li>置顶/免打扰</li>
 *   <li>未读消息数</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "会话管理", description = "会话创建、列表、置顶、免打扰、删除")
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/create")
    @Operation(summary = "创建或获取会话（幂等，已存在则返回已有会话）")
    public Result<ConversationDTO> getOrCreateConversation(
            @Parameter(description = "用户 ID") @RequestParam String userId,
            @Parameter(description = "目标 ID（单聊为对方 userId，群聊为 groupId）") @RequestParam String targetId,
            @Parameter(description = "会话类型：1-单聊 2-群聊") @RequestParam int conversationType) {
        return conversationService.getOrCreateConversation(userId, targetId, conversationType);
    }

    @GetMapping("/list")
    @Operation(summary = "获取用户会话列表")
    public Result<List<ConversationDTO>> getConversations(
            @Parameter(description = "用户 ID") @RequestParam String userId) {
        return conversationService.getConversations(userId);
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "获取会话详情")
    public Result<ConversationDTO> getConversationDetail(
            @Parameter(description = "会话 ID") @PathVariable String conversationId) {
        return conversationService.getConversationDetail(conversationId);
    }

    @DeleteMapping("/{conversationId}")
    @Operation(summary = "删除会话（软删除）")
    public Result<Void> deleteConversation(
            @Parameter(description = "会话 ID") @PathVariable String conversationId,
            @Parameter(description = "用户 ID") @RequestParam String userId) {
        return conversationService.deleteConversation(conversationId, userId);
    }

    @PutMapping("/{conversationId}/top")
    @Operation(summary = "置顶/取消置顶会话")
    public Result<Void> topConversation(
            @Parameter(description = "会话 ID") @PathVariable String conversationId,
            @Parameter(description = "用户 ID") @RequestParam String userId,
            @Parameter(description = "是否置顶") @RequestParam boolean isTop) {
        return conversationService.topConversation(conversationId, userId, isTop);
    }

    @PutMapping("/{conversationId}/mute")
    @Operation(summary = "设置/取消免打扰")
    public Result<Void> muteConversation(
            @Parameter(description = "会话 ID") @PathVariable String conversationId,
            @Parameter(description = "用户 ID") @RequestParam String userId,
            @Parameter(description = "是否免打扰") @RequestParam boolean isMuted) {
        return conversationService.muteConversation(conversationId, userId, isMuted);
    }

    @GetMapping("/unread/count")
    @Operation(summary = "获取用户总未读消息数")
    public Result<Integer> getTotalUnreadCount(
            @Parameter(description = "用户 ID") @RequestParam String userId) {
        return conversationService.getTotalUnreadCount(userId);
    }
}