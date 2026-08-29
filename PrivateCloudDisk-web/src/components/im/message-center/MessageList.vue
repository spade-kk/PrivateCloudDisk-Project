<template>
  <section class="message-list-shell" aria-label="消息记录">
    <div
      ref="scroller"
      class="message-scroller"
      tabindex="0"
      @scroll="onScroll"
      @dragover.prevent="$emit('drag-active', true)"
      @dragleave.self="$emit('drag-active', false)"
      @drop.prevent="onDrop"
    >
      <button v-if="!historyComplete" class="load-history" :disabled="loadingHistory" @click="loadOlder">
        <i class="fa" :class="loadingHistory ? 'fa-circle-o-notch fa-spin' : 'fa-clock-o'"></i>
        {{ loadingHistory ? '加载历史消息…' : '加载更早消息' }}
      </button>

      <div v-if="!messages.length" class="empty-messages">
        <i class="fa fa-paper-plane-o"></i>
        <strong>发送第一条消息吧</strong>
        <span>消息通过 IM V2 加密二进制通道实时同步</span>
      </div>

      <template v-for="(message, index) in messages" :key="message.id">
        <div v-if="showDateDivider(message, index)" class="date-divider">
          <span>{{ dateDivider(message.created_at) }}</span>
        </div>
        <article
          :id="`message-${message.id}`"
          class="message-row"
          :class="{ mine: message.sender === 'me', system: message.type === 'system', group: conversationType === ConversationType.GROUP, compact: !showGroupSender(message,index), selected: selected.has(message.id) }"
          @contextmenu.prevent="openMenu($event, message)"
        >
          <button v-if="multiSelect" class="select-message" :aria-label="`选择消息 ${message.content}`" @click="toggleSelected(message.id)">
            <i :class="selected.has(message.id) ? 'fa fa-check-circle' : 'fa fa-circle-o'"></i>
          </button>
          <!-- 发送消息的用户头像 -->
          <span v-if="message.type !== 'system'" class="message-avatar">
            <img v-if="message.senderAvatar" :src="message.senderAvatar" alt="" loading="lazy" />
            <span v-else>{{ (message.senderName || (message.sender === 'me' ? '我' : 'U')).slice(0, 1) }}</span>
          </span>

          <div v-if="message.type === 'system'" class="system-message">{{ message.content }}</div>
          <div v-else class="message-stack">
            <!-- 发送消息的用户名字 -->
            <span class="sender-name">{{ message.senderName }}</span>
            <div class="message-bubble" :class="message.type">
              <button v-if="message.replyTo" class="reply-preview" @click="$emit('jump', message.replyTo)">
                <i class="fa fa-reply"></i>
                <span>{{ quotedPreview(message) }}</span>
              </button>

              <template v-if="message.status === 'recalled'">
                <p class="recalled-copy"><i class="fa fa-undo"></i>{{ message.content }}</p>
              </template>
              <template v-else-if="message.type === 'image'">
                <button class="image-message" @click="openImage(message)">
                  <img v-if="attachmentUrl(message)" :src="attachmentUrl(message)" :alt="String(message.payload?.altText || message.content || '图片消息')" loading="lazy" />
                  <span v-else v-observe-preview="message" class="attachment-placeholder">
                    <i :class="attachmentLoading[message.id] ? 'fa fa-circle-o-notch fa-spin' : 'fa fa-picture-o'"></i>
                    {{ attachmentLoading[message.id] ? '正在安全加载' : '点击加载图片' }}
                  </span>
                  <span v-if="message.payload?.isAnimated">GIF</span>
                </button>
                <small v-if="attachmentErrors[message.id]" class="attachment-error">{{ attachmentErrors[message.id] }}</small>
                <p v-if="message.content && message.content !== '[图片]'">{{ message.content }}</p>
              </template>
              <template v-else-if="message.type === 'file'">
                <button class="file-message" :disabled="attachmentLoading[message.id]" @click="downloadAttachment(message)">
                  <i :class="fileIcon(String(message.payload?.fileName || message.content))"></i>
                  <span><strong>{{ message.payload?.fileName || message.content }}</strong><small>{{ fileSize(Number(message.payload?.size || 0)) }} · {{ attachmentErrors[message.id] || '点击申请授权并下载' }}</small></span>
                  <i :class="attachmentLoading[message.id] ? 'fa fa-circle-o-notch fa-spin' : 'fa fa-download'"></i>
                </button>
              </template>
              <template v-else-if="message.type === 'voice'">
                <div class="voice-message">
                  <button :disabled="attachmentLoading[message.id]" :aria-label="playingId === message.id ? '暂停语音' : '播放语音'" @click="toggleAudio(message)">
                    <i :class="attachmentLoading[message.id] ? 'fa fa-circle-o-notch fa-spin' : playingId === message.id ? 'fa fa-pause' : 'fa fa-play'"></i>
                  </button>
                  <span class="waveform"><i v-for="bar in 18" :key="bar" :style="{ height: `${6 + (bar * 7 % 15)}px` }"></i></span>
                  <time>{{ Number(message.payload?.duration || 0) }}″</time>
                </div>
                <small v-if="attachmentErrors[message.id]" class="attachment-error">{{ attachmentErrors[message.id] }}</small>
              </template>
              <template v-else-if="message.type === 'video'">
                <video v-if="attachmentUrl(message)" class="video-message" :poster="safeRemoteUrl(message.payload?.coverUrl)" :src="attachmentUrl(message)" controls preload="metadata"></video>
                <button v-else class="video-load" :disabled="attachmentLoading[message.id]" @click="loadMedia(message)">
                  <i :class="attachmentLoading[message.id] ? 'fa fa-circle-o-notch fa-spin' : 'fa fa-play-circle-o'"></i>
                  <span>{{ attachmentLoading[message.id] ? '正在安全加载视频' : '点击加载视频' }}</span>
                </button>
                <small v-if="attachmentErrors[message.id]" class="attachment-error">{{ attachmentErrors[message.id] }}</small>
              </template>
              <template v-else-if="message.type === 'sticker'">
                <!-- 平台表情仅渲染
                     后端已持久化的 HTTPS URL，不把贴纸标识符解释为 HTML，避免富媒体 XSS。 -->
                <img v-if="safeStickerUrl(message)" class="sticker-message" :src="safeStickerUrl(message)" :alt="String(message.payload?.description || message.content || '平台表情')" loading="lazy" />
                <span v-else class="attachment-placeholder"><i class="fa fa-smile-o"></i>{{ message.content || '[平台表情]' }}</span>
              </template>
              <template v-else-if="message.type === 'location'">
                <a class="location-message" :href="mapUrl(message)" target="_blank" rel="noopener">
                  <img v-if="message.payload?.staticMapUrl" :src="String(message.payload.staticMapUrl)" alt="位置地图" loading="lazy" />
                  <i v-else class="fa fa-map-marker"></i>
                  <span><strong>{{ message.payload?.name || '位置' }}</strong><small>{{ message.payload?.address }}</small></span>
                </a>
              </template>
              <template v-else-if="message.payload?.linkPreview && typeof message.payload.linkPreview === 'object'">
                <a class="link-card" :href="linkPreviewHref(message)" target="_blank" rel="noopener noreferrer">
                  <i class="fa fa-link"></i><span><strong>{{ linkPreviewValue(message, 'host') || '链接' }}</strong><small>{{ message.content }}</small></span>
                </a>
              </template>
              <template v-else-if="message.payload?.isMarkdown">
                <pre class="code-message"><code>{{ message.content }}</code></pre>
                <button class="copy-code" @click="copyContent(message.content)"><i class="fa fa-copy"></i>复制代码</button>
              </template>
              <template v-else>
                <p class="text-message" :class="{ collapsed: isLong(message) && !expanded.has(message.id) }">
                  <template v-for="(token, tokenIndex) in textTokens(message.content)" :key="tokenIndex">
                    <a v-if="token.link" :href="token.text" target="_blank" rel="noopener noreferrer">{{ token.text }}</a>
                    <mark v-else-if="token.mention">{{ token.text }}</mark>
                    <span v-else>{{ token.text }}</span>
                  </template>
                </p>
                <button v-if="isLong(message)" class="expand-text" @click="toggleExpanded(message.id)">
                  {{ expanded.has(message.id) ? '收起' : '展开全文' }}
                </button>
              </template>
            </div>

            <footer class="message-meta">
              <time :title="new Date(message.created_at).toLocaleString('zh-CN')">{{ messageTime(message.created_at) }}</time>
              <span v-if="message.sender === 'me'" class="delivery-state" :class="message.status" :title="message.error || (message.readAt ? `已读于 ${new Date(message.readAt).toLocaleString('zh-CN')}` : statusText(message.status))">
                <i v-if="message.status === 'scheduled'" class="fa fa-clock-o"></i>
                <i v-else-if="message.status === 'sending'" class="fa fa-circle-o-notch fa-spin"></i>
                <i v-else-if="message.status === 'sent'" class="fa fa-check"></i>
                <i v-else-if="message.status === 'delivered'" class="fa fa-check-circle-o"></i>
                <i v-else-if="message.status === 'read'" class="fa fa-check-circle"></i>
                <button v-else-if="message.status === 'failed'" aria-label="重新发送" @click="$emit('retry', message)"><i class="fa fa-exclamation-circle"></i></button>
                <i v-else-if="message.status === 'recalled'" class="fa fa-undo"></i>
              </span>
            </footer>
          </div>
        </article>
      </template>
    </div>

    <button v-if="showJumpBottom" class="jump-bottom" @click="scrollToBottom(true)">
      <i class="fa fa-angle-down"></i><span v-if="newBelow">{{ newBelow }} 条新消息</span>
    </button>

    <div v-if="multiSelect" class="multi-actions">
      <span>已选择 {{ selected.size }} 条</span>
      <button @click="$emit('forward', [...selected])"><i class="fa fa-share"></i>转发</button>
      <button @click="$emit('delete-selected', [...selected])"><i class="fa fa-trash-o"></i>删除</button>
      <button @click="exitMulti">取消</button>
    </div>

    <div v-if="menuMessage" class="message-menu" :style="{ left: `${menuPosition.x}px`, top: `${menuPosition.y}px` }" @mouseleave="menuMessage = null">
      <button @click="copyMessage"><i class="fa fa-copy"></i>复制</button>
      <button @click="quoteMessage"><i class="fa fa-reply"></i>引用</button>
      <button @click="startMulti"><i class="fa fa-check-square-o"></i>多选</button>
      <button v-if="menuMessage.sender === 'me' && canRecall(menuMessage)" @click="recall"><i class="fa fa-undo"></i>撤回</button>
      <button @click="$emit('report', menuMessage)"><i class="fa fa-flag-o"></i>举报</button>
    </div>

    <dialog ref="mediaDialog" class="media-lightbox" @click.self="mediaDialog?.close()">
      <button aria-label="关闭预览" @click="mediaDialog?.close()"><i class="fa fa-times"></i></button>
      <div class="lightbox-tools">
        <button aria-label="缩小" @click="mediaScale = Math.max(.5, mediaScale - .25)"><i class="fa fa-search-minus"></i></button>
        <button aria-label="放大" @click="mediaScale = Math.min(3, mediaScale + .25)"><i class="fa fa-search-plus"></i></button>
        <button aria-label="旋转" @click="mediaRotation += 90"><i class="fa fa-repeat"></i></button>
        <a :href="mediaUrl" download="chat-image" aria-label="下载图片"><i class="fa fa-download"></i></a>
      </div>
      <img :src="mediaUrl" alt="图片大图预览" :style="{ transform: `scale(${mediaScale}) rotate(${mediaRotation}deg)` }" />
    </dialog>
    <audio ref="audioRef" @ended="playingId = ''"></audio>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { MessageCenterMessage } from '@/stores/messageCenterStore'
