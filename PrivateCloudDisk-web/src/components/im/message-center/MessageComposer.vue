<template>
  <section
    class="composer-shell"
    :class="{ dragging, compact: !toolbarExpanded, disabled }"
    aria-label="消息输入区"
    @dragenter.prevent="dragging = true"
    @dragover.prevent="dragging = true"
    @dragleave.self="dragging = false"
    @drop.prevent="handleDrop"
  >
    <div v-if="dragging" class="drop-overlay"><i class="fa fa-cloud-upload"></i><strong>松开以添加文件</strong></div>

    <div v-if="replyTo" class="reply-strip">
      <i class="fa fa-reply"></i>
      <div><strong>回复 {{ replyTo.senderName || '消息' }}</strong><span>{{ replyTo.content }}</span></div>
      <button aria-label="取消引用" @click="$emit('cancel-reply')"><i class="fa fa-times"></i></button>
    </div>

    <div v-if="attachments.length" class="attachment-zone" :class="{ folded: attachmentsFolded }">
      <header>
        <span>{{ attachments.length }} 个附件 · {{ fileSize(totalAttachmentSize) }}</span>
        <button v-if="attachments.length > 3" @click="attachmentsFolded = !attachmentsFolded">{{ attachmentsFolded ? '展开' : '折叠' }}</button>
        <button @click="clearAttachments">全部清除</button>
      </header>
      <div class="attachment-list">
        <article
          v-for="(attachment, index) in visibleAttachments"
          :key="attachment.id"
          class="attachment-card"
          draggable="true"
          @dragstart="dragIndex = index"
          @dragover.prevent
          @drop.stop.prevent="reorder(index)"
        >
          <img v-if="attachment.kind === 'image'" :src="attachment.previewUrl" :alt="attachment.name" />
          <video v-else-if="attachment.kind === 'video'" :src="attachment.previewUrl" muted preload="metadata"></video>
          <i v-else :class="attachmentIcon(attachment)"></i>
          <div>
            <input v-model="attachment.name" aria-label="附件文件名" />
            <small>{{ fileSize(attachment.file.size) }}<template v-if="attachment.progress"> · {{ attachment.progress }}%</template></small>
            <input v-model="attachment.description" class="attachment-description" placeholder="添加说明" />
          </div>
          <button aria-label="删除附件" @click="removeAttachment(attachment.id)"><i class="fa fa-times"></i></button>
          <progress v-if="attachment.uploading" :value="attachment.progress" max="100"></progress>
          <span v-if="attachment.file.size > 500 * 1024 * 1024" class="large-warning" title="建议发送网盘分享链接"><i class="fa fa-exclamation-triangle"></i></span>
        </article>
      </div>
    </div>

    <!-- AUDIT FIX [4.5/4.8] / IM-EMOJI-SESSION-20260810：贴纸不混入 Unicode
         文本。待发送状态仅保存平台贴纸稳定 ID 与渲染资源，发送后由 STICKER Payload 重建。 -->
    <div v-if="pendingStickers.length" class="sticker-draft-zone" aria-label="待发送的平台表情">
      <button v-for="sticker in pendingStickers" :key="sticker.id" class="sticker-draft" :title="`移除 ${sticker.title}`" @click="removePendingSticker(sticker.id)">
        <img :src="sticker.thumbnailUrl" :alt="sticker.title" /><i class="fa fa-times"></i>
      </button>
    </div>

    <div v-if="linkPreview" class="link-preview">
      <i class="fa fa-link"></i><div><strong>{{ linkPreview.host }}</strong><span>{{ linkPreview.url }}</span></div>
      <button @click="linkPreview = null"><i class="fa fa-times"></i></button>
    </div>

    <div class="composer-toolbar" role="toolbar" aria-label="消息工具">
      <button title="表情" :aria-expanded="emojiOpen" @click="emojiOpen = !emojiOpen"><i class="fa fa-smile-o"></i></button>
      <button title="选择图片或文件" @click="fileInput?.click()"><i class="fa fa-paperclip"></i></button>
      <button title="Markdown 预览" :class="{ active: markdownPreview }" @click="markdownPreview = !markdownPreview"><i class="fa fa-markdown">M</i></button>
      <button title="插入代码块" @click="insertCodeBlock"><i class="fa fa-code"></i></button>
      <button title="从网盘选择文件（预留选择器）" @click="$emit('pick-drive-file')"><i class="fa fa-cloud"></i></button>
      <button title="定时消息" @click="scheduleOpen = !scheduleOpen"><i class="fa fa-clock-o"></i></button>
      <button title="语音录制" :class="{ recording }" @click="toggleRecording"><i class="fa" :class="recording ? 'fa-stop-circle' : 'fa-microphone'"></i></button>
      <button v-if="recording" :title="recordingPaused ? '继续录音' : '暂停录音'" @click="toggleRecordingPause"><i class="fa" :class="recordingPaused ? 'fa-play' : 'fa-pause'"></i></button>
      <button v-if="toolbarExpanded" title="发送名片" @click="$emit('share-card')"><i class="fa fa-address-card-o"></i></button>
      <button v-if="toolbarExpanded" title="全屏编辑" @click="fullscreen = true"><i class="fa fa-arrows-alt"></i></button>
      <button class="toolbar-more" :title="toolbarExpanded ? '收起工具栏' : '更多工具'" @click="toolbarExpanded = !toolbarExpanded"><i class="fa fa-ellipsis-h"></i></button>
      <span class="composer-spacer"></span>
      <span v-if="showCounter" class="character-count">{{ draft.length }}/5000</span>
    </div>

    <div v-if="emojiOpen" class="emoji-panel" :class="{ pinned: emojiPinned }">
      <header>
        <input v-model="emojiKeyword" placeholder="搜索平台表情（Emoji 可用面板内搜索）" @keyup.enter="searchStickers" />
        <button :class="{ active: emojiPinned }" title="常驻" @click="emojiPinned = !emojiPinned"><i class="fa fa-thumb-tack"></i></button>
      </header>
      <nav aria-label="表情类型">
        <button :class="{ active: emojiTab === 'unicode' }" @click="emojiTab = 'unicode'"><i class="fa fa-smile-o"></i> Emoji</button>
        <button :class="{ active: emojiTab === 'platform' }" @click="openPlatformTab"><i class="fa fa-heart-o"></i> 平台表情</button>
        <button :class="{ active: emojiTab === 'favorites' }" @click="openFavoritesTab"><i class="fa fa-star-o"></i> 我的最爱</button>
      </nav>
      <section v-show="emojiTab === 'unicode'" class="unicode-emoji-pane">
        <!-- AUDIT FIX [3.1/3.4/3.6] / IM-EMOJI-SESSION-20260810：主面板使用中文
             Emoji 数据集；英文 Database 仅补充 :shortcode 搜索。两者输出同一 Unicode 字符。 -->
        <emoji-picker ref="unicodePicker" emoji-version="15.0" locale="zh" :data-source="unicodeEmojiDataSource" @emoji-click="handleUnicodeEmojiClick"></emoji-picker>
      </section>
      <section v-show="emojiTab !== 'unicode'" class="platform-sticker-pane">
        <div v-if="stickerLoading" class="sticker-empty"><i class="fa fa-circle-o-notch fa-spin"></i> 正在加载平台表情</div>
        <div v-else-if="stickerConfigurationRequired && !platformStickers.length" class="sticker-empty"><i class="fa fa-key"></i> 请配置 VITE_GIPHY_API_KEY 后搜索平台表情</div>
        <div v-else-if="!platformStickers.length" class="sticker-empty"><i class="fa fa-search"></i> 没有可用表情；可更换关键词或检查网络</div>
        <div v-else class="sticker-grid">
          <article v-for="sticker in platformStickers" :key="sticker.id" class="sticker-item">
            <button :title="`发送 ${sticker.title}`" @click="queueSticker(sticker)"><img :src="sticker.thumbnailUrl" :alt="sticker.title" loading="lazy" /></button>
            <button class="favorite-sticker" :title="isFavorite(sticker) ? '取消收藏' : '收藏'" @click="toggleStickerFavorite(sticker)"><i :class="isFavorite(sticker) ? 'fa fa-star' : 'fa fa-star-o'"></i></button>
          </article>
        </div>
      </section>
    </div>

    <div v-if="scheduleOpen" class="schedule-row">
      <label>发送时间 <input v-model="scheduledAt" type="datetime-local" :min="minimumSchedule" /></label>
      <button @click="scheduleOpen = false">完成</button>
    </div>

    <div v-if="markdownPreview" class="markdown-preview" aria-label="Markdown 预览">
      <pre>{{ draft || '输入 Markdown 后在这里预览' }}</pre>
    </div>

    <div class="editor-row">
      <textarea
        ref="editor"
        v-model="draft"
        :disabled="disabled || sending"
        :placeholder="disabled ? disabledReason : '输入消息，Enter 发送，Shift + Enter 换行'"
        :style="{ height: `${editorHeight}px` }"
        spellcheck="true"
        @compositionstart="composing = true"
        @compositionend="composing = false"
        @keydown="handleKeydown"
        @input="onInput"
        @paste="handlePaste"
      ></textarea>
      <div v-if="emojiSuggestions.length" class="emoji-shortcut-menu" role="listbox" aria-label="Emoji 快捷补全">
        <button v-for="emoji in emojiSuggestions" :key="emoji.unicode" role="option" @mousedown.prevent="insertEmojiSuggestion(emoji)">
          <span>{{ emoji.unicode }}</span><small>:{{ emoji.shortcodes?.[0] || emoji.name }}:</small>
        </button>
      </div>
      <div v-if="mentionSuggestions.length" class="mention-shortcut-menu" role="listbox" aria-label="群成员提及">
        <button v-for="member in mentionSuggestions" :key="member.userId" role="option" @mousedown.prevent="insertMention(member)"><span>{{ member.name.slice(0,1) }}</span><small>@{{ member.name }}</small></button>
      </div>
      <button v-if="draft || attachments.length || pendingStickers.length" class="reset-button" title="清空" aria-label="清空输入" @click="reset"><i class="fa fa-times-circle"></i></button>
      <button class="send-button" :disabled="!canSend || sending" @click="send">
        <i v-if="sending" class="fa fa-circle-o-notch fa-spin"></i>
        <i v-else class="fa fa-paper-plane"></i>
        <span>{{ scheduledAt ? '定时发送' : '发送' }}</span>
      </button>
      <span class="resize-handle" title="拖拽调整输入区高度" @pointerdown="startResize"></span>
    </div>
    <footer class="composer-footer">
      <span>{{ sendShortcutLabel }}</span>
      <span v-if="recording">录音中 {{ recordingSeconds }} 秒</span>
      <span v-if="sendLimit">{{ sendLimit }}</span>
    </footer>

    <input ref="fileInput" class="sr-only" type="file" multiple @change="handleFileInput" />

    <dialog ref="fullscreenDialog" class="fullscreen-editor" :open="fullscreen">
      <header><strong>全屏编辑消息</strong><button @click="fullscreen = false"><i class="fa fa-times"></i></button></header>
      <textarea v-model="draft" spellcheck="true"></textarea>
      <footer><span>{{ draft.length }} 字</span><button @click="fullscreen = false">完成编辑</button></footer>
    </dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Database } from 'emoji-picker-element'
