<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="存储空间"
      description="查看存储空间使用详情，管理文件清理和空间优化"
      :breadcrumbs="[{ label: '存储空间', icon: 'fa fa-hdd-o' }]"
      :tabs="storageTabs"
      :active-tab="activeTab"
      @tab-change="activeTab = $event"
    />

    <!-- 概览 -->
    <div v-if="activeTab === 'overview'" class="space-y-4 sm:space-y-6">
      <!-- 存储空间总览卡片 -->
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard title="已使用空间" :value="storageStore.used" :format="formatSize" progress-color="bg-primary" />
        <StatCard title="总空间" :value="storageStore.total" :format="formatSize" progress-color="bg-info" />
        <StatCard title="剩余空间" :value="total - used" :format="formatSize" progress-color="bg-success" />
      </div>

      <!-- 使用进度条 -->
      <div class="responsive-panel p-4 sm:p-6">
        <div class="flex items-center justify-between mb-3">
          <h3 class="text-base font-semibold text-neutral-700">空间使用情况</h3>
          <span class="text-sm text-neutral-400">{{ formatSize(used) }} / {{ formatSize(total) }}</span>
        </div>
        <div class="h-4 w-full rounded-full bg-neutral-100 overflow-hidden">
          <div
            class="h-full rounded-full transition-all duration-500"
            :class="usageColorClass"
            :style="{ width: usagePercent + '%' }"
          ></div>
        </div>
        <div class="mt-3 flex flex-wrap gap-4 text-xs text-neutral-400">
          <span class="flex items-center gap-1"><span class="inline-block w-2.5 h-2.5 rounded-full" :class="usageColorClass"></span>已使用 {{ usagePercent.toFixed(1) }}%</span>
          <span class="flex items-center gap-1"><span class="inline-block w-2.5 h-2.5 rounded-full bg-neutral-200"></span>剩余 {{ (100 - usagePercent).toFixed(1) }}%</span>
        </div>
      </div>

      <!-- 文件类型分布 -->
      <div class="responsive-panel p-4 sm:p-6">
        <h3 class="mb-4 text-base font-semibold text-neutral-700">文件类型分布</h3>
        <div class="space-y-3">
          <div v-for="cat in fileCategories" :key="cat.type" class="flex items-center gap-3">
            <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg" :class="cat.bgClass">
              <i :class="cat.icon" :style="{ color: cat.color }"></i>
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between text-sm">
                <span class="font-medium text-neutral-700 truncate">{{ cat.label }}</span>
                <span class="text-neutral-500 shrink-0">{{ formatSize(cat.size) }}</span>
              </div>
              <div class="mt-1 h-1.5 w-full rounded-full bg-neutral-100">
                <div
                  class="h-1.5 rounded-full transition-all"
                  :style="{ width: catPercent(cat) + '%', backgroundColor: cat.color }"
                ></div>
              </div>
              <p class="mt-0.5 text-xs text-neutral-400">{{ cat.count }} 个文件 · 占比 {{ catPercent(cat).toFixed(1) }}%</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 大文件管理 -->
    <div v-if="activeTab === 'large'" class="space-y-4">
      <div class="flex items-center justify-between">
        <p class="text-sm text-neutral-400">
          显示大于 {{ largeFileThresholdMB }} MB 的文件，共 {{ largeFiles.length }} 个
        </p>
        <select v-model="largeFileThresholdMB" class="rounded-lg border border-neutral-200 px-3 py-1.5 text-sm text-neutral-600 focus:border-primary focus:outline-none">
          <option :value="10">> 10 MB</option>
          <option :value="50">> 50 MB</option>
          <option :value="100">> 100 MB</option>
          <option :value="500">> 500 MB</option>
          <option :value="1024">> 1 GB</option>
        </select>
      </div>

      <EmptyState
        v-if="largeFiles.length === 0"
        icon="fa fa-file-o"
        message="没有找到大文件"
        description="当前存储空间中没有超过阈值的文件"
      />

      <div v-else class="responsive-panel overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm">
            <thead class="border-b border-neutral-100 bg-neutral-50/50">
              <tr>
                <th class="px-4 py-3 font-medium text-neutral-500">文件名</th>
                <th class="px-4 py-3 font-medium text-neutral-500">类型</th>
                <th class="px-4 py-3 font-medium text-neutral-500">大小</th>
                <th class="px-4 py-3 font-medium text-neutral-500">修改时间</th>
                <th class="px-4 py-3 font-medium text-neutral-500 text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(file, i) in largeFiles" :key="i" class="border-b border-neutral-50 transition-colors hover:bg-neutral-50/50">
                <td class="px-4 py-3">
                  <div class="flex items-center gap-2">
                    <i :class="getFileIcon(file.ext)" class="text-neutral-400"></i>
                    <span class="font-medium text-neutral-700 truncate max-w-[200px]">{{ file.name }}</span>
                  </div>
                </td>
                <td class="px-4 py-3">
                  <span class="rounded-full bg-neutral-100 px-2 py-0.5 text-xs text-neutral-500">{{ file.typeLabel }}</span>
                </td>
                <td class="px-4 py-3 text-neutral-600">{{ formatSize(file.size) }}</td>
                <td class="px-4 py-3 text-neutral-400">{{ formatTime(file.modifiedAt) }}</td>
                <td class="px-4 py-3 text-right">
                  <button class="text-primary hover:underline text-xs">查看</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 清理建议 -->
    <div v-if="activeTab === 'cleanup'" class="space-y-4">
      <div v-for="suggestion in cleanupSuggestions" :key="suggestion.id" class="responsive-panel flex items-start gap-4 p-4 sm:p-5">
        <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full" :class="suggestion.bgClass">
          <i :class="suggestion.icon" :style="{ color: suggestion.color }"></i>
        </div>
        <div class="flex-1 min-w-0">
          <div class="flex items-start justify-between gap-2">
            <div>
              <h4 class="text-sm font-semibold text-neutral-700">{{ suggestion.title }}</h4>
              <p class="mt-0.5 text-xs text-neutral-400">{{ suggestion.description }}</p>
            </div>
            <span class="shrink-0 text-sm font-medium" :class="suggestion.sizeColor">
              {{ formatSize(suggestion.size) }}
            </span>
          </div>
          <div class="mt-3 flex gap-2">
            <button class="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white hover:bg-primary/90 transition">
              立即清理
            </button>
            <button class="rounded-lg border border-neutral-200 px-3 py-1.5 text-xs text-neutral-500 hover:bg-neutral-50 transition">
              查看详情
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatCard from '@/components/common/StatCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useStorageStore } from '@/stores/storageStore'

