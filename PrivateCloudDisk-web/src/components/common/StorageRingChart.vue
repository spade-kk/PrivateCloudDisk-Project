<template>
  <div class="storage-ring-chart flex flex-col items-center">
    <!-- 环形图 SVG -->
    <div class="relative" :style="{ width: size + 'px', height: size + 'px' }">
      <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`">
        <!-- 背景环 -->
        <circle
          :cx="center"
          :cy="center"
          :r="radius"
          fill="none"
          :stroke="bgColor"
          :stroke-width="strokeWidth"
        />
        <!-- 数据环 -->
        <circle
          v-for="(segment, i) in animatedSegments"
          :key="i"
          :cx="center"
          :cy="center"
          :r="radius"
          fill="none"
          :stroke="segment.color"
          :stroke-width="strokeWidth"
          :stroke-linecap="i === segments.length - 1 || segments.length === 1 ? 'round' : 'butt'"
          :stroke-dasharray="segment.dashArray"
          :stroke-dashoffset="segment.dashOffset"
          :transform="`rotate(-90 ${center} ${center})`"
          :style="{
            transition: 'stroke-dasharray 1s ease-out, stroke-dashoffset 1s ease-out',
            filter: `drop-shadow(0 1px 2px ${segment.color}40)`,
          }"
        />
      </svg>

      <!-- 中心文字 -->
      <div class="absolute inset-0 flex flex-col items-center justify-center">
        <span class="text-2xl font-bold text-neutral-800" :style="{ fontSize: labelSize + 'px' }">
          {{ percentage }}%
        </span>
        <span class="mt-0.5 text-xs text-neutral-400">已使用</span>
      </div>
    </div>

    <!-- 图例 -->
    <div v-if="showLegend" class="mt-4 flex flex-wrap justify-center gap-4">
      <div
        v-for="(seg, i) in segments"
        :key="i"
        class="flex items-center gap-2 text-xs"
      >
        <span
          class="inline-block h-2.5 w-2.5 rounded-full"
          :style="{ backgroundColor: seg.color }"
        ></span>
        <span class="text-neutral-500">{{ seg.label }}</span>
        <span class="font-medium text-neutral-700">{{ seg.value }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Segment {
  /** 数值 */
  value: number
  /** 颜色 */
  color: string
  /** 标签 */
  label: string
}

const props = withDefaults(defineProps<{
  /** 总大小 */
  total?: number
  /** 数据段 */
  segments?: Segment[]
  /** 图表尺寸 */
  size?: number
  /** 环宽度 */
  strokeWidth?: number
  /** 背景环颜色 */
  bgColor?: string
  /** 是否显示图例 */
  showLegend?: boolean
}>(), {
  total: 100,
  segments: () => [],
  size: 180,
  strokeWidth: 14,
  bgColor: '#f3f4f6',
  showLegend: true,
})

const center = computed(() => props.size / 2)
const radius = computed(() => (props.size - props.strokeWidth) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)

const percentage = computed(() => {
  const sum = props.segments.reduce((s, seg) => s + seg.value, 0)
  if (props.total <= 0) return 0
  return Math.round((sum / props.total) * 100)
})

const labelSize = computed(() => {
  if (props.size >= 200) return 28
  if (props.size >= 160) return 22
  return 18
})

const animatedSegments = computed(() => {
  let offset = 0
  return props.segments.map((seg) => {
    const segPercent = props.total > 0 ? seg.value / props.total : 0
    const dashLength = segPercent * circumference.value
    const result = {
      ...seg,
      dashArray: `${dashLength} ${circumference.value - dashLength}`,
      dashOffset: -offset,
    }
    offset += dashLength
    return result
  })
})
</script>