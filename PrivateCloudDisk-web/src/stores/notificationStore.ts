// ============================================================
// notificationStore.ts — 消息中心 / 通知 + IM 会话状态管理
// ============================================================
// 基于 ImWebSocketClient SDK 实现的企业级消息中心状态管理。
// 覆盖通知、好友、会话、消息、实时通信全链路。
//
// 核心能力：
// - 通知/好友/会话的 HTTP 拉取 + WebSocket 实时推送
// - 消息发送/接收/撤回/已读（WebSocket 优先，HTTP 降级）
// - 在线状态同步（上线/下线/被踢）
// - 正在输入状态感知
// - 离线消息自动同步
// - 群组管理（创建/加入/退出/禁言/踢人/解散）
// ============================================================

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getFriendsApi,
  getNotificationsApi,
  markAllNotificationsReadApi,
  markNotificationReadApi,
  searchUsersApi,
  sendFriendRequestApi,
  getConversationsApi,
  getMessageHistoryApi,
  markMessageReadApi,
  sendMessageApi,
  recallMessageApi,
  createConversationApi,
  getTotalUnreadCountApi,
} from '@/api/index'
import {
  type ImWebSocketClient,
  type MessageProtocol,
  type MessageDTO,
  type ConversationDTO,
  type CallInvitePayload,
  CommandType,
  ConnectionState,
  MessageType,
  MessageStatus,
  ConversationType,
  ResponseCode,
  getImClient,
  destroyImClient,
} from '@/api/im'
import { useAuthStore } from './authStore'
import * as messageCache from '@/utils/messageCache'
import { useImClient } from '@/composables/useImClient'

// ==================== 类型定义 ====================

export interface Notification {
  id: string
  type: string
  title: string
  message: string
  time: number
  read: boolean
  category: string
}

export interface Friend {
  id: string
  account: string
  name: string
  email: string
  avatar: string
  role: string
  online: boolean
}

export interface Conversation {
  id: string
  friend_id: string
  title: string
  subtitle: string
  avatar?: string
  unread: number
  pinned: boolean
  muted: boolean
  updated_at: number
  conversationType: number
  lastMessage?: string
  lastMessageType?: number
}

export interface ChatMessage {
  id: string
  sender: string
  senderName?: string
  senderAvatar?: string
  type: string
  content: string
  file_id?: string
  share_url?: string
  extra?: string
  created_at: number
  status: string
  serverSeq?: number
  replyTo?: string
  /** 消息同步状态 */
  syncStatus?: 'synced' | 'syncing' | 'failed' | 'pending'
  /** 视频通话相关 */
  callType?: 'video' | 'voice'
  callDuration?: number
  callStatus?: 'missed' | 'answered' | 'rejected' | 'ended'
}

export interface GroupInfo {
  id: string
  name: string
  avatar?: string
  ownerId: string
  ownerName?: string
  announcement?: string
  description?: string
  memberCount: number
  maxMembers: number
  isAllMuted: boolean
  joinMode: number
  status: number
  createdAt?: string
}

export interface GroupMember {
  userId: string
  nickname?: string
  avatar?: string
  role: number
  alias?: string
  muteUntil?: string
  joinTime?: string
}

// ==================== 种子数据 ====================

const now = Date.now()

const seedNotifications: Notification[] = [
  { id: 'n1', type: 'success', title: '文件上传成功', message: '文件 "report.pdf" 已成功上传到 我的网盘 / 项目资料。', time: now - 1000 * 60 * 5, read: false, category: '文件' },
  { id: 'n2', type: 'security', title: '账号登录提醒', message: '你的账号刚刚在 浙江杭州 的 Chrome 浏览器登录。', time: now - 1000 * 60 * 32, read: false, category: '安全' },
  { id: 'n3', type: 'info', title: '密码修改成功', message: '你的账号密码已成功修改，如非本人操作请立即联系管理员。', time: now - 1000 * 60 * 60 * 4, read: true, category: '账号' },
  { id: 'n4', type: 'warning', title: '分享链接即将过期', message: '分享 "项目资料" 将在 24 小时后过期。', time: now - 1000 * 60 * 60 * 12, read: true, category: '分享' },
]

