// ============================================================
// workflowIdeStore.ts — CloudFlow 工作流 IDE 编辑态
// ============================================================
// 原页面将画布、历史、面板与编译结果分散在单一 Vue
// 组件中，难以保障 50 步撤销与路由离开清理。此 Store 只保存前端编辑态，
// 运行时真源仍是 CloudFlow Runtime 编译得到的 IR。
// ============================================================

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  createDefaultWorkflowProject,
  createVisualNode,
  DEFAULT_WORKFLOW_UI,
  projectFromCloudFlowIr,
  serializeProjectToCloudFlow,
} from '@/utils/cloudflowVisualDsl'
import type { CloudFlowCompileIssue, WorkflowVisualEdge, WorkflowVisualNode, WorkflowVisualProject } from '@/types/cloudflowVisual'

const HISTORY_LIMIT = 50

function clone<T>(value: T): T { return JSON.parse(JSON.stringify(value)) as T }
function layoutKey(id?: string) { return `pcd.workflow-ide.layout.${id || 'new'}` }

export const useWorkflowIdeStore = defineStore('workflowIde', () => {
  const project = ref<WorkflowVisualProject>(createDefaultWorkflowProject())
  const selectedNodeId = ref<string | null>(null)
  const selectedEdgeId = ref<string | null>(null)
  const selectedNodeIds = ref<string[]>([])
  const selectedEdgeIds = ref<string[]>([])
  const mode = ref<'visual' | 'source'>('visual')
  const bottomTab = ref<'output' | 'problems' | 'execution' | 'debug' | 'dsl'>('output')
  const issues = ref<CloudFlowCompileIssue[]>([])
  const compilerOutput = ref('')
  const normalizedIr = ref<Record<string, unknown> | null>(null)
  const dirty = ref(false)
  const sourceDirty = ref(false)
  const saveState = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const online = ref(typeof navigator === 'undefined' ? true : navigator.onLine)
  const history = ref<WorkflowVisualProject[]>([])
  const historyIndex = ref(-1)
  const currentWorkflowId = ref<string | undefined>()
  const executionLogs = ref<string[]>([])
  const copiedNodes = ref<WorkflowVisualNode[]>([])
  const copiedEdges = ref<WorkflowVisualEdge[]>([])

  const selectedNode = computed(() => project.value.nodes.find((node) => node.id === selectedNodeId.value) || null)
  const selectedEdge = computed(() => project.value.edges.find((edge) => edge.id === selectedEdgeId.value) || null)
  const dsl = computed(() => serializeProjectToCloudFlow(project.value))
  const visualNodes = computed(() => project.value.nodes.filter((node) => node.type !== 'trigger'))
  const hasErrors = computed(() => issues.value.some((issue) => issue.severity !== 'WARNING' && issue.severity !== 'INFO'))
  const canUndo = computed(() => historyIndex.value > 0)
  const canRedo = computed(() => historyIndex.value >= 0 && historyIndex.value < history.value.length - 1)

  function persistLayout() {
    try { localStorage.setItem(layoutKey(currentWorkflowId.value), JSON.stringify(project.value.ui)) } catch { /* 私密模式/配额满时不阻断编辑。 */ }
  }
  function restoreLayout(id?: string) {
    try {
      const saved = localStorage.getItem(layoutKey(id))
      if (saved) project.value.ui = { ...DEFAULT_WORKFLOW_UI, ...JSON.parse(saved) }
    } catch { project.value.ui = { ...DEFAULT_WORKFLOW_UI } }
  }
  function resetHistory() { history.value = [clone(project.value)]; historyIndex.value = 0 }
  function record(next: WorkflowVisualProject) {
    history.value = history.value.slice(0, historyIndex.value + 1)
    history.value.push(clone(next))
    if (history.value.length > HISTORY_LIMIT) history.value.shift()
    historyIndex.value = history.value.length - 1
  }
  function commit(mutator: (draft: WorkflowVisualProject) => void, options: { history?: boolean; dirty?: boolean } = {}) {
    const draft = clone(project.value)
    mutator(draft)
    project.value = draft
    if (options.history !== false) record(draft)
    if (options.dirty !== false) { dirty.value = true; sourceDirty.value = false; if (saveState.value === 'saved') saveState.value = 'idle' }
    persistLayout()
  }
  function initialize(id?: string, graph?: Partial<WorkflowVisualProject>) {
    currentWorkflowId.value = id
    const initial = graph?.nodes?.length ? clone(graph as WorkflowVisualProject) : createDefaultWorkflowProject()
    project.value = { ...createDefaultWorkflowProject(), ...initial, ui: { ...DEFAULT_WORKFLOW_UI, ...(initial.ui || {}) } }
    restoreLayout(id)
    selectedNodeId.value = project.value.nodes.find((node) => node.type === 'trigger')?.id || null
    selectedEdgeId.value = null
    selectedNodeIds.value = selectedNodeId.value ? [selectedNodeId.value] : []
    selectedEdgeIds.value = []
    issues.value = []
    normalizedIr.value = null
    compilerOutput.value = ''
    executionLogs.value = []
    dirty.value = false
    sourceDirty.value = false
    saveState.value = 'idle'
    resetHistory()
  }
  function applyRuntimeIr(ir: Record<string, any>, persistedGraph?: Partial<WorkflowVisualProject>, markDirty = false) {
    project.value = projectFromCloudFlowIr(ir, persistedGraph || project.value)
    normalizedIr.value = ir
    restoreLayout(currentWorkflowId.value)
    resetHistory()
    dirty.value = markDirty
    sourceDirty.value = false
  }
  function addNode(type: WorkflowVisualNode['type'], position?: { x: number; y: number }, parentId?: string, branch?: string) {
    const node = createVisualNode(type, project.value.nodes.length, position)
    node.data.parentId = parentId
    node.data.branch = branch
    commit((draft) => { draft.nodes.push(node) })
    selectedNodeId.value = node.id
    selectedEdgeId.value = null
    selectedNodeIds.value = [node.id]
    selectedEdgeIds.value = []
    return node
  }
  function updateNode(id: string, patch: Partial<WorkflowVisualNode['data']>) {
    commit((draft) => {
      const target = draft.nodes.find((node) => node.id === id)
      if (target) target.data = { ...target.data, ...patch }
    })
  }
  /** CF-IDE-2026-08：边的视觉描述/路由在 graphJson 中持久化，语义仍由 kind 与控制流归属决定。 */
  function updateEdge(id: string, patch: Partial<WorkflowVisualEdge['data']>) {
    commit((draft) => {
      const target = draft.edges.find((edge) => edge.id === id)
      if (target) target.data = { ...target.data, ...patch }
    })
  }
  function updateProject(mutator: (draft: WorkflowVisualProject) => void) { commit(mutator) }
  function addEdge(edge: WorkflowVisualEdge): { accepted: boolean; reason?: string } {
    if (edge.source === edge.target) return { accepted: false, reason: '节点不能连接到自身' }
    const source = project.value.nodes.find((node) => node.id === edge.source)
    const target = project.value.nodes.find((node) => node.id === edge.target)
    if (!source || !target || ['note', 'group'].includes(source.type) || ['note', 'group'].includes(target.type)) return { accepted: false, reason: '便签和分组不能参与执行连线' }
    if (project.value.edges.some((item) => item.source === edge.source && item.target === edge.target && item.data.kind === edge.data.kind)) return { accepted: false, reason: '相同连线已经存在' }
    if (edge.data.kind === 'dependency' && createsCycle(project.value.edges, edge)) return { accepted: false, reason: '该依赖会形成循环，CloudFlow 工作流必须是 DAG' }
    commit((draft) => { draft.edges.push(edge) })
    selectedEdgeId.value = edge.id
    selectedNodeId.value = null
    selectedEdgeIds.value = [edge.id]
    selectedNodeIds.value = []
    return { accepted: true }
  }
  /**
   * CF-IDE-2026-08：Vue Flow 的框选结果在此统一落入 Store；原有单选字段
   * 继续作为属性面板的主选项，兼容既有组件调用。
   */
  function setSelection(nodeIds: string[], edgeIds: string[]) {
    selectedNodeIds.value = [...new Set(nodeIds)]
    selectedEdgeIds.value = [...new Set(edgeIds)]
    selectedNodeId.value = selectedNodeIds.value[0] || null
    selectedEdgeId.value = selectedNodeId.value ? null : (selectedEdgeIds.value[0] || null)
  }
  function selectNode(nodeId: string | null) { setSelection(nodeId ? [nodeId] : [], []) }
  function selectEdge(edgeId: string | null) { setSelection([], edgeId ? [edgeId] : []) }
  /**
   * 控制流端口不是普通 DAG 依赖：连接到 true/body/catch 等端口时，
   * 子节点必须写入 parentId/branch，随后由确定性 DSL 序列化器生成嵌套块。
   */
  function assignNodeToControl(childId: string, parentId: string, branch: string) {
    commit((draft) => {
      const child = draft.nodes.find((node) => node.id === childId)
      if (child) { child.data.parentId = parentId; child.data.branch = branch }
    })
  }
  function removeSelection() {
    const nodeIds = selectedNodeIds.value.length ? selectedNodeIds.value : (selectedNodeId.value ? [selectedNodeId.value] : [])
    const edgeIds = selectedEdgeIds.value.length ? selectedEdgeIds.value : (selectedEdgeId.value ? [selectedEdgeId.value] : [])
    const removableNodeIds = nodeIds.filter((id) => id !== '__trigger__')
    if (!removableNodeIds.length && !edgeIds.length) return
    commit((draft) => {
      draft.nodes = draft.nodes.filter((node) => !removableNodeIds.includes(node.id) && !removableNodeIds.includes(node.data.parentId || ''))
      draft.edges = draft.edges.filter((edge) => !removableNodeIds.includes(edge.source) && !removableNodeIds.includes(edge.target) && !edgeIds.includes(edge.id))
    })
    setSelection([], [])
  }
  function undo() {
    if (!canUndo.value) return
    historyIndex.value -= 1
    project.value = clone(history.value[historyIndex.value]!)
    dirty.value = true
  }
  function redo() {
    if (!canRedo.value) return
    historyIndex.value += 1
    project.value = clone(history.value[historyIndex.value]!)
    dirty.value = true
  }
  function copySelection() {
    const nodeIds = selectedNodeIds.value.length ? selectedNodeIds.value : (selectedNodeId.value ? [selectedNodeId.value] : [])
    const selection = project.value.nodes.filter((node) => nodeIds.includes(node.id) && node.id !== '__trigger__')
    copiedNodes.value = clone(selection)
    copiedEdges.value = clone(project.value.edges.filter((edge) => nodeIds.includes(edge.source) && nodeIds.includes(edge.target)))
  }
  function paste(position?: { x: number; y: number }) {
    if (!copiedNodes.value.length) return
    const copied = clone(copiedNodes.value)
    const replacements = new Map<string, string>()
    const firstPosition = copied[0]?.position || { x: 0, y: 0 }
    copied.forEach((node, index) => {
      const created = createVisualNode(node.type, project.value.nodes.length + index, {
        x: (position?.x ?? node.position.x) + (node.position.x - firstPosition.x) + 24,
        y: (position?.y ?? node.position.y) + (node.position.y - firstPosition.y) + 24,
      })
      created.data = { ...node.data, order: project.value.nodes.length + index, stepId: node.data.stepId ? `${node.data.stepId}_copy` : undefined }
      replacements.set(node.id, created.id)
      copied[index] = created
    })
    copied.forEach((node) => {
      if (node.data.parentId) node.data.parentId = replacements.get(node.data.parentId)
    })
    const pastedEdges = copiedEdges.value.map((edge) => ({
      ...clone(edge),
      id: `edge-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
      source: replacements.get(edge.source)!,
      target: replacements.get(edge.target)!,
    }))
    commit((draft) => { draft.nodes.push(...copied); draft.edges.push(...pastedEdges) })
    setSelection(copied.map((node) => node.id), pastedEdges.map((edge) => edge.id))
  }
  function setIssueState(next: CloudFlowCompileIssue[], output = '') {
    issues.value = next
    compilerOutput.value = output
    const errorPaths = new Map<string, string[]>()
    next.forEach((issue) => {
      const match = /(?:steps\.)?([A-Za-z_][\w-]*)/.exec(issue.path || issue.message)
      if (match) errorPaths.set(match[1], [...(errorPaths.get(match[1]) || []), issue.code])
    })
    commit((draft) => draft.nodes.forEach((node) => { node.data.errorKeys = errorPaths.get(node.data.stepId || node.id) || [] }), { history: false, dirty: false })
  }
  function setPanelSize(key: 'leftWidth' | 'rightWidth' | 'bottomHeight', value: number) {
    commit((draft) => { draft.ui[key] = Math.round(value) }, { history: false, dirty: false })
  }
  function setOnline(value: boolean) { online.value = value }
  function markSaved() { dirty.value = false; sourceDirty.value = false; saveState.value = 'saved' }
  function markSaving() { saveState.value = 'saving' }
  function markSaveError() { saveState.value = 'error' }
  function reset() { initialize() }

  return {
    project, selectedNodeId, selectedEdgeId, selectedNodeIds, selectedEdgeIds, selectedNode, selectedEdge, mode, bottomTab, issues, compilerOutput,
    normalizedIr, dirty, sourceDirty, saveState, online, history, historyIndex, executionLogs, dsl, visualNodes,
    hasErrors, canUndo, canRedo, initialize, applyRuntimeIr, addNode, updateNode, updateEdge, updateProject, addEdge,
    removeSelection, undo, redo, copySelection, paste, setSelection, selectNode, selectEdge, setIssueState, setPanelSize, setOnline, assignNodeToControl, markSaved,
    markSaving, markSaveError, reset,
  }
})

function createsCycle(edges: WorkflowVisualEdge[], incoming: WorkflowVisualEdge) {
  const graph = new Map<string, string[]>()
  // 显式构造候选边集合，连接前检测闭环；避免把条件/异常分支
  // 误判为 DAG 依赖，同时保持所有浏览器构建器均可稳定转换此段 TypeScript。
  const candidateEdges = edges.filter((edge) => edge.data.kind === 'dependency')
  candidateEdges.push(incoming)
  candidateEdges.forEach((edge) => {
    const targets = graph.get(edge.source) || []
    targets.push(edge.target)
    graph.set(edge.source, targets)
  })
  const visited = new Set<string>()
  const walk = (node: string): boolean => {
    if (node === incoming.source) return true
    if (visited.has(node)) return false
    visited.add(node)
    return (graph.get(node) || []).some(walk)
  }
  return walk(incoming.target)
}
