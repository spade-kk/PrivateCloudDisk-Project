// ============================================================
// pluginIdeStore.ts — 插件开发 IDE 状态
// ============================================================
// Web IDE 需求 1/3/9：将项目文件、打开标签、校验问题、执行日志与保存状态集中管理。
// 原有页面级 ref 迁移已完成；新 IDE 页面按路由创建本 store
// 实例并在离开时 reset，避免编辑器、文件树和属性面板之间出现状态分叉。
// ============================================================

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export type PluginIdeFileKind = 'file' | 'folder'
export type PluginIdeSaveState = 'idle' | 'saving' | 'saved' | 'error'
export type PluginIdePanelTab = 'output' | 'problems' | 'execution' | 'debug'

export interface FileTreeNodeItem {
  id: string
  name: string
  kind: PluginIdeFileKind
  path?: string
  language?: string
  content?: string
  children?: FileTreeNodeItem[]
  dirty?: boolean
  readOnly?: boolean
}

export interface PluginIdeTab {
  id: string
  label: string
  language?: string
  dirty?: boolean
}

export interface PluginIdeIssue {
  message: string
  severity: 'error' | 'warning' | 'info'
  line?: number
  column?: number
  path?: string
}

/** PluginIdeView 的兼容问题类型别名，保留统一 Problems 面板的语义。 */
export type IdeProblem = PluginIdeIssue

export interface PluginIdeLog {
  message: string
  level?: 'debug' | 'info' | 'success' | 'warning' | 'error'
  timestamp?: string
  source?: string
}

export interface PluginIdeMetadata {
  pluginId?: string | null
  name: string
  slug: string
  description: string
  version: string
  pluginType: 'CLOUD_PLUGIN' | 'LOCAL_PLUGIN' | 'WORKFLOW_PLUGIN'
  visibility?: 'PRIVATE' | 'SPACE' | 'PUBLIC'
}

export interface PluginIdeProject {
  metadata?: Partial<PluginIdeMetadata>
  files?: FileTreeNodeItem[]
  openFileIds?: string[]
  activeFileId?: string | null
  expandedFolderIds?: string[]
}

function cloneNode(node: FileTreeNodeItem): FileTreeNodeItem {
  return {
    ...node,
    children: node.children?.map(cloneNode),
  }
}