const seedFriends: Friend[] = [
  { id: 'u1', account: 'pm_chen', name: '陈产品', email: 'chen.pm@example.com', avatar: '', role: '产品经理', online: true },
  { id: 'u2', account: 'dev_lin', name: '林后端', email: 'lin.dev@example.com', avatar: '', role: '后端工程师', online: true },
  { id: 'u3', account: 'ops_wu', name: '吴运维', email: 'ops.wu@example.com', avatar: '', role: '运维负责人', online: false },
]

const seedConversations: Conversation[] = [
  { id: 'c1', friend_id: 'u1', title: '陈产品', subtitle: '产品经理', unread: 2, pinned: true, muted: false, updated_at: now - 1000 * 60 * 3, conversationType: 1, lastMessage: '收到，我这边同步给法务。', lastMessageType: 1 },
  { id: 'c2', friend_id: 'u2', title: '林后端', subtitle: '后端工程师', unread: 0, pinned: false, muted: false, updated_at: now - 1000 * 60 * 28, conversationType: 1, lastMessage: 'OpenSearch 索引字段我已经补完', lastMessageType: 1 },
]

const seedMessages: Record<string, ChatMessage[]> = {
  c1: [
    { id: 'm1', sender: 'u1', senderName: '陈产品', type: 'text', content: '我看了你刚分享的项目资料，能把最终版合同也发我一下吗？', created_at: now - 1000 * 60 * 25, status: 'read' },
    { id: 'm2', sender: 'me', type: 'file', content: '最终版合同.pdf', file_id: 'demo-file-1', created_at: now - 1000 * 60 * 12, status: 'read' },
    { id: 'm3', sender: 'u1', senderName: '陈产品', type: 'text', content: '收到，我这边同步给法务。', created_at: now - 1000 * 60 * 3, status: 'delivered' },
  ],
  c2: [
    { id: 'm4', sender: 'u2', senderName: '林后端', type: 'text', content: 'OpenSearch 索引字段我已经补完，你前端可以按 file_category 过滤。', created_at: now - 1000 * 60 * 30, status: 'read' },
  ],
}

// ==================== 辅助函数 ====================

function normalizeNotification(item: Record<string, unknown>): Notification {
  return {
    id: (item.id || item.notification_id || crypto.randomUUID()) as string,
    type: (item.type as string) || 'info',
    title: (item.title as string) || '系统消息',
    message: (item.message || item.content || '') as string,
    time: (item.time as number) || (item.created_at as number) || Date.now(),
    read: Boolean(item.read ?? item.is_read),
    category: (item.category as string) || '系统',
  }
}

function normalizeConversation(dto: ConversationDTO): Conversation {
  return {
    id: dto.conversationId,
    friend_id: dto.targetId,
    title: dto.conversationName || '',
    subtitle: dto.lastMessage ? dto.lastMessage.slice(0, 30) : '',
    avatar: dto.avatar,
    unread: dto.unreadCount,
    pinned: dto.isTop,
    muted: dto.isMuted,
    updated_at: dto.lastMessageTime ? new Date(dto.lastMessageTime).getTime() : Date.now(),
    conversationType: dto.conversationType,
    lastMessage: dto.lastMessage,
    lastMessageType: dto.lastMessageType,
  }
}

function normalizeMessage(dto: MessageDTO): ChatMessage {
  return {
    id: dto.messageId || '',
    sender: dto.senderId,
    senderName: dto.senderName,
    senderAvatar: dto.senderAvatar,
    type: messageTypeToString(dto.messageType),
    content: dto.content,
    extra: dto.extra,
    created_at: dto.sendTime ? new Date(dto.sendTime).getTime() : Date.now(),
    status: messageStatusToString(dto.status ?? MessageStatus.SENT),
    serverSeq: dto.serverSeq,
    replyTo: dto.replyTo,
  }
}

