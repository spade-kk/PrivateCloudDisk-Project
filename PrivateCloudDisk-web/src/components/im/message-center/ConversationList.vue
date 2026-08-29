<template>
  <section class="conversation-list-panel" :class="{ compact: props.compact }" aria-label="会话列表">
    <header class="account-header">
      <button class="avatar-button" aria-label="打开个人设置" @click="$emit('open-settings')">
        <img v-if="avatar" :src="avatar" alt="" />
        <span v-else>{{ initial }}</span>
      </button>
      <div class="account-copy">
        <strong>{{ displayName }}</strong>
        <span :class="['connection', connectionState]">{{ connectionLabel }}</span>
      </div>
      <button class="icon-button" title="刷新会话" aria-label="刷新会话" @click="$emit('refresh')">
        <i class="fa fa-refresh" :class="{ 'fa-spin': loading }" aria-hidden="true"></i>
      </button>
    </header>

    <label class="search-box">
      <i class="fa fa-search" aria-hidden="true"></i>
      <input v-model="keyword" type="search" autocomplete="off" placeholder="搜索会话或消息" />
      <kbd>⌘K</kbd>
    </label>

    <div v-if="selectedIds.size" class="batch-bar">
      <span>已选择 {{ selectedIds.size }} 项</span>
      <button @click="markSelectedRead">标记已读</button>
      <button @click="selectedIds.clear()">取消</button>
    </div>

    <div v-if="loading" class="conversation-skeleton" aria-label="正在加载会话">
      <div v-for="item in 7" :key="item" class="skeleton-row"><i></i><span></span></div>
    </div>

    <div
      v-else-if="filtered.length"
      ref="scrollRef"
      class="conversation-scroll"
      role="listbox"
      :aria-activedescendant="activeId ? `conversation-${activeId}` : undefined"
      @scroll="onScroll"
    >
      <div :style="{ height: `${topSpacer}px` }"></div>
      <template v-for="(conversation, visibleIndex) in visibleItems" :key="conversation.id">
        <div v-if="showPinnedDivider(conversation, visibleIndex)" class="group-divider">
          {{ conversation.pinned ? '置顶会话' : '最近聊天' }}
        </div>
        <button
          :id="`conversation-${conversation.id}`"
          class="conversation-row"
          :class="{ active: conversation.id === activeId, group: conversation.conversationType === ConversationType.GROUP, selected: selectedIds.has(conversation.id) }"
          role="option"
          :aria-selected="conversation.id === activeId"
          :draggable="conversation.pinned"
          @click="selectConversation($event, conversation)"
          @contextmenu.prevent="openContextMenu($event, conversation)"
          @dragstart="$emit('drag-start', conversation)"
          @dragover.prevent
          @drop="$emit('drop', conversation)"
        >
          <span class="conversation-avatar" :title="conversationTitle(conversation)" @click.stop="$emit('profile', conversation)">
            <img v-if="conversation.avatar" :src="conversation.avatar" alt="" loading="lazy" />
            <span v-else>{{ conversation.title.slice(0, 1).toUpperCase() }}</span>
            <em v-if="conversation.conversationType === ConversationType.GROUP" class="group-badge" title="群聊"><i class="fa fa-users"></i></em>
            <i v-if="conversation.conversationType === ConversationType.PRIVATE" :class="presenceClass(conversation)" :title="presenceLabel(conversation)" :aria-label="presenceLabel(conversation)"></i>
          </span>
          <span class="conversation-content">
            <span class="conversation-heading">
              <strong>{{ conversation.title }}</strong>
              <time>{{ relativeTime(conversation.updatedAt) }}</time>
            </span>
            <span class="conversation-preview">
              <b v-if="drafts[conversation.id]" class="draft-label">草稿</b>
              <span v-if="conversation.typing || typingUsers[conversation.id]" class="typing-copy">对方正在输入…</span>
              <span v-else>{{ drafts[conversation.id] || summary(conversation) }}</span>
              <i v-if="conversation.lastMessageStatus === 'failed'" class="fa fa-exclamation-circle failed-mark" title="发送失败，点击进入会话重试" aria-label="发送失败"></i>
              <i v-if="conversation.pinned" class="fa fa-thumb-tack" title="已置顶"></i>
              <i v-if="conversation.muted" class="fa fa-bell-slash-o" title="已免打扰"></i>
            </span>
          </span>
          <span
            v-if="conversation.unread"
            class="unread-count"
            :class="{ muted: conversation.muted, mentioned: conversation.mentioned, pulse: !conversation.muted }"
            :aria-label="`${conversation.unread} 条未读消息`"
          >{{ conversation.muted ? '' : unreadText(conversation.unread) }}</span>
        </button>
      </template>
      <div :style="{ height: `${bottomSpacer}px` }"></div>
    </div>

    <div v-else class="empty-conversations">
      <i class="fa fa-comments-o" aria-hidden="true"></i>
      <strong>{{ keyword ? '没有匹配的会话' : '还没有会话' }}</strong>
      <p>{{ keyword ? '尝试更换关键词' : '接受好友申请或加入群组后，会话会自动出现在这里。' }}</p>
    </div>

    <footer class="left-shortcuts">
      <button title="联系人" @click="$emit('show-contacts')"><i class="fa fa-address-book-o"></i></button>
      <button title="消息设置" @click="$emit('open-settings')"><i class="fa fa-cog"></i></button>
      <button title="主题切换" @click="$emit('toggle-theme')"><i class="fa fa-adjust"></i></button>
      <button title="在独立窗口打开" @click="$emit('popout')"><i class="fa fa-external-link"></i></button>
    </footer>

    <div
      v-if="contextConversation"
      class="context-menu"
      :style="{ left: `${contextPosition.x}px`, top: `${contextPosition.y}px` }"
      role="menu"
      @mouseleave="closeContextMenu"
    >
      <button role="menuitem" @click="emitContext('pin')">
        <i class="fa fa-thumb-tack"></i>{{ contextConversation.pinned ? '取消置顶' : '置顶会话' }}
      </button>
      <button role="menuitem" @click="emitContext('mute')">
        <i class="fa fa-bell-slash-o"></i>{{ contextConversation.muted ? '开启通知' : '消息免打扰' }}
      </button>
      <button role="menuitem" @click="emitContext('read')"><i class="fa fa-check-circle-o"></i>标记已读</button>
      <button role="menuitem" @click="emitContext('detail')"><i class="fa fa-info-circle"></i>查看详情</button>
      <button role="menuitem" @click="emitContext('clear')"><i class="fa fa-eraser"></i>清空本地聊天</button>
      <button role="menuitem" @click="emitContext('delete')"><i class="fa fa-eye-slash"></i>隐藏会话</button>
      <button v-if="contextConversation.conversationType === ConversationType.PRIVATE" role="menuitem" @click="emitContext('block')"><i class="fa fa-ban"></i>拉黑用户</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { MessageCenterConnectionState, MessageCenterConversation } from '@/stores/messageCenterStore'
