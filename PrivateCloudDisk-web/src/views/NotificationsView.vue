<template>
  <div class="collab-page">
    <header class="collab-header">
      <div>
        <h1>协作消息中心</h1>
        <p>系统通知、好友会话、文件与分享链接协作都集中在这里。</p>
      </div>
      <div class="header-actions">
        <span class="status-pill" :class="notificationStore.realtimeStatus">
          <i class="fa fa-circle"></i>
          {{ realtimeText }}
        </span>
        <span v-if="syncStatusText" class="sync-status" :class="syncStatusClass">
          <i :class="syncStatusIcon"></i>
          {{ syncStatusText }}
        </span>
        <button @click="notificationStore.markAllAsRead">
          <i class="fa fa-check-circle-o"></i>
          <span>通知全已读</span>
        </button>
      </div>
    </header>

    <section class="stats-grid">
      <div v-for="stat in stats" :key="stat.label" class="stat-card">
        <span :class="stat.bg"><i :class="stat.icon"></i></span>
        <div>
          <p>{{ stat.label }}</p>
          <strong>{{ stat.value }}</strong>
        </div>
      </div>
    </section>

    <!-- 消息同步状态横幅 -->
    <div v-if="notificationStore.syncStatus === 'syncing'" class="sync-banner syncing">
      <i class="fa fa-refresh fa-spin"></i> 正在同步消息...
    </div>
    <div v-else-if="notificationStore.syncStatus === 'error'" class="sync-banner error">
      <i class="fa fa-exclamation-triangle"></i> 消息同步失败，请检查网络
      <button @click="notificationStore.bootstrap">重试</button>
    </div>

    <section class="collab-shell">
      <aside class="conversation-panel">
        <div class="panel-title">
          <div>
            <strong>会话</strong>
            <p>{{ notificationStore.chatUnreadCount }} 条未读私信</p>
          </div>
          <button title="刷新" @click="notificationStore.bootstrap">
            <i class="fa fa-refresh"></i>
          </button>
        </div>

        <div class="search-line">
          <i class="fa fa-search"></i>
          <input v-model="conversationKeyword" placeholder="搜索同事或会话" />
        </div>

        <div class="conversation-list">
          <button
            v-for="conversation in filteredConversations"
            :key="conversation.id"
            class="conversation-item"
            :class="{ active: conversation.id === notificationStore.activeConversationId }"
            @click="notificationStore.openConversation(conversation.id)"
          >
            <div class="avatar">
              <span>{{ conversation.title.slice(0, 1) }}</span>
              <i v-if="friendOf(conversation)?.online" class="online-dot"></i>
            </div>
            <div class="min-w-0 flex-1">
              <div class="flex items-center justify-between gap-2">
                <strong class="truncate">{{ conversation.title }}</strong>
                <time>{{ timeAgo(conversation.updated_at) }}</time>
              </div>
              <p class="truncate">{{ latestMessage(conversation.id) }}</p>
            </div>
            <span v-if="conversation.unread" class="unread-badge">{{ conversation.unread }}</span>
          </button>
        </div>

        <div class="system-section">
          <div class="panel-title compact">
            <strong>系统通知</strong>
            <span>{{ notificationStore.unreadCount }} 未读</span>
          </div>
          <button
            v-for="item in notificationStore.recentNotifications"
            :key="item.id"
            class="notice-item"
            @click="notificationStore.markAsRead(item.id)"
          >
            <i :class="[typeMeta(item.type).icon, typeMeta(item.type).color]"></i>
            <div class="min-w-0">
              <strong class="truncate">{{ item.title }}</strong>
              <p class="truncate">{{ item.message }}</p>
            </div>
            <span v-if="!item.read"></span>
          </button>
        </div>
      </aside>

      <main class="chat-panel">
        <template v-if="notificationStore.activeConversation">
          <div class="chat-head">
            <div class="avatar large">
              <span>{{ notificationStore.activeConversation.title.slice(0, 1) }}</span>
            </div>
            <div class="min-w-0">
              <h2>{{ notificationStore.activeConversation.title }}</h2>
              <p>{{ notificationStore.activeConversation.subtitle || '平台联系人' }}</p>
            </div>
            <div class="chat-head-actions">
              <!-- 视频通话按钮 -->
              <button class="call-btn video-call" @click="startVideoCall('video')" title="视频通话">
                <i class="fa fa-video-camera"></i>
              </button>
              <button class="call-btn voice-call" @click="startVideoCall('voice')" title="语音通话">
                <i class="fa fa-phone"></i>
              </button>
            </div>
          </div>

          <!-- 正在输入提示 -->
          <div v-if="typingHint" class="typing-indicator">
            <span class="typing-dots"><i></i><i></i><i></i></span>
            {{ typingHint }} 正在输入...
          </div>

          <div ref="messagesRef" class="message-stream">
            <div
              v-for="message in notificationStore.activeMessages"
              :key="message.id"
              class="message-row"
              :class="{ mine: message.sender === 'me' }"
            >
              <!-- 视频通话邀请消息 -->
              <div v-if="message.type === 'video_call'" class="message-bubble call-invite">
                <div class="call-invite-content">
                  <div class="call-invite-icon">
                    <i :class="message.callType === 'video' ? 'fa fa-video-camera' : 'fa fa-phone'"></i>
                  </div>
                  <div class="call-invite-text">
                    <strong>{{ message.sender === 'me' ? '你发起了' : '对方发起了' }}{{ message.callType === 'video' ? '视频通话' : '语音通话' }}</strong>
                    <p>{{ message.content }}</p>
                  </div>
                </div>
                <!-- 如果是对面发来的邀请且当前通话未激活 -->
                <div v-if="message.sender !== 'me' && !notificationStore.isCallActive" class="call-invite-actions">
                  <button class="btn-accept" @click="acceptCallFromMessage(message)">
                    <i class="fa fa-phone"></i> 接听
                  </button>
                  <button class="btn-reject" @click="rejectCallFromMessage(message)">
                    <i class="fa fa-times"></i> 拒绝
                  </button>
                </div>
                <div v-else-if="message.sender !== 'me' && notificationStore.isCallActive" class="call-invite-status">
                  <span class="status-ended">通话已结束</span>
                </div>
                <footer>
                  <time>{{ formatDateTime(message.created_at) }}</time>
                </footer>
              </div>

              <!-- 普通消息 -->
              <div v-else class="message-bubble" :class="message.type">
                <div v-if="message.type === 'file'" class="attachment">
                  <i class="fa fa-file-o"></i>
                  <div>
                    <strong>{{ message.content }}</strong>
                    <p>平台文件 ID: {{ message.file_id || '待绑定' }}</p>
                  </div>
                </div>
                <div v-else-if="message.type === 'share'" class="attachment">
                  <i class="fa fa-link"></i>
                  <div>
                    <strong>{{ message.content }}</strong>
                    <p>{{ message.share_url }}</p>
                  </div>
                </div>
                <p v-else>{{ message.content }}</p>
                <footer>
                  <time>{{ formatDateTime(message.created_at) }}</time>
                  <!-- 消息状态指示器（仅自己发送的消息显示） -->
                  <span v-if="message.sender === 'me'" class="message-status">
                    <!-- 发送中 -->
                    <i v-if="message.status === 'sending' || message.syncStatus === 'syncing'" class="fa fa-circle-o-notch fa-spin status-sending" title="发送中"></i>
                    <!-- 已发送 -->
                    <i v-else-if="message.status === 'sent'" class="fa fa-check status-sent" title="已发送"></i>
                    <!-- 已送达 -->
                    <i v-else-if="message.status === 'delivered'" class="fa fa-check-circle status-delivered" title="已送达"></i>
                    <!-- 已读 -->
                    <i v-else-if="message.status === 'read'" class="fa fa-check-circle status-read" title="已读"></i>
                    <!-- 发送失败 -->
                    <span v-else-if="message.status === 'failed' || message.syncStatus === 'failed'" class="status-failed-group">
                      <i class="fa fa-exclamation-circle status-failed" title="发送失败"></i>
                      <button class="retry-btn" @click="retryMessage(message)" title="重试发送">
                        <i class="fa fa-refresh"></i>
                      </button>
                    </span>
                    <!-- 已撤回 -->
                    <i v-else-if="message.status === 'recalled'" class="fa fa-undo status-recalled" title="已撤回"></i>
                  </span>
                </footer>
              </div>
            </div>
          </div>

          <form class="composer" @submit.prevent="sendText">
            <div class="composer-tools">
              <button type="button" :class="{ active: composerMode === 'text' }" @click="composerMode = 'text'"><i class="fa fa-comment-o"></i>消息</button>
              <button type="button" :class="{ active: composerMode === 'file' }" @click="composerMode = 'file'"><i class="fa fa-file-o"></i>文件</button>
              <button type="button" :class="{ active: composerMode === 'share' }" @click="composerMode = 'share'"><i class="fa fa-link"></i>链接</button>
            </div>
            <div v-if="composerMode !== 'text'" class="attachment-form">
              <input v-if="composerMode === 'file'" v-model="attachmentId" placeholder="输入系统内文件 ID，后续可接文件选择器" />
              <input v-else v-model="shareUrl" placeholder="粘贴分享链接或 /share/xxx" />
            </div>
            <div class="composer-input">
              <textarea v-model="draft" rows="2" :placeholder="composerPlaceholder" @keydown.enter.exact.prevent="sendText"></textarea>
              <button type="submit"><i class="fa fa-paper-plane"></i></button>
            </div>
          </form>
        </template>
        <PageState v-else title="选择一个会话" description="选择联系人后开始发送消息、文件或分享链接。" />
      </main>

      <aside class="people-panel">
        <div class="panel-title">
          <div>
            <strong>联系人</strong>
            <p>{{ notificationStore.friends.length }} 位平台好友</p>
          </div>
        </div>

        <form class="add-friend" @submit.prevent="addFriend">
          <input v-model="friendAccount" placeholder="输入同事平台账号" />
          <button type="submit"><i class="fa fa-user-plus"></i></button>
        </form>

        <div class="friend-list">
          <button
            v-for="friend in notificationStore.friends"
            :key="friend.id"
            class="friend-item"
            @click="notificationStore.startDirectConversation(friend.id)"
          >
            <div class="avatar">
              <span>{{ friend.name.slice(0, 1) }}</span>
              <i v-if="friend.online" class="online-dot"></i>
            </div>
            <div class="min-w-0">
              <strong class="truncate">{{ friend.name }}</strong>
              <p class="truncate">{{ friend.account }} · {{ friend.role || friend.email }}</p>
            </div>
            <!-- 联系人快捷通话按钮 -->
            <div class="friend-call-actions">
              <button class="mini-call-btn" @click.stop="startCallWithFriend(friend, 'video')" title="视频通话">
                <i class="fa fa-video-camera"></i>
              </button>
              <button class="mini-call-btn" @click.stop="startCallWithFriend(friend, 'voice')" title="语音通话">
                <i class="fa fa-phone"></i>
              </button>
            </div>
          </button>
        </div>

        <div class="share-hint">
          <i class="fa fa-share-alt"></i>
          <div>
            <strong>协作分享预留</strong>
            <p>后续文件列表可直接调用当前会话发送文件 ID 或分享链接。</p>
          </div>
        </div>
      </aside>
    </section>

    <!-- 来电弹窗 — 由 useCall 信令层驱动 -->
    <IncomingCallDialog
      :visible="call.hasIncomingCall.value"
      :incoming-call-info="incomingCallDialogInfo"
      @accept="handleAcceptCall"
      @reject="handleRejectCall"
    />

    <!-- 浮窗通话组件 -->
    <FloatingCallWindow
      :visible="showFloatingCall"
      :peer-name="floatingCallPeerName"
      :is-video="floatingCallIsVideo"
      :call-duration="floatingCallDuration"
      :is-muted="isCallMuted"
      :is-camera-off="isCallCameraOff"
      :local-stream="callLocalStream"
      :remote-stream="callRemoteStream"
      @hangup="handleHangupCall"
      @toggle-mute="toggleMute"
      @toggle-camera="toggleCamera"
      @fullscreen="goToFullscreenCall"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import PageState from '@/components/common/PageState.vue'
