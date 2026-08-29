<template>
  <section class="task-block task-output" :class="{ collapsed }">
    <button class="task-block-heading" type="button" :aria-expanded="!collapsed" @click="emit('toggle')">
      <i class="fa fa-file-text-o" aria-hidden="true"></i><span>阶段输出</span><small>{{ collapsed ? excerpt : '基于已验证信息生成' }}</small><i class="fa fa-angle-down task-chevron" aria-hidden="true"></i>
    </button>
    <div v-show="!collapsed" class="task-block-content"><div class="task-markdown"><AiMarkdownRenderer :content="text" :streaming="block.status === 'running'" :show-actions="false" @copy="emit('copy', $event)" /></div><button class="copy-block" type="button" title="复制阶段输出" aria-label="复制阶段输出" @click="emit('copy', text)"><i class="fa fa-copy"></i></button></div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AiTaskBlock } from '@/api/aiAgent'
import AiMarkdownRenderer from '@/components/ai/AiMarkdownRenderer.vue'

const props = defineProps<{ block: AiTaskBlock; collapsed: boolean }>()
const emit = defineEmits<{ toggle: []; copy: [value: string] }>()
const text = computed(() => String(props.block.data.output_text || ''))
const excerpt = computed(() => text.value.replace(/\s+/g, ' ').slice(0, 72) || '正在生成阶段输出…')
</script>

<style scoped>
.task-block{min-width:0}.task-block-heading{display:flex;width:100%;align-items:center;gap:8px;border:0;background:transparent;padding:7px 0;color:inherit;text-align:left}.task-block-heading>i:first-child{width:14px;color:#0284c7}.task-block-heading>span{font-size:13px;font-weight:650}.task-block-heading small{overflow:hidden;min-width:0;flex:1;color:var(--ai-task-muted,#64748b);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.task-chevron{margin-left:2px;color:var(--ai-task-muted,#64748b);transition:transform .2s ease}.collapsed .task-chevron{transform:rotate(-90deg)}.task-block-content{position:relative;padding:2px 28px 12px 22px}.task-markdown{color:var(--ai-task-text,#334155);font-size:13px;line-height:1.75;overflow-wrap:anywhere}.task-markdown :deep(h1),.task-markdown :deep(h2),.task-markdown :deep(h3){margin:13px 0 6px;line-height:1.35}.task-markdown :deep(h1){font-size:19px}.task-markdown :deep(h2){font-size:16px}.task-markdown :deep(h3){font-size:14px}.task-markdown :deep(pre){overflow:auto;border-radius:8px;background:var(--ai-task-code-bg,#0f172a);padding:10px;color:var(--ai-task-code-text,#e2e8f0)}.task-markdown :deep(code){border-radius:4px;background:rgba(148,163,184,.15);padding:1px 4px;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:.88em}.copy-block{position:absolute;right:2px;top:2px;border:0;background:transparent;color:var(--ai-task-muted,#64748b);padding:5px}@media (max-width:768px){.task-block-heading{min-height:44px}.task-block-content{padding-left:22px}}
</style>
