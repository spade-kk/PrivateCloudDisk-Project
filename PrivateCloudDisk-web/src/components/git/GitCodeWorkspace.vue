<template>
  <section class="git-code-workspace" :class="`git-code-workspace--${layout}`">
    <header class="git-code-workspace__toolbar">
      <label class="git-code-workspace__ref"><i class="fa fa-code-fork"></i><select :value="refName" aria-label="选择分支或标签" @change="$emit('change-ref', ($event.target as HTMLSelectElement).value)"><option v-for="item in refs" :key="`${item.type}-${item.name}`" :value="item.name">{{ item.type === 'TAG' ? '标签' : '分支' }}：{{ item.name }}</option></select></label>
      <span class="git-code-workspace__hint">{{ selectedFile?.path || '选择左侧文件以预览内容' }}</span>
      <div class="git-code-workspace__toolbar-actions"><button type="button" :title="layout === 'split' ? '切换为上下布局' : '切换为左右布局'" @click="toggleLayout"><i :class="layout === 'split' ? 'fa fa-columns' : 'fa fa-window-maximize'"></i></button><button type="button" title="克隆仓库" @click="$emit('clone')"><i class="fa fa-clone"></i>克隆</button></div>
    </header>
    <div class="git-code-workspace__body">
      <GitFileTree :repository-name="repositoryName" :nodes="nodes" :expanded-paths="expandedPaths" :selected-path="selectedFile?.path" :collapsed="treeCollapsed" :loading="treeLoading" @toggle="toggleDirectory" @open="openFile" @refresh="loadRoot" @toggle-collapse="treeCollapsed = !treeCollapsed" @download="downloadNode" @history="(node) => openHistory(node.path)" @copied="toast" />
      <GitFileViewer :repository-id="repositoryId" :repository-name="repositoryName" :ref-name="refName" :file="selectedFile" :directory-path="selectedDirectoryPath" :directory-entries="directoryEntries" :directory-loading="directoryLoading" :is-dark="isDark" :initial-line="initialLine" @navigate="navigate" @history="openHistory" @toast="toast" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import GitFileTree, { type GitTreeNode } from '@/components/git/GitFileTree.vue'
import GitFileViewer from '@/components/git/GitFileViewer.vue'
import type { GitRef, GitTreeEntry } from '@/api/modules/git'
import { getGitRawBlobApi, listGitTreeApi } from '@/api/modules/git'

const LAYOUT_KEY = 'pcd.git.repository.layout'
const props = withDefaults(defineProps<{
  repositoryId: string
  repositoryName: string
  refName: string
  refs: GitRef[]
  isDark?: boolean
  initialPath?: string
  initialLine?: number | null
}>(), { isDark: false, initialPath: '', initialLine: null })
const emit = defineEmits<{ 'change-ref': [ref: string]; clone: []; 'open-history': [path: string]; 'file-change': [path: string]; toast: [message: string, type?: 'success' | 'error' | 'warning'] }>()

const nodes = ref<GitTreeNode[]>([])
const expandedPaths = ref<string[]>([])
const selectedFile = ref<GitTreeEntry | null>(null)
const selectedDirectoryPath = ref<string | undefined>('')
const treeLoading = ref(false)
const treeCollapsed = ref(false)
const layout = ref<'split' | 'stacked'>(localStorage.getItem(LAYOUT_KEY) === 'stacked' ? 'stacked' : 'split')

watch(() => [props.repositoryId, props.refName] as const, async () => { selectedFile.value = null; expandedPaths.value = []; await loadRoot(); if (props.initialPath) await navigate(props.initialPath) }, { immediate: true })
watch(() => props.initialPath, (path) => { if (path) void navigate(path) })
const directoryEntries = computed(() => {
  if (selectedDirectoryPath.value === undefined) return []
  if (!selectedDirectoryPath.value) return nodes.value
  return findNode(selectedDirectoryPath.value)?.children || []
})
const directoryLoading = computed(() => {
  if (selectedDirectoryPath.value === undefined) return false
  if (!selectedDirectoryPath.value) return treeLoading.value
  return Boolean(findNode(selectedDirectoryPath.value)?.loading)
})

async function loadRoot(): Promise<void> {
  if (!props.repositoryId) return
  treeLoading.value = true
  try { nodes.value = toNodes((await listGitTreeApi(props.repositoryId, props.refName)).data || []); selectedDirectoryPath.value = '' } catch (cause: any) { nodes.value = []; toast(cause?.message || '目录树加载失败', 'error') } finally { treeLoading.value = false }
}

