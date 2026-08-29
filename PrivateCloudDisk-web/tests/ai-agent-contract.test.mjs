import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

const root = new URL('../', import.meta.url)
const source = async (path) => readFile(new URL(path, root), 'utf8')

test('AI web client consumes structured V2 task events and restores task snapshots without replaying a run', async () => {
  const api = await source('src/api/aiAgent.ts')
  const taskStore = await source('src/stores/agentTaskStore.ts')
  assert.match(api, /agent_task_start/)
  assert.match(api, /tool_call_end/)
  assert.match(api, /getAiTaskSnapshot/)
  assert.match(api, /\/runs\/\$\{encodeURIComponent\(runId\)\}\/task/)
  assert.match(api, /fetch\(aiUrl\(/)
  assert.doesNotMatch(api, /AI_AGENT_IDENTITY_SHARED_SECRET/)
  assert.match(taskStore, /restoreTask/)
  assert.match(taskStore, /seenEventIds/)
  assert.match(taskStore, /output_data = event\.data\.output_data/)
  assert.doesNotMatch(taskStore, /output_data = event\.data\.result/)
})

test('AI task view is independent from ordinary chat bubbles and has explicit block components', async () => {
  const view = await source('src/views/AiAssistantView.vue')
  const taskView = await source('src/components/ai/task/AgentTaskView.vue')
  const tool = await source('src/components/ai/task/ToolCallBlock.vue')
  assert.match(view, /AgentTaskView/)
  assert.match(view, /message\.taskId/)
  assert.match(taskView, /ThinkingBlock/)
  assert.match(taskView, /PlanBlock/)
  assert.match(taskView, /ToolCallBlock/)
  assert.match(taskView, /Ctrl\+E/)
  assert.match(tool, /output_data/)
  assert.match(tool, /Do not substitute a raw tool_result wrapper/)
})

test('AI assistant stays routable and retains server-bound approval resume', async () => {
  const router = await source('src/router/index.ts')
  const sidebar = await source('src/components/layout/Sidebar.vue')
  const store = await source('src/stores/aiAgentStore.ts')
  assert.match(router, /path:\s*'ai'/)
  assert.match(sidebar, /AI 助手/)
  assert.match(store, /respondAiApproval\(payload\.taskId, payload\.approved, payload\.approvalToken\)/)
  assert.match(store, /streamAiRunResume\(payload\.taskId/)
})
