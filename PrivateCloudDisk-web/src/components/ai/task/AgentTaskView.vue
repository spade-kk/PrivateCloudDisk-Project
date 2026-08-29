<template>
  <article class="agent-task-document" :data-task-status="task.status" aria-label="AI Agent 任务执行过程">
    <header class="task-document-header">
      <div><span class="task-kicker">AI Agent 任务</span><h2>{{ task.user_request || '正在处理任务' }}</h2><p><span class="task-status" :class="`state-${task.status}`">{{ statusLabel }}</span><span>{{ task.model }}</span><span v-if="task.total_duration_ms">{{ task.total_duration_ms }} ms</span></p></div>
      <div class="task-document-actions"><button type="button" title="展开全部（Ctrl+E）" aria-label="展开全部" @click="taskStore.expandAll(task.task_id)"><i class="fa fa-angle-double-down"></i></button><button type="button" title="折叠全部（Ctrl+Shift+E）" aria-label="折叠全部" @click="taskStore.collapseAll(task.task_id)"><i class="fa fa-angle-double-up"></i></button><button type="button" title="导出 Markdown" aria-label="导出 Markdown" @click="emit('export', 'markdown')"><i class="fa fa-download"></i></button></div>
    </header>

    <div class="task-block-list">
      <component
        :is="componentFor(block.type)"
        v-for="block in blocks"
        :key="block.id"
        :block="block"
        :collapsed="taskStore.isCollapsed(task.task_id, block)"
        @toggle="taskStore.toggleCollapse(task.task_id, block.id)"
        @copy="emit('copy', $event)"
        @approve="approve(block, $event)"
      />
    </div>
    <p v-if="task.status === 'running' && !blocks.length" class="task-empty"><i class="fa fa-circle-o-notch fa-spin"></i> 正在建立任务执行视图…</p>
    <p v-if="task.status === 'failed'" class="task-terminal task-failed"><i class="fa fa-exclamation-circle"></i> 任务未完成。请检查失败块中的原因并重试或缩小范围。</p>
    <p v-if="task.status === 'cancelled'" class="task-terminal"><i class="fa fa-stop-circle"></i> 任务已停止；已完成的只读步骤仍保留在上方。</p>
    <footer v-if="task.status === 'completed'" class="task-response-actions">
      <button type="button" title="复制完整任务回复" aria-label="复制完整任务回复" @click="copyResponse"><i class="fa fa-copy" aria-hidden="true"></i><span>复制回复</span></button>
    </footer>
  </article>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import type { AiTaskBlock, AiTaskBlockType, AiTaskSnapshot } from '@/api/aiAgent'
import { useAgentTaskStore } from '@/stores/agentTaskStore'
import ThinkingBlock from './ThinkingBlock.vue'
import ContextBlock from './ContextBlock.vue'
import PlanBlock from './PlanBlock.vue'
import ToolCallBlock from './ToolCallBlock.vue'
import OutputBlock from './OutputBlock.vue'
import SummaryBlock from './SummaryBlock.vue'

const props = defineProps<{ task: AiTaskSnapshot }>()
const emit = defineEmits<{ copy: [value: string]; approve: [payload: { taskId: string; blockId: string; approved: boolean; approvalToken: string }]; export: [format: 'markdown' | 'json'] }>()
const taskStore = useAgentTaskStore()
const blocks = computed(() => [...props.task.blocks].sort((left, right) => left.order - right.order || left.started_at.localeCompare(right.started_at)))
const statusLabel = computed(() => ({ running: '执行中', paused: '等待确认', completed: '已完成', failed: '未完成', cancelled: '已取消' }[props.task.status]))
const componentMap: Record<AiTaskBlockType, unknown> = { thinking: ThinkingBlock, context: ContextBlock, plan: PlanBlock, tool_call: ToolCallBlock, output: OutputBlock, summary: SummaryBlock }
function componentFor(type: AiTaskBlockType) { return componentMap[type] }
function approve(block: AiTaskBlock, approved: boolean) {
  const approvalToken = String(block.data.approval_token || '')
  if (!approvalToken) return
  emit('approve', { taskId: props.task.task_id, blockId: block.id, approved, approvalToken })
}
function copyResponse() {
  const content = taskStore.exportTask(props.task.task_id, 'markdown')
  if (content) emit('copy', content)
}
function keyboardHandler(event: KeyboardEvent) {
  if (!event.ctrlKey || event.key.toLowerCase() !== 'e') return
  event.preventDefault()
  if (event.shiftKey) taskStore.collapseAll(props.task.task_id)
  else taskStore.expandAll(props.task.task_id)
}
onMounted(() => window.addEventListener('keydown', keyboardHandler))
onBeforeUnmount(() => window.removeEventListener('keydown', keyboardHandler))
</script>

<style scoped>
.agent-task-document{--ai-task-muted:#64748b;--ai-task-text:#334155;--ai-task-code-bg:#f1f5f9;--ai-task-code-text:#e2e8f0;max-width:870px;margin:2px 0 18px;padding:15px 17px 16px;border-radius:13px;background:color-mix(in srgb,var(--el-fill-color-light,#f8fafc) 76%,transparent);color:var(--ai-task-text)}.dark .agent-task-document{--ai-task-muted:#94a3b8;--ai-task-text:#dbeafe;--ai-task-code-bg:#111827;--ai-task-code-text:#e2e8f0;background:rgba(15,23,42,.48)}.task-document-header{display:flex;align-items:flex-start;justify-content:space-between;gap:14px;padding-bottom:7px}.task-kicker{display:block;color:var(--ai-task-muted);font-size:10px;font-weight:700;letter-spacing:.08em;text-transform:uppercase}.task-document-header h2{margin:3px 0 5px;font-size:14px;line-height:1.5}.task-document-header p{display:flex;flex-wrap:wrap;gap:6px;margin:0;color:var(--ai-task-muted);font-size:11px}.task-document-header p>span+span::before{margin-right:6px;color:rgba(100,116,139,.55);content:'·'}.task-status{font-weight:650}.task-status.state-running{color:#2563eb}.task-status.state-paused{color:#d97706}.task-status.state-completed{color:#16a34a}.task-status.state-failed{color:#dc2626}.task-status.state-cancelled{color:#64748b}.task-document-actions{display:flex;gap:2px}.task-document-actions button{display:grid;width:29px;height:29px;place-items:center;border:0;border-radius:7px;background:transparent;color:var(--ai-task-muted);transition:background .18s,color .18s}.task-document-actions button:hover{background:rgba(100,116,139,.12);color:#2563eb}.task-block-list{display:grid;gap:2px}.task-empty,.task-terminal{margin:10px 0 0;color:var(--ai-task-muted);font-size:12px;line-height:1.6}.task-empty i{color:#3b82f6}.task-failed{color:#dc2626}.task-terminal i{margin-right:5px}.task-response-actions{display:flex;margin-top:10px;padding-top:9px;border-top:1px solid rgba(148,163,184,.18)}.task-response-actions button{display:inline-flex;align-items:center;gap:6px;border:0;border-radius:6px;background:transparent;padding:5px 7px;color:var(--ai-task-muted);font-size:11px}.task-response-actions button:hover{background:rgba(100,116,139,.12);color:#2563eb}@media (max-width:768px){.agent-task-document{margin-bottom:14px;padding:12px 13px;border-radius:12px}.task-document-header h2{font-size:13px}.task-document-actions button{width:38px;height:38px}.task-document-header{gap:8px}}
@media (prefers-reduced-motion:reduce){.agent-task-document *{transition:none!important}}
</style>
