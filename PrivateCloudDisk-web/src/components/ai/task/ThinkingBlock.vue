<template>
  <section class="task-block task-thinking" :class="{ collapsed }">
    <button class="task-block-heading" type="button" :aria-expanded="!collapsed" @click="$emit('toggle')">
      <i class="fa fa-lightbulb-o" aria-hidden="true"></i>
      <span>执行思考</span><small>{{ collapsed ? excerpt : '面向用户的执行依据' }}</small>
      <i class="fa fa-angle-down task-chevron" aria-hidden="true"></i>
    </button>
    <div v-show="!collapsed" class="task-block-content task-thinking-copy"><AiMarkdownRenderer :content="text" :streaming="block.status === 'running'" :show-actions="false" /></div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AiTaskBlock } from '@/api/aiAgent'
import AiMarkdownRenderer from '@/components/ai/AiMarkdownRenderer.vue'

const props = defineProps<{ block: AiTaskBlock; collapsed: boolean }>()
defineEmits<{ toggle: [] }>()
const text = computed(() => String(props.block.data.thinking_text || '正在整理可见执行信息…'))
const excerpt = computed(() => text.value.replace(/\s+/g, ' ').slice(0, 72) || '正在整理执行依据…')
</script>

<style scoped>
.task-block{min-width:0}.task-block-heading{display:flex;width:100%;align-items:center;gap:8px;border:0;background:transparent;padding:7px 0;color:inherit;text-align:left}.task-block-heading>i:first-child{width:14px;color:#8b5cf6}.task-block-heading>span{font-size:13px;font-weight:650}.task-block-heading small{overflow:hidden;min-width:0;flex:1;color:var(--ai-task-muted,#64748b);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.task-chevron{margin-left:auto;color:var(--ai-task-muted,#64748b);transition:transform .2s ease}.collapsed .task-chevron{transform:rotate(-90deg)}.task-block-content{padding:1px 4px 8px 22px}.task-thinking-copy{color:var(--ai-task-muted,#64748b);font-size:13px;font-style:italic;line-height:1.7}.task-thinking-copy :deep(p){margin:0}.task-thinking-copy :deep(code){border-radius:4px;background:rgba(139,92,246,.1);padding:1px 4px;font-style:normal}@media (max-width:768px){.task-block-heading{min-height:44px}.task-block-content{padding-left:22px}}
</style>
