<template>
  <div class="space-management-page min-h-screen bg-neutral-50">
    <!-- 顶部标题栏 -->
    <div class="border-b bg-white px-6 py-4">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-xl font-bold text-neutral-800">空间管理</h1>
          <p class="mt-0.5 text-sm text-neutral-500">管理你的所有空间，创建、编辑、邀请成员</p>
        </div>
        <button
          class="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white transition hover:bg-primary/90"
          @click="showCreateDialog = true"
        >
          <i class="fa fa-plus"></i>
          创建空间
        </button>
      </div>
    </div>

    <!-- 空间列表 -->
    <div class="p-6">
      <div class="mb-4 flex flex-col gap-3 rounded-xl border bg-white p-3 sm:flex-row sm:items-center">
        <label class="relative min-w-0 flex-1">
          <i class="fa fa-search absolute left-3 top-1/2 -translate-y-1/2 text-xs text-neutral-400"></i>
          <input
            v-model.trim="spaceKeyword"
            class="w-full rounded-lg border border-neutral-200 py-2 pl-9 pr-3 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15"
            placeholder="筛选空间名称"
          />
        </label>
        <select
          v-model="spaceSort"
          class="rounded-lg border border-neutral-200 bg-white px-3 py-2 text-sm text-neutral-600 outline-none focus:border-primary"
          aria-label="空间配额排序"
        >
          <option value="personal-first">默认空间优先</option>
          <option value="usage-desc">使用率从高到低</option>
          <option value="name">按名称排序</option>
        </select>
        <span v-if="quotaLoading" class="text-xs text-neutral-400">
          <i class="fa fa-spinner fa-spin mr-1"></i>正在同步配额
        </span>
      </div>

      <div v-if="spaceStore.loading" class="flex items-center justify-center py-20">
        <i class="fa fa-spinner fa-spin text-2xl text-primary"></i>
      </div>

      <div v-else-if="spaceStore.spaces.length === 0" class="flex flex-col items-center justify-center py-20 text-neutral-400">
        <i class="fa fa-cloud text-5xl"></i>
        <p class="mt-4 text-sm">“我的网盘”空间正在初始化</p>
        <button
          class="mt-3 rounded-lg border border-neutral-200 bg-white px-4 py-2 text-sm text-primary transition hover:border-primary"
          @click="reloadSpaces"
        >
          <i class="fa fa-refresh mr-1"></i>重新加载
        </button>
      </div>

      <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        <div
          v-for="space in displayedSpaces"
          :key="space.spaceId"
          class="group cursor-pointer rounded-xl border bg-white p-5 shadow-sm transition hover:shadow-md"
          :class="space.spaceId === spaceStore.currentSpaceId ? 'ring-2 ring-primary' : ''"
          @click="selectSpace(space.spaceId)"
        >
          <!-- 空间头部 -->
          <div class="mb-3 flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
              <i :class="getSpaceIcon(space.spaceType)" class="text-lg text-primary"></i>
            </div>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-semibold text-neutral-800">{{ space.spaceName }}</p>
              <span class="inline-block rounded-full bg-neutral-100 px-2 py-0.5 text-[10px] font-medium text-neutral-500">
                {{ getSpaceTypeLabel(space.spaceType) }}
              </span>
            </div>
            <button
              v-if="space.spaceType !== 'personal' && space.spaceOwnerId === currentUserId"
              class="hidden rounded-lg p-1.5 text-neutral-400 transition hover:bg-red-50 hover:text-red-500 group-hover:block"
              title="删除空间"
              @click.stop="confirmDelete(space)"
            >
              <i class="fa fa-trash text-xs"></i>
            </button>
          </div>

          <!-- 空间描述 -->
          <p v-if="space.spaceDescription" class="mb-3 line-clamp-2 text-xs text-neutral-500">
            {{ space.spaceDescription }}
          </p>

          <!-- 空间统计 -->
          <div class="space-y-1.5 text-xs text-neutral-400">
            <div class="flex items-center justify-between">
              <span>已用容量</span>
              <span class="font-medium text-neutral-600">
                {{ formatSize(spaceUsage(space).used_quota) }} / {{ formatSize(spaceUsage(space).total_quota) }}
              </span>
            </div>
            <div class="flex items-center justify-between">
              <span>文件数量</span>
              <span class="font-medium text-neutral-600">{{ spaceUsage(space).file_count }} 个</span>
            </div>
          </div>

          <!-- 进度条 -->
          <div class="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-neutral-100">
            <div
              class="h-full rounded-full transition-all"
              :class="getQuotaBarColor(space)"
              :style="{ width: getQuotaPercent(space) + '%' }"
            ></div>
          </div>

          <!-- 底部操作 -->
          <div class="mt-3 flex items-center gap-2 border-t pt-3">
            <button
              class="flex-1 rounded-lg px-2 py-1.5 text-xs font-medium text-primary transition hover:bg-primary/5"
              @click.stop="enterSpace(space.spaceId)"
            >
              进入空间
            </button>
            <button
              class="rounded-lg px-2 py-1.5 text-xs text-neutral-500 transition hover:bg-neutral-100"
              @click.stop="openManage(space)"
            >
              <i class="fa fa-cog"></i>
            </button>
            <button
              v-if="space.spaceType !== 'personal' && space.spaceType !== 'public'"
              class="rounded-lg px-2 py-1.5 text-xs text-neutral-500 transition hover:bg-neutral-100"
              title="成员管理"
              @click.stop="router.push(`/space/${space.spaceId}/members`)"
            >
              <i class="fa fa-users"></i>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建空间对话框 -->
    <CreateSpaceDialog
      v-model:visible="showCreateDialog"
      @created="onSpaceCreated"
    />

    <!-- 管理对话框 -->
    <SpaceManageDialog
      v-if="managingSpace"
      :visible="!!managingSpace"
      :space="managingSpace"
      @close="managingSpace = null"
      @refresh="spaceStore.refreshSpaces"
    />

    <!-- 删除确认 -->
    <Teleport to="body">
      <div
        v-if="deletingSpace"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
        @click.self="deletingSpace = null"
      >
        <div class="w-80 rounded-xl bg-white p-6 shadow-2xl">
          <h3 class="text-lg font-semibold text-neutral-800">确认删除</h3>
          <p class="mt-2 text-sm text-neutral-500">
            确定要删除空间「{{ deletingSpace.spaceName }}」吗？此操作不可撤销。
          </p>
          <div class="mt-4 flex justify-end gap-3">
            <button
              class="rounded-lg px-4 py-2 text-sm text-neutral-600 hover:bg-neutral-100"
              @click="deletingSpace = null"
            >
              取消
            </button>
            <button
              class="rounded-lg bg-red-500 px-4 py-2 text-sm font-medium text-white hover:bg-red-600"
              @click="doDelete"
            >
              删除
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useSpaceStore } from '@/stores/spaceStore'
import { useAuthStore } from '@/stores/authStore'
import { deleteSpaceApi } from '@/api/modules/space'
import type { SpaceInfo } from '@/api/modules/space'
import { getAllSpaceQuotasApi, type SpaceQuotaInfo } from '@/api/modules/quotas'
import CreateSpaceDialog from '@/components/space/CreateSpaceDialog.vue'
import SpaceManageDialog from '@/components/space/SpaceManageDialog.vue'

