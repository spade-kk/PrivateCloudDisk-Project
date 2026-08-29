<template>
  <section class="ai-assistant" :class="{ 'is-loading': store.loading }">
    <aside class="ai-conversations" aria-label="AI 会话列表">
      <div class="ai-brand"><span class="ai-brand-icon"><i class="fa fa-sparkles"></i></span><div><strong>AI 助手</strong><small>企业智能体</small></div></div>
      <button class="ai-new" type="button" @click="newConversation"><i class="fa fa-plus"></i> 新建对话</button>
      <div class="ai-conversation-list">
        <button v-for="conversation in store.conversations" :key="conversation.id" type="button" class="ai-conversation" :class="{ active: conversation.id === store.activeConversationId }" @click="store.selectConversation(conversation.id)">
          <span class="ai-conversation-copy"><strong>{{ conversation.title }}</strong><small>{{ formatTime(conversation.updated_at) }}</small></span><i class="fa fa-trash-o" title="删除会话" @click.stop="confirmDelete(conversation.id)"></i>
        </button>
        <p v-if="!store.conversations.length && !store.loading" class="ai-empty-list">开始一个对话，让助手帮你分析文件、空间和工作流。</p>
      </div>
      <div class="ai-sidebar-footer"><i class="fa fa-shield"></i> 所有企业操作均经过权限校验</div>
    </aside>

    <main class="ai-chat" aria-live="polite">
      <header class="ai-toolbar">
        <div><h1>企业 AI 助手</h1><p><span class="ai-connection-dot" :class="store.connectionState"></span>{{ connectionText }}</p></div>
        <div class="ai-toolbar-actions"><select v-model="selectedModel" aria-label="选择模型"><option v-for="model in store.models" :key="model.id" :value="model.id">{{ model.id }}</option></select><button type="button" title="新建对话" aria-label="新建对话" @click="newConversation"><i class="fa fa-plus"></i></button></div>
      </header>

      <div ref="messageContainer" class="ai-messages">
        <div v-if="!store.messages.length" class="ai-welcome"><span><i class="fa fa-sparkles"></i></span><h2>今天想完成什么工作？</h2><p>我可以在你的权限范围内查找文件、梳理空间信息、生成并校验 CloudFlow 工作流。</p><div class="ai-suggestions"><button v-for="suggestion in suggestions" :key="suggestion" type="button" @click="prompt = suggestion">{{ suggestion }}</button></div></div>

        <article v-for="message in store.messages" :key="message.id" class="ai-message" :class="`role-${message.role}`">
          <div class="ai-avatar"><i :class="message.role === 'user' ? 'fa fa-user' : 'fa fa-sparkles'"></i></div>
          <div class="ai-message-body">
            <div v-if="message.role === 'assistant' && message.taskId && taskStore.tasks[message.taskId]" class="ai-task-host">
              <AgentTaskView :task="taskStore.tasks[message.taskId]" @copy="copy" @approve="store.approveTaskTool" @export="exportTask(message.taskId, $event)" />
            </div>
            <AiMarkdownRenderer
              v-else-if="message.role === 'assistant' && message.content"
              :content="message.content"
              :streaming="message.streaming"
              @copy="copy"
            />
            <div v-else-if="message.content" class="ai-plain-content">{{ message.content }}</div>
            <p v-if="message.role === 'assistant' && message.streaming && !message.taskId" class="ai-preparing"><i class="fa fa-circle-o-notch fa-spin"></i> 正在建立任务执行视图…</p>
            <p v-if="message.error" class="ai-error"><i class="fa fa-exclamation-circle"></i>{{ message.error }}</p>
            <div v-if="message.role === 'assistant' && !message.streaming && message.content && !message.taskId" class="ai-message-actions"><button type="button" title="复制" aria-label="复制消息" @click="copy(message.content)"><i class="fa fa-copy"></i></button><button type="button" title="重新生成" aria-label="重新生成" @click="regenerate"><i class="fa fa-refresh"></i></button></div>
          </div>
        </article>
      </div>

      <footer class="ai-composer-wrap">
        <form class="ai-composer" @submit.prevent="send"><textarea v-model="prompt" rows="1" placeholder="描述想完成的任务，例如：汇总本周销售文件并生成报告…" :disabled="store.streaming" @keydown.enter.exact.prevent="send" @input="resizeComposer"></textarea><div class="ai-composer-tools"><span><i class="fa fa-lock"></i> 仅使用你有权限访问的数据</span><div><button v-if="store.streaming" class="stop" type="button" @click="store.stop"><i class="fa fa-stop"></i> 停止</button><button v-else class="send" type="submit" :disabled="!prompt.trim()" aria-label="发送任务"><i class="fa fa-arrow-up"></i></button></div></div></form>
        <p>执行过程会以计划、受限工具调用和最终总结展示；高风险操作仍会请求你的确认。</p>
      </footer>
    </main>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useAiAgentStore } from '@/stores/aiAgentStore'