function toNodes(entries: GitTreeEntry[]): GitTreeNode[] {
  return entries.map((item) => ({ ...item, children: item.type === 'tree' ? [] : undefined, loaded: item.type !== 'tree', loading: false })).sort((left, right) => Number(right.type === 'tree') - Number(left.type === 'tree') || left.name.localeCompare(right.name, 'zh-CN'))
}

/**
 * [REQ-GIT-TREE-3.1~3.18] 原实现只加载点击的当前目录，连续的 a/b/c 目录需要
 * 连点三次。现在桌面端在单一子目录链上逐层异步加载并展开，手机端默认单层展开；
 * Shift 点击显式传入 deep=false，也保持单层展开。最大 20 层防止异常仓库耗尽请求。
 */
async function toggleDirectory(node: GitTreeNode, options: { deep: boolean } = { deep: true }): Promise<void> {
  if (node.type !== 'tree') return
  if (expandedPaths.value.includes(node.path)) { expandedPaths.value = expandedPaths.value.filter((path) => path !== node.path); selectedFile.value = null; selectedDirectoryPath.value = node.path; emit('file-change', node.path); return }
  const loadedNode = await ensureChildren(node.path)
  if (!loadedNode) return
  expandedPaths.value = [...new Set([...expandedPaths.value, node.path])]
  selectedFile.value = null
  selectedDirectoryPath.value = node.path
  emit('file-change', node.path)
  if (options.deep && shouldAutoExpandChain()) await expandSingleDirectoryChain(node.path)
}

function shouldAutoExpandChain(): boolean {
  return typeof window === 'undefined' || !window.matchMedia('(max-width: 1023px)').matches
}

async function ensureChildren(path: string): Promise<GitTreeNode | null> {
  const current = findNode(path)
  if (!current || current.type !== 'tree') return null
  if (current.loaded) return current
  if (current.loading) return null
  updateNode(path, (target) => ({ ...target, loading: true }))
  try {
    const children = toNodes((await listGitTreeApi(props.repositoryId, props.refName, path)).data || [])
    updateNode(path, (target) => ({ ...target, children, loaded: true, loading: false }))
    return findNode(path)
  } catch (cause: any) {
    updateNode(path, (target) => ({ ...target, loading: false }))
    toast(cause?.message || '目录加载失败', 'error')
    return null
  }
}

async function expandSingleDirectoryChain(startPath: string): Promise<void> {
  let currentPath = startPath
  for (let depth = 0; depth < 20; depth += 1) {
    const current = await ensureChildren(currentPath)
    const onlyChild = current?.children?.length === 1 ? current.children[0] : null
    if (!onlyChild || onlyChild.type !== 'tree') return
    const loadedChild = await ensureChildren(onlyChild.path)
    if (!loadedChild) return
    expandedPaths.value = [...new Set([...expandedPaths.value, currentPath, loadedChild.path])]
    currentPath = loadedChild.path
  }
  toast('目录层级超过 20 层，已停止自动展开', 'warning')
}

function updateNode(path: string, transform: (node: GitTreeNode) => GitTreeNode): void {
  const walk = (items: GitTreeNode[]): GitTreeNode[] => items.map((item) => {
    if (item.path === path) return transform(item)
    if (item.children?.length) return { ...item, children: walk(item.children) }
    return item
  })
  nodes.value = walk(nodes.value)
}

function findNode(path: string, items = nodes.value): GitTreeNode | null {
  for (const item of items) { if (item.path === path) return item; const found = item.children?.length ? findNode(path, item.children) : null; if (found) return found }
  return null
}
function openFile(node: GitTreeNode): void { selectedDirectoryPath.value = undefined; selectedFile.value = node; emit('file-change', node.path) }

async function navigate(path: string): Promise<void> {
  if (!path) { selectedFile.value = null; selectedDirectoryPath.value = ''; emit('file-change', ''); return }
  const parts = path.split('/').filter(Boolean)
  let current = ''
  for (let index = 0; index < parts.length; index++) {
    current = current ? `${current}/${parts[index]}` : parts[index]
    const node = findNode(current)
    if (!node) { toast(`未在当前引用找到路径：${path}`, 'warning'); return }
    if (node.type === 'tree') {
      if (!expandedPaths.value.includes(node.path) || !node.loaded) await toggleDirectory(node, { deep: false })
      const refreshed = findNode(node.path)
      if (index === parts.length - 1) { selectedFile.value = null; selectedDirectoryPath.value = node.path; emit('file-change', node.path) }
      else if (!refreshed?.children?.length) { toast(`目录为空或无法读取路径：${path}`, 'warning'); return }
    } else if (index === parts.length - 1) openFile(node)
  }
}