const router = useRouter()
const spaceStore = useSpaceStore()
const authStore = useAuthStore()

const showCreateDialog = ref(false)
const managingSpace = ref<SpaceInfo | null>(null)
const deletingSpace = ref<SpaceInfo | null>(null)
const spaceQuotas = ref<SpaceQuotaInfo[]>([])
const quotaLoading = ref(false)
const spaceKeyword = ref('')
const spaceSort = ref<'personal-first' | 'usage-desc' | 'name'>('personal-first')

const currentUserId = authStore.user?.id || ''
const quotaMap = computed(() => new Map(spaceQuotas.value.map((quota) => [quota.space_id, quota])))

const displayedSpaces = computed(() => {
  const keyword = spaceKeyword.value.toLocaleLowerCase('zh-CN')
  const result = spaceStore.spaces.filter((space) => (
    !keyword || space.spaceName.toLocaleLowerCase('zh-CN').includes(keyword)
  ))
  return [...result].sort((left, right) => {
    if (spaceSort.value === 'name') return left.spaceName.localeCompare(right.spaceName, 'zh-CN')
    if (spaceSort.value === 'usage-desc') {
      return getQuotaPercent(right) - getQuotaPercent(left)
    }
    return Number(right.spaceType === 'personal') - Number(left.spaceType === 'personal')
  })
})

