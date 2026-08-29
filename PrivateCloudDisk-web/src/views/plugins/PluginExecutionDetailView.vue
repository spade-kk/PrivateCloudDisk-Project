<template>
  <main class="execution-detail-workspace">
    <header class="execution-detail-workspace__header">
      <nav aria-label="面包屑"><router-link to="/app/plugins">插件中心</router-link><i class="fa fa-angle-right"></i><router-link :to="`/app/plugins/${pluginId}/executions`">{{ pluginId }}</router-link><i class="fa fa-angle-right"></i><span>{{ executionId }}</span></nav>
      <router-link class="execution-back" :to="`/app/plugins/${pluginId}/executions`"><i class="fa fa-arrow-left"></i> 返回执行记录</router-link>
    </header>
    <PluginExecutionDetailPanel :execution-id="executionId" mode="page" />
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PluginExecutionDetailPanel from '@/components/plugins/execution/PluginExecutionDetailPanel.vue'

const route = useRoute()
const pluginId = computed(() => String(route.params.pluginId || ''))
const executionId = computed(() => String(route.params.executionId || ''))
</script>

<style scoped>
.execution-detail-workspace { display: grid; height: 100dvh; grid-template-rows: auto minmax(0, 1fr); background: #edf1f7; }.execution-detail-workspace__header { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: .75rem clamp(.75rem, 2vw, 1.5rem); border-bottom: 1px solid #dce3ed; background: #fff; }.execution-detail-workspace__header nav { display: flex; min-width: 0; align-items: center; gap: .5rem; overflow: hidden; color: #7b899f; font-size: 12px; }.execution-detail-workspace__header a { overflow: hidden; color: #476caa; text-overflow: ellipsis; text-decoration: none; white-space: nowrap; }.execution-detail-workspace__header span { overflow: hidden; max-width: 320px; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; text-overflow: ellipsis; white-space: nowrap; }.execution-back { flex: 0 0 auto; border: 1px solid #d9e1ec; border-radius: 8px; padding: .45rem .65rem; font-size: 12px; }.execution-detail-workspace :deep(.execution-detail-panel) { margin: .8rem; border: 1px solid #dce3ed; border-radius: 12px; box-shadow: 0 8px 28px rgb(47 68 101 / .08); }
:global(.dark) .execution-detail-workspace { background: #0f1520; }.dark .execution-detail-workspace__header { border-color: #293445; background: #141b27; }.dark .execution-detail-workspace :deep(.execution-detail-panel) { border-color: #2d3a4e; }
@media (max-width: 768px) { .execution-detail-workspace__header { padding: .55rem .7rem; }.execution-detail-workspace__header nav { display: none; }.execution-detail-workspace :deep(.execution-detail-panel) { margin: 0; border: 0; border-radius: 0; box-shadow: none; } }
</style>