async function downloadNode(node: GitTreeNode): Promise<void> {
  try { const content = await getGitRawBlobApi(props.repositoryId, props.refName, node.path, true); const url = URL.createObjectURL(content); const anchor = document.createElement('a'); anchor.href = url; anchor.download = node.name; anchor.click(); URL.revokeObjectURL(url); toast('下载已开始', 'success') } catch (cause: any) { toast(cause?.message || '下载失败', 'error') }
}
function openHistory(path: string): void { emit('open-history', path) }
function toggleLayout(): void { layout.value = layout.value === 'split' ? 'stacked' : 'split'; localStorage.setItem(LAYOUT_KEY, layout.value) }
function toast(message: string, type: 'success' | 'error' | 'warning' = 'success'): void { emit('toast', message, type) }
</script>

<style scoped>
.git-code-workspace { overflow:hidden;border:1px solid var(--git-border,#d0d7de);border-radius:8px;background:var(--git-panel,#fff); }.git-code-workspace__toolbar { display:flex;min-height:48px;align-items:center;gap:10px;border-bottom:1px solid var(--git-border,#d0d7de);padding:0 10px; }.git-code-workspace__ref { display:flex;align-items:center;gap:7px;border:1px solid var(--git-border,#d0d7de);border-radius:6px;padding:0 8px;color:#0969da; }.git-code-workspace__ref select { height:30px;max-width:230px;border:0;outline:0;background:transparent;color:var(--git-text,#24292f);font-size:12px; }.git-code-workspace__hint { min-width:0;flex:1;overflow:hidden;color:var(--git-muted,#57606a);font:11px ui-monospace,SFMono-Regular,Menlo,monospace;text-overflow:ellipsis;white-space:nowrap; }.git-code-workspace__toolbar-actions { display:flex;align-items:center;gap:3px; }.git-code-workspace__toolbar-actions button { display:inline-flex;height:30px;align-items:center;justify-content:center;gap:6px;border:0;border-radius:6px;background:transparent;padding:0 8px;color:var(--git-muted,#57606a);font-size:12px; }.git-code-workspace__toolbar-actions button:hover { background:var(--git-hover,#f6f8fa);color:#0969da; }.git-code-workspace__body { display:flex;min-height:640px; }.git-code-workspace--stacked .git-code-workspace__body { flex-direction:column; }.git-code-workspace--stacked :deep(.git-file-tree) { width:100%;height:260px;flex:0 0 260px;border-bottom:1px solid var(--git-border,#d0d7de); }.git-code-workspace--stacked :deep(.git-file-viewer) { min-height:500px; }
:global(.dark) .git-code-workspace { --git-panel:#0d1117;--git-text:#c9d1d9;--git-muted:#8b949e;--git-border:#30363d;--git-hover:#21262d; }.dark-mode .git-code-workspace { --git-panel:#0d1117;--git-text:#c9d1d9;--git-muted:#8b949e;--git-border:#30363d;--git-hover:#21262d; }
@media (min-width:1024px) and (max-width:1366px) { .git-code-workspace :deep(.git-file-tree) { width:240px;flex-basis:240px; } }.git-code-workspace :deep(.git-file-tree--collapsed) { width:48px;flex-basis:48px; } @media (max-width:1023px) { .git-code-workspace__body,.git-code-workspace--split .git-code-workspace__body { flex-direction:column; }.git-code-workspace :deep(.git-file-tree) { width:100%;height:250px;flex:0 0 250px;border-bottom:1px solid var(--git-border,#d0d7de); }.git-code-workspace :deep(.git-file-tree--collapsed) { width:100%;height:52px;flex-basis:52px; }.git-code-workspace__hint { display:none; }.git-code-workspace__toolbar { gap:6px; }.git-code-workspace__ref { min-width:0;flex:1; }.git-code-workspace__ref select { max-width:100%;width:100%; } } @media (max-width:575px) { .git-code-workspace__toolbar-actions button:last-child { font-size:0; }.git-code-workspace__toolbar-actions button:last-child i { font-size:13px; } }
</style>
