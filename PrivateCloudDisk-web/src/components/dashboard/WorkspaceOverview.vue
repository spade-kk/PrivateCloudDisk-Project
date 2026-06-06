<template>
  <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
    <div
      v-for="item in metrics"
      :key="item.label"
      class="rounded-lg border border-neutral-200 bg-white p-4 shadow-card transition duration-200 hover:-translate-y-0.5 hover:shadow-hover"
    >
      <div class="flex items-center justify-between gap-3">
        <div class="min-w-0">
          <p class="text-xs text-neutral-500">{{ item.label }}</p>
          <p class="mt-1 truncate text-xl font-semibold text-neutral-700">{{ item.value }}</p>
        </div>
        <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg" :class="item.bg">
          <i :class="[item.icon, item.color]"></i>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  selectedCount: { type: Number, default: 0 },
  pathDepth: { type: Number, default: 1 },
})

const folderCount = computed(() => props.nodes.filter(node => node.node_type === 'FOLDER').length)
const fileCount = computed(() => props.nodes.filter(node => node.node_type === 'FILE').length)

const metrics = computed(() => [
  { label: '文件夹', value: folderCount.value, icon: 'fa fa-folder', bg: 'bg-primary/10', color: 'text-primary' },
  { label: '文件', value: fileCount.value, icon: 'fa fa-file-o', bg: 'bg-secondary/10', color: 'text-secondary' },
  { label: '已选中', value: props.selectedCount, icon: 'fa fa-check-square-o', bg: 'bg-success/10', color: 'text-success' },
  { label: '当前层级', value: `第 ${Math.max(props.pathDepth, 1)} 层`, icon: 'fa fa-sitemap', bg: 'bg-warning/10', color: 'text-warning' },
])
</script>