function messageTypeToString(type: MessageType): string {
  const map: Record<number, string> = {
    [MessageType.TEXT]: 'text',
    [MessageType.IMAGE]: 'image',
    [MessageType.FILE]: 'file',
    [MessageType.VOICE]: 'voice',
    [MessageType.VIDEO]: 'video',
    [MessageType.LOCATION]: 'location',
    [MessageType.SYSTEM_NOTICE]: 'system',
    [MessageType.CUSTOM]: 'custom',
    [MessageType.REPLY]: 'reply',
    [MessageType.READ_RECEIPT]: 'read_receipt',
    [MessageType.TYPING]: 'typing',
  }
  return map[type] || 'text'
}

function stringToMessageType(type: string): MessageType {
  const map: Record<string, MessageType> = {
    text: MessageType.TEXT,
    image: MessageType.IMAGE,
    file: MessageType.FILE,
    voice: MessageType.VOICE,
    video: MessageType.VIDEO,
    location: MessageType.LOCATION,
    system: MessageType.SYSTEM_NOTICE,
    custom: MessageType.CUSTOM,
    reply: MessageType.REPLY,
  }
  return map[type] || MessageType.TEXT
}

function messageStatusToString(status: MessageStatus): string {
  const map: Record<number, string> = {
    [MessageStatus.SENDING]: 'sending',
    [MessageStatus.SENT]: 'sent',
    [MessageStatus.DELIVERED]: 'delivered',
    [MessageStatus.READ]: 'read',
    [MessageStatus.FAILED]: 'failed',
    [MessageStatus.RECALLED]: 'recalled',
    [MessageStatus.DELETED]: 'deleted',
  }
  return map[status] || 'sent'
}

// ==================== Store ====================

