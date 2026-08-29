<template>
  <div class="teamwork-page min-h-screen bg-neutral-50">
    <!-- [REQ-TEAMWORK-5.1] 独立协作工作区保留明确的控制台返回路径，避免浏览器返回导致用户迷失。 -->
    <header class="border-b border-neutral-200 bg-white px-4 py-3 sm:px-6 lg:px-8">
      <div class="mx-auto flex max-w-6xl flex-wrap items-center gap-2">
        <router-link class="inline-flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-white" to="/app" aria-label="返回控制面板"><i class="fa fa-cube"></i></router-link>
        <nav class="order-3 flex w-full gap-1 overflow-x-auto text-sm sm:order-none sm:w-auto" aria-label="协作导航">
          <router-link class="rounded-md px-3 py-1.5 text-neutral-500 hover:bg-neutral-50 hover:text-primary" to="/app"><i class="fa fa-arrow-left mr-1.5"></i>我的网盘</router-link>
          <router-link class="rounded-md px-3 py-1.5 text-neutral-500 hover:bg-neutral-50 hover:text-primary" to="/explore">公开仓库</router-link>
          <router-link class="rounded-md bg-primary/10 px-3 py-1.5 font-semibold text-primary" to="/teamwork">团队协作</router-link>
        </nav>
        <router-link class="ml-auto inline-flex min-h-9 items-center gap-2 rounded-lg bg-primary px-3 text-sm font-semibold text-white hover:bg-primary/90" to="/space/manage?create=1"><i class="fa fa-plus"></i>创建团队空间</router-link>
      </div>
    </header>
    <main class="px-4 py-6 sm:px-6 lg:px-8">
    <div class="mx-auto max-w-6xl">
      <header class="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p class="text-xs font-semibold uppercase tracking-[0.18em] text-primary">Collaboration</p>
          <h1 class="mt-1 text-2xl font-bold text-neutral-900">团队协作</h1>
          <p class="mt-1 text-sm text-neutral-500">发现空间、申请加入并管理你参与的协作环境。</p>
        </div>
        <button class="rounded-lg border border-neutral-200 bg-white px-4 py-2 text-sm text-neutral-600 shadow-sm hover:border-primary hover:text-primary" @click="loadAll">
          <i class="fa fa-refresh mr-1" :class="loading ? 'fa-spin' : ''"></i>刷新
        </button>
      </header>

      <nav class="mb-5 flex gap-1 overflow-x-auto rounded-xl border bg-white p-1 shadow-sm" aria-label="团队协作分区">
        <button v-for="tab in tabs" :key="tab.key" class="whitespace-nowrap rounded-lg px-4 py-2 text-sm transition" :class="activeTab === tab.key ? 'bg-primary text-white shadow-sm' : 'text-neutral-500 hover:bg-neutral-50'" @click="activeTab = tab.key">
          {{ tab.label }} <span v-if="tab.key === 'requests' && pendingCount" class="ml-1 rounded-full bg-red-100 px-1.5 py-0.5 text-[10px] text-red-600">{{ pendingCount }}</span>
        </button>
      </nav>

      <section v-if="activeTab === 'discover'" class="space-y-4">
        <div class="rounded-xl border bg-white p-4 shadow-sm">
          <label class="relative block">
            <i class="fa fa-search absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400"></i>
            <input v-model.trim="keyword" class="w-full rounded-lg border border-neutral-200 py-2.5 pl-9 pr-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/15" placeholder="搜索空间名称或描述" @keyup.enter="search" />
          </label>
          <div class="mt-3 flex flex-wrap gap-2">
            <button v-for="filter in typeFilters" :key="filter.value" type="button" class="rounded-full border px-3 py-1 text-xs transition" :class="spaceTypeFilter === filter.value ? 'border-primary bg-primary text-white' : 'border-neutral-200 bg-white text-neutral-500 hover:border-primary hover:text-primary'" @click="spaceTypeFilter = filter.value; syncRoute()">{{ filter.label }}</button>
            <span class="ml-auto self-center text-xs text-neutral-400">{{ filteredDiscovered.length }} 个可发现空间</span>
          </div>
        </div>
        <div v-if="loading" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"><div v-for="i in 3" :key="i" class="h-40 animate-pulse rounded-xl bg-neutral-200"></div></div>
        <div v-else-if="!filteredDiscovered.length" class="rounded-xl border bg-white py-16 text-center text-sm text-neutral-400">没有找到符合筛选条件的可加入空间</div>
        <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <article v-for="space in filteredDiscovered" :key="space.spaceId" class="group rounded-xl border bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-md">
            <div class="flex items-start gap-3">
              <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary"><i class="fa fa-users text-lg"></i></div>
              <div class="min-w-0 flex-1"><h2 class="truncate font-semibold text-neutral-800">{{ space.spaceName }}</h2><span class="mt-1 inline-flex rounded-full bg-blue-50 px-2 py-0.5 text-[10px] text-blue-600">{{ typeLabel(space.spaceType) }}</span></div>
            </div>
            <p class="mt-4 line-clamp-2 min-h-10 text-sm text-neutral-500">{{ space.spaceDescription || '暂无空间描述' }}</p>
            <div class="mt-4 flex items-center justify-between text-xs text-neutral-400"><span><i class="fa fa-users mr-1"></i>{{ space.memberCount ?? '—' }} 位成员</span><span><i class="fa fa-shield mr-1"></i>{{ policyLabel(space.joinPolicy) }}</span></div>
            <div class="mt-4 flex items-center justify-between border-t border-neutral-100 pt-3"><span class="text-xs text-neutral-400">更新于 {{ formatDate(space.spaceUpdatedAt) }}</span><button class="text-sm font-medium text-primary hover:underline" @click="openPreview(space.spaceId)">{{ space.isMember ? '进入详情' : space.joinPolicy === 'open' ? '立即加入' : '申请加入' }}</button></div>
          </article>
        </div>
      </section>

      <section v-else-if="activeTab === 'spaces'" class="space-y-4">
        <div v-if="loading" class="h-32 animate-pulse rounded-xl bg-neutral-200"></div>
        <div v-else-if="!mySpaces.length" class="rounded-xl border bg-white py-16 text-center text-sm text-neutral-400">暂无已加入空间</div>
        <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <article v-for="space in mySpaces" :key="space.spaceId" class="rounded-xl border bg-white p-5 shadow-sm">
            <div class="flex items-center gap-3"><div class="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary"><i class="fa fa-cube"></i></div><div class="min-w-0 flex-1"><h2 class="truncate font-semibold text-neutral-800">{{ space.spaceName }}</h2><p class="text-xs text-neutral-400">{{ typeLabel(space.spaceType) }} · {{ roleFor(space) }}</p></div></div>
            <p class="mt-3 line-clamp-2 text-sm text-neutral-500">{{ space.spaceDescription || '暂无空间描述' }}</p>
            <div class="mt-4 flex gap-2"><button class="flex-1 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-white hover:bg-primary/90" @click="enter(space)">进入空间</button><button v-if="space.spaceType !== 'personal'" class="rounded-lg border px-3 py-2 text-sm text-red-500 hover:bg-red-50" @click="leave(space)">退出</button></div>
          </article>
        </div>
      </section>

      <section v-else class="rounded-xl border bg-white shadow-sm">
        <div v-if="!requests.length" class="py-16 text-center text-sm text-neutral-400">暂无申请记录</div>
        <div v-else class="divide-y"><div v-for="request in requests" :key="request.requestId" class="flex flex-wrap items-center justify-between gap-3 px-5 py-4"><div><p class="font-medium text-neutral-800">空间 {{ request.spaceId }}</p><p class="mt-1 text-xs text-neutral-500">{{ request.requestMessage || '未填写申请说明' }} · {{ request.createdAt }}</p></div><div class="flex items-center gap-3"><span class="rounded-full px-2.5 py-1 text-xs" :class="statusClass(request.status)">{{ statusLabel(request.status) }}</span><button v-if="request.status === 'pending'" class="text-xs text-red-500 hover:underline" @click="cancelRequest(request.requestId)">取消申请</button></div></div></div>
      </section>
    </div>
  </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSpaceStore } from '@/stores/spaceStore'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { cancelJoinRequestApi, joinCollaborationSpaceApi, listMyCollaborationSpacesApi, listMyJoinRequestsApi, searchCollaborationSpacesApi, type SpaceInfo, type SpaceJoinRequest, type SpacePreview } from '@/api/modules/space'