import { ConversationType } from '@/api/im/types'
import { downloadImAttachment, loadImImageObjectUrl, loadImMediaObjectUrl, type ImAttachmentDescriptor } from '@/utils/imAttachmentAccess'

const props = defineProps<{
  messages: MessageCenterMessage[]
  loadingHistory: boolean
  historyComplete: boolean
  conversationType: ConversationType
}>()
const emit = defineEmits<{
  'load-older': []
  retry: [message: MessageCenterMessage]
  quote: [message: MessageCenterMessage]
  recall: [message: MessageCenterMessage]
  jump: [messageId: string]
  forward: [messageIds: string[]]
  'delete-selected': [messageIds: string[]]
  report: [message: MessageCenterMessage]
  files: [files: File[]]
  'drag-active': [active: boolean]
  'attachment-error': [message: string]
}>()

const scroller = ref<HTMLElement | null>(null)
const showJumpBottom = ref(false)
const newBelow = ref(0)
const expanded = ref(new Set<string>())
const selected = ref(new Set<string>())
const multiSelect = ref(false)
const menuMessage = ref<MessageCenterMessage | null>(null)
const menuPosition = ref({ x: 0, y: 0 })
const mediaDialog = ref<HTMLDialogElement | null>(null)
const mediaUrl = ref('')
const mediaScale = ref(1)
const mediaRotation = ref(0)
const audioRef = ref<HTMLAudioElement | null>(null)
const playingId = ref('')
const attachmentUrls = ref<Record<string, string>>({})
const attachmentLoading = ref<Record<string, boolean>>({})
const attachmentErrors = ref<Record<string, string>>({})
const previewObservers = new WeakMap<HTMLElement, IntersectionObserver>()
let wasNearBottom = true