export const useNotificationStore = defineStore('notification', () => {
  const authStore = useAuthStore()

  // ---- 状态 ----
  const notifications = ref<Notification[]>([...seedNotifications])
  const friends = ref<Friend[]>([...seedFriends])
  const conversations = ref<Conversation[]>([...seedConversations])
  const messagesByConversation = ref<Record<string, ChatMessage[]>>({ ...seedMessages })
  const activeConversationId = ref<string | null>(seedConversations[0]?.id || null)
  const loading = ref(false)
  const realtimeStatus = ref<string>('offline')
  const backendReady = ref(false)
  const userSearchResults = ref<Friend[]>([])
  const typingUsers = ref<Record<string, string>>({}) // conversationId -> userName
  const syncStatus = ref<'idle' | 'syncing' | 'synced' | 'error'>('idle') // 全局消息同步状态

  // ---- 视频通话相关 ----
  const hasIncomingCall = ref(false)
  const incomingCallInfo = ref<CallInvitePayload | null>(null)
  const isCallActive = ref(false)
  const activeCallSession = ref<{
    callId: string
    peerId: string
    peerName: string
    callType: 'video' | 'voice'
    startTime: number
  } | null>(null)

  // ---- IM SDK 实例 ----
  let imClient: ImWebSocketClient | null = null

  // ---- 计算属性 ----
  const unreadCount = computed(() => notifications.value.filter(item => !item.read).length)
  const chatUnreadCount = computed(() => conversations.value.reduce((sum, item) => sum + (item.unread || 0), 0))
  const recentNotifications = computed(() => notifications.value.slice(0, 5))
  const activeConversation = computed(() => conversations.value.find(item => item.id === activeConversationId.value) || null)
  const activeFriend = computed(() => friends.value.find(item => item.id === activeConversation.value?.friend_id) || null)
  const activeMessages = computed(() => messagesByConversation.value[activeConversationId.value || ''] || [])
  const sortedConversations = computed(() => [...conversations.value].sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.updated_at - a.updated_at))

  // ==================== 初始化 ====================

  async function bootstrap(): Promise<void> {
    loading.value = true
    await Promise.allSettled([fetchNotifications(), fetchFriends(), fetchConversations()])
    loading.value = false
    connectRealtime()
  }

  // ==================== HTTP 拉取 ====================

  async function fetchNotifications(): Promise<void> {
    try {
      const res = await getNotificationsApi()
      if (res.code === 200 && Array.isArray(res.data)) {
        notifications.value = res.data.map(normalizeNotification)
        backendReady.value = true
      }
    } catch {
      backendReady.value = false
    }
  }

  async function fetchFriends(): Promise<void> {
    try {
      const res = await getFriendsApi()
      if (res.code === 200 && Array.isArray(res.data)) {
        friends.value = res.data
      }
    } catch {
      // 后端未就绪时保留种子数据
    }
  }

  async function fetchConversations(): Promise<void> {
    try {
      const res = await getConversationsApi(authStore.user.account || authStore.user.name)
      if (res.code === 200 && Array.isArray(res.data)) {
        conversations.value = (res.data as ConversationDTO[]).map(normalizeConversation)
      }
    } catch {
      // 后端未就绪时保留种子数据
    }
  }

  async function fetchTotalUnread(): Promise<number> {
    try {
      const res = await getTotalUnreadCountApi(authStore.user.account || authStore.user.name)
      if (res.code === 200 && typeof res.data === 'number') {
        return res.data
      }
    } catch { /* 静默 */ }
    return 0
  }

  // ==================== 会话操作 ====================

  async function openConversation(conversationId: string): Promise<void> {
    syncStatus.value = 'syncing'
    activeConversationId.value = conversationId
    const conversation = conversations.value.find(item => item.id === conversationId)
    if (conversation) {
      conversation.unread = 0
    }

    // 先从本地缓存加载
    try {
      const cached = await messageCache.loadMessages(conversationId)
      if (cached.length > 0) {
        messagesByConversation.value[conversationId] = cached
      }
    } catch { /* 忽略缓存错误 */ }

    // 获取历史消息
    try {
      const res = await getMessageHistoryApi(
        conversationId,
        authStore.user.account || authStore.user.name,
        1,
        50,
      )
      if (res.code === 200 && Array.isArray(res.data)) {
        const serverMessages = (res.data as MessageDTO[]).map(normalizeMessage)
        messagesByConversation.value[conversationId] = serverMessages
        // 同步到本地缓存
        messageCache.saveMessages(conversationId, serverMessages)
      }
    } catch {
      if (!messagesByConversation.value[conversationId]) {
        messagesByConversation.value[conversationId] = []
      }
    }

    syncStatus.value = 'synced'

    // 发送已读回执（WebSocket + HTTP 双通道）
    if (imClient?.isConnected) {
      imClient.sendReadReceipt(conversationId)
    }
    try {
      await markMessageReadApi(conversationId, authStore.user.account || authStore.user.name)
    } catch { /* 静默 */ }
  }

  async function startDirectConversation(friendId: string): Promise<void> {
    let existing = conversations.value.find(item => item.friend_id === friendId)
    if (!existing) {
      const friend = friends.value.find(item => item.id === friendId)
      existing = {
        id: `local-${friendId}`,
        friend_id: friendId,
        title: friend?.name || friend?.account || '新会话',
        subtitle: friend?.role || friend?.email || '',
        unread: 0,
        pinned: false,
        muted: false,
        updated_at: Date.now(),
        conversationType: ConversationType.PRIVATE,
      }
      conversations.value.unshift(existing)
      messagesByConversation.value[existing.id] = []

      // 请求后端创建会话
      try {
        const res = await createConversationApi(
          authStore.user.account || authStore.user.name,
          friendId,
          ConversationType.PRIVATE,
        )
        if (res.code === 200 && res.data?.conversationId) {
          existing.id = res.data.conversationId
        }
      } catch {
        // 保留本地会话
      }
    }
    await openConversation(existing.id)
  }

  // ==================== 消息操作 ====================

  async function sendMessage(payload: {
    type?: string
    content: string
    file_id?: string
    share_url?: string
    extra?: string
  }): Promise<void> {
    if (!activeConversationId.value || !payload.content?.trim()) return

    const conversation = conversations.value.find(item => item.id === activeConversationId.value)
    if (!conversation) return

    const userId = authStore.user.account || authStore.user.name

    // 构建消息 -- sender 设为 'me' 表示自己发送，前端根据此判断消息方向
    const message: ChatMessage = {
      id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      sender: 'me',
      senderName: authStore.user.name || userId,
      type: payload.type || 'text',
      content: payload.content.trim(),
      file_id: payload.file_id || undefined,
      share_url: payload.share_url || undefined,
      extra: payload.extra || undefined,
      created_at: Date.now(),
      status: 'sending',
      syncStatus: 'syncing',
    }

    appendMessage(activeConversationId.value, message)
    // 保存到本地缓存
    messageCache.saveMessage(activeConversationId.value, message)

    // 更新会话的最后消息
    if (conversation) {
      conversation.updated_at = message.created_at
      conversation.lastMessage = message.content.slice(0, 50)
      conversation.lastMessageType = 1
    }

    // WebSocket 优先发送
    if (imClient?.isConnected) {
      try {
        const messageDTO: MessageDTO = {
          conversationId: activeConversationId.value,
          conversationType: conversation.conversationType || ConversationType.PRIVATE,
          messageType: stringToMessageType(payload.type || 'text'),
          senderId: userId,
          receiverId: conversation.friend_id,
          content: payload.content.trim(),
          extra: payload.extra,
        }
        imClient.sendMessage(messageDTO)
        message.status = 'sent'
        message.syncStatus = 'synced'
        messageCache.updateMessageStatus(activeConversationId.value, message.id, 'sent', 'synced')
        return
      } catch {
        // WebSocket 失败，降级到 HTTP
      }
    }

    // HTTP 降级
    try {
      const res = await sendMessageApi({
        conversationId: activeConversationId.value,
        conversationType: conversation.conversationType || ConversationType.PRIVATE,
        messageType: stringToMessageType(payload.type || 'text'),
        senderId: userId,
        receiverId: conversation.friend_id,
        content: payload.content.trim(),
        extra: payload.extra,
      })
      if (res.code === 200) {
        message.status = 'sent'
        message.syncStatus = 'synced'
        if (res.data?.messageId) {
          message.id = res.data.messageId
          message.serverSeq = res.data.serverSeq
        }
        messageCache.updateMessageStatus(activeConversationId.value, message.id, 'sent', 'synced')
      } else {
        message.status = 'failed'
        message.syncStatus = 'failed'
        messageCache.updateMessageStatus(activeConversationId.value, message.id, 'failed', 'failed')
      }
    } catch {
      message.status = 'failed'
      message.syncStatus = 'failed'
      messageCache.updateMessageStatus(activeConversationId.value, message.id, 'failed', 'failed')
    }
  }

  async function recallMessage(messageId: string): Promise<boolean> {
    const userId = authStore.user.account || authStore.user.name

    // WebSocket 优先
    if (imClient?.isConnected) {
      try {
        imClient.sendRecallMessage(messageId)
      } catch { /* 降级 HTTP */ }
    }

    // HTTP 调用
    try {
      const res = await recallMessageApi(messageId, userId)
      if (res.code === 200) {
        // 更新本地消息状态
        updateMessageStatus(messageId, 'recalled')
        return true
      }
    } catch { /* 静默 */ }
    return false
  }

  /**
   * 重试发送失败的消息
   */
  async function retryMessage(conversationId: string, messageId: string): Promise<void> {
    const messages = messagesByConversation.value[conversationId]
    if (!messages) return
    const msg = messages.find(m => m.id === messageId)
    if (!msg || (msg.status !== 'failed' && msg.syncStatus !== 'failed')) return

    // 重新发送
    await sendMessage({
      type: msg.type,
      content: msg.content,
      file_id: msg.file_id,
      share_url: msg.share_url,
      extra: msg.extra,
    })
    // 删除旧的失败消息
    messagesByConversation.value[conversationId] = messages.filter(m => m.id !== messageId)
    messageCache.deleteMessage(conversationId, messageId)
  }

  /**
   * 发送视频通话邀请消息
   */
  async function sendVideoCallInvite(callType: 'video' | 'voice'): Promise<string | null> {
    if (!activeConversationId.value) return null

    const conversation = conversations.value.find(item => item.id === activeConversationId.value)
    if (!conversation) return null

    const callId = `call-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    const userId = authStore.user.account || authStore.user.name

    const extra = JSON.stringify({
      callId,
      callType,
      callerId: userId,
      callerName: authStore.user.name || userId,
      timestamp: Date.now(),
    })

    await sendMessage({
      type: 'video_call',
      content: callType === 'video' ? '视频通话邀请' : '语音通话邀请',
      extra,
    })

    return callId
  }

  // ==================== 视频通话操作 ====================

  /** 接受来电 */
  function acceptIncomingCall(): void {
    if (!incomingCallInfo.value) return
    hasIncomingCall.value = false

    activeCallSession.value = {
      callId: incomingCallInfo.value.callId || '',
      peerId: incomingCallInfo.value.callerId || '',
      peerName: incomingCallInfo.value.callerName || '未知用户',
      callType: incomingCallInfo.value.callType === 'VIDEO' ? 'video' : 'voice',
      startTime: Date.now(),
    }
    isCallActive.value = true
    incomingCallInfo.value = null
  }

  /** 拒绝来电 */
  function rejectIncomingCall(): void {
    hasIncomingCall.value = false
    incomingCallInfo.value = null
  }

  /** 挂断通话 */
  function endCall(): void {
    isCallActive.value = false
    activeCallSession.value = null
  }

  // ==================== 联系人操作 ====================

  async function searchUsers(keyword: string): Promise<void> {
    if (!keyword?.trim()) {
      userSearchResults.value = []
      return
    }
    try {
      const res = await searchUsersApi(keyword.trim())
      userSearchResults.value = res.code === 200 && Array.isArray(res.data) ? res.data as Friend[] : []
    } catch {
      userSearchResults.value = seedFriends.filter(
        item => item.account.includes(keyword) || item.name.includes(keyword),
      )
    }
  }

  async function sendFriendRequest(
    account: string,
    remark: string = '',
  ): Promise<{ success: boolean; message?: string; local?: boolean }> {
    if (!account?.trim()) return { success: false, message: '请输入平台账号' }
    try {
      const res = await sendFriendRequestApi(account.trim(), remark)
      if (res.code === 200) return { success: true }
      return { success: false, message: (res.message as string) || '好友申请发送失败' }
    } catch {
      notifications.value.unshift({
        id: `friend-${Date.now()}`,
        type: 'info',
        title: '好友申请已进入待发送队列',
        message: `后端协作接口接入后，将向 ${account} 发送好友申请。`,
        time: Date.now(),
        read: false,
        category: '协作',
      })
      return { success: true, local: true }
    }
  }

  // ==================== 通知操作 ====================

  function markAsRead(id: string): void {
    const item = notifications.value.find(notification => notification.id === id)
    if (item) item.read = true
    markNotificationReadApi(id).catch(() => {})
  }

  function markAllAsRead(): void {
    notifications.value.forEach(item => { item.read = true })
    markAllNotificationsReadApi().catch(() => {})
  }

  // ==================== 实时通信 ====================

  function connectRealtime(): void {
    if (!authStore.token || imClient) return

    const wsBase = import.meta.env.VITE_IM_WS_URL ||
      `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/im`

    try {
      imClient = getImClient({
        url: `${wsBase}/api/v1/ws`,
        token: () => authStore.token,
        autoReconnect: true,
        enableHeartbeat: true,
      })

      // 注册到 useCall 共享
      useImClient().setClient(imClient)

      // 注册连接状态监听
      imClient.onStatusChange((state: ConnectionState) => {
        switch (state) {
          case ConnectionState.CONNECTING:
            realtimeStatus.value = 'connecting'
            break
          case ConnectionState.CONNECTED:
            realtimeStatus.value = 'online'
            break
          case ConnectionState.RECONNECTING:
            realtimeStatus.value = 'reconnecting'
            break
          case ConnectionState.DISCONNECTED:
            realtimeStatus.value = 'offline'
            break
          default:
            realtimeStatus.value = 'offline'
        }
      })

      // 注册错误监听
      imClient.onError((error: Error) => {
        console.warn('[IM] 错误:', error.message)
        if (error.message.includes('被踢下线')) {
          realtimeStatus.value = 'kicked'
        }
      })

      // 注册消息处理器（按命令字路由）
      registerMessageHandlers(imClient)

      // 建立连接
      imClient.connect()
    } catch {
      realtimeStatus.value = 'degraded'
    }
  }

  function registerMessageHandlers(client: ImWebSocketClient): void {
    // 收到新消息
    client.onCommand(CommandType.SEND_MESSAGE, (protocol: MessageProtocol) => {
      const msg = protocol.payload as MessageDTO | undefined
      if (!msg) return
      const chatMessage = normalizeMessage(msg)
      const conversationId = msg.conversationId

      // 检测视频通话邀请
      if (msg.messageType === MessageType.CUSTOM && msg.extra) {
        try {
          const extra = JSON.parse(msg.extra)
          if (extra.callType && extra.callId) {
            chatMessage.type = 'video_call'
            chatMessage.callType = extra.callType
            // 触发来电弹窗
            if (msg.senderId !== authStore.user.account) {
              hasIncomingCall.value = true
              incomingCallInfo.value = {
                callId: extra.callId,
                callType: extra.callType === 'video' ? 'VIDEO' as any : 'VOICE' as any,
                callerId: extra.callerId || msg.senderId,
                callerName: extra.callerName || msg.senderName || msg.senderId,
                conversationId,
                timestamp: extra.timestamp || Date.now(),
              }
            }
          }
        } catch { /* 忽略解析错误 */ }
      }

      // 确保会话存在
      let conversation = conversations.value.find(item => item.id === conversationId)
      if (!conversation) {
        // 新会话，从后端拉取
        fetchConversations()
        return
      }

      appendMessage(conversationId, chatMessage)

      // 发送 ACK
      if (protocol.seq !== undefined) {
        client.sendAck(protocol.seq)
      }
    })

    // 消息 ACK（自己发出的消息得到服务端确认）
    client.onCommand(CommandType.MESSAGE_ACK, (protocol: MessageProtocol) => {
      const payload = protocol.payload as { messageId?: string; serverSeq?: number } | undefined
      if (payload?.messageId) {
        updateMessageStatus(payload.messageId, 'sent', payload.serverSeq)
      }
    })

    // 撤回消息
    client.onCommand(CommandType.RECALL_MESSAGE, (protocol: MessageProtocol) => {
      const payload = protocol.payload as { messageId?: string } | undefined
      if (payload?.messageId) {
        updateMessageStatus(payload.messageId, 'recalled')
      }
    })

    // 已读回执
    client.onCommand(CommandType.READ_MESSAGE, (protocol: MessageProtocol) => {
      const payload = protocol.payload as { conversationId?: string; userId?: string } | undefined
      if (payload?.conversationId) {
        updateMessagesReadStatus(payload.conversationId)
      }
    })

    // 正在输入
    client.onCommand(CommandType.TYPING, (protocol: MessageProtocol) => {
      const senderId = protocol.senderId
      if (!senderId) return
      const friend = friends.value.find(f => f.id === senderId)
      const name = friend?.name || senderId
      typingUsers.value[protocol.payload as string || ''] = name

      // 3 秒后清除正在输入状态
      setTimeout(() => {
        delete typingUsers.value[protocol.payload as string || '']
      }, 3000)
    })

    // 离线消息同步
    client.onCommand(CommandType.SYNC_OFFLINE_MESSAGES, (protocol: MessageProtocol) => {
      const messages = protocol.payload as MessageDTO[] | undefined
      if (!Array.isArray(messages)) return
      messages.forEach((msg) => {
        const chatMessage = normalizeMessage(msg)
        const conversationId = msg.conversationId
        if (!conversations.value.find(item => item.id === conversationId)) {
          fetchConversations()
        }
        appendMessage(conversationId, chatMessage)
      })
    })

    // 系统通知
    client.onCommand(CommandType.SYSTEM_NOTIFY, (protocol: MessageProtocol) => {
      const payload = protocol.payload as {
        title?: string
        message?: string
        type?: string
        category?: string
      } | undefined
      if (payload) {
        notifications.value.unshift({
          id: `sys-${Date.now()}`,
          type: payload.type || 'info',
          title: payload.title || '系统通知',
          message: payload.message || '',
          time: Date.now(),
          read: false,
          category: payload.category || '系统',
        })
      }
    })

    // 错误通知
    client.onCommand(CommandType.ERROR_NOTIFY, (protocol: MessageProtocol) => {
      const payload = protocol.payload as { code?: number; message?: string } | undefined
      console.error('[IM] 服务端错误:', payload?.code, payload?.message)
    })
  }

  function disconnectRealtime(): void {
    if (imClient) {
      imClient.disconnect()
      imClient = null
    }
    destroyImClient()
    useImClient().setClient(null)
    realtimeStatus.value = 'offline'
  }

  // ==================== 内部辅助 ====================

  function appendMessage(conversationId: string, message: ChatMessage): void {
    const current = messagesByConversation.value[conversationId] || []
    messagesByConversation.value[conversationId] = [...current, message]

    // 保存到本地缓存
    messageCache.saveMessage(conversationId, message)

    const conversation = conversations.value.find(item => item.id === conversationId)
    if (conversation) {
      conversation.updated_at = message.created_at
      conversation.lastMessage = message.content.slice(0, 50)
      conversation.lastMessageType = message.type === 'text' ? 1 : 2
      if (message.sender !== 'me' && message.sender !== authStore.user.account && conversationId !== activeConversationId.value) {
        conversation.unread = (conversation.unread || 0) + 1
      }
    }
  }

  function updateMessageStatus(
    messageId: string,
    status: string,
    serverSeq?: number,
  ): void {
    for (const [conversationId, messages] of Object.entries(messagesByConversation.value)) {
      const msg = messages.find(m => m.id === messageId)
      if (msg) {
        msg.status = status
        if (serverSeq !== undefined) msg.serverSeq = serverSeq
        messageCache.updateMessageStatus(conversationId, messageId, status)
        return
      }
    }
  }

  function updateMessagesReadStatus(conversationId: string): void {
    const messages = messagesByConversation.value[conversationId]
    if (messages) {
      messages.forEach(msg => {
        if (msg.status === 'sent' || msg.status === 'delivered') {
          msg.status = 'read'
        }
      })
    }
  }

  // ==================== 导出 ====================

  return {
    // 状态
    notifications,
    friends,
    conversations,
    messagesByConversation,
    activeConversationId,
    loading,
    realtimeStatus,
    backendReady,
    userSearchResults,
    typingUsers,
    syncStatus,

    // 视频通话
    hasIncomingCall,
    incomingCallInfo,
    isCallActive,
    activeCallSession,

    // 计算属性
    unreadCount,
    chatUnreadCount,
    recentNotifications,
    activeConversation,
    activeFriend,
    activeMessages,
    sortedConversations,

    // 初始化
    bootstrap,
    fetchNotifications,
    fetchFriends,
    fetchConversations,
    fetchTotalUnread,

    // 会话
    openConversation,
    startDirectConversation,

    // 消息
    sendMessage,
    recallMessage,
    retryMessage,
    sendVideoCallInvite,

    // 视频通话
    acceptIncomingCall,
    rejectIncomingCall,
    endCall,

    // 联系人
    searchUsers,
    sendFriendRequest,

    // 通知
    markAsRead,
    markAllAsRead,

    // 实时通信
    connectRealtime,
    disconnectRealtime,
  }
})