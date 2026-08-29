<template>
  <aside class="git-file-tree" :class="{ 'git-file-tree--collapsed': collapsed }" aria-label="仓库文件目录">
    <header class="git-file-tree__header">
      <div class="git-file-tree__title" :title="repositoryName">
        <i class="fa fa-code-fork" aria-hidden="true"></i>
        <strong>{{ repositoryName }}</strong>
      </div>
      <button class="git-file-tree__collapse" type="button" :title="collapsed ? '展开目录树' : '收起目录树'" :aria-label="collapsed ? '展开目录树' : '收起目录树'" @click="$emit('toggle-collapse')">
        <i :class="collapsed ? 'fa fa-angle-double-right' : 'fa fa-angle-double-left'" aria-hidden="true"></i>
      </button>
    </header>

    <div class="git-file-tree__controls">
      <label class="git-file-tree__search">
        <i class="fa fa-search" aria-hidden="true"></i>
        <input v-model.trim="query" type="search" placeholder="查找文件（T）" aria-label="查找仓库文件" @keydown.stop />
      </label>
      <button type="button" class="git-file-tree__refresh" title="刷新目录" aria-label="刷新目录" @click="$emit('refresh')"><i class="fa fa-refresh" aria-hidden="true"></i></button>
    </div>

    <div
      ref="listElement"
      class="git-file-tree__viewport"
      role="tree"
      tabindex="0"
      aria-label="仓库文件树，可使用方向键导航"
      @scroll="onScroll"
      @keydown="onKeydown"
    >
      <div v-if="loading && !rows.length" class="git-file-tree__skeleton" aria-live="polite">
        <span v-for="index in 9" :key="index" :style="{ width: `${55 + (index % 4) * 10}%` }"></span>
      </div>
      <div v-else-if="!rows.length" class="git-file-tree__empty"><i class="fa fa-folder-open-o"></i><span>{{ query ? '没有匹配的文件' : '当前引用暂无文件' }}</span></div>
      <div v-else class="git-file-tree__spacer" :style="{ height: `${rows.length * ROW_HEIGHT}px` }">
        <div class="git-file-tree__window" :style="{ transform: `translateY(${windowStart * ROW_HEIGHT}px)` }">
          <button
            v-for="row in visibleRows"
            :key="row.node.path"
            class="git-file-tree__row"
            :class="{ 'is-selected': row.node.path === selectedPath, 'is-folder': row.node.type === 'tree', 'is-active': row.index === activeIndex, 'is-compressed': row.displayName !== row.node.name }"
            type="button"
            role="treeitem"
            :aria-expanded="row.node.type === 'tree' ? isExpanded(row.node.path) : undefined"
            :aria-selected="row.node.path === selectedPath"
            :style="{ paddingLeft: `${10 + row.depth * 15}px` }"
            :title="row.node.path"
            @click="activate(row, $event)"
            @dblclick="row.node.type === 'blob' && $emit('open', row.node)"
            @contextmenu.prevent="openContextMenu($event, row.node)"
          >
            <i v-if="row.node.type === 'tree'" :class="isExpanded(row.node.path) ? 'fa fa-chevron-down git-file-tree__chevron' : 'fa fa-chevron-right git-file-tree__chevron'" aria-hidden="true"></i>
            <span v-else class="git-file-tree__chevron-placeholder"></span>
            <FileTypeIcon class="git-file-tree__icon" :file-name="row.node.name" :path="row.node.path" :is-directory="row.node.type === 'tree'" color-mode="monochrome" />
            <span class="git-file-tree__name" v-html="highlightName(row.displayName)"></span>
            <i v-if="row.node.loading" class="fa fa-circle-o-notch fa-spin git-file-tree__loading" aria-label="正在加载"></i>
          </button>
        </div>
      </div>
    </div>

    <div v-if="contextNode" class="git-file-tree__context" :style="{ left: `${contextPosition.x}px`, top: `${contextPosition.y}px` }" role="menu" @mouseleave="contextNode = null">
      <button type="button" role="menuitem" @click="copyPath"><i class="fa fa-copy"></i>复制路径</button>
      <button v-if="contextNode.type === 'blob'" type="button" role="menuitem" @click="emitContext('download')"><i class="fa fa-download"></i>下载文件</button>
      <button v-if="contextNode.type === 'blob'" type="button" role="menuitem" @click="emitContext('history')"><i class="fa fa-history"></i>查看历史</button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { GitTreeEntry } from '@/api/modules/git'
