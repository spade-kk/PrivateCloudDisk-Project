<template>
  <div class="overflow-visible rounded-lg bg-white shadow-card">
    <div class="hidden grid-cols-12 bg-neutral-50 px-4 py-2 font-medium text-neutral-600 border-b border-neutral-200 sm:grid">
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
        @contextmenu.prevent.stop="$emit('contextmenu', $event, node)"
        @mouseenter="hoveredId = node.node_id"
        @mouseleave="hoveredId = null"
        class="file-list-row group relative block px-3 py-3 hover:bg-neutral-50 sm:grid sm:grid-cols-12 sm:items-center sm:px-4 sm:py-2"
        :class="{ 'bg-primary/5': isSelected(node.node_id) }"
      >
        <!-- 复选框 -->
        <div class="hidden items-center sm:col-span-1 sm:flex" @click.stop>
          <input type="checkbox" :checked="isSelected(node.node_id)" @change="toggleSelect(node.node_id, node.node_type)" class="w-4 h-4" />
        </div>
        <!-- 名称 -->
        <div class="flex min-w-0 cursor-pointer items-start gap-3 sm:col-span-5 sm:items-center" @click="$emit('itemClick', node)">
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded bg-neutral-50 sm:h-8 sm:w-8">
            <template v-if="node.node_type === 'FILE' && isThumbnailable(node.node_name)">
              <ThumbnailImage
                :file-id="node.node_id"
                :file-name="node.node_name"
                size="small"
                icon-size="1rem"
              />
            </template>
            <FileTypeIcon
              v-else
              :file-name="node.node_name"
              :is-directory="node.node_type === 'FOLDER'"
              class="text-base"
            />
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex min-w-0 items-center gap-2">
              <span class="text-ellipsis-1 flex-1 font-medium text-neutral-700">{{ node.node_name }}</span>
              <!-- 星标图标 -->
              <button @click.stop="$emit('star', node)" class="shrink-0 text-neutral-300 hover:text-warning">
                <i :class="isStarred(node.node_id) ? 'fa fa-star text-warning' : 'fa fa-star-o'"></i>
              </button>
            </div>
            <div class="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-neutral-500 sm:hidden">
              <span>{{ node.node_type === 'FOLDER' ? '文件夹' : getFileExtension(node.node_name) }}</span>
              <span>{{ node.node_type === 'FILE' ? formatFileSize(node.node_size ?? 0) : '--' }}</span>
            </div>
            <div v-if="tagsFor(node).length" class="mt-1.5 flex min-w-0 max-w-md" @click.stop>
              <FileTagBubble :tags="tagsFor(node)" :max-rows="2" />
            </div>
          </div>
        </div>
        <!-- 类型 -->
        <div class="col-span-2 hidden md:block text-neutral-500">{{ node.node_type === 'FOLDER' ? '文件夹' : getFileExtension(node.node_name) }}</div>
        <!-- 大小 -->
        <div class="col-span-2 hidden md:block text-neutral-500">{{ node.node_type === 'FILE' ? formatFileSize(node.node_size ?? 0) : '--' }}</div>
        <!-- 操作按钮 -->
        <div class="mt-2 flex items-center justify-between gap-3 sm:col-span-2 sm:mt-0 sm:justify-end sm:space-x-1 sm:text-right">
          <label class="inline-flex touch-button items-center gap-2 text-sm text-neutral-500 sm:hidden" @click.stop>
            <input type="checkbox" :checked="isSelected(node.node_id)" @change="toggleSelect(node.node_id, node.node_type)" class="h-4 w-4" />
            选择
          </label>
          <div class="flex shrink-0 touch-button items-center gap-1 sm:gap-0">
            <button v-if="node.node_type === 'FILE'" @click.stop="$emit('action', node, 'download')" class="rounded p-1.5 text-sm text-primary active:bg-primary/10 sm:p-1" title="下载"><i class="fa fa-download"></i></button>
            <button @click.stop="$emit('action', node, 'rename')" class="rounded p-1.5 text-sm text-neutral-500 active:bg-neutral-100 sm:p-1" title="重命名"><i class="fa fa-pencil"></i></button>
            <button @click.stop="$emit('action', node, 'tags')" class="rounded p-1.5 text-sm text-neutral-500 active:bg-neutral-100 sm:p-1" title="管理标签"><i class="fa fa-tags"></i></button>
            <button @click.stop="$emit('action', node, 'delete')" class="rounded p-1.5 text-sm text-danger active:bg-danger/10 sm:p-1" title="删除"><i class="fa fa-trash"></i></button>
            <button @click.stop="$emit('action', node, 'detail')" class="rounded p-1.5 text-sm text-neutral-500 active:bg-neutral-100 sm:p-1" title="详情"><i class="fa fa-info-circle"></i></button>
          </div>
        </div>
        <FileHoverPreview
          v-if="node.node_type === 'FILE'"
          :file-id="node.node_id"
          :file-name="node.node_name"
          :armed="hoveredId === node.node_id"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { getFileExtension, formatFileSize } from '@/utils/helpers'
import { isImage, isOffice, isPdf, isVideo } from '@/utils/previewHelper'
import ThumbnailImage from './ThumbnailImage.vue'
import FileTypeIcon from './FileTypeIcon.vue'
import FileHoverPreview from './FileHoverPreview.vue'
import FileTagBubble from '@/components/tag/FileTagBubble.vue'
import type { TagVO } from '@/api/modules/tags'

interface FileNode { node_id: string; node_name: string; node_type: string; node_size?: number }

const props = withDefaults(defineProps<{
  nodes: FileNode[]
  selectedIds?: Set<string>
  starredIds?: Set<string>
  tagsByTarget?: Record<string, TagVO[]>
}>(), {
  selectedIds: () => new Set<string>(),
  starredIds: () => new Set<string>(),
  tagsByTarget: () => ({}),
})

const emit = defineEmits(['itemClick', 'selection-change', 'action', 'star', 'contextmenu'])

const isSelected = (id: string) => props.selectedIds.has(id)
const isStarred = (id: string) => props.starredIds.has(id)
const isImageFile = (fileName: string) => isImage(fileName)
const isVideoFile = (fileName: string) => isVideo(fileName)
const isThumbnailable = (fileName: string) =>
  isImage(fileName) || isVideo(fileName) || isPdf(fileName) || isOffice(fileName)
const toggleSelect = (id: string, type: string) => emit('selection-change', id, type)
const tagsFor = (node: FileNode) => props.tagsByTarget[node.node_id] || []
const hoveredId = ref<string | null>(null)

const allSelected = computed(() => props.nodes.length > 0 && props.nodes.every(n => props.selectedIds.has(n.node_id)))

const toggleSelectAll = () => {
  if (allSelected.value) {
    props.nodes.forEach(n => emit('selection-change', n.node_id, n.node_type))
  } else {
    props.nodes.forEach(n => {
      if (!props.selectedIds.has(n.node_id)) emit('selection-change', n.node_id, n.node_type)
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

/* AUDIT FIX [2.4]：列表行悬停时建立更高层叠上下文，预览覆盖相邻行但不改变布局尺寸。 */
.file-list-row:hover {
  z-index: 30;
}
</style>
