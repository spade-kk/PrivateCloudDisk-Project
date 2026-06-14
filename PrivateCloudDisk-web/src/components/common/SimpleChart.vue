<template>
  <div class="responsive-panel overflow-hidden p-4 sm:p-5">
    <div class="mb-4 flex items-center justify-between">
      <h3 class="text-base font-semibold text-neutral-700">{{ title }}</h3>
      <div v-if="periods" class="flex gap-1">
        <button
          v-for="p in periods"
          :key="p.value"
          @click="$emit('period-change', p.value)"
          :class="['rounded-lg px-3 py-1 text-xs font-medium transition', period === p.value ? 'bg-primary text-white' : 'bg-neutral-100 text-neutral-500 hover:bg-neutral-200']"
        >
          {{ p.label }}
        </button>
      </div>
    </div>
    <div :style="{ height: height + 'px' }" class="relative">
      <!-- 简易柱状图 / 折线图区域 -->
      <div class="flex h-full items-end gap-1">
        <div
          v-for="(item, i) in data"
          :key="i"
          class="group relative flex flex-1 flex-col items-center justify-end"
          :style="{ height: '100%' }"
        >
          <!-- 柱状条 -->
          <div
            class="w-full rounded-t transition-all duration-300 hover:opacity-80"
            :class="chartColor"
            :style="{ height: getBarHeight(item) + '%' }"
          ></div>
          <!-- Tooltip -->
          <div class="absolute -top-10 left-1/2 z-10 -translate-x-1/2 whitespace-nowrap rounded-lg bg-neutral-800 px-2 py-1 text-xs text-white opacity-0 transition-opacity group-hover:opacity-100">
            {{ item.label }}: {{ item.value }}
          </div>
        </div>
      </div>
      <!-- X 轴标签 -->
      <div class="mt-2 flex justify-between text-xs text-neutral-400">
        <span v-for="(item, i) in data" :key="i" class="truncate text-center" :style="{ width: 100 / data.length + '%' }">
          {{ item.label }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  title: { type: String, required: true },
  data: { type: Array, default: () => [] },
  height: { type: Number, default: 200 },
  chartColor: { type: String, default: 'bg-primary' },
  period: { type: String, default: '' },
  periods: { type: Array, default: undefined },
})
defineEmits(['period-change'])

const maxValue = computed(() => {
  if (!props.data.length) return 1
  return Math.max(...props.data.map(d => d.value || 0), 1)
})

function getBarHeight(item) {
  return ((item.value || 0) / maxValue.value) * 100
}
</script>