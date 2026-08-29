<template>
  <div
    ref="root"
    class="message-center"
    :class="[`font-${preferences.fontSize}`, `density-${preferences.density}`, `mobile-${mobilePane}`, { 'right-open': rightOpen, 'is-offline': !online }]"
  >
    <div v-if="!online || store.connectionState === 'reconnecting' || recoveryBanner" class="network-banner" :class="online ? 'recovering' : 'offline'" role="status">
      <i :class="online ? 'fa fa-refresh fa-spin' : 'fa fa-wifi'"></i>
      <span>{{ !online ? '网络已断开，历史消息仍可浏览；恢复连接前无法发送。' : recoveryBanner || '实时连接正在恢复，消息将自动同步。' }}</span>
      <button v-if="online" @click="store.connectRealtime">立即重连</button>
    </div>

    <button v-if="newMessageBanner" class="new-message-banner" @click="openBannerConversation">
      <span>{{ newMessageBanner.title.slice(0, 1) }}</span><div><strong>{{ newMessageBanner.title }}</strong><small>{{ newMessageBanner.lastMessage }}</small></div><i class="fa fa-angle-right"></i>
    </button>

    <main
      class="message-grid"
      :style="desktopGridStyle"
      @keydown="handleWorkspaceKeydown"
    >
      <ConversationList
        v-model:keyword="conversationKeyword"
        class="left-pane"
        :conversations="store.sortedConversations"
        :active-id="store.activeConversationId"
        :drafts="store.drafts"
        :typing-users="store.typingUsers"
        :loading="store.loadingConversations"
        :display-name="authStore.displayName"
        :avatar="authStore.user.image_path"
        :connection-state="store.connectionState"
        :compact="preferences.leftWidth < 240"
        @open="openConversation"
        @refresh="refresh"
        @show-contacts="openContacts"
        @open-settings="settingsOpen = true"
        @toggle-theme="toggleTheme"
        @popout="popout"
        @profile="openConversationProfile"
        @drag-start="handleConversationDragStart"
        @drop="handleConversationDrop"
        @action="handleConversationAction"
      />

      <div class="resize-bar left-resizer" role="separator" aria-label="调整会话列表宽度" tabindex="0" @pointerdown="startResize('left', $event)" @keydown="resizeByKeyboard('left', $event)"></div>

      <section class="chat-pane" aria-label="聊天窗口">
        <template v-if="store.activeConversation">
          <header class="chat-header">
            <button class="mobile-back" aria-label="返回会话列表" @click="mobilePane = 'conversations'"><i class="fa fa-angle-left"></i></button>
            <button class="chat-avatar" aria-label="查看会话详情" @click="openDetails">
              <img v-if="store.activeConversation.avatar" :src="store.activeConversation.avatar" alt="" />
              <span v-else>{{ store.activeConversation.title.slice(0, 1) }}</span>
            </button>
            <button class="chat-title" @click="openDetails">
              <strong>{{ store.activeConversation.title }}</strong>
              <span v-if="store.typingUsers[store.activeConversation.id]" class="typing">正在输入…</span>
              <span v-else>{{ headerStatus }}</span>
            </button>
            <div v-if="store.activeConversation.conversationType === 1 && !store.activeConversation.canSend" class="relationship-warning" role="status">
              <i class="fa fa-exclamation-triangle" aria-hidden="true"></i>
              <span>{{ store.activeConversation.sessionStatus === 'FRIEND_REMOVED' ? '对方已不是你的好友，无法发送消息' : '用户状态不可用，无法发送消息' }}</span>
              <button v-if="store.activeConversation.sessionStatus === 'FRIEND_REMOVED'" @click="router.push(`/im/add-friend?q=${encodeURIComponent(store.activeConversation.targetId)}`)">重新添加</button>
            </div>
            <div class="chat-actions">
              <button title="语音通话" aria-label="语音通话" @click="startCall('voice')"><i class="fa fa-phone"></i></button>
              <button title="视频通话" aria-label="视频通话" @click="startCall('video')"><i class="fa fa-video-camera"></i></button>
              <button title="搜索聊天记录" aria-label="搜索聊天记录" @click="openSearch"><i class="fa fa-search"></i></button>
              <button title="会话详情" aria-label="会话详情" @click="openDetails"><i class="fa fa-info-circle"></i></button>
              <button class="more-action" title="更多操作" aria-label="更多操作" @click="headerMenuOpen = !headerMenuOpen"><i class="fa fa-ellipsis-h"></i></button>
            </div>
            <div v-if="headerMenuOpen" class="header-menu">
              <button @click="rightTab='files';rightOpen=true">查看共享文件</button>
              <button @click="confirmClearLocal">清除本地缓存</button>
              <button v-if="store.activeConversation.conversationType === 1" @click="blockFriend(store.activeConversation.targetId);headerMenuOpen=false">举报 / 拉黑</button>
            </div>
          </header>

          <MessageList
            ref="messageList"
            :messages="store.activeMessages"
            :loading-history="Boolean(store.loadingHistory[store.activeConversation.id])"
            :history-complete="Boolean(store.historyComplete[store.activeConversation.id])"
            :conversation-type="store.activeConversation.conversationType"
            @load-older="store.loadOlderMessages"
            @retry="retryMessage"
            @quote="replyTo = $event"
            @recall="recallMessage"
            @jump="jumpMessage"
            @files="composer?.addFiles($event)"
            @drag-active="dragActive = $event"
            @forward="showUnavailable('批量转发需要 IM Business 转发接口')"
            @delete-selected="deleteLocalMessages"
            @report="showUnavailable('举报与风控案件接口尚未接入')"
            @attachment-error="showToast($event, 'fa fa-exclamation-circle')"
          />

          <MessageComposer
            ref="composer"
            v-model="draft"
            :reply-to="replyTo"
            :disabled="!online || store.activeConversation.canSend === false"
            :disabled-reason="!online ? '网络不可用，恢复连接后可继续发送' : store.activeConversation.sessionStatus === 'GROUP_LEFT' ? '你已退出该群组，历史消息仍可查看' : '好友关系已解除，历史消息仍可查看'"
            :enter-to-send="preferences.enterToSend"
            :show-counter="preferences.showCounter"
            :mention-candidates="groupMentionMembers.map(member => ({ userId: member.userId, name: member.alias || member.nickname || member.userId }))"
            @send="sendMessage"
            @cancel-reply="replyTo = null"
            @pick-drive-file="showUnavailable('网盘文件选择器将在独立资源选择组件接入后开放')"
            @share-card="shareMyCard"
          />
        </template>
        <div v-else-if="store.loadingConversations" class="chat-skeleton"><header></header><main><i></i><i></i><i></i></main><footer></footer></div>
        <div v-else class="chat-empty">
          <span><i class="fa fa-comments-o"></i></span><h2>消息与协作</h2><p>选择已有会话，或在联系人中打开已建立好友关系的聊天。</p><button @click="openContacts">查看联系人</button>
        </div>
      </section>

      <div v-if="rightOpen" class="resize-bar right-resizer" role="separator" aria-label="调整详情面板宽度" tabindex="0" @pointerdown="startResize('right', $event)" @keydown="resizeByKeyboard('right', $event)"></div>

      <DetailPanel
        v-if="rightOpen"
        v-model:tab="rightTab"
        class="right-pane"
        :conversation="store.activeConversation"
        :messages="store.activeMessages"
        :friends="store.friends"
        :groups="store.sortedGroups"
        :groups-loading="store.groupsLoading"
        :current-user-id="authStore.user.id || ''"
        :active-group-id="activeGroupId"
        :blacklisted-friends="store.blacklistedFriends"
        :pending-count="store.pendingFriendRequestCount"
        :contacts-loading="store.contactsLoading"
        @close="rightOpen = false"
        @open-friend="openFriendConversation"
        @open-group="openGroupConversation"
        @create-group="router.push('/im/create-group')"
        @leave-group="leaveGroup"
        @dissolve-group="dissolveGroup"
        @groups-changed="store.fetchGroups"
        @toast="(message,error) => showToast(message,error?'fa fa-exclamation-circle':'fa fa-check-circle')"
        @jump-message="jumpMessage"
        @send-file="composer?.openFilePicker()"
        @download-file="downloadSharedFile"
        @toggle-muted="store.activeConversation && store.toggleMuted(store.activeConversation)"
        @toggle-pinned="store.activeConversation && store.togglePinned(store.activeConversation)"
        @add-friend="router.push('/im/add-friend')"
        @friend-requests="router.push('/im/friend-requests')"
        @remark-friend="updateFriendRemark"
        @star-friend="updateFriendStar"
        @remove-friend="removeFriend"
        @block-friend="blockFriend"
        @unblock-friend="unblockFriend"
      />
      <button v-else class="open-detail-button" title="展开详情面板" aria-label="展开详情面板" @click="rightOpen = true"><i class="fa fa-angle-double-left"></i></button>
    </main>

    <nav class="mobile-tabs" aria-label="移动端消息导航">
      <button :class="{active:mobilePane==='conversations'}" @click="mobilePane='conversations'"><i class="fa fa-commenting-o"></i><span>消息</span><b v-if="store.totalUnread">{{ store.totalUnread>99?'99+':store.totalUnread }}</b></button>
      <button :class="{active:mobilePane==='chat'}" :disabled="!store.activeConversation" @click="mobilePane='chat'"><i class="fa fa-comments-o"></i><span>聊天</span></button>
      <button :class="{active:mobilePane==='contacts'}" @click="openContacts"><i class="fa fa-address-book-o"></i><span>联系人</span></button>
      <button :class="{active:mobilePane==='groups'}" @click="openGroups"><i class="fa fa-users"></i><span>群组</span></button>
      <button @click="settingsOpen=true"><i class="fa fa-user-o"></i><span>我</span></button>
    </nav>

    <dialog ref="settingsDialog" class="settings-dialog" :open="settingsOpen" @click.self="settingsOpen=false">
      <header><div><strong>消息中心设置</strong><span>设置保存在当前设备；后端同步接口未提供时不会伪造多端同步。</span></div><button @click="settingsOpen=false"><i class="fa fa-times"></i></button></header>
      <section>
        <label><span>主题</span><select v-model="preferences.theme" @change="applyPreferences"><option value="auto">跟随系统</option><option value="light">亮色</option><option value="dark">暗色</option></select></label>
        <label><span>字体大小</span><select v-model="preferences.fontSize" @change="savePreferences"><option value="small">小</option><option value="medium">中</option><option value="large">大</option></select></label>
        <label><span>界面密度</span><select v-model="preferences.density" @change="savePreferences"><option value="compact">紧凑</option><option value="comfortable">舒适</option></select></label>
        <label><span>发送快捷键</span><select v-model="preferences.enterToSend" @change="savePreferences"><option :value="true">Enter 发送</option><option :value="false">Ctrl/⌘ + Enter 发送</option></select></label>
        <label><span>桌面通知</span><input v-model="preferences.desktopNotifications" type="checkbox" @change="requestNotifications" /></label>
        <label><span>字数统计</span><input v-model="preferences.showCounter" type="checkbox" @change="savePreferences" /></label>
        <label><span>免打扰</span><input v-model="preferences.doNotDisturb" type="checkbox" @change="savePreferences" /></label>
      </section>
      <footer><button @click="resetLayout">恢复默认布局</button><button class="primary" @click="settingsOpen=false">完成</button></footer>
    </dialog>

    <div v-if="toast" class="message-toast" role="status" @click="toast=null"><i :class="toast.icon"></i><span>{{ toast.text }}</span></div>

    <IncomingCallDialog :visible="call.hasIncomingCall.value" :incoming-call-info="incomingCallInfo" @accept="call.acceptIncomingCall" @reject="call.rejectIncomingCall('用户拒绝')" />
    <FloatingCallWindow
      :visible="call.isInCall.value"
      :peer-name="callPeerName"
      :is-video="call.isVideoCall.value"
      :call-duration="call.callDuration.value"
      :is-muted="call.isMuted.value"
      :is-camera-off="call.isCameraOff.value"
      :local-stream="call.localStream.value"
      :remote-stream="call.remoteStream.value"
      @hangup="call.hangup"
      @toggle-mute="call.toggleMute"
      @toggle-camera="call.toggleCamera"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConversationList from '@/components/im/message-center/ConversationList.vue'