import { escapeHtml } from '@/utils/gitRepositoryPresentation'
import FileTypeIcon from '@/components/file/FileTypeIcon.vue'

export interface GitTreeNode extends GitTreeEntry {
  children?: GitTreeNode[]
  loaded?: boolean
  loading?: boolean
}

interface TreeRow {
  /** 最后一个实际目录节点；展开、右键和键盘操作仍调用真实 Git path。 */
  node: GitTreeNode
  depth: number
  index: number
  /** 用于 GitHub 风格单子目录压缩的展示路径，不改变后端 tree 模型。 */
  displayName: string
}

const ROW_HEIGHT = 34
const OVERSCAN = 8
const props = withDefaults(defineProps<{
  repositoryName: string
  nodes: GitTreeNode[]
  expandedPaths: string[]
  selectedPath?: string
  collapsed?: boolean
  loading?: boolean
}>(), { selectedPath: '', collapsed: false, loading: false })

const emit = defineEmits<{
  /**
   * [REQ-GIT-TREE-3.1~3.11] deep=true 会让宿主逐级加载并展开单目录链；
   * Shift 点击保留传统的单层展开，便于用户在大树中精确控制展开范围。
   */
  toggle: [node: GitTreeNode, options: { deep: boolean }]
  open: [node: GitTreeNode]
  refresh: []
  'toggle-collapse': []
  download: [node: GitTreeNode]
  history: [node: GitTreeNode]
  copied: [message: string]
}>()

const query = ref('')
const listElement = ref<HTMLElement | null>(null)
const scrollTop = ref(0)
const viewportHeight = ref(520)
const activeIndex = ref(0)
const contextNode = ref<GitTreeNode | null>(null)
const contextPosition = ref({ x: 0, y: 0 })

const expandedSet = computed(() => new Set(props.expandedPaths))
function isExpanded(path: string): boolean { return expandedSet.value.has(path) }

function filterNodes(nodes: GitTreeNode[]): GitTreeNode[] {
  if (!query.value) return nodes
  const keyword = query.value.toLocaleLowerCase()
  return nodes.reduce<GitTreeNode[]>((result, node) => {
    const children = node.children ? filterNodes(node.children) : []
    if (node.name.toLocaleLowerCase().includes(keyword) || children.length) result.push({ ...node, children })
    return result
  }, [])
}

const rows = computed<TreeRow[]>(() => {
  const result: TreeRow[] = []
  const walk = (nodes: GitTreeNode[], depth: number) => nodes.forEach((source) => {
    const row = compressSingleDirectoryChain(source)
    result.push({ node: row.node, depth, index: result.length, displayName: row.displayName })
    if (row.node.type === 'tree' && (isExpanded(row.node.path) || Boolean(query.value)) && row.node.children?.length) walk(row.node.children, depth + 1)
  })
  walk(filterNodes(props.nodes), 0)
  return result
})

/**
 * [REQ-PUBLIC-GIT-UI-3.5]
 * 原行为逐层渲染仅包含一个子目录的链路，例如 a/b 会要求用户先展开 a 再展开 b。
 * GitHub/IDE 文件树会把这一类没有同级文件或目录分叉的路径显示为 “a / b”。
 * 新行为只压缩已加载的纯目录链，保留尾节点作为真实交互节点，因此不影响 lazy-load、
 * 路由 path、下载、历史、键盘导航和后端 Git tree API 的原始语义。
 */
function compressSingleDirectoryChain(source: GitTreeNode): { node: GitTreeNode; displayName: string } {
  if (source.type !== 'tree' || query.value) return { node: source, displayName: source.name }
  let current = source
  const segments = [source.name]
  while (current.loaded && current.children?.length === 1 && current.children[0].type === 'tree') {
    current = current.children[0]
    segments.push(current.name)
  }
  return { node: current, displayName: segments.join(' / ') }
}

const windowStart = computed(() => Math.max(0, Math.floor(scrollTop.value / ROW_HEIGHT) - OVERSCAN))
const windowEnd = computed(() => Math.min(rows.value.length, Math.ceil((scrollTop.value + viewportHeight.value) / ROW_HEIGHT) + OVERSCAN))
const visibleRows = computed(() => rows.value.slice(windowStart.value, windowEnd.value))

watch(rows, (items) => {
  const selectedIndex = items.findIndex((row) => row.node.path === props.selectedPath)
  if (selectedIndex >= 0) activeIndex.value = selectedIndex
  else activeIndex.value = Math.min(activeIndex.value, Math.max(0, items.length - 1))
}, { immediate: true })

