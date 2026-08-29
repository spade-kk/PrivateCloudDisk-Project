<template>
  <aside class="detail-panel" aria-label="会话详情与联系人">
    <header>
      <nav role="tablist" aria-label="详情面板">
        <button v-for="item in tabs" :key="item.key" role="tab" :aria-selected="tab === item.key" :class="{ active: tab === item.key }" @click="tab = item.key">
          <i :class="item.icon"></i><span>{{ item.label }}</span>
        </button>
      </nav>
      <button class="close-panel" aria-label="折叠详情面板" @click="$emit('close')"><i class="fa fa-angle-double-right"></i></button>
    </header>

    <div v-if="loading" class="detail-skeleton"><i></i><span></span><span></span><span></span></div>

    <section v-else-if="tab === 'details'" class="detail-content">
      <template v-if="conversation">
        <div class="profile-card">
          <span class="large-avatar"><img v-if="conversation.avatar" :src="conversation.avatar" alt="" /><b v-else>{{ conversation.title.slice(0,1) }}</b><i :class="conversation.online ? 'online' : ''"></i></span>
          <h3>{{ conversation.title }}</h3>
          <p>{{ conversation.subtitle || (conversation.conversationType === 2 ? '群组会话' : '平台联系人') }}</p>
          <span class="profile-status">{{ conversation.online ? '在线' : '离线' }}</span>
        </div>
        <div class="quick-actions">
          <button @click="$emit('send-file')"><i class="fa fa-file-o"></i><span>发送文件</span></button>
          <button disabled title="等待通话服务正式开放"><i class="fa fa-phone"></i><span>语音</span></button>
          <button disabled title="等待通话服务正式开放"><i class="fa fa-video-camera"></i><span>视频</span></button>
          <button @click="tab='search'"><i class="fa fa-search"></i><span>查找记录</span></button>
        </div>
        <div class="settings-list">
          <button><span>聊天背景</span><i class="fa fa-angle-right"></i></button>
          <button @click="$emit('toggle-muted')"><span>消息免打扰</span><i :class="conversation.muted ? 'fa fa-toggle-on active' : 'fa fa-toggle-off'"></i></button>
          <button @click="$emit('toggle-pinned')"><span>置顶会话</span><i :class="conversation.pinned ? 'fa fa-toggle-on active' : 'fa fa-toggle-off'"></i></button>
          <button><span>加密传输</span><em>IM V2</em></button>
          <button class="danger" disabled title="IM Business 尚未提供黑名单接口"><span>拉黑 / 举报</span><em>待后端</em></button>
        </div>
        <div v-if="conversation.conversationType === 2" class="group-section">
          <h4>群公告</h4><p>群公告将在群组详情接口返回后显示。</p>
          <h4>群成员</h4><p>点击顶部成员入口加载成员列表。</p>
        </div>
      </template>
      <div v-else class="empty-panel">选择会话后查看详情</div>
    </section>

    <section v-else-if="tab === 'contacts'" class="detail-content contacts-tab">
      <div class="contact-toolbar"><label class="panel-search"><i class="fa fa-search"></i><input v-model="contactKeyword" placeholder="搜索好友、备注或账号" /></label><button title="添加好友" @click="$emit('add-friend')"><i class="fa fa-user-plus"/></button></div>
      <div class="contact-nav"><button :class="{active:contactGroup==='all'}" @click="contactGroup='all'">好友 {{ friends.length }}</button><button :class="{active:contactGroup==='starred'}" @click="contactGroup='starred'">星标</button><button :class="{active:contactGroup==='blacklist'}" @click="contactGroup='blacklist'">黑名单</button><button class="request-link" @click="$emit('friend-requests')">申请 <b v-if="pendingCount">{{ pendingCount }}</b></button></div>
      <!-- AUDIT FIX [5.1/5.10] / IM-EMOJI-SESSION-20260810：联系人仅展示已建立
           的好友关系。点击只查询好友申请接受事务已同步创建的会话，不再创建新会话。 -->
      <div class="capability-note"><i class="fa fa-info-circle"></i><span>好友申请接受后会同步创建双方会话；解除好友后，历史消息仍可查看但无法继续发送。</span></div>
      <div v-if="contactsLoading" class="inline-loading"><i class="fa fa-circle-o-notch fa-spin"></i>正在加载好友</div>
      <p v-if="contactGroup!=='blacklist'" class="contact-stat">{{ filteredFriends.length }} 位好友 · {{ filteredFriends.filter(item=>item.online).length }} 位在线</p>
      <button v-for="friend in filteredFriends" :key="friend.friendId" class="contact-row" @contextmenu.prevent="selectedFriend=friend" @click="selectedFriend=friend">
        <span class="friend-avatar"><img v-if="friend.avatarPath" :src="friend.avatarPath" alt=""/><b v-else>{{ friendLabel(friend).slice(0,1).toUpperCase() }}</b><i v-if="friend.online"></i></span>
        <div><strong>{{ friendLabel(friend) }} <i v-if="friend.starred" class="fa fa-star star"/></strong><small>{{ friend.account || friend.friendId }}</small><em v-if="friend.remark">{{ friend.remark }}</em></div>
        <i v-if="contactGroup!=='blacklist'" class="fa fa-ellipsis-h" @click.stop="selectedFriend=friend"></i>
      </button>
      <div v-if="!contactsLoading && !filteredFriends.length" class="empty-panel">{{ contactKeyword ? '未找到匹配好友' : '暂无好友，请先处理好友申请。' }}</div>
      <div v-if="selectedFriend" class="contact-detail"><button class="detail-back" @click="selectedFriend=null"><i class="fa fa-angle-left"/> 联系人列表</button><span class="detail-avatar"><img v-if="selectedFriend.avatarPath" :src="selectedFriend.avatarPath" alt=""/><b v-else>{{ friendLabel(selectedFriend).slice(0,1) }}</b></span><h3>{{ friendLabel(selectedFriend) }}</h3><p>账号：{{ selectedFriend.account || selectedFriend.friendId }}</p><small>共同空间 {{ selectedFriend.commonSpaceCount || 0 }} · 共同群组 {{ selectedFriend.commonGroupCount || 0 }}</small><label v-if="contactGroup!=='blacklist'" class="remark"><span>备注名</span><input v-model="remarkDraft" maxlength="64" @keyup.enter="saveRemark"/><button @click="saveRemark">保存</button></label><div class="friend-actions"><button v-if="contactGroup!=='blacklist'" class="primary" @click="$emit('open-friend',selectedFriend.friendId)">发送消息</button><button v-if="contactGroup!=='blacklist'" @click="$emit('star-friend',selectedFriend.friendId,!selectedFriend.starred)">{{ selectedFriend.starred?'取消星标':'星标好友' }}</button><button v-if="contactGroup!=='blacklist'" class="danger" @click="$emit('block-friend',selectedFriend.friendId)">拉黑</button><button v-if="contactGroup!=='blacklist'" class="danger" @click="$emit('remove-friend',selectedFriend.friendId)">删除好友</button><button v-else @click="$emit('unblock-friend',selectedFriend.friendId)">取消拉黑</button></div></div>
    </section>

    <!-- GROUP-CHAT-20260810 [2/5]：群组独立为右侧 Tab，资料、成员和管理操作均走
         IM Business 群组 REST API；不把群状态变更伪装成既有消息 WebSocket 二进制帧。 -->
    <section v-else-if="tab === 'groups'" class="detail-content groups-tab">
      <GroupPanel
        :groups="groups || []"
        :friends="friends"
        :current-user-id="currentUserId"
        :loading="groupsLoading"
        :active-group-id="activeGroupId"
        @create="$emit('create-group')"
        @open-group="groupId => $emit('open-group', groupId)"
        @open-private="friendId => $emit('open-friend', friendId)"
        @leave="groupId => $emit('leave-group', groupId)"
        @dissolve="groupId => $emit('dissolve-group', groupId)"
        @changed="$emit('groups-changed')"
        @toast="(message,error) => $emit('toast', message, error)"
      />
    </section>

    <section v-else-if="tab === 'files'" class="detail-content shared-files">
      <label class="panel-search"><i class="fa fa-search"></i><input v-model="fileKeyword" placeholder="搜索共享文件" /></label>
      <button v-for="message in filteredFiles" :key="message.id" class="shared-file" @click="$emit('download-file', message)">
        <i class="fa fa-file-o"></i><div><strong>{{ message.payload?.fileName || message.content }}</strong><small>{{ new Date(message.created_at).toLocaleString('zh-CN') }}</small></div><i class="fa fa-download"></i>
      </button>
      <div v-if="!filteredFiles.length" class="empty-panel">当前会话还没有共享文件</div>
    </section>

    <section v-else class="detail-content search-tab">
      <label class="panel-search"><i class="fa fa-search"></i><input ref="searchInput" v-model="messageKeyword" placeholder="搜索当前会话消息" /></label>
      <div class="search-filters"><button v-for="type in messageTypes" :key="type.key" :class="{active:messageType===type.key}" @click="messageType=type.key">{{ type.label }}</button></div>
      <div class="capability-note"><i class="fa fa-database"></i><span>IM Business 尚无全文检索端点，当前搜索范围为已加载和本地缓存的消息。</span></div>
      <button v-for="message in filteredMessages" :key="message.id" class="message-result" @click="$emit('jump-message', message.id)">
        <strong>{{ message.sender === 'me' ? '我' : message.senderName || conversation?.title }}</strong><p>{{ message.content }}</p><time>{{ new Date(message.created_at).toLocaleString('zh-CN') }}</time>
      </button>
      <div v-if="messageKeyword && !filteredMessages.length" class="empty-panel">未找到相关结果，请更换关键词</div>
    </section>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import GroupPanel from './GroupPanel.vue'
