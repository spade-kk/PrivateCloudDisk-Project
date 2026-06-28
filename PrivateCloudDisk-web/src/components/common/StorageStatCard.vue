<template>
  <div class="storage-stat-card group relative overflow-hidden rounded-2xl border border-neutral-100 bg-white p-5 shadow-sm transition-all duration-300 hover:shadow-md hover:-translate-y-0.5">
    <!-- 背景装饰 -->
    <div
      class="absolute -right-4 -top-4 h-20 w-20 rounded-full opacity-10 transition-opacity duration-300 group-hover:opacity-20"
      :style="{ backgroundColor: accentColor }"
    ></div>

    <div class="relative">
      <!-- 图标 + 标题 -->
      <div class="mb-3 flex items-center gap-3">
        <div
          class="flex h-10 w-10 items-center justify-center rounded-xl transition-transform duration-300 group-hover:scale-110"
          :style="{ backgroundColor: accentColor + '18', color: accentColor }"
        >
          <i :class="icon" class="text-lg"></i>
        </div>
        <span class="text-sm font-medium text-neutral-500">{{ title }}</span>
      </div>

      <!-- 格式化数值 -->
      <div class="flex items-baseline gap-1.5">
        <span class="text-2xl font-bold text-neutral-800 sm:text-3xl">
          {{ formattedValue }}
        </span>
        <span v-if="formattedUnit" class="text-sm text-neutral-400">{{ formattedUnit }}</span>
      </div>

      <!-- 进度条 -->
      <div v-if="progress !== undefined" class="mt-3">
        <div class="h-2 w-full rounded-full bg-neutral-100 overflow-hidden">
          <div
            class="h-full rounded-full transition-all duration-700 ease-out"
            :style="{ width: progressClamped + '%', backgroundColor: progressColor }"
          ></div>
        </div>
        <p class="mt-1.5 text-xs text-neutral-400">{{ progressLabel }}</p>
      </div>

      <!-- 副标题 -->
      <p v-if="subtitle" class="mt-2 text-xs text-neutral-400">{{ subtitle }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps({
  /** 卡片标题 */
  title: { type: String, required: true },
  /** 字节数（自动格式化） */
  bytes: { type: Number, default: 0 },
  /** 图标 class */
  icon: { type: String, default: 'fa fa-hdd-o' },
  /** 强调色 */
  accentColor: { type: String, default: '#3b82f6' },
  /** 进度百分比 0-100 */
  progress: { type: Number, default: undefined },
  /** 进度条颜色 */
  progressColor: { type: String, default: '#3b82f6' },
  /** 进度条标签 */
  progressLabel: { type: String, default: '' },
  /** 副标题 */
  subtitle: { type: String, default: '' },
})

const progressClamped = computed(() => Math.min(100, Math.max(0, props.progress ?? 0)))

const { formattedValue, formattedUnit } = computed(() => {
  if (!props.bytes || props.bytes === 0) return { formattedValue: '0', formattedUnit: 'B' }
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(props.bytes) / Math.log(k))
  const val = parseFloat((props.bytes / Math.pow(k, i)).toFixed(2))
  return { formattedValue: String(val), formattedUnit: sizes[i] }
}).value
</script>