import { ConversationType } from '@/api/im/types'

const props = defineProps<{
  conversations: MessageCenterConversation[]
  activeId: string | null
  drafts: Record<string, string>
  typingUsers: Record<string, string>
  loading: boolean
  displayName: string
  avatar?: string
  connectionState: MessageCenterConnectionState
  compact?: boolean
}>()

const emit = defineEmits<{
  open: [conversation: MessageCenterConversation]
  refresh: []
  'open-settings': []
  'show-contacts': []
  'toggle-theme': []
  popout: []
  profile: [conversation: MessageCenterConversation]
  'drag-start': [conversation: MessageCenterConversation]
  drop: [conversation: MessageCenterConversation]
  action: [action: 'pin' | 'mute' | 'read' | 'detail' | 'clear' | 'delete' | 'block', conversation: MessageCenterConversation]
}>()

const keyword = defineModel<string>('keyword', { default: '' })
const scrollRef = ref<HTMLElement | null>(null)
const scrollTop = ref(0)
const viewportHeight = ref(640)
const selectedIds = ref(new Set<string>())
const contextConversation = ref<MessageCenterConversation | null>(null)
const contextPosition = ref({ x: 0, y: 0 })
const ITEM_HEIGHT = 76
const OVERSCAN = 5

const initial = computed(() => props.displayName.slice(0, 1).toUpperCase() || 'U')
const connectionLabel = computed(() => ({
  online: '已连接', connecting: '连接中', reconnecting: '正在重连', degraded: 'HTTP 降级', kicked: '已下线', offline: '离线',
}[props.connectionState]))

