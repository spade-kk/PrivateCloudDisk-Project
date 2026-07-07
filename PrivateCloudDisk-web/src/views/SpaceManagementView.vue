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
      <div v-if="spaceStore.loading" class="flex items-center justify-center py-20">
        <i class="fa fa-spinner fa-spin text-2xl text-primary"></i>
      </div>

      <div v-else-if="spaceStore.spaces.length === 0" class="flex flex-col items-center justify-center py-20 text-neutral-400">
        <i class="fa fa-folder-open text-5xl"></i>
        <p class="mt-4 text-sm">暂无空间，点击上方按钮创建</p>
      </div>

      <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        <div
          v-for="space in spaceStore.spaces"
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
                {{ formatSize(space.spaceUsed) }} / {{ formatSize(space.spaceQuota) }}
              </span>
            </div>
            <div class="flex items-center justify-between">
              <span>文件数量</span>
              <span class="font-medium text-neutral-600">{{ space.spaceFileCount }} 个</span>
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
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSpaceStore } from '@/stores/spaceStore'
import { useAuthStore } from '@/stores/authStore'
import { deleteSpaceApi } from '@/api/modules/space'
import type { SpaceInfo } from '@/api/modules/space'
import CreateSpaceDialog from '@/components/space/CreateSpaceDialog.vue'
import SpaceManageDialog from '@/components/space/SpaceManageDialog.vue'

const router = useRouter()
const spaceStore = useSpaceStore()
const authStore = useAuthStore()

const showCreateDialog = ref(false)
const managingSpace = ref<SpaceInfo | null>(null)
const deletingSpace = ref<SpaceInfo | null>(null)

const currentUserId = authStore.user?.id || ''

function getSpaceIcon(type: string): string {
  const icons: Record<string, string> = {
    personal: 'fa fa-user',
    enterprise: 'fa fa-building',
    public: 'fa fa-globe',
    team: 'fa fa-users',
  }
  return icons[type] || 'fa fa-folder'
}

function getSpaceTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    personal: '个人空间',
    enterprise: '企业空间',
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
  if (!space.spaceQuota) return 0
  return Math.min(100, Math.round((space.spaceUsed / space.spaceQuota) * 100))
}

function getQuotaBarColor(space: SpaceInfo): string {
  const pct = getQuotaPercent(space)
  if (pct > 90) return 'bg-red-500'
  if (pct > 70) return 'bg-yellow-500'
  return 'bg-primary'
}

function selectSpace(spaceId: string) {
  spaceStore.switchSpace(spaceId)
}

function enterSpace(spaceId: string) {
  spaceStore.switchSpace(spaceId)
  router.push(`/app?space=${spaceId}`)
}

function openManage(space: SpaceInfo) {
  managingSpace.value = space
}

function onSpaceCreated(spaceId: string) {
  spaceStore.switchSpace(spaceId)
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
</script>