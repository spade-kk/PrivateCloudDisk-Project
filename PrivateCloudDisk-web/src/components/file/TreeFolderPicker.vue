<template>
  <div class="tree-picker">
    <div ref="scrollContainer" class="scroll-container" @scroll="onScroll">
      <div class="fade-left" :class="{ visible: showLeftFade }"></div>
      <div class="fade-right" :class="{ visible: showRightFade }"></div>

      <div class="columns-wrapper">
        <div
          v-for="(column, colIndex) in folderTree"
          :key="colIndex"
          class="column"
          :class="{ 'column-active': selectedNodeId && isNodeInColumn(column, selectedNodeId) }"
        >
          <div class="column-header">
            <i class="fa fa-folder-o mr-1.5 text-primary/70"></i>
            <span class="truncate">{{ colIndex === 0 ? '根目录' : (breadcrumbNodes[colIndex - 1]?.node_name || '') }}</span>
            <span class="ml-auto pl-2 text-[10px] text-neutral-400">{{ column.length }}</span>
          </div>

          <div class="column-body">
            <div
              v-for="node in column"
              :key="node.node_id"
              class="row-item"
              :class="{
                'row-selected': selectedNodeId === node.node_id,
              }"
              @click="handleNodeClick(node, colIndex)"
            >
              <span class="row-icon" :class="isFolderNode(node) ? 'folder' : 'file'">
                <i :class="isFolderNode(node) ? 'fa fa-folder' : 'fa fa-file-o'"></i>
              </span>
              <span class="row-name truncate">{{ node.node_name }}</span>
              <i v-if="isFolderNode(node)" class="fa fa-angle-right row-arrow"></i>
            </div>

            <div v-if="column.length === 0" class="empty-state">
              <i class="fa fa-inbox mb-1 text-xl text-neutral-300"></i>
              <span>空文件夹</span>
            </div>
          </div>
        </div>

        <div v-if="loadingColumn !== null" class="column column-loading">
          <div class="column-header">
            <i class="fa fa-spinner fa-spin mr-1.5 text-primary/70"></i>
            <span>加载中</span>
          </div>
          <div class="column-body flex items-center justify-center">
            <div class="loading-dots">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'