import type { EmojiClickEvent, NativeEmoji } from 'emoji-picker-element/shared.js'
import type { MessageCenterMessage, SendMessageInput } from '@/stores/messageCenterStore'
import { uploadImAttachment } from '@/utils/imAttachmentUploader'
import { getFavoritePlatformStickers, searchPlatformStickers, toggleFavoritePlatformSticker, type PlatformSticker } from '@/utils/platformStickerCatalog'

interface PendingAttachment { id: string; file: File; name: string; description: string; kind: 'image'|'video'|'audio'|'file'; previewUrl: string; progress: number; uploading: boolean; duration?: number }
interface MentionCandidate { userId: string; name: string }
const props = withDefaults(defineProps<{ modelValue: string; replyTo?: MessageCenterMessage | null; disabled?: boolean; disabledReason?: string; enterToSend?: boolean; showCounter?: boolean; sendLimit?: string; mentionCandidates?: MentionCandidate[] }>(), { enterToSend: true, showCounter: true, disabledReason: '当前会话不可发送消息', mentionCandidates: () => [] })
const emit = defineEmits<{ 'update:modelValue':[value:string]; send:[payload:SendMessageInput]; 'cancel-reply':[]; 'pick-drive-file':[]; 'share-card':[]; 'attachment-error':[message:string] }>()
const draft = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value.slice(0, 5000)) })
const editor = ref<HTMLTextAreaElement|null>(null)
const fileInput = ref<HTMLInputElement|null>(null)
const fullscreenDialog = ref<HTMLDialogElement|null>(null)
const unicodePicker = ref<HTMLElement|null>(null)
const attachments = ref<PendingAttachment[]>([])
const dragging = ref(false), composing = ref(false), sending = ref(false), emojiOpen = ref(false), emojiPinned = ref(false), markdownPreview = ref(false), toolbarExpanded = ref(true), attachmentsFolded = ref(false), scheduleOpen = ref(false), fullscreen = ref(false), recording = ref(false), recordingPaused = ref(false)
const emojiKeyword = ref(''), emojiTab = ref<'unicode'|'platform'|'favorites'>('unicode'), scheduledAt = ref(''), editorHeight = ref(68), recordingSeconds = ref(0)
const linkPreview = ref<{url:string;host:string}|null>(null)
let dragIndex = -1, recordingTimer: ReturnType<typeof setInterval>|null = null, mediaRecorder: MediaRecorder|null = null, recordingChunks: Blob[] = [], resizeStart = { y:0, height:68 }
// AUDIT FIX [3.1-3.8] / IM-EMOJI-SESSION-20260810：移除不完整的手工 Emoji
// 数组；emoji-picker-element Database 统一维护 Unicode 15.0、肤色偏好和最近使用。
const unicodeEmojiDataSource = 'https://cdn.jsdelivr.net/npm/emoji-picker-element-data@^1/zh/emojibase/data.json'
const emojiDatabase = new Database({ locale: 'zh', dataSource: unicodeEmojiDataSource })
// 中文数据集用于面板与中文搜索；英文数据集保留成熟库的标准 shortcodes/英文名称索引。
const englishEmojiDatabase = new Database({ locale: 'en' })
const emojiSuggestions = ref<NativeEmoji[]>([])
const mentionSuggestions = computed(() => {
  const cursor = editor.value?.selectionStart ?? draft.value.length
  const query = draft.value.slice(0, cursor).match(/(?:^|\s)@([^\s@]{0,24})$/)?.[1]
  if (query === undefined) return []
  return props.mentionCandidates.filter(member => member.name.toLowerCase().includes(query.toLowerCase())).slice(0, 6)
})
const platformStickers = ref<PlatformSticker[]>([])
const favoriteStickers = ref<PlatformSticker[]>(getFavoritePlatformStickers())
const pendingStickers = ref<PlatformSticker[]>([])
const stickerLoading = ref(false)
const stickerConfigurationRequired = ref(false)
const totalAttachmentSize = computed(() => attachments.value.reduce((sum,item)=>sum+item.file.size,0))
const visibleAttachments = computed(() => attachmentsFolded.value ? attachments.value.slice(0,3) : attachments.value)
const canSend = computed(() => !props.disabled && (draft.value.trim().length>0 || attachments.value.length>0 || pendingStickers.value.length>0))
const sendShortcutLabel = computed(() => props.enterToSend ? 'Enter 发送 · Shift+Enter 换行' : 'Ctrl+Enter 发送')
const minimumSchedule = computed(() => new Date(Date.now()+60_000).toISOString().slice(0,16))

