// ============================================================
// messageCenterStore.ts — 企业级消息中心独立状态域
// ============================================================
// [IM-WEB-ENTERPRISE-20260809]
// 改动原因：旧 notificationStore 同时管理平台通知、演示好友、会话、消息与通话，
// 在后端接口失败时仍返回演示数据或“本地成功”，无法保证企业消息中心的数据真实性。
// 新行为：该 Store 只管理 IM Business / IM Server 已实际提供的会话与消息能力；
// 平台用户搜索只用于新建会话，不伪装为好友关系。影响范围：新消息中心页面。
// 旧 Store 暂时保留，供现有调用和回溯使用。
// ============================================================

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getExistingConversationApi,
  getConversationsApi,
  getMessageHistoryByCursorApi,
  getPresenceApi,
  markMessageReadApi,
  recallMessageApi,
  sendMessageApi,
  toggleConversationMuteApi,
  toggleConversationTopApi,
} from '@/api/im/imApi'
import {
  blockFriendApi,
  deleteFriendApi,
  getFriendBlacklistApi,
  getPendingFriendRequestCountApi,
  listFriendsApi,
  setFriendStarredApi,
  unblockFriendApi,
  updateFriendRemarkApi,
} from '@/api/im/friendApi'
import { createGroupApi, dissolveGroupApi, leaveGroupApi, listGroupsApi } from '@/api/im/groupApi'
import {
  CommandType,
  ConnectionState,
  ConversationType,
  MessageStatus,
  MessageType,
  ReceiptStatus,
  destroyImClient,
  getImClient,
  type ConversationDTO,
  type GroupDTO,
  type ImWebSocketClient,
  type MessageDTO,
  type MessageProtocol,
  type ReceiptEvent,
} from '@/api/im'
import { useAuthStore } from '@/stores/authStore'
import * as messageCache from '@/utils/messageCache'
import { useImClient } from '@/composables/useImClient'

export type MessageDeliveryState = 'scheduled' | 'sending' | 'sent' | 'delivered' | 'read' | 'failed' | 'recalled'
export type MessageCenterConnectionState = 'offline' | 'connecting' | 'online' | 'reconnecting' | 'degraded' | 'kicked'

export interface MessageCenterConversation {
  id: string
  targetId: string
  title: string
  subtitle: string
  avatar?: string
  unread: number
  pinned: boolean
  muted: boolean
  updatedAt: number
  conversationType: ConversationType
  lastMessage: string
  lastMessageType: MessageType
  lastMessageStatus?: MessageDeliveryState
  online?: boolean
  onlineStatus?: 'online' | 'offline' | 'busy' | 'unknown'
  lastOnlineAt?: number
  typing?: boolean
  canSend: boolean
  sessionStatus: 'ACTIVE' | 'FRIEND_REMOVED' | 'GROUP_LEFT'
  /** 群聊未读中包含 @ 当前用户的消息时用于橙色提醒。 */
  mentioned?: boolean
}

export interface MessageCenterMessage {
  id: string
  clientId?: string
  conversationId: string
  sender: string
  senderName?: string
  senderAvatar?: string
  type: string
  content: string
  extra?: string
  payload?: Record<string, unknown>
  created_at: number
  status: MessageDeliveryState
  syncStatus?: 'synced' | 'syncing' | 'failed' | 'pending'
  serverSeq?: number
  replyTo?: string
  error?: string
  file_id?: string
  share_url?: string
  scheduledAt?: number
  readAt?: number
}

export interface PublicUserSearchResult {
  userId: string
  username?: string
  account?: string
  avatarPath?: string
}

export interface MessageCenterFriend {
  friendId: string
  username?: string
  account?: string
  avatarPath?: string
  remark?: string
  starred?: boolean
  online?: boolean
  commonSpaceCount?: number
  commonGroupCount?: number
  status: number
  createdAt?: string
}

/** 群列表视图模型：群资料来自群组 API，摘要/未读/置顶来自同一用户的会话缓存。 */
export interface MessageCenterGroup extends GroupDTO {
  lastMessage: string
  lastMessageTime: number
  unread: number
  pinned: boolean
  muted: boolean
  mentioned?: boolean
}

export interface SendMessageInput {
  conversationId?: string
  type?: string
  content: string
  payload?: Record<string, unknown>
  replyTo?: string
  transport?: 'http' | 'websocket'
  scheduledAt?: number
}

const DRAFT_KEY = 'pcd-im-drafts-v2'
const SCHEDULED_KEY = 'pcd-im-scheduled-v1'
const MAX_DEDUP_IDS = 5000
const HISTORY_PAGE_SIZE = 50

function parseObject(source?: string): Record<string, unknown> {
  if (!source) return {}
  try {
    const value = JSON.parse(source)
    return value && typeof value === 'object' && !Array.isArray(value)
      ? value as Record<string, unknown>
      : {}
  } catch {
    return {}
  }
}

function restStatus(status?: MessageStatus): MessageDeliveryState {
  switch (status) {
    case MessageStatus.PREPARING: return 'sent'
    case MessageStatus.DELIVERED: return 'delivered'
    case MessageStatus.READ: return 'read'
    case MessageStatus.FAILED: return 'failed'
    case MessageStatus.RECALLED: return 'recalled'
    default: return 'sent'
  }
}

function messageTypeName(type: MessageType): string {
  const names: Partial<Record<MessageType, string>> = {
    [MessageType.TEXT]: 'text',
    [MessageType.IMAGE]: 'image',
    [MessageType.FILE]: 'file',
    [MessageType.VOICE]: 'voice',
    [MessageType.VIDEO]: 'video',
    // AUDIT FIX [4.5/4.8] / IM-EMOJI-SESSION-20260810：平台表情使用协议既有
    // STICKER 类型，不再降级为 CUSTOM，确保 HTTP 与 WebSocket 历史消息均能重建贴纸负载。
    [MessageType.STICKER]: 'sticker',
    [MessageType.LOCATION]: 'location',
    [MessageType.SYSTEM_NOTICE]: 'system',
    [MessageType.CUSTOM]: 'custom',
    [MessageType.REPLY]: 'reply',
  }
  return names[type] || 'text'
}