// [IM-WEB-ENTERPRISE-20260809 / 12.2]
// 图片仅在占位卡片进入可视区时申请一次短期预览授权，避免历史消息中的图片批量耗流量。
const vObservePreview = {
  mounted(el: HTMLElement, binding: { value: MessageCenterMessage }): void {
    const observer = new IntersectionObserver(entries => {
      if (!entries.some(entry => entry.isIntersecting)) return
      observer.disconnect()
      previewObservers.delete(el)
      void ensureImageUrl(binding.value)
    }, { root: scroller.value, rootMargin: '240px 0px' })
    previewObservers.set(el, observer)
    observer.observe(el)
  },
  unmounted(el: HTMLElement): void {
    previewObservers.get(el)?.disconnect()
    previewObservers.delete(el)
  },
}

watch(() => props.messages.length, async (length, previous) => {
  await nextTick()
  if (wasNearBottom) scrollToBottom(false)
  else if (length > previous) {
    newBelow.value += length - previous
    showJumpBottom.value = true
  }
})

function onScroll(): void {
  const el = scroller.value
  if (!el) return
  wasNearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120
  showJumpBottom.value = !wasNearBottom
  if (wasNearBottom) newBelow.value = 0
  if (el.scrollTop < 80 && !props.loadingHistory && !props.historyComplete) loadOlder()
}

