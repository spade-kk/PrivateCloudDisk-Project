package org.project.im.platform.service;

import org.project.im.common.dto.FriendDTO;
import org.project.im.common.dto.FriendRequestDTO;
import org.project.im.common.dto.PageResult;
import org.project.im.common.dto.Result;

import java.util.List;

/** 好友申请与关系服务。 */
public interface FriendshipService {
    Result<FriendRequestDTO> requestFriend(String requesterId, String recipientId, String verificationMessage);
    Result<Void> acceptRequest(String requestId, String recipientId);
    Result<Void> rejectRequest(String requestId, String recipientId);
    Result<List<FriendDTO>> getFriends(String userId);
    Result<List<FriendRequestDTO>> getPendingRequests(String recipientId);
    Result<Void> removeFriend(String userId, String friendId);
    Result<PageResult<FriendRequestDTO>> getIncomingRequests(String recipientId, int page, int size);
    Result<PageResult<FriendRequestDTO>> getOutgoingRequests(String requesterId, int page, int size);
    Result<Long> getPendingRequestCount(String recipientId);
    Result<Void> cancelRequest(String requestId, String requesterId);
    Result<Void> rejectRequest(String requestId, String recipientId, boolean blockFuture);
    Result<FriendDTO> getFriendDetail(String userId, String friendId);
    Result<Void> updateRemark(String userId, String friendId, String remark);
    Result<Void> setStarred(String userId, String friendId, boolean starred);
    Result<Void> blockUser(String userId, String friendId);
    Result<Void> unblockUser(String userId, String friendId);
    Result<List<FriendDTO>> getBlacklist(String userId);
    boolean isActiveFriend(String userId, String friendId);
}