import MessageList from '@/components/im/message-center/MessageList.vue'
import MessageComposer from '@/components/im/message-center/MessageComposer.vue'
import DetailPanel from '@/components/im/message-center/DetailPanel.vue'
import IncomingCallDialog from '@/components/im/IncomingCallDialog.vue'
import FloatingCallWindow from '@/components/im/FloatingCallWindow.vue'
import { useMessageCenterStore, type MessageCenterConversation, type MessageCenterMessage, type SendMessageInput } from '@/stores/messageCenterStore'
import { useAuthStore } from '@/stores/authStore'
import { useCall } from '@/composables/useCall'
import { CallType } from '@/api/im/types'
import { downloadImAttachment } from '@/utils/imAttachmentAccess'
import { listGroupMembersApi } from '@/api/im/groupApi'
import type { GroupMemberDTO } from '@/api/im/types'

type MobilePane='conversations'|'chat'|'contacts'|'groups'
type DetailTab='details'|'contacts'|'groups'|'files'|'search'
interface Preferences{theme:'light'|'dark'|'auto';fontSize:'small'|'medium'|'large';density:'compact'|'comfortable';enterToSend:boolean;desktopNotifications:boolean;showCounter:boolean;doNotDisturb:boolean;leftWidth:number;rightWidth:number}
const PREF_KEY='pcd-im-preferences-v2'
const store=useMessageCenterStore(),authStore=useAuthStore(),call=useCall(),route=useRoute(),router=useRouter()
const root=ref<HTMLElement|null>(null),messageList=ref<InstanceType<typeof MessageList>|null>(null),composer=ref<InstanceType<typeof MessageComposer>|null>(null),settingsDialog=ref<HTMLDialogElement|null>(null)
const conversationKeyword=ref(''),draft=ref(''),replyTo=ref<MessageCenterMessage|null>(null),rightOpen=ref(window.innerWidth>=1440),rightTab=ref<DetailTab>('details'),mobilePane=ref<MobilePane>('conversations'),settingsOpen=ref(false),headerMenuOpen=ref(false),online=ref(navigator.onLine),recoveryBanner=ref(''),dragActive=ref(false),newMessageBanner=ref<MessageCenterConversation|null>(null)
const toast=ref<{text:string;icon:string}|null>(null)
const groupMentionMembers=ref<GroupMemberDTO[]>([])
const defaultPreferences:Preferences={theme:'auto',fontSize:'medium',density:'comfortable',enterToSend:true,desktopNotifications:false,showCounter:true,doNotDisturb:false,leftWidth:304,rightWidth:330}
const preferences=reactive<Preferences>({...defaultPreferences,...loadPreferences()})
let resizeState:{side:'left'|'right';startX:number;startWidth:number}|null=null,toastTimer:ReturnType<typeof setTimeout>|null=null,callInitTimer:ReturnType<typeof setTimeout>|null=null

