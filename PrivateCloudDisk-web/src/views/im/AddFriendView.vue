<template>
  <main class="friend-page">
    <header class="page-header"><button class="back" @click="goBack"><i class="fa fa-angle-left" /> 返回消息中心</button><nav><RouterLink class="active" to="/im/add-friend">添加好友</RouterLink><RouterLink to="/im/friend-requests">好友申请<span v-if="store.pendingFriendRequestCount" class="badge">{{ store.pendingFriendRequestCount > 99 ? '99+' : store.pendingFriendRequestCount }}</span></RouterLink></nav></header>
    <section class="search-card">
      <h1>添加好友</h1><p>搜索用户账号、用户名或邮箱。邮箱只用于服务端匹配，不会在结果中展示。</p>
      <label class="search-box"><i class="fa fa-search"/><input ref="searchInput" v-model="keyword" autocomplete="off" placeholder="搜索用户账号、用户名或邮箱" @keydown.down.prevent="moveFocus(1)" @keydown.up.prevent="moveFocus(-1)" @keydown.enter.prevent="openFocused"/><i v-if="searching" class="fa fa-circle-o-notch fa-spin"/></label>
      <div v-if="!keyword && histories.length" class="history"><span>最近搜索</span><button v-for="item in histories" :key="item" @click="keyword=item">{{ item }}</button><button class="clear" @click="clearHistory">清除</button></div>
    </section>
    <section class="results" @scroll.passive="onScroll">
      <div v-if="searching && !results.length" class="state"><i class="fa fa-circle-o-notch fa-spin"/> 正在搜索</div>
      <template v-else-if="results.length"><article v-for="(user,index) in results" :key="user.userId" :class="{focused:index===focusedIndex}" tabindex="0" @click="openRequest(user)"><div class="avatar"><img v-if="user.avatarPath" :src="user.avatarPath" alt=""/><span v-else>{{ displayName(user).slice(0,1) }}</span></div><div class="user-copy"><strong>{{ displayName(user) }}</strong><span>账号：{{ user.account || '未设置' }}</span><small>{{ relationshipText(user.relationshipStatus) }}</small></div><button :disabled="user.relationshipStatus!=='NONE'" @click.stop="openRequest(user)">{{ actionText(user.relationshipStatus) }}</button></article><div v-if="loadingMore" class="state"><i class="fa fa-circle-o-notch fa-spin"/> 加载更多</div></template>
      <div v-else-if="keyword && !searching" class="state"><i class="fa fa-user-o"/><b>未找到相关用户</b><span>请检查账号、用户名或邮箱是否正确。</span></div>
      <div v-else class="recommend"><i class="fa fa-user-plus"/><b>从这里开始建立联系</b><span>输入关键词查找平台用户。</span><div><button disabled title="扫码能力预留">扫一扫（即将接入）</button><button disabled title="个人名片能力预留">通过名片添加（即将接入）</button></div></div>
    </section>
    <dialog ref="requestDialog" class="request-dialog" @close="requesting=false"><form method="dialog" @submit.prevent="sendRequest"><header><strong>发送好友申请</strong><button aria-label="关闭" @click="closeDialog"><i class="fa fa-times"/></button></header><p>发送给 {{ target ? displayName(target) : '' }}</p><textarea v-model="message" maxlength="50" placeholder="我是…"/><small>{{ message.length }}/50</small><footer><button type="button" @click="closeDialog">取消</button><button class="primary" :disabled="requesting" type="submit">{{ requesting ? '发送中…' : '发送申请' }}</button></footer></form></dialog>
    <div v-if="toast" class="toast" role="status">{{ toast }}</div>
  </main>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createFriendRequestApi, getFriendBlacklistApi, getIncomingFriendRequestsApi, getOutgoingFriendRequestsApi, listFriendsApi } from '@/api/im/friendApi'
import { searchSpaceUsersApi } from '@/api/modules/space'
import type { PublicUserSearchResult } from '@/api/im/types'
import { useAuthStore } from '@/stores/authStore'
import { useMessageCenterStore } from '@/stores/messageCenterStore'