const props = defineProps({
  folderTree: {
    type: Array,
    default: () => [],
  },
  selectedNodeId: {
    type: String,
    default: '',
  },
  loadingColumn: {
    type: Number,
    default: null,
  },
  breadcrumbNodes: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['select', 'expand'])

const scrollContainer = ref(null)
const showLeftFade = ref(false)
const showRightFade = ref(false)

const onScroll = () => {
  if (!scrollContainer.value) return
  const el = scrollContainer.value
  showLeftFade.value = el.scrollLeft > 8
  showRightFade.value = el.scrollLeft + el.clientWidth < el.scrollWidth - 8
}

watch(
  () => props.folderTree.length,
  async () => {
    await nextTick()
    if (scrollContainer.value) {
      scrollContainer.value.scrollTo({
        left: scrollContainer.value.scrollWidth,
        behavior: 'smooth',
      })
    }
    onScroll()
  }
)

const isFolderNode = (node) => {
  if (node.node_type) return node.node_type === 'FOLDER' || node.node_type === 'folder'
  if (node.type) return node.type === 'folder' || node.type === 'FOLDER'
  return true
}

const isNodeInColumn = (column, nodeId) => {
  return column.some((n) => n.node_id === nodeId)
}

const handleNodeClick = (node, colIndex) => {
  emit('select', { node, colIndex })
  if (isFolderNode(node)) {
    emit('expand', { node, colIndex })
  }
}
</script>

<style scoped>
.tree-picker {
  width: 100%;
}

.scroll-container {
  position: relative;
  display: flex;
  height: 18rem;
  overflow-x: auto;
  overflow-y: hidden;
  border: 1px solid rgb(229 231 235);
  border-radius: 0.5rem;
  background-color: #fafafa;
  scroll-behavior: smooth;
}

.scroll-container::-webkit-scrollbar {
  height: 10px;
}

.scroll-container::-webkit-scrollbar-track {
  background: #f5f5f5;
  border-radius: 0 0 0.5rem 0.5rem;
}

.scroll-container::-webkit-scrollbar-thumb {
  background: linear-gradient(180deg, #d4d4d4, #a3a3a3);
  border-radius: 5px;
  border: 2px solid #f5f5f5;
}

.scroll-container::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(180deg, #737373, #525252);
}

.columns-wrapper {
  display: flex;
  height: 100%;
  padding-right: 0.5rem;
}

.fade-left,
.fade-right {
  position: absolute;
  top: 0;
  bottom: 10px;
  width: 40px;
  pointer-events: none;
  z-index: 10;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.fade-left {
  left: 0;
  background: linear-gradient(90deg, #fafafa 0%, transparent 100%);
  border-top-left-radius: 0.5rem;
  border-bottom-left-radius: 0.5rem;
}

.fade-right {
  right: 0;
  background: linear-gradient(-90deg, #fafafa 0%, transparent 100%);
  border-top-right-radius: 0.5rem;
  border-bottom-right-radius: 0.5rem;
}

.fade-left.visible,
.fade-right.visible {
  opacity: 1;
}

.column {
  display: flex;
  min-width: 200px;
  max-width: 260px;
  flex-shrink: 0;
  flex-direction: column;
  border-right: 1px solid rgb(229 231 235);
  background: white;
  transition: background-color 0.15s ease;
}

.column-active {
  background: linear-gradient(180deg, #eff6ff 0%, #ffffff 12%);
}

.column-loading {
  background: linear-gradient(180deg, #f5f5f5 0%, #fafafa 100%);
  opacity: 0.85;
}

.column:last-of-type {
  border-right: none;
}

.column-header {
  display: flex;
  align-items: center;
  padding: 0.5rem 0.75rem;
  font-size: 0.75rem;
  font-weight: 500;
  color: #525252;
  background: linear-gradient(180deg, #fafafa 0%, #f5f5f5 100%);
  border-bottom: 1px solid rgb(229 231 235);
}

.column-body {
  flex: 1;
  overflow-y: auto;
  padding: 0.25rem 0;
}

.column-body::-webkit-scrollbar {
  width: 6px;
}

.column-body::-webkit-scrollbar-track {
  background: transparent;
}

.column-body::-webkit-scrollbar-thumb {
  background: #d4d4d4;
  border-radius: 3px;
}

.column-body::-webkit-scrollbar-thumb:hover {
  background: #a3a3a3;
}

.row-item {
  display: flex;
  align-items: center;
  padding: 0.375rem 0.75rem;
  font-size: 0.875rem;
  cursor: pointer;
  color: #404040;
  transition: background-color 0.12s ease, color 0.12s ease;
  border-radius: 4px;
  margin: 1px 4px;
}

.row-item:hover {
  background-color: #eff6ff;
  color: #1d4ed8;
}

.row-selected {
  background: linear-gradient(90deg, #2563eb 0%, #3b82f6 100%) !important;
  color: white !important;
  box-shadow: 0 1px 2px rgba(37, 99, 235, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.1);
}

.row-selected .row-icon,
.row-selected .row-arrow {
  color: rgba(255, 255, 255, 0.9) !important;
}

.row-icon {
  display: flex;
  width: 20px;
  margin-right: 0.5rem;
  justify-content: center;
  align-items: center;
}

.row-icon.folder {
  color: #f59e0b;
}

.row-icon.file {
  color: #9ca3af;
}

.row-name {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row-arrow {
  margin-left: 0.25rem;
  color: #a3a3a3;
  font-size: 0.875rem;
  opacity: 0.7;
}

.row-item:hover .row-arrow {
  opacity: 1;
  color: #3b82f6;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 2rem 1rem;
  font-size: 0.75rem;
  color: #9ca3af;
}

.loading-dots {
  display: flex;
  gap: 6px;
}

.loading-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #9ca3af;
  animation: bounce 1.4s ease-in-out infinite both;
}

.loading-dots span:nth-child(1) {
  animation-delay: -0.32s;
}

.loading-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