watch(query, async () => {
  await nextTick()
  if (listElement.value) {
    listElement.value.scrollTop = 0
    scrollTop.value = 0
    viewportHeight.value = listElement.value.clientHeight
  }
})

function onScroll(event: Event): void {
  const element = event.currentTarget as HTMLElement
  scrollTop.value = element.scrollTop
  viewportHeight.value = element.clientHeight
}

function activate(row: TreeRow, event?: MouseEvent): void {
  activeIndex.value = row.index
  if (row.node.type === 'tree') emit('toggle', row.node, { deep: !event?.shiftKey })
  else emit('open', row.node)
}

function onKeydown(event: KeyboardEvent): void {
  if (!rows.value.length) return
  const current = rows.value[activeIndex.value]
  if (event.key === 'ArrowDown') { event.preventDefault(); moveActive(1) }
  else if (event.key === 'ArrowUp') { event.preventDefault(); moveActive(-1) }
  else if (event.key === 'ArrowRight' && current?.node.type === 'tree' && !isExpanded(current.node.path)) { event.preventDefault(); emit('toggle', current.node, { deep: true }) }
  else if (event.key === 'ArrowLeft' && current?.node.type === 'tree' && isExpanded(current.node.path)) { event.preventDefault(); emit('toggle', current.node, { deep: false }) }
  else if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); if (current) activate(current) }
}

function moveActive(offset: number): void {
  activeIndex.value = Math.max(0, Math.min(rows.value.length - 1, activeIndex.value + offset))
  const target = activeIndex.value * ROW_HEIGHT
  if (listElement.value && (target < listElement.value.scrollTop || target + ROW_HEIGHT > listElement.value.scrollTop + listElement.value.clientHeight)) {
    listElement.value.scrollTop = Math.max(0, target - listElement.value.clientHeight / 2)
  }
}

