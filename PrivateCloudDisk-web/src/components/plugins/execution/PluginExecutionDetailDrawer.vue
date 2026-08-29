<template>
  <!-- Teleport + 明确 scrim/drawer 层级：抽屉永远位于蒙版之上，兼容小屏抽屉场景。 -->
  <Teleport to="body">
    <transition name="execution-scrim"><div v-if="modelValue" class="execution-drawer-scrim" @click.self="close"></div></transition>
    <transition name="execution-drawer">
      <aside v-if="modelValue" class="execution-drawer" role="dialog" aria-modal="true" aria-label="插件执行记录详情">
        <PluginExecutionDetailPanel :execution-id="executionId" :plugin-name="pluginName" mode="drawer" @close="close" @open-page="openPage" />
      </aside>
    </transition>
  </Teleport>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PluginExecutionDetailPanel from './PluginExecutionDetailPanel.vue'

const props = defineProps<{ modelValue: boolean; executionId: string; pluginId?: string; pluginName?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const router = useRouter()
function close() { emit('update:modelValue', false) }
function openPage() { if (props.pluginId) router.push({ name: 'PluginExecutionDetail', params: { pluginId: props.pluginId, executionId: props.executionId } }); close() }
function onCloseEvent() { close() }
function onKeydown(event: KeyboardEvent) { if (event.key === 'Escape' && props.modelValue) close() }
onMounted(() => { window.addEventListener('pcd-plugin-execution-close', onCloseEvent); window.addEventListener('keydown', onKeydown) })
onBeforeUnmount(() => { window.removeEventListener('pcd-plugin-execution-close', onCloseEvent); window.removeEventListener('keydown', onKeydown) })
</script>

<style scoped>
.execution-drawer-scrim { position: fixed; z-index: 120; inset: 0; background: rgb(10 16 27 / .56); backdrop-filter: blur(4px); }.execution-drawer { position: fixed; z-index: 130; top: 2.5vh; right: 2.5vw; bottom: 2.5vh; width: min(80vw, 1200px); min-width: min(760px, 92vw); overflow: hidden; border: 1px solid rgb(255 255 255 / .28); border-radius: 14px; box-shadow: 0 24px 65px rgb(0 0 0 / .35); }.execution-scrim-enter-active, .execution-scrim-leave-active { transition: opacity .2s ease; }.execution-scrim-enter-from, .execution-scrim-leave-to { opacity: 0; }.execution-drawer-enter-active, .execution-drawer-leave-active { transition: transform .24s cubic-bezier(.2,.8,.2,1), opacity .2s ease; }.execution-drawer-enter-from, .execution-drawer-leave-to { opacity: 0; transform: translateX(32px); } @media (prefers-reduced-motion: reduce) { .execution-scrim-enter-active, .execution-scrim-leave-active, .execution-drawer-enter-active, .execution-drawer-leave-active { transition-duration: .01ms !important; } }
@media (max-width: 768px) { .execution-drawer { z-index: 130; inset: 0; width: 100vw; min-width: 0; border: 0; border-radius: 0; }.execution-drawer-scrim { z-index: 120; } }
</style>
