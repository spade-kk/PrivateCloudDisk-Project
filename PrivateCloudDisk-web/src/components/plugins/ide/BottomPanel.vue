<template>
  <section class="bottom-panel" :class="{ 'bottom-panel--fullscreen': fullscreen }" aria-label="开发输出面板">
    <header class="bottom-panel__tabs">
      <button v-for="item in tabs" :key="item.value" class="bottom-panel__tab" :class="{ active: modelValue === item.value }" type="button" @click="$emit('update:modelValue', item.value)">
        <i :class="item.icon"></i>{{ item.label }}<span v-if="item.value === 'problems' && problemCount" class="bottom-panel__count">{{ problemCount }}</span>
      </button>
      <button class="bottom-panel__close" type="button" aria-label="最小化面板" title="最小化面板" @click="$emit('collapse')"><i class="fa fa-chevron-down"></i></button>
      <button class="bottom-panel__fullscreen" type="button" :aria-label="fullscreen ? '退出面板全屏' : '全屏查看当前面板'" :title="fullscreen ? '退出全屏' : '全屏查看'" @click="fullscreen = !fullscreen"><i :class="fullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i></button>
    </header>
    <div class="bottom-panel__body" role="log" aria-live="polite"><slot :name="modelValue"><p class="bottom-panel__empty">{{ emptyText }}</p></slot></div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
// [AUDIT FIX 3.4]：[CLOUDFLOW-IDE-001] 工作流 IDE 需要把可编译的 CloudFlow 源码作为
// 唯一事实来源展示；新增 DSL 预览页签，不改变既有插件 IDE 的输出、问题与执行日志语义。
const props = withDefaults(defineProps<{ modelValue?: 'output' | 'problems' | 'execution' | 'debug' | 'dsl'; problemCount?: number }>(), { modelValue: 'output', problemCount: 0 })
defineEmits<{ 'update:modelValue': [value: 'output' | 'problems' | 'execution' | 'debug' | 'dsl']; collapse: [] }>()
const tabs = [
  { value: 'output' as const, label: '输出', icon: 'fa fa-terminal' },
  { value: 'problems' as const, label: '问题', icon: 'fa fa-warning' },
  { value: 'execution' as const, label: '执行日志', icon: 'fa fa-play-circle' },
  { value: 'debug' as const, label: '调试控制台', icon: 'fa fa-bug' },
  { value: 'dsl' as const, label: 'DSL 预览', icon: 'fa fa-code' },
]
// [IDE-RESP-2026-08 / 5.9] 日志和 DSL 在手机上可独立全屏查看；不改变现有 Tab 数据。
const fullscreen = ref(false)
const emptyText = computed(() => ({ output: '等待校验或保存输出…', problems: '暂无问题', execution: '尚未运行插件', debug: '暂无调试信息', dsl: '画布变更后将在此生成 CloudFlow DSL…' }[props.modelValue]))
</script>

<style scoped>
.bottom-panel { display: flex; min-height: 0; flex-direction: column; color: #cbd5e1; }
.bottom-panel__tabs { display: flex; min-height: 36px; align-items: center; gap: 2px; padding: 0 8px; border-bottom: 1px solid #273244; background: #172033; }
.bottom-panel__tab { display: inline-flex; min-height: 32px; align-items: center; gap: 6px; padding: 0 10px; border-bottom: 2px solid transparent; color: #94a3b8; font-size: 11px; }
.bottom-panel__tab:hover,.bottom-panel__tab.active { border-bottom-color: #60a5fa; color: #f8fafc; }
.bottom-panel__count { display: inline-flex; min-width: 17px; height: 17px; align-items: center; justify-content: center; border-radius: 999px; background: #dc2626; color: #fff; font-size: 9px; }
.bottom-panel__close { margin-left: auto; min-width: 30px; min-height: 30px; color: #94a3b8; }
.bottom-panel__body { min-height: 100px; overflow: auto; padding: 12px; background: #111827; font-size: 12px; }
/* AUDIT FIX [2.4]：[CLOUDFLOW-IDE-TERMINAL-001] 插槽中的 CLI 文本统一保留换行并安全折行；Vue 文本插值负责 HTML 转义。 */
.bottom-panel__body :deep(.ide-terminal-text),
.bottom-panel__body :deep(.workflow-log-line),
.bottom-panel__body :deep(.workflow-problem__message) {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}
.bottom-panel__empty { color: #64748b; }
</style>
