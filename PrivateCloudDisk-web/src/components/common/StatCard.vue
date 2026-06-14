<template>
  <div
    class="responsive-panel flex flex-col gap-2 p-4 sm:p-5 transition-all hover:shadow-md cursor-pointer"
    :class="clickable ? 'cursor-pointer' : ''"
    @click="clickable && $emit('click')"
  >
    <div class="flex items-center justify-between">
      <span class="text-xs font-medium uppercase tracking-wider text-neutral-400">{{ title }}</span>
      <span :class="['rounded-full px-2 py-0.5 text-xs font-medium', trendClass]">
        <i :class="trendIcon"></i> {{ trend }}
      </span>
    </div>
    <div class="flex items-baseline gap-2">
      <span class="text-2xl font-bold text-neutral-800 sm:text-3xl">{{ value }}</span>
      <span v-if="unit" class="text-sm text-neutral-400">{{ unit }}</span>
    </div>
    <p v-if="description" class="text-xs text-neutral-400">{{ description }}</p>
    <!-- 进度条 -->
    <div v-if="progress !== undefined" class="mt-1">
      <div class="h-1.5 w-full rounded-full bg-neutral-200">
        <div
          class="h-1.5 rounded-full transition-all duration-500"
          :class="progressColor"
          :style="{ width: Math.min(progress, 100) + '%' }"
        ></div>
      </div>
      <p class="mt-1 text-xs text-neutral-400">{{ progressLabel }}</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  title: { type: String, required: true },
  value: { type: [String, Number], required: true },
  unit: { type: String, default: '' },
  description: { type: String, default: '' },
  trend: { type: String, default: '' },
  trendUp: { type: Boolean, default: true },
  progress: { type: Number, default: undefined },
  progressLabel: { type: String, default: '' },
  progressColor: { type: String, default: 'bg-primary' },
  clickable: { type: Boolean, default: false },
})
defineEmits(['click'])

const trendClass = computed(() => {
  if (!props.trend) return 'hidden'
  return props.trendUp ? 'bg-success/10 text-success' : 'bg-danger/10 text-danger'
})

const trendIcon = computed(() => {
  if (!props.trend) return ''
  return props.trendUp ? 'fa fa-arrow-up' : 'fa fa-arrow-down'
})
</script>