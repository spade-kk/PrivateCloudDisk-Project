package org.project.im.platform.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.project.im.common.dto.FriendDTO;
import org.project.im.common.dto.FriendRequestDTO;
import org.project.im.common.dto.PageResult;
import org.project.im.common.dto.Result;
import org.project.im.platform.dto.FriendRemarkCommand;
import org.project.im.platform.dto.FriendRequestCreateCommand;
import org.project.im.platform.dto.FriendRequestDecisionCommand;
import org.project.im.platform.dto.FriendStarCommand;
import org.project.im.platform.service.FriendshipService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 好友申请和联系人 HTTP 接口。
 *
 * <p>FRIEND-MANAGEMENT-20260810：新增 JSON 命令接口、详情/备注/星标/黑名单和分页查询；
 * 原有 Query 参数接口保留，防止已有客户端的好友申请与列表查询回归。</p>
 */
@RestController
@RequestMapping("/im/friends")
@RequiredArgsConstructor
public class FriendshipController {
    private final FriendshipService friendshipService;

    /** 新前端使用 JSON 体，验证信息限制在服务端 50 字符。 */
    @PostMapping(value = "/requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<FriendRequestDTO> requestJson(@Valid @RequestBody FriendRequestCreateCommand command) {
        return friendshipService.requestFriend(command.getRequesterId(), command.getRecipientId(), command.getMessage());
    }

    /** 旧客户端兼容接口；行为与 JSON 版本一致。 */
    @PostMapping(value = "/requests", params = {"requesterId", "recipientId"})
    public Result<FriendRequestDTO> request(@RequestParam String requesterId, @RequestParam String recipientId,
                                            @RequestParam(required = false) String verificationMessage) {
        return friendshipService.requestFriend(requesterId, recipientId, verificationMessage);
    }

    @PutMapping("/requests/{requestId}/accept")
    public Result<Void> accept(@PathVariable String requestId, @RequestParam String userId) {
        return friendshipService.acceptRequest(requestId, userId);
    }

    @PutMapping("/requests/{requestId}/reject")
    public Result<Void> reject(@PathVariable String requestId, @RequestParam String userId,
                               @RequestBody(required = false) FriendRequestDecisionCommand command) {
        return friendshipService.rejectRequest(requestId, userId, command != null && Boolean.TRUE.equals(command.getBlockFuture()));
    }

    @DeleteMapping("/requests/{requestId}")
    public Result<Void> cancel(@PathVariable String requestId, @RequestParam String userId) {
        return friendshipService.cancelRequest(requestId, userId);
    }

    @GetMapping("/requests/incoming")
    public Result<PageResult<FriendRequestDTO>> incoming(@RequestParam String userId, @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return friendshipService.getIncomingRequests(userId, page, size);
    }

    @GetMapping("/requests/outgoing")
    public Result<PageResult<FriendRequestDTO>> outgoing(@RequestParam String userId, @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return friendshipService.getOutgoingRequests(userId, page, size);
    }

    @GetMapping("/requests/pending/count")
    public Result<Long> pendingCount(@RequestParam String userId) { return friendshipService.getPendingRequestCount(userId); }

    /** 历史兼容：只返回待处理的收到申请。 */
    @GetMapping("/requests/pending")
    public Result<List<FriendRequestDTO>> pending(@RequestParam String userId) { return friendshipService.getPendingRequests(userId); }

    @GetMapping
    public Result<List<FriendDTO>> list(@RequestParam String userId) { return friendshipService.getFriends(userId); }

    @GetMapping("/{friendId}")
    public Result<FriendDTO> detail(@PathVariable String friendId, @RequestParam String userId) { return friendshipService.getFriendDetail(userId, friendId); }

    @PutMapping("/{friendId}/remark")
    public Result<Void> remark(@PathVariable String friendId, @RequestParam String userId, @Valid @RequestBody FriendRemarkCommand command) {
        return friendshipService.updateRemark(userId, friendId, command.getRemark());
    }

    @PutMapping("/{friendId}/star")
    public Result<Void> star(@PathVariable String friendId, @RequestParam String userId, @RequestBody(required = false) FriendStarCommand command) {
        return friendshipService.setStarred(userId, friendId, command != null && Boolean.TRUE.equals(command.getStarred()));
    }

    @PostMapping("/{friendId}/block")
    public Result<Void> block(@PathVariable String friendId, @RequestParam String userId) { return friendshipService.blockUser(userId, friendId); }

    @DeleteMapping("/{friendId}/block")
    public Result<Void> unblock(@PathVariable String friendId, @RequestParam String userId) { return friendshipService.unblockUser(userId, friendId); }

    @GetMapping("/blacklist")
    public Result<List<FriendDTO>> blacklist(@RequestParam String userId) { return friendshipService.getBlacklist(userId); }

    @DeleteMapping("/{friendId}")
    public Result<Void> remove(@PathVariable String friendId, @RequestParam String userId) { return friendshipService.removeFriend(userId, friendId); }
}