import type { MessageCenterConversation, MessageCenterFriend, MessageCenterGroup, MessageCenterMessage } from '@/stores/messageCenterStore'
type TabKey='details'|'contacts'|'groups'|'files'|'search'
const props=defineProps<{conversation:MessageCenterConversation|null;messages:MessageCenterMessage[];friends:MessageCenterFriend[];groups?:MessageCenterGroup[];groupsLoading?:boolean;currentUserId:string;activeGroupId?:string;blacklistedFriends?:MessageCenterFriend[];pendingCount?:number;contactsLoading:boolean;loading?:boolean}>()
const emit=defineEmits<{close:[];'send-file':[];'toggle-muted':[];'toggle-pinned':[];'open-friend':[friendId:string];'open-group':[groupId:string];'leave-group':[groupId:string];'dissolve-group':[groupId:string];'groups-changed':[];'create-group':[];toast:[message:string,error?:boolean];'jump-message':[messageId:string];'download-file':[message:MessageCenterMessage];'add-friend':[];'friend-requests':[];'remark-friend':[friendId:string,remark:string];'star-friend':[friendId:string,starred:boolean];'remove-friend':[friendId:string];'block-friend':[friendId:string];'unblock-friend':[friendId:string]}>()
const tab=defineModel<TabKey>('tab',{default:'details'})
const tabs=[{key:'details' as const,label:'详情',icon:'fa fa-info-circle'},{key:'contacts' as const,label:'联系人',icon:'fa fa-address-book-o'},{key:'groups' as const,label:'群组',icon:'fa fa-users'},{key:'files' as const,label:'文件',icon:'fa fa-folder-o'},{key:'search' as const,label:'搜索',icon:'fa fa-search'}]
const contactKeyword=ref(''),contactGroup=ref<'all'|'starred'|'blacklist'>('all'),selectedFriend=ref<MessageCenterFriend|null>(null),remarkDraft=ref(''),fileKeyword=ref(''),messageKeyword=ref(''),messageType=ref('all'),searchInput=ref<HTMLInputElement|null>(null)
const messageTypes=[{key:'all',label:'全部'},{key:'text',label:'文本'},{key:'image',label:'图片'},{key:'file',label:'文件'}]
const filteredFriends=computed(()=>{const query=contactKeyword.value.trim().toLowerCase();const source=contactGroup.value==='blacklist'?(props.blacklistedFriends||[]):contactGroup.value==='starred'?props.friends.filter(item=>item.starred):props.friends;return source.filter(friend=>!query||`${friendLabel(friend)} ${friend.account||''} ${friend.remark||''} ${friend.friendId}`.toLowerCase().includes(query)).sort((a,b)=>Number(Boolean(b.starred))-Number(Boolean(a.starred))||friendLabel(a).localeCompare(friendLabel(b),'zh-CN'))})
function friendLabel(friend:MessageCenterFriend){return friend.remark||friend.username||friend.account||friend.friendId}
function saveRemark(){if(!selectedFriend.value)return;emit('remark-friend',selectedFriend.value.friendId,remarkDraft.value.trim())}
const filteredFiles=computed(()=>props.messages.filter(m=>['file','image','video'].includes(m.type)&&(!fileKeyword.value||m.content.toLowerCase().includes(fileKeyword.value.toLowerCase()))).sort((a,b)=>b.created_at-a.created_at))
const filteredMessages=computed(()=>{const q=messageKeyword.value.trim().toLowerCase();if(!q)return[];return props.messages.filter(m=>(messageType.value==='all'||m.type===messageType.value)&&m.content.toLowerCase().includes(q)).slice().reverse()})
watch(tab,value=>{if(value==='search')nextTick(()=>searchInput.value?.focus())})
watch(selectedFriend,value=>{remarkDraft.value=value?.remark||''})
</script>

