<template>
  <section class="task-block task-tool" :class="[`state-${block.status || 'running'}`, { collapsed }]">
    <button class="task-block-heading" type="button" :aria-expanded="!collapsed" @click="emit('toggle')">
      <i :class="statusIcon" aria-hidden="true"></i><span>{{ toolName }}</span>
      <small>{{ statusLabel }}<template v-if="duration"> · {{ duration }}</template></small><i class="fa fa-angle-down task-chevron" aria-hidden="true"></i>
    </button>
    <div v-show="!collapsed" class="task-block-content">
      <div class="tool-command"><code>{{ command }}</code><button type="button" aria-label="复制工具命令" title="复制命令" @click="emit('copy', command)"><i class="fa fa-copy"></i></button></div>
      <details class="tool-detail"><summary>输入参数</summary><pre>{{ formattedInput }}</pre></details>
      <p v-if="message" class="tool-message">{{ message }}</p>
      <p v-if="error" class="tool-error"><i class="fa fa-exclamation-circle"></i>{{ error }}</p>
      <template v-if="isApproval">
        <div class="tool-approval"><span>此操作可能改变企业数据。</span><div><button type="button" @click="emit('approve', true)">确认继续</button><button type="button" class="secondary" @click="emit('approve', false)">拒绝</button></div></div>
      </template>
      <template v-else-if="isFinished">
        <div class="tool-result-heading"><span>结果数据</span><button type="button" aria-label="复制工具结果数据" title="复制结果数据" @click="emit('copy', formattedOutput)"><i class="fa fa-copy"></i></button></div>
        <!-- Only output_data is ever rendered.  Do not substitute a raw tool_result wrapper here. -->
        <pre class="tool-result">{{ formattedOutput }}</pre>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AiTaskBlock } from '@/api/aiAgent'

const props = defineProps<{ block: AiTaskBlock; collapsed: boolean }>()
const emit = defineEmits<{ toggle: []; approve: [approved: boolean]; copy: [value: string] }>()
const toolName = computed(() => String(props.block.data.tool_name || '工具调用'))
const command = computed(() => String(props.block.data.command || `调用 ${toolName.value}`))
const isApproval = computed(() => props.block.status === 'awaiting_approval')
const isFinished = computed(() => props.block.status === 'completed')
const message = computed(() => String(props.block.data.message || ''))
const error = computed(() => String(props.block.data.error || ''))
const duration = computed(() => Number(props.block.data.duration_ms || 0) ? `${Number(props.block.data.duration_ms)} ms` : '')
const formattedInput = computed(() => JSON.stringify(props.block.data.input || {}, null, 2))
const formattedOutput = computed(() => JSON.stringify(props.block.data.output_data || {}, null, 2))
const statusLabel = computed(() => ({ running: '正在执行', completed: '已完成', failed: '执行失败', awaiting_approval: '等待确认' }[String(props.block.status)] || '已记录'))
const statusIcon = computed(() => ({ running: 'fa fa-circle-o-notch fa-spin', completed: 'fa fa-check-circle', failed: 'fa fa-exclamation-circle', awaiting_approval: 'fa fa-shield' }[String(props.block.status)] || 'fa fa-cog'))
</script>

<style scoped>
.task-block{min-width:0}.task-block-heading{display:flex;width:100%;align-items:center;gap:8px;border:0;background:transparent;padding:7px 0;color:inherit;text-align:left}.task-block-heading>i:first-child{width:14px;color:#3b82f6}.task-block.state-completed .task-block-heading>i:first-child{color:#16a34a}.task-block.state-failed .task-block-heading>i:first-child{color:#dc2626}.task-block.state-awaiting_approval .task-block-heading>i:first-child{color:#d97706}.task-block-heading>span{font-size:13px;font-weight:650}.task-block-heading small{overflow:hidden;min-width:0;flex:1;color:var(--ai-task-muted,#64748b);font-size:12px;text-align:right;text-overflow:ellipsis;white-space:nowrap}.task-chevron{margin-left:2px;color:var(--ai-task-muted,#64748b);transition:transform .2s ease}.collapsed .task-chevron{transform:rotate(-90deg)}.task-block-content{padding:2px 4px 11px 22px}.tool-command,.tool-result-heading{display:flex;min-width:0;align-items:center;gap:7px}.tool-command{border-radius:7px;background:var(--ai-task-code-bg,#f1f5f9);padding:7px 8px}.tool-command code{overflow:auto;min-width:0;flex:1;color:var(--ai-task-text,#334155);font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:11px;white-space:nowrap}.tool-command button,.tool-result-heading button{border:0;background:transparent;color:var(--ai-task-muted,#64748b);padding:3px 4px}.tool-detail{margin-top:7px;color:var(--ai-task-muted,#64748b);font-size:11px}.tool-detail summary{cursor:pointer}.tool-detail pre,.tool-result{overflow:auto;max-height:260px;margin:6px 0 0;border-radius:8px;background:var(--ai-task-code-bg,#f1f5f9);padding:9px;color:var(--ai-task-text,#334155);font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:11px;line-height:1.55;white-space:pre}.tool-result-heading{justify-content:space-between;margin-top:9px;color:var(--ai-task-muted,#64748b);font-size:11px}.tool-message{margin:9px 0 0;color:var(--ai-task-muted,#64748b);font-size:12px}.tool-error{margin:9px 0 0;color:#dc2626;font-size:12px;line-height:1.55}.tool-error i{margin-right:5px}.tool-approval{display:flex;align-items:center;justify-content:space-between;gap:9px;margin-top:10px;border-radius:8px;background:rgba(245,158,11,.1);padding:8px 9px;color:#92400e;font-size:11px}.tool-approval>div{display:flex;gap:5px}.tool-approval button{border:0;border-radius:6px;background:#2563eb;padding:5px 8px;color:#fff;font-size:11px}.tool-approval button.secondary{background:rgba(100,116,139,.16);color:#475569}.dark .tool-approval{color:#fcd34d}@media (max-width:768px){.task-block-heading{min-height:44px}.task-block-content{padding-left:22px}.tool-approval{align-items:flex-start;flex-direction:column}.tool-detail pre,.tool-result{max-height:200px}}
</style>