const storageStore = useStorageStore()
const activeTab = ref('overview')
const largeFileThresholdMB = ref(100)

const storageTabs = [
  { key: 'overview', label: '概览', icon: 'fa fa-pie-chart' },
  { key: 'large', label: '大文件', icon: 'fa fa-file-archive-o' },
  { key: 'cleanup', label: '清理建议', icon: 'fa fa-broom' },
]

const used = computed(() => storageStore.used)
const total = computed(() => storageStore.total)
const usagePercent = computed(() => storageStore.percent)

const usageColorClass = computed(() => {
  if (usagePercent.value > 90) return 'bg-red-500'
  if (usagePercent.value > 70) return 'bg-yellow-500'
  return 'bg-green-500'
})

// 文件类型分布（基于存储总量模拟分配）
const fileCategories = computed(() => {
  const used = storageStore.used
  return [
    { type: 'video', label: '视频', size: Math.round(used * 0.35), count: 12, icon: 'fa fa-video-camera', color: '#8b5cf6', bgClass: 'bg-purple-100' },
    { type: 'image', label: '图片', size: Math.round(used * 0.25), count: 156, icon: 'fa fa-image', color: '#3b82f6', bgClass: 'bg-blue-100' },
    { type: 'document', label: '文档', size: Math.round(used * 0.20), count: 89, icon: 'fa fa-file-text-o', color: '#f59e0b', bgClass: 'bg-yellow-100' },
    { type: 'archive', label: '压缩包', size: Math.round(used * 0.12), count: 23, icon: 'fa fa-file-archive-o', color: '#ef4444', bgClass: 'bg-red-100' },
    { type: 'other', label: '其他', size: Math.round(used * 0.08), count: 45, icon: 'fa fa-file-o', color: '#6b7280', bgClass: 'bg-gray-100' },
  ]
})

