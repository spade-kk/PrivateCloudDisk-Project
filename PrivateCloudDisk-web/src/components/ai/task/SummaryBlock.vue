<template>
  <section class="task-block task-summary" :class="{ collapsed }">
    <button class="task-block-heading" type="button" :aria-expanded="!collapsed" @click="emit('toggle')">
      <i class="fa fa-check-circle" aria-hidden="true"></i><span>任务总结</span><small>{{ collapsed ? excerpt : '最终可交付结果' }}</small><i class="fa fa-angle-down task-chevron" aria-hidden="true"></i>
    </button>
    <div v-show="!collapsed" class="task-block-content"><div class="task-markdown"><AiMarkdownRenderer :content="text" :show-actions="false" @copy="emit('copy', $event)" /></div><button class="copy-block" type="button" title="复制任务总结" aria-label="复制任务总结" @click="emit('copy', text)"><i class="fa fa-copy"></i></button></div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AiTaskBlock } from '@/api/aiAgent'
import AiMarkdownRenderer from '@/components/ai/AiMarkdownRenderer.vue'

const props = defineProps<{ block: AiTaskBlock; collapsed: boolean }>()
const emit = defineEmits<{ toggle: []; copy: [value: string] }>()
const text = computed(() => String(props.block.data.summary_text || '任务已结束。'))
const excerpt = computed(() => text.value.replace(/\s+/g, ' ').slice(0, 72))
</script>

<style scoped>
.task-block{min-width:0}.task-block-heading{display:flex;width:100%;align-items:center;gap:8px;border:0;background:transparent;padding:9px 0;color:inherit;text-align:left}.task-block-heading>i:first-child{width:14px;color:#16a34a}.task-block-heading>span{font-size:14px;font-weight:700}.task-block-heading small{overflow:hidden;min-width:0;flex:1;color:var(--ai-task-muted,#64748b);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.task-chevron{margin-left:2px;color:var(--ai-task-muted,#64748b);transition:transform .2s ease}.collapsed .task-chevron{transform:rotate(-90deg)}.task-block-content{position:relative;padding:3px 28px 14px 22px}.task-markdown{color:var(--ai-task-text,#334155);font-size:14px;line-height:1.78;overflow-wrap:anywhere}.task-markdown :deep(h1),.task-markdown :deep(h2),.task-markdown :deep(h3){margin:14px 0 7px;line-height:1.35}.task-markdown :deep(h1){font-size:20px}.task-markdown :deep(h2){font-size:17px}.task-markdown :deep(h3){font-size:15px}.task-markdown :deep(pre){overflow:auto;border-radius:8px;background:var(--ai-task-code-bg,#0f172a);padding:11px;color:var(--ai-task-code-text,#e2e8f0)}.task-markdown :deep(code){border-radius:4px;background:rgba(148,163,184,.15);padding:1px 4px;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:.88em}.copy-block{position:absolute;right:2px;top:4px;border:0;background:transparent;color:var(--ai-task-muted,#64748b);padding:5px}@media (max-width:768px){.task-block-heading{min-height:44px}.task-block-content{padding-left:22px}}
</style>