function messageTypeCode(type: string): MessageType {
  const codes: Record<string, MessageType> = {
    text: MessageType.TEXT,
    image: MessageType.IMAGE,
    file: MessageType.FILE,
    voice: MessageType.VOICE,
    video: MessageType.VIDEO,
    sticker: MessageType.STICKER,
    location: MessageType.LOCATION,
    system: MessageType.SYSTEM_NOTICE,
    custom: MessageType.CUSTOM,
    reply: MessageType.REPLY,
    share: MessageType.CUSTOM,
    code: MessageType.TEXT,
  }
  return codes[type] || MessageType.TEXT
}

function normalizeWsUrl(raw: string): string {
  const value = raw.trim().replace(/\/+$/, '')
  if (/^wss?:\/\//i.test(value)) return value
  if (/^wss?:/i.test(value)) return value.replace(/^(wss?):/i, '$1://')
  return `${location.protocol === 'https:' ? 'wss' : 'ws'}://${value}`
}

export const useMessageCenterStore = defineStore('message-center', () => {
  const authStore = useAuthStore()
  const conversations = ref<MessageCenterConversation[]>([])
  const messages = ref<Record<string, MessageCenterMessage[]>>({})
  const activeConversationId = ref<string | null>(null)
  const connectionState = ref<MessageCenterConnectionState>('offline')
  const loadingConversations = ref(false)
  const loadingHistory = ref<Record<string, boolean>>({})
  const historyCursor = ref<Record<string, number | undefined>>({})
  const historyComplete = ref<Record<string, boolean>>({})
  const loadError = ref('')
  const syncProgress = ref<'idle' | 'syncing' | 'synced' | 'error'>('idle')
  const friends = ref<MessageCenterFriend[]>([])
  const blacklistedFriends = ref<MessageCenterFriend[]>([])
  const pendingFriendRequestCount = ref(0)
  const contactsLoading = ref(false)
  const groups = ref<MessageCenterGroup[]>([])
  const groupsLoading = ref(false)
  const drafts = ref<Record<string, string>>({})
  const typingUsers = ref<Record<string, string>>({})

  let client: ImWebSocketClient | null = null
  let friendPollingTimer: ReturnType<typeof setInterval> | null = null
  let groupPollingTimer: ReturnType<typeof setInterval> | null = null
  let presencePollingTimer: ReturnType<typeof setInterval> | null = null
  let activeHistoryController: AbortController | null = null
  let sendQueue: Promise<void> = Promise.resolve()
  let typingTimer: ReturnType<typeof setTimeout> | null = null
  let ownsRealtimeClient = false
  let realtimeWasOnline = false
  const realtimeUnsubscribers: Array<() => void> = []
  const typingTimers = new Map<string, ReturnType<typeof setTimeout>>()
  const seenMessageIds = new Set<string>()
  const scheduledTimers = new Map<string, ReturnType<typeof setTimeout>>()

  interface ScheduledRecord { id: string; dueAt: number; input: SendMessageInput }
  let scheduledRecords: ScheduledRecord[] = []

  const activeConversation = computed(() =>
    conversations.value.find(item => item.id === activeConversationId.value) || null)
  const activeMessages = computed(() => messages.value[activeConversationId.value || ''] || [])
  const totalUnread = computed(() => conversations.value.reduce((sum, item) => sum + item.unread, 0))
  const sortedConversations = computed(() => [...conversations.value].sort((a, b) =>
    Number(b.pinned) - Number(a.pinned) || b.updatedAt - a.updatedAt))
  const sortedGroups = computed(() => [...groups.value].sort((a, b) =>
    Number(b.pinned) - Number(a.pinned) || b.lastMessageTime - a.lastMessageTime))

  function currentUserId(): string {
    const userId = authStore.user.id?.trim()
    if (!userId) throw new Error('当前账号缺少用户 UUID，请重新登录后重试')
    return userId
  }

  async function ensureIdentity(): Promise<string> {
    if (!authStore.user.id) await authStore.fetchUserInfo()
    return currentUserId()
  }

  function restoreDrafts(): void {
    try { drafts.value = JSON.parse(localStorage.getItem(DRAFT_KEY) || '{}') } catch { drafts.value = {} }
  }

  function saveDraft(conversationId: string, value: string): void {
    drafts.value[conversationId] = value
    if (!value) delete drafts.value[conversationId]
    localStorage.setItem(DRAFT_KEY, JSON.stringify(drafts.value))
  }

  function conversationFromDto(dto: ConversationDTO): MessageCenterConversation {
    return {
      id: dto.conversationId,
      targetId: dto.targetId,
      title: dto.conversationName || dto.targetId,
      subtitle: dto.lastMessage || '',
      avatar: dto.avatar,
      unread: dto.unreadCount || 0,
      pinned: Boolean(dto.isTop),
      muted: Boolean(dto.isMuted),
      updatedAt: dto.lastMessageTime ? new Date(dto.lastMessageTime).getTime() : Date.now(),
      conversationType: dto.conversationType,
      lastMessage: dto.lastMessage || '',
      lastMessageType: dto.lastMessageType || MessageType.TEXT,
      onlineStatus: dto.conversationType === ConversationType.PRIVATE ? 'unknown' : undefined,
      canSend: dto.canSend !== false,
      sessionStatus: dto.sessionStatus || 'ACTIVE',
    }
  }

  function messageFromDto(dto: MessageDTO): MessageCenterMessage {
    const extra = parseObject(dto.extra)
    const payload = extra.payload && typeof extra.payload === 'object'
      ? extra.payload as Record<string, unknown>
      : extra
    return {
      id: dto.messageId || crypto.randomUUID(),
      conversationId: dto.conversationId,
      sender: dto.senderId === authStore.user.id ? 'me' : dto.senderId,
      senderName: dto.senderName,
      senderAvatar: dto.senderAvatar,
      type: messageTypeName(dto.messageType),
      content: dto.content || '',
      extra: dto.extra,
      payload,
      created_at: dto.sendTime ? new Date(dto.sendTime).getTime() : Date.now(),
      status: restStatus(dto.status),
      syncStatus: 'synced',
      serverSeq: dto.serverSeq,
      replyTo: dto.replyTo,
      file_id: typeof payload.diskFileId === 'string' ? payload.diskFileId : undefined,
    }
  }

  function rememberMessageId(id: string): boolean {
    if (!id || seenMessageIds.has(id)) return false
    seenMessageIds.add(id)
    if (seenMessageIds.size > MAX_DEDUP_IDS) {
      const firstHalf = [...seenMessageIds].slice(0, Math.floor(MAX_DEDUP_IDS / 2))
      firstHalf.forEach(value => seenMessageIds.delete(value))
    }
    return true
  }

  function mergeMessages(conversationId: string, incoming: MessageCenterMessage[], prepend = false): void {
    const existing = messages.value[conversationId] || []
    existing.forEach(item => seenMessageIds.add(item.id))
    const unique = incoming.filter(item => rememberMessageId(item.id))
    const merged = prepend ? [...unique, ...existing] : [...existing, ...unique]
    messages.value[conversationId] = merged.sort((a, b) =>
      (a.serverSeq || 0) - (b.serverSeq || 0) || a.created_at - b.created_at)
    messageCache.saveMessages(conversationId, messages.value[conversationId])
  }

  async function withRetry<T>(operation: () => Promise<T>, attempts = 3): Promise<T> {
    let lastError: unknown
    for (let attempt = 0; attempt < attempts; attempt++) {
      try { return await operation() } catch (error) {
        lastError = error
        if (attempt < attempts - 1) {
          await new Promise(resolve => setTimeout(resolve, 250 * 2 ** attempt + Math.random() * 150))
        }
      }
    }
    throw lastError
  }

  async function bootstrap(): Promise<void> {
    restoreDrafts()
    loadError.value = ''
    try {
      await ensureIdentity()
    } catch (error) {
      loadError.value = error instanceof Error ? error.message : '消息中心初始化失败'
      return
    }

    // AUDIT FIX [1.1/1.2/1.3] / IM-MESSAGE-CENTER-20260811：会话是首屏主链路，好友、
    // 黑名单申请数和群组是增强链路。原实现把所有请求放进同一个 try，任一增强接口
    // 失败都会跳过 WebSocket 绑定和首个会话打开；新行为先完成会话，再隔离加载增强数据。
    // 影响范围：消息中心首次进入、刷新、共享 IM 客户端复用，不改变任何后端 API。
    try {
      await fetchConversations()
    } catch (error) {
      loadError.value = error instanceof Error ? error.message : '会话列表加载失败'
    }

    await Promise.allSettled([
      fetchGroups(),
      fetchFriends(),
    ])
    restoreScheduledMessages()
    connectRealtime()
    startPresencePolling()
    // AUDIT FIX [1.4] / IM-MESSAGE-CENTER-20260811：Store 在离开/重新进入路由时会
    // 保留 Pinia 状态。若此前选中的会话已被服务端隐藏或被当前列表替换，旧的
    // activeConversationId 会让“有会话但聊天区为空”的状态阻止首个会话打开。
    if (activeConversationId.value && !conversations.value.some(item => item.id === activeConversationId.value)) {
      activeConversationId.value = null
    }
    if (!activeConversationId.value && conversations.value[0]) {
      await openConversation(conversations.value[0].id)
    }
  }

  async function fetchConversations(): Promise<void> {
    loadingConversations.value = true
    try {
      const response = await withRetry(() => getConversationsApi(currentUserId()))
      if (response.code !== 200) throw new Error(response.message || '会话列表加载失败')
      conversations.value = (response.data || []).map(conversationFromDto)
      await refreshPresence()
    } finally {
      loadingConversations.value = false
    }
  }

  /**
   * PRIVATE-CHAT-20260810 [2.3/2.24/3.3]：仅刷新单聊对端状态，避免把群成员在线
   * 误当作单聊 presence。接口失败时保留上次状态，不阻断会话和消息主链路。
   */
  async function refreshPresence(): Promise<void> {
    const userIds = conversations.value
      .filter(item => item.conversationType === ConversationType.PRIVATE)
      .map(item => item.targetId)
      .filter(Boolean)
    if (!userIds.length) return
    try {
      const response = await getPresenceApi([...new Set(userIds)].slice(0, 100))
      if (response.code !== 200 || !response.data) return
      conversations.value.forEach(conversation => {
        if (conversation.conversationType !== ConversationType.PRIVATE) return
        const status = response.data?.[conversation.targetId]?.status || 'unknown'
        conversation.onlineStatus = status
        conversation.online = status === 'online'
      })
      friends.value.forEach(friend => {
        const status = response.data?.[friend.friendId]?.status
        if (status) friend.online = status === 'online'
      })
    } catch { /* presence 是增强信息，故障时不能影响消息收发。 */ }
  }

  function startPresencePolling(): void {
    if (presencePollingTimer) return
    void refreshPresence()
    presencePollingTimer = setInterval(() => void refreshPresence(), 3000)
  }

  function stopPresencePolling(): void {
    if (presencePollingTimer) clearInterval(presencePollingTimer)
    presencePollingTimer = null
  }

  async function fetchFriends(): Promise<void> {
    contactsLoading.value = true
    try {
      const userId = currentUserId()
      // 好友主列表必须独立于黑名单和申请数。某个历史部署尚未提供增强端点时，
      // 原 Promise.all 会连好友列表也一并丢弃，造成联系人 Tab 看似“没有数据”。
      const [friendsResult, blacklistResult, countResult] = await Promise.allSettled([
        withRetry(() => listFriendsApi(userId)),
        withRetry(() => getFriendBlacklistApi(userId)),
        withRetry(() => getPendingFriendRequestCountApi(userId)),
      ])
      if (friendsResult.status === 'rejected') throw friendsResult.reason
      const response = friendsResult.value
      if (response.code !== 200) throw new Error(response.message || '好友列表加载失败')
      friends.value = (response.data || []).filter((item): item is MessageCenterFriend =>
        Boolean(item) && typeof (item as MessageCenterFriend).friendId === 'string')
      if (blacklistResult.status === 'fulfilled' && blacklistResult.value.code === 200 && Array.isArray(blacklistResult.value.data)) {
        blacklistedFriends.value = blacklistResult.value.data
      }
      if (countResult.status === 'fulfilled' && countResult.value.code === 200) {
        pendingFriendRequestCount.value = Number(countResult.value.data || 0)
      }
    } finally {
      contactsLoading.value = false
    }
  }

  /**
   * GROUP-CHAT-20260810 [2.2/2.12/2.25]：群资料使用 HTTP 拉取，摘要与未读严格复用
   * ConversationService 的 Redis 组装结果。当前 V2 Protobuf 未定义群资料变更事件，不能
   * 向既有二进制通道塞入 JSON；在线时由低频轮询补齐资料/成员变动，消息仍实时走 WebSocket。
   */
  async function fetchGroups(): Promise<void> {
    groupsLoading.value = true
    try {
      const response = await withRetry(() => listGroupsApi(currentUserId(), 1, 100))
      if (response.code !== 200) throw new Error(response.message || '群组列表加载失败')
      // 兼容分页根接口和旧版直接返回数组的服务端响应，避免数据已返回但 UI 被
      // `data.items` 的固定形状误判为空。
      const data = response.data as unknown as { items?: GroupDTO[] } | GroupDTO[] | undefined
      const items = Array.isArray(data) ? data : data?.items || []
      groups.value = items.map(group => mergeGroupConversation(group))
    } finally {
      groupsLoading.value = false
    }
  }

  function mergeGroupConversation(group: GroupDTO): MessageCenterGroup {
    const conversation = conversations.value.find(item => item.targetId === group.groupId && item.conversationType === ConversationType.GROUP)
    return {
      ...group,
      lastMessage: conversation?.lastMessage || '',
      lastMessageTime: conversation?.updatedAt || new Date(group.updateTime || group.createTime || Date.now()).getTime(),
      unread: conversation?.unread || 0,
      pinned: conversation?.pinned || false,
      muted: conversation?.muted || false,
    }
  }

  function syncGroupConversation(groupId: string, conversation?: MessageCenterConversation): void {
    const group = groups.value.find(item => item.groupId === groupId)
    if (!group) return
    const source = conversation || conversations.value.find(item => item.targetId === groupId && item.conversationType === ConversationType.GROUP)
    if (!source) return
    group.lastMessage = source.lastMessage
    group.lastMessageTime = source.updatedAt
    group.unread = source.unread
    group.pinned = source.pinned
    group.muted = source.muted
  }

  async function openGroupConversation(groupId: string): Promise<string> {
    const existing = conversations.value.find(item => item.targetId === groupId && item.conversationType === ConversationType.GROUP)
    if (existing) { await openConversation(existing.id); return existing.id }
    const response = await getExistingConversationApi(currentUserId(), groupId, ConversationType.GROUP)
    if (response.code !== 200 || !response.data) throw new Error(response.message || '群会话尚未同步完成')
    const conversation = conversationFromDto(response.data)
    conversations.value.unshift(conversation)
    await openConversation(conversation.id)
    return conversation.id
  }

  async function createGroup(groupName: string, memberIds: string[], avatarFileId?: string): Promise<MessageCenterGroup> {
    const response = await createGroupApi({ ownerId: currentUserId(), groupName, memberIds, avatarFileId })
    if (response.code !== 200 || !response.data) throw new Error(response.message || '创建群聊失败')
    const group = mergeGroupConversation(response.data)
    groups.value = [group, ...groups.value.filter(item => item.groupId !== group.groupId)]
    await openGroupConversation(group.groupId)
    return group
  }

  async function leaveGroup(groupId: string): Promise<void> {
    const response = await leaveGroupApi(groupId, currentUserId())
    if (response.code !== 200) throw new Error(response.message || '退出群聊失败')
    groups.value = groups.value.filter(item => item.groupId !== groupId)
    await fetchConversations()
  }

  async function dissolveGroup(groupId: string): Promise<void> {
    const response = await dissolveGroupApi(groupId, currentUserId())
    if (response.code !== 200) throw new Error(response.message || '解散群聊失败')
    groups.value = groups.value.filter(item => item.groupId !== groupId)
    await fetchConversations()
  }

  function startGroupPolling(): void {
    if (groupPollingTimer) return
    groupPollingTimer = setInterval(() => { if (connectionState.value === 'online') void fetchGroups().catch(() => {}) }, 30_000)
  }

  function stopGroupPolling(): void {
    if (groupPollingTimer) clearInterval(groupPollingTimer)
    groupPollingTimer = null
  }

  /**
   * FRIEND-MANAGEMENT-20260810 [3.11/5.19]：当前 V2 Protobuf 协议未定义好友事件。
   * 原行为若伪造二进制事件会被 IM Server 拒绝；新行为使用 30 秒低频 HTTP 轮询申请数，
   * 不干扰消息 WebSocket 的编码、心跳或收发状态机。
   */
  async function refreshPendingFriendRequestCount(): Promise<void> {
    try {
      const response = await getPendingFriendRequestCountApi(currentUserId())
      if (response.code === 200) pendingFriendRequestCount.value = Number(response.data || 0)
    } catch { /* 离线时保持最后可用红点，不影响消息中心核心功能。 */ }
  }

  function startFriendRequestPolling(): void {
    if (friendPollingTimer) return
    void refreshPendingFriendRequestCount()
    friendPollingTimer = setInterval(() => void refreshPendingFriendRequestCount(), 30_000)
  }

  function stopFriendRequestPolling(): void {
    if (friendPollingTimer) clearInterval(friendPollingTimer)
    friendPollingTimer = null
  }

  async function updateFriendRemark(friendId: string, remark: string): Promise<void> {
    const response = await updateFriendRemarkApi(currentUserId(), friendId, remark)
    if (response.code !== 200) throw new Error(response.message || '更新备注失败')
    const friend = friends.value.find(item => item.friendId === friendId)
    if (friend) friend.remark = remark
  }

  async function toggleFriendStar(friendId: string, starred: boolean): Promise<void> {
    const response = await setFriendStarredApi(currentUserId(), friendId, starred)
    if (response.code !== 200) throw new Error(response.message || '更新星标失败')
    const friend = friends.value.find(item => item.friendId === friendId)
    if (friend) friend.starred = starred
  }

  async function removeFriend(friendId: string): Promise<void> {
    const response = await deleteFriendApi(currentUserId(), friendId)
    if (response.code !== 200) throw new Error(response.message || '删除好友失败')
    friends.value = friends.value.filter(item => item.friendId !== friendId)
    await fetchConversations()
  }

  async function blockFriend(friendId: string): Promise<void> {
    const response = await blockFriendApi(currentUserId(), friendId)
    if (response.code !== 200) throw new Error(response.message || '拉黑失败')
    await fetchFriends()
    await fetchConversations()
  }

  async function unblockFriend(friendId: string): Promise<void> {
    const response = await unblockFriendApi(currentUserId(), friendId)
    if (response.code !== 200) throw new Error(response.message || '取消拉黑失败')
    await fetchFriends()
  }

  async function openConversation(conversationId: string): Promise<void> {
    activeHistoryController?.abort()
    activeHistoryController = new AbortController()
    activeConversationId.value = conversationId
    const conversation = conversations.value.find(item => item.id === conversationId)
    if (conversation) { conversation.unread = 0; conversation.mentioned = false }
    if (conversation?.conversationType === ConversationType.GROUP) syncGroupConversation(conversation.targetId, conversation)
    syncProgress.value = 'syncing'

    try {
      const cached = await messageCache.loadMessages(conversationId)
      if (cached.length) {
        messages.value[conversationId] = cached.map(item => ({
          ...item,
          conversationId,
          status: item.status as MessageDeliveryState,
        }))
      }
    } catch { /* 缓存损坏不阻断在线链路 */ }

    try {
      const response = await getMessageHistoryByCursorApi(
        conversationId, currentUserId(), HISTORY_PAGE_SIZE, undefined, undefined,
        activeHistoryController.signal,
      )
      if (response.code !== 200) throw new Error(response.message || '历史消息加载失败')
      const normalized = (response.data || []).map(messageFromDto)
      // 不清空当前数组：首屏历史请求期间可能已经收到 WebSocket 推送。旧行为会用
      // 历史响应覆盖实时消息，表现为“调试面板收到二进制帧但聊天窗口没有消息”。
      // 新行为由 mergeMessages 按 messageId 去重并按 serverSeq/时间排序。
      mergeMessages(conversationId, normalized)
      const seqs = normalized.map(item => item.serverSeq).filter((value): value is number => typeof value === 'number')
      historyCursor.value[conversationId] = seqs.length ? Math.min(...seqs) : undefined
      historyComplete.value[conversationId] = normalized.length < HISTORY_PAGE_SIZE
      syncProgress.value = 'synced'
    } catch (error) {
      if ((error as { name?: string }).name !== 'CanceledError' && (error as { name?: string }).name !== 'AbortError') {
        syncProgress.value = messages.value[conversationId]?.length ? 'synced' : 'error'
      }
    }

    const ids = (messages.value[conversationId] || []).filter(item => item.sender !== 'me').map(item => item.id)
    if (client?.isConnected) client.sendReadReceipt(conversationId, ids, conversation?.targetId).catch(() => {})
    markMessageReadApi(conversationId, currentUserId()).catch(() => {})
  }

  /** PRIVATE-CHAT-20260810 [2.11/2.21]：批量标记已读必须同步后端，不能只修改列表红点。 */
  async function markConversationRead(conversation: MessageCenterConversation): Promise<void> {
    conversation.unread = 0
    conversation.mentioned = false
    await markMessageReadApi(conversation.id, currentUserId())
  }

  async function loadOlderMessages(): Promise<void> {
    const conversationId = activeConversationId.value
    if (!conversationId || loadingHistory.value[conversationId] || historyComplete.value[conversationId]) return
    loadingHistory.value[conversationId] = true
    try {
      const response = await getMessageHistoryByCursorApi(
        conversationId, currentUserId(), HISTORY_PAGE_SIZE, historyCursor.value[conversationId],
      )
      if (response.code !== 200) throw new Error(response.message || '历史消息加载失败')
      const normalized = (response.data || []).map(messageFromDto)
      mergeMessages(conversationId, normalized, true)
      const seqs = normalized.map(item => item.serverSeq).filter((value): value is number => typeof value === 'number')
      if (seqs.length) historyCursor.value[conversationId] = Math.min(...seqs)
      historyComplete.value[conversationId] = normalized.length < HISTORY_PAGE_SIZE
    } finally {
      loadingHistory.value[conversationId] = false
    }
  }

  async function openFriendConversation(friendId: string): Promise<string> {
    const existing = conversations.value.find(item => item.targetId === friendId && item.conversationType === ConversationType.PRIVATE)
    if (existing) {
      await openConversation(existing.id)
      return existing.id
    }
    // AUDIT FIX [5.5/5.10] / IM-EMOJI-SESSION-20260810：好友点击只能查询接受
    // 申请事务已创建的会话；不再由前端调用会话创建接口。
    const response = await getExistingConversationApi(currentUserId(), friendId, ConversationType.PRIVATE)
    if (response.code !== 200 || !response.data) throw new Error(response.message || '好友会话尚未同步完成')
    const conversation = conversationFromDto(response.data)
    conversations.value.unshift(conversation)
    await openConversation(conversation.id)
    return conversation.id
  }

  async function sendMessage(input: SendMessageInput): Promise<MessageCenterMessage | null> {
    const conversationId = input.conversationId || activeConversationId.value
    const content = input.content.trim()
    if (!conversationId || (!content && !input.payload)) return null
    const conversation = conversations.value.find(item => item.id === conversationId)
    if (!conversation) throw new Error('会话不存在')
    if (!conversation.canSend) throw new Error(conversation.sessionStatus === 'GROUP_LEFT' ? '你已退出该群组，无法发送消息' : '好友关系已解除，无法发送消息')

    if (input.scheduledAt && input.scheduledAt > Date.now()) {
      // AUDIT FIX [10.5/10.6]：定时发送不再只是装饰性日期控件。待发送参数持久化到
      // localStorage，刷新页面后恢复计时；到期才进入既有串行发送队列。附件在排程前已完成
      // 网盘上传，因此这里只保存文件 ID 与消息元数据，不持久化 Blob/File。
      const record: ScheduledRecord = {
        id: `scheduled-${crypto.randomUUID()}`,
        dueAt: input.scheduledAt,
        input: { ...input, conversationId, scheduledAt: undefined },
      }
      scheduledRecords.push(record)
      persistScheduledMessages()
      const localMessage: MessageCenterMessage = {
        id: record.id,
        conversationId,
        sender: 'me',
        senderName: authStore.displayName,
        type: input.type || 'text',
        content,
        payload: input.payload,
        created_at: Date.now(),
        status: 'scheduled',
        syncStatus: 'pending',
        replyTo: input.replyTo,
        scheduledAt: input.scheduledAt,
      }
      messages.value[conversationId] = [...(messages.value[conversationId] || []), localMessage]
      await messageCache.saveMessage(conversationId, localMessage)
      armScheduledMessage(record)
      return localMessage
    }

    const localMessage: MessageCenterMessage = {
      id: `client-${crypto.randomUUID()}`,
      clientId: crypto.randomUUID(),
      conversationId,
      sender: 'me',
      senderName: authStore.displayName,
      type: input.type || 'text',
      content,
      payload: input.payload,
      extra: JSON.stringify({ payload: input.payload || {}, clientMessageId: crypto.randomUUID() }),
      created_at: Date.now(),
      status: 'sending',
      syncStatus: 'syncing',
      replyTo: input.replyTo,
    }
    messages.value[conversationId] = [...(messages.value[conversationId] || []), localMessage]
    conversation.lastMessage = content || `[${localMessage.type}]`
    conversation.lastMessageType = messageTypeCode(localMessage.type)
    conversation.lastMessageStatus = 'sending'
    conversation.updatedAt = localMessage.created_at
    if (conversation.conversationType === ConversationType.GROUP) syncGroupConversation(conversation.targetId, conversation)
    messageCache.saveMessage(conversationId, localMessage)

    sendQueue = sendQueue.then(async () => {
      const dto: MessageDTO = {
        conversationId,
        conversationType: conversation.conversationType,
        messageType: messageTypeCode(localMessage.type),
        senderId: currentUserId(),
        receiverId: conversation.targetId,
        content: content || `[${localMessage.type}]`,
        extra: localMessage.extra,
        replyTo: input.replyTo,
      }
      try {
        if (input.transport === 'websocket' && client?.isConnected) {
          await client.sendMessage(dto)
          localMessage.status = 'sent'
          localMessage.syncStatus = 'pending'
          conversation.lastMessageStatus = 'sent'
        } else {
          // HTTP 是当前后端唯一会返回持久化 Snowflake ID 的发送通道；默认使用它保证
          // 本地状态与后续 Router 回执可关联。WebSocket 发送仍通过 transport 显式可用。
          const response = await withRetry(() => sendMessageApi(dto))
          if (response.code !== 200 || !response.data) throw new Error(response.message || '消息发送失败')
          const oldId = localMessage.id
          localMessage.id = response.data.messageId || oldId
          localMessage.serverSeq = response.data.serverSeq
          localMessage.status = 'sent'
          localMessage.syncStatus = 'synced'
          conversation.lastMessageStatus = 'sent'
          await messageCache.deleteMessage(conversationId, oldId)
        }
        await messageCache.saveMessage(conversationId, localMessage)
      } catch (error) {
        localMessage.status = 'failed'
        localMessage.syncStatus = 'failed'
        localMessage.error = error instanceof Error ? error.message : '发送失败'
        conversation.lastMessageStatus = 'failed'
        await messageCache.updateMessageStatus(conversationId, localMessage.id, 'failed', 'failed')
      }
    })
    await sendQueue
    return localMessage
  }

  function persistScheduledMessages(): void {
    localStorage.setItem(SCHEDULED_KEY, JSON.stringify(scheduledRecords))
  }

  function restoreScheduledMessages(): void {
    try {
      const parsed = JSON.parse(localStorage.getItem(SCHEDULED_KEY) || '[]')
      scheduledRecords = Array.isArray(parsed) ? parsed.filter(record =>
        record && typeof record.id === 'string' && typeof record.dueAt === 'number' && record.input) : []
    } catch { scheduledRecords = [] }
    scheduledRecords.forEach(armScheduledMessage)
  }

  function armScheduledMessage(record: ScheduledRecord): void {
    scheduledTimers.get(record.id) && clearTimeout(scheduledTimers.get(record.id))
    const delay = Math.max(0, Math.min(record.dueAt - Date.now(), 2_147_000_000))
    scheduledTimers.set(record.id, setTimeout(() => dispatchScheduledMessage(record.id), delay))
  }

  async function dispatchScheduledMessage(recordId: string): Promise<void> {
    const record = scheduledRecords.find(item => item.id === recordId)
    if (!record) return
    if (record.dueAt > Date.now() + 1000) {
      armScheduledMessage(record)
      return
    }
    scheduledTimers.delete(recordId)
    scheduledRecords = scheduledRecords.filter(item => item.id !== recordId)
    persistScheduledMessages()
    const conversationId = record.input.conversationId || ''
    messages.value[conversationId] = (messages.value[conversationId] || []).filter(item => item.id !== recordId)
    await messageCache.deleteMessage(conversationId, recordId)
    try {
      await sendMessage(record.input)
    } catch (error) {
      loadError.value = error instanceof Error ? error.message : '定时消息发送失败'
    }
  }

  async function retryMessage(conversationId: string, messageId: string): Promise<void> {
    const source = messages.value[conversationId]?.find(item => item.id === messageId)
    if (!source || source.status !== 'failed') return
    source.status = 'sending'
    source.syncStatus = 'syncing'
    const replacement = await sendMessage({
      conversationId,
      type: source.type,
      content: source.content,
      payload: source.payload,
      replyTo: source.replyTo,
    })
    if (replacement?.status !== 'failed') {
      messages.value[conversationId] = messages.value[conversationId].filter(item => item.id !== messageId)
      await messageCache.deleteMessage(conversationId, messageId)
    } else {
      source.status = 'failed'
      source.syncStatus = 'failed'
    }
  }

  async function recallMessage(messageId: string): Promise<void> {
    const response = await recallMessageApi(messageId, currentUserId())
    if (response.code !== 200) throw new Error(response.message || '撤回失败')
    Object.values(messages.value).forEach(list => {
      const message = list.find(item => item.id === messageId)
      if (message) {
        message.status = 'recalled'
        message.content = '你撤回了一条消息'
      }
    })
  }

  async function togglePinned(conversation: MessageCenterConversation): Promise<void> {
    const next = !conversation.pinned
    const response = await toggleConversationTopApi(conversation.id, currentUserId(), next)
    if (response.code !== 200) throw new Error(response.message || '置顶设置失败')
    conversation.pinned = next
  }

  async function toggleMuted(conversation: MessageCenterConversation): Promise<void> {
    const next = !conversation.muted
    const response = await toggleConversationMuteApi(conversation.id, currentUserId(), next)
    if (response.code !== 200) throw new Error(response.message || '免打扰设置失败')
    conversation.muted = next
  }


  function sendTyping(isTyping = true): void {
    const conversation = activeConversation.value
    if (!conversation || !client?.isConnected) return
    if (typingTimer) clearTimeout(typingTimer)
    client.sendTyping(conversation.targetId, conversation.id, isTyping).catch(() => {})
    if (isTyping) {
      typingTimer = setTimeout(() => {
        typingTimer = null
        if (client?.isConnected && activeConversationId.value === conversation.id) {
          client.sendTyping(conversation.targetId, conversation.id, false).catch(() => {})
        }
      }, 3000)
    } else {
      typingTimer = null
    }
  }

  function handleIncoming(protocol: MessageProtocol): void {
    if (protocol.command === CommandType.TYPING) {
      const data = protocol.payload as { conversationId?: string; isTyping?: boolean } | undefined
      if (data?.conversationId) {
        const conversationId = data.conversationId
        const existingTimer = typingTimers.get(conversationId)
        if (existingTimer) clearTimeout(existingTimer)
        if (data.isTyping === false) {
          delete typingUsers.value[conversationId]
          typingTimers.delete(conversationId)
        } else {
          typingUsers.value[conversationId] = protocol.senderId || '对方'
          typingTimers.set(conversationId, setTimeout(() => {
            delete typingUsers.value[conversationId]
            typingTimers.delete(conversationId)
          }, 3000))
        }
      }
      return
    }

    // AUDIT FIX [3.3/14.8/14.25] / IM-MESSAGE-CENTER-20260811：统一消费 SDK 的
    // onMessage 入口，而不是另行注册一套只覆盖部分场景的 onCommand 处理器。实时推送、
    // HTTP 离线同步和历史补拉最终都使用同一套 MessageProtocol；聊天消息仍限定为
    // SEND_MESSAGE，避免把 CALL/SIGNALING Envelope 误渲染进聊天。payload 解析失败时，
    // Envelope 元数据仍可保证消息进入正确会话，后续历史接口再补齐正文。
    const raw = protocol.payload && typeof protocol.payload === 'object'
      ? protocol.payload as Record<string, unknown> : {}
    const readString = (value: unknown): string => typeof value === 'string' ? value : ''
    // 仅 SEND_MESSAGE 是聊天消息。CALL_* / SIGNALING_* 也带有 Envelope messageId
    // 和 conversationId，但它们由 useCall 的命令回调处理，不能被消息中心误渲染为一条
    // “[无法解码的消息]”。
    if (protocol.command !== CommandType.SEND_MESSAGE) return
    const messageId = protocol.messageId || readString(raw.messageId)
    const conversationId = protocol.conversationId || readString(raw.conversationId)
    const senderId = protocol.senderId || readString(raw.senderId)
    const receiverId = protocol.receiverId || readString(raw.receiverId)
    const messageType = Number(raw.messageType ?? protocol.messageType ?? MessageType.TEXT) as MessageType
    if (!conversationId || !messageId) return
    const dto: MessageDTO = {
      messageId,
      conversationId,
      conversationType: protocol.conversationType ?? Number(raw.conversationType || ConversationType.PRIVATE) as ConversationType,
      messageType,
      senderId,
      receiverId,
      senderName: readString(raw.senderName) || undefined,
      senderAvatar: readString(raw.senderAvatar) || undefined,
      content: readString(raw.content) || (messageType === MessageType.TEXT ? '[无法解码的消息]' : '[消息]'),
      extra: readString(raw.extra) || undefined,
      serverSeq: protocol.serverSeq ?? (typeof raw.serverSeq === 'number' ? raw.serverSeq : undefined),
      sendTime: new Date(protocol.timestamp || Date.now()).toISOString(),
    }
    const message = messageFromDto(dto)
    mergeMessages(dto.conversationId, [message])
    const conversation = conversations.value.find(item => item.id === dto.conversationId)
    if (conversation) {
      // 群会话摘要保留发送者前缀，便于会话列表在多人消息中识别来源。
      conversation.lastMessage = conversation.conversationType === ConversationType.GROUP && message.sender !== 'me'
        ? `${message.senderName || '成员'}: ${message.content}` : message.content
      conversation.lastMessageType = dto.messageType
      conversation.updatedAt = message.created_at
      if (activeConversationId.value !== conversation.id && message.sender !== 'me') {
        conversation.unread++
        if (conversation.conversationType === ConversationType.GROUP) {
          const nickname = authStore.displayName?.trim()
          // @ 提及当前采用稳定的文本标记；群消息仍由原 V2 TextPayload 传输，待协议新增
          // mentions 字段后可无缝改为结构化列表而不影响会话排序和未读计数。
          conversation.mentioned = Boolean(nickname && new RegExp(`(^|\\s)@${escapeRegExp(nickname)}(?:\\s|$)`).test(message.content))
        }
      }
      if (conversation.conversationType === ConversationType.GROUP) syncGroupConversation(conversation.targetId, conversation)
    } else {
      fetchConversations().then(() => {
        if (!activeConversationId.value && conversations.value.some(item => item.id === dto.conversationId)) {
          void openConversation(dto.conversationId)
        }
      }).catch(() => {})
    }
  }

  function handleReceipt(receipt: ReceiptEvent): void {
    for (const [conversationId, list] of Object.entries(messages.value)) {
      const message = list.find(item => item.id === receipt.originalMessageId)
      if (!message) continue
      const failed = receipt.status === ReceiptStatus.PUSH_FAILED || receipt.status === ReceiptStatus.SEND_FAILED
      message.status = failed ? 'failed' : 'delivered'
      message.syncStatus = failed ? 'failed' : 'synced'
      message.error = failed ? receipt.failReason : undefined
      const conversation = conversations.value.find(item => item.id === conversationId)
      if (conversation && message.id === list[list.length - 1]?.id) {
        conversation.lastMessageStatus = message.status
      }
      messageCache.updateMessageStatus(conversationId, message.id, message.status, message.syncStatus)
      return
    }
  }

  function escapeRegExp(value: string): string { return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') }

  function connectRealtime(): void {
    if (!authStore.token || !authStore.user.id) return
    // AUDIT FIX [3.3] / IM-MESSAGE-CENTER-20260811：浏览器恢复在线、首屏重试和
    // 共享客户端状态回放都可能重复调用该方法。已有监听器时只负责唤醒断开的连接，
    // 不重复注册 onMessage，否则同一条二进制消息会被合并多次并重复增加未读数。
    if (client && realtimeUnsubscribers.length) {
      if (!client.isConnected && [ConnectionState.IDLE, ConnectionState.DISCONNECTED].includes(client.connectionState)) {
        client.connect()
      }
      return
    }
    const configured = import.meta.env.VITE_IM_WS_URL ||
      `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}`
    if (!client) {
      try {
        // Layout 顶部通知中心可能已经创建了共享客户端；复用它，不重复建立连接。
        client = getImClient()
      } catch {
        client = getImClient({
          url: `${normalizeWsUrl(configured)}/ws`,
          token: () => authStore.token,
          userId: authStore.user.id,
          autoReconnect: true,
          enableHeartbeat: true,
        })
        ownsRealtimeClient = true
      }
    }
    // 保留现有 WebRTC 组合式函数的共享客户端接入点，避免消息中心重构影响通话信令。
    useImClient().setClient(client)
    const clientStateToUi = (state: ConnectionState): MessageCenterConnectionState => {
      const map: Partial<Record<ConnectionState, MessageCenterConnectionState>> = {
        [ConnectionState.CONNECTING]: 'connecting',
        [ConnectionState.HANDSHAKING]: 'connecting',
        [ConnectionState.CONNECTED]: 'online',
        [ConnectionState.RECONNECTING]: 'reconnecting',
        [ConnectionState.DISCONNECTED]: 'offline',
      }
      return map[state] || 'offline'
    }
    const handleStatus = (state: ConnectionState): void => {
      connectionState.value = clientStateToUi(state)
      const connected = state === ConnectionState.CONNECTED
      if (connected && !realtimeWasOnline) {
        realtimeWasOnline = true
        // 重连完成后补拉三类列表；单项失败不影响已建立的 WebSocket。
        void Promise.allSettled([fetchConversations(), fetchFriends(), fetchGroups()])
      } else if (!connected) {
        realtimeWasOnline = false
      }
    }
    const handleError = (error: Error): void => {
      connectionState.value = error.message.includes('踢下线') ? 'kicked' : 'degraded'
    }
    realtimeUnsubscribers.push(client.onStatusChange(handleStatus))
    realtimeUnsubscribers.push(client.onError(handleError))
    realtimeUnsubscribers.push(client.onReceipt(handleReceipt))
    // onMessage 是 SDK 对实时推送、离线同步和 HTTP 拉取的统一回调入口。
    realtimeUnsubscribers.push(client.onMessage(handleIncoming))
    realtimeUnsubscribers.push(client.onRead(event => {
      const list = messages.value[event.conversationId] || []
      const readAt = Date.now()
      list.filter(item => item.sender === 'me' && item.status !== 'failed').forEach(item => { item.status = 'read'; item.readAt = readAt })
      const conversation = conversations.value.find(item => item.id === event.conversationId)
      if (conversation && list.length && list[list.length - 1].sender === 'me') conversation.lastMessageStatus = 'read'
    }))
    // 共享客户端可能在本 Store 注册监听前已经完成握手。原实现只等待下一次状态变化，
    // 所以顶部长期显示离线；新行为立即同步当前状态，并仅在确实未连接时发起连接。
    connectionState.value = clientStateToUi(client.connectionState)
    realtimeWasOnline = client.isConnected
    // AUDIT FIX [3.1/3.2] / IM-MESSAGE-CENTER-20260811：通知中心可能先创建了共享
    // 客户端但连接失败，旧逻辑只在 IDLE 状态重连，进入消息中心后会永久停在离线。
    // 新行为对 IDLE/DISCONNECTED 都发起一次连接；RECONNECTING/HANDSHAKING 交给 SDK
    // 自己的状态机，避免重复建立 WebSocket。
    if (!client.isConnected && [ConnectionState.IDLE, ConnectionState.DISCONNECTED].includes(client.connectionState)) {
      client.connect()
    }
  }

  function disconnectRealtime(): void {
    activeHistoryController?.abort()
    if (typingTimer) clearTimeout(typingTimer)
    typingTimers.forEach(timer => clearTimeout(timer))
    typingTimers.clear()
    stopPresencePolling()
    realtimeUnsubscribers.splice(0).forEach(unsubscribe => unsubscribe())
    if (ownsRealtimeClient) client?.disconnect()
    client = null
    if (ownsRealtimeClient) {
      destroyImClient()
      useImClient().setClient(null)
    }
    ownsRealtimeClient = false
    realtimeWasOnline = false
    connectionState.value = 'offline'
  }

  return {
    conversations,
    messages,
    activeConversationId,
    connectionState,
    loadingConversations,
    loadingHistory,
    historyComplete,
    loadError,
    syncProgress,
    friends,
    blacklistedFriends,
    groups,
    groupsLoading,
    pendingFriendRequestCount,
    contactsLoading,
    drafts,
    typingUsers,
    activeConversation,
    activeMessages,
    totalUnread,
    sortedConversations,
    sortedGroups,
    bootstrap,
    fetchConversations,
    refreshPresence,
    startPresencePolling,
    stopPresencePolling,
    openConversation,
    markConversationRead,
    loadOlderMessages,
    openFriendConversation,
    sendMessage,
    retryMessage,
    recallMessage,
    togglePinned,
    toggleMuted,
    fetchFriends,
    fetchGroups,
    openGroupConversation,
    createGroup,
    leaveGroup,
    dissolveGroup,
    startGroupPolling,
    stopGroupPolling,
    refreshPendingFriendRequestCount,
    startFriendRequestPolling,
    stopFriendRequestPolling,
    updateFriendRemark,
    toggleFriendStar,
    removeFriend,
    blockFriend,
    unblockFriend,
    saveDraft,
    sendTyping,
    connectRealtime,
    disconnectRealtime,
  }
})