const filtered = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase()
  if (!query) return props.conversations
  const exact = query.startsWith('"') && query.endsWith('"') ? query.slice(1, -1) : ''
  return props.conversations.filter(item => {
    const source = `${item.title} ${item.subtitle} ${item.lastMessage}`.toLocaleLowerCase()
    return exact ? source.includes(exact) : query.split(/\s+/).every(part => source.includes(part))
  })
})

const startIndex = computed(() => Math.max(0, Math.floor(scrollTop.value / ITEM_HEIGHT) - OVERSCAN))
const visibleCount = computed(() => Math.ceil(viewportHeight.value / ITEM_HEIGHT) + OVERSCAN * 2)
const visibleItems = computed(() => filtered.value.slice(startIndex.value, startIndex.value + visibleCount.value))
const topSpacer = computed(() => startIndex.value * ITEM_HEIGHT)
const bottomSpacer = computed(() => Math.max(0, (filtered.value.length - startIndex.value - visibleItems.value.length) * ITEM_HEIGHT))

function onScroll(event: Event): void {
  const target = event.currentTarget as HTMLElement
  scrollTop.value = target.scrollTop
  viewportHeight.value = target.clientHeight
}

function selectConversation(event: MouseEvent, conversation: MessageCenterConversation): void {
  if (event.ctrlKey || event.metaKey) {
    const next = new Set(selectedIds.value)
    next.has(conversation.id) ? next.delete(conversation.id) : next.add(conversation.id)
    selectedIds.value = next
    return
  }
  selectedIds.value.clear()
  emit('open', conversation)
}

function markSelectedRead(): void {
  props.conversations.filter(item => selectedIds.value.has(item.id)).forEach(item => {
    item.unread = 0
    emit('action', 'read', item)
  })
  selectedIds.value.clear()
}

function openContextMenu(event: MouseEvent, conversation: MessageCenterConversation): void {
  contextConversation.value = conversation
  contextPosition.value = {
    x: Math.min(event.clientX, window.innerWidth - 190),
    y: Math.min(event.clientY, window.innerHeight - 250),
  }
}

function emitContext(action: 'pin' | 'mute' | 'read' | 'detail' | 'clear' | 'delete' | 'block'): void {
  if (contextConversation.value) emit('action', action, contextConversation.value)
  closeContextMenu()
}

function closeContextMenu(): void { contextConversation.value = null }
function unreadText(count: number): string { return count > 99 ? '99+' : String(count) }
function summary(item: MessageCenterConversation): string {
  const prefix: Partial<Record<MessageTypeLike, string>> = {
    2: '[图片] ', 3: '[文件] ', 4: '[语音] ', 5: '[视频] ', 50: '[系统通知] ', 100: '[链接] ',
  }
  const source = item.lastMessage || item.subtitle || '暂无消息'
  return `${prefix[item.lastMessageType as MessageTypeLike] || ''}${source}`
}
type MessageTypeLike = 1 | 2 | 3 | 4 | 5 | 50 | 100
function relativeTime(timestamp: number): string {
  const diff = Date.now() - timestamp
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  const date = new Date(timestamp)
  if (new Date().toDateString() === date.toDateString()) return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  const now = new Date()
  const days = Math.floor((new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime() - new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()) / 86_400_000)
  if (days < 7) return `星期${'日一二三四五六'[date.getDay()]}`
  if (now.getFullYear() === date.getFullYear()) return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}