watch(fullscreen, value => { if (value) fullscreenDialog.value?.showModal(); else fullscreenDialog.value?.close() })
watch(() => props.modelValue, value => { const match=value.match(/https?:\/\/[^\s]+/); if(match){try{const url=new URL(match[0]);linkPreview.value={url:url.toString(),host:url.hostname}}catch{}} else linkPreview.value=null })
watch(emojiKeyword, () => { if (emojiTab.value !== 'unicode') void searchStickers() })

function handleKeydown(event: KeyboardEvent): void { if (composing.value) return; if(mentionSuggestions.value.length&&event.key==='Enter'){event.preventDefault();insertMention(mentionSuggestions.value[0]);return} if(emojiSuggestions.value.length&&event.key==='Enter'){event.preventDefault();insertEmojiSuggestion(emojiSuggestions.value[0]);return} const shouldSend = props.enterToSend ? event.key==='Enter'&&!event.shiftKey : event.key==='Enter'&&(event.ctrlKey||event.metaKey); if(shouldSend){event.preventDefault();send()} if(event.key==='Escape'&&emojiOpen.value)emojiOpen.value=false }
function onInput(): void { const el=editor.value;if(!el)return;el.style.height='auto';editorHeight.value=Math.min(220,Math.max(44,el.scrollHeight));void refreshEmojiSuggestions() }
function insertAtCursor(value:string):void{const el=editor.value;if(!el){draft.value+=value;return}const start=el.selectionStart,end=el.selectionEnd;draft.value=draft.value.slice(0,start)+value+draft.value.slice(end);nextTick(()=>{el.focus();el.setSelectionRange(start+value.length,start+value.length)})}
function handleUnicodeEmojiClick(event: Event):void{const detail=(event as EmojiClickEvent).detail;const unicode=detail?.unicode;if(!unicode)return;insertAtCursor(unicode);void emojiDatabase.incrementFavoriteEmojiCount(unicode);if(!emojiPinned.value)emojiOpen.value=false}
async function refreshEmojiSuggestions():Promise<void>{const el=editor.value;const before=draft.value.slice(0,el?.selectionStart??draft.value.length);const match=before.match(/:([a-zA-Z0-9_+-]{2,})$/);if(!match){emojiSuggestions.value=[];return}try{const [zh,en]=await Promise.all([emojiDatabase.getEmojiBySearchQuery(match[1]),englishEmojiDatabase.getEmojiBySearchQuery(match[1])]);const seen=new Set<string>();emojiSuggestions.value=[...zh,...en].filter((item):item is NativeEmoji=>'unicode'in item).filter(item=>{if(seen.has(item.unicode))return false;seen.add(item.unicode);return true}).slice(0,6)}catch{emojiSuggestions.value=[]}}
function insertEmojiSuggestion(emoji:NativeEmoji):void{const el=editor.value;const cursor=el?.selectionStart??draft.value.length;const before=draft.value.slice(0,cursor).replace(/:([a-zA-Z0-9_+-]{2,})$/,emoji.unicode);draft.value=before+draft.value.slice(cursor);emojiSuggestions.value=[];void emojiDatabase.incrementFavoriteEmojiCount(emoji.unicode);nextTick(()=>{editor.value?.focus();editor.value?.setSelectionRange(before.length,before.length)})}
/** GROUP-CHAT-20260810 [3.10]：@ 仅写入普通文本，沿用消息 V2 TextPayload，不新增未定义协议字段。 */
function insertMention(member:MentionCandidate):void{const el=editor.value;const cursor=el?.selectionStart??draft.value.length;const before=draft.value.slice(0,cursor).replace(/@[^\s@]{0,24}$/ ,`@${member.name} `);draft.value=before+draft.value.slice(cursor);nextTick(()=>{editor.value?.focus();editor.value?.setSelectionRange(before.length,before.length)})}
async function openPlatformTab():Promise<void>{emojiTab.value='platform';await searchStickers()}
function openFavoritesTab():void{emojiTab.value='favorites';platformStickers.value=favoriteStickers.value}
async function searchStickers():Promise<void>{if(emojiTab.value==='unicode')return;stickerLoading.value=true;try{const result=await searchPlatformStickers(emojiKeyword.value);platformStickers.value=emojiTab.value==='favorites'?favoriteStickers.value:result.items;stickerConfigurationRequired.value=result.configurationRequired}finally{stickerLoading.value=false}}
function queueSticker(sticker:PlatformSticker):void{pendingStickers.value=[...pendingStickers.value,sticker];if(!emojiPinned.value)emojiOpen.value=false;nextTick(()=>editor.value?.focus())}
function removePendingSticker(id:string):void{pendingStickers.value=pendingStickers.value.filter(item=>item.id!==id)}
function isFavorite(sticker:PlatformSticker):boolean{return favoriteStickers.value.some(item=>item.id===sticker.id)}
function toggleStickerFavorite(sticker:PlatformSticker):void{favoriteStickers.value=toggleFavoritePlatformSticker(sticker);if(emojiTab.value==='favorites')platformStickers.value=favoriteStickers.value}
function insertCodeBlock():void{insertAtCursor('\n```text\n\n```\n');markdownPreview.value=true}
const MAX_ATTACHMENT_SIZE = 2 * 1024 * 1024 * 1024
function addFiles(files:File[]):void{for(const file of files){if(file.size>MAX_ATTACHMENT_SIZE){emit('attachment-error',`${file.name} 超过 2GB 单文件限制，请改用网盘分享链接`);continue}const kind=file.type.startsWith('image/')?'image':file.type.startsWith('video/')?'video':file.type.startsWith('audio/')?'audio':'file';attachments.value.push({id:crypto.randomUUID(),file,name:file.name,description:'',kind,previewUrl:URL.createObjectURL(file),progress:0,uploading:false})}attachmentsFolded.value=attachments.value.length>3}
function handleFileInput(event:Event):void{addFiles([...(event.target as HTMLInputElement).files||[]]);(event.target as HTMLInputElement).value=''}
function handleDrop(event:DragEvent):void{dragging.value=false;addFiles([...(event.dataTransfer?.files||[])])}
function handlePaste(event:ClipboardEvent):void{const files=[...(event.clipboardData?.files||[])];if(files.length){event.preventDefault();addFiles(files);return}const html=event.clipboardData?.getData('text/html');const plain=event.clipboardData?.getData('text/plain')||'';if(html&&/<table[\s>]/i.test(html)){event.preventDefault();insertAtCursor(tableHtmlToMarkdown(html));return}if(plain.includes('\t')&&plain.includes('\n')){event.preventDefault();insertAtCursor(tsvToMarkdown(plain))}}
function tableHtmlToMarkdown(html:string):string{const doc=new DOMParser().parseFromString(html,'text/html');const rows=[...doc.querySelectorAll('tr')].map(row=>[...row.querySelectorAll('th,td')].map(cell=>(cell.textContent||'').trim()));if(!rows.length)return doc.body.textContent||'';return rows.map((row,i)=>`| ${row.join(' | ')} |${i===0?`\n| ${row.map(()=> '---').join(' | ')} |`:''}`).join('\n')}
function tsvToMarkdown(text:string):string{const rows=text.trim().split('\n').map(row=>row.split('\t'));return rows.map((row,i)=>`| ${row.join(' | ')} |${i===0?`\n| ${row.map(()=> '---').join(' | ')} |`:''}`).join('\n')}
function removeAttachment(id:string):void{const item=attachments.value.find(a=>a.id===id);if(item)URL.revokeObjectURL(item.previewUrl);attachments.value=attachments.value.filter(a=>a.id!==id)}
function clearAttachments():void{attachments.value.forEach(a=>URL.revokeObjectURL(a.previewUrl));attachments.value=[]}
function reorder(target:number):void{if(dragIndex<0||dragIndex===target)return;const next=[...attachments.value];const [item]=next.splice(dragIndex,1);next.splice(target,0,item);attachments.value=next;dragIndex=-1}
function attachmentIcon(item:PendingAttachment):string{return item.kind==='audio'?'fa fa-file-audio-o':'fa fa-file-o'}
function fileSize(bytes:number):string{if(!bytes)return'0 B';const units=['B','KB','MB','GB'];const i=Math.min(3,Math.floor(Math.log(bytes)/Math.log(1024)));return`${(bytes/1024**i).toFixed(i?1:0)} ${units[i]}`}
async function compressImageIfNeeded(attachment:PendingAttachment):Promise<File>{if(attachment.kind!=='image'||attachment.file.size<=10*1024*1024)return attachment.file;try{const bitmap=await createImageBitmap(attachment.file);const scale=Math.min(1,1920/bitmap.width);const canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(bitmap.width*scale));canvas.height=Math.max(1,Math.round(bitmap.height*scale));const context=canvas.getContext('2d');if(!context)throw new Error('Canvas 不可用');context.drawImage(bitmap,0,0,canvas.width,canvas.height);bitmap.close();const blob=await new Promise<Blob|null>(resolve=>canvas.toBlob(resolve,attachment.file.type==='image/png'?'image/png':'image/jpeg',.86));if(!blob)return attachment.file;const compressed=new File([blob],attachment.name,{type:blob.type,lastModified:Date.now()});URL.revokeObjectURL(attachment.previewUrl);attachment.file=compressed;attachment.previewUrl=URL.createObjectURL(compressed);return compressed}catch{emit('attachment-error',`${attachment.name} 图片压缩失败，将尝试原文件上传`);return attachment.file}}
async function send():Promise<void>{if(!canSend.value||sending.value)return;sending.value=true;try{const dueAt=scheduledAt.value?new Date(scheduledAt.value).getTime():undefined;if(dueAt&&dueAt<=Date.now())throw new Error('定时发送时间必须晚于当前时间');for(const sticker of pendingStickers.value){emit('send',{type:'sticker',content:`[表情] ${sticker.title}`,payload:{stickerId:sticker.id,stickerPackId:sticker.packId,url:sticker.url,thumbnailUrl:sticker.thumbnailUrl,width:sticker.width,height:sticker.height,isAnimated:sticker.isAnimated,format:sticker.format,description:sticker.title},scheduledAt:dueAt})}if(draft.value.trim())emit('send',{type:markdownPreview.value?'code':props.replyTo?'reply':'text',content:draft.value.trim(),replyTo:props.replyTo?.id,scheduledAt:dueAt,payload:{isMarkdown:markdownPreview.value,linkPreview:linkPreview.value,quotedMessageId:props.replyTo?.id,quotedSenderId:props.replyTo?.sender,quotedContentPreview:props.replyTo?.content}});for(const attachment of attachments.value){attachment.uploading=true;const file=await compressImageIfNeeded(attachment);const result=await uploadImAttachment(file,p=>attachment.progress=p);const base={diskFileId:result.fileId,fileName:attachment.name,size:result.fileSize,mimeType:result.mimeType,url:'',description:attachment.description};attachment.uploading=false;emit('send',{type:attachment.kind==='image'?'image':attachment.kind==='video'?'video':attachment.kind==='audio'?'voice':'file',content:attachment.description||attachment.name,payload:base,scheduledAt:dueAt})}draft.value='';clearAttachments();pendingStickers.value=[];scheduledAt.value='';emit('cancel-reply')}catch(error){emit('attachment-error',error instanceof Error?error.message:'附件上传失败')}finally{sending.value=false}}
function reset():void{draft.value='';clearAttachments();pendingStickers.value=[];emit('cancel-reply')}
function startResize(event:PointerEvent):void{resizeStart={y:event.clientY,height:editorHeight.value};window.addEventListener('pointermove',resizeMove);window.addEventListener('pointerup',resizeEnd,{once:true})}
function resizeMove(event:PointerEvent):void{editorHeight.value=Math.min(320,Math.max(44,resizeStart.height+(resizeStart.y-event.clientY)))}
function resizeEnd():void{window.removeEventListener('pointermove',resizeMove)}
async function toggleRecording():Promise<void>{if(recording.value){mediaRecorder?.stop();return}try{const stream=await navigator.mediaDevices.getUserMedia({audio:true});recordingChunks=[];mediaRecorder=new MediaRecorder(stream);mediaRecorder.ondataavailable=e=>recordingChunks.push(e.data);mediaRecorder.onstop=()=>{const blob=new Blob(recordingChunks,{type:mediaRecorder?.mimeType||'audio/webm'});addFiles([new File([blob],`voice-${Date.now()}.webm`,{type:blob.type})]);stream.getTracks().forEach(t=>t.stop());recording.value=false;recordingPaused.value=false;if(recordingTimer)clearInterval(recordingTimer)};mediaRecorder.start();recording.value=true;recordingPaused.value=false;recordingSeconds.value=0;recordingTimer=setInterval(()=>{if(!recordingPaused.value)recordingSeconds.value++},1000)}catch{recording.value=false}}
function toggleRecordingPause():void{if(!mediaRecorder||!recording.value)return;if(mediaRecorder.state==='recording'){mediaRecorder.pause();recordingPaused.value=true}else if(mediaRecorder.state==='paused'){mediaRecorder.resume();recordingPaused.value=false}}
function openFilePicker():void{fileInput.value?.click()}
defineExpose({addFiles,openFilePicker})
onBeforeUnmount(()=>{clearAttachments();if(recordingTimer)clearInterval(recordingTimer);mediaRecorder?.stream.getTracks().forEach(t=>t.stop());window.removeEventListener('pointermove',resizeMove)})
</script>

