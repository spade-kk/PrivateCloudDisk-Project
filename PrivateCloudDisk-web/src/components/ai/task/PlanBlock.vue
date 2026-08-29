<template>
  <section class="task-block task-plan" :class="{ collapsed }">
    <button class="task-block-heading" type="button" :aria-expanded="!collapsed" @click="$emit('toggle')">
      <i class="fa fa-list-ol" aria-hidden="true"></i><span>执行计划</span>
      <small>{{ completedCount }}/{{ items.length }} 已完成</small><i class="fa fa-angle-down task-chevron" aria-hidden="true"></i>
    </button>
    <div v-show="!collapsed" class="task-block-content">
      <div class="plan-progress" aria-label="计划完成度"><span :style="{ width: `${progress}%` }"></span></div>
      <ol class="plan-items">
        <li v-for="item in items" :key="item.id" :class="`state-${item.status}`">
          <i :class="stateIcon(item.status)" aria-hidden="true"></i>
          <div><strong>{{ item.title }}</strong><small v-if="item.details">{{ item.details }}</small></div>
        </li>
      </ol>
      <p v-if="source === 'request_fallback'" class="plan-fallback">模型未返回可校验的计划 JSON，已按当前请求保留最小执行计划。</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AiPlanItem, AiTaskBlock } from '@/api/aiAgent'

const props = defineProps<{ block: AiTaskBlock; collapsed: boolean }>()
defineEmits<{ toggle: [] }>()
const items = computed<AiPlanItem[]>(() => Array.isArray(props.block.data.plan_items) ? props.block.data.plan_items : [])
const source = computed(() => String(props.block.data.source || 'llm'))
const completedCount = computed(() => items.value.filter((item) => item.status === 'completed').length)
const progress = computed(() => items.value.length ? Math.round((completedCount.value / items.value.length) * 100) : 0)
function stateIcon(state: AiPlanItem['status']) { return ({ pending: 'fa fa-circle-o', running: 'fa fa-circle-o-notch fa-spin', completed: 'fa fa-check-circle', failed: 'fa fa-exclamation-circle', superseded: 'fa fa-minus-circle' } as const)[state] }
</script>

<style scoped>
.task-block{min-width:0}.task-block-heading{display:flex;width:100%;align-items:center;gap:8px;border:0;background:transparent;padding:7px 0;color:inherit;text-align:left}.task-block-heading>i:first-child{width:14px;color:#4f46e5}.task-block-heading>span{font-size:13px;font-weight:650}.task-block-heading small{margin-left:auto;color:var(--ai-task-muted,#64748b);font-size:12px}.task-chevron{margin-left:2px;color:var(--ai-task-muted,#64748b);transition:transform .2s ease}.collapsed .task-chevron{transform:rotate(-90deg)}.task-block-content{padding:3px 4px 10px 22px}.plan-progress{height:4px;overflow:hidden;border-radius:999px;background:rgba(99,102,241,.14)}.plan-progress span{display:block;height:100%;border-radius:inherit;background:#6366f1;transition:width .2s ease}.plan-items{display:grid;gap:8px;margin:10px 0 0;padding:0;list-style:none}.plan-items li{display:flex;gap:8px;align-items:flex-start;color:var(--ai-task-text,#334155)}.plan-items li>i{width:14px;margin-top:3px;color:#94a3b8}.plan-items li.state-running>i{color:#3b82f6}.plan-items li.state-completed>i{color:#16a34a}.plan-items li.state-failed>i{color:#dc2626}.plan-items strong,.plan-items small{display:block}.plan-items strong{font-size:12px;font-weight:650;line-height:1.45}.plan-items small{margin-top:2px;color:var(--ai-task-muted,#64748b);font-size:11px;line-height:1.5}.plan-fallback{margin:9px 0 0;color:var(--ai-task-muted,#64748b);font-size:11px;line-height:1.55}@media (max-width:768px){.task-block-heading{min-height:44px}.task-block-content{padding-left:22px}}
</style>
