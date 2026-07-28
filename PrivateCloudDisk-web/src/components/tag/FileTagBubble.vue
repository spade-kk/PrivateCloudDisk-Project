<template>
  <div
    v-if="tags.length"
    ref="bubbleRef"
    class="file-tag-bubble"
    :class="{ 'is-compact': compact }"
    :title="allTagNames"
    role="list"
    aria-label="文件标签"
  >
    <TagBadge
      v-for="tag in visibleTags"
      :key="tag.tag_id"
      :tag="tag"
      role="listitem"
    />
    <span v-if="hiddenCount > 0" class="file-tag-more" :aria-label="`另有 ${hiddenCount} 个标签`">
      +{{ hiddenCount }}
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import TagBadge from './TagBadge.vue'
import type { TagVO } from '@/api/modules/tags'

const props = withDefaults(defineProps<{
  tags: TagVO[]
  compact?: boolean
  maxRows?: number
}>(), {
  compact: false,
  maxRows: 2,
})

const bubbleRef = ref<HTMLElement | null>(null)
const visibleCount = ref(props.tags.length)
let resizeObserver: ResizeObserver | null = null

const allTagNames = computed(() => props.tags.map((tag) => tag.tag_name).join('、'))
const visibleTags = computed(() => props.tags.slice(0, visibleCount.value))
const hiddenCount = computed(() => Math.max(0, props.tags.length - visibleCount.value))

/**
 * AUDIT FIX [2.1]（需求二-1.2/1.3/1.4）：
 * 原行为固定显示 2/3 个标签后立即折叠；新行为根据容器实际宽度、允许行数和标签文本宽度
 * 动态计算可展示数量。计算结果仍预留“+N”空间，窄屏不会撑破文件项目。
 */
function recalculateVisibleTags() {
  const element = bubbleRef.value
  if (!element || !props.tags.length) {
    visibleCount.value = 0
    return
  }
  const availablePerRow = Math.max(72, element.clientWidth - 12)
  const totalBudget = availablePerRow * Math.max(1, props.maxRows)
  const moreBadgeWidth = props.tags.length > 1 ? 34 : 0
  let consumed = 0
  let count = 0

  for (const tag of props.tags) {
    // 10px 字号下中文近似等宽；设上下界防止极短/极长标签破坏估算。
    const estimatedWidth = Math.min(106, Math.max(38, Array.from(tag.tag_name).length * 11 + 24))
    const reserve = count < props.tags.length - 1 ? moreBadgeWidth : 0
    if (consumed + estimatedWidth + reserve > totalBudget) break
    consumed += estimatedWidth + 4
    count += 1
  }
  visibleCount.value = Math.max(1, count)
}

watch(
  () => [props.tags, props.maxRows, props.compact],
  () => void nextTick(recalculateVisibleTags),
  { deep: true },
)

onMounted(() => {
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(recalculateVisibleTags)
    if (bubbleRef.value) resizeObserver.observe(bubbleRef.value)
  } else {
    // Safari 旧版本降级：使用窗口尺寸变化触发重算，保证页面不会因缺少 API 白屏。
    window.addEventListener('resize', recalculateVisibleTags, { passive: true })
  }
  recalculateVisibleTags()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  window.removeEventListener('resize', recalculateVisibleTags)
})
</script>

<style scoped>
.file-tag-bubble {
  display: flex;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  /*
   * 【需求十二改动说明】
   * 原行为：容器使用微信气泡的背景、边框、阴影和左侧箭头。
   * 新行为：仅保留响应式换行和动态折叠计算，标签颜色由 TagBadge 自身表达。
   */
  padding: 2px 0;
}

.file-tag-bubble.is-compact {
  justify-content: center;
  padding: 1px 0;
}

.file-tag-more {
  flex: none;
  color: #606266;
  font-size: 10px;
  font-weight: 600;
  line-height: 18px;
}
</style>
