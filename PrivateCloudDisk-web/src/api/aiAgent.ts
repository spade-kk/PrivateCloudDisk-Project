// ============================================================================
// aiAgent.ts — Cloud AI Agent REST/SSE client
// ============================================================================
// [AI-AGENT-FE-001] SSE uses authenticated fetch rather than EventSource because
// EventSource cannot reliably carry this project's Bearer and X-Space-Id headers.
// Gateway, not the browser, creates the signed X-PCD identity context consumed by
// cloud-ai-agent. No provider key or Capability Hub credential exists in this client.
// ============================================================================

import service, { TOKEN_COOKIE_KEY } from '@/utils/request'
import { cookie } from '@/utils/cookie'
import { useSpaceStore } from '@/stores/spaceStore'

export type AiRole = 'user' | 'assistant' | 'tool'
export type AiRunStatus = 'CREATED' | 'PLANNING' | 'GENERATING' | 'CALLING_TOOL' | 'OBSERVING' | 'REFLECTING' | 'AWAITING_APPROVAL' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'TIMED_OUT'
export type AiTaskStatus = 'running' | 'paused' | 'completed' | 'failed' | 'cancelled'
export type AiTaskBlockType = 'thinking' | 'context' | 'plan' | 'tool_call' | 'output' | 'summary'
/**
 * [AI-AGENT-TASK-005] V2 task events are structured presentation events.  The
 * web client never parses Agent prose to infer a tool, plan or hidden reasoning.
 * Legacy event names remain in this union only for an incremental backend rollout.
 */
export type AiSseEventType =
  | 'agent_task_start' | 'thinking_start' | 'thinking_delta' | 'thinking_end'
  | 'context_start' | 'context_item' | 'context_end' | 'plan_created' | 'plan_item_update'
  | 'tool_call_start' | 'tool_call_end' | 'tool_call_error' | 'output' | 'summary'
  | 'task_completed' | 'task_failed' | 'task_cancelled'
  | 'status' | 'plan' | 'token' | 'tool_call' | 'tool_result' | 'tool_retry' | 'reflection'
  | 'approval_required' | 'heartbeat' | 'error' | 'done'

export interface AiConversation {
  id: string
  title: string
  model?: string | null
  created_at: string
  updated_at: string
  archived: boolean
}

export interface AiMessage {
  id: string
  role: AiRole
  content: string
  run_id?: string | null
  tool_calls?: Array<Record<string, unknown>>
  created_at: string
}

export interface AiPlanItem {
  id: string
  title: string
  details?: string | null
  status: 'pending' | 'running' | 'completed' | 'failed' | 'superseded'
}

export interface AiTaskBlock {
  id: string
  type: AiTaskBlockType
  order: number
  parent_id?: string | null
  status?: string | null
  data: Record<string, any>
  started_at: string
  ended_at?: string | null
}

export interface AiTaskSnapshot {
  schema_version: number
  task_id: string
  conversation_id: string
  space_id?: string | null
  user_request: string
  model: string
  status: AiTaskStatus
  started_at: string
  ended_at?: string | null
  total_duration_ms?: number | null
  blocks: AiTaskBlock[]
}

export interface AiRun {
  id: string
  conversation_id: string
  status: AiRunStatus
  model: string
  iterations: number
  tool_calls: number
  error_code?: string | null
  error_message?: string | null
}

export interface AiStreamEvent {
  event: AiSseEventType
  id?: string
  data: Record<string, any>
}

export interface StartAgentRunPayload {
  message: string
  model?: string
  mode?: 'agent' | 'api'
  stream?: boolean
  attachments?: Array<{ file_id: string; name?: string; type?: string }>
}

