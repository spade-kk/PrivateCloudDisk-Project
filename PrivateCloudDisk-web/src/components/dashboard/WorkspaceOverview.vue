<template>
  <section class="workspace-strip">
    <div class="workspace-main">
      <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
        <i class="fa fa-briefcase"></i>
      </div>
      <div class="min-w-0">
        <div class="flex flex-wrap items-center gap-2">
          <h2 class="truncate text-sm font-semibold text-neutral-700">工作区</h2>
          <span class="inline-flex items-center gap-1 rounded-full bg-success/10 px-2 py-0.5 text-[11px] font-medium text-success">
            <span class="h-1.5 w-1.5 rounded-full bg-success"></span>
            运行正常
          </span>
        </div>
        <p class="mt-1 truncate text-xs text-neutral-400">{{ overviewText }}</p>
      </div>
    </div>

    <div class="workspace-metrics" aria-label="目录统计">
      <div v-for="item in metrics" :key="item.label" class="metric-pill">
        <i :class="[item.icon, item.color]"></i>
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  selectedCount: { type: Number, default: 0 },
  pathDepth: { type: Number, default: 1 },
})

const folderCount = computed(() => props.nodes.filter(node => node.node_type === 'FOLDER').length)
const fileCount = computed(() => props.nodes.filter(node => node.node_type === 'FILE').length)
const totalCount = computed(() => folderCount.value + fileCount.value)
const overviewText = computed(() => {
  const selectedText = props.selectedCount > 0 ? `，已选中 ${props.selectedCount} 项` : ''
  return `当前目录共 ${totalCount.value} 项内容，位于第 ${Math.max(props.pathDepth, 1)} 层${selectedText}`
})

const metrics = computed(() => [
  { label: '文件夹', value: folderCount.value, icon: 'fa fa-folder', color: 'text-primary' },
  { label: '文件', value: fileCount.value, icon: 'fa fa-file-o', color: 'text-secondary' },
  { label: '已选', value: props.selectedCount, icon: 'fa fa-check-square-o', color: 'text-success' },
  { label: '层级', value: Math.max(props.pathDepth, 1), icon: 'fa fa-sitemap', color: 'text-warning' },
])
</script>

<style scoped>
.workspace-strip {
  display: grid;
  min-height: 0;
  align-items: center;
  grid-template-columns: minmax(0, 1fr);
  gap: 18px;
  overflow: hidden;
  border: 1px solid rgba(228, 231, 237, 0.9);
  border-radius: 12px;
  background:
    linear-gradient(135deg, rgba(22, 93, 255, 0.07), rgba(54, 207, 201, 0.04) 45%, rgba(255, 255, 255, 0.94)),
    #fff;
  padding: 14px;
  box-shadow: 0 10px 30px rgba(31, 41, 55, 0.04);
}

.workspace-main {
  display: flex;
  min-width: 220px;
  align-items: center;
  gap: 12px;
}

.workspace-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.metric-pill {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 7px;
  border: 1px solid rgba(228, 231, 237, 0.78);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.75);
  padding: 8px 9px;
  font-size: 12px;
  line-height: 1;
}

.metric-pill span {
  min-width: 0;
  overflow: hidden;
  color: #909399;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-pill strong {
  color: #303133;
  font-weight: 700;
}

@media (max-width: 768px) {
  .workspace-strip {
    gap: 12px;
  }
}
</style>
