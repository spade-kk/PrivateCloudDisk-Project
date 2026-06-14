<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="文件版本"
      description="管理文件的历史版本，恢复到任意时间点"
      :breadcrumbs="[{ label: '我的网盘', to: '/' }, { label: '文件版本', icon: 'fa fa-history' }]"
    />

    <!-- 文件信息 -->
    <div class="responsive-panel flex items-center gap-4 p-4">
      <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-primary/10">
        <i class="fa fa-file text-xl text-primary"></i>
      </div>
      <div class="min-w-0 flex-1">
        <h3 class="text-base font-semibold text-neutral-700 truncate">年度总结报告_v3.pptx</h3>
        <p class="text-xs text-neutral-400">当前版本: v3 · 12.5 MB · 修改于 2026-01-10 14:30</p>
      </div>
      <div class="text-right">
        <p class="text-xs text-neutral-400">共 {{ versions.length }} 个版本</p>
        <p class="text-xs text-neutral-400">占用 38.2 MB</p>
      </div>
    </div>

    <!-- 版本时间线 -->
    <div class="responsive-panel p-4 sm:p-6">
      <Timeline :items="versions">
        <template #title="{ item }">
          <span class="font-medium">版本 {{ item.version }}</span>
          <StatusBadge v-if="item.current" status="active" class="ml-2" />
        </template>
        <template #description="{ item }">{{ item.size }} · 由 {{ item.author }} 修改</template>
        <template #extra="{ item }">
          <div class="mt-2 flex gap-2">
            <button v-if="!item.current" @click="restoreVersion(item)" class="rounded border border-primary/30 px-3 py-1 text-xs text-primary hover:bg-primary/5">
              <i class="fa fa-undo mr-1"></i> 恢复此版本
            </button>
            <button @click="previewVersion(item)" class="rounded border border-neutral-200 px-3 py-1 text-xs text-neutral-500 hover:bg-neutral-50">
              <i class="fa fa-eye mr-1"></i> 预览
            </button>
            <button @click="downloadVersion(item)" class="rounded border border-neutral-200 px-3 py-1 text-xs text-neutral-500 hover:bg-neutral-50">
              <i class="fa fa-download mr-1"></i> 下载
            </button>
          </div>
        </template>
      </Timeline>
    </div>

    <!-- 版本管理设置 -->
    <div class="responsive-panel p-4 sm:p-6">
      <h3 class="mb-4 text-base font-semibold text-neutral-700">版本管理设置</h3>
      <div class="space-y-4">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-neutral-700">自动版本管理</p>
            <p class="text-xs text-neutral-400">每次修改文件时自动保存历史版本</p>
          </div>
          <label class="relative inline-flex cursor-pointer items-center">
            <input type="checkbox" checked class="peer sr-only" />
            <div class="h-6 w-11 rounded-full bg-primary after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:bg-white after:transition-all peer-checked:after:translate-x-full"></div>
          </label>
        </div>
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-neutral-700">最大保留版本数</p>
            <p class="text-xs text-neutral-400">超过限制将自动删除最旧版本</p>
          </div>
          <select class="rounded-lg border border-neutral-200 px-3 py-1.5 text-sm">
            <option>10</option>
            <option selected>30</option>
            <option>50</option>
            <option>100</option>
          </select>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import Timeline from '@/components/common/Timeline.vue'

const versions = [
  { version: 'v3', current: true, size: '12.5 MB', author: '张三', time: '2026-01-10 14:30', type: 'update' },
  { version: 'v2', size: '11.8 MB', author: '李四', time: '2026-01-08 09:15', type: 'update' },
  { version: 'v1', size: '10.2 MB', author: '张三', time: '2026-01-05 16:00', type: 'create' },
]

function restoreVersion(version) {
  if (!confirm(`确定要恢复到版本 ${version.version} 吗？当前版本将被保存为历史版本。`)) return
  // 恢复逻辑
}

function previewVersion(version) {
  // 预览逻辑
}

function downloadVersion(version) {
  // 下载逻辑
}
</script>