/** 群聊连续消息仅在首条展示成员头像与昵称，时间间隔超过五分钟后重新展示。 */
function showGroupSender(message: MessageCenterMessage, index: number): boolean {
  if (props.conversationType !== ConversationType.GROUP || message.sender === 'me') return false
  const previous = props.messages[index - 1]
  return !previous || previous.type === 'system' || previous.sender !== message.sender || message.created_at - previous.created_at > 5 * 60_000
}

async function loadOlder(): Promise<void> {
  const el = scroller.value
  const oldHeight = el?.scrollHeight || 0
  emit('load-older')
  await nextTick()
  if (el) el.scrollTop += el.scrollHeight - oldHeight
}

function scrollToBottom(smooth: boolean): void {
  const el = scroller.value
  if (!el) return
  el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
  showJumpBottom.value = false
  newBelow.value = 0
}

function onDrop(event: DragEvent): void {
  emit('drag-active', false)
  const files = [...(event.dataTransfer?.files || [])]
  if (files.length) emit('files', files)
}

function showDateDivider(message: MessageCenterMessage, index: number): boolean {
  if (!index) return true
  const previous = props.messages[index - 1]
  return message.created_at - previous.created_at > 5 * 60_000 ||
    new Date(message.created_at).toDateString() !== new Date(previous.created_at).toDateString()
}
function dateDivider(timestamp: number): string {
  const date = new Date(timestamp)
  const today = new Date()
  const yesterday = new Date(Date.now() - 86_400_000)
  if (date.toDateString() === today.toDateString()) return messageTime(timestamp)
  if (date.toDateString() === yesterday.toDateString()) return `昨天 ${messageTime(timestamp)}`
  return date.toLocaleString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
function canRecall(message: MessageCenterMessage): boolean { return Date.now() - message.created_at <= 2 * 60_000 }
async function copyContent(content: string): Promise<void> { await navigator.clipboard?.writeText(content) }
function linkPreviewValue(message: MessageCenterMessage, key: 'url' | 'host'): string { const value=message.payload?.linkPreview;return value&&typeof value==='object'&&key in value?String((value as Record<string,unknown>)[key]||''):'' }
function linkPreviewHref(message: MessageCenterMessage): string { const candidate=linkPreviewValue(message,'url')||message.content;return /^https?:\/\//i.test(candidate)?candidate:'#' }
function messageTime(timestamp: number): string { return new Date(timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) }
function statusText(status: string): string { return ({ scheduled: '等待定时发送', sending: '发送中', sent: '已发送', delivered: '已送达', read: '已读', failed: '发送失败', recalled: '已撤回' } as Record<string, string>)[status] || status }
function isLong(message: MessageCenterMessage): boolean { return message.content.length > 500 }
function toggleExpanded(id: string): void { const next = new Set(expanded.value); next.has(id) ? next.delete(id) : next.add(id); expanded.value = next }
function textTokens(content: string): Array<{ text: string; link?: boolean; mention?: boolean }> {
  return content.split(/(https?:\/\/[^\s]+|@[\p{L}\p{N}_-]+)/gu).filter(Boolean).map(text => ({
    text,
    link: /^https?:\/\//.test(text),
    mention: text.startsWith('@'),
  }))
}
function fileSize(bytes: number): string { if (!bytes) return '大小未知'; const units = ['B', 'KB', 'MB', 'GB']; const i = Math.min(3, Math.floor(Math.log(bytes) / Math.log(1024))); return `${(bytes / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}` }
function fileIcon(name: string): string { const ext = name.split('.').pop()?.toLowerCase(); if (ext === 'pdf') return 'fa fa-file-pdf-o'; if (['doc', 'docx'].includes(ext || '')) return 'fa fa-file-word-o'; if (['xls', 'xlsx'].includes(ext || '')) return 'fa fa-file-excel-o'; if (['zip', 'rar', '7z'].includes(ext || '')) return 'fa fa-file-archive-o'; return 'fa fa-file-o' }
function mapUrl(message: MessageCenterMessage): string { return `https://www.openstreetmap.org/?mlat=${message.payload?.latitude || ''}&mlon=${message.payload?.longitude || ''}` }
function quotedPreview(message: MessageCenterMessage): string { return String(message.payload?.quotedContentPreview || `引用消息 ${message.replyTo}`) }
function safeRemoteUrl(value: unknown): string { const url=String(value||'').trim();return /^(https?:|blob:|data:image\/)/i.test(url)?url:'' }
function safeStickerUrl(message: MessageCenterMessage): string {
  // AUDIT FIX [4.5/4.8] / IM-EMOJI-SESSION-20260810：第三方贴纸仅接受 HTTPS，
  // 防止历史消息扩展字段把 javascript:/data: 等非媒体协议注入渲染节点。
  const url = String(message.payload?.url || message.payload?.thumbnailUrl || '').trim()
  return /^https:\/\//i.test(url) ? url : ''
}
function attachmentUrl(message: MessageCenterMessage): string { return attachmentUrls.value[message.id] || safeRemoteUrl(message.payload?.thumbnailUrl || message.payload?.url) }
function attachmentDescriptor(message: MessageCenterMessage): ImAttachmentDescriptor { return { diskFileId: String(message.payload?.diskFileId || message.file_id || ''), fileName: String(message.payload?.fileName || message.content || '聊天附件'), fileSize: Number(message.payload?.size || 0) } }
function reportAttachmentError(message: MessageCenterMessage, error: unknown): void { const text=error instanceof Error?error.message:'附件访问失败';attachmentErrors.value[message.id]=text;emit('attachment-error',`${message.content || '附件'}：${text}`) }
async function ensureImageUrl(message: MessageCenterMessage): Promise<string> { const existing=attachmentUrl(message);if(existing)return existing;if(attachmentLoading.value[message.id])return'';attachmentLoading.value[message.id]=true;attachmentErrors.value[message.id]='';try{const url=await loadImImageObjectUrl(attachmentDescriptor(message));attachmentUrls.value[message.id]=url;return url}catch(error){reportAttachmentError(message,error);return''}finally{attachmentLoading.value[message.id]=false} }
async function openImage(message: MessageCenterMessage): Promise<void> { const url=await ensureImageUrl(message);if(!url)return;mediaUrl.value=url;mediaScale.value=1;mediaRotation.value=0;mediaDialog.value?.showModal() }
async function loadMedia(message: MessageCenterMessage): Promise<string> { const existing=attachmentUrl(message);if(existing)return existing;if(attachmentLoading.value[message.id])return'';attachmentLoading.value[message.id]=true;attachmentErrors.value[message.id]='';try{const url=await loadImMediaObjectUrl(attachmentDescriptor(message));attachmentUrls.value[message.id]=url;return url}catch(error){reportAttachmentError(message,error);return''}finally{attachmentLoading.value[message.id]=false} }
async function downloadAttachment(message: MessageCenterMessage): Promise<void> { if(attachmentLoading.value[message.id])return;attachmentLoading.value[message.id]=true;attachmentErrors.value[message.id]='';try{await downloadImAttachment(attachmentDescriptor(message))}catch(error){reportAttachmentError(message,error)}finally{attachmentLoading.value[message.id]=false} }
async function toggleAudio(message: MessageCenterMessage): Promise<void> { const audio=audioRef.value;if(!audio)return;if(playingId.value===message.id){audio.pause();playingId.value='';return}const url=await loadMedia(message);if(!url)return;audio.src=url;audio.play().then(()=>{playingId.value=message.id}).catch(error=>reportAttachmentError(message,error)) }
function openMenu(event: MouseEvent, message: MessageCenterMessage): void { menuMessage.value = message; menuPosition.value = { x: Math.min(event.clientX, window.innerWidth - 170), y: Math.min(event.clientY, window.innerHeight - 220) } }
async function copyMessage(): Promise<void> { if (menuMessage.value) await navigator.clipboard?.writeText(menuMessage.value.content); menuMessage.value = null }
function quoteMessage(): void { if (menuMessage.value) emit('quote', menuMessage.value); menuMessage.value = null }
function recall(): void { if (menuMessage.value) emit('recall', menuMessage.value); menuMessage.value = null }
function startMulti(): void { if (menuMessage.value) selected.value.add(menuMessage.value.id); multiSelect.value = true; menuMessage.value = null }
function toggleSelected(id: string): void { const next = new Set(selected.value); next.has(id) ? next.delete(id) : next.add(id); selected.value = next }
function exitMulti(): void { selected.value.clear(); multiSelect.value = false }
function jumpTo(messageId: string): void { document.getElementById(`message-${messageId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }) }
defineExpose({ scrollToBottom, jumpTo })
onBeforeUnmount(() => { audioRef.value?.pause();Object.values(attachmentUrls.value).forEach(url=>{if(url.startsWith('blob:'))URL.revokeObjectURL(url)}) })
</script>

<style scoped>
.message-list-shell{position:relative;min-height:0;background:var(--im-chat-bg)}.message-scroller{height:100%;overflow:auto;padding:20px clamp(14px,4vw,56px);scrollbar-gutter:stable;overscroll-behavior:contain}.message-scroller:focus-visible{outline:2px solid var(--im-accent);outline-offset:-2px}.load-history{display:flex;align-items:center;gap:7px;margin:0 auto 16px;padding:7px 12px;border-radius:12px;background:var(--im-panel);color:var(--im-muted);box-shadow:var(--im-shadow);font-size:12px}.empty-messages{height:100%;display:grid;place-content:center;justify-items:center;color:var(--im-muted);text-align:center}.empty-messages i{font-size:46px;opacity:.25}.empty-messages strong{margin-top:12px;color:var(--im-text)}.empty-messages span{font-size:12px;margin-top:4px}.date-divider{display:flex;justify-content:center;margin:18px 0 12px}.date-divider span,.system-message{padding:4px 9px;border-radius:8px;background:var(--im-system);color:var(--im-muted);font-size:11px}.message-row{display:flex;align-items:flex-start;gap:9px;margin:8px 0;content-visibility:auto;contain-intrinsic-size:80px}.message-row.mine{flex-direction:row-reverse}.message-row.system{justify-content:center}.message-row.selected{border-radius:12px;background:var(--im-selected)}.message-avatar{width:32px;height:32px;flex:0 0 auto;border-radius:50%;display:grid;place-items:center;overflow:hidden;background:var(--im-avatar);font-size:12px}.message-avatar img{width:100%;height:100%;object-fit:cover}.message-stack{max-width:min(72%,720px);display:flex;flex-direction:column;align-items:flex-start}.mine .message-stack{align-items:flex-end}.sender-name{padding:0 4px 3px;color:var(--im-muted);font-size:11px}.message-bubble{min-width:42px;padding:9px 12px;border:1px solid var(--im-border);border-radius:4px 16px 16px 16px;background:var(--im-received);color:var(--im-text);box-shadow:0 1px 2px rgba(0,0,0,.04);overflow:hidden}.mine .message-bubble{border-color:transparent;border-radius:16px 4px 16px 16px;background:var(--im-sent);color:var(--im-sent-text)}.message-bubble.image,.message-bubble.video{padding:3px}.text-message{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.55}.text-message.collapsed{display:-webkit-box;-webkit-line-clamp:8;-webkit-box-orient:vertical;overflow:hidden}.text-message a{text-decoration:underline;color:inherit}.text-message mark{padding:0 2px;border-radius:3px;background:var(--im-mention);color:inherit}.expand-text{display:block;margin-top:6px;color:inherit;font-size:12px;text-decoration:underline}.reply-preview{width:100%;margin-bottom:7px;padding:6px 8px;display:flex;align-items:center;gap:6px;border-left:3px solid var(--im-accent);border-radius:6px;background:rgba(127,127,127,.12);font-size:11px;text-align:left}.image-message{position:relative;display:block;max-width:320px;max-height:320px;overflow:hidden;border-radius:12px}.image-message img{display:block;max-width:100%;max-height:320px;object-fit:contain}.image-message span{position:absolute;right:6px;bottom:6px;padding:2px 5px;border-radius:5px;background:#111a;color:#fff;font-size:10px}.file-message{min-width:250px;display:flex;align-items:center;gap:10px;color:inherit}.file-message>i:first-child{width:38px;height:38px;display:grid;place-items:center;border-radius:9px;background:rgba(127,127,127,.14);font-size:20px}.file-message span{min-width:0;flex:1;display:flex;flex-direction:column}.file-message strong{max-width:220px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.file-message small{opacity:.68;font-size:10px}.voice-message{min-width:210px;display:flex;align-items:center;gap:9px}.voice-message button{width:30px;height:30px;border-radius:50%;background:rgba(127,127,127,.15)}.waveform{display:flex;align-items:center;gap:2px;flex:1}.waveform i{width:2px;border-radius:2px;background:currentColor;opacity:.6}.video-message{display:block;max-width:360px;max-height:280px;border-radius:12px;background:#000}.location-message{min-width:240px;display:flex;align-items:center;gap:10px;color:inherit}.location-message img{width:84px;height:62px;object-fit:cover;border-radius:8px}.location-message>i{width:48px;height:48px;display:grid;place-items:center;font-size:28px}.location-message span{display:flex;flex-direction:column}.location-message small{opacity:.7}.message-meta{padding:3px 4px;display:flex;align-items:center;gap:5px;color:var(--im-muted);font-size:10px}.delivery-state{font-size:11px}.delivery-state.read{color:var(--im-accent)}.delivery-state.failed{color:#e5484d}.jump-bottom{position:absolute;right:24px;bottom:18px;min-width:40px;height:40px;padding:0 12px;display:flex;align-items:center;gap:6px;border:1px solid var(--im-border);border-radius:20px;background:var(--im-panel);color:var(--im-text);box-shadow:var(--im-shadow)}.multi-actions{position:absolute;left:50%;bottom:16px;transform:translateX(-50%);display:flex;align-items:center;gap:10px;padding:8px 12px;border:1px solid var(--im-border);border-radius:14px;background:var(--im-panel);box-shadow:var(--im-shadow)}.multi-actions button{padding:7px;color:var(--im-accent)}.select-message{align-self:center;color:var(--im-accent)}.message-menu{position:fixed;z-index:1100;width:160px;padding:6px;border:1px solid var(--im-border);border-radius:12px;background:var(--im-panel);box-shadow:var(--im-shadow)}.message-menu button{width:100%;height:34px;display:flex;align-items:center;gap:8px;padding:0 9px;border-radius:8px;color:var(--im-text)}.message-menu button:hover{background:var(--im-hover)}.media-lightbox{max-width:92vw;max-height:92vh;padding:0;border:0;background:transparent}.media-lightbox::backdrop{background:#000d}.media-lightbox>button{position:fixed;right:24px;top:24px;width:44px;height:44px;border-radius:50%;background:#fff2;color:#fff;font-size:22px}.media-lightbox img{max-width:90vw;max-height:88vh;object-fit:contain}.recalled-copy{color:var(--im-muted);font-style:italic}
@media(max-width:767px){.message-scroller{padding:14px 10px 90px}.message-stack{max-width:82%}.message-bubble{padding:8px 10px}.file-message{min-width:210px}.message-avatar{width:28px;height:28px}}
</style>

<style scoped>
/* [IM-WEB-ENTERPRISE-20260809 / 5.6-5.9 / 12.2] 安全附件授权加载状态。 */
.image-message > .attachment-placeholder,
.video-load {
  position:static;
  width:230px;
  height:150px;
  padding:0;
  display:grid;
  place-content:center;
  justify-items:center;
  gap:7px;
  border-radius:12px;
  background:rgba(127,127,127,.14);
  color:inherit;
  font-size:12px;
}
.attachment-placeholder i,.video-load i{font-size:28px}
.attachment-error{display:block;max-width:280px;padding:5px 7px 0;color:#e5484d;font-size:10px}
.file-message{text-align:left}
.video-load{color:inherit}
.sticker-message{display:block;width:min(180px,52vw);max-height:180px;object-fit:contain}
.code-message{max-width:min(620px,70vw);margin:0;white-space:pre-wrap;overflow:auto;font:12px/1.55 ui-monospace,SFMono-Regular,Menlo,monospace}.copy-code{display:block;margin-top:7px;color:inherit;font-size:11px;opacity:.8}.link-card{min-width:240px;display:flex;align-items:center;gap:10px;color:inherit;text-decoration:none}.link-card>i{width:38px;height:38px;display:grid;place-items:center;border-radius:9px;background:rgba(127,127,127,.15);font-size:18px}.link-card span{min-width:0;display:flex;flex-direction:column}.link-card strong,.link-card small{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.link-card small{max-width:250px;opacity:.7}.media-lightbox{overflow:hidden}.media-lightbox>img{transition:transform .18s ease}.lightbox-tools{position:fixed;left:50%;bottom:24px;transform:translateX(-50%);display:flex;align-items:center;gap:6px;padding:6px 8px;border-radius:14px;background:#111b;color:#fff}.lightbox-tools button,.lightbox-tools a{width:34px;height:30px;display:grid;place-items:center;color:#fff;border-radius:7px}.lightbox-tools button:hover,.lightbox-tools a:hover{background:#fff3}
.message-row:not(.group) .message-bubble{border-radius:18px}.message-row:not(.group).mine .message-bubble{border-radius:18px}.message-row.group.compact{padding-left:41px}
@media(max-width:767px){.message-row.group.compact{padding-left:37px}}
</style>