const desktopGridStyle=computed(()=>({gridTemplateColumns:`${preferences.leftWidth}px 5px minmax(360px,1fr) ${rightOpen.value?`5px ${preferences.rightWidth}px`:''}`}))
const headerStatus=computed(()=>{if(store.syncProgress==='syncing')return'消息同步中';const active=store.activeConversation;if(!active)return'';if(active.conversationType===2){const group=store.groups.find(item=>item.groupId===active.targetId);return group?`群成员 ${group.memberCount} 人`:'群聊'}if(active.sessionStatus==='FRIEND_REMOVED')return'好友关系已解除';if(active.onlineStatus==='online')return'在线';if(active.onlineStatus==='busy')return'忙碌';if(active.onlineStatus==='offline')return'离线';return store.connectionState==='online'?'状态未知':'离线'})
const activeGroupId=computed(()=>store.activeConversation?.conversationType===2?store.activeConversation.targetId:undefined)
const incomingCallInfo=computed(()=>call.incomingCallInfo.value)
const callPeerName=computed(()=>call.session.value?.calleeName||call.session.value?.callerName||store.activeConversation?.title||'联系人')

watch(()=>store.activeConversationId,(id,oldId)=>{if(oldId)store.saveDraft(oldId,draft.value);draft.value=id?store.drafts[id]||'':'';replyTo.value=null;if(window.innerWidth<1024&&id)mobilePane.value='chat'})
// GROUP-CHAT-20260810 [3.10]：进入群会话时拉取成员供 @ 提及选择；单聊不加载，避免
// 无效请求。提及文本仍走既有 TextPayload，确保 IM V2 编解码契约不变。
watch(activeGroupId,async groupId=>{groupMentionMembers.value=[];if(!groupId||!authStore.user.id)return;try{const response=await listGroupMembersApi(groupId,authStore.user.id,1,100);if(response.code===200)groupMentionMembers.value=response.data?.items||[]}catch{/* 失败时仅隐藏候选菜单，不阻断消息输入。 */}})
// AUDIT FIX [2.1/2.12/2.13] / IM-MESSAGE-CENTER-20260811：桌面端右侧 Tab 原来只有
// `v-model`，点击“联系人/群组”不会经过移动端的 openContacts/openGroups，因此没有触发
// 首次加载请求。新行为把 Tab 变化作为唯一的数据加载入口；打开面板、切换 Tab 和重连后
// 的补拉都复用 Store 方法，避免桌面端与移动端出现两套行为。失败只提示，不阻断当前聊天。
watch([rightOpen, rightTab], ([opened, tab]) => {
  if (!opened) return
  if (tab === 'contacts') {
    void store.fetchFriends().catch(error => showToast(error instanceof Error ? error.message : '好友列表加载失败', 'fa fa-exclamation-circle'))
  } else if (tab === 'groups') {
    void store.fetchGroups().catch(error => showToast(error instanceof Error ? error.message : '群组列表加载失败', 'fa fa-exclamation-circle'))
  }
})
watch(draft,value=>{if(store.activeConversationId)store.saveDraft(store.activeConversationId,value);store.sendTyping(Boolean(value))})
watch(()=>store.totalUnread,(count,previous)=>{document.title=count?`(${count}) 消息中心 - 私有云盘`:'消息中心 - 私有云盘';if(count>previous){const conversation=store.sortedConversations.find(item=>item.unread>0);if(conversation){newMessageBanner.value=conversation;notify(conversation)}}})
watch(settingsOpen,value=>{if(value)settingsDialog.value?.showModal();else settingsDialog.value?.close()})

