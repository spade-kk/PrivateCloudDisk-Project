import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  createDirectConversationApi,
  getConversationMessagesApi,
  getConversationsApi,
  getFriendsApi,
  getNotificationsApi,
  markAllNotificationsReadApi,
  markNotificationReadApi,
  searchUsersApi,
  sendChatMessageApi,
  sendFriendRequestApi,
} from '@/api/index'
import { useAuthStore } from './authStore'

const now = Date.now()

const seedNotifications = [
  { id: 'n1', type: 'success', title: '文件上传成功', message: '文件 "report.pdf" 已成功上传到 我的网盘 / 项目资料。', time: now - 1000 * 60 * 5, read: false, category: '文件' },
  { id: 'n2', type: 'security', title: '账号登录提醒', message: '你的账号刚刚在 浙江杭州 的 Chrome 浏览器登录。', time: now - 1000 * 60 * 32, read: false, category: '安全' },
  { id: 'n3', type: 'info', title: '密码修改成功', message: '你的账号密码已成功修改，如非本人操作请立即联系管理员。', time: now - 1000 * 60 * 60 * 4, read: true, category: '账号' },
  { id: 'n4', type: 'warning', title: '分享链接即将过期', message: '分享 "项目资料" 将在 24 小时后过期。', time: now - 1000 * 60 * 60 * 12, read: true, category: '分享' },
]

const seedFriends = [
  { id: 'u1', account: 'pm_chen', name: '陈产品', email: 'chen.pm@example.com', avatar: '', role: '产品经理', online: true },
  { id: 'u2', account: 'dev_lin', name: '林后端', email: 'lin.dev@example.com', avatar: '', role: '后端工程师', online: true },
  { id: 'u3', account: 'ops_wu', name: '吴运维', email: 'ops.wu@example.com', avatar: '', role: '运维负责人', online: false },
]

const seedConversations = [
  { id: 'c1', friend_id: 'u1', title: '陈产品', subtitle: '产品经理', unread: 2, pinned: true, updated_at: now - 1000 * 60 * 3 },
  { id: 'c2', friend_id: 'u2', title: '林后端', subtitle: '后端工程师', unread: 0, pinned: false, updated_at: now - 1000 * 60 * 28 },
]

const seedMessages = {
  c1: [
    { id: 'm1', sender: 'u1', type: 'text', content: '我看了你刚分享的项目资料，能把最终版合同也发我一下吗？', created_at: now - 1000 * 60 * 25, status: 'read' },
    { id: 'm2', sender: 'me', type: 'file', content: '最终版合同.pdf', file_id: 'demo-file-1', created_at: now - 1000 * 60 * 12, status: 'read' },
    { id: 'm3', sender: 'u1', type: 'text', content: '收到，我这边同步给法务。', created_at: now - 1000 * 60 * 3, status: 'delivered' },
  ],
  c2: [
    { id: 'm4', sender: 'u2', type: 'text', content: 'OpenSearch 索引字段我已经补完，你前端可以按 file_category 过滤。', created_at: now - 1000 * 60 * 30, status: 'read' },
  ],
}

function normalizeNotification(item) {
  return {
    id: item.id || item.notification_id || crypto.randomUUID(),
    type: item.type || 'info',
    title: item.title || '系统消息',
    message: item.message || item.content || '',
    time: item.time || item.created_at || Date.now(),
    read: Boolean(item.read ?? item.is_read),
    category: item.category || '系统',
  }
}