function highlightName(name: string): string {
  const escaped = escapeHtml(name)
  if (!query.value) return escaped
  const escapedQuery = escapeHtml(query.value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return escaped.replace(new RegExp(`(${escapedQuery})`, 'ig'), '<mark>$1</mark>')
}
function openContextMenu(event: MouseEvent, node: GitTreeNode): void { contextNode.value = node; contextPosition.value = { x: event.clientX, y: event.clientY } }
async function copyPath(): Promise<void> {
  if (!contextNode.value) return
  try { await navigator.clipboard.writeText(contextNode.value.path); emit('copied', '文件路径已复制') } catch { emit('copied', '当前浏览器不允许自动复制') }
  contextNode.value = null
}
function emitContext(action: 'download' | 'history'): void {
  if (contextNode.value && action === 'download') emit('download', contextNode.value)
  if (contextNode.value && action === 'history') emit('history', contextNode.value)
  contextNode.value = null
}
</script>

<style scoped>
.git-file-tree { position:relative; display:flex; min-height:0; width:280px; flex:0 0 280px; flex-direction:column; overflow:hidden; background:var(--git-tree-bg,#f6f8fa); color:var(--git-text,#24292f); transition:width .18s ease,flex-basis .18s ease; }
.git-file-tree--collapsed { width:48px; flex-basis:48px; }.git-file-tree--collapsed:hover { width:280px; flex-basis:280px; box-shadow:6px 0 16px rgba(31,35,40,.13); z-index:4; }
.git-file-tree__header { display:flex; min-height:52px; align-items:center; justify-content:space-between; gap:8px; padding:0 12px; border-bottom:1px solid var(--git-border,#d0d7de); }.git-file-tree__title { display:flex; min-width:0; align-items:center; gap:8px; font-size:13px; }.git-file-tree__title i { color:#0969da; }.git-file-tree__title strong { overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }.git-file-tree__collapse,.git-file-tree__refresh { display:inline-flex;width:28px;height:28px;align-items:center;justify-content:center;border:0;border-radius:6px;background:transparent;color:var(--git-muted,#57606a); }.git-file-tree__collapse:hover,.git-file-tree__refresh:hover { background:var(--git-hover,#eaeef2); color:#0969da; }
.git-file-tree__controls { display:flex; gap:6px; padding:9px 10px; border-bottom:1px solid var(--git-border,#d0d7de); }.git-file-tree__search { display:flex; min-width:0; flex:1; align-items:center; gap:7px; border:1px solid var(--git-border,#d0d7de); border-radius:6px; background:var(--git-panel,#fff); padding:0 8px; color:var(--git-muted,#57606a); }.git-file-tree__search input { width:100%;min-width:0;height:30px;border:0;outline:0;background:transparent;color:inherit;font-size:12px; }
.git-file-tree__viewport { min-height:0;flex:1;overflow-x:auto;overflow-y:auto;overscroll-behavior:contain;outline:0;scrollbar-color:color-mix(in srgb,var(--git-muted,#57606a) 52%,transparent) transparent;scrollbar-width:thin; }.git-file-tree__viewport::-webkit-scrollbar { width:8px;height:8px; }.git-file-tree__viewport::-webkit-scrollbar-thumb { border:2px solid transparent;border-radius:999px;background:color-mix(in srgb,var(--git-muted,#57606a) 48%,transparent);background-clip:padding-box; }.git-file-tree__viewport:focus-visible { box-shadow:inset 0 0 0 2px #0969da; }.git-file-tree__spacer { position:relative;min-width:max-content; }.git-file-tree__window { position:absolute;top:0;left:0;min-width:100%;width:max-content; }.git-file-tree__row { display:flex;min-width:100%;width:max-content;height:34px;align-items:center;gap:6px;border:0;background:transparent;color:inherit;text-align:left;cursor:pointer; }.git-file-tree__row:hover,.git-file-tree__row.is-active { background:var(--git-hover,#eaeef2); }.git-file-tree__row.is-selected { background:#ddf4ff;color:#0969da;--git-icon-color:#0969da;font-weight:600; }.git-file-tree__icon { flex:0 0 auto; color:var(--git-icon-color,currentColor); }.git-file-tree__chevron,.git-file-tree__chevron-placeholder { width:12px;flex:0 0 12px;color:var(--git-muted,#57606a);font-size:9px; }.git-file-tree__name { min-width:max-content;white-space:nowrap;font-size:12px; }.git-file-tree__row.is-compressed .git-file-tree__name { font-weight:600;letter-spacing:.005em; }.git-file-tree__name :deep(mark) { background:#fff8c5;color:inherit;padding:0; }.git-file-tree__loading { margin-left:auto;margin-right:8px;color:#0969da; }.git-file-tree__skeleton { display:flex; flex-direction:column; gap:12px; padding:16px; }.git-file-tree__skeleton span { height:12px;border-radius:5px;background:linear-gradient(90deg,#eaeef2 25%,#f6f8fa 50%,#eaeef2 75%);background-size:200% 100%;animation:git-tree-loading 1.2s infinite; }.git-file-tree__empty { display:flex; flex-direction:column; align-items:center; gap:10px; padding:58px 14px; color:var(--git-muted,#57606a); font-size:12px; text-align:center; }.git-file-tree__empty i { font-size:25px;color:#8c959f; }
.git-file-tree__context { position:fixed; z-index:30; min-width:160px; overflow:hidden; border:1px solid var(--git-border,#d0d7de); border-radius:8px; background:var(--git-panel,#fff); box-shadow:0 8px 24px rgba(31,35,40,.16); padding:4px; }.git-file-tree__context button { display:flex;width:100%;align-items:center;gap:8px;border:0;border-radius:5px;background:transparent;padding:7px 9px;color:var(--git-text,#24292f);font-size:12px;text-align:left; }.git-file-tree__context button:hover { background:var(--git-hover,#eaeef2); }
.git-file-tree--collapsed .git-file-tree__title strong,.git-file-tree--collapsed .git-file-tree__controls { display:none; }.git-file-tree--collapsed .git-file-tree__header { justify-content:center;padding:0 6px; }.git-file-tree--collapsed .git-file-tree__title { display:none; }.git-file-tree--collapsed:hover .git-file-tree__title,.git-file-tree--collapsed:hover .git-file-tree__controls,.git-file-tree--collapsed:hover .git-file-tree__title strong { display:flex; }.git-file-tree--collapsed:hover .git-file-tree__header { justify-content:space-between;padding:0 12px; }.git-file-tree--collapsed:hover .git-file-tree__controls { display:flex; }
:global(.dark) .git-file-tree { --git-tree-bg:#161b22;--git-panel:#0d1117;--git-text:#c9d1d9;--git-muted:#8b949e;--git-border:#30363d;--git-hover:#21262d; }.dark-mode .git-file-tree { --git-tree-bg:#161b22;--git-panel:#0d1117;--git-text:#c9d1d9;--git-muted:#8b949e;--git-border:#30363d;--git-hover:#21262d; }
@keyframes git-tree-loading { to { background-position:-200% 0; } } @media (prefers-reduced-motion: reduce) { .git-file-tree,.git-file-tree__skeleton span { transition:none;animation:none; } }
</style>