import { useAgentTaskStore } from '@/stores/agentTaskStore'
import { useToastStore } from '@/stores/toastStore'
import AgentTaskView from '@/components/ai/task/AgentTaskView.vue'
import AiMarkdownRenderer from '@/components/ai/AiMarkdownRenderer.vue'

const store = useAiAgentStore()
const taskStore = useAgentTaskStore()
const toast = useToastStore()
const prompt = ref('')
const selectedModel = ref('')
const messageContainer = ref<HTMLElement | null>(null)
const suggestions = ['帮我查找最近修改的合同文件', '总结当前空间中的项目资料', '生成一个每周汇总的 CloudFlow 工作流']
const connectionText = computed(() => ({ idle: '等待开始', connecting: '正在连接…', connected: '已连接', reconnecting: '正在重连…', error: '连接异常' })[store.connectionState])

onMounted(async () => {
  try { await store.initialize(); selectedModel.value = store.models[0]?.id || '' } catch (error: any) { toast.showToast(error?.message || '加载 AI 助手失败', 'error') }
})
watch(() => store.messages.length, () => nextTick(scrollBottom))
watch(taskStore.tasks, () => nextTick(scrollBottom), { deep: true })

async function newConversation() { await store.createConversation(); prompt.value = '' }
async function confirmDelete(id: string) { if (window.confirm('删除此对话及其历史记录？')) await store.removeConversation(id) }
async function send() { const value = prompt.value.trim(); if (!value) return; prompt.value = ''; await store.send(value, selectedModel.value || undefined) }
function regenerate() { const prior = [...store.messages].reverse().find((item) => item.role === 'user'); if (prior) store.send(prior.content, selectedModel.value || undefined) }
function resizeComposer(event: Event) { const target = event.target as HTMLTextAreaElement; target.style.height = 'auto'; target.style.height = `${Math.min(target.scrollHeight, 180)}px` }
function scrollBottom() { if (messageContainer.value) messageContainer.value.scrollTop = messageContainer.value.scrollHeight }
async function copy(value: string) { try { await navigator.clipboard.writeText(value); toast.showToast('已复制', 'success') } catch { toast.showToast('复制失败，请手动复制', 'warning') } }
function formatTime(value: string) { try { return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) } catch { return '' } }
function exportTask(taskId: string, format: 'markdown' | 'json') {
  const content = taskStore.exportTask(taskId, format)
  if (!content) return
  const blob = new Blob([content], { type: format === 'json' ? 'application/json;charset=utf-8' : 'text/markdown;charset=utf-8' })
  const anchor = document.createElement('a')
  anchor.href = URL.createObjectURL(blob)
  anchor.download = `agent-task-${taskId}.${format === 'json' ? 'json' : 'md'}`
  anchor.click()
  URL.revokeObjectURL(anchor.href)
}
</script>

