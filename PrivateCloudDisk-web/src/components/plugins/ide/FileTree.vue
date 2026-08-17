<template>
  <section class="file-tree" aria-label="插件项目文件">
    <header class="file-tree__header">
      <div><strong>项目文件</strong><small>{{ files.length }} 个文件</small></div>
      <div class="file-tree__actions"><button v-if="selectedId" class="file-tree__action" type="button" title="重命名" aria-label="重命名" @click="$emit('rename', selectedId)"><i class="fa fa-pencil"></i></button><button v-if="selectedId" class="file-tree__action file-tree__action--danger" type="button" title="删除" aria-label="删除" @click="$emit('delete', selectedId)"><i class="fa fa-trash-o"></i></button><button class="file-tree__action" type="button" title="新建文件" aria-label="新建文件" @click="$emit('create-file')"><i class="fa fa-plus"></i></button></div>
    </header>
    <div class="file-tree__toolbar">
      <button class="file-tree__tab" :class="{ active: activePanel === 'files' }" type="button" @click="$emit('panel-change', 'files')"><i class="fa fa-folder-o"></i> 文件</button>
      <button class="file-tree__tab" :class="{ active: activePanel === 'snippets' }" type="button" @click="$emit('panel-change', 'snippets')"><i class="fa fa-code"></i> 片段</button>
      <button class="file-tree__tab" :class="{ active: activePanel === 'templates' }" type="button" @click="$emit('panel-change', 'templates')"><i class="fa fa-clone"></i> 模板</button>
    </div>
    <div v-if="activePanel === 'files'" class="file-tree__search"><i class="fa fa-search"></i><input v-model.trim="search" type="search" placeholder="搜索项目文件" aria-label="搜索项目文件" /></div>
    <ul v-if="activePanel === 'files'" class="file-tree__list" role="tree">
      <FileTreeNode
        v-for="node in filteredTree"
        :key="node.id"
        :node="node"
        :selected-id="selectedId"
        :expanded-ids="expandedIds"
        @select="$emit('select', $event)"
        @toggle="toggle"
        @contextmenu="$emit('contextmenu', $event)"
      />
      <li v-if="!filteredTree.length" class="file-tree__empty">{{ search ? '没有匹配的文件' : '暂无项目文件' }}</li>
    </ul>
    <div v-else class="file-tree__panel-slot"><slot :name="activePanel"></slot></div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import FileTreeNode from './FileTreeNode.vue'
import type { FileTreeNodeItem } from '@/stores/pluginIdeStore'

const props = withDefaults(defineProps<{
  files: FileTreeNodeItem[]
  tree?: FileTreeNodeItem[]
  selectedId?: string | null
  activePanel?: 'files' | 'snippets' | 'templates'
}>(), {
  tree: undefined,
  selectedId: null,
  activePanel: 'files',
})

/** Web IDE 需求 2：兼容旧调用方只传 files 的场景，避免模板读取 undefined tree。 */
const tree = computed(() => props.tree || props.files)
const search = ref('')
function filterNodes(nodes: FileTreeNodeItem[]): FileTreeNodeItem[] {
  if (!search.value) return nodes
  const keyword = search.value.toLowerCase()
  return nodes.reduce<FileTreeNodeItem[]>((result, node) => {
    const children = node.children ? filterNodes(node.children) : []
    if (node.name.toLowerCase().includes(keyword) || children.length) result.push({ ...node, children })
    return result
  }, [])
}
const filteredTree = computed(() => filterNodes(tree.value))

const emit = defineEmits<{
  select: [node: FileTreeNodeItem]
  toggle: [node: FileTreeNodeItem]
  contextmenu: [node: FileTreeNodeItem]
  'create-file': []
  'panel-change': [panel: 'files' | 'snippets' | 'templates']
  rename: [id: string]
  delete: [id: string]
}>()

const expandedIds = ref<string[]>([])
watch(search, () => {
  if (!search.value) return
  const collect = (nodes: FileTreeNodeItem[]): string[] => nodes.flatMap((node) => node.kind === 'folder' ? [node.id, ...(node.children ? collect(node.children) : [])] : [])
  expandedIds.value = collect(filteredTree.value)
})
function toggle(node: FileTreeNodeItem) {
  if (node.kind !== 'folder') return
  expandedIds.value = expandedIds.value.includes(node.id)
    ? expandedIds.value.filter((id) => id !== node.id)
    : [...expandedIds.value, node.id]
  emit('toggle', node)
}
</script>

<style scoped>
.file-tree { min-height: 100%; background: #f8fafc; }
.file-tree__header { display: flex; align-items: center; justify-content: space-between; padding: 14px 12px 10px; border-bottom: 1px solid #e2e8f0; }
.file-tree__header strong { display: block; color: #1e293b; font-size: 13px; }
.file-tree__header small { display: block; margin-top: 2px; color: #94a3b8; font-size: 10px; }
.file-tree__action { display: inline-flex; min-width: 30px; min-height: 30px; align-items: center; justify-content: center; border-radius: 7px; color: #64748b; }
.file-tree__actions { display:flex; gap:2px; }.file-tree__action:hover { background: #e0ebff; color: #2563eb; }.file-tree__action--danger:hover { background:#fff1f2; color:#dc2626; }
.file-tree__toolbar { display: flex; gap: 4px; padding: 9px 10px; border-bottom: 1px solid #e2e8f0; }
.file-tree__tab { display: inline-flex; min-height: 30px; align-items: center; gap: 5px; padding: 0 8px; border-radius: 7px; color: #64748b; font-size: 10px; font-weight: 600; }
.file-tree__tab.active { background: #e0ebff; color: #1d4ed8; }
.file-tree__list { margin: 0; padding: 8px; }
.file-tree__search { display:flex;align-items:center;gap:7px;margin:8px 10px 0;border:1px solid #e2e8f0;border-radius:8px;background:#fff;padding:0 8px;color:#94a3b8; }.file-tree__search input{width:100%;min-height:30px;border:0;outline:0;color:#475569;font-size:11px;}
.file-tree__empty { padding: 32px 10px; color: #94a3b8; font-size: 12px; text-align: center; list-style: none; }
.file-tree__panel-slot { padding: 10px; }
</style>