<style scoped>
.composer-shell{position:relative;border:none;background:var(--im-panel);padding:8px 14px max(8px,env(safe-area-inset-bottom))}.composer-shell.dragging{outline:2px solid var(--im-accent);outline-offset:-3px}.drop-overlay{position:absolute;inset:0;z-index:20;display:grid;place-content:center;justify-items:center;background:color-mix(in srgb,var(--im-panel) 88%,transparent);color:var(--im-accent);font-size:18px}.drop-overlay i{font-size:36px}.reply-strip,.link-preview{display:flex;align-items:center;gap:9px;margin-bottom:7px;padding:7px 10px;border-radius:10px;background:var(--im-input);color:var(--im-muted)}.reply-strip>div,.link-preview>div{min-width:0;flex:1;display:flex;flex-direction:column}.reply-strip span,.link-preview span{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:11px}.reply-strip strong,.link-preview strong{font-size:12px;color:var(--im-text)}.attachment-zone{margin-bottom:7px}.attachment-zone header{display:flex;gap:10px;color:var(--im-muted);font-size:11px}.attachment-zone header span{flex:1}.attachment-zone header button{color:var(--im-accent)}.attachment-list{display:flex;gap:8px;overflow-x:auto;padding:7px 0}.attachment-card{position:relative;min-width:210px;max-width:260px;padding:7px;display:flex;align-items:center;gap:8px;border:none;border-radius:11px;background:var(--im-input);box-shadow:0 1px 5px color-mix(in srgb,var(--im-text) 10%,transparent)}.attachment-card>img,.attachment-card>video,.attachment-card>i{width:44px;height:44px;object-fit:cover;border-radius:8px}.attachment-card>i{display:grid;place-items:center;font-size:24px}.attachment-card>div{min-width:0;flex:1}.attachment-card input{width:100%;background:transparent;color:var(--im-text);font-size:12px}.attachment-card small{color:var(--im-muted);font-size:10px}.attachment-description{margin-top:2px}.attachment-card progress{position:absolute;left:7px;right:7px;bottom:2px;width:calc(100% - 14px);height:2px}.large-warning{position:absolute;left:4px;top:4px;color:#d99000}.sticker-draft-zone{display:flex;gap:7px;overflow-x:auto;padding:6px 0}.sticker-draft{position:relative;width:46px;height:46px;border-radius:10px;background:var(--im-input);box-shadow:0 1px 5px color-mix(in srgb,var(--im-text) 10%,transparent)}.sticker-draft img{width:100%;height:100%;object-fit:contain}.sticker-draft i{position:absolute;right:-3px;top:-3px;width:15px;height:15px;display:grid;place-items:center;border-radius:50%;background:var(--im-text);color:var(--im-panel);font-size:9px}.composer-toolbar{height:34px;display:flex;align-items:center;gap:2px}.composer-toolbar>button{width:34px;height:32px;border-radius:8px;color:var(--im-muted)}.composer-toolbar>button:hover,.composer-toolbar>button.active{background:transparent;color:var(--im-accent)}.composer-spacer{flex:1}.character-count{font-size:10px;color:var(--im-muted)}.emoji-panel{position:absolute;left:12px;bottom:calc(100% - 4px);z-index:30;width:min(390px,calc(100vw - 24px));padding:10px;border:1px solid var(--im-border);border-radius:14px;background:var(--im-panel);box-shadow:var(--im-shadow)}.emoji-panel header{display:flex;gap:7px}.emoji-panel header input{flex:1;height:34px;padding:0 9px;border-radius:9px;background:var(--im-input);color:var(--im-text)}.emoji-panel header button{width:34px;height:34px;border-radius:8px}.emoji-panel header button.active,.emoji-panel nav button.active{background:var(--im-selected)}.emoji-panel nav{display:flex;gap:3px;overflow-x:auto;margin:7px 0;border-bottom:1px solid var(--im-border)}.emoji-panel nav button{height:32px;padding:0 8px;border-radius:8px;white-space:nowrap;color:var(--im-muted);font-size:11px}.unicode-emoji-pane{height:340px;overflow:hidden}.unicode-emoji-pane emoji-picker{display:block;width:100%;height:100%;--border-color:transparent;--background:var(--im-panel);--input-border-color:transparent;--input-font-color:var(--im-text);--input-placeholder-color:var(--im-muted);--category-button-color:var(--im-muted);--category-button-active-color:var(--im-accent)}.platform-sticker-pane{height:250px;overflow:auto}.sticker-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:6px}.sticker-item{position:relative;min-width:0}.sticker-item>button:first-child{width:100%;aspect-ratio:1;display:grid;place-items:center;border-radius:8px;background:var(--im-input)}.sticker-item img{max-width:100%;max-height:100%;object-fit:contain}.favorite-sticker{position:absolute;right:1px;bottom:1px;width:21px;height:21px;border-radius:50%;background:color-mix(in srgb,var(--im-panel) 82%,transparent);color:#e0a000;font-size:10px}.sticker-empty{height:100%;display:grid;place-content:center;gap:8px;justify-items:center;color:var(--im-muted);font-size:12px;text-align:center}.schedule-row{display:flex;align-items:center;gap:10px;padding:5px 0;color:var(--im-muted);font-size:12px}.schedule-row label{flex:1}.schedule-row input{margin-left:7px;padding:4px;border-radius:7px;background:var(--im-input);color:var(--im-text)}.schedule-row button{color:var(--im-accent)}.markdown-preview{max-height:140px;overflow:auto;margin-bottom:6px;padding:8px;border-radius:9px;background:var(--im-input)}.markdown-preview pre{white-space:pre-wrap;font-family:inherit;color:var(--im-text)}.editor-row{position:relative;display:flex;align-items:flex-end;gap:8px}.editor-row textarea{min-height:44px;max-height:220px;min-width:0;flex:1;padding:10px 34px 10px 11px;resize:none;border:none;border-radius:0;background:transparent;color:var(--im-text);line-height:1.5;outline:none;box-shadow:none}.editor-row textarea:focus{border:none;outline:none;box-shadow:none}.editor-row textarea::placeholder{color:var(--im-muted)}.emoji-shortcut-menu,.mention-shortcut-menu{position:absolute;z-index:32;bottom:calc(100% + 3px);left:4px;min-width:188px;padding:4px;border-radius:9px;background:var(--im-panel);box-shadow:var(--im-shadow)}.mention-shortcut-menu{left:198px}.emoji-shortcut-menu button,.mention-shortcut-menu button{width:100%;display:flex;align-items:center;gap:8px;padding:5px 7px;border-radius:6px;text-align:left}.emoji-shortcut-menu button:hover,.mention-shortcut-menu button:hover{background:var(--im-hover)}.emoji-shortcut-menu span,.mention-shortcut-menu span{font-size:19px}.emoji-shortcut-menu small,.mention-shortcut-menu small{color:var(--im-muted)}.reset-button{position:absolute;right:102px;bottom:11px;color:var(--im-muted)}.send-button{height:42px;padding:0 14px;display:flex;align-items:center;gap:7px;border-radius:12px;background:var(--im-accent);color:#fff;font-weight:700}.send-button:disabled{opacity:.45;cursor:not-allowed}.resize-handle{position:absolute;top:-5px;left:40%;right:40%;height:7px;cursor:ns-resize}.composer-footer{height:18px;display:flex;justify-content:space-between;align-items:end;color:var(--im-muted);font-size:10px}.fullscreen-editor{inset:4vh 3vw;width:94vw;height:92vh;padding:0;border:1px solid var(--im-border);border-radius:16px;background:var(--im-panel);color:var(--im-text)}.fullscreen-editor::backdrop{background:#0008}.fullscreen-editor header,.fullscreen-editor footer{height:56px;padding:0 18px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--im-border)}.fullscreen-editor footer{border-top:1px solid var(--im-border);border-bottom:0}.fullscreen-editor textarea{width:100%;height:calc(100% - 112px);padding:24px;resize:none;background:var(--im-chat-bg);color:var(--im-text);outline:0}.fullscreen-editor footer button{padding:8px 14px;border-radius:9px;background:var(--im-accent);color:#fff}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}
@media(max-width:767px){.composer-shell{padding:6px 8px max(6px,env(safe-area-inset-bottom))}.composer-toolbar>button:nth-of-type(n+5){display:none}.send-button span,.composer-footer{display:none}.send-button{width:44px;padding:0;justify-content:center}.reset-button{right:58px}.attachment-card{min-width:190px}.emoji-panel{position:fixed;left:0;bottom:calc(58px + env(safe-area-inset-bottom));width:100vw;border-radius:18px 18px 0 0}.unicode-emoji-pane{height:300px}.platform-sticker-pane{height:280px}.sticker-grid{grid-template-columns:repeat(5,1fr)}}
</style>
