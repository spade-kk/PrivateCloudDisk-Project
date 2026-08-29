<template>
  <div class="ai-widget" :class="{ open: expanded }" :style="widgetStyle">
    <section v-if="expanded" class="ai-widget-panel" role="dialog" aria-label="AI 助手悬浮窗口">
      <header><div><i class="fa fa-sparkles"></i><strong>AI 助手</strong><small>{{ store.connectionState === 'connected' ? '已连接' : '随时为你服务' }}</small></div><div><button type="button" title="全屏打开" @click="openPage"><i class="fa fa-expand"></i></button><button type="button" title="最小化" @click="expanded = false"><i class="fa fa-minus"></i></button></div></header>
      <div class="ai-widget-body">
        <div v-if="!store.messages.length" class="ai-widget-empty"><i class="fa fa-commenting-o"></i><p>我可以在你的权限范围内检索文件、分析空间信息与协助工作流。</p></div>
        <article v-for="message in store.messages.slice(-6)" :key="message.id" :class="`role-${message.role}`"><span>{{ message.role === 'user' ? '你' : 'AI' }}</span><p>{{ message.content || message.plan || (message.streaming ? '正在思考…' : '') }}</p></article>
      </div>
      <form @submit.prevent="send"><textarea v-model="prompt" rows="1" placeholder="询问 AI 助手…" :disabled="store.streaming" @keydown.enter.exact.prevent="send"></textarea><button v-if="store.streaming" type="button" class="stop" @click="store.stop"><i class="fa fa-stop"></i></button><button v-else type="submit" :disabled="!prompt.trim()"><i class="fa fa-arrow-up"></i></button></form>
    </section>
    <button v-if="!expanded" type="button" class="ai-widget-trigger" title="打开 AI 助手" @pointerdown="beginDrag" @click="openAfterClick"><i class="fa fa-sparkles"></i><span v-if="store.streaming" class="ai-widget-badge"></span></button>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAiAgentStore } from '@/stores/aiAgentStore'

const router = useRouter()
const store = useAiAgentStore()
const expanded = ref(false)
const prompt = ref('')
const position = ref(JSON.parse(localStorage.getItem('pcd.ai.widget.position') || '{"right":24,"bottom":24}'))
const widgetStyle = computed(() => ({ right: `${Math.max(12, Number(position.value.right) || 24)}px`, bottom: `${Math.max(12, Number(position.value.bottom) || 24)}px` }))
let dragging = false
let moved = false
let pointerStart = { x: 0, y: 0, right: 24, bottom: 24 }

onMounted(() => { if (!store.conversations.length) store.initialize().catch(() => undefined) })
function openAfterClick() { if (!moved) expanded.value = true }
function openPage() { expanded.value = false; router.push('/app/ai') }
async function send() { const value = prompt.value.trim(); if (!value) return; prompt.value = ''; await store.send(value) }
function beginDrag(event: PointerEvent) {
  dragging = true; moved = false
  pointerStart = { x: event.clientX, y: event.clientY, right: Number(position.value.right) || 24, bottom: Number(position.value.bottom) || 24 }
  window.addEventListener('pointermove', drag)
  window.addEventListener('pointerup', endDrag, { once: true })
}
function drag(event: PointerEvent) {
  if (!dragging) return
  const dx = event.clientX - pointerStart.x; const dy = event.clientY - pointerStart.y
  if (Math.abs(dx) + Math.abs(dy) > 4) moved = true
  position.value = { right: Math.max(12, pointerStart.right - dx), bottom: Math.max(12, pointerStart.bottom - dy) }
}
function endDrag() { dragging = false; window.removeEventListener('pointermove', drag); localStorage.setItem('pcd.ai.widget.position', JSON.stringify(position.value)) }
</script>

<style scoped>
.ai-widget{position:fixed;z-index:70}.ai-widget-panel{display:flex;width:420px;height:600px;max-width:calc(100vw - 24px);max-height:calc(100dvh - 24px);flex-direction:column;overflow:hidden;resize:both;border:1px solid rgba(148,163,184,.3);border-radius:16px;background:var(--el-bg-color,#fff);box-shadow:0 24px 70px rgba(15,23,42,.28)}.ai-widget-panel header{display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid rgba(148,163,184,.2);padding:13px 14px}.ai-widget-panel header>div:first-child{display:flex;align-items:center;gap:8px}.ai-widget-panel header>div:first-child>i{display:grid;width:27px;height:27px;place-items:center;border-radius:8px;background:#2563eb;color:white}.ai-widget-panel header strong,.ai-widget-panel header small{display:block}.ai-widget-panel header strong{font-size:13px}.ai-widget-panel header small{margin-top:2px;color:#64748b;font-size:10px}.ai-widget-panel header button{border:0;background:transparent;color:#64748b;padding:6px}.ai-widget-body{min-height:0;flex:1;overflow:auto;padding:14px}.ai-widget-body article{display:flex;gap:8px;margin-bottom:12px}.ai-widget-body article>span{display:grid;width:22px;height:22px;flex:0 0 22px;place-items:center;border-radius:7px;background:#e2e8f0;color:#475569;font-size:10px;font-weight:700}.ai-widget-body article.role-user{flex-direction:row-reverse}.ai-widget-body article.role-user>span{background:#2563eb;color:#fff}.ai-widget-body p{margin:0;border-radius:10px;background:#f1f5f9;padding:8px 10px;font-size:12px;line-height:1.55;white-space:pre-wrap}.dark .ai-widget-body p{background:#1e293b}.ai-widget-body .role-user p{background:#2563eb;color:white}.ai-widget-empty{padding:25% 18px;text-align:center;color:#64748b;font-size:12px;line-height:1.65}.ai-widget-empty i{font-size:25px;color:#6366f1}.ai-widget-panel form{display:flex;gap:8px;border-top:1px solid rgba(148,163,184,.2);padding:10px}.ai-widget-panel textarea{min-height:32px;max-height:90px;flex:1;resize:none;border:1px solid rgba(148,163,184,.3);border-radius:9px;background:transparent;padding:7px 9px;outline:0;font:inherit;font-size:12px}.ai-widget-panel textarea:focus{border-color:#60a5fa}.ai-widget-panel form button{align-self:flex-end;width:32px;height:32px;border:0;border-radius:9px;background:#2563eb;color:#fff}.ai-widget-panel form button.stop{background:#dc2626}.ai-widget-trigger{position:relative;display:grid;width:52px;height:52px;place-items:center;border:0;border-radius:17px;background:linear-gradient(135deg,#2563eb,#7c3aed);box-shadow:0 14px 30px rgba(37,99,235,.35);color:#fff;font-size:19px;cursor:grab;touch-action:none}.ai-widget-trigger:active{cursor:grabbing;transform:scale(.96)}.ai-widget-badge{position:absolute;right:4px;top:4px;width:9px;height:9px;border:2px solid #fff;border-radius:50%;background:#22c55e}@media(max-width:640px){.ai-widget-panel{width:calc(100vw - 20px);height:min(70dvh,600px);resize:none}.ai-widget{right:10px!important;bottom:max(10px,env(safe-area-inset-bottom))!important}.ai-widget-trigger{width:48px;height:48px;border-radius:15px}}
</style>