function createId(prefix: string) {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return `${prefix}-${crypto.randomUUID()}`
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export const usePluginIdeStore = defineStore('pluginIde', () => {
  const metadata = ref<PluginIdeMetadata>({
    pluginId: null,
    name: '',
    slug: '',
    description: '',
    version: '1.0.0',
    pluginType: 'CLOUD_PLUGIN',
    visibility: 'PRIVATE',
  })
  const files = ref<FileTreeNodeItem[]>([])
  const openTabs = ref<PluginIdeTab[]>([])
  const activeFileId = ref<string | null>(null)
  const expandedFolderIds = ref<string[]>([])
  const dirty = ref(false)
  const saveState = ref<PluginIdeSaveState>('idle')
  const activePanel = ref<PluginIdePanelTab>('output')
  const issues = ref<PluginIdeIssue[]>([])
  const logs = ref<PluginIdeLog[]>([])
  const executionStatus = ref<'idle' | 'running' | 'success' | 'failed' | 'timeout'>('idle')
  // [IDE-COMPAT] 新版工作区使用 runStatus/executionLogs 等命名；保留旧字段并提供兼容别名，避免迁移期间页面状态分叉。
  const runStatus = ref<'idle' | 'queued' | 'running' | 'success' | 'failed' | 'timeout'>('idle')
  const executionId = ref<string | null>(null)
  const output = ref<string[]>([])
  const executionLogs = ref<string[]>([])
  const localProblems = ref<PluginIdeIssue[]>([])
  const serverProblems = ref<PluginIdeIssue[]>([])
  const bottomOpen = ref(true)
  const rightOpen = ref(true)
  const leftPanel = ref<'files' | 'snippets' | 'templates'>('files')
  const fullscreen = ref(false)
  const autoSave = ref(true)
  const executionStartedAt = ref<string | null>(null)
  const executionEndedAt = ref<string | null>(null)

  const activeFile = computed(() => activeFileId.value ? findNode(files.value, activeFileId.value) : null)
  const problems = computed(() => [...localProblems.value, ...serverProblems.value])
  const hasErrors = computed(() => problems.value.some((issue) => issue.severity === 'error'))
  const projectId = computed(() => metadata.value.pluginId || null)
  const projectType = computed(() => metadata.value.pluginType === 'LOCAL_PLUGIN' ? 'LOCAL_PLUGIN' : 'CLOUD_PLUGIN')
  const openFileIds = computed(() => openTabs.value.map((tab) => tab.id))
  const dirtyFileIds = computed(() => flatten().filter((node) => node.kind === 'file' && node.dirty).map((node) => node.id))
  const bottomPanel = computed({ get: () => activePanel.value, set: (value: PluginIdePanelTab) => { activePanel.value = value } })

  function findNode(nodes: FileTreeNodeItem[], id: string): FileTreeNodeItem | null {
    for (const node of nodes) {
      if (node.id === id) return node
      if (node.children) {
        const child = findNode(node.children, id)
        if (child) return child
      }
    }
    return null
  }

  function flatten(nodes: FileTreeNodeItem[] = files.value): FileTreeNodeItem[] {
    return nodes.flatMap((node) => [node, ...(node.children ? flatten(node.children) : [])])
  }

  function tabFromNode(node: FileTreeNodeItem): PluginIdeTab {
    return { id: node.id, label: node.name, language: node.language, dirty: !!node.dirty }
  }

  function initialize(project: PluginIdeProject = {}) {
    metadata.value = { ...metadata.value, ...project.metadata }
    files.value = (project.files || []).map(cloneNode)
    expandedFolderIds.value = [...(project.expandedFolderIds || [])]
    const fileNodes = flatten(files.value).filter((node) => node.kind === 'file')
    const allowed = new Set(fileNodes.map((node) => node.id))
    const ids = (project.openFileIds || []).filter((id) => allowed.has(id))
    openTabs.value = ids.map((id) => tabFromNode(findNode(files.value, id)!))
    activeFileId.value = project.activeFileId && allowed.has(project.activeFileId)
      ? project.activeFileId
      : openTabs.value[0]?.id || null
    dirty.value = false
    saveState.value = 'idle'
    issues.value = []
    logs.value = []
    executionStatus.value = 'idle'
  }

  function markDirty(value = true) {
    dirty.value = value
    if (value && saveState.value === 'saved') saveState.value = 'idle'
  }

  function openFile(target: string | FileTreeNodeItem) {
    const id = typeof target === 'string' ? target : target.id
    const node = findNode(files.value, id)
    if (!node || node.kind !== 'file') return
    activeFileId.value = id
    if (!openTabs.value.some((tab) => tab.id === id)) openTabs.value.push(tabFromNode(node))
  }

  /** [IDE-COMPAT] 页面层以节点对象调用的打开入口。 */
  function openFileNode(node: FileTreeNodeItem) { openFile(node) }

  function closeTab(id: string) {
    const index = openTabs.value.findIndex((tab) => tab.id === id)
    if (index < 0) return
    openTabs.value.splice(index, 1)
    if (activeFileId.value !== id) return
    activeFileId.value = openTabs.value[index]?.id || openTabs.value[index - 1]?.id || null
  }

  function reorderTabs(ids: string[]) {
    const current = new Map(openTabs.value.map((tab) => [tab.id, tab]))
    openTabs.value = ids.map((id) => current.get(id)).filter((tab): tab is PluginIdeTab => !!tab)
  }

  function updateNode(nodes: FileTreeNodeItem[], id: string, updater: (node: FileTreeNodeItem) => FileTreeNodeItem): boolean {
    for (let index = 0; index < nodes.length; index += 1) {
      const node = nodes[index]
      if (node.id === id) {
        nodes[index] = updater(node)
        return true
      }
      if (node.children && updateNode(node.children, id, updater)) return true
    }
    return false
  }

  function setFileContent(id: string, content: string) {
    const updated = updateNode(files.value, id, (node) => ({ ...node, content, dirty: true }))
    if (!updated) return
    const tab = openTabs.value.find((item) => item.id === id)
    if (tab) tab.dirty = true
    markDirty(true)
  }

  function setProject(type: 'CLOUD_PLUGIN' | 'LOCAL_PLUGIN', id?: string | null) {
    metadata.value.pluginType = type
    metadata.value.pluginId = id || null
  }

  function setFiles(nextFiles: FileTreeNodeItem[]) {
    files.value = nextFiles.map(cloneNode)
    const first = flatten().find((node) => node.kind === 'file')
    if (first) openFile(first)
  }

  function contentFor(file: FileTreeNodeItem) { return file.content || '' }
  function updateContent(file: FileTreeNodeItem, content: string) { setFileContent(file.id, content) }
  function markFileSaved(fileId: string) {
    updateNode(files.value, fileId, (node) => ({ ...node, dirty: false }))
    const tab = openTabs.value.find((item) => item.id === fileId)
    if (tab) tab.dirty = false
    if (!dirtyFileIds.value.length) markSaved()
  }
  /** Web IDE 需求 2：未保存文件禁止静默关闭，页面可据此弹出确认或保存提示。 */
  function closeFile(fileId: string) {
    const node = findNode(files.value, fileId)
    if (node?.dirty) return false
    closeTab(fileId)
    return true
  }

  function toggleFolder(id: string) {
    const next = new Set(expandedFolderIds.value)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    expandedFolderIds.value = [...next]
  }

  function addNode(parentId: string | null, node: Omit<FileTreeNodeItem, 'id'> & { id?: string }) {
    const created: FileTreeNodeItem = { ...node, id: node.id || createId(node.kind) }
    if (created.kind === 'folder' && !created.children) created.children = []
    if (!parentId) files.value.push(created)
    else {
      const parent = findNode(files.value, parentId)
      if (!parent || parent.kind !== 'folder') return null
      parent.children = [...(parent.children || []), created]
      if (!expandedFolderIds.value.includes(parent.id)) expandedFolderIds.value.push(parent.id)
    }
    markDirty(true)
    return created
  }

  function renameNode(id: string, name: string) {
    const safeName = name.trim()
    if (!safeName) return false
    const updated = updateNode(files.value, id, (node) => ({ ...node, name: safeName, dirty: node.kind === 'file' ? true : node.dirty }))
    if (updated) {
      const tab = openTabs.value.find((item) => item.id === id)
      if (tab) tab.label = safeName
      markDirty(true)
    }
    return updated
  }

  function removeFromTree(nodes: FileTreeNodeItem[], id: string): FileTreeNodeItem | null {
    const index = nodes.findIndex((node) => node.id === id)
    if (index >= 0) return nodes.splice(index, 1)[0] || null
    for (const node of nodes) {
      if (node.children) {
        const removed = removeFromTree(node.children, id)
        if (removed) return removed
      }
    }
    return null
  }

  function deleteNode(id: string) {
    const removed = removeFromTree(files.value, id)
    if (!removed) return false
    flatten([removed]).filter((node) => node.kind === 'file').forEach((node) => closeTab(node.id))
    expandedFolderIds.value = expandedFolderIds.value.filter((folderId) => folderId !== id)
    if (activeFileId.value === id) activeFileId.value = openTabs.value[0]?.id || null
    markDirty(true)
    return true
  }

  function setValidation(nextIssues: PluginIdeIssue[]) {
    issues.value = [...nextIssues]
    localProblems.value = [...nextIssues]
    activePanel.value = nextIssues.length ? 'problems' : activePanel.value
  }

  function appendLog(log: PluginIdeLog | string) {
    const normalized = typeof log === 'string' ? { message: log, level: 'info' as const, timestamp: new Date().toISOString() } : { ...log, timestamp: log.timestamp || new Date().toISOString() }
    logs.value.push(normalized)
    executionLogs.value.push(normalized.message)
    output.value.push(normalized.message)
    if (logs.value.length > 500) logs.value.splice(0, logs.value.length - 500)
  }

  function beginExecution() {
    executionStatus.value = 'running'
    runStatus.value = 'running'
    executionStartedAt.value = new Date().toISOString()
    executionEndedAt.value = null
    activePanel.value = 'execution'
  }

  function finishExecution(status: Exclude<typeof executionStatus.value, 'idle' | 'running'>) {
    executionStatus.value = status
    runStatus.value = status
    executionEndedAt.value = new Date().toISOString()
  }

  function markSaving() { saveState.value = 'saving' }
  function markSaved() {
    dirty.value = false
    saveState.value = 'saved'
    flatten().forEach((node) => { node.dirty = false })
    openTabs.value.forEach((tab) => { tab.dirty = false })
  }
  function markSaveError() { saveState.value = 'error' }

  function reset() {
    metadata.value = { pluginId: null, name: '', slug: '', description: '', version: '1.0.0', pluginType: 'CLOUD_PLUGIN', visibility: 'PRIVATE' }
    files.value = []
    openTabs.value = []
    activeFileId.value = null
    expandedFolderIds.value = []
    dirty.value = false
    saveState.value = 'idle'
    activePanel.value = 'output'
    leftPanel.value = 'files'
    issues.value = []
    logs.value = []
    output.value = []
    executionLogs.value = []
    localProblems.value = []
    serverProblems.value = []
    runStatus.value = 'idle'
    executionId.value = null
    executionStatus.value = 'idle'
    executionStartedAt.value = null
    executionEndedAt.value = null
  }

  return {
    metadata, files, openTabs, activeFileId, expandedFolderIds, dirty, saveState,
    activePanel, issues, logs, executionStatus, executionStartedAt, executionEndedAt,
    runStatus, executionId, output, executionLogs, localProblems, serverProblems, bottomOpen, rightOpen, leftPanel, fullscreen, autoSave,
    projectId, projectType, openFileIds, dirtyFileIds, bottomPanel, problems,
    activeFile, hasErrors, initialize, setProject, setFiles, findNode, flatten, markDirty, openFile, openFileNode, closeFile, closeTab,
    reorderTabs, setFileContent, contentFor, updateContent, markFileSaved, toggleFolder, addNode, renameNode, deleteNode, setValidation,
    appendLog, beginExecution, finishExecution, markSaving, markSaved, markSaveError, reset,
  }
})
