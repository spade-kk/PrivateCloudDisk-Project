<template>
  <section class="task-block task-context" :class="{ collapsed }">
    <button class="task-block-heading" type="button" :aria-expanded="!collapsed" @click="$emit('toggle')">
      <i class="fa fa-folder-open-o" aria-hidden="true"></i><span>上下文收集</span>
      <small>{{ collapsed ? `${items.length} 项已收集` : summary }}</small><i class="fa fa-angle-down task-chevron" aria-hidden="true"></i>
    </button>
    <div v-show="!collapsed" class="task-block-content">
      <p v-if="summary" class="context-summary">{{ summary }}</p>
      <ul v-if="items.length" class="context-items">
        <li v-for="(item, index) in items" :key="`${index}-${item.summary || item.type || 'context'}`"><i class="fa fa-check" aria-hidden="true"></i><span>{{ item.summary || item.type || '已收集上下文' }}</span></li>
      </ul>
      <p v-else class="context-empty">正在等待可用上下文…</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AiTaskBlock } from '@/api/aiAgent'

const props = defineProps<{ block: AiTaskBlock; collapsed: boolean }>()
defineEmits<{ toggle: [] }>()
const summary = computed(() => String(props.block.data.context_summary || '正在收集任务所需的受限上下文。'))
const items = computed<Array<Record<string, any>>>(() => Array.isArray(props.block.data.items) ? props.block.data.items : [])
</script>

<style scoped>
.task-block{min-width:0}.task-block-heading{display:flex;width:100%;align-items:center;gap:8px;border:0;background:transparent;padding:7px 0;color:inherit;text-align:left}.task-block-heading>i:first-child{width:14px;color:#0ea5e9}.task-block-heading>span{font-size:13px;font-weight:650}.task-block-heading small{overflow:hidden;min-width:0;flex:1;color:var(--ai-task-muted,#64748b);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.task-chevron{margin-left:auto;color:var(--ai-task-muted,#64748b);transition:transform .2s ease}.collapsed .task-chevron{transform:rotate(-90deg)}.task-block-content{padding:1px 4px 8px 22px}.context-summary,.context-empty{margin:0;color:var(--ai-task-muted,#64748b);font-size:12px;line-height:1.6}.context-items{display:grid;gap:5px;margin:7px 0 0;padding:0;list-style:none}.context-items li{display:flex;gap:7px;align-items:flex-start;color:var(--ai-task-text,#334155);font-size:12px;line-height:1.55}.context-items i{margin-top:3px;color:#10b981;font-size:10px}@media (max-width:768px){.task-block-heading{min-height:44px}.task-block-content{padding-left:22px}}
</style>
