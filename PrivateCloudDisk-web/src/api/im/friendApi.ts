import { del, get, post, put } from '@/utils/request'
import type { FriendDTO, FriendRequestDTO, PageResult, Result } from './types'

const IM_FRIENDS_BASE = 'im/friends'

/**
 * FRIEND-MANAGEMENT-20260810：好友 API 独立封装，所有调用显式传 userId。
 * 当前网关不会把用户主体注入 IM Platform，故不能在浏览器端假设后端可从 Token 自动取得 ID。
 */
export function createFriendRequestApi(userId: string, recipientId: string, message: string): Promise<Result<FriendRequestDTO>> {
  return post(`${IM_FRIENDS_BASE}/requests`, { requesterId: userId, recipientId, message }, { silent: true })
}

export function getIncomingFriendRequestsApi(userId: string, page = 1, size = 20): Promise<Result<PageResult<FriendRequestDTO>>> {
  return get(`${IM_FRIENDS_BASE}/requests/incoming`, { userId, page, size }, { silent: true })
}

export function getOutgoingFriendRequestsApi(userId: string, page = 1, size = 20): Promise<Result<PageResult<FriendRequestDTO>>> {
  return get(`${IM_FRIENDS_BASE}/requests/outgoing`, { userId, page, size }, { silent: true })
}

export function getPendingFriendRequestCountApi(userId: string): Promise<Result<number>> {
  return get(`${IM_FRIENDS_BASE}/requests/pending/count`, { userId }, { silent: true })
}

export function acceptFriendRequestApi(userId: string, requestId: string): Promise<Result<void>> {
  return put(`${IM_FRIENDS_BASE}/requests/${requestId}/accept`, null, { params: { userId }, silent: true })
}

export function rejectFriendRequestApi(userId: string, requestId: string, blockFuture = false): Promise<Result<void>> {
  return put(`${IM_FRIENDS_BASE}/requests/${requestId}/reject`, { blockFuture }, { params: { userId }, silent: true })
}

export function cancelFriendRequestApi(userId: string, requestId: string): Promise<Result<void>> {
  return del(`${IM_FRIENDS_BASE}/requests/${requestId}`, { params: { userId }, silent: true })
}

export function listFriendsApi(userId: string): Promise<Result<FriendDTO[]>> {
  return get(IM_FRIENDS_BASE, { userId }, { silent: true })
}

export function getFriendDetailApi(userId: string, friendId: string): Promise<Result<FriendDTO>> {
  return get(`${IM_FRIENDS_BASE}/${friendId}`, { userId }, { silent: true })
}

export function updateFriendRemarkApi(userId: string, friendId: string, remark: string): Promise<Result<void>> {
  return put(`${IM_FRIENDS_BASE}/${friendId}/remark`, { remark }, { params: { userId }, silent: true })
}

export function setFriendStarredApi(userId: string, friendId: string, starred: boolean): Promise<Result<void>> {
  return put(`${IM_FRIENDS_BASE}/${friendId}/star`, { starred }, { params: { userId }, silent: true })
}

export function deleteFriendApi(userId: string, friendId: string): Promise<Result<void>> {
  return del(`${IM_FRIENDS_BASE}/${friendId}`, { params: { userId }, silent: true })
}

export function blockFriendApi(userId: string, friendId: string): Promise<Result<void>> {
  return post(`${IM_FRIENDS_BASE}/${friendId}/block`, null, { params: { userId }, silent: true })
}

export function unblockFriendApi(userId: string, friendId: string): Promise<Result<void>> {
  return del(`${IM_FRIENDS_BASE}/${friendId}/block`, { params: { userId }, silent: true })
}

export function getFriendBlacklistApi(userId: string): Promise<Result<FriendDTO[]>> {
  return get(`${IM_FRIENDS_BASE}/blacklist`, { userId }, { silent: true })
}