import IncomingCallDialog from '@/components/im/IncomingCallDialog.vue'
import FloatingCallWindow from '@/components/im/FloatingCallWindow.vue'
import { useNotificationStore } from '@/stores/notificationStore'
import { useToastStore } from '@/stores/toastStore'
import { useCall } from '@/composables/useCall'
import { formatDateTime, timeAgo } from '@/utils/helpers'
import type { Friend, ChatMessage } from '@/stores/notificationStore'
import { CallType, CallStatus } from '@/api/im/types'

const notificationStore = useNotificationStore()
const toastStore = useToastStore()
const router = useRouter()
const call = useCall()

const conversationKeyword = ref('')
const draft = ref('')
const composerMode = ref('text')
const attachmentId = ref('')
const shareUrl = ref('')
const friendAccount = ref('')
const messagesRef = ref<HTMLDivElement | null>(null)

// ---- 通话相关 ----
const isCallMuted = ref(false)
const isCallCameraOff = ref(false)
const callLocalStream = ref<MediaStream | null>(null)
const callRemoteStream = ref<MediaStream | null>(null)
const callStartTime = ref<number>(0)
const callDurationRef = ref('00:00')
let callDurationTimer: ReturnType<typeof setInterval> | null = null

// ---- 计算属性 ----

const realtimeText = computed(() => {
  const map: Record<string, string> = {
    online: '实时在线', connecting: '连接中', reconnecting: '重连中',
    degraded: '降级模式', kicked: '已被踢下线', offline: '离线占位'
  }
  return map[notificationStore.realtimeStatus] || '离线占位'
})