onMounted(async()=>{applyPreferences();window.addEventListener('online',onOnline);window.addEventListener('offline',onOffline);window.addEventListener('keydown',globalShortcuts);await store.bootstrap();store.startFriendRequestPolling();store.startGroupPolling();const conversationId=typeof route.query.conversation==='string'?route.query.conversation:'';if(conversationId)await store.openConversation(conversationId);callInitTimer=setTimeout(()=>call.init().catch(()=>{}),900)})
onBeforeUnmount(()=>{if(store.activeConversationId)store.saveDraft(store.activeConversationId,draft.value);store.stopFriendRequestPolling();store.stopGroupPolling();store.disconnectRealtime();call.destroy();window.removeEventListener('online',onOnline);window.removeEventListener('offline',onOffline);window.removeEventListener('keydown',globalShortcuts);window.removeEventListener('pointermove',resizeMove);if(callInitTimer)clearTimeout(callInitTimer);document.title='私有云盘'})

function loadPreferences():Partial<Preferences>{try{return JSON.parse(localStorage.getItem(PREF_KEY)||'{}')}catch{return{}}}
function savePreferences():void{localStorage.setItem(PREF_KEY,JSON.stringify(preferences))}
function applyPreferences():void{const dark=preferences.theme==='dark'||(preferences.theme==='auto'&&matchMedia('(prefers-color-scheme: dark)').matches);document.documentElement.classList.toggle('dark',dark);savePreferences()}
function toggleTheme():void{preferences.theme=document.documentElement.classList.contains('dark')?'light':'dark';applyPreferences()}
function resetLayout():void{Object.assign(preferences,defaultPreferences);rightOpen.value=window.innerWidth>=1440;applyPreferences()}
function openConversation(conversation:MessageCenterConversation):void{store.openConversation(conversation.id);newMessageBanner.value=null}
async function openConversationProfile(conversation:MessageCenterConversation):Promise<void>{if(store.activeConversationId!==conversation.id)await store.openConversation(conversation.id);openDetails()}
async function refresh():Promise<void>{try{await store.fetchConversations();showToast('会话列表已刷新','fa fa-check-circle')}catch(error){showToast(error instanceof Error?error.message:'刷新失败','fa fa-exclamation-circle')}}
function openContacts():void{rightTab.value='contacts';rightOpen.value=true;mobilePane.value='contacts'}
function openGroups():void{rightTab.value='groups';rightOpen.value=true;mobilePane.value='groups'}
function openDetails():void{rightTab.value=store.activeConversation?.conversationType===2?'groups':'details';rightOpen.value=true}
function openSearch():void{rightTab.value='search';rightOpen.value=true}
async function openFriendConversation(friendId:string):Promise<void>{try{await store.openFriendConversation(friendId);mobilePane.value='chat'}catch(error){showToast(error instanceof Error?error.message:'打开好友会话失败','fa fa-exclamation-circle')}}
async function openGroupConversation(groupId:string):Promise<void>{try{await store.openGroupConversation(groupId);mobilePane.value='chat'}catch(error){showToast(error instanceof Error?error.message:'打开群聊失败','fa fa-exclamation-circle')}}
async function leaveGroup(groupId:string):Promise<void>{try{await store.leaveGroup(groupId);showToast('已退出群聊','fa fa-check-circle')}catch(error){showToast(error instanceof Error?error.message:'退出群聊失败','fa fa-exclamation-circle')}}
async function dissolveGroup(groupId:string):Promise<void>{try{await store.dissolveGroup(groupId);showToast('群聊已解散','fa fa-check-circle')}catch(error){showToast(error instanceof Error?error.message:'解散群聊失败','fa fa-exclamation-circle')}}
async function updateFriendRemark(friendId:string,remark:string):Promise<void>{try{await store.updateFriendRemark(friendId,remark);showToast('备注已保存','fa fa-check-circle')}catch(error){showToast(error instanceof Error?error.message:'备注保存失败','fa fa-exclamation-circle')}}
async function updateFriendStar(friendId:string,starred:boolean):Promise<void>{try{await store.toggleFriendStar(friendId,starred);showToast(starred?'已星标好友':'已取消星标','fa fa-check-circle')}catch(error){showToast(error instanceof Error?error.message:'星标更新失败','fa fa-exclamation-circle')}}
async function removeFriend(friendId:string):Promise<void>{if(!confirm('删除好友后会保留历史会话，但不能继续发送消息。确定删除吗？'))return;try{await store.removeFriend(friendId);showToast('已删除好友','fa fa-check-circle')}catch(error){showToast(error instanceof Error?error.message:'删除好友失败','fa fa-exclamation-circle')}}
async function blockFriend(friendId:string):Promise<void>{if(!confirm('拉黑后将解除好友关系，对方无法继续发送消息或好友申请。确定拉黑吗？'))return;try{await store.blockFriend(friendId);showToast('已拉黑该用户','fa fa-ban')}catch(error){showToast(error instanceof Error?error.message:'拉黑失败','fa fa-exclamation-circle')}}
async function unblockFriend(friendId:string):Promise<void>{try{await store.unblockFriend(friendId);showToast('已取消拉黑','fa fa-check-circle')}catch(error){showToast(error instanceof Error?error.message:'取消拉黑失败','fa fa-exclamation-circle')}}
async function sendMessage(payload:SendMessageInput):Promise<void>{try{const sent=await store.sendMessage(payload);if(sent?.status==='failed')showToast(sent.error||'发送失败，可点击红色图标重试','fa fa-exclamation-circle');else{draft.value='';await nextTick();messageList.value?.scrollToBottom(true)}}catch(error){showToast(error instanceof Error?error.message:'发送失败','fa fa-exclamation-circle')}}
async function retryMessage(message:MessageCenterMessage):Promise<void>{await store.retryMessage(message.conversationId,message.id)}
async function recallMessage(message:MessageCenterMessage):Promise<void>{try{await store.recallMessage(message.id)}catch(error){showToast(error instanceof Error?error.message:'撤回失败','fa fa-exclamation-circle')}}
async function downloadSharedFile(message:MessageCenterMessage):Promise<void>{try{await downloadImAttachment({diskFileId:String(message.payload?.diskFileId||message.file_id||''),fileName:String(message.payload?.fileName||message.content||'聊天附件'),fileSize:Number(message.payload?.size||0)})}catch(error){showToast(error instanceof Error?error.message:'附件下载失败','fa fa-exclamation-circle')}}
function jumpMessage(id:string):void{messageList.value?.jumpTo(id);if(window.innerWidth<1024)mobilePane.value='chat'}
async function handleConversationAction(action:'pin'|'mute'|'read'|'detail'|'clear'|'delete'|'block',conversation:MessageCenterConversation):Promise<void>{try{if(action==='pin')await store.togglePinned(conversation);else if(action==='mute')await store.toggleMuted(conversation);else if(action==='read')await store.markConversationRead(conversation);else if(action==='clear'){store.messages[conversation.id]=[];showToast('已清空当前设备的聊天缓存','fa fa-eraser')}else if(action==='delete'){store.conversations=store.conversations.filter(item=>item.id!==conversation.id);if(store.activeConversationId===conversation.id)store.activeConversationId=null;showToast('会话已隐藏，历史消息仍保留','fa fa-eye-slash')}else if(action==='block'){await blockFriend(conversation.targetId)}else openDetails()}catch(error){showToast(error instanceof Error?error.message:'操作失败','fa fa-exclamation-circle')}}
let draggedConversationId:string|null=null
function handleConversationDragStart(conversation:MessageCenterConversation):void{draggedConversationId=conversation.id}
function handleConversationDrop(target:MessageCenterConversation):void{if(!draggedConversationId||draggedConversationId===target.id)return;const list=[...store.conversations];const from=list.findIndex(item=>item.id===draggedConversationId);const to=list.findIndex(item=>item.id===target.id);if(from<0||to<0)return;const [moved]=list.splice(from,1);list.splice(to,0,moved);store.conversations=list;draggedConversationId=null}
function deleteLocalMessages(ids:string[]):void{const cid=store.activeConversationId;if(!cid)return;store.messages[cid]=store.messages[cid].filter(item=>!ids.includes(item.id));showToast('已从当前设备隐藏所选消息','fa fa-trash-o')}
function confirmClearLocal():void{headerMenuOpen.value=false;const id=store.activeConversationId;if(!id)return;if(confirm('仅清除当前设备的聊天缓存，服务器历史消息不会删除。确定继续吗？')){store.messages[id]=[];showToast('当前设备聊天缓存已清除','fa fa-eraser')}}
function shareMyCard():void{sendMessage({type:'custom',content:`${authStore.displayName} 的个人名片`,payload:{customType:'contact_card',data:{userId:authStore.user.id,name:authStore.displayName,avatar:authStore.user.image_path}}})}
function showUnavailable(text:string):void{showToast(text,'fa fa-info-circle')}
function showToast(text:string,icon:string):void{toast.value={text,icon};if(toastTimer)clearTimeout(toastTimer);toastTimer=setTimeout(()=>toast.value=null,3500)}
function onOnline():void{online.value=true;recoveryBanner.value='网络已恢复，正在同步离线消息。';store.connectRealtime();setTimeout(()=>recoveryBanner.value='',3000)}
function onOffline():void{online.value=false}
async function requestNotifications():Promise<void>{savePreferences();if(preferences.desktopNotifications&&'Notification'in window&&Notification.permission==='default')await Notification.requestPermission()}
function notify(conversation:MessageCenterConversation):void{if(preferences.doNotDisturb||!preferences.desktopNotifications||document.visibilityState==='visible'||!('Notification'in window)||Notification.permission!=='granted')return;const notification=new Notification(conversation.title,{body:conversation.lastMessage||'收到一条新消息',icon:conversation.avatar});notification.onclick=()=>{window.focus();openConversation(conversation);notification.close()}}
function openBannerConversation():void{if(newMessageBanner.value)openConversation(newMessageBanner.value)}
function popout():void{window.open(window.location.href,'pcd-message-center','popup=yes,width=1280,height=820,resizable=yes')}
function startCall(type:'voice'|'video'):void{const c=store.activeConversation;if(!c)return;call.startCall(c.targetId,c.title,c.avatar||'',type==='video'?CallType.VIDEO:CallType.VOICE).catch(error=>showToast(error.message||'通话启动失败','fa fa-exclamation-circle'))}

