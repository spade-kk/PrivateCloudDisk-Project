package org.project.im.platform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.project.im.common.dto.MessageDTO;
import org.project.im.common.dto.Result;
import org.project.im.platform.service.MessageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息管理控制器
 * <p>
 * 提供消息相关的 REST API：
 * <ul>
 *   <li>发送消息</li>
 *   <li>撤回消息</li>
 *   <li>标记已读</li>
 *   <li>查询历史消息</li>
 *   <li>增量拉取消息</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Tag(name = "消息管理", description = "消息发送、撤回、已读、历史查询")
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/send")
    @Operation(summary = "发送消息")
    public Result<MessageDTO> sendMessage(@Valid @RequestBody MessageDTO messageDTO) {
        return messageService.sendMessage(messageDTO);
    }

    @PostMapping("/recall")
    @Operation(summary = "撤回消息（2分钟内有效）")
    public Result<Void> recallMessage(
            @Parameter(description = "消息 ID") @RequestParam String messageId,
            @Parameter(description = "用户 ID") @RequestParam String userId) {
        return messageService.recallMessage(messageId, userId);
    }

    @PostMapping("/read")
    @Operation(summary = "标记会话消息已读")
    public Result<Void> markAsRead(
            @Parameter(description = "会话 ID") @RequestParam String conversationId,
            @Parameter(description = "用户 ID") @RequestParam String userId) {
        return messageService.markAsRead(conversationId, userId);
    }

    @GetMapping("/history")
    @Operation(summary = "分页查询历史消息")
    public Result<List<MessageDTO>> getHistory(
            @Parameter(description = "会话 ID") @RequestParam String conversationId,
            @Parameter(description = "用户 ID") @RequestParam String userId,
            @Parameter(description = "页码（从 1 开始）") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        return messageService.getHistory(conversationId, userId, page, size);
    }

    @GetMapping("/sync")
    @Operation(summary = "增量同步消息（从指定序号之后拉取）")
    public Result<List<MessageDTO>> syncMessages(
            @Parameter(description = "会话 ID") @RequestParam String conversationId,
            @Parameter(description = "用户 ID") @RequestParam String userId,
            @Parameter(description = "上次拉取的最大序号") @RequestParam Long serverSeq,
            @Parameter(description = "拉取条数") @RequestParam(defaultValue = "50") int limit) {
        return messageService.getMessagesAfter(conversationId, userId, serverSeq, limit);
    }

    @GetMapping("/{messageId}")
    @Operation(summary = "根据消息 ID 查询消息详情")
    public Result<MessageDTO> getMessageById(
            @Parameter(description = "消息 ID") @PathVariable String messageId) {
        return messageService.getMessageById(messageId);
    }
}