const syncStatusText = computed(() => {
  const map: Record<string, string> = {
    syncing: '消息同步中...', error: '同步失败', idle: '',
  }
  return map[notificationStore.syncStatus] || ''
})

const syncStatusIcon = computed(() => {
  if (notificationStore.syncStatus === 'syncing') return 'fa fa-refresh fa-spin'
  if (notificationStore.syncStatus === 'error') return 'fa fa-exclamation-triangle'
  return ''
})

const syncStatusClass = computed(() => notificationStore.syncStatus)

const stats = computed(() => [
  { label: '系统未读', value: notificationStore.unreadCount, icon: 'fa fa-bell', bg: 'bg-primary/10 text-primary' },
  { label: '私信未读', value: notificationStore.chatUnreadCount, icon: 'fa fa-comments-o', bg: 'bg-danger/10 text-danger' },
  { label: '联系人', value: notificationStore.friends.length, icon: 'fa fa-address-book-o', bg: 'bg-success/10 text-success' },
  { label: '会话', value: notificationStore.conversations.length, icon: 'fa fa-inbox', bg: 'bg-warning/10 text-warning' },
])

const filteredConversations = computed(() => {
  const keyword = conversationKeyword.value.trim().toLowerCase()
  if (!keyword) return notificationStore.sortedConversations
  return notificationStore.sortedConversations.filter(item =>
    item.title.toLowerCase().includes(keyword) || item.subtitle?.toLowerCase().includes(keyword)
  )
})

