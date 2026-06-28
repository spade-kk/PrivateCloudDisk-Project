<template>
  <div class="finder-grid">
    <div
      v-for="node in nodes"
      :key="node.node_id"
      class="finder-item group"
      :class="{ 'is-selected': isSelected(node.node_id) }"
      @click="$emit('itemClick', node)"
      @contextmenu.prevent.stop="$emit('contextmenu', $event, node)"
    >
      <!-- 复选框 -->
      <div class="finder-control finder-checkbox" @click.stop>
        <input
          type="checkbox"
          :checked="isSelected(node.node_id)"
          @change="toggleSelect(node.node_id, node.node_type)"
          class="h-4 w-4 rounded border-neutral-300 text-primary focus:ring-primary"
        />
      </div>
      <!-- 星标按钮 -->
      <div class="finder-control finder-star" @click.stop>
        <button @click="$emit('star', node)" class="rounded-full px-1.5 py-1 text-neutral-300 transition hover:bg-white/80 hover:text-warning">
          <i :class="isStarred(node.node_id) ? 'fa fa-star text-warning' : 'fa fa-star-o'"></i>
        </button>
      </div>
      <!-- 内容 -->
      <div class="finder-content">
        <div class="finder-icon">
          <!-- 图片文件：使用缩略图，失败回退字体图标 -->
          <template v-if="node.node_type === 'FILE' && isImageFile(node.node_name)">
            <ThumbnailImage
              :file-id="node.node_id"
              :file-name="node.node_name"
              size="small"
              icon-size="2rem"
            />
          </template>
          <!-- 文件夹 / 非图片文件：使用字体图标 -->
          <i
            v-else
            :class="['fa', iconClass(node), node.node_type === 'FOLDER' ? 'fa-folder text-primary text-3xl' : 'text-2xl sm:text-3xl']"
          ></i>
        </div>
        <h3 class="finder-name text-ellipsis-2">{{ node.node_name }}</h3>
        <p class="finder-meta">{{ node.node_type === 'FOLDER' ? '文件夹' : getFileExtension(node.node_name) }}</p>
      </div>
      <!-- 操作菜单（悬浮显示） -->
      <div class="finder-actions" @click.stop>
        <div class="flex items-center gap-0.5 rounded-full border border-neutral-200/80 bg-white/95 p-1 shadow-card backdrop-blur">
          <button @click="$emit('action', node, 'download')" class="finder-action-btn text-primary" title="下载"><i class="fa fa-download"></i></button>
          <button @click="$emit('action', node, 'rename')" class="finder-action-btn text-neutral-500" title="重命名"><i class="fa fa-pencil"></i></button>
          <button @click="$emit('action', node, 'delete')" class="finder-action-btn text-danger" title="删除"><i class="fa fa-trash"></i></button>
          <button @click="$emit('action', node, 'detail')" class="finder-action-btn text-neutral-500" title="详情"><i class="fa fa-info-circle"></i></button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { getFileExtension } from '@/utils/helpers'
import { getFileIconClass } from '@/utils/fileIcon'
import { isImage } from '@/utils/previewHelper'
import ThumbnailImage from './ThumbnailImage.vue'

const props = defineProps({
  nodes: { type: Array, required: true },
  selectedIds: { type: Set, default: () => new Set() },
  starredIds: { type: Set, default: () => new Set() },
})

const emit = defineEmits(['itemClick', 'selection-change', 'action', 'star', 'contextmenu'])

const iconClass = (node) => getFileIconClass(node.node_name)
const isSelected = (id) => props.selectedIds.has(id)
const isStarred = (id) => props.starredIds.has(id)
const isImageFile = (fileName: string) => isImage(fileName)
const toggleSelect = (id, type) => emit('selection-change', id, type)
</script>

<style scoped>
.finder-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(108px, 124px));
  justify-content: space-between;
  gap: 30px 24px;
  padding: 6px 2px 18px;
}

.finder-item {
  position: relative;
  min-height: 132px;
  cursor: pointer;
  border-radius: 14px;
  padding: 12px 8px 10px;
  color: #303133;
  outline: 1px solid transparent;
  transition:
    background-color 160ms ease,
    box-shadow 160ms ease,
    outline-color 160ms ease,
    transform 160ms ease;
}

.finder-item:hover,
.finder-item.is-selected {
  background: rgba(22, 93, 255, 0.06);
}

.finder-item:hover {
  transform: translateY(-1px);
  outline-color: rgba(22, 93, 255, 0.08);
}

.finder-item.is-selected {
  background: rgba(22, 93, 255, 0.1);
  box-shadow: inset 0 0 0 1px rgba(22, 93, 255, 0.2);
}

.finder-content {
  display: flex;
  height: 100%;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  text-align: center;
}

.finder-icon {
  display: flex;
  height: 64px;
  width: 64px;
  align-items: center;
  justify-content: center;
  filter: drop-shadow(0 8px 10px rgba(31, 41, 55, 0.08));
}

.finder-name {
  margin-top: 8px;
  width: 100%;
  min-height: 36px;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  word-break: break-word;
}

.finder-meta {
  margin-top: 2px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #909399;
  font-size: 12px;
  line-height: 16px;
}

.finder-control {
  position: absolute;
  z-index: 10;
  opacity: 1;
  transition: opacity 160ms ease, transform 160ms ease;
}

.finder-checkbox {
  left: 8px;
  top: 8px;
}

.finder-star {
  right: 7px;
  top: 5px;
}

.finder-actions {
  position: absolute;
  left: 50%;
  bottom: -10px;
  z-index: 20;
  opacity: 1;
  transform: translateX(-50%);
  transition: opacity 160ms ease, transform 160ms ease;
}

.finder-action-btn {
  display: inline-flex;
  height: 26px;
  width: 26px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  font-size: 12px;
  transition: background-color 160ms ease, color 160ms ease;
}

.finder-action-btn:hover {
  background: #f5f7fa;
}

.text-ellipsis-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (hover: hover) and (pointer: fine) {
  .finder-control,
  .finder-actions {
    opacity: 0;
  }

  .finder-item:hover .finder-control,
  .finder-item:hover .finder-actions,
  .finder-item.is-selected .finder-control,
  .finder-item.is-selected .finder-actions {
    opacity: 1;
  }

  .finder-actions {
    transform: translateX(-50%) translateY(4px);
  }

  .finder-item:hover .finder-actions,
  .finder-item.is-selected .finder-actions {
    transform: translateX(-50%) translateY(0);
  }
}

@media (min-width: 640px) {
  .finder-grid {
    grid-template-columns: repeat(auto-fill, minmax(118px, 132px));
    gap: 38px 28px;
    padding: 10px 4px 24px;
  }

  .finder-item {
    min-height: 146px;
    padding-top: 14px;
  }

  .finder-icon {
    height: 72px;
    width: 72px;
  }

  .finder-name {
    font-size: 14px;
  }
}

@media (max-width: 420px) {
  .finder-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    justify-content: stretch;
    gap: 18px 12px;
  }
}
</style>