function apiBase(): string {
  return (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')
}

function aiUrl(path: string): string {
  return `${apiBase()}/ai${path}`
}

function authenticatedHeaders(): HeadersInit {
  const headers: Record<string, string> = { Accept: 'text/event-stream', 'Content-Type': 'application/json' }
  const token = cookie.get(TOKEN_COOKIE_KEY)
  if (token) headers.Authorization = `Bearer ${token}`
  try {
    const spaceStore = useSpaceStore()
    if (spaceStore.currentSpaceId) headers['X-Space-Id'] = spaceStore.currentSpaceId
  } catch {
    // Pinia initialization should not block an API request; Gateway will still require JWT.
  }
  return headers
}

function unwrap<T>(response: any): T {
  // AI Agent returns direct typed JSON. Supporting a future standard response envelope
  // keeps the web client compatible with Gateway response normalization.
  return (response?.data ?? response) as T
}

export async function listAiConversations(): Promise<AiConversation[]> {
  return unwrap<{ items: AiConversation[] }>(await service.get('/ai/conversations')).items || []
}

export async function createAiConversation(payload: { title?: string; model?: string } = {}): Promise<AiConversation> {
  return unwrap<AiConversation>(await service.post('/ai/conversations', payload))
}

export async function updateAiConversation(id: string, payload: { title?: string; archived?: boolean }): Promise<AiConversation> {
  return unwrap<AiConversation>(await service.patch(`/ai/conversations/${encodeURIComponent(id)}`, payload))
}

export async function deleteAiConversation(id: string): Promise<void> {
  await service.delete(`/ai/conversations/${encodeURIComponent(id)}`)
}

export async function listAiMessages(id: string, offset = 0, limit = 100): Promise<AiMessage[]> {
  return unwrap<{ items: AiMessage[] }>(await service.get(`/ai/conversations/${encodeURIComponent(id)}/messages`, { params: { offset, limit } })).items || []
}

export async function getAiModels(): Promise<Array<{ id: string; provider: string }>> {
  return unwrap<{ items: Array<{ id: string; provider: string }> }>(await service.get('/ai/models')).items || []
}

export async function cancelAiRun(runId: string): Promise<void> {
  await service.post(`/ai/runs/${encodeURIComponent(runId)}/cancel`)
}

export async function getAiTaskSnapshot(runId: string): Promise<AiTaskSnapshot> {
  return unwrap<AiTaskSnapshot>(await service.get(`/ai/runs/${encodeURIComponent(runId)}/task`))
}

export async function respondAiApproval(runId: string, approved: boolean, approvalToken: string): Promise<void> {
  await service.post(`/ai/runs/${encodeURIComponent(runId)}/approval`, { approved, approval_token: approvalToken })
}

/** Consume a POST SSE response and preserve line boundaries even when a network chunk splits an event. */
export async function streamAgentRun(
  conversationId: string,
  payload: StartAgentRunPayload,
  options: { signal?: AbortSignal; onEvent: (event: AiStreamEvent) => void },
): Promise<void> {
  const response = await fetch(aiUrl(`/conversations/${encodeURIComponent(conversationId)}/runs`), {
    method: 'POST', headers: authenticatedHeaders(), body: JSON.stringify({ ...payload, stream: true }), signal: options.signal,
  })
  await consumeSseResponse(response, options)
}

/** Resume only the server-bound approval payload; the browser sends no tool arguments. */
export async function streamAiRunResume(
  runId: string,
  options: { signal?: AbortSignal; onEvent: (event: AiStreamEvent) => void },
): Promise<void> {
  const response = await fetch(aiUrl(`/runs/${encodeURIComponent(runId)}/resume`), {
    method: 'POST', headers: authenticatedHeaders(), signal: options.signal,
  })
  await consumeSseResponse(response, options)
}

async function consumeSseResponse(
  response: Response,
  options: { signal?: AbortSignal; onEvent: (event: AiStreamEvent) => void },
): Promise<void> {
  if (!response.ok || !response.body) {
    let message = 'AI 助手连接失败，请稍后重试'
    try { message = (await response.json()).detail || message } catch { /* preserve safe fallback */ }
    throw new Error(message)
  }
  const decoder = new TextDecoder()
  const reader = response.body.getReader()
  let buffer = ''
  try {
    while (true) {
      const chunk = await reader.read()
      if (chunk.done) break
      buffer += decoder.decode(chunk.value, { stream: true }).replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        boundary = buffer.indexOf('\n\n')
        const parsed = parseSseBlock(block)
        if (parsed) options.onEvent(parsed)
      }
    }
  } finally {
    reader.releaseLock()
  }
}

function parseSseBlock(block: string): AiStreamEvent | null {
  if (!block || block.startsWith(':')) return null
  let event = 'message'
  let id: string | undefined
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('id:')) id = line.slice(3).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
  }
  if (!data.length) return null
  try { return { event: event as AiSseEventType, id, data: JSON.parse(data.join('\n')) } } catch { return { event: 'error', id, data: { code: 'AI-SSE-PARSE', message: '助手流式响应格式无效' } } }
}
