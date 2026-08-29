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

import { del, get, patch, post, put } from '@/utils/request'
import type {
  ConversationDTO,
  GroupDTO,
  GroupMemberDTO,
  MessageDTO,
  PresenceDTO,
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

/** 拉取当前用户离线消息（状态为 PREPARING，拉取后标记为已送达） */
export function getOfflineMessagesApi(
  userId: string,
  limit: number = 100,
): Promise<Result<MessageDTO[]>> {
  return get(`${IM_BASE}/messages/offline`, {
    userId,
    limit,
  }, { silent: true })
}

/**
 * 游标分页查询会话历史消息
 * 仅返回已送达/已读/失败终态，不含未送达（PREPARING）消息。
 * @param conversationId 会话 ID
 * @param userId 当前用户 ID
 * @param limit 每页条数（默认 20，最大 100）
 * @param cursor 上一页最小 server_seq（首次传 undefined）
 * @param before 可选，拉取该时间之前的消息
 */
export function getMessageHistoryByCursorApi(
  conversationId: string,
  userId: string,
  limit: number = 20,
  cursor?: number,
  before?: string,
  signal?: AbortSignal,
): Promise<Result<MessageDTO[]>> {
  const params: Record<string, string | number> = { conversationId, userId, limit }
  if (cursor !== undefined && cursor !== null) params.cursor = cursor
  if (before) params.before = before
  // AUDIT FIX [12.18] / IM-WEB-ENTERPRISE-20260809：会话切换时允许取消上一会话的历史请求，
  // 防止迟到响应覆盖当前窗口。新增参数为可选，保持原调用向后兼容。
  return get(`${IM_BASE}/messages/history/cursor`, params, { silent: true, signal })
}

// ==================== 会话 API ====================

/** 查询由好友接受/群组加入事务内部创建的既有会话。 */
export function getExistingConversationApi(
  userId: string,
  peerId: string,
  conversationType: number,
): Promise<Result<ConversationDTO>> {
  return get(`${IM_BASE}/conversations/peer`, { userId, peerId, conversationType }, { silent: true })
}

/** 获取会话列表 */
export function getConversationsApi(userId: string): Promise<Result<ConversationDTO[]>> {
  return get(`${IM_BASE}/conversations/list`, { userId }, { silent: true })
}

/** 获取会话详情 */
export function getConversationDetailApi(conversationId: string, userId: string): Promise<Result<ConversationDTO>> {
  return get(`${IM_BASE}/conversations/${conversationId}`, { userId }, { silent: true })
}

/** 置顶/取消置顶 */
export function toggleConversationTopApi(
  conversationId: string,
  userId: string,
  isTop: boolean,
): Promise<Result<void>> {
  // AUDIT FIX [3.4] / IM-WEB-ENTERPRISE-20260809：后端 ConversationController 使用 PUT。
  // 原 PATCH 会返回 405；新行为与服务端幂等更新语义一致。
  return put(`${IM_BASE}/conversations/${conversationId}/top`, null, {
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
  // AUDIT FIX [3.9] / IM-WEB-ENTERPRISE-20260809：与后端 @PutMapping 对齐。
  return put(`${IM_BASE}/conversations/${conversationId}/mute`, null, {
    params: { userId, isMuted },
    silent: true,
  })
}

/** 获取总未读数 */
export function getTotalUnreadCountApi(userId: string): Promise<Result<number>> {
  return get(`${IM_BASE}/conversations/unread/count`, { userId }, { silent: true })
}

/**
 * 批量查询用户在线状态。
 * PRIVATE-CHAT-20260810：V2 WebSocket 当前没有 presence payload，使用 IM Server
 * 心跳维护的 Redis TTL 映射由 Platform 统一读取；接口只返回状态，不返回内部节点信息。
 */
export function getPresenceApi(userIds: string[]): Promise<Result<Record<string, PresenceDTO>>> {
  return get(`${IM_BASE}/presence`, { userIds: userIds.join(',') }, { silent: true })
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
  return put(`${IM_BASE}/groups/${groupId}/mute-all`, null, {
    params: { operatorId, isAllMuted },
    silent: true,
  })
}

/** 解散群组 */
export function dissolveGroupApi(groupId: string, ownerId: string): Promise<Result<void>> {
  return del(`${IM_BASE}/groups/${groupId}/dissolve`, { ownerId }, { silent: true })
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
  return put(`${IM_BASE}/groups/${groupId}/announcement`, null, {
    params: { operatorId, announcement },
    silent: true,
  })
}

// ==================== 联系人/用户搜索 ====================

/** 搜索用户 */
export function searchUsersApi(keyword: string): Promise<Result<unknown[]>> {
  // AUDIT FIX [8.2,14.14] / IM-WEB-ENTERPRISE-20260809：IM Business 当前没有用户搜索
  // Controller。改为复用主业务服务现有的最小公开资料接口；该接口不返回手机号、邮箱等敏感字段。
  // 影响范围：新建聊天用户选择器。搜索结果仅代表平台用户，不宣称已经建立好友关系。
  return get('business/users/search', { q: keyword, limit: 20 }, { silent: true })
}

/** 发送好友申请 */
export function sendFriendRequestApi(
  requesterId: string,
  recipientId: string,
  message: string = '',
): Promise<Result<void>> {
  // FRIEND-MANAGEMENT-20260810：原实现请求不存在的 im/friend-requests，且仅有账号无法
  // 安全定位目标。改为兼容 IM Business 的 JSON 申请接口；调用方需先搜索并提供 userId。
  return post(`${IM_BASE}/friends/requests`, { requesterId, recipientId, message }, { silent: true })
}

/** 获取好友列表 */
export function getFriendsApi(userId: string): Promise<Result<unknown[]>> {
  return get(`${IM_BASE}/friends`, { userId }, { silent: true })
}

/** 删除好友 */
export function removeFriendApi(userId: string, friendId: string): Promise<Result<void>> {
  return del(`${IM_BASE}/friends/${friendId}`, { userId }, { silent: true })
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
