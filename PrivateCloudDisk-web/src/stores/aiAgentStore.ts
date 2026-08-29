// Cloud AI Agent conversation orchestration. The task execution projection is
// delegated to useAgentTaskStore so chat history never parses model prose into plans
// or tool calls.

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  cancelAiRun, createAiConversation, deleteAiConversation, getAiModels, listAiConversations,
  listAiMessages, respondAiApproval, streamAgentRun, streamAiRunResume,
  type AiConversation, type AiMessage, type AiStreamEvent,
} from '@/api/aiAgent'
import { useAgentTaskStore } from '@/stores/agentTaskStore'
import { useToastStore } from '@/stores/toastStore'

export interface AiDisplayMessage extends AiMessage {
  error?: string
  streaming?: boolean
  taskId?: string
}

function localMessage(role: 'user' | 'assistant', content = ''): AiDisplayMessage {
  return { id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`, role, content, created_at: new Date().toISOString() }
}

export const useAiAgentStore = defineStore('aiAgent', () => {
  const conversations = ref<AiConversation[]>([])
  const activeConversationId = ref<string | null>(localStorage.getItem('pcd.ai.active-conversation'))
  const messages = ref<AiDisplayMessage[]>([])
  const models = ref<Array<{ id: string; provider: string }>>([])
  const loading = ref(false)
  const streaming = ref(false)
  const connectionState = ref<'idle' | 'connecting' | 'connected' | 'reconnecting' | 'error'>('idle')
  const activeRunId = ref<string | null>(null)
  const controller = ref<AbortController | null>(null)
  const latestAssistant = computed(() => [...messages.value].reverse().find((item) => item.role === 'assistant') || null)
  const taskStore = useAgentTaskStore()

  async function initialize() {
    loading.value = true
    try {
      const [nextConversations, nextModels] = await Promise.all([listAiConversations(), getAiModels()])
      conversations.value = nextConversations
      models.value = nextModels
      const stillExists = nextConversations.some((item) => item.id === activeConversationId.value)
      if (!stillExists) activeConversationId.value = nextConversations[0]?.id || null
      if (activeConversationId.value) await selectConversation(activeConversationId.value)
    } finally { loading.value = false }
  }

  async function restoreMessageTasks(nextMessages: AiDisplayMessage[]) {
    const runIds = [...new Set(nextMessages.map((message) => message.run_id).filter(Boolean) as string[])]
    await Promise.all(runIds.map(async (runId) => {
      try { await taskStore.restoreTask(runId) } catch { /* Runs created before V2 have no task snapshot. */ }
    }))
  }

  async function selectConversation(id: string) {
    activeConversationId.value = id
    localStorage.setItem('pcd.ai.active-conversation', id)
    const loaded = (await listAiMessages(id)).map((item) => ({ ...item, taskId: item.run_id || undefined }))
    messages.value = loaded
    await restoreMessageTasks(loaded)
  }

  async function createConversation(title?: string) {
    const conversation = await createAiConversation({ title, model: models.value[0]?.id })
    conversations.value = [conversation, ...conversations.value]
    await selectConversation(conversation.id)
    return conversation
  }

  async function removeConversation(id: string) {
    await deleteAiConversation(id)
    conversations.value = conversations.value.filter((item) => item.id !== id)
    if (activeConversationId.value === id) {
      activeConversationId.value = conversations.value[0]?.id || null
      messages.value = []
      if (activeConversationId.value) await selectConversation(activeConversationId.value)
    }
  }

  async function send(message: string, model?: string) {
    const trimmed = message.trim()
    if (!trimmed || streaming.value) return
    const conversation = activeConversationId.value
      ? conversations.value.find((item) => item.id === activeConversationId.value)
      : await createConversation(trimmed.slice(0, 28))
    if (!conversation) return
    const userMessage = localMessage('user', trimmed)
    const assistantMessage = localMessage('assistant')
    assistantMessage.streaming = true
    messages.value.push(userMessage, assistantMessage)
    streaming.value = true
    connectionState.value = 'connecting'
    controller.value = new AbortController()
    try {
      await streamAgentRun(conversation.id, { message: trimmed, model, mode: 'agent', stream: true }, {
        signal: controller.value.signal,
        onEvent: (event) => applyStreamEvent(event, assistantMessage),
      })
      assistantMessage.streaming = false
      connectionState.value = 'connected'
      await refreshActiveMessages(conversation.id, assistantMessage.taskId)
    } catch (error: any) {
      if (error?.name === 'AbortError') assistantMessage.error ||= '已停止生成。'
      else {
        assistantMessage.error = error?.message || 'AI 助手连接失败'
        useToastStore().showToast(assistantMessage.error, 'error')
        connectionState.value = 'error'
      }
      assistantMessage.streaming = false
    } finally {
      streaming.value = false
      controller.value = null
    }
  }

  function applyStreamEvent(event: AiStreamEvent, assistant: AiDisplayMessage) {
    const taskId = String(event.data.task_id || event.data.run_id || '')
    if (taskId) {
      assistant.taskId ||= taskId
      activeRunId.value = taskId
      taskStore.handleSSEEvent(event)
      connectionState.value = 'connected'
    }
    if (event.event === 'summary') assistant.content = String(event.data.summary_text || '')
    if (event.event === 'task_completed' || event.event === 'task_cancelled') {
      assistant.streaming = false
      activeRunId.value = null
    }
    if (event.event === 'task_failed') {
      assistant.streaming = false
      assistant.error = String(event.data.message || 'Agent 运行失败')
      activeRunId.value = null
    }
  }

  async function refreshActiveMessages(conversationId: string, latestTaskId?: string) {
    if (activeConversationId.value !== conversationId) return
    const persisted = (await listAiMessages(conversationId)).map((item) => ({ ...item, taskId: item.run_id || undefined }))
    // A just-finished assistant message can race final message persistence. Keep its
    // structured task document until the next refresh rather than replacing it.
    if (latestTaskId && !persisted.some((item) => item.taskId === latestTaskId)) {
      const local = messages.value.find((item) => item.taskId === latestTaskId)
      if (local) persisted.push(local)
    }
    messages.value = persisted
    await restoreMessageTasks(persisted)
  }

  async function stop() {
    controller.value?.abort()
    if (activeRunId.value) await cancelAiRun(activeRunId.value)
  }

  async function approveTaskTool(payload: { taskId: string; blockId: string; approved: boolean; approvalToken: string }) {
    if (!payload.approvalToken) return
    const assistant = messages.value.find((message) => message.taskId === payload.taskId)
    if (!assistant) return
    await respondAiApproval(payload.taskId, payload.approved, payload.approvalToken)
    streaming.value = true
    assistant.streaming = true
    activeRunId.value = payload.taskId
    connectionState.value = 'connecting'
    controller.value = new AbortController()
    try {
      await streamAiRunResume(payload.taskId, { signal: controller.value.signal, onEvent: (event) => applyStreamEvent(event, assistant) })
      if (activeConversationId.value) await refreshActiveMessages(activeConversationId.value, payload.taskId)
    } catch (error: any) {
      assistant.error = error?.message || '审批后的任务恢复失败'
      connectionState.value = 'error'
      useToastStore().showToast(assistant.error, 'error')
    } finally {
      assistant.streaming = false
      streaming.value = false
      controller.value = null
    }
  }

  return {
    conversations, activeConversationId, messages, models, loading, streaming, connectionState, activeRunId, latestAssistant,
    initialize, selectConversation, createConversation, removeConversation, send, stop, approveTaskTool,
  }
})
