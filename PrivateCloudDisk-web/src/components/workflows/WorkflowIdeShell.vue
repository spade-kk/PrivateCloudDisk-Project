<template>
  <section class="workflow-ide-shell" :class="{ 'is-focus': focusMode, 'is-fullscreen': fullscreen, 'has-mobile-panel': mobilePanelOpen }" :style="cssVariables" aria-label="CloudFlow 工作流开发环境">
    <header class="workflow-ide-shell__toolbar"><slot name="toolbar"></slot></header>
    <!-- [IDE-RESP-2026-08 / 3.9、4.20、5.18] 小屏抽屉的点击外部关闭层，不影响桌面可调整面板。 -->
    <button v-if="mobilePanelOpen" class="workflow-ide-shell__mobile-scrim" type="button" aria-label="关闭当前 IDE 面板" @click="$emit('close-mobile-panels')"></button>
    <main class="workflow-ide-shell__main">
      <aside v-if="!ui.leftCollapsed" class="workflow-ide-shell__left"><slot name="left"></slot></aside>
      <div v-if="!ui.leftCollapsed" class="workflow-ide-shell__resizer workflow-ide-shell__resizer--vertical" role="separator" aria-label="调整节点库宽度" @pointerdown="startResize('left', $event)"></div>
      <section class="workflow-ide-shell__center"><slot name="center"></slot></section>
      <div v-if="!ui.rightCollapsed" class="workflow-ide-shell__resizer workflow-ide-shell__resizer--vertical" role="separator" aria-label="调整属性面板宽度" @pointerdown="startResize('right', $event)"></div>
      <aside v-if="!ui.rightCollapsed" class="workflow-ide-shell__right"><slot name="right"></slot></aside>
    </main>
    <div v-if="!ui.bottomCollapsed" class="workflow-ide-shell__resizer workflow-ide-shell__resizer--horizontal" role="separator" aria-label="调整输出面板高度" @pointerdown="startResize('bottom', $event)"></div>
    <section v-if="!ui.bottomCollapsed" class="workflow-ide-shell__bottom"><slot name="bottom"></slot></section>
  </section>
</template>

<script setup lang="ts">
// [AUDIT FIX 3.1] IDE 面板尺寸可调整且按工作流持久化；布局状态不影响 DSL 语义。
import { computed, onBeforeUnmount } from 'vue'
import type { WorkflowIdeUiState } from '@/types/cloudflowVisual'
const props = defineProps<{ ui: WorkflowIdeUiState; focusMode?: boolean; fullscreen?: boolean; mobilePanelOpen?: boolean }>()
const emit = defineEmits<{ resize: [key: 'leftWidth' | 'rightWidth' | 'bottomHeight', value: number]; 'close-mobile-panels': [] }>()
const cssVariables = computed(() => ({ '--workflow-left-width': `${props.ui.leftWidth}px`, '--workflow-right-width': `${props.ui.rightWidth}px`, '--workflow-bottom-height': `${props.ui.bottomHeight}px` }))
let cleanup: (() => void) | undefined
function startResize(side: 'left' | 'right' | 'bottom', event: PointerEvent) {
  event.preventDefault(); const start = side === 'bottom' ? event.clientY : event.clientX; const initial = side === 'left' ? props.ui.leftWidth : side === 'right' ? props.ui.rightWidth : props.ui.bottomHeight
  const move = (moveEvent: PointerEvent) => { const delta = (side === 'bottom' ? moveEvent.clientY : moveEvent.clientX) - start; const value = side === 'left' ? initial + delta : side === 'right' ? initial - delta : initial - delta; emit('resize', side === 'left' ? 'leftWidth' : side === 'right' ? 'rightWidth' : 'bottomHeight', Math.max(side === 'bottom' ? 120 : 220, Math.min(side === 'bottom' ? 480 : 460, value))) }
  const up = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); cleanup = undefined }
  cleanup?.(); cleanup = up; window.addEventListener('pointermove', move); window.addEventListener('pointerup', up)
}
onBeforeUnmount(() => cleanup?.())
</script>