function spaceUsage(space: SpaceInfo): SpaceQuotaInfo {
  return quotaMap.value.get(space.spaceId) || {
    space_id: space.spaceId,
    space_name: space.spaceName,
    space_type: space.spaceType,
    total_quota: space.spaceQuota || 0,
    used_quota: space.spaceUsed || 0,
    reserved_quota: 0,
    file_count: space.spaceFileCount || 0,
    usage_percent: 0,
  }
}

async function loadSpaceQuotas() {
  quotaLoading.value = true
  try {
    const response = await getAllSpaceQuotasApi()
    if (response.code === 200) spaceQuotas.value = response.data || []
  } finally {
    quotaLoading.value = false
  }
}

async function reloadSpaces() {
  await spaceStore.refreshSpaces()
  await loadSpaceQuotas()
}

function getSpaceIcon(type: string): string {
  const icons: Record<string, string> = {
    personal: 'fa fa-user',
    enterprise: 'fa fa-building',
    private: 'fa fa-lock',
    public: 'fa fa-globe',
    team: 'fa fa-users',
  }
  return icons[type] || 'fa fa-folder'
}

function getSpaceTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    personal: '个人空间',
    enterprise: '企业空间',
    private: '私有空间',
    public: '公共空间',
    team: '团队空间',
  }
  return labels[type] || '空间'
}

function formatSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

function getQuotaPercent(space: SpaceInfo): number {
  const quota = spaceUsage(space)
  if (!quota.total_quota) return 0
  return Math.min(100, Math.round((quota.used_quota / quota.total_quota) * 100))
}

function getQuotaBarColor(space: SpaceInfo): string {
  const pct = getQuotaPercent(space)
  if (pct > 90) return 'bg-red-500'
  if (pct > 70) return 'bg-yellow-500'
  return 'bg-primary'
}

async function selectSpace(spaceId: string) {
  await spaceStore.switchSpace(spaceId)
}

async function enterSpace(spaceId: string) {
  /*
   * 空间管理能力全量集成（需求四-1/2）：
   * 原行为切换请求与路由跳转并发，目标页可能先携带旧空间头加载；
   * 新行为等待切换提交成功后再进入文件页，消除旧数据闪烁。
   */
  if (await spaceStore.switchSpace(spaceId)) {
    await router.push({ path: '/app', query: { space_id: spaceId } })
  }
}

function openManage(space: SpaceInfo) {
  managingSpace.value = space
}

async function onSpaceCreated(spaceId: string) {
  await spaceStore.refreshSpaces()
  await spaceStore.switchSpace(spaceId)
}

function confirmDelete(space: SpaceInfo) {
  deletingSpace.value = space
}

async function doDelete() {
  if (!deletingSpace.value) return
  try {
    await deleteSpaceApi(deletingSpace.value.spaceId)
    await spaceStore.refreshSpaces()
  } finally {
    deletingSpace.value = null
  }
}

onMounted(async () => {
  if (!spaceStore.initialized) await spaceStore.initSpaces()
  await loadSpaceQuotas()
})

watch(() => spaceStore.revision, () => void loadSpaceQuotas())
</script>