const composerPlaceholder = computed(() => {
  if (composerMode.value === 'file') return '给这个文件补一句说明'
  if (composerMode.value === 'share') return '给这个分享链接补一句说明'
  return '输入消息，Enter 发送，Shift + Enter 换行'
})

const typingHint = computed(() => {
  if (!notificationStore.activeConversationId) return null
  return notificationStore.typingUsers[notificationStore.activeConversationId] || null
})

// 来电弹窗 — 使用 useCall 的 incomingCallInfo 作为信令层数据源
const incomingCallDialogInfo = computed(() => {
  if (!call.incomingCallInfo.value) return null
  const info = call.incomingCallInfo.value
  return {
    callId: info.callId || '',
    callType: info.callType === CallType.VIDEO ? 'video' : 'voice',
    callerId: info.callerId || '',
    callerName: info.callerName || '未知用户',
    callerAvatar: info.callerAvatar || undefined,
    conversationId: (info as any).conversationId || '',
    timestamp: (info as any).timestamp || Date.now(),
  }
})

// 浮窗通话控制 — 使用 useCall 的流和状态
const showFloatingCall = computed(() =>
  call.status.value === CallStatus.ACTIVE || call.status.value === CallStatus.RINGING
)

const floatingCallPeerName = computed(() =>
  call.session.value?.calleeName || call.session.value?.callerName || '未知用户'
)

const floatingCallIsVideo = computed(() =>
  call.session.value?.callType === CallType.VIDEO
)

const floatingCallDuration = computed(() => callDurationRef.value)

// ---- 生命周期 ----

onMounted(async () => {
  notificationStore.bootstrap()
  // 初始化 useCall（由 notificationStore 的 connectRealtime 注册 IM 客户端后）
  // 延迟初始化，等待 IM 客户端就绪
  setTimeout(async () => {
    try {
      await call.init()
    } catch (e) {
      console.warn('[NotificationsView] useCall 初始化:', e)
    }
  }, 1000)
})

onBeforeUnmount(() => {
  if (callDurationTimer) clearInterval(callDurationTimer)
  call.destroy()
})

watch(() => notificationStore.activeMessages.length, () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
})

// 监听 useCall 的来电状态（信令层）
watch(() => call.hasIncomingCall.value, (val) => {
  if (val) {
    // 播放铃声提示
    try {
      const audio = new Audio('/sounds/call_ringtone.mp3')
      audio.loop = true
      audio.play().catch(() => {})
      const stopRing = () => {
        audio.pause()
        audio.currentTime = 0
      }
      const unwatchAccept = watch(() => call.hasIncomingCall.value, (v) => {
        if (!v) { stopRing(); unwatchAccept() }
      })
    } catch { /* 忽略音频错误 */ }
  }
})

// 监听 useCall 的流变化，同步到本地 ref
watch(() => call.localStream.value, (stream) => {
  callLocalStream.value = stream || null
})

watch(() => call.remoteStream.value, (stream) => {
  callRemoteStream.value = stream || null
})

// 监听 useCall 的静音/摄像头状态
watch(() => call.isMuted.value, (val) => {
  isCallMuted.value = val
})

watch(() => call.isCameraOff.value, (val) => {
  isCallCameraOff.value = val
})

// 监听 useCall 的通话状态变化，同步 notificationStore 和计时器
watch(() => call.status.value, (newStatus) => {
  if (newStatus === CallStatus.ACTIVE) {
    notificationStore.isCallActive = true
    notificationStore.activeCallSession = {
      callId: call.session.value?.callId || '',
      peerId: call.session.value?.calleeId || call.session.value?.callerId || '',
      peerName: floatingCallPeerName.value,
      callType: floatingCallIsVideo.value ? 'video' : 'voice',
      startTime: Date.now(),
    }
    startCallDurationTimer()
  } else if (newStatus === CallStatus.ENDED) {
    stopCallDurationTimer()
    notificationStore.endCall()
    callLocalStream.value = null
    callRemoteStream.value = null
  }
})

// ---- 方法 ----

function friendOf(conversation: any) {
  return notificationStore.friends.find((item: Friend) => item.id === conversation.friend_id)
}