const router = useRouter()
const route = useRoute()
const spaceStore = useSpaceStore()
const authStore = useAuthStore()
const toast = useToastStore()
const activeTab = ref<'discover' | 'spaces' | 'requests'>('discover')
const keyword = ref('')
const spaceTypeFilter = ref<'all' | 'team' | 'enterprise'>('all')
const loading = ref(false)
const discovered = ref<SpacePreview[]>([])
const mySpaces = ref<SpaceInfo[]>([])
const requests = ref<SpaceJoinRequest[]>([])
const tabs = [{ key: 'discover', label: '发现空间' }, { key: 'spaces', label: '我的空间' }, { key: 'requests', label: '我的申请' }] as const
const pendingCount = computed(() => requests.value.filter((item) => item.status === 'pending').length)
const typeFilters = [{ label: '全部', value: 'all' as const }, { label: '团队空间', value: 'team' as const }, { label: '企业空间', value: 'enterprise' as const }]
const filteredDiscovered = computed(() => discovered.value.filter((space) => spaceTypeFilter.value === 'all' || space.spaceType === spaceTypeFilter.value))

async function search() { loading.value = true; try { const res = await searchCollaborationSpacesApi(keyword.value); discovered.value = res.code === 200 ? (res.data || []) : [] } catch { toast.showToast('空间搜索失败，请稍后重试', 'error') } finally { loading.value = false } }
async function loadAll() { loading.value = true; try { const [spaces, reqs] = await Promise.all([listMyCollaborationSpacesApi(), listMyJoinRequestsApi()]); mySpaces.value = spaces.data || []; requests.value = reqs.data || []; await search() } catch { toast.showToast('协作数据加载失败，请稍后重试', 'error') } finally { loading.value = false } }
function openPreview(spaceId: string) { router.push({ name: 'TeamworkSpacePreview', params: { spaceId } }) }
async function enter(space: SpaceInfo) {
  /* [REQ-TEAMWORK-5.12] 协作空间仍复用控制台文件工作区；若未来列表包含 public 类型，统一转到资源仓库入口。 */
  if (space.spaceType === 'public') { await router.push(`/repo/${encodeURIComponent(space.spaceId)}`); return }
  await spaceStore.refreshSpaces(); if (await spaceStore.switchSpace(space.spaceId)) router.push({ path: '/app', query: { space_id: space.spaceId } })
}
async function leave(space: SpaceInfo) { if (!window.confirm(`确定退出「${space.spaceName}」吗？`)) return; try { const { removeMemberApi } = await import('@/api/modules/space'); const userId = String(authStore.user?.id || ''); await removeMemberApi(space.spaceId, userId); mySpaces.value = mySpaces.value.filter((item) => item.spaceId !== space.spaceId); toast.showToast('已退出空间', 'success') } catch { toast.showToast('退出失败，请确认你具有权限', 'error') } }
async function cancelRequest(id: number) { try { await cancelJoinRequestApi(id); requests.value = requests.value.filter((item) => item.requestId !== id); toast.showToast('申请已取消', 'success') } catch { toast.showToast('取消申请失败', 'error') } }
function roleFor(space: SpaceInfo) { return String(space.spaceOwnerId) === String(authStore.user?.id) ? '所有者' : '已加入' }
function typeLabel(type: string) { return ({ enterprise: '企业空间', team: '团队空间', private: '私有空间', personal: '我的网盘' } as Record<string, string>)[type] || '协作空间' }
function policyLabel(policy?: string) { return ({ open: '开放加入', approval_required: '需要审批', invite_only: '仅限邀请' } as Record<string, string>)[policy || 'approval_required'] || '需要审批' }
function statusLabel(status: string) { return ({ pending: '待审批', approved: '已通过', rejected: '已拒绝' } as Record<string, string>)[status] || status }
function statusClass(status: string) { return status === 'approved' ? 'bg-green-50 text-green-600' : status === 'rejected' ? 'bg-red-50 text-red-500' : 'bg-amber-50 text-amber-600' }
function formatDate(value?: string) { return value ? new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric' }).format(new Date(value)) : '—' }
function syncRoute() { void router.replace({ query: { ...(activeTab.value !== 'discover' ? { tab: activeTab.value } : {}), ...(keyword.value ? { q: keyword.value } : {}), ...(spaceTypeFilter.value !== 'all' ? { type: spaceTypeFilter.value } : {}) } }) }
watch(activeTab, syncRoute)
watch(() => route.query, () => { activeTab.value = ['spaces', 'requests'].includes(String(route.query.tab)) ? route.query.tab as typeof activeTab.value : 'discover'; keyword.value = typeof route.query.q === 'string' ? route.query.q : keyword.value; spaceTypeFilter.value = ['team', 'enterprise'].includes(String(route.query.type)) ? route.query.type as typeof spaceTypeFilter.value : 'all' }, { immediate: true, deep: true })
onMounted(loadAll)
</script>
