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
          </div>

          <div ref="messagesRef" class="message-stream">
            <div
              v-for="message in notificationStore.activeMessages"
              :key="message.id"
              class="message-row"
              :class="{ mine: message.sender === 'me' }"
            >
              <div class="message-bubble" :class="message.type">
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
                  <span v-if="message.sender === 'me'">{{ statusText(message.status) }}</span>
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
              <textarea v-model="draft" rows="2" :placeholder="composerPlaceholder"></textarea>
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
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import PageState from '@/components/common/PageState.vue'
import { useNotificationStore } from '@/stores/notificationStore'
import { useToastStore } from '@/stores/toastStore'
import { formatDateTime, timeAgo } from '@/utils/helpers'

const notificationStore = useNotificationStore()
const toastStore = useToastStore()
const conversationKeyword = ref('')
const draft = ref('')
const composerMode = ref('text')
const attachmentId = ref('')
const shareUrl = ref('')
const friendAccount = ref('')
const messagesRef = ref(null)

const realtimeText = computed(() => {
  const map = { online: '实时在线', connecting: '连接中', degraded: '降级模式', offline: '离线占位' }
  return map[notificationStore.realtimeStatus] || '离线占位'
})

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

onMounted(() => {
  notificationStore.bootstrap()
})

watch(() => notificationStore.activeMessages.length, () => {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
})

function friendOf(conversation) {
  return notificationStore.friends.find(item => item.id === conversation.friend_id)
}

function latestMessage(conversationId) {
  const list = notificationStore.messagesByConversation[conversationId] || []
  const latest = list[list.length - 1]
  if (!latest) return '暂无消息'
  if (latest.type === 'file') return `[文件] ${latest.content}`
  if (latest.type === 'share') return `[链接] ${latest.content}`
  return latest.content
}

async function sendText() {
  const content = draft.value.trim()
  if (!content && composerMode.value === 'text') return
  if (composerMode.value === 'file' && !attachmentId.value.trim()) return toastStore.showToast('请输入文件 ID', 'error')
  if (composerMode.value === 'share' && !shareUrl.value.trim()) return toastStore.showToast('请输入分享链接', 'error')

  await notificationStore.sendMessage({
    type: composerMode.value,
    content: content || (composerMode.value === 'file' ? `共享文件 ${attachmentId.value}` : '共享链接'),
    file_id: attachmentId.value.trim(),
    share_url: shareUrl.value.trim(),
  })
  draft.value = ''
  attachmentId.value = ''
  shareUrl.value = ''
  composerMode.value = 'text'
}

async function addFriend() {
  const result = await notificationStore.sendFriendRequest(friendAccount.value)
  if (result.success) {
    toastStore.showToast(result.local ? '好友申请已进入待发送队列' : '好友申请已发送', 'success')
    friendAccount.value = ''
  } else {
    toastStore.showToast(result.message, 'error')
  }
}

function statusText(status) {
  const map = { sending: '发送中', sent: '已发送', delivered: '已送达', read: '已读', local: '本地待同步', failed: '失败' }
  return map[status] || status
}

function typeMeta(type) {
  const map = {
    success: { icon: 'fa fa-check', color: 'text-success' },
    warning: { icon: 'fa fa-exclamation-triangle', color: 'text-warning' },
    security: { icon: 'fa fa-shield', color: 'text-primary' },
    info: { icon: 'fa fa-info', color: 'text-neutral-500' },
  }
  return map[type] || map.info
}
</script>

<style scoped>
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
</style>
