<template>
  <section class="bottom-panel" aria-label="开发输出面板">
    <header class="bottom-panel__tabs">
      <button v-for="item in tabs" :key="item.value" class="bottom-panel__tab" :class="{ active: modelValue === item.value }" type="button" @click="$emit('update:modelValue', item.value)">
        <i :class="item.icon"></i>{{ item.label }}<span v-if="item.value === 'problems' && problemCount" class="bottom-panel__count">{{ problemCount }}</span>
      </button>
      <button class="bottom-panel__close" type="button" aria-label="最小化面板" title="最小化面板" @click="$emit('collapse')"><i class="fa fa-chevron-down"></i></button>
    </header>
    <div class="bottom-panel__body" role="log" aria-live="polite"><slot :name="modelValue"><p class="bottom-panel__empty">{{ emptyText }}</p></slot></div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
const props = withDefaults(defineProps<{ modelValue?: 'output' | 'problems' | 'execution' | 'debug'; problemCount?: number }>(), { modelValue: 'output', problemCount: 0 })
defineEmits<{ 'update:modelValue': [value: 'output' | 'problems' | 'execution' | 'debug']; collapse: [] }>()
const tabs = [
  { value: 'output' as const, label: '输出', icon: 'fa fa-terminal' },
  { value: 'problems' as const, label: '问题', icon: 'fa fa-warning' },
  { value: 'execution' as const, label: '执行日志', icon: 'fa fa-play-circle' },
  { value: 'debug' as const, label: '调试控制台', icon: 'fa fa-bug' },
]
const emptyText = computed(() => ({ output: '等待校验或保存输出…', problems: '暂无问题', execution: '尚未运行插件', debug: '暂无调试信息' }[props.modelValue]))
</script>

<style scoped>
.bottom-panel { display: flex; min-height: 0; flex-direction: column; color: #cbd5e1; }
.bottom-panel__tabs { display: flex; min-height: 36px; align-items: center; gap: 2px; padding: 0 8px; border-bottom: 1px solid #273244; background: #172033; }
.bottom-panel__tab { display: inline-flex; min-height: 32px; align-items: center; gap: 6px; padding: 0 10px; border-bottom: 2px solid transparent; color: #94a3b8; font-size: 11px; }
.bottom-panel__tab:hover,.bottom-panel__tab.active { border-bottom-color: #60a5fa; color: #f8fafc; }
.bottom-panel__count { display: inline-flex; min-width: 17px; height: 17px; align-items: center; justify-content: center; border-radius: 999px; background: #dc2626; color: #fff; font-size: 9px; }
.bottom-panel__close { margin-left: auto; min-width: 30px; min-height: 30px; color: #94a3b8; }
.bottom-panel__body { min-height: 100px; overflow: auto; padding: 12px; background: #111827; font-size: 12px; }
.bottom-panel__empty { color: #64748b; }
</style>
