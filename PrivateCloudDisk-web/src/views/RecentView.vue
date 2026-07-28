<template>
  <div class="space-y-4 sm:space-y-6">
    <!-- 标题栏 -->
    <div class="flex items-center justify-between">
      <h1 class="flex items-center gap-2 text-xl font-bold text-neutral-700 sm:text-2xl">
        <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10">
          <i class="fa fa-clock-o text-primary"></i>
        </span>
        最近访问
      </h1>
    </div>

    <!-- Tab 切换 — 纯 Tailwind 实现 -->
    <div class="inline-flex rounded-xl bg-neutral-100 p-1">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="inline-flex items-center gap-1.5 rounded-lg px-4 py-2 text-sm font-medium transition-all"
        :class="recentStore.activeTab === tab.key
          ? 'bg-white text-primary shadow-sm'
          : 'text-neutral-500 hover:text-neutral-700'"
        @click="recentStore.switchTab(tab.key as AccessType)"
      >
        <i :class="tab.icon" class="text-sm"></i>
        <span>{{ tab.label }}</span>
        <span
          class="ml-1 inline-flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-xs font-semibold"
          :class="recentStore.activeTab === tab.key
            ? 'bg-primary/10 text-primary'
            : 'bg-neutral-200 text-neutral-500'"
        >
          {{ tab.count }}
        </span>
      </button>
    </div>

    <!-- 加载中 -->
    <div v-if="recentStore.loading" class="flex justify-center py-16">
      <LoadingSpinner />
    </div>

    <!-- 空状态 -->
    <div
      v-else-if="recentStore.currentData.length === 0"
      class="flex flex-col items-center rounded-xl bg-white py-16 text-center shadow-sm"
    >
      <div class="mb-4 flex h-20 w-20 items-center justify-center rounded-full bg-neutral-100">
        <i class="fa fa-inbox text-4xl text-neutral-300"></i>
      </div>
      <p class="text-lg font-medium text-neutral-500">暂无{{ tabLabel }}记录</p>
      <p class="mt-1 text-sm text-neutral-400">{{ tabHint }}</p>
    </div>

    <!-- 列表 -->
    <div v-else class="overflow-hidden rounded-xl bg-white shadow-sm">
      <!-- 桌面端表格 -->
      <div class="hidden overflow-x-auto sm:block">
        <table class="w-full">
          <thead>
            <tr class="border-b border-neutral-100 bg-neutral-50/50 text-left text-xs font-medium text-neutral-400">
              <th class="py-3 pl-5 pr-3">名称</th>
              <th class="py-3 px-3">类型</th>
              <th class="py-3 px-3">大小</th>
              <th class="py-3 pl-3 pr-5">时间</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in recentStore.currentData"
              :key="item.ra_id"
              class="border-b border-neutral-50 transition-colors hover:bg-neutral-50/50 cursor-pointer"
              @click="openItem(item)"
            >
              <!-- 名称 -->
              <td class="py-3 pl-5 pr-3">
                <div class="flex items-center gap-3">
                  <div
                    class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg"
                    :class="item.target_type === 'folder' ? 'bg-warning/10' : 'bg-primary/5'"
                  >
                    <i
                      :class="[
                        item.target_type === 'folder'
                          ? 'fa fa-folder text-warning'
                          : getIconForFile(item.target_name),
                        'text-lg'
                      ]"
                    ></i>
                  </div>
                  <span class="max-w-[280px] truncate text-sm font-medium text-neutral-700">
                    {{ item.target_name }}
                  </span>
                </div>
              </td>
              <!-- 类型 — 图标 + 文字 -->
              <td class="py-3 px-3">
                <span class="inline-flex items-center gap-1.5 text-xs text-neutral-500">
                  <i :class="accessTypeIcon(item.access_type)" class="text-xs"></i>
                  {{ accessTypeLabel(item.access_type) }}
                </span>
              </td>
              <!-- 大小 -->
              <td class="py-3 px-3 text-sm text-neutral-400">
                {{ item.target_type === 'folder' ? '—' : formatSize(item.target_size) }}
              </td>
              <!-- 时间 -->
              <td class="py-3 pl-3 pr-5 text-sm text-neutral-400">
                {{ formatTime(item.accessed_at) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 移动端卡片列表 -->
      <div class="divide-y divide-neutral-50 sm:hidden">
        <div
          v-for="item in recentStore.currentData"
          :key="item.ra_id"
          class="flex items-center gap-3 px-4 py-3.5 transition-colors active:bg-neutral-50 cursor-pointer"
          @click="openItem(item)"
        >
          <div
            class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg"
            :class="item.target_type === 'folder' ? 'bg-warning/10' : 'bg-primary/5'"
          >
            <i
              :class="[
                item.target_type === 'folder'
                  ? 'fa fa-folder text-warning'
                  : getIconForFile(item.target_name),
                'text-xl'
              ]"
            ></i>
          </div>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-neutral-700">{{ item.target_name }}</p>
            <div class="mt-1 flex items-center gap-2 text-xs text-neutral-400">
              <span class="inline-flex items-center gap-1">
                <i :class="accessTypeIcon(item.access_type)" class="text-[10px]"></i>
                {{ accessTypeLabel(item.access_type) }}
              </span>
              <span v-if="item.target_type !== 'folder'">· {{ formatSize(item.target_size) }}</span>
              <span>· {{ formatTime(item.accessed_at) }}</span>
            </div>
          </div>
          <i class="fa fa-chevron-right text-xs text-neutral-300"></i>
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
import { isVideo } from '@/utils/previewHelper'
import type { RecentAccessVO, AccessType } from '@/api/modules/recent'

const recentStore = useRecentStore()
const router = useRouter()

// ============================================================
// Tab 标签数据
// ============================================================

const tabs = computed(() => [
  { key: 'upload', label: '最近上传', icon: 'fa fa-cloud-upload', count: recentStore.uploadCount },
  { key: 'download', label: '最近下载', icon: 'fa fa-cloud-download', count: recentStore.downloadCount },
  { key: 'open', label: '最近打开', icon: 'fa fa-eye', count: recentStore.openCount },
])

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
// 类型显示
// ============================================================

const accessTypeLabel = (type: AccessType): string => {
  const map: Record<AccessType, string> = { upload: '上传', download: '下载', open: '打开' }
  return map[type] || type
}

/** 访问类型图标 */
const accessTypeIcon = (type: AccessType): string => {
  const map: Record<AccessType, string> = {
    upload: 'fa fa-cloud-upload text-primary',
    download: 'fa fa-cloud-download text-success',
    open: 'fa fa-eye text-secondary',
  }
  return map[type] || 'fa fa-circle'
}

// ============================================================
// 文件图标（仅返回图标类，不包含颜色类，颜色由父容器控制）
// ============================================================

/** 获取文件图标（仅 FA 图标类名，不含颜色） */
const getIconForFile = (fileName: string): string => {
  const full = getFileIconClass(fileName)
  // 提取纯图标类名（去掉颜色类如 text-danger/text-blue-600 等）
  const iconClass = full.split(' ').find(c => c.startsWith('fa-'))
  return iconClass || 'fa fa-file-o'
}

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
    return
  }

  const fileName = item.target_name || ''

  // 视频文件：跳转至专属流媒体播放页面，携带 fileId 参数
  if (isVideo(fileName)) {
    router.push({
      name: 'VideoPlayer',
      params: { fileId: item.target_id },
      query: { name: encodeURIComponent(fileName) }
    })
    return
  }

  router.push(`/app/preview/${item.target_id}`)
}

// ============================================================
// 初始化
// ============================================================

onMounted(() => {
  recentStore.loadAll()
})
</script>