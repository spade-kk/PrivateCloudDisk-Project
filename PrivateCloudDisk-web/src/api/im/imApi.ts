// ============================================================
// im/imApi.ts — IM HTTP REST API 模块
// ============================================================
// 封装 IM 业务平台（im-platform）的 HTTP REST API 调用。
// 覆盖消息、会话、群组、联系人等所有 IM 业务接口。
//
// 后端对应：
//   org.project.im.platform.controller.MessageController
//   org.project.im.platform.controller.ConversationController
//   org.project.im.platform.controller.GroupController
//   org.project.im.platform.service.*
// ============================================================

import { del, get, patch, post } from '@/utils/request'
import type {
  ConversationDTO,
  GroupDTO,
  GroupMemberDTO,
  MessageDTO,
  Result,
} from './types'

// ---- 基础路径 ----
// 注意：IM 路径前缀为 im，通过网关 /api/v1/im/** 路由。
// 网关会剥离 /api/v1/ 前缀，下游 IM Platform 收到 /im/xxx/xxx。
const IM_BASE = 'im'

// ==================== 消息 API ====================

/** 发送消息 */
export function sendMessageApi(message: Partial<MessageDTO>): Promise<Result<MessageDTO>> {
  return post(`${IM_BASE}/messages/send`, message, { silent: true })
}

/** 撤回消息 */
export function recallMessageApi(messageId: string, userId: string): Promise<Result<void>> {
  return post(`${IM_BASE}/messages/recall`, null, {
    params: { messageId, userId },
    silent: true,
  })
}

/** 标记消息已读 */
export function markMessageReadApi(conversationId: string, userId: string): Promise<Result<void>> {
  return post(`${IM_BASE}/messages/read`, null, {
    params: { conversationId, userId },
    silent: true,
  })
}

/** 分页查询历史消息 */
export function getMessageHistoryApi(
  conversationId: string,
  userId: string,
  page: number = 1,
  size: number = 20,
): Promise<Result<MessageDTO[]>> {
  return get(`${IM_BASE}/messages/history`, {
    conversationId,
    userId,
    page,
    size,
  }, { silent: true })
}

/** 增量同步消息 */
export function syncMessagesApi(
  conversationId: string,
  userId: string,
  serverSeq: number,
  limit: number = 50,
): Promise<Result<MessageDTO[]>> {
  return get(`${IM_BASE}/messages/sync`, {
    conversationId,
    userId,
    serverSeq,
    limit,
  }, { silent: true })
}

/** 根据消息 ID 查询 */
export function getMessageByIdApi(messageId: string): Promise<Result<MessageDTO>> {
  return get(`${IM_BASE}/messages/${messageId}`, {}, { silent: true })
}

// ==================== 会话 API ====================

/** 创建或获取会话 */
export function createConversationApi(
  userId: string,
  targetId: string,
  conversationType: number,
): Promise<Result<ConversationDTO>> {
  return post(`${IM_BASE}/conversations/create`, null, {
    params: { userId, targetId, conversationType },
    silent: true,
  })
}

/** 获取会话列表 */
export function getConversationsApi(userId: string): Promise<Result<ConversationDTO[]>> {
  return get(`${IM_BASE}/conversations/list`, { userId }, { silent: true })
}

/** 获取会话详情 */
export function getConversationDetailApi(conversationId: string): Promise<Result<ConversationDTO>> {
  return get(`${IM_BASE}/conversations/${conversationId}`, {}, { silent: true })
}

/** 删除会话 */
export function deleteConversationApi(conversationId: string, userId: string): Promise<Result<void>> {
  return del(`${IM_BASE}/conversations/${conversationId}`, {
    params: { userId },
    silent: true,
  })
}

/** 置顶/取消置顶 */
export function toggleConversationTopApi(
  conversationId: string,
  userId: string,
  isTop: boolean,
): Promise<Result<void>> {
  return patch(`${IM_BASE}/conversations/${conversationId}/top`, null, {
    params: { userId, isTop },
    silent: true,
  })
}

/** 免打扰/取消免打扰 */
export function toggleConversationMuteApi(
  conversationId: string,
  userId: string,
  isMuted: boolean,
): Promise<Result<void>> {
  return patch(`${IM_BASE}/conversations/${conversationId}/mute`, null, {
    params: { userId, isMuted },
    silent: true,
  })
}

/** 获取总未读数 */
export function getTotalUnreadCountApi(userId: string): Promise<Result<number>> {
  return get(`${IM_BASE}/conversations/unread/count`, { userId }, { silent: true })
}

// ==================== 群组 API ====================