export const useNotificationStore = defineStore('notification', () => {
  const authStore = useAuthStore()
  const notifications = ref([...seedNotifications])
  const friends = ref([...seedFriends])
  const conversations = ref([...seedConversations])
  const messagesByConversation = ref({ ...seedMessages })
  const activeConversationId = ref(seedConversations[0]?.id || null)
  const loading = ref(false)
  const realtimeStatus = ref('offline')
  const backendReady = ref(false)
  const userSearchResults = ref([])
  const socket = ref(null)

  const unreadCount = computed(() => notifications.value.filter(item => !item.read).length)
  const chatUnreadCount = computed(() => conversations.value.reduce((sum, item) => sum + (item.unread || 0), 0))
  const recentNotifications = computed(() => notifications.value.slice(0, 5))
  const activeConversation = computed(() => conversations.value.find(item => item.id === activeConversationId.value) || null)
  const activeFriend = computed(() => friends.value.find(item => item.id === activeConversation.value?.friend_id) || null)
  const activeMessages = computed(() => messagesByConversation.value[activeConversationId.value] || [])
  const sortedConversations = computed(() => [...conversations.value].sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.updated_at - a.updated_at))

  async function bootstrap() {
    loading.value = true
    await Promise.allSettled([fetchNotifications(), fetchFriends(), fetchConversations()])
    loading.value = false
    connectRealtime()
  }

  async function fetchNotifications() {
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

  async function fetchFriends() {
    try {
      const res = await getFriendsApi()
      if (res.code === 200 && Array.isArray(res.data)) friends.value = res.data
    } catch {
      // 后端未就绪时保留企业级占位数据
    }
  }

  async function fetchConversations() {
    try {
      const res = await getConversationsApi()
      if (res.code === 200 && Array.isArray(res.data)) conversations.value = res.data
    } catch {
      // 后端未就绪时保留企业级占位数据
    }
  }

  async function openConversation(conversationId) {
    activeConversationId.value = conversationId
    const conversation = conversations.value.find(item => item.id === conversationId)
    if (conversation) conversation.unread = 0
    try {
      const res = await getConversationMessagesApi(conversationId, { limit: 50 })
      if (res.code === 200 && Array.isArray(res.data)) {
        messagesByConversation.value[conversationId] = res.data
      }
    } catch {
      if (!messagesByConversation.value[conversationId]) messagesByConversation.value[conversationId] = []
    }
  }

  async function startDirectConversation(friendId) {
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
        updated_at: Date.now(),
      }
      conversations.value.unshift(existing)
      messagesByConversation.value[existing.id] = []
      try {
        const res = await createDirectConversationApi(friendId)
        if (res.code === 200 && res.data?.id) existing.id = res.data.id
      } catch {
        // 保留本地会话，后端接入后会自动替换
      }
    }
    await openConversation(existing.id)
  }

  async function sendMessage(payload) {
    if (!activeConversationId.value || !payload.content?.trim()) return
    const message = {
      id: `local-${Date.now()}`,
      sender: 'me',
      type: payload.type || 'text',
      content: payload.content.trim(),
      file_id: payload.file_id || '',
      share_url: payload.share_url || '',
      created_at: Date.now(),
      status: 'sending',
    }
    appendMessage(activeConversationId.value, message)
    try {
      const res = await sendChatMessageApi(activeConversationId.value, message)
      message.status = res.code === 200 ? 'sent' : 'failed'
      if (res.data?.id) message.id = res.data.id
    } catch {
      message.status = 'local'
    }
  }

  async function searchUsers(keyword) {
    if (!keyword?.trim()) {
      userSearchResults.value = []
      return
    }
    try {
      const res = await searchUsersApi(keyword.trim())
      userSearchResults.value = res.code === 200 && Array.isArray(res.data) ? res.data : []
    } catch {
      userSearchResults.value = seedFriends.filter(item => item.account.includes(keyword) || item.name.includes(keyword))
    }
  }

  async function sendFriendRequest(account, remark = '') {
    if (!account?.trim()) return { success: false, message: '请输入平台账号' }
    try {
      const res = await sendFriendRequestApi(account.trim(), remark)
      if (res.code === 200) return { success: true }
      return { success: false, message: res.message || '好友申请发送失败' }
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

  function appendMessage(conversationId, message) {
    const current = messagesByConversation.value[conversationId] || []
    messagesByConversation.value[conversationId] = [...current, message]
    const conversation = conversations.value.find(item => item.id === conversationId)
    if (conversation) {
      conversation.updated_at = message.created_at
      if (message.sender !== 'me' && conversationId !== activeConversationId.value) conversation.unread = (conversation.unread || 0) + 1
    }
  }

  function ingestRealtimeEvent(event) {
    if (event.type === 'notification') notifications.value.unshift(normalizeNotification(event.payload))
    if (event.type === 'message') appendMessage(event.payload.conversation_id, event.payload)
  }

  function connectRealtime() {
    if (!authStore.token || socket.value) return
    try {
      const base = import.meta.env.VITE_WS_BASE_URL || `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/api/v1`
      socket.value = new WebSocket(`${base}/business/collaboration/ws?token=${encodeURIComponent(authStore.token)}`)
      realtimeStatus.value = 'connecting'
      socket.value.onopen = () => { realtimeStatus.value = 'online' }
      socket.value.onclose = () => { realtimeStatus.value = 'offline'; socket.value = null }
      socket.value.onerror = () => { realtimeStatus.value = 'degraded' }
      socket.value.onmessage = (event) => ingestRealtimeEvent(JSON.parse(event.data))
    } catch {
      realtimeStatus.value = 'degraded'
    }
  }

  function markAsRead(id) {
    const item = notifications.value.find(notification => notification.id === id)
    if (item) item.read = true
    markNotificationReadApi(id).catch(() => {})
  }

  function markAllAsRead() {
    notifications.value.forEach(item => { item.read = true })
    markAllNotificationsReadApi().catch(() => {})
  }

  return {
    notifications,
    friends,
    conversations,
    messagesByConversation,
    activeConversationId,
    loading,
    realtimeStatus,
    backendReady,
    userSearchResults,
    unreadCount,
    chatUnreadCount,
    recentNotifications,
    activeConversation,
    activeFriend,
    activeMessages,
    sortedConversations,
    bootstrap,
    fetchNotifications,
    fetchFriends,
    fetchConversations,
    openConversation,
    startDirectConversation,
    sendMessage,
    searchUsers,
    sendFriendRequest,
    connectRealtime,
    markAsRead,
    markAllAsRead,
  }
})