function startResize(side:'left'|'right',event:PointerEvent):void{resizeState={side,startX:event.clientX,startWidth:side==='left'?preferences.leftWidth:preferences.rightWidth};window.addEventListener('pointermove',resizeMove);window.addEventListener('pointerup',resizeEnd,{once:true})}
function resizeMove(event:PointerEvent):void{if(!resizeState)return;const delta=event.clientX-resizeState.startX;if(resizeState.side==='left')preferences.leftWidth=Math.min(400,Math.max(200,resizeState.startWidth+delta));else preferences.rightWidth=Math.min(460,Math.max(260,resizeState.startWidth-delta))}
function resizeEnd():void{resizeState=null;window.removeEventListener('pointermove',resizeMove);savePreferences()}
function resizeByKeyboard(side:'left'|'right',event:KeyboardEvent):void{if(!['ArrowLeft','ArrowRight'].includes(event.key))return;event.preventDefault();const delta=event.key==='ArrowRight'?8:-8;if(side==='left')preferences.leftWidth=Math.min(400,Math.max(200,preferences.leftWidth+delta));else preferences.rightWidth=Math.min(460,Math.max(260,preferences.rightWidth-delta));savePreferences()}
function globalShortcuts(event:KeyboardEvent):void{if((event.ctrlKey||event.metaKey)&&event.key==='1'){event.preventDefault();mobilePane.value='conversations';document.querySelector<HTMLElement>('.conversation-list-panel input')?.focus()}if((event.ctrlKey||event.metaKey)&&event.key==='2'){event.preventDefault();mobilePane.value='chat';document.querySelector<HTMLElement>('.composer-shell textarea')?.focus()}if((event.ctrlKey||event.metaKey)&&event.key==='3'){event.preventDefault();rightOpen.value=true;mobilePane.value='contacts';document.querySelector<HTMLElement>('.detail-panel input')?.focus()}if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='k'){event.preventDefault();document.querySelector<HTMLElement>('.conversation-list-panel input')?.focus()}if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='f'&&store.activeConversation){event.preventDefault();openSearch()}}
function handleWorkspaceKeydown():void{}
</script>