function latestMessage(conversationId: string) {
  const list = notificationStore.messagesByConversation[conversationId] || []
  const latest = list[list.length - 1]
  if (!latest) return '暂无消息'
  if (latest.type === 'file') return `[文件] ${latest.content}`
  if (latest.type === 'share') return `[链接] ${latest.content}`
  if (latest.type === 'video_call') return `[${latest.callType === 'video' ? '视频' : '语音'}通话]`
  return latest.content
}

function typeMeta(type: string) {
  const map: Record<string, { icon: string; color: string }> = {
    success: { icon: 'fa fa-check-circle', color: 'text-green-500' },
    security: { icon: 'fa fa-shield', color: 'text-red-500' },
    info: { icon: 'fa fa-info-circle', color: 'text-blue-500' },
    warning: { icon: 'fa fa-exclamation-triangle', color: 'text-yellow-500' },
  }
  return map[type] || { icon: 'fa fa-bell', color: 'text-gray-500' }
}

// ---- 消息操作 ----

async function sendText() {
  const content = draft.value.trim()
  if (!content && composerMode.value === 'text') return

  await notificationStore.sendMessage({
    type: composerMode.value,
    content: content || attachmentId.value || shareUrl.value,
    file_id: composerMode.value === 'file' ? attachmentId.value : undefined,
    share_url: composerMode.value === 'share' ? shareUrl.value : undefined,
  })

  draft.value = ''
  attachmentId.value = ''
  shareUrl.value = ''
  composerMode.value = 'text'

  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function retryMessage(message: ChatMessage) {
  if (!notificationStore.activeConversationId) return
  notificationStore.retryMessage(notificationStore.activeConversationId, message.id)
}

async function addFriend() {
  const account = friendAccount.value.trim()
  if (!account) return
  const result = await notificationStore.sendFriendRequest(account)
  if (result.success) {
    friendAccount.value = ''
    toastStore.show('好友申请已发送', 'success')
  } else {
    toastStore.show(result.message || '操作失败', 'error')
  }
}

// ---- 视频通话操作 ----

/**
 * 发起视频/语音通话
 * 1. 通过 notificationStore 发送 video_call 消息到聊天中
 * 2. 通过 useCall 启动 WebRTC 信令和媒体采集
 */
async function startVideoCall(callType: 'video' | 'voice') {
  const conversation = notificationStore.activeConversation
  if (!conversation) return

  const calleeId = conversation.friend_id || ''
  const calleeName = conversation.title || '未知用户'
  const calleeAvatar = ''

  // 1. 发送视频邀请消息到聊天中
  await notificationStore.sendVideoCallInvite(callType)

  // 2. 启动 WebRTC 通话
  try {
    const ct = callType === 'video' ? CallType.VIDEO : CallType.VOICE
    await call.startCall(calleeId, calleeName, calleeAvatar, ct)
  } catch (e: any) {
    console.error('[NotificationsView] 发起通话失败:', e)
    toastStore.show(e?.message || '发起通话失败', 'error')
  }
}

/**
 * 从联系人列表直接发起通话
 */
async function startCallWithFriend(friend: Friend, callType: 'video' | 'voice') {
  await notificationStore.startDirectConversation(friend.id)
  // 等待会话切换完成
  await nextTick()
  await startVideoCall(callType)
}

/**
 * 接听来电（信令层）
 */
async function handleAcceptCall() {
  try {
    await call.acceptIncomingCall()
    // 状态同步由 watch(call.status) 处理
  } catch (e: any) {
    console.error('[NotificationsView] 接听失败:', e)
    toastStore.show(e?.message || '接听失败', 'error')
  }
}

/**
 * 拒绝来电（信令层）
 */
function handleRejectCall() {
  call.rejectIncomingCall('用户拒绝')
  notificationStore.rejectIncomingCall()
}

/**
 * 从聊天消息中的邀请点击接听
 */
function acceptCallFromMessage(message: ChatMessage) {
  // 如果 useCall 已经有来电信息，直接接听
  if (call.hasIncomingCall.value) {
    handleAcceptCall()
  } else {
    // 否则，先解析消息中的 extra 信息来初始化通话
    try {
      const extra = message.extra ? JSON.parse(message.extra) : null
      if (extra?.callId) {
        // 手动设置 incomingCallInfo 以便 acceptIncomingCall 能工作
        call.incomingCallInfo.value = {
          callId: extra.callId,
          callType: extra.callType === 'video' ? CallType.VIDEO : CallType.VOICE,
          callerId: extra.callerId || '',
          callerName: extra.callerName || '未知用户',
          callerAvatar: '',
          timestamp: extra.timestamp || Date.now(),
        }
        call.hasIncomingCall.value = true
        handleAcceptCall()
      }
    } catch {
      toastStore.show('无法解析通话邀请', 'error')
    }
  }
}

function rejectCallFromMessage(_message: ChatMessage) {
  handleRejectCall()
}

/**
 * 挂断通话
 */
function handleHangupCall() {
  call.hangup()
  stopCallDurationTimer()
  callLocalStream.value = null
  callRemoteStream.value = null
}

function toggleMute() {
  call.toggleMute()
}

function toggleCamera() {
  call.toggleCamera()
}

function goToFullscreenCall() {
  if (call.session.value) {
    router.push({
      name: 'Call',
      query: {
        callId: call.session.value.callId,
        peerId: call.session.value.calleeId || call.session.value.callerId || '',
        peerName: floatingCallPeerName.value,
        callType: floatingCallIsVideo.value ? 'video' : 'voice',
      }
    })
  }
}

function startCallDurationTimer() {
  callStartTime.value = Date.now()
  callDurationTimer = setInterval(() => {
    const elapsed = Math.floor((Date.now() - callStartTime.value) / 1000)
    const mins = Math.floor(elapsed / 60)
    const secs = elapsed % 60
    callDurationRef.value = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
  }, 1000)
}

function stopCallDurationTimer() {
  if (callDurationTimer) {
    clearInterval(callDurationTimer)
    callDurationTimer = null
  }
  callDurationRef.value = '00:00'
}
</script>

<style scoped>
/* ============================================================
   Collab Page — 协作消息中心整体布局
   ============================================================ */
.collab-page {
  display: grid;
  gap: 16px;
}

.collab-header,
.stat-card,
.conversation-panel,
.chat-panel,
.people-panel {
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.collab-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
}

.collab-header h1 {
  color: #1e1e1e;
  font-size: 24px;
  font-weight: 800;
}

.collab-header p,
.panel-title p {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}

.header-actions,
.stats-grid,
.composer-tools,
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-actions button,
.composer-tools button,
.add-friend button,
.composer-input button,
.panel-title button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 38px;
  border-radius: 8px;
  padding: 0 12px;
  color: #165dff;
  transition: background 0.16s ease;
}