const route=useRoute(),router=useRouter(),auth=useAuthStore(),store=useMessageCenterStore()
const key='pcd-im-friend-search-history-v1',searchInput=ref<HTMLInputElement|null>(null),requestDialog=ref<HTMLDialogElement|null>(null)
const keyword=ref(String(route.query.q||'')),results=ref<PublicUserSearchResult[]>([]),page=ref(1),hasMore=ref(false),searching=ref(false),loadingMore=ref(false),focusedIndex=ref(0),target=ref<PublicUserSearchResult|null>(null),message=ref(''),requesting=ref(false),toast=ref('')
const histories=ref<string[]>(readHistory()),relationshipByUserId=ref<Record<string, PublicUserSearchResult['relationshipStatus']>>({});let timer:ReturnType<typeof setTimeout>|null=null,requestNo=0,toastTimer:ReturnType<typeof setTimeout>|null=null
onMounted(async()=>{if(!auth.user.id)await auth.fetchUserInfo();void store.refreshPendingFriendRequestCount();await refreshRelationshipStatuses();await nextTick();searchInput.value?.focus();if(keyword.value)void search()})
onBeforeUnmount(()=>{if(timer)clearTimeout(timer);if(toastTimer)clearTimeout(toastTimer)})
watch(keyword,()=>{if(timer)clearTimeout(timer);timer=setTimeout(()=>void search(),300)})
function userId(){if(!auth.user.id)throw new Error('请重新登录后添加好友');return auth.user.id}
async function refreshRelationshipStatuses(){
  const viewer=userId()
  try{
    const [friends,incoming,outgoing,blacklist]=await Promise.all([listFriendsApi(viewer),getIncomingFriendRequestsApi(viewer,1,100),getOutgoingFriendRequestsApi(viewer,1,100),getFriendBlacklistApi(viewer)])
    const next:Record<string,PublicUserSearchResult['relationshipStatus']>={}
    ;(blacklist.data||[]).forEach(item=>{next[item.friendId]='BLOCKED'})
    ;(friends.data||[]).forEach(item=>{next[item.friendId]='FRIEND'})
    ;(outgoing.data?.items||[]).filter(item=>item.status===0).forEach(item=>{next[item.recipientId]='PENDING_OUTGOING'})
    ;(incoming.data?.items||[]).filter(item=>item.status===0).forEach(item=>{next[item.requesterId]='PENDING_INCOMING'})
    relationshipByUserId.value=next
  }catch{relationshipByUserId.value={}}
}
function displayName(user:PublicUserSearchResult){return user.username||user.account||user.userId}
function relationshipText(status:PublicUserSearchResult['relationshipStatus']){return ({FRIEND:'已是好友',PENDING_OUTGOING:'等待验证',PENDING_INCOMING:'对方已申请你',BLOCKED:'当前不可申请',NONE:''} as Record<string,string>)[status]||''}
function actionText(status:PublicUserSearchResult['relationshipStatus']){return ({FRIEND:'已添加',PENDING_OUTGOING:'等待验证',PENDING_INCOMING:'去处理',BLOCKED:'不可添加',NONE:'添加好友'} as Record<string,string>)[status]}
async function search(next=false){const query=keyword.value.trim();if(!query){results.value=[];hasMore.value=false;return}const serial=++requestNo;if(next)loadingMore.value=true;else{searching.value=true;page.value=1}try{const targetPage=next?page.value+1:1;const res=await searchSpaceUsersApi(query,20,targetPage);if(serial!==requestNo)return;if(res.code!==200)throw new Error('用户目录搜索失败');const items=(res.data||[]).map(user=>({userId:user.userId,username:user.username,account:user.account,avatarPath:user.avatarPath,relationshipStatus:relationshipByUserId.value[user.userId]||'NONE'} satisfies PublicUserSearchResult));results.value=next?[...results.value,...items]:items;page.value=targetPage;hasMore.value=items.length>=20;focusedIndex.value=0;if(!next)remember(query)}catch(error){if(serial===requestNo)showToast(error instanceof Error?error.message:'搜索失败')}finally{if(serial===requestNo){searching.value=false;loadingMore.value=false}}}
function onScroll(event:Event){const node=event.currentTarget as HTMLElement;if(hasMore.value&&!loadingMore.value&&node.scrollTop+node.clientHeight>=node.scrollHeight-50)void search(true)}
function openRequest(user:PublicUserSearchResult){if(user.relationshipStatus==='PENDING_INCOMING'){void router.push('/im/friend-requests');return}if(user.relationshipStatus!=='NONE')return;target.value=user;message.value=`我是${auth.displayName||auth.user.name||''}`.slice(0,50);requestDialog.value?.showModal()}
function closeDialog(){requestDialog.value?.close()}
async function sendRequest(){if(!target.value)return;requesting.value=true;try{const res=await createFriendRequestApi(userId(),target.value.userId,message.value.trim());if(res.code!==200)throw new Error(res.message||'好友申请发送失败');target.value.relationshipStatus='PENDING_OUTGOING';showToast('好友申请已发送');closeDialog()}catch(error){showToast(error instanceof Error?error.message:'好友申请发送失败')}finally{requesting.value=false}}
function moveFocus(step:number){if(!results.value.length)return;focusedIndex.value=(focusedIndex.value+step+results.value.length)%results.value.length}
function openFocused(){const user=results.value[focusedIndex.value];if(user)openRequest(user)}
function readHistory(){try{return JSON.parse(localStorage.getItem(key)||'[]').slice(0,5)}catch{return[]}}
function remember(value:string){histories.value=[value,...histories.value.filter(item=>item!==value)].slice(0,5);localStorage.setItem(key,JSON.stringify(histories.value))}
function clearHistory(){histories.value=[];localStorage.removeItem(key)}
function goBack(){if(window.history.length>1)router.back();else void router.push('/app/notifications')}
function showToast(value:string){toast.value=value;if(toastTimer)clearTimeout(toastTimer);toastTimer=setTimeout(()=>toast.value='',3000)}
</script>

