<template>
  <main class="min-h-screen bg-neutral-50 px-4 py-6 sm:px-6 lg:px-8">
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
        </div>
        <div v-if="loading" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"><div v-for="i in 3" :key="i" class="h-40 animate-pulse rounded-xl bg-neutral-200"></div></div>
        <div v-else-if="!discovered.length" class="rounded-xl border bg-white py-16 text-center text-sm text-neutral-400">没有找到可加入的空间</div>
        <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <article v-for="space in discovered" :key="space.spaceId" class="group rounded-xl border bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-md">
            <div class="flex items-start gap-3">
              <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary"><i class="fa fa-users text-lg"></i></div>
              <div class="min-w-0 flex-1"><h2 class="truncate font-semibold text-neutral-800">{{ space.spaceName }}</h2><span class="mt-1 inline-flex rounded-full bg-blue-50 px-2 py-0.5 text-[10px] text-blue-600">{{ typeLabel(space.spaceType) }}</span></div>
            </div>
            <p class="mt-4 line-clamp-2 min-h-10 text-sm text-neutral-500">{{ space.spaceDescription || '暂无空间描述' }}</p>
            <div class="mt-4 flex items-center justify-between text-xs text-neutral-400"><span><i class="fa fa-shield mr-1"></i>{{ policyLabel(space.joinPolicy) }}</span><button class="text-primary hover:underline" @click="openPreview(space.spaceId)">查看详情</button></div>
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
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSpaceStore } from '@/stores/spaceStore'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { cancelJoinRequestApi, joinCollaborationSpaceApi, listMyCollaborationSpacesApi, listMyJoinRequestsApi, searchCollaborationSpacesApi, type SpaceInfo, type SpaceJoinRequest, type SpacePreview } from '@/api/modules/space'

const router = useRouter()
const spaceStore = useSpaceStore()
const authStore = useAuthStore()
const toast = useToastStore()
const activeTab = ref<'discover' | 'spaces' | 'requests'>('discover')
const keyword = ref('')
const loading = ref(false)
const discovered = ref<SpacePreview[]>([])
const mySpaces = ref<SpaceInfo[]>([])
const requests = ref<SpaceJoinRequest[]>([])
const tabs = [{ key: 'discover', label: '发现空间' }, { key: 'spaces', label: '我的空间' }, { key: 'requests', label: '我的申请' }] as const
const pendingCount = computed(() => requests.value.filter((item) => item.status === 'pending').length)

async function search() { loading.value = true; try { const res = await searchCollaborationSpacesApi(keyword.value); discovered.value = res.code === 200 ? (res.data || []) : [] } catch { toast.showToast('空间搜索失败，请稍后重试', 'error') } finally { loading.value = false } }
async function loadAll() { loading.value = true; try { const [spaces, reqs] = await Promise.all([listMyCollaborationSpacesApi(), listMyJoinRequestsApi()]); mySpaces.value = spaces.data || []; requests.value = reqs.data || []; await search() } catch { toast.showToast('协作数据加载失败，请稍后重试', 'error') } finally { loading.value = false } }
function openPreview(spaceId: string) { router.push({ name: 'TeamworkSpacePreview', params: { spaceId } }) }
async function enter(space: SpaceInfo) { await spaceStore.refreshSpaces(); if (await spaceStore.switchSpace(space.spaceId)) router.push({ path: '/app', query: { space_id: space.spaceId } }) }
async function leave(space: SpaceInfo) { if (!window.confirm(`确定退出「${space.spaceName}」吗？`)) return; try { const { removeMemberApi } = await import('@/api/modules/space'); const userId = String(authStore.user?.id || ''); await removeMemberApi(space.spaceId, userId); mySpaces.value = mySpaces.value.filter((item) => item.spaceId !== space.spaceId); toast.showToast('已退出空间', 'success') } catch { toast.showToast('退出失败，请确认你具有权限', 'error') } }
async function cancelRequest(id: number) { try { await cancelJoinRequestApi(id); requests.value = requests.value.filter((item) => item.requestId !== id); toast.showToast('申请已取消', 'success') } catch { toast.showToast('取消申请失败', 'error') } }
function roleFor(space: SpaceInfo) { return String(space.spaceOwnerId) === String(authStore.user?.id) ? '所有者' : '已加入' }
function typeLabel(type: string) { return ({ enterprise: '企业空间', team: '团队空间', private: '私有空间', personal: '我的网盘' } as Record<string, string>)[type] || '协作空间' }
function policyLabel(policy?: string) { return ({ open: '开放加入', approval_required: '需要审批', invite_only: '仅限邀请' } as Record<string, string>)[policy || 'approval_required'] || '需要审批' }
function statusLabel(status: string) { return ({ pending: '待审批', approved: '已通过', rejected: '已拒绝' } as Record<string, string>)[status] || status }
function statusClass(status: string) { return status === 'approved' ? 'bg-green-50 text-green-600' : status === 'rejected' ? 'bg-red-50 text-red-500' : 'bg-amber-50 text-amber-600' }
onMounted(loadAll)
</script>
