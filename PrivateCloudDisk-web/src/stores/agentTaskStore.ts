// ============================================================================
// agentTaskStore.ts — Structured Cloud AI Agent task execution projection
// ============================================================================
// [AI-AGENT-TASK-006] Keeps a local, presentation-only projection of the
// server-authoritative Agent task document.  It never derives tool/plan state from
// model text and never replays an Agent POST after a reconnect.
// ============================================================================

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getAiTaskSnapshot,
  type AiPlanItem,
  type AiStreamEvent,
  type AiTaskBlock,
  type AiTaskBlockType,
  type AiTaskSnapshot,
  type AiTaskStatus,
} from '@/api/aiAgent'

const COLLAPSE_STORAGE_KEY = 'pcd.ai.task.collapsed.v2'
const MAX_EXPANDED_BLOCKS = 60

function now(): string { return new Date().toISOString() }

function defaultCollapsed(type: AiTaskBlockType): boolean {
  return type === 'thinking' || type === 'context' || type === 'tool_call'
}

function readCollapsedMap(): Record<string, boolean> {
  try {
    const parsed = JSON.parse(localStorage.getItem(COLLAPSE_STORAGE_KEY) || '{}')
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch { return {} }
}

function normalizeBlocks(blocks: AiTaskBlock[]): AiTaskBlock[] {
  return [...blocks].sort((left, right) => left.order - right.order || left.started_at.localeCompare(right.started_at))
}

function newTask(data: Record<string, any>): AiTaskSnapshot {
  const taskId = String(data.task_id || data.run_id)
  return {
    schema_version: Number(data.schema_version || 2), task_id: taskId,
    conversation_id: String(data.conversation_id || ''), user_request: String(data.user_request || ''),
    model: String(data.model || ''), status: 'running', started_at: String(data.timestamp || now()), blocks: [],
  }
}

export const useAgentTaskStore = defineStore('agentTask', () => {
  const tasks = ref<Record<string, AiTaskSnapshot>>({})
  const currentTaskId = ref<string | null>(null)
  const collapsedMap = ref<Record<string, boolean>>(readCollapsedMap())
  const streamBuffer = ref<Record<string, string>>({})
  const seenEventIds = new Set<string>()

  const currentTask = computed(() => currentTaskId.value ? tasks.value[currentTaskId.value] || null : null)
  const visibleBlocks = computed(() => currentTask.value ? normalizeBlocks(currentTask.value.blocks) : [])
  const planItems = computed<AiPlanItem[]>(() => {
    const block = visibleBlocks.value.find((item) => item.type === 'plan')
    return Array.isArray(block?.data.plan_items) ? block!.data.plan_items as AiPlanItem[] : []
  })
  const completedPlanCount = computed(() => planItems.value.filter((item) => item.status === 'completed').length)
  const activeToolCalls = computed(() => visibleBlocks.value.filter((item) => item.type === 'tool_call' && ['running', 'awaiting_approval'].includes(String(item.status))))
  const summaryBlock = computed(() => visibleBlocks.value.find((item) => item.type === 'summary') || null)

  function persistCollapsed() {
    try { localStorage.setItem(COLLAPSE_STORAGE_KEY, JSON.stringify(collapsedMap.value)) } catch { /* non-essential preference */ }
  }

  function blockKey(taskId: string, blockId: string): string { return `${taskId}:${blockId}` }

  function isCollapsed(taskId: string, block: AiTaskBlock): boolean {
    return collapsedMap.value[blockKey(taskId, block.id)] ?? defaultCollapsed(block.type)
  }

  function toggleCollapse(taskId: string, blockId: string, collapsed?: boolean) {
    const task = tasks.value[taskId]
    const block = task?.blocks.find((item) => item.id === blockId)
    if (!block) return
    const key = blockKey(taskId, blockId)
    collapsedMap.value[key] = collapsed ?? !isCollapsed(taskId, block)
    persistCollapsed()
  }

  function collapseAll(taskId = currentTaskId.value) {
    const task = taskId ? tasks.value[taskId] : null
    if (!task || !taskId) return
    for (const block of task.blocks) collapsedMap.value[blockKey(taskId, block.id)] = true
    persistCollapsed()
  }

  function expandAll(taskId = currentTaskId.value) {
    const task = taskId ? tasks.value[taskId] : null
    if (!task || !taskId) return
    for (const block of task.blocks) collapsedMap.value[blockKey(taskId, block.id)] = false
    persistCollapsed()
  }

  function setTask(snapshot: AiTaskSnapshot, makeCurrent = false) {
    tasks.value = { ...tasks.value, [snapshot.task_id]: { ...snapshot, blocks: normalizeBlocks(snapshot.blocks) } }
    if (makeCurrent) currentTaskId.value = snapshot.task_id
    enforceBlockBudget(snapshot.task_id)
  }

  function ensureTask(data: Record<string, any>): AiTaskSnapshot {
    const taskId = String(data.task_id || data.run_id || '')
    if (!taskId) throw new Error('AI 任务事件缺少 task_id')
    if (!tasks.value[taskId]) setTask(newTask({ ...data, task_id: taskId }), true)
    currentTaskId.value ||= taskId
    return tasks.value[taskId]
  }

  function upsertBlock(task: AiTaskSnapshot, input: Omit<AiTaskBlock, 'order' | 'started_at'> & Partial<Pick<AiTaskBlock, 'order' | 'started_at'>>) {
    const existing = task.blocks.find((block) => block.id === input.id)
    if (existing) {
      existing.status = input.status ?? existing.status
      existing.parent_id = input.parent_id ?? existing.parent_id
      existing.ended_at = input.ended_at ?? existing.ended_at
      existing.data = { ...existing.data, ...input.data }
      return existing
    }
    const block: AiTaskBlock = {
      id: input.id, type: input.type, order: input.order ?? task.blocks.length,
      parent_id: input.parent_id, status: input.status, data: input.data,
      started_at: input.started_at || now(), ended_at: input.ended_at,
    }
    task.blocks.push(block)
    return block
  }

  function enforceBlockBudget(taskId: string) {
    const task = tasks.value[taskId]
    if (!task || task.blocks.length <= MAX_EXPANDED_BLOCKS) return
    for (const block of normalizeBlocks(task.blocks).slice(0, task.blocks.length - MAX_EXPANDED_BLOCKS)) {
      collapsedMap.value[blockKey(taskId, block.id)] = true
    }
    persistCollapsed()
  }

  function appendText(block: AiTaskBlock, key: string, value: unknown, maximum = 64_000) {
    block.data[key] = `${String(block.data[key] || '')}${String(value || '')}`.slice(0, maximum)
  }

  /** Apply an idempotent SSE event; events are already tenant-authorized by the API. */
  function handleSSEEvent(event: AiStreamEvent) {
    const eventId = event.id || `${event.data.run_id || event.data.task_id}:${event.data.sequence || event.event}`
    if (seenEventIds.has(eventId)) return
    seenEventIds.add(eventId)
    const task = ensureTask(event.data)
    const taskId = task.task_id
    currentTaskId.value = taskId

    switch (event.event) {
      case 'agent_task_start':
        task.user_request = String(event.data.user_request || task.user_request)
        task.model = String(event.data.model || task.model)
        task.status = 'running'
        break
      case 'thinking_start':
        upsertBlock(task, { id: String(event.data.block_id), type: 'thinking', status: 'running', data: { thinking_text: '' } })
        toggleCollapse(taskId, String(event.data.block_id), false)
        break
      case 'thinking_delta': {
        const block = upsertBlock(task, { id: String(event.data.block_id), type: 'thinking', status: 'running', data: { thinking_text: '' } })
        appendText(block, 'thinking_text', event.data.delta, 16_000)
        break
      }
      case 'thinking_end': {
        const block = task.blocks.find((item) => item.id === String(event.data.block_id))
        if (block) { block.status = 'completed'; block.ended_at = now(); toggleCollapse(taskId, block.id, true) }
        break
      }
      case 'context_start':
        upsertBlock(task, { id: String(event.data.block_id), type: 'context', status: 'running', data: { context_summary: event.data.context_summary || '', items: [] } })
        break
      case 'context_item': {
        const block = upsertBlock(task, { id: String(event.data.block_id), type: 'context', status: 'running', data: { items: [] } })
        const items = Array.isArray(block.data.items) ? block.data.items : (block.data.items = [])
        items.push(event.data.item || {})
        break
      }
      case 'context_end': {
        const block = task.blocks.find((item) => item.id === String(event.data.block_id))
        if (block) { block.status = 'completed'; block.ended_at = now() }
        break
      }
      case 'plan_created':
        upsertBlock(task, { id: String(event.data.block_id), type: 'plan', status: 'running', data: { plan_items: event.data.plan_items || [], source: event.data.source || 'llm' } })
        toggleCollapse(taskId, String(event.data.block_id), false)
        break
      case 'plan_item_update': {
        const plan = task.blocks.find((item) => item.id === String(event.data.block_id))
        const item = Array.isArray(plan?.data.plan_items) ? plan?.data.plan_items.find((candidate: AiPlanItem) => candidate.id === event.data.plan_item_id) : null
        if (item) { item.status = event.data.status || item.status; if (event.data.details) item.details = event.data.details }
        break
      }
      case 'tool_call_start':
        task.status = 'running'
        upsertBlock(task, {
          id: String(event.data.block_id), type: 'tool_call', status: 'running', parent_id: event.data.parent_id,
          data: { call_id: event.data.call_id, tool_name: event.data.tool_name, command: event.data.command, input: event.data.input || {} },
        })
        break
      case 'tool_call_end': {
        const block = upsertBlock(task, { id: String(event.data.block_id), type: 'tool_call', status: String(event.data.status || 'completed'), data: {} })
        block.status = String(event.data.status || 'completed')
        // Requirement: result rendering reads only output_data — never an outer result envelope.
        block.data.output_data = event.data.output_data || {}
        block.data.duration_ms = event.data.duration_ms || 0
        if (event.data.approval_token) block.data.approval_token = event.data.approval_token
        if (event.data.message) block.data.message = event.data.message
        block.ended_at = now()
        if (block.status === 'awaiting_approval') task.status = 'paused'
        break
      }
      case 'tool_call_error': {
        const block = upsertBlock(task, { id: String(event.data.block_id), type: 'tool_call', status: 'failed', data: {} })
        block.status = 'failed'; block.data.error = event.data.message || '工具调用失败'; block.data.duration_ms = event.data.duration_ms || 0; block.ended_at = now()
        break
      }
      case 'output': {
        const block = upsertBlock(task, { id: String(event.data.block_id), type: 'output', status: 'running', data: { output_text: '', format: event.data.format || 'markdown' } })
        // The V2 contract marks a chunk with delta=true and carries its text in
        // output_text. Older providers sometimes put the actual chunk in delta;
        // accept both shapes so streamed Markdown is never silently dropped.
        if (event.data.delta) appendText(block, 'output_text', typeof event.data.delta === 'string' ? event.data.delta : event.data.output_text)
        else block.data.output_text = String(event.data.output_text || '')
        streamBuffer.value[taskId] = String(block.data.output_text || '')
        break
      }
      case 'summary':
        upsertBlock(task, { id: String(event.data.block_id), type: 'summary', status: 'completed', data: { summary_text: event.data.summary_text || '', format: event.data.format || 'markdown' }, ended_at: now() })
        break
      case 'task_completed':
        task.status = 'completed'; task.ended_at = now(); break
      case 'task_failed':
        task.status = 'failed'; task.ended_at = now(); break
      case 'task_cancelled':
        task.status = 'cancelled'; task.ended_at = now(); break
      // Compatibility-only mapping; new browser surfaces do not derive process state
      // from model text.  It helps an in-flight older server fail safely during rollout.
      case 'error':
        task.status = 'failed'; break
      default:
        return
    }
    task.blocks = normalizeBlocks(task.blocks)
    enforceBlockBudget(taskId)
  }

  async function restoreTask(taskId: string) {
    const snapshot = await getAiTaskSnapshot(taskId)
    setTask(snapshot, true)
    return snapshot
  }

  function reset(taskId?: string) {
    if (!taskId) { tasks.value = {}; currentTaskId.value = null; streamBuffer.value = {}; return }
    const next = { ...tasks.value }; delete next[taskId]; tasks.value = next
    if (currentTaskId.value === taskId) currentTaskId.value = null
  }

  function exportTask(taskId = currentTaskId.value, format: 'json' | 'markdown' = 'markdown'): string {
    const task = taskId ? tasks.value[taskId] : null
    if (!task) return ''
    if (format === 'json') return JSON.stringify(task, null, 2)
    const lines = [`# ${task.user_request || 'AI Agent 任务'}`, '', `- 状态：${task.status}`, `- 模型：${task.model}`, '']
    for (const block of normalizeBlocks(task.blocks)) {
      if (block.type === 'plan') lines.push('## 计划', ...(block.data.plan_items || []).map((item: AiPlanItem) => `- [${item.status}] ${item.title}`), '')
      else if (block.type === 'tool_call') lines.push(`## 工具：${block.data.tool_name || '未知工具'}`, '```json', JSON.stringify(block.data.output_data || {}, null, 2), '```', '')
      else if (block.type === 'summary') lines.push('## 最终总结', String(block.data.summary_text || ''), '')
      else if (block.type === 'output') lines.push(String(block.data.output_text || ''), '')
    }
    return lines.join('\n')
  }

  return {
    tasks, currentTaskId, currentTask, collapsedMap, streamBuffer, visibleBlocks, planItems,
    completedPlanCount, activeToolCalls, summaryBlock, setTask, handleSSEEvent, restoreTask,
    toggleCollapse, isCollapsed, collapseAll, expandAll, reset, exportTask,
  }
})