<style scoped>
.workflow-ide-shell { --workflow-border:#dbe4f0;display:grid;width:100%;height:100%;grid-template-rows:auto minmax(0,1fr) auto var(--workflow-bottom-height);min-height:calc(100dvh - 84px);overflow:hidden;border:1px solid var(--workflow-border);border-radius:16px;background:var(--workflow-panel,#fff);box-shadow:0 18px 50px rgb(15 23 42 / .1);box-sizing:border-box; }.workflow-ide-shell.is-fullscreen { position:fixed;inset:0;z-index:140;min-height:100dvh;border-radius:0; }.workflow-ide-shell__toolbar { min-height:60px;border-bottom:1px solid var(--workflow-border);background:var(--workflow-panel,#fff); }.workflow-ide-shell__main { display:grid;width:100%;height:100%;grid-template-columns:var(--workflow-left-width) 5px minmax(0,1fr) 5px var(--workflow-right-width);min-height:0; }.workflow-ide-shell__left,.workflow-ide-shell__right,.workflow-ide-shell__center,.workflow-ide-shell__bottom { min-width:0;min-height:0;overflow:hidden; }.workflow-ide-shell__left { border-right:1px solid var(--workflow-border); }.workflow-ide-shell__right { border-left:1px solid var(--workflow-border); }.workflow-ide-shell__center { width:100%;height:100%;min-height:280px;background:var(--workflow-canvas,#f8fafc); }.workflow-ide-shell__bottom { border-top:1px solid var(--workflow-border);background:#111827; }.workflow-ide-shell__resizer { z-index:3;background:transparent;transition:background-color .15s ease; }.workflow-ide-shell__resizer:hover { background:var(--workflow-primary,#2563eb); }.workflow-ide-shell__resizer--vertical { cursor:col-resize; }.workflow-ide-shell__resizer--horizontal { height:5px;cursor:row-resize; }.workflow-ide-shell.is-focus { grid-template-rows:auto minmax(0,1fr); }.workflow-ide-shell.is-focus .workflow-ide-shell__left,.workflow-ide-shell.is-focus .workflow-ide-shell__right,.workflow-ide-shell.is-focus .workflow-ide-shell__bottom,.workflow-ide-shell.is-focus .workflow-ide-shell__resizer { display:none; }.workflow-ide-shell.is-focus .workflow-ide-shell__main { grid-template-columns:minmax(0,1fr); }
@media (max-width:1279px) { .workflow-ide-shell__main { grid-template-columns:var(--workflow-left-width) 5px minmax(0,1fr); }.workflow-ide-shell__right { position:absolute;right:0;top:61px;bottom:var(--workflow-bottom-height);/* [IDE-RESP-2026-08 / 遮罩层级修复] 旧值 8 小于蒙版层 80，导致抽屉被遮住。 */z-index:var(--ide-z-drawer,90);width:min(88vw,var(--workflow-right-width));border-left:1px solid var(--workflow-border);box-shadow:-15px 0 35px rgb(15 23 42 / .15); }.workflow-ide-shell__main>.workflow-ide-shell__resizer:nth-last-of-type(1) { display:none; } }
@media (max-width:767px) { .workflow-ide-shell { min-height:calc(100dvh - 8px);border-radius:10px; }.workflow-ide-shell__main { grid-template-columns:minmax(0,1fr); }.workflow-ide-shell__left { position:absolute;top:61px;bottom:var(--workflow-bottom-height);left:0;/* [IDE-RESP-2026-08 / 遮罩层级修复] 保证左抽屉与右抽屉均在 Shell 蒙版上方。 */z-index:var(--ide-z-drawer,90);width:min(88vw,var(--workflow-left-width));box-shadow:15px 0 35px rgb(15 23 42 / .15); }.workflow-ide-shell__right { top:61px;bottom:var(--workflow-bottom-height); }.workflow-ide-shell__resizer--vertical { display:none; }.workflow-ide-shell__bottom { min-height:150px; }.workflow-ide-shell:not(.is-focus) { grid-template-rows:auto minmax(0,1fr) auto min(38dvh,var(--workflow-bottom-height)); } }
@media (prefers-reduced-motion:reduce) { .workflow-ide-shell__resizer { transition:none; } }
</style>
