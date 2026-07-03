<template>
  <div class="space-y-4 sm:space-y-6">
    <!-- 标题栏 -->
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold sm:text-2xl">
        <i class="fa fa-clock-o text-primary"></i> 最近访问
      </h1>
    </div>

    <!-- Tab 切换 -->
    <div class="tabs tabs-box bg-base-200">
      <button
        class="tab"
        :class="{ 'tab-active': recentStore.activeTab === 'upload' }"
        @click="recentStore.switchTab('upload')"
      >
        <i class="fa fa-cloud-upload mr-1"></i> 最近上传
        <span class="badge badge-sm ml-1">{{ recentStore.uploadCount }}</span>
      </button>
      <button
        class="tab"
        :class="{ 'tab-active': recentStore.activeTab === 'download' }"
        @click="recentStore.switchTab('download')"
      >
        <i class="fa fa-cloud-download mr-1"></i> 最近下载
        <span class="badge badge-sm ml-1">{{ recentStore.downloadCount }}</span>
      </button>
      <button
        class="tab"
        :class="{ 'tab-active': recentStore.activeTab === 'open' }"
        @click="recentStore.switchTab('open')"
      >
        <i class="fa fa-eye mr-1"></i> 最近打开
        <span class="badge badge-sm ml-1">{{ recentStore.openCount }}</span>
      </button>
    </div>

    <!-- 加载中 -->
    <div v-if="recentStore.loading"><LoadingSpinner /></div>

    <!-- 空状态 -->
    <div v-else-if="recentStore.currentData.length === 0" class="responsive-panel p-8 text-center text-neutral-400 sm:p-10">
      <i class="fa fa-inbox text-4xl mb-2"></i>
      <p>暂无{{ tabLabel }}记录</p>
      <p class="text-xs mt-1">{{ tabHint }}</p>
    </div>

    <!-- 列表 -->
    <div v-else class="responsive-panel">
      <!-- 桌面端表格 -->
      <div class="hidden overflow-x-auto sm:block">
        <table class="table table-sm">
          <thead>
            <tr class="text-xs text-neutral-400">
              <th>名称</th>
              <th>类型</th>
              <th>大小</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in recentStore.currentData"
              :key="item.ra_id"
              class="hover cursor-pointer"
              @click="openItem(item)"
            >
              <td class="flex items-center gap-2">
                <i
                  :class="item.target_type === 'folder' ? 'fa fa-folder text-warning' : getFileIconClass(item.target_name)"
                ></i>
                <span class="text-sm truncate max-w-[200px]">{{ item.target_name }}</span>
              </td>
              <td>
                <span class="badge badge-xs" :class="accessTypeBadge(item.access_type)">
                  {{ accessTypeLabel(item.access_type) }}
                </span>
              </td>
              <td class="text-xs text-neutral-400">
                {{ item.target_type === 'folder' ? '-' : formatSize(item.target_size) }}
              </td>
              <td class="text-xs text-neutral-400">
                {{ formatTime(item.accessed_at) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <!-- 移动端卡片列表 -->
      <div class="divide-y sm:hidden">
        <div
          v-for="item in recentStore.currentData"
          :key="item.ra_id"
          class="flex items-center gap-3 px-4 py-3 cursor-pointer"
          @click="openItem(item)"
        >
          <i
            :class="item.target_type === 'folder' ? 'fa fa-folder text-warning' : getFileIconClass(item.target_name)"
            class="text-lg text-neutral-500"
          ></i>
          <div class="min-w-0 flex-1">
            <p class="text-sm font-medium text-neutral-700 truncate">{{ item.target_name }}</p>
            <div class="mt-0.5 flex items-center gap-2 text-xs">
              <span class="badge badge-xs" :class="accessTypeBadge(item.access_type)">
                {{ accessTypeLabel(item.access_type) }}
              </span>
              <span v-if="item.target_type !== 'folder'" class="text-neutral-400">{{ formatSize(item.target_size) }}</span>
              <span class="text-neutral-400">{{ formatTime(item.accessed_at) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useRecentStore } from '@/stores/recentStore'
import { getFileIconClass } from '@/utils/fileIcon'
import type { RecentAccessVO, AccessType } from '@/api/modules/recent'

const recentStore = useRecentStore()
const router = useRouter()

// ============================================================
// Tab 标签
// ============================================================

const accessTypeLabel = (type: AccessType): string => {
  const map: Record<AccessType, string> = { upload: '上传', download: '下载', open: '打开' }
  return map[type] || type
}

const accessTypeBadge = (type: AccessType): string => {
  const map: Record<AccessType, string> = { upload: 'badge-primary', download: 'badge-success', open: 'badge-info' }
  return map[type] || ''
}

const tabLabel = computed(() => {
  const map: Record<AccessType, string> = { upload: '最近上传', download: '最近下载', open: '最近打开' }
  return map[recentStore.activeTab] || '最近访问'
})

const tabHint = computed(() => {
  const map: Record<AccessType, string> = {
    upload: '上传文件后将自动记录在此处',
    download: '下载文件后将自动记录在此处',
    open: '打开文件后将自动记录在此处',
  }
  return map[recentStore.activeTab] || ''
})

// ============================================================
// 格式化
// ============================================================

const formatSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

const formatTime = (iso: string): string => {
  const d = new Date(iso)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return Math.floor(diff / (60 * 1000)) + ' 分钟前'
  if (diff < 24 * 60 * 60 * 1000) return Math.floor(diff / (60 * 60 * 1000)) + ' 小时前'
  if (diff < 7 * 24 * 60 * 60 * 1000) return Math.floor(diff / (24 * 60 * 60 * 1000)) + ' 天前'
  return d.toLocaleDateString('zh-CN')
}

// ============================================================
// 事件
// ============================================================

const openItem = (item: RecentAccessVO) => {
  if (item.target_type === 'folder') {
    router.push(`/app?folder=${item.target_id}`)
  } else {
    router.push(`/app/preview/${item.target_id}`)
  }
}

// ============================================================
// 初始化
// ============================================================

onMounted(() => {
  recentStore.loadAll()
})
</script>