.header-actions button:hover,
.composer-tools button:hover,
.composer-tools button.active {
  background: rgba(22, 93, 255, 0.08);
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 999px;
  background: #f5f7fa;
  padding: 8px 10px;
  color: #606266;
  font-size: 12px;
}

.status-pill.online { color: #52c41a; }
.status-pill.connecting { color: #faad14; }
.status-pill.degraded { color: #faad14; }

.stats-grid {
  align-items: stretch;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
}

.stat-card > span {
  display: flex;
  height: 42px;
  width: 42px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.stat-card p {
  color: #909399;
  font-size: 12px;
}

.stat-card strong {
  color: #303133;
  font-size: 22px;
}

.collab-shell {
  display: grid;
  min-height: 620px;
  grid-template-columns: 300px minmax(0, 1fr) 280px;
  gap: 16px;
}

.conversation-panel,
.people-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f2f5;
  padding: 14px;
}

.panel-title.compact {
  border-top: 1px solid #f0f2f5;
}

.search-line,
.add-friend,
.attachment-form,
.composer-input {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px;
  border-radius: 8px;
  background: #f5f7fa;
  padding: 0 10px;
}

.search-line input,
.add-friend input,
.attachment-form input,
.composer-input textarea {
  min-width: 0;
  flex: 1;
  border: 0;
  background: transparent;
  outline: 0;
}

.search-line input,
.add-friend input,
.attachment-form input {
  height: 40px;
}

.conversation-list,
.friend-list {
  min-height: 0;
  overflow-y: auto;
}

.conversation-item,
.friend-item,
.notice-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  text-align: left;
  transition: background 0.16s ease;
}

.conversation-item:hover,
.conversation-item.active,
.friend-item:hover,
.notice-item:hover {
  background: rgba(22, 93, 255, 0.06);
}

.avatar {
  position: relative;
  display: flex;
  height: 40px;
  width: 40px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(22, 93, 255, 0.1);
  color: #165dff;
  font-weight: 800;
}

.avatar.large {
  height: 48px;
  width: 48px;
}

.online-dot {
  position: absolute;
  bottom: 1px;
  right: 1px;
  height: 10px;
  width: 10px;
  border-radius: 999px;
  background: #52c41a;
  box-shadow: 0 0 0 2px #fff;
}

.conversation-item time {
  color: #c0c6cf;
  font-size: 11px;
}

.conversation-item p,
.friend-item p,
.notice-item p {
  color: #909399;
  font-size: 12px;
}

.unread-badge {
  border-radius: 999px;
  background: #ff4d4f;
  color: #fff;
  font-size: 11px;
  line-height: 1;
  padding: 5px 7px;
}

.system-section {
  margin-top: auto;
}

.notice-item {
  position: relative;
  padding-block: 10px;
}

.notice-item > span {
  height: 8px;
  width: 8px;
  border-radius: 999px;
  background: #ff4d4f;
}

.chat-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
}

.chat-head {
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #f0f2f5;
  padding: 14px 16px;
}

.chat-head h2 {
  color: #303133;
  font-size: 17px;
  font-weight: 800;
}

.chat-head p {
  color: #909399;
  font-size: 13px;
}

.message-stream {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 18px;
}

.message-row {
  display: flex;
  margin-bottom: 14px;
}

.message-row.mine {
  justify-content: flex-end;
}

.message-bubble {
  max-width: min(620px, 78%);
  border-radius: 8px;
  background: #f5f7fa;
  padding: 10px 12px;
  color: #303133;
}

.message-row.mine .message-bubble {
  background: #165dff;
  color: #fff;
}

.message-bubble footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 6px;
  color: currentColor;
  font-size: 11px;
  opacity: 0.66;
}