<style scoped>
.ai-assistant{--ai-border:rgba(148,163,184,.24);--ai-muted:#64748b;display:grid;grid-template-columns:260px minmax(0,1fr);min-height:calc(100vh - 156px);overflow:hidden;border:1px solid var(--ai-border);border-radius:18px;background:var(--el-bg-color,#fff);box-shadow:0 18px 42px rgba(15,23,42,.07)}.ai-conversations{display:flex;min-height:0;flex-direction:column;border-right:1px solid var(--ai-border);background:linear-gradient(180deg,rgba(248,250,252,.94),rgba(255,255,255,.98));padding:16px}.dark .ai-conversations{background:linear-gradient(180deg,#161b28,#111827)}.ai-brand{display:flex;align-items:center;gap:10px;padding:6px 4px 18px}.ai-brand-icon{display:grid;width:34px;height:34px;place-items:center;border-radius:11px;background:linear-gradient(135deg,#2563eb,#7c3aed);color:#fff}.ai-brand strong,.ai-brand small{display:block}.ai-brand strong{font-size:14px}.ai-brand small{margin-top:2px;color:var(--ai-muted);font-size:11px}.ai-new{width:100%;border:1px solid rgba(37,99,235,.22);border-radius:10px;background:#2563eb;padding:10px 12px;color:#fff;font-size:13px;font-weight:650;transition:.18s}.ai-new:hover{background:#1d4ed8}.ai-new i{margin-right:7px}.ai-conversation-list{min-height:0;flex:1;overflow:auto;margin:15px -7px 0;padding:0 7px}.ai-conversation{display:flex;width:100%;align-items:center;gap:8px;border:0;border-radius:10px;background:transparent;padding:10px 8px;color:inherit;text-align:left}.ai-conversation:hover,.ai-conversation.active{background:rgba(37,99,235,.09)}.ai-conversation-copy{min-width:0;flex:1}.ai-conversation strong,.ai-conversation small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.ai-conversation strong{font-size:12px}.ai-conversation small{margin-top:3px;color:var(--ai-muted);font-size:10px}.ai-conversation>i{visibility:hidden;color:#94a3b8}.ai-conversation:hover>i{visibility:visible}.ai-empty-list{padding:16px 8px;color:var(--ai-muted);font-size:12px;line-height:1.65}.ai-sidebar-footer{padding:14px 4px 2px;color:var(--ai-muted);font-size:10px}.ai-sidebar-footer i{margin-right:5px;color:#16a34a}.ai-chat{display:flex;min-width:0;min-height:0;flex-direction:column;background:var(--el-bg-color,#fff)}.ai-toolbar{display:flex;min-height:68px;align-items:center;justify-content:space-between;border-bottom:1px solid var(--ai-border);padding:10px 22px}.ai-toolbar h1{margin:0;font-size:15px}.ai-toolbar p{margin:4px 0 0;color:var(--ai-muted);font-size:11px}.ai-connection-dot{display:inline-block;width:7px;height:7px;margin-right:6px;border-radius:50%;background:#94a3b8}.ai-connection-dot.connected{background:#22c55e}.ai-connection-dot.connecting,.ai-connection-dot.reconnecting{background:#f59e0b}.ai-connection-dot.error{background:#ef4444}.ai-toolbar-actions{display:flex;align-items:center;gap:8px}.ai-toolbar-actions select{max-width:220px;border:1px solid var(--ai-border);border-radius:8px;background:transparent;padding:7px 9px;font-size:12px}.ai-toolbar-actions button{display:grid;width:32px;height:32px;place-items:center;border:1px solid var(--ai-border);border-radius:8px;background:transparent}.ai-messages{min-height:0;flex:1;overflow:auto;padding:28px max(6vw,30px)}.ai-welcome{max-width:680px;margin:10vh auto;text-align:center}.ai-welcome>span{display:inline-grid;width:52px;height:52px;place-items:center;border-radius:17px;background:linear-gradient(135deg,#dbeafe,#ede9fe);color:#4f46e5;font-size:22px}.ai-welcome h2{margin:17px 0 8px;font-size:23px}.ai-welcome p{max-width:540px;margin:0 auto;color:var(--ai-muted);font-size:14px;line-height:1.7}.ai-suggestions{display:flex;flex-wrap:wrap;justify-content:center;gap:8px;margin-top:22px}.ai-suggestions button{border:1px solid var(--ai-border);border-radius:999px;background:transparent;padding:8px 12px;color:var(--ai-muted);font-size:12px}.ai-suggestions button:hover{border-color:#60a5fa;color:#2563eb}.ai-message{display:flex;max-width:870px;gap:12px;margin:0 auto 23px}.ai-message.role-user{flex-direction:row-reverse}.ai-avatar{display:grid;width:30px;height:30px;flex:0 0 30px;place-items:center;border-radius:9px;background:#e2e8f0;color:#475569;font-size:12px}.role-user .ai-avatar{background:#2563eb;color:#fff}.ai-message-body{min-width:0;max-width:calc(100% - 42px);flex:1}.role-user .ai-message-body{max-width:min(75%,700px);border-radius:14px 4px 14px 14px;background:#2563eb;padding:10px 13px;color:#fff}.ai-content{font-size:14px;line-height:1.75;overflow-wrap:anywhere}.ai-content :deep(pre){max-width:100%;overflow:auto;border-radius:10px;background:#0f172a;padding:12px;color:#e2e8f0}.ai-content :deep(code){border-radius:4px;background:rgba(148,163,184,.16);padding:1px 4px;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:.88em}.role-user .ai-content :deep(code){background:rgba(255,255,255,.18)}.ai-preparing,.ai-error{margin:8px 0;color:var(--ai-muted);font-size:12px}.ai-error{color:#dc2626}.ai-error i{margin-right:5px}.ai-message-actions{display:flex;gap:4px;margin-top:7px;opacity:0;transition:.16s}.ai-message:hover .ai-message-actions{opacity:1}.ai-message-actions button{border:0;background:transparent;color:#94a3b8;padding:4px}.ai-composer-wrap{padding:14px max(6vw,30px) 20px}.ai-composer{max-width:870px;margin:auto;border:1px solid rgba(148,163,184,.36);border-radius:15px;background:var(--el-bg-color,#fff);padding:10px 11px;box-shadow:0 10px 30px rgba(15,23,42,.08)}.ai-composer:focus-within{border-color:#60a5fa;box-shadow:0 0 0 3px rgba(59,130,246,.11)}.ai-composer textarea{display:block;box-sizing:border-box;width:100%;max-height:180px;resize:none;border:0;outline:0;background:transparent;color:inherit;font:inherit;font-size:14px;line-height:1.55}.ai-composer-tools{display:flex;align-items:center;justify-content:space-between;margin-top:7px;color:var(--ai-muted);font-size:10px}.ai-composer-tools span i{margin-right:4px;color:#16a34a}.ai-composer-tools button{border:0;border-radius:8px;padding:7px 10px;font-size:12px}.ai-composer-tools .send{display:grid;width:31px;height:31px;place-items:center;padding:0;background:#2563eb;color:#fff}.ai-composer-tools .send:disabled{opacity:.4}.ai-composer-tools .stop{background:#fee2e2;color:#b91c1c}.ai-composer-wrap>p{max-width:870px;margin:8px auto 0;color:var(--ai-muted);text-align:center;font-size:10px}@media (max-width:840px){.ai-assistant{grid-template-columns:1fr;min-height:calc(100vh - 128px)}.ai-conversations{display:none}.ai-messages{padding:22px 16px}.ai-composer-wrap{padding:12px 16px 16px}.ai-toolbar{padding:10px 15px}.ai-toolbar-actions select{max-width:140px}.ai-welcome{margin:8vh auto}.ai-message{margin-bottom:18px}.role-user .ai-message-body{max-width:86%}}@media (max-width:480px){.ai-toolbar{min-height:54px}.ai-toolbar-actions select{max-width:112px}.ai-messages{padding:16px 11px}.ai-composer-wrap{padding:9px 11px calc(12px + env(safe-area-inset-bottom))}.ai-message{gap:8px}.ai-avatar{width:28px;height:28px;flex-basis:28px}.ai-message-body{max-width:calc(100% - 36px)}.ai-message-actions{opacity:1}}@media (prefers-reduced-motion:reduce){.ai-assistant *{transition:none!important}}
.ai-plain-content{font-size:14px;line-height:1.75;white-space:pre-wrap;overflow-wrap:anywhere}
</style>