/** 创建群组 */
export function createGroupApi(
  ownerId: string,
  groupName: string,
  avatar?: string,
): Promise<Result<GroupDTO>> {
  return post(`${IM_BASE}/groups/create`, null, {
    params: { ownerId, groupName, avatar },
    silent: true,
  })
}

/** 获取群组详情 */
export function getGroupDetailApi(groupId: string): Promise<Result<GroupDTO>> {
  return get(`${IM_BASE}/groups/${groupId}`, {}, { silent: true })
}

/** 获取用户群组列表 */
export function getUserGroupsApi(userId: string): Promise<Result<GroupDTO[]>> {
  return get(`${IM_BASE}/groups/user/${userId}`, {}, { silent: true })
}

/** 加入群组 */
export function joinGroupApi(groupId: string, userId: string): Promise<Result<void>> {
  return post(`${IM_BASE}/groups/${groupId}/join`, null, {
    params: { userId },
    silent: true,
  })
}

/** 退出群组 */
export function leaveGroupApi(groupId: string, userId: string): Promise<Result<void>> {
  return post(`${IM_BASE}/groups/${groupId}/leave`, null, {
    params: { userId },
    silent: true,
  })
}

/** 踢出成员 */
export function kickMemberApi(
  groupId: string,
  operatorId: string,
  targetUid: string,
): Promise<Result<void>> {
  return post(`${IM_BASE}/groups/${groupId}/kick`, null, {
    params: { operatorId, targetUid },
    silent: true,
  })
}

/** 禁言成员 */
export function muteMemberApi(
  groupId: string,
  operatorId: string,
  targetUid: string,
  durationMinutes: number,
): Promise<Result<void>> {
  return post(`${IM_BASE}/groups/${groupId}/mute`, null, {
    params: { operatorId, targetUid, durationMinutes },
    silent: true,
  })
}

/** 解除禁言 */
export function unmuteMemberApi(
  groupId: string,
  operatorId: string,
  targetUid: string,
): Promise<Result<void>> {
  return post(`${IM_BASE}/groups/${groupId}/unmute`, null, {
    params: { operatorId, targetUid },
    silent: true,
  })
}

/** 全员禁言/取消 */
export function toggleMuteAllApi(
  groupId: string,
  operatorId: string,
  isAllMuted: boolean,
): Promise<Result<void>> {
  return patch(`${IM_BASE}/groups/${groupId}/mute-all`, null, {
    params: { operatorId, isAllMuted },
    silent: true,
  })
}

/** 解散群组 */
export function dissolveGroupApi(groupId: string, ownerId: string): Promise<Result<void>> {
  return del(`${IM_BASE}/groups/${groupId}/dissolve`, {
    params: { ownerId },
    silent: true,
  })
}

/** 获取群成员列表 */
export function getGroupMembersApi(groupId: string): Promise<Result<GroupMemberDTO[]>> {
  return get(`${IM_BASE}/groups/${groupId}/members`, {}, { silent: true })
}

/** 更新群公告 */
export function updateGroupAnnouncementApi(
  groupId: string,
  operatorId: string,
  announcement: string,
): Promise<Result<void>> {
  return patch(`${IM_BASE}/groups/${groupId}/announcement`, null, {
    params: { operatorId, announcement },
    silent: true,
  })
}

// ==================== 联系人/用户搜索 ====================

/** 搜索用户 */
export function searchUsersApi(keyword: string): Promise<Result<unknown[]>> {
  return get(`${IM_BASE}/users/search`, { keyword }, { silent: true })
}

/** 发送好友申请 */
export function sendFriendRequestApi(
  account: string,
  remark: string = '',
): Promise<Result<void>> {
  return post(`${IM_BASE}/friend-requests`, { account, remark }, { silent: true })
}

/** 获取好友列表 */
export function getFriendsApi(): Promise<Result<unknown[]>> {
  return get(`${IM_BASE}/friends`, {}, { silent: true })
}

/** 删除好友 */
export function removeFriendApi(friendId: string): Promise<Result<void>> {
  return del(`${IM_BASE}/friends/${friendId}`, {}, { silent: true })
}

/** 获取通知列表 */
export function getNotificationsApi(): Promise<Result<unknown[]>> {
  return get(`${IM_BASE}/notifications`, {}, { silent: true })
}

/** 标记通知已读 */
export function markNotificationReadApi(id: string): Promise<Result<void>> {
  return patch(`${IM_BASE}/notifications/${id}/read`, {}, { silent: true })
}

/** 全部标记已读 */
export function markAllNotificationsReadApi(): Promise<Result<void>> {
  return patch(`${IM_BASE}/notifications/read-all`, {}, { silent: true })
}

// ==================== 健康检查 ====================

/** 健康检查 */
export function imHealthCheckApi(): Promise<Result<string>> {
  return get(`${IM_BASE}/health`, {}, { silent: true })
}