.attachment {
  display: flex;
  gap: 10px;
}

.attachment i {
  margin-top: 2px;
  font-size: 20px;
}

.attachment p {
  margin-top: 2px;
  opacity: 0.7;
  word-break: break-all;
}

.composer {
  border-top: 1px solid #f0f2f5;
  padding: 10px 12px 12px;
}

.composer-tools {
  margin-bottom: 8px;
}

.composer-tools button {
  min-height: 32px;
  color: #606266;
  font-size: 12px;
}

.attachment-form {
  margin: 0 0 8px;
}

.composer-input {
  margin: 0;
  align-items: end;
  padding: 8px 8px 8px 12px;
}

.composer-input textarea {
  resize: none;
  line-height: 1.6;
  padding-block: 4px;
}

.composer-input button,
.add-friend button {
  background: #165dff;
  color: #fff;
}

.share-hint {
  display: flex;
  gap: 10px;
  margin: auto 12px 12px;
  border-radius: 8px;
  background: #f5f7fa;
  padding: 12px;
  color: #606266;
  font-size: 12px;
}

.share-hint i {
  color: #165dff;
  font-size: 18px;
}

@media (max-width: 1180px) {
  .collab-shell {
    grid-template-columns: 280px minmax(0, 1fr);
}

  .people-panel {
    grid-column: 1 / -1;
    min-height: 260px;
  }

  .friend-list {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .collab-header {
    align-items: stretch;
    flex-direction: column;
  }

  .stats-grid,
  .collab-shell,
  .friend-list {
    grid-template-columns: 1fr;
  }

  .message-bubble {
    max-width: 92%;
  }
}

/* ============================================================
   响应式：中屏（平板）
   ============================================================ */
@media (max-width: 1180px) {
  .collab-shell {
    grid-template-columns: 280px minmax(0, 1fr);
  }

  .people-panel {
    grid-column: 1 / -1;
    min-height: 260px;
  }

  .friend-list {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

/* ============================================================
   响应式：小屏手机
   ============================================================ */
@media (max-width: 760px) {
  .collab-header {
    align-items: stretch;
    flex-direction: column;
  }

  .collab-header h1 {
    font-size: 20px;
  }

  .header-actions {
    flex-wrap: wrap;
  }

  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .collab-shell {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  /* 移动端：会话面板和聊天面板切换显示 */
  .conversation-panel {
    display: flex;
  }

  .chat-panel {
    display: flex;
  }

  .conversation-panel.mobile-hidden,
  .chat-panel.mobile-hidden {
    display: none;
  }

  .people-panel {
    min-height: auto;
  }

  .friend-list {
    grid-template-columns: 1fr;
  }

  .message-bubble {
    max-width: 92%;
  }

  .message-stream {
    padding: 12px;
  }

  .composer {
    padding: 8px 8px 10px;
  }

  .composer-tools {
    flex-wrap: wrap;
    gap: 4px;
  }

  .composer-tools button {
    flex: 1;
    min-width: 0;
    justify-content: center;
    font-size: 11px;
  }
}

/* ============================================================
   响应式：极小屏手机
   ============================================================ */
@media (max-width: 400px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .collab-header {
    padding: 12px;
  }

  .panel-title {
    padding: 10px;
  }

  .conversation-item,
  .friend-item {
    padding: 10px;
  }
}

/* ============================================================
   移动端返回按钮
   ============================================================ */
.mobile-back-btn {
  display: none;
}

@media (max-width: 760px) {
  .mobile-back-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    height: 36px;
    width: 36px;
    border-radius: 8px;
    border: 1px solid #e4e7ed;
    background: #fff;
    color: #606266;
    font-size: 16px;
    cursor: pointer;
    transition: background 0.16s ease;
  }
  .mobile-back-btn:hover {
    background: #f5f7fa;
  }
}

.sync-status.error {
  color: #ff4d4f;
}

/* ============================================================
   消息同步状态横幅
   ============================================================ */
.sync-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  border-radius: 8px;
  padding: 10px 16px;
  font-size: 13px;
}

.sync-banner.syncing {
  background: rgba(22, 93, 255, 0.06);
  color: #165dff;
}

.sync-banner.error {
  background: rgba(255, 77, 79, 0.06);
  color: #ff4d4f;
}

.sync-banner.error button {
  margin-left: 4px;
  border: 0;
  background: transparent;
  color: #165dff;
  font-size: 12px;
  cursor: pointer;
  text-decoration: underline;
}

.sync-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: 999px;
  background: #f5f7fa;
  padding: 4px 10px;
  font-size: 12px;
  color: #606266;
}

.sync-status.syncing {
  color: #165dff;
}

/* ============================================================
   新增：聊天头部通话按钮
   ============================================================ */
.chat-head-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.call-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 1px solid #e0e3e9;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: background 0.16s ease, border-color 0.16s ease, color 0.16s ease;
  font-size: 15px;
}

