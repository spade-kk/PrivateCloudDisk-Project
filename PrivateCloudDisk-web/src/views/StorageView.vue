<template>
  <div class="space-y-5 sm:space-y-6">
    <!-- ==================== 页面头部 ==================== -->
    <PageHeader
      title="存储空间"
      description="管理存储空间，查看使用详情，清理冗余文件"
      :breadcrumbs="[{ label: '存储空间', icon: 'fa fa-hdd-o' }]"
      :tabs="storageTabs"
      :active-tab="activeTab"
      @tab-change="activeTab = $event"
    />

    <!-- ==================== 概览标签页 ==================== -->
    <template v-if="activeTab === 'overview'">
      <!-- 统计卡片行 -->
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StorageStatCard
          title="已使用空间"
          :bytes="used"
          icon="fa fa-database"
          accent-color="#3b82f6"
          :progress="usagePercent"
          progress-color="#3b82f6"
          :progress-label="`占比 ${usagePercent.toFixed(1)}%`"
          subtitle="当前已消耗的存储容量"
        />
        <StorageStatCard
          title="总空间"
          :bytes="total"
          icon="fa fa-cloud"
          accent-color="#8b5cf6"
          subtitle="可用存储空间上限"
        />
        <StorageStatCard
          title="剩余空间"
          :bytes="remaining"
          icon="fa fa-check-circle"
          accent-color="#10b981"
          :progress="remainingPercent"
          progress-color="#10b981"
          :progress-label="`剩余 ${remainingPercent.toFixed(1)}%`"
          :subtitle="remaining <= 0 ? '空间已满，请及时清理' : '可继续使用的存储容量'"
        />
      </div>

      <!-- 环形图 + 文件分布 -->
      <div class="grid grid-cols-1 gap-5 lg:grid-cols-5">
        <!-- 环形图 -->
        <div class="responsive-panel flex flex-col items-center p-5 sm:p-6 lg:col-span-2">
          <h3 class="mb-4 self-start text-base font-semibold text-neutral-700">空间使用概览</h3>
          <StorageRingChart
            :total="total"
            :segments="ringSegments"
            :size="200"
            :stroke-width="16"
            bg-color="#f1f5f9"
            :show-legend="true"
          />
          <p class="mt-4 text-xs text-neutral-400">
            已使用 {{ formatSize(used) }}，共 {{ formatSize(total) }}
          </p>
        </div>

        <!-- 文件类型分布 -->
        <div class="responsive-panel p-5 sm:p-6 lg:col-span-3">
          <h3 class="mb-4 text-base font-semibold text-neutral-700">文件类型分布</h3>
          <div class="space-y-4">
            <div
              v-for="cat in fileCategories"
              :key="cat.type"
              class="group flex items-center gap-4 rounded-xl p-3 transition-colors hover:bg-neutral-50"
            >
              <!-- 图标 -->
              <div
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl transition-transform duration-300 group-hover:scale-110"
                :style="{ backgroundColor: cat.color + '18' }"
              >
                <i :class="cat.icon" :style="{ color: cat.color }" class="text-lg"></i>
              </div>

              <!-- 信息 -->
              <div class="flex-1 min-w-0">
                <div class="flex items-center justify-between">
                  <span class="text-sm font-medium text-neutral-700">{{ cat.label }}</span>
                  <span class="text-sm text-neutral-500">{{ formatSize(cat.size) }}</span>
                </div>
                <div class="mt-1.5 h-2 w-full rounded-full bg-neutral-100 overflow-hidden">
                  <div
                    class="h-full rounded-full transition-all duration-700 ease-out"
                    :style="{ width: catPercent(cat) + '%', backgroundColor: cat.color }"
                  ></div>
                </div>
                <div class="mt-1 flex items-center gap-3 text-xs text-neutral-400">
                  <span>{{ cat.count }} 个文件</span>
                  <span class="inline-block h-1 w-1 rounded-full bg-neutral-300"></span>
                  <span>占比 {{ catPercent(cat).toFixed(1) }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 快捷操作 -->
      <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <button
          v-for="action in quickActions"
          :key="action.key"
          class="responsive-panel group flex flex-col items-center gap-2 p-4 text-center transition-all hover:shadow-md hover:-translate-y-0.5"
          @click="activeTab = action.tab"
        >
          <div
            class="flex h-10 w-10 items-center justify-center rounded-xl transition-transform duration-300 group-hover:scale-110"
            :style="{ backgroundColor: action.color + '18' }"
          >
            <i :class="action.icon" :style="{ color: action.color }" class="text-lg"></i>
          </div>
          <span class="text-sm font-medium text-neutral-700">{{ action.label }}</span>
          <span class="text-xs text-neutral-400">{{ action.desc }}</span>
        </button>
      </div>
    </template>

    <!-- ==================== 大文件标签页 ==================== -->
    <template v-if="activeTab === 'large'">
      <!-- 筛选栏 -->
      <div class="responsive-panel flex flex-wrap items-center justify-between gap-3 p-4">
        <p class="text-sm text-neutral-500">
          显示大于 <span class="font-medium text-neutral-700">{{ formatSize(largeFileThresholdBytes) }}</span> 的文件，共
          <span class="font-medium text-neutral-700">{{ largeFiles.length }}</span> 个
        </p>
        <select
          v-model="largeFileThresholdMB"
          class="rounded-lg border border-neutral-200 bg-white px-3 py-2 text-sm text-neutral-600 shadow-sm transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
        >
          <option :value="10">大于 10 MB</option>
          <option :value="50">大于 50 MB</option>
          <option :value="100">大于 100 MB</option>
          <option :value="500">大于 500 MB</option>
          <option :value="1024">大于 1 GB</option>
        </select>
      </div>

      <!-- 空状态 -->
      <EmptyState
        v-if="largeFiles.length === 0"
        icon="fa fa-file-o"
        message="没有找到大文件"
        description="当前存储空间中没有超过阈值的文件"
      />

      <!-- 大文件表格 -->
      <div v-else class="responsive-panel overflow-hidden">
        <!-- 桌面端表格 -->
        <div class="hidden overflow-x-auto sm:block">
          <table class="w-full text-left text-sm">
            <thead>
              <tr class="border-b border-neutral-100 bg-neutral-50/50">
                <th class="px-4 py-3 font-medium text-neutral-500">文件名</th>
                <th class="px-4 py-3 font-medium text-neutral-500">类型</th>
                <th class="px-4 py-3 font-medium text-neutral-500">大小</th>
                <th class="px-4 py-3 font-medium text-neutral-500 hidden sm:table-cell">修改时间</th>
                <th class="px-4 py-3 font-medium text-neutral-500 text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(file, i) in largeFiles"
                :key="i"
                class="border-b border-neutral-50 transition-colors hover:bg-neutral-50/50"
              >
                <td class="px-4 py-3">
                  <div class="flex items-center gap-2.5">
                    <i :class="getFileIcon(file.ext)" class="text-neutral-400"></i>
                    <span class="font-medium text-neutral-700 truncate max-w-[200px]">{{ file.name }}</span>
                  </div>
                </td>
                <td class="px-4 py-3">
                  <span class="rounded-full bg-neutral-100 px-2.5 py-1 text-xs text-neutral-500">{{ file.typeLabel }}</span>
                </td>
                <td class="px-4 py-3">
                  <span class="font-medium text-neutral-700">{{ formatSize(file.size) }}</span>
                </td>
                <td class="px-4 py-3 text-neutral-400 hidden sm:table-cell">{{ formatTime(file.modifiedAt) }}</td>
                <td class="px-4 py-3 text-right">
                  <button class="touch-button rounded-lg px-3 py-1.5 text-xs font-medium text-primary transition hover:bg-primary/10">
                    查看
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <!-- 移动端卡片列表 -->
        <div class="divide-y sm:hidden">
          <div
            v-for="(file, i) in largeFiles"
            :key="i"
            class="flex items-center gap-3 px-4 py-3"
          >
            <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-neutral-100">
              <i :class="getFileIcon(file.ext)" class="text-neutral-500 text-lg"></i>
            </div>
            <div class="min-w-0 flex-1">
              <p class="text-sm font-medium text-neutral-700 truncate">{{ file.name }}</p>
              <div class="mt-0.5 flex items-center gap-2 text-xs text-neutral-400">
                <span class="rounded-full bg-neutral-100 px-2 py-0.5 text-xs">{{ file.typeLabel }}</span>
                <span class="font-medium text-neutral-600">{{ formatSize(file.size) }}</span>
              </div>
            </div>
            <button class="touch-button shrink-0 rounded-lg border border-primary px-3 py-1.5 text-xs text-primary">
              查看
            </button>
          </div>
        </div>
      </div>
    </template>

    <!-- ==================== 清理建议标签页 ==================== -->
    <template v-if="activeTab === 'cleanup'">
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div
          v-for="suggestion in cleanupSuggestions"
          :key="suggestion.id"
          class="responsive-panel group flex flex-col gap-4 p-5 transition-all hover:shadow-md hover:-translate-y-0.5"
        >
          <!-- 头部 -->
          <div class="flex items-start gap-4">
            <div
              class="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl transition-transform duration-300 group-hover:scale-110"
              :style="{ backgroundColor: suggestion.color + '18' }"
            >
              <i :class="suggestion.icon" :style="{ color: suggestion.color }" class="text-xl"></i>
            </div>
            <div class="flex-1 min-w-0">
              <h4 class="text-sm font-semibold text-neutral-700">{{ suggestion.title }}</h4>
              <p class="mt-1 text-xs text-neutral-400 leading-relaxed">{{ suggestion.description }}</p>
              <div class="mt-2 flex items-center gap-2">
                <span class="text-sm font-semibold" :style="{ color: suggestion.color }">
                  {{ formatSize(suggestion.size) }}
                </span>
                <span class="text-xs text-neutral-400">可释放空间</span>
              </div>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="flex gap-2">
            <button
              class="flex-1 rounded-lg px-3 py-2 text-xs font-medium text-white transition hover:opacity-90"
              :style="{ backgroundColor: suggestion.color }"
            >
              <i class="fa fa-bolt mr-1"></i>立即清理
            </button>
            <button
              class="rounded-lg border border-neutral-200 px-3 py-2 text-xs text-neutral-500 transition hover:bg-neutral-50"
            >
              查看详情
            </button>
          </div>
        </div>
      </div>

      <!-- 清理提示 -->
      <div class="responsive-panel flex items-start gap-4 p-5" style="background: linear-gradient(135deg, #eff6ff 0%, #f0fdf4 100%);">
        <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white shadow-sm">
          <i class="fa fa-lightbulb-o text-amber-500 text-lg"></i>
        </div>
        <div>
          <h4 class="text-sm font-semibold text-neutral-700">智能清理建议</h4>
          <p class="mt-1 text-xs text-neutral-500 leading-relaxed">
            以上建议基于文件访问频率、修改时间和文件类型综合分析生成。清理前建议确认文件不再需要，重要文件请先备份。
          </p>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import StorageStatCard from '@/components/common/StorageStatCard.vue'
import StorageRingChart from '@/components/common/StorageRingChart.vue'
import { useStorageStore } from '@/stores/storageStore'

// ==================== Store ====================

const storageStore = useStorageStore()
const activeTab = ref('overview')
const largeFileThresholdMB = ref(100)

// ==================== 标签页 ====================

const storageTabs = [
  { key: 'overview', label: '概览', icon: 'fa fa-pie-chart' },
  { key: 'large', label: '大文件', icon: 'fa fa-file-archive-o' },
  { key: 'cleanup', label: '清理建议', icon: 'fa fa-broom' },
]

// ==================== 计算属性 ====================

const used = computed(() => storageStore.used)
const total = computed(() => storageStore.total)
const usagePercent = computed(() => storageStore.percent)
const remaining = computed(() => Math.max(0, total.value - used.value))
const remainingPercent = computed(() => (total.value > 0 ? (remaining.value / total.value) * 100 : 0))

/** 环形图数据段 */
const ringSegments = computed(() => [
  { value: used.value, color: '#3b82f6', label: '已使用' },
  { value: remaining.value, color: '#e5e7eb', label: '剩余' },
])

/** 大文件阈值（字节） */
const largeFileThresholdBytes = computed(() => largeFileThresholdMB.value * 1024 * 1024)

// ==================== 文件类型分布 ====================

const fileCategories = computed(() => {
  const u = used.value || 1
  return [
    { type: 'video',    label: '视频',   size: Math.round(u * 0.35), count: 12,  icon: 'fa fa-video-camera',   color: '#8b5cf6' },
    { type: 'image',    label: '图片',   size: Math.round(u * 0.25), count: 156, icon: 'fa fa-image',          color: '#3b82f6' },
    { type: 'document', label: '文档',   size: Math.round(u * 0.20), count: 89,  icon: 'fa fa-file-text-o',    color: '#f59e0b' },
    { type: 'archive',  label: '压缩包', size: Math.round(u * 0.12), count: 23,  icon: 'fa fa-file-archive-o', color: '#ef4444' },
    { type: 'other',    label: '其他',   size: Math.round(u * 0.08), count: 45,  icon: 'fa fa-file-o',         color: '#6b7280' },
  ]
})

function catPercent(cat: { size: number }): number {
  const u = storageStore.used
  if (u <= 0) return 0
  return (cat.size / u) * 100
}

// ==================== 快捷操作 ====================

const quickActions = [
  { key: 'large', label: '大文件', desc: '查找清理', icon: 'fa fa-search', color: '#f59e0b', tab: 'large' },
  { key: 'cleanup', label: '清理建议', desc: '一键释放', icon: 'fa fa-broom', color: '#10b981', tab: 'cleanup' },
  { key: 'trash', label: '回收站', desc: '彻底清理', icon: 'fa fa-trash', color: '#ef4444', tab: 'cleanup' },
  { key: 'duplicates', label: '重复文件', desc: '智能去重', icon: 'fa fa-files-o', color: '#8b5cf6', tab: 'cleanup' },
]

// ==================== 大文件（模拟数据） ====================

const largeFiles = computed(() => {
  const threshold = largeFileThresholdBytes.value
  const mockFiles = [
    { name: '项目演示视频.mp4',     ext: '.mp4',    size: 856 * 1024 * 1024,        typeLabel: '视频',   modifiedAt: '2026-05-12' },
    { name: '设计素材包.zip',       ext: '.zip',    size: 420 * 1024 * 1024,        typeLabel: '压缩包', modifiedAt: '2026-04-28' },
    { name: '系统镜像.iso',         ext: '.iso',    size: 3.2 * 1024 * 1024 * 1024, typeLabel: '镜像',   modifiedAt: '2026-03-15' },
    { name: '数据备份.tar.gz',      ext: '.tar.gz', size: 1.5 * 1024 * 1024 * 1024, typeLabel: '压缩包', modifiedAt: '2026-06-01' },
    { name: '年会视频.mov',         ext: '.mov',    size: 2.8 * 1024 * 1024 * 1024, typeLabel: '视频',   modifiedAt: '2026-01-20' },
    { name: 'PSD源文件合集.psd',    ext: '.psd',    size: 180 * 1024 * 1024,        typeLabel: '设计',   modifiedAt: '2026-05-30' },
    { name: 'AI训练数据集.h5',      ext: '.h5',     size: 4.5 * 1024 * 1024 * 1024, typeLabel: '数据',   modifiedAt: '2026-02-10' },
  ]
  return mockFiles.filter(f => f.size >= threshold)
})

// ==================== 清理建议 ====================

const cleanupSuggestions = computed(() => [
  {
    id: 1,
    title: '回收站文件',
    description: '回收站中有待彻底清理的文件，删除后永久释放空间',
    size: Math.round(used.value * 0.08),
    icon: 'fa fa-trash',
    color: '#ef4444',
  },
  {
    id: 2,
    title: '重复文件',
    description: '检测到可能存在内容重复的文件副本，建议保留一份',
    size: Math.round(used.value * 0.05),
    icon: 'fa fa-files-o',
    color: '#f59e0b',
  },
  {
    id: 3,
    title: '旧版本文件',
    description: '版本历史中的旧版本文件可选择性清理以释放空间',
    size: Math.round(used.value * 0.03),
    icon: 'fa fa-history',
    color: '#3b82f6',
  },
  {
    id: 4,
    title: '临时缓存文件',
    description: '系统缓存和临时文件，删除不影响正常使用',
    size: Math.round(used.value * 0.02),
    icon: 'fa fa-clock-o',
    color: '#6b7280',
  },
])

// ==================== 工具函数 ====================

function formatSize(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function formatTime(time: string): string {
  if (!time) return '--'
  try { return new Date(time).toLocaleDateString('zh-CN') } catch { return time }
}

function getFileIcon(ext: string): string {
  const map: Record<string, string> = {
    '.mp4': 'fa fa-video-camera',
    '.mov': 'fa fa-video-camera',
    '.zip': 'fa fa-file-archive-o',
    '.tar.gz': 'fa fa-file-archive-o',
    '.iso': 'fa fa-file-archive-o',
    '.psd': 'fa fa-image',
    '.h5': 'fa fa-database',
  }
  return map[ext] || 'fa fa-file-o'
}

// ==================== 初始化 ====================

onMounted(() => {
  storageStore.fetchStorageInfo()
})
</script>