<style scoped>
.friend-page{min-height:100vh;background:var(--im-bg,#f6f7fb);color:var(--im-text,#20232a);padding:28px clamp(18px,5vw,72px)}.page-header,.search-card,.results{max-width:880px;margin:auto}.page-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:26px}.back,nav a{color:var(--im-muted,#6b7280);font-size:14px}nav{display:flex;gap:18px}nav a{position:relative;padding:8px 0}nav .active{color:var(--im-accent,#2563eb);border-bottom:2px solid var(--im-accent,#2563eb)}.badge{margin-left:4px;min-width:17px;padding:1px 4px;border-radius:9px;background:#ef4444;color:#fff;font-size:10px}.search-card{padding:34px;border-radius:20px;background:var(--im-panel,#fff);box-shadow:0 16px 45px #151b3420}.search-card h1{margin:0;font-size:26px}.search-card p{color:var(--im-muted,#6b7280);font-size:13px}.search-box{height:52px;margin-top:22px;display:flex;align-items:center;gap:11px;padding:0 16px;border-radius:14px;background:var(--im-input,#f3f4f6);color:var(--im-muted,#6b7280)}.search-box input{flex:1;min-width:0;background:transparent;color:inherit;font-size:15px;outline:0}.history{display:flex;align-items:center;flex-wrap:wrap;gap:7px;margin-top:12px;font-size:12px;color:var(--im-muted,#6b7280)}.history button{padding:4px 9px;border-radius:12px;background:var(--im-input,#f3f4f6)}.history .clear{margin-left:auto;background:none}.results{margin-top:18px;max-height:calc(100vh - 250px);overflow:auto;border-radius:16px;background:var(--im-panel,#fff)}.results article{display:flex;align-items:center;gap:12px;padding:14px 18px;border-bottom:1px solid var(--im-border,#e5e7eb);cursor:pointer}.results article.focused,.results article:hover{background:var(--im-selected,#edf4ff)}.avatar{width:44px;height:44px;display:grid;place-items:center;overflow:hidden;border-radius:50%;background:var(--im-avatar,#dbeafe);font-weight:700}.avatar img{width:100%;height:100%;object-fit:cover}.user-copy{flex:1;min-width:0;display:flex;flex-direction:column;gap:3px}.user-copy span,.user-copy small{color:var(--im-muted,#6b7280);font-size:12px}.results article>button{padding:7px 11px;border-radius:8px;background:var(--im-accent-soft,#dbeafe);color:var(--im-accent,#2563eb);font-size:12px}.results article>button:disabled{color:var(--im-muted,#6b7280);background:var(--im-input,#f3f4f6)}.state,.recommend{min-height:190px;display:grid;place-content:center;justify-items:center;gap:10px;color:var(--im-muted,#6b7280);font-size:13px}.state b,.recommend b{color:var(--im-text,#20232a)}.recommend div{display:flex;gap:8px}.recommend button{padding:8px 10px;border-radius:8px;background:var(--im-input,#f3f4f6);color:var(--im-muted,#6b7280)}.request-dialog{width:min(430px,calc(100vw - 32px));padding:0;border:0;border-radius:16px;background:var(--im-panel,#fff);color:var(--im-text,#20232a);box-shadow:0 20px 60px #11182755}.request-dialog::backdrop{background:#11182766}.request-dialog form{padding:20px}.request-dialog header,.request-dialog footer{display:flex;justify-content:space-between;align-items:center}.request-dialog textarea{box-sizing:border-box;width:100%;height:88px;margin-top:12px;padding:10px;border:1px solid var(--im-border,#e5e7eb);border-radius:10px;background:var(--im-input,#f3f4f6);color:inherit;resize:none;outline:0}.request-dialog small{display:block;text-align:right;color:var(--im-muted,#6b7280)}.request-dialog footer{margin-top:16px;justify-content:flex-end;gap:8px}.request-dialog footer button{padding:8px 13px;border-radius:8px;background:var(--im-input,#f3f4f6)}.request-dialog .primary{background:var(--im-accent,#2563eb);color:#fff}.toast{position:fixed;right:24px;bottom:24px;padding:11px 15px;border-radius:10px;background:#1f2937;color:#fff;box-shadow:0 10px 28px #11182744}@media(max-width:720px){.friend-page{padding:14px}.page-header{margin-bottom:15px}.search-card{padding:22px;border-radius:14px}.results{max-height:calc(100vh - 218px);border-radius:14px}.request-dialog{position:fixed;right:0;bottom:0;width:100%;margin:0;border-radius:18px 18px 0 0}.request-dialog::backdrop{background:#11182744}}
</style>
