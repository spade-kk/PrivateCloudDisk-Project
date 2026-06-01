<template>
  <div class="bg-white rounded-lg shadow-card overflow-hidden">
    <div class="grid grid-cols-12 bg-neutral-50 py-2 px-4 font-medium text-neutral-600 border-b border-neutral-200">
      <div class="col-span-1 flex items-center">
        <input type="checkbox" :checked="allSelected" @change="toggleSelectAll" class="w-4 h-4" />
      </div>
      <div class="col-span-5">名称</div>
      <div class="col-span-2 hidden md:block">类型</div>
      <div class="col-span-2 hidden md:block">大小</div>
      <div class="col-span-2 text-right">操作</div>
    </div>
    <div class="divide-y divide-neutral-200">
      <div
        v-for="node in nodes"
        :key="node.node_id"
        class="grid grid-cols-12 py-2 px-4 hover:bg-neutral-50 items-center group"
        :class="{ 'bg-primary/5': isSelected(node.node_id) }"
      >
        <!-- 复选框 -->
        <div class="col-span-1 flex items-center" @click.stop>
          <input type="checkbox" :checked="isSelected(node.node_id)" @change="toggleSelect(node.node_id)" class="w-4 h-4" />
        </div>
        <!-- 名称 -->
        <div class="col-span-5 flex items-center space-x-3 cursor-pointer" @click="$emit('itemClick', node)">
          <div class="w-8 h-8 rounded bg-neutral-50 flex items-center justify-center">
            <i :class="['fa', iconClass(node), node.node_type === 'FOLDER' ? 'fa-folder text-primary' : '']"></i>
          </div>
          <span class="text-ellipsis-1 font-medium text-neutral-700 flex-1">{{ node.node_name }}</span>
          <!-- 星标图标 -->
          <button @click.stop="$emit('star', node)" class="text-neutral-300 hover:text-warning">
            <i :class="isStarred(node.node_id) ? 'fa fa-star text-warning' : 'fa fa-star-o'"></i>
          </button>
        </div>
        <!-- 类型 -->
        <div class="col-span-2 hidden md:block text-neutral-500">{{ node.node_type === 'FOLDER' ? '文件夹' : getFileExtension(node.node_name) }}</div>
        <!-- 大小 -->
        <div class="col-span-2 hidden md:block text-neutral-500">{{ node.node_type === 'FILE' ? formatFileSize(node.node_size) : '--' }}</div>
        <!-- 操作按钮 -->
        <div class="col-span-2 text-right space-x-1">
          <button v-if="node.node_type === 'FILE'" @click.stop="$emit('action', node, 'download')" class="text-primary text-sm p-1" title="下载"><i class="fa fa-download"></i></button>
          <button @click.stop="$emit('action', node, 'rename')" class="text-neutral-500 text-sm p-1" title="重命名"><i class="fa fa-pencil"></i></button>
          <button @click.stop="$emit('action', node, 'delete')" class="text-danger text-sm p-1" title="删除"><i class="fa fa-trash"></i></button>
          <button @click.stop="$emit('action', node, 'detail')" class="text-neutral-500 text-sm p-1" title="详情"><i class="fa fa-info-circle"></i></button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { getFileExtension, formatFileSize } from '@/utils/helpers'
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

const allSelected = computed(() => props.nodes.length > 0 && props.nodes.every(n => props.selectedIds.has(n.node_id)))

const toggleSelectAll = () => {
  if (allSelected.value) {
    props.nodes.forEach(n => emit('selection-change', n.node_id))
  } else {
    props.nodes.forEach(n => {
      if (!props.selectedIds.has(n.node_id)) emit('selection-change', n.node_id)
    })
  }
}
</script>

<style scoped>
.text-ellipsis-1 {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>