function catPercent(cat) {
  const u = storageStore.used
  if (u <= 0) return 0
  return (cat.size / u) * 100
}

// 大文件列表 (模拟数据)
const largeFiles = computed(() => {
  const threshold = largeFileThresholdMB.value * 1024 * 1024
  const mockFiles = [
    { name: '项目演示视频.mp4', ext: '.mp4', size: 856 * 1024 * 1024, typeLabel: '视频', modifiedAt: '2026-05-12' },
    { name: '设计素材包.zip', ext: '.zip', size: 420 * 1024 * 1024, typeLabel: '压缩包', modifiedAt: '2026-04-28' },
    { name: '系统镜像.iso', ext: '.iso', size: 3.2 * 1024 * 1024 * 1024, typeLabel: '镜像', modifiedAt: '2026-03-15' },
    { name: '数据备份.tar.gz', ext: '.tar.gz', size: 1.5 * 1024 * 1024 * 1024, typeLabel: '压缩包', modifiedAt: '2026-06-01' },
    { name: '年会视频.mov', ext: '.mov', size: 2.8 * 1024 * 1024 * 1024, typeLabel: '视频', modifiedAt: '2026-01-20' },
    { name: 'PSD源文件合集.psd', ext: '.psd', size: 180 * 1024 * 1024, typeLabel: '设计', modifiedAt: '2026-05-30' },
  ]
  return mockFiles.filter(f => f.size >= threshold)
})

const cleanupSuggestions = computed(() => [
  {
    id: 1,
    title: '回收站文件',
    description: '回收站中有文件待彻底清理，释放空间',
    size: Math.round(storageStore.used * 0.08),
    icon: 'fa fa-trash',
    color: '#ef4444',
    bgClass: 'bg-red-100',
    sizeColor: 'text-red-500',
  },
  {
    id: 2,
    title: '重复文件',
    description: '检测到可能存在重复的文件副本',
    size: Math.round(storageStore.used * 0.05),
    icon: 'fa fa-files-o',
    color: '#f59e0b',
    bgClass: 'bg-yellow-100',
    sizeColor: 'text-yellow-600',
  },
  {
    id: 3,
    title: '旧版本文件',
    description: '版本管理中的旧版本文件可选择性清理',
    size: Math.round(storageStore.used * 0.03),
    icon: 'fa fa-history',
    color: '#3b82f6',
    bgClass: 'bg-blue-100',
    sizeColor: 'text-blue-500',
  },
  {
    id: 4,
    title: '临时文件',
    description: '缓存和临时文件，删除不影响使用',
    size: Math.round(storageStore.used * 0.02),
    icon: 'fa fa-clock-o',
    color: '#6b7280',
    bgClass: 'bg-gray-100',
    sizeColor: 'text-gray-500',
  },
])

function formatSize(bytes) {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function formatTime(time) {
  if (!time) return '--'
  try { return new Date(time).toLocaleDateString('zh-CN') } catch { return time }
}

function getFileIcon(ext) {
  const map = {
    '.mp4': 'fa fa-video-camera', '.mov': 'fa fa-video-camera',
    '.zip': 'fa fa-file-archive-o', '.tar.gz': 'fa fa-file-archive-o', '.iso': 'fa fa-file-archive-o',
    '.psd': 'fa fa-image',
  }
  return map[ext] || 'fa fa-file-o'
}

onMounted(() => {
  storageStore.fetchStorageInfo()
})
</script>