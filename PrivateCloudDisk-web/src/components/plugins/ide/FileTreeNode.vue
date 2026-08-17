<template>
  <li class="file-tree-node" :class="{ 'is-directory': node.kind === 'folder' }">
    <div
      class="file-tree-node__row"
      :class="{ 'is-selected': selectedId === node.id }"
      :style="{ '--tree-depth': depth }"
      role="treeitem"
      :aria-selected="selectedId === node.id"
      :aria-expanded="node.kind === 'folder' ? isExpanded : undefined"
      tabindex="0"
      @click="selectNode"
      @keydown.enter.prevent="selectNode"
      @keydown.space.prevent="selectNode"
      @keydown.arrowright.prevent="expandNode"
      @keydown.arrowleft.prevent="collapseNode"
      @contextmenu.prevent="$emit('contextmenu', node)"
    >
      <button
        v-if="node.kind === 'folder'"
        class="file-tree-node__toggle"
        type="button"
        :aria-label="isExpanded ? `折叠 ${node.name}` : `展开 ${node.name}`"
        @click.stop="toggleNode"
      >
        <i :class="isExpanded ? 'fa fa-chevron-down' : 'fa fa-chevron-right'" aria-hidden="true"></i>
      </button>
      <span v-else class="file-tree-node__toggle file-tree-node__toggle--placeholder" aria-hidden="true"></span>
      <span class="file-tree-node__icon" aria-hidden="true"><i :class="iconClass"></i></span>
      <span class="file-tree-node__name" :title="node.name">{{ node.name }}</span>
      <span v-if="node.dirty" class="file-tree-node__dirty" title="未保存更改" aria-label="未保存更改">●</span>
      <span v-if="node.kind === 'folder' && node.children?.length" class="file-tree-node__count">{{ node.children.length }}</span>
    </div>

    <Transition name="file-tree-expand">
      <ul v-if="node.kind === 'folder' && isExpanded" class="file-tree-node__children" role="group">
        <FileTreeNode
          v-for="child in node.children"
          :key="child.id"
          :node="child"
          :depth="depth + 1"
          :selected-id="selectedId"
          :expanded-ids="expandedIds"
          @select="$emit('select', $event)"
          @toggle="$emit('toggle', $event)"
          @contextmenu="$emit('contextmenu', $event)"
        />
      </ul>
    </Transition>
  </li>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { FileTreeNodeItem } from '@/stores/pluginIdeStore'

const props = withDefaults(defineProps<{
  node: FileTreeNodeItem
  depth?: number
  selectedId?: string | null
  expandedIds?: string[]
}>(), {
  depth: 0,
  selectedId: null,
  expandedIds: () => [],
})

const emit = defineEmits<{
  select: [node: FileTreeNodeItem]
  toggle: [node: FileTreeNodeItem]
  contextmenu: [node: FileTreeNodeItem]
}>()

const isExpanded = computed(() => props.expandedIds.includes(props.node.id))
const iconClass = computed(() => {
  if (props.node.kind === 'folder') return isExpanded.value ? 'fa fa-folder-open-o' : 'fa fa-folder-o'
  const extension = props.node.name.split('.').pop()?.toLowerCase()
  if (extension === 'py') return 'fa fa-file-code-o text-amber-600'
  if (extension === 'js' || extension === 'ts') return 'fa fa-file-code-o text-yellow-600'
  if (extension === 'yaml' || extension === 'yml' || extension === 'json') return 'fa fa-file-text-o text-blue-600'
  if (extension === 'md') return 'fa fa-file-text-o text-emerald-600'
  return 'fa fa-file-o text-slate-400'
})

function selectNode() { emit('select', props.node) }
function toggleNode() { emit('toggle', props.node) }
function expandNode() {
  if (props.node.kind === 'folder' && !isExpanded.value) emit('toggle', props.node)
}
function collapseNode() {
  if (props.node.kind === 'folder' && isExpanded.value) emit('toggle', props.node)
}
</script>

<style scoped>
.file-tree-node { list-style: none; }
.file-tree-node__row { display: flex; min-height: 34px; align-items: center; gap: 5px; padding: 0 8px 0 calc(8px + var(--tree-depth) * 15px); border-radius: 7px; color: #475569; cursor: pointer; outline: none; transition: background-color .15s ease, color .15s ease; }
.file-tree-node__row:hover { background: #eef4ff; color: #1d4ed8; }
.file-tree-node__row:focus-visible { box-shadow: inset 0 0 0 2px rgba(37, 99, 235, .25); }
.file-tree-node__row.is-selected { background: #e0ebff; color: #1d4ed8; font-weight: 600; }
.file-tree-node__toggle { display: inline-flex; width: 20px; height: 24px; flex: 0 0 20px; align-items: center; justify-content: center; border: 0; background: transparent; color: #94a3b8; font-size: 10px; }
.file-tree-node__toggle--placeholder { pointer-events: none; }
.file-tree-node__icon { display: inline-flex; width: 18px; align-items: center; justify-content: center; font-size: 14px; }
.file-tree-node__name { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.file-tree-node__dirty { color: #f59e0b; font-size: 12px; }
.file-tree-node__count { color: #94a3b8; font-size: 10px; }
.file-tree-expand-enter-active,
.file-tree-expand-leave-active { max-height: 600px; overflow: hidden; transition: max-height .18s ease, opacity .18s ease; }
.file-tree-expand-enter-from,
.file-tree-expand-leave-to { max-height: 0; opacity: 0; }
@media (prefers-reduced-motion: reduce) {
  .file-tree-node__row,
  .file-tree-expand-enter-active,
  .file-tree-expand-leave-active { transition: none; }
}
</style>