<style scoped>
.detail-panel{height:100%;min-width:0;display:grid;grid-template-rows:58px minmax(0,1fr);background:var(--im-panel);border-left:1px solid var(--im-border);overflow:hidden}.detail-panel>header{display:flex;align-items:center;border-bottom:1px solid var(--im-border)}.detail-panel nav{min-width:0;flex:1;display:flex;height:100%}.detail-panel nav button{min-width:0;flex:1;display:flex;align-items:center;justify-content:center;gap:5px;border-bottom:2px solid transparent;color:var(--im-muted);font-size:12px}.detail-panel nav button.active{border-color:var(--im-accent);color:var(--im-accent)}.close-panel{width:38px;height:38px;margin-right:5px;border-radius:9px;color:var(--im-muted)}.detail-content{min-height:0;overflow:auto;padding:18px}.profile-card{display:grid;justify-items:center;text-align:center}.large-avatar{position:relative;width:76px;height:76px;border-radius:24px;display:grid;place-items:center;overflow:hidden;background:linear-gradient(135deg,var(--im-accent),#8b5cf6);color:#fff;font-size:26px}.large-avatar img{width:100%;height:100%;object-fit:cover}.profile-card h3{margin-top:10px;font-size:18px}.profile-card p{color:var(--im-muted);font-size:12px}.profile-status{margin-top:5px;padding:3px 8px;border-radius:10px;background:var(--im-system);color:#22a06b;font-size:10px}.quick-actions{display:grid;grid-template-columns:repeat(4,1fr);gap:6px;margin:18px 0}.quick-actions button{height:62px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:5px;border-radius:11px;background:var(--im-input);color:var(--im-text);font-size:11px}.quick-actions button:disabled{opacity:.45}.settings-list{border-top:1px solid var(--im-border)}.settings-list button{width:100%;height:46px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--im-border);color:var(--im-text);font-size:13px}.settings-list i.active{color:var(--im-accent)}.settings-list em{color:var(--im-muted);font-size:10px}.settings-list .danger{color:#d33b43}.group-section{margin-top:18px}.group-section h4{margin-top:12px}.group-section p{color:var(--im-muted);font-size:12px}.panel-search{height:38px;padding:0 9px;display:flex;align-items:center;gap:7px;border-radius:10px;background:var(--im-input)}.panel-search input{min-width:0;flex:1;background:transparent;color:var(--im-text);outline:0}.capability-note{margin:10px 0;padding:9px;display:flex;gap:7px;border-radius:10px;background:var(--im-accent-soft);color:var(--im-muted);font-size:11px;line-height:1.45}.contact-toolbar{display:flex;gap:6px}.contact-toolbar .panel-search{flex:1}.contact-toolbar>button{width:38px;border-radius:9px;background:var(--im-accent-soft);color:var(--im-accent)}.contact-nav{display:flex;gap:4px;margin:9px 0;overflow:auto}.contact-nav button{padding:5px 7px;border-radius:7px;color:var(--im-muted);font-size:11px;white-space:nowrap}.contact-nav button.active{background:var(--im-selected);color:var(--im-accent)}.contact-nav .request-link{margin-left:auto}.contact-nav b{padding:1px 4px;border-radius:7px;background:#ef4444;color:#fff;font-size:9px}.contact-stat{margin:7px 0;color:var(--im-muted);font-size:10px}.contact-row,.shared-file,.message-result,.unsupported-action{width:100%;min-height:58px;padding:8px;display:flex;align-items:center;gap:9px;border-bottom:1px solid var(--im-border);color:var(--im-text);text-align:left}.friend-avatar{position:relative;width:38px;height:38px;display:grid;place-items:center;overflow:visible!important;border-radius:50%;background:var(--im-avatar)}.friend-avatar img{width:100%;height:100%;border-radius:50%;object-fit:cover}.friend-avatar i{position:absolute;right:0;bottom:0;width:8px;height:8px;border:2px solid var(--im-panel);border-radius:50%;background:#22a06b}.contact-row>div,.shared-file>div{min-width:0;flex:1;display:flex;flex-direction:column}.contact-row strong{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.contact-row strong .star{color:#eab308;font-size:10px}.contact-row small,.contact-row em,.shared-file small{color:var(--im-muted);font-size:10px;font-style:normal}.contact-detail{position:absolute;inset:58px 0 0;z-index:3;padding:20px;background:var(--im-panel);text-align:center;overflow:auto}.detail-back{display:block;color:var(--im-muted);font-size:12px}.detail-avatar{display:grid;place-items:center;width:70px;height:70px;margin:20px auto 8px;overflow:hidden;border-radius:22px;background:var(--im-avatar);font-size:24px}.detail-avatar img{width:100%;height:100%;object-fit:cover}.contact-detail p,.contact-detail small{color:var(--im-muted);font-size:12px}.remark{display:flex;align-items:center;gap:6px;margin-top:20px;text-align:left;font-size:12px}.remark input{min-width:0;flex:1;padding:6px;border-radius:7px;background:var(--im-input);color:var(--im-text);outline:0}.remark button,.friend-actions button{padding:7px 9px;border-radius:8px;background:var(--im-input);font-size:12px}.friend-actions{display:grid;gap:7px;margin-top:15px}.friend-actions .primary{background:var(--im-accent);color:#fff}.friend-actions .danger{color:#d33b43}.unsupported-action{margin-top:9px;border:1px dashed var(--im-border);border-radius:10px}.unsupported-action em{margin-left:auto;color:var(--im-muted);font-size:9px}.search-filters{display:flex;gap:5px;margin:8px 0}.search-filters button{padding:5px 8px;border-radius:8px;background:var(--im-input);font-size:11px}.search-filters button.active{background:var(--im-selected);color:var(--im-accent)}.message-result{display:block}.message-result p{margin:3px 0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.message-result time{color:var(--im-muted);font-size:10px}.empty-panel{padding:30px 10px;text-align:center;color:var(--im-muted);font-size:12px}.inline-loading{padding:14px;text-align:center;color:var(--im-muted)}.detail-skeleton{display:grid;justify-items:center;align-content:start;gap:13px;padding:30px}.detail-skeleton i{width:76px;height:76px;border-radius:24px;background:var(--im-skeleton)}.detail-skeleton span{width:75%;height:16px;border-radius:8px;background:var(--im-skeleton)}
.contacts-tab,.groups-tab{position:relative;padding:0}@media(max-width:1180px){.detail-panel nav button span{display:none}}@media(max-width:767px){.detail-panel{border-left:0}.detail-panel nav button span{display:inline}}
/* AUDIT FIX [4.1/4.4] / IM-MESSAGE-CENTER-20260811：联系人子导航原先 gap/margin
   过小且 section padding 被覆盖为 0，导致桌面端内容贴边、Tab 过于拥挤。新增统一
   8px 网格间距；联系人详情覆盖层从内容区顶部开始，避免重复扣除 58px。 */
.contacts-tab{position:relative;padding:16px;overflow:auto}
.groups-tab{position:relative;padding:0;overflow:hidden}
.contacts-tab .contact-toolbar{gap:10px}
.contacts-tab .contact-toolbar>button{width:40px;flex:0 0 40px;border-radius:10px}
.contacts-tab .contact-nav{gap:8px;margin:14px 0 12px;padding:4px;border-radius:12px;background:var(--im-input)}
.contacts-tab .contact-nav button{padding:8px 11px;border-radius:9px}
.contacts-tab .contact-nav button.active{background:var(--im-panel);box-shadow:0 1px 4px rgba(31,41,55,.10)}
.contacts-tab .contact-stat{margin:0 0 10px;padding-inline:2px}
.contacts-tab .contact-row{margin:4px 0;border-bottom:0;border-radius:10px;background:color-mix(in srgb,var(--im-panel) 86%,var(--im-input))}
.contacts-tab .contact-detail{inset:0}
</style>
