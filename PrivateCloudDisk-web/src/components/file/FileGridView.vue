<template>
  <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
    <div
      v-for="node in nodes"
      :key="node.node_id"
      class="relative bg-white rounded-lg shadow-card p-3 transition-all duration-200 hover:shadow-hover hover:-translate-y-1 cursor-pointer group"
      :class="{ 'ring-2 ring-primary': isSelected(node.node_id) }"
    >
      <!-- 复选框 -->
      <div class="absolute top-2 left-2 z-10" @click.stop>
        <input
          type="checkbox"
          :checked="isSelected(node.node_id)"
          @change="toggleSelect(node.node_id)"
          class="w-4 h-4 text-primary rounded border-neutral-300 focus:ring-primary"
        />
      </div>
      <!-- 星标按钮 -->
      <div class="absolute top-2 right-2 z-10" @click.stop>
        <button @click="$emit('star', node)" class="text-neutral-300 hover:text-warning transition">
          <i :class="isStarred(node.node_id) ? 'fa fa-star text-warning' : 'fa fa-star-o'"></i>
        </button>
      </div>
      <!-- 内容 -->
      <div class="flex flex-col items-center pt-4" @click="$emit('itemClick', node)">
        <div class="w-16 h-16 rounded-lg bg-neutral-50 flex items-center justify-center mb-3">
          <i :class="['fa', iconClass(node), node.node_type === 'FOLDER' ? 'fa-folder text-primary text-3xl' : 'text-3xl']"></i>
        </div>
        <h3 class="text-ellipsis-1 text-center font-medium text-neutral-700 w-full">{{ node.node_name }}</h3>
        <p class="text-xs text-neutral-400 mt-1">{{ node.node_type === 'FOLDER' ? '文件夹' : getFileExtension(node.node_name) }}</p>
      </div>
      <!-- 操作菜单（悬浮显示） -->
      <div class="absolute bottom-2 right-2 opacity-0 group-hover:opacity-100 transition">
        <div class="flex space-x-1 bg-white rounded shadow p-1">
          <button @click.stop="$emit('action', node, 'download')" class="text-primary text-xs p-1 hover:bg-neutral-100 rounded" title="下载"><i class="fa fa-download"></i></button>
          <button @click.stop="$emit('action', node, 'rename')" class="text-neutral-500 text-xs p-1 hover:bg-neutral-100 rounded" title="重命名"><i class="fa fa-pencil"></i></button>
          <button @click.stop="$emit('action', node, 'delete')" class="text-danger text-xs p-1 hover:bg-neutral-100 rounded" title="删除"><i class="fa fa-trash"></i></button>
          <button @click.stop="$emit('action', node, 'detail')" class="text-neutral-500 text-xs p-1 hover:bg-neutral-100 rounded" title="详情"><i class="fa fa-info-circle"></i></button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { getFileExtension } from '@/utils/helpers'
import { getFileIconClass } from '@/utils/fileIcon'

const props = defineProps({
  nodes: { type: Array, required: true },
  selectedIds: { type: Set, default: () => new Set() },
  starredIds: { type: Set, default: () => new Set() },
})

const emit = defineEmits(['itemClick', 'selection-change', 'action', 'star'])

const iconClass = (node) => getFileIconClass(node.node_name)
const isSelected = (id) => props.selectedIds.has(id)
const isStarred = (id) => props.starredIds.has(id)
const toggleSelect = (id) => emit('selection-change', id)
</script>

<style scoped>
.text-ellipsis-1 {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.group:hover .group-hover\:opacity-100 {
  opacity: 1;
}
</style>