.call-btn:hover {
  background: #f5f7fa;
}

.call-btn.video-call {
  color: #165dff;
}

.call-btn.video-call:hover {
  border-color: #165dff;
  background: rgba(22, 93, 255, 0.06);
}

.call-btn.voice-call {
  color: #52c41a;
}

.call-btn.voice-call:hover {
  border-color: #52c41a;
  background: rgba(82, 196, 26, 0.06);
}

/* ============================================================
   新增：联系人列表快捷通话按钮
   ============================================================ */
.friend-call-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-left: auto;
  opacity: 0;
  transition: opacity 0.16s ease;
}

.friend-item:hover .friend-call-actions {
  opacity: 1;
}

.mini-call-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease;
  font-size: 13px;
  color: #909399;
}

.mini-call-btn:hover {
  background: rgba(22, 93, 255, 0.08);
  color: #165dff;
}

/* ============================================================
   新增：正在输入指示器
   ============================================================ */
.typing-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 18px;
  color: #909399;
  font-size: 12px;
}

.typing-dots {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.typing-dots i {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #909399;
  animation: typing-bounce 1.2s infinite ease-in-out;
}

.typing-dots i:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dots i:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing-bounce {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

/* ============================================================
   新增：视频通话邀请消息气泡
   ============================================================ */
.message-bubble.call-invite {
  background: linear-gradient(135deg, rgba(22, 93, 255, 0.04), rgba(82, 196, 26, 0.04));
  border: 1px solid rgba(22, 93, 255, 0.12);
  padding: 12px 14px;
}

.message-row.mine .message-bubble.call-invite {
  background: linear-gradient(135deg, #165dff, #3b7dff);
  border-color: transparent;
  color: #fff;
}

.call-invite-content {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.call-invite-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(22, 93, 255, 0.1);
  color: #165dff;
  font-size: 16px;
  flex-shrink: 0;
}

.message-row.mine .call-invite-icon {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}

.call-invite-text strong {
  display: block;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 2px;
}

.call-invite-text p {
  font-size: 12px;
  opacity: 0.7;
  margin: 0;
}

.call-invite-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(22, 93, 255, 0.1);
}

.message-row.mine .call-invite-actions {
  border-top-color: rgba(255, 255, 255, 0.15);
}

.btn-accept,
.btn-reject {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border: 0;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.16s ease, transform 0.1s ease;
}

.btn-accept:active,
.btn-reject:active {
  transform: scale(0.96);
}

.btn-accept {
  background: #52c41a;
  color: #fff;
}

.btn-accept:hover {
  background: #49b014;
}

.btn-reject {
  background: #f5f7fa;
  color: #ff4d4f;
  border: 1px solid #e0e3e9;
}

.btn-reject:hover {
  background: #ff4d4f;
  color: #fff;
  border-color: #ff4d4f;
}

.call-invite-status {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(22, 93, 255, 0.1);
}

.status-ended {
  font-size: 12px;
  color: #909399;
  font-style: italic;
}

.message-row.mine .status-ended {
  color: rgba(255, 255, 255, 0.65);
}

/* ============================================================
   新增：消息状态指示器
   ============================================================ */
.message-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.message-status .status-sending {
  color: #909399;
  font-size: 11px;
}

.message-status .status-sent {
  color: #909399;
}

.message-status .status-delivered {
  color: #909399;
}

.message-status .status-read {
  color: #52c41a;
}

.status-failed-group {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.status-failed {
  color: #ff4d4f;
  font-size: 12px;
}

.retry-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 77, 79, 0.1);
  color: #ff4d4f;
  cursor: pointer;
  font-size: 10px;
  padding: 0;
  transition: background 0.16s ease;
}

.retry-btn:hover {
  background: rgba(255, 77, 79, 0.2);
}

.status-recalled {
  color: #909399;
  font-size: 12px;
}

/* 自己发送的消息中状态指示器颜色微调 */
.message-row.mine .message-status .status-sending,
.message-row.mine .message-status .status-sent,
.message-row.mine .message-status .status-delivered {
  color: rgba(255, 255, 255, 0.6);
}

.message-row.mine .message-status .status-read {
  color: #b7eb8f;
}

.message-row.mine .status-recalled {
  color: rgba(255, 255, 255, 0.5);
}
</style>