<style scoped>
.message-center{--im-accent:#5468ff;--im-accent-soft:#eef0ff;--im-focus:#5468ff25;--im-text:#18202b;--im-muted:#6d7785;--im-panel:#fff;--im-chat-bg:#f5f7fb;--im-input:#f2f4f7;--im-border:#e6e9ef;--im-hover:#f2f4f8;--im-selected:#e9edff;--im-received:#fff;--im-sent:#596dff;--im-sent-text:#fff;--im-avatar:#e9edf5;--im-system:#e9ecf2;--im-mention:#ffe58f;--im-skeleton:linear-gradient(90deg,#edf0f4,#f7f8fa,#edf0f4);--im-shadow:0 14px 38px rgba(32,42,68,.16);position:relative;height:calc(100dvh - 108px);min-height:580px;overflow:hidden;border:1px solid var(--im-border);border-radius:16px;background:var(--im-panel);color:var(--im-text);box-shadow:0 10px 35px rgba(31,41,55,.08);font-size:14px}.message-center :deep(button),.message-center :deep(input),.message-center :deep(textarea),.message-center :deep(select){font:inherit}.message-center :deep(button){border:0;background-color:transparent;cursor:pointer}.message-center :deep(button:focus-visible),.message-center :deep(input:focus-visible),.message-center :deep(textarea:focus-visible),.message-center :deep(select:focus-visible){outline:2px solid var(--im-accent);outline-offset:2px}:global(.dark) .message-center{--im-text:#edf1f7;--im-muted:#9ca6b5;--im-panel:#141921;--im-chat-bg:#0e131a;--im-input:#202731;--im-border:#2a3340;--im-hover:#232b36;--im-selected:#273253;--im-received:#202833;--im-sent:#5268e8;--im-avatar:#2a3543;--im-system:#252c35;--im-accent-soft:#222c52;--im-skeleton:linear-gradient(90deg,#222a34,#2a333f,#222a34);--im-shadow:0 15px 42px rgba(0,0,0,.45)}.message-grid{height:100%;display:grid;min-width:0}.left-pane{grid-column:1}.left-resizer{grid-column:2}.chat-pane{grid-column:3;min-width:0;min-height:0;display:grid;grid-template-rows:64px minmax(0,1fr) auto;background:var(--im-chat-bg)}.right-resizer{grid-column:4}.right-pane{grid-column:5}.resize-bar{position:relative;z-index:5;background:var(--im-border);cursor:col-resize}.resize-bar::after{content:'';position:absolute;inset:0 -4px}.resize-bar:hover,.resize-bar:focus{background:var(--im-accent)}.chat-header{position:relative;z-index:8;display:flex;align-items:center;gap:10px;padding:8px 12px;border-bottom:1px solid var(--im-border);background:var(--im-panel)}.chat-avatar{width:42px;height:42px;flex:0 0 auto;border-radius:14px;display:grid;place-items:center;overflow:hidden;background:linear-gradient(135deg,var(--im-accent),#8b5cf6);color:#fff;font-weight:800}.chat-avatar img{width:100%;height:100%;object-fit:cover}.chat-title{min-width:0;flex:1;text-align:left;color:var(--im-text)}.chat-title strong,.chat-title span{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.chat-title span{color:var(--im-muted);font-size:11px}.chat-title .typing{color:var(--im-accent)}.chat-actions{display:flex;gap:2px}.chat-actions button,.mobile-back{width:38px;height:38px;border-radius:10px;color:var(--im-muted)}.chat-actions button:hover{background:var(--im-hover);color:var(--im-text)}.mobile-back{display:none}.header-menu{position:absolute;right:12px;top:56px;width:210px;padding:6px;border:1px solid var(--im-border);border-radius:12px;background:var(--im-panel);box-shadow:var(--im-shadow)}.header-menu button{width:100%;height:36px;padding:0 9px;border-radius:8px;color:var(--im-text);text-align:left}.header-menu button:hover{background:var(--im-hover)}.open-detail-button{position:absolute;right:10px;top:82px;z-index:20;width:34px;height:40px;border:1px solid var(--im-border)!important;border-radius:10px!important;background:var(--im-panel)!important;color:var(--im-muted);box-shadow:var(--im-shadow)}.network-banner{position:absolute;z-index:100;left:50%;top:8px;transform:translateX(-50%);max-width:calc(100% - 32px);min-height:36px;padding:7px 12px;display:flex;align-items:center;gap:8px;border-radius:12px;color:#fff;box-shadow:var(--im-shadow);font-size:12px}.network-banner.offline{background:#c7353f}.network-banner.recovering{background:#238b61}.network-banner span{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.network-banner button{text-decoration:underline;color:#fff}.new-message-banner{position:absolute;z-index:90;left:50%;top:12px;transform:translateX(-50%);max-width:360px;padding:8px 12px;display:flex;align-items:center;gap:9px;border:1px solid var(--im-border)!important;border-radius:14px!important;background:var(--im-panel)!important;color:var(--im-text);box-shadow:var(--im-shadow)}.new-message-banner>span{width:34px;height:34px;display:grid;place-items:center;border-radius:50%;background:var(--im-accent);color:#fff}.new-message-banner div{min-width:0;flex:1;text-align:left}.new-message-banner strong,.new-message-banner small{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.new-message-banner small{color:var(--im-muted)}.chat-empty{grid-row:1/4;display:grid;place-content:center;justify-items:center;text-align:center;color:var(--im-muted)}.chat-empty>span{width:84px;height:84px;display:grid;place-items:center;border-radius:28px;background:var(--im-input);font-size:36px}.chat-empty h2{margin-top:18px;color:var(--im-text);font-size:20px}.chat-empty p{margin:4px 0 14px}.chat-empty button{padding:9px 14px;border-radius:10px!important;background:var(--im-accent)!important;color:#fff}.chat-skeleton{grid-row:1/4;display:grid;grid-template-rows:64px 1fr 120px}.chat-skeleton header,.chat-skeleton footer{background:var(--im-skeleton)}.chat-skeleton main{padding:20%;display:grid;gap:20px}.chat-skeleton main i{height:52px;border-radius:15px;background:var(--im-skeleton)}.mobile-tabs{display:none}.settings-dialog{width:min(520px,calc(100vw - 24px));padding:0;border:1px solid var(--im-border);border-radius:16px;background:var(--im-panel);color:var(--im-text);box-shadow:var(--im-shadow)}.settings-dialog::backdrop{background:#0008}.settings-dialog header{padding:16px 18px;display:flex;justify-content:space-between;border-bottom:1px solid var(--im-border)}.settings-dialog header div{display:flex;flex-direction:column}.settings-dialog header span{color:var(--im-muted);font-size:11px}.settings-dialog section{padding:12px 18px}.settings-dialog label{min-height:48px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--im-border)}.settings-dialog select{padding:6px 8px;border-radius:8px;background:var(--im-input);color:var(--im-text)}.settings-dialog footer{padding:12px 18px;display:flex;justify-content:flex-end;gap:8px}.settings-dialog footer button{padding:8px 12px;border-radius:9px!important;color:var(--im-text)}.settings-dialog footer .primary{background:var(--im-accent)!important;color:#fff}.message-toast{position:fixed;right:22px;bottom:22px;z-index:2000;max-width:360px;padding:12px 15px;display:flex;align-items:center;gap:9px;border:1px solid var(--im-border);border-radius:13px;background:var(--im-panel);color:var(--im-text);box-shadow:var(--im-shadow)}.font-small{font-size:12px}.font-large{font-size:16px}.density-compact :deep(.conversation-row){min-height:62px;padding-block:6px}.density-compact :deep(.message-row){margin-block:4px}
@media(max-width:1439px){.message-center{height:calc(100dvh - 98px)}.right-pane{position:absolute;z-index:40;right:0;top:0;bottom:0;width:min(360px,90vw);box-shadow:var(--im-shadow)}.right-resizer{display:none}.message-grid{grid-template-columns:var(--left,304px) 5px minmax(360px,1fr)!important}}
@media(max-width:1023px){.message-center{height:calc(100dvh - 82px);min-height:500px;border-radius:12px}.message-grid{display:block}.left-pane,.chat-pane,.right-pane{position:absolute;inset:0 0 58px;display:none}.mobile-conversations .left-pane{display:grid}.mobile-chat .chat-pane{display:grid}.mobile-contacts .right-pane,.mobile-groups .right-pane{display:grid;width:100%;box-shadow:none}.resize-bar,.open-detail-button{display:none}.mobile-back{display:grid;place-items:center}.mobile-tabs{position:absolute;z-index:60;left:0;right:0;bottom:0;height:58px;padding-bottom:env(safe-area-inset-bottom);display:flex;border-top:1px solid var(--im-border);background:var(--im-panel)}.mobile-tabs button{position:relative;flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;color:var(--im-muted);font-size:10px}.mobile-tabs button i{font-size:18px}.mobile-tabs button.active{color:var(--im-accent)}.mobile-tabs b{position:absolute;top:4px;left:55%;min-width:17px;height:17px;padding:0 4px;border-radius:9px;background:#e5484d;color:#fff;font-size:9px}.new-message-banner{top:6px;width:calc(100% - 20px)}.network-banner{top:6px;width:calc(100% - 20px)}}
@media(max-width:480px){.chat-actions button:nth-child(-n+2){display:none}.message-center{border-radius:0;border-left:0;border-right:0}.chat-header{padding-inline:6px}.settings-dialog{width:100vw;max-width:none;border-radius:18px 18px 0 0;margin:auto 0 0}}
.relationship-warning{position:absolute;left:72px;bottom:-34px;z-index:15;display:flex;align-items:center;gap:7px;max-width:min(520px,calc(100% - 84px));padding:7px 10px;border:1px solid #e7b9b9;border-radius:9px;background:#fff4f4;color:#a62b32;font-size:11px;box-shadow:var(--im-shadow)}.relationship-warning span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.relationship-warning button{color:#9f2c37;text-decoration:underline;white-space:nowrap}:global(.dark) .relationship-warning{background:#3b2024;border-color:#704048;color:#ffb6b7}
@media(prefers-reduced-motion:reduce){.message-center *{scroll-behavior:auto!important;animation-duration:.01ms!important;transition-duration:.01ms!important}}
/* AUDIT FIX [2.1/2.9] / IM-MESSAGE-CENTER-20260811：让三栏 Grid 的可滚动子项正确
   参与高度计算，左侧工具栏固定在消息中心底部，不再跟随会话内容高度漂移。 */
.message-grid{min-height:0;overflow:hidden}
.left-pane,.right-pane{min-width:0;min-height:0;height:100%;overflow:hidden}
</style>
