<template>
  <Teleport to="body">
    <div v-if="open" class="workflow-onboarding" role="presentation" @keydown.esc="$emit('update:open', false)">
      <div class="workflow-onboarding__shade" @click="$emit('update:open', false)"></div>
      <div class="workflow-onboarding__highlight" :style="highlightStyle" aria-hidden="true"></div>
      <section class="workflow-onboarding__card" :style="cardStyle" role="dialog" aria-modal="true" aria-label="CloudFlow IDE 新手指引">
        <p class="workflow-onboarding__progress">{{ stepIndex + 1 }} / {{ steps.length }} · CloudFlow IDE</p>
        <h2>{{ currentStep.title }}</h2>
        <p>{{ currentStep.description }}</p>
        <footer>
          <button type="button" @click="$emit('update:open', false)">跳过</button>
          <span></span>
          <button v-if="stepIndex > 0" type="button" @click="stepIndex -= 1">上一步</button>
          <button class="workflow-onboarding__primary" type="button" @click="next"><i :class="stepIndex === steps.length - 1 ? 'fa fa-check' : 'fa fa-arrow-right' "></i>{{ stepIndex === steps.length - 1 ? '开始编辑' : '下一步' }}</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
// [CF-IDE-2026-08 / 2.17、13.1] 首次使用引导仅说明现有可编译的 IDE 区域，
// 不创建额外工作流状态，也不改变 CloudFlow DSL 的唯一事实来源。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [value: boolean]; complete: [] }>()
const stepIndex = ref(0)
const targetRect = ref<DOMRect | null>(null)
const steps = [
  { selector: '[data-workflow-guide="library"]', title: '从节点库开始', description: '拖拽触发器、任务和控制流节点到无限画布。每种可执行节点均映射到一个 CloudFlow 语法结构。' },
  { selector: '[data-workflow-guide="canvas"]', title: '用连线表达语义', description: '普通连线表示依赖；条件、循环、并行和异常处理使用对应的端口与颜色。右键可快速复制、编辑或删除。' },
  { selector: '[data-workflow-guide="inspector"]', title: '配置变量与能力', description: '在属性面板中选择能力、输入参数、变量引用、表达式、超时和重试；修改会立即投影到画布与 DSL。' },
  { selector: '[data-workflow-guide="toolbar"]', title: '以编译校验为准', description: '保存前会调用 CloudFlow Runtime 编译当前 DSL。通过后才可保存；错误会同时显示在问题面板与对应节点。' },
]
const currentStep = computed(() => steps[stepIndex.value]!)
const highlightStyle = computed(() => {
  const rect = targetRect.value
  if (!rect) return { display: 'none' }
  return { left: `${Math.max(4, rect.left - 6)}px`, top: `${Math.max(4, rect.top - 6)}px`, width: `${Math.max(0, rect.width + 12)}px`, height: `${Math.max(0, rect.height + 12)}px` }
})
const cardStyle = computed(() => {
  const rect = targetRect.value
  if (!rect) return { left: '50%', top: '50%', transform: 'translate(-50%, -50%)' }
  const below = rect.bottom + 18
  const top = below + 220 < window.innerHeight ? below : Math.max(16, rect.top - 220)
  return { left: `${Math.max(16, Math.min(window.innerWidth - 360, rect.left))}px`, top: `${top}px` }
})
function locateTarget() {
  if (!props.open) return
  targetRect.value = document.querySelector(currentStep.value.selector)?.getBoundingClientRect() || null
}
function next() { if (stepIndex.value >= steps.length - 1) { emit('complete'); emit('update:open', false); return } stepIndex.value += 1; requestAnimationFrame(locateTarget) }
watch(() => [props.open, stepIndex.value], () => requestAnimationFrame(locateTarget), { immediate: true })
onMounted(() => window.addEventListener('resize', locateTarget))
onBeforeUnmount(() => window.removeEventListener('resize', locateTarget))
</script>

<style scoped>
.workflow-onboarding { position:fixed;z-index:1000;inset:0; }.workflow-onboarding__shade { position:absolute;inset:0;background:rgb(15 23 42 / .64); }.workflow-onboarding__highlight { position:fixed;z-index:2;border:2px solid #60a5fa;border-radius:12px;box-shadow:0 0 0 9999px transparent,0 0 0 5px rgb(96 165 250 / .22),0 0 32px rgb(96 165 250 / .7);pointer-events:none;transition:all .2s ease; }.workflow-onboarding__card { position:fixed;z-index:3;width:min(340px,calc(100vw - 32px));padding:18px;border:1px solid rgb(191 219 254 / .9);border-radius:14px;background:#fff;box-shadow:0 24px 72px rgb(15 23 42 / .35);color:#1e293b; }.workflow-onboarding__progress { margin:0 0 8px;color:#2563eb;font-size:10px;font-weight:800;letter-spacing:.05em; }.workflow-onboarding__card h2 { margin:0 0 9px;font-size:16px; }.workflow-onboarding__card>p:not(.workflow-onboarding__progress) { margin:0;color:#475569;font-size:12px;line-height:1.65; }.workflow-onboarding__card footer { display:flex;align-items:center;gap:8px;margin-top:18px; }.workflow-onboarding__card footer span { flex:1; }.workflow-onboarding__card button { min-height:32px;padding:0 10px;border-radius:7px;color:#475569;font-size:11px;font-weight:700; }.workflow-onboarding__card button:hover { background:#f1f5f9; }.workflow-onboarding__primary { background:#2563eb!important;color:#fff!important; }.workflow-onboarding__primary i { margin-right:5px; } @media (prefers-reduced-motion:reduce) { .workflow-onboarding__highlight { transition:none; } }
</style>
