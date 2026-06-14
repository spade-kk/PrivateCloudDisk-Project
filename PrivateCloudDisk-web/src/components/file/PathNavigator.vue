<template>
  <section class="path-navigator">
    <div class="path-title-row">
      <button
        class="path-back"
        :disabled="!parentNode"
        :title="parentNode ? '返回上一级' : '已在根目录'"
        @click="goParent"
      >
        <i class="fa fa-angle-left"></i>
      </button>
      <div class="path-current">
        <div class="path-current-icon">
          <i class="fa fa-folder-open"></i>
        </div>
        <div class="min-w-0">
          <p class="text-[11px] font-semibold uppercase tracking-wide text-neutral-400">当前位置</p>
          <h1 class="truncate text-lg font-bold leading-tight text-neutral-800 sm:text-xl">{{ currentTitle }}</h1>
        </div>
      </div>
    </div>

    <nav class="path-crumbs" aria-label="文件路径">
      <button class="crumb-root" @click="$emit('home')">
        <i class="fa fa-home"></i>
        <span>我的网盘</span>
      </button>
      <template v-for="(item, idx) in visibleCrumbs" :key="item.node_id || idx">
        <span class="crumb-separator"><i class="fa fa-angle-right"></i></span>
        <span v-if="item.isEllipsis" class="crumb-ellipsis">...</span>
        <button
          v-else
          class="crumb-link"
          :class="{ 'is-current': item.originalIndex === normalizedPath.length - 1 }"
          :disabled="item.originalIndex === normalizedPath.length - 1"
          @click="$emit('navigate', item)"
        >
          {{ item.node_name }}
        </button>
      </template>
    </nav>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  pathStack: { type: Array, default: () => [] },
})
const emit = defineEmits(['home', 'navigate'])

const normalizedPath = computed(() => props.pathStack || [])
const currentNode = computed(() => normalizedPath.value[normalizedPath.value.length - 1] || null)
const parentNode = computed(() => normalizedPath.value.length > 1 ? normalizedPath.value[normalizedPath.value.length - 2] : null)
const currentTitle = computed(() => currentNode.value?.node_name || '我的网盘')

const visibleCrumbs = computed(() => {
  const withoutRoot = normalizedPath.value.slice(1).map((item, index) => ({
    ...item,
    originalIndex: index + 1,
  }))
  if (withoutRoot.length <= 3) return withoutRoot
  return [
    withoutRoot[0],
    { node_id: 'ellipsis', node_name: '...', isEllipsis: true, originalIndex: -1 },
    ...withoutRoot.slice(-2),
  ]
})

function goParent() {
  if (parentNode.value) {
    emit('navigate', parentNode.value)
  }
}
</script>

<style scoped>
.path-navigator {
  display: flex;
  min-width: 0;
  width: 100%;
  flex-direction: column;
  gap: 12px;
}

.path-title-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.path-back {
  display: inline-flex;
  height: 38px;
  width: 38px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(228, 231, 237, 0.9);
  border-radius: 12px;
  background: #fff;
  color: #606266;
  transition: all 160ms ease;
}

.path-back:not(:disabled):hover {
  border-color: rgba(22, 93, 255, 0.28);
  background: rgba(22, 93, 255, 0.06);
  color: #165dff;
}

.path-back:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.path-current {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 11px;
}

.path-current-icon {
  display: flex;
  height: 42px;
  width: 42px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(22, 93, 255, 0.14), rgba(54, 207, 201, 0.12));
  color: #165dff;
}

.path-crumbs {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  overflow-x: auto;
  border: 1px solid rgba(228, 231, 237, 0.8);
  border-radius: 12px;
  background: rgba(245, 247, 250, 0.75);
  padding: 8px 10px;
  scrollbar-width: none;
}

.path-crumbs::-webkit-scrollbar {
  display: none;
}

.crumb-root,
.crumb-link,
.crumb-ellipsis {
  display: inline-flex;
  max-width: 220px;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  border-radius: 9px;
  color: #606266;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  padding: 7px 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.crumb-root,
.crumb-link {
  transition: background-color 160ms ease, color 160ms ease;
}

.crumb-root:hover,
.crumb-link:not(:disabled):hover {
  background: #fff;
  color: #165dff;
}

.crumb-link.is-current {
  background: #fff;
  color: #303133;
  cursor: default;
}

.crumb-separator {
  flex-shrink: 0;
  color: #c0c6cf;
  font-size: 12px;
}

.crumb-ellipsis {
  max-width: none;
  color: #909399;
}

@media (max-width: 640px) {
  .path-current-icon {
    height: 38px;
    width: 38px;
    border-radius: 12px;
  }

  .crumb-root,
  .crumb-link {
    max-width: 160px;
  }
}
</style>