function presenceClass(item: MessageCenterConversation): string { return item.onlineStatus === 'busy' ? 'busy' : item.onlineStatus === 'online' ? 'online' : 'offline' }
function presenceLabel(item: MessageCenterConversation): string { return item.onlineStatus === 'online' ? '在线' : item.onlineStatus === 'busy' ? '忙碌' : item.onlineStatus === 'offline' ? '离线' : '状态未知' }
function conversationTitle(item: MessageCenterConversation): string { return item.sessionStatus === 'FRIEND_REMOVED' ? '好友关系已解除' : item.title }
function showPinnedDivider(item: MessageCenterConversation, index: number): boolean {
  const absolute = startIndex.value + index
  if (absolute === 0) return true
  return filtered.value[absolute - 1]?.pinned !== item.pinned
}
</script>

<style scoped>
.conversation-list-panel{height:100%;min-width:0;display:grid;grid-template-rows:auto auto auto minmax(0,1fr) auto;background:var(--im-panel);border-right:1px solid var(--im-border);position:relative;overflow:hidden}.account-header{height:72px;padding:12px 14px;display:flex;align-items:center;gap:10px;border-bottom:1px solid var(--im-border)}.avatar-button,.conversation-avatar{width:42px;height:42px;border-radius:14px;display:grid;place-items:center;background:linear-gradient(135deg,var(--im-accent),#8b5cf6);color:#fff;font-weight:800;overflow:hidden;flex:0 0 auto}.avatar-button img,.conversation-avatar img{width:100%;height:100%;object-fit:cover}.account-copy{min-width:0;flex:1;display:flex;flex-direction:column}.account-copy strong{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.connection{font-size:11px;color:var(--im-muted)}.connection.online{color:#22a06b}.connection.reconnecting,.connection.connecting{color:#c17d10}.connection.kicked{color:#d33b43}.icon-button,.left-shortcuts button{width:36px;height:36px;border-radius:10px;color:var(--im-muted)}.icon-button:hover,.left-shortcuts button:hover{background:var(--im-hover);color:var(--im-text)}.search-box{margin:12px 14px;display:flex;align-items:center;gap:8px;height:40px;padding:0 11px;border:1px solid transparent;border-radius:12px;background:var(--im-input)}.search-box:focus-within{border-color:var(--im-accent);box-shadow:0 0 0 3px var(--im-focus)}.search-box input{min-width:0;flex:1;background:transparent;color:var(--im-text);outline:0}.search-box kbd{font-size:10px;color:var(--im-muted);border:1px solid var(--im-border);padding:2px 5px;border-radius:5px}.batch-bar{margin:0 12px 8px;padding:8px;display:flex;align-items:center;gap:8px;border-radius:10px;background:var(--im-accent-soft);font-size:12px}.batch-bar span{flex:1}.batch-bar button{color:var(--im-accent)}.conversation-scroll{min-height:0;overflow:auto;overscroll-behavior:contain}.conversation-row{position:relative;width:calc(100% - 12px);min-height:72px;margin:2px 6px;padding:10px 10px;display:flex;align-items:center;gap:11px;border-radius:12px;text-align:left;color:var(--im-text)}.conversation-row:hover{background:var(--im-hover)}.conversation-row.active{background:var(--im-selected)}.conversation-row.selected{outline:2px solid var(--im-accent)}.conversation-avatar{width:44px;height:44px;border-radius:50%;position:relative}.conversation-row.group .conversation-avatar{border-radius:13px}.conversation-avatar i{position:absolute;right:0;bottom:0;width:10px;height:10px;border:2px solid var(--im-panel);border-radius:50%;background:#9ba3af}.conversation-avatar i.online{background:#2dbd7f}.conversation-avatar i.busy{background:#e6a21a}.conversation-avatar .group-badge{position:absolute;right:-2px;bottom:-2px;width:16px;height:16px;display:grid;place-items:center;border:2px solid var(--im-panel);border-radius:50%;background:var(--im-accent);color:#fff;font-size:8px}.conversation-content{min-width:0;flex:1}.conversation-heading,.conversation-preview{display:flex;align-items:center;gap:6px;min-width:0}.conversation-heading strong,.conversation-preview span{min-width:0;flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.conversation-heading time{color:var(--im-muted);font-size:11px;white-space:nowrap}.conversation-preview{margin-top:4px;color:var(--im-muted);font-size:12px}.draft-label{color:#e5484d}.typing-copy{color:var(--im-accent)!important}.failed-mark{color:#e5484d;flex:0 0 auto}.unread-count{min-width:19px;height:19px;padding:0 5px;border-radius:10px;display:grid;place-items:center;background:#f04452;color:#fff;font-size:10px}.unread-count.pulse{animation:unread-in .22s ease-out}.unread-count.mentioned{background:#f59e0b}.unread-count.muted{min-width:8px;width:8px;height:8px;padding:0;background:#aab1bb}.group-divider{height:22px;padding:4px 14px;color:var(--im-muted);font-size:10px;text-transform:uppercase;letter-spacing:.08em}.left-shortcuts{height:52px;padding:7px 12px;display:flex;justify-content:space-around;border-top:1px solid var(--im-border)}.empty-conversations{height:100%;display:grid;place-content:center;justify-items:center;text-align:center;padding:24px;color:var(--im-muted)}.empty-conversations>i{font-size:38px;opacity:.35}.empty-conversations strong{margin-top:12px;color:var(--im-text)}.empty-conversations p{font-size:12px;margin:5px 0 14px}.empty-conversations button{padding:8px 13px;border-radius:10px;background:var(--im-accent);color:white}.conversation-skeleton{padding:0 12px}.skeleton-row{height:70px;display:flex;gap:12px;align-items:center}.skeleton-row i{width:44px;height:44px;border-radius:50%;background:var(--im-skeleton)}.skeleton-row span{width:65%;height:28px;border-radius:8px;background:var(--im-skeleton)}.context-menu{position:fixed;z-index:1000;width:190px;padding:6px;border:1px solid var(--im-border);border-radius:12px;background:var(--im-panel);box-shadow:0 14px 40px rgba(0,0,0,.2)}.context-menu button{width:100%;height:36px;padding:0 10px;display:flex;align-items:center;gap:9px;border-radius:8px;text-align:left;color:var(--im-text)}.context-menu button:hover{background:var(--im-hover)}.context-menu .danger{color:#e5484d}
@keyframes unread-in{from{transform:scale(.75);opacity:.45}to{transform:scale(1);opacity:1}}
.compact .conversation-content,.compact .conversation-heading time,.compact .conversation-preview{display:none}.compact .conversation-row{justify-content:center;padding-inline:6px}.compact .conversation-avatar{width:40px;height:40px}.compact .unread-count{position:absolute;right:3px;top:6px}
/* AUDIT FIX [2.1/2.9] / IM-MESSAGE-CENTER-20260811：Grid 最后一行固定为工具栏，
   会话滚动区单独收缩并滚动，避免工具栏被内容撑开。 */
.conversation-list-panel{min-height:0;grid-template-rows:auto auto auto minmax(0,1fr)}
.conversation-list-panel>.batch-bar{grid-row:3}
.conversation-list-panel>.conversation-skeleton,.conversation-list-panel>.conversation-scroll,.conversation-list-panel>.empty-conversations{grid-row:4;min-height:0}
.conversation-scroll{min-height:0;padding-bottom:56px}
/* 工具栏脱离可选的批量操作行，始终贴在左侧面板底边；会话内容只在上方滚动。 */
.left-shortcuts{height:52px;min-height:52px;background:var(--im-panel);position:absolute;left:0;right:0;bottom:0;z-index:2}
</style>
