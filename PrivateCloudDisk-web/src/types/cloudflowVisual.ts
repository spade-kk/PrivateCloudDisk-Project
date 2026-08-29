// ============================================================
// cloudflowVisual.ts — CloudFlow 可视化工作流的前端投影契约
// ============================================================
// [AUDIT FIX 2.4/7.1] 画布不是另一套工作流语言。该类型仅保存可以确定性
// 序列化为 CloudFlow DSL 的执行信息，以及明确隔离的 UI 注释/布局信息。
// 关联需求：工作流 IDE 全语法覆盖、DSL 双向同步、Runtime 编译校验。
// 影响范围：WorkflowEditorView、WorkflowCanvas、属性面板和 graphJson 草稿。
// ============================================================

export type WorkflowVisualNodeType =
  | 'trigger'
  | 'task'
  | 'plugin'
  | 'api'
  | 'condition'
  | 'foreach'
  | 'while'
  | 'parallel'
  | 'try'
  | 'wait'
  | 'assert'
  | 'group'
  | 'note'

export type WorkflowVisualEdgeKind =
  | 'dependency'
  | 'condition-true'
  | 'condition-false'
  | 'loop-body'
  | 'parallel-branch'
  | 'try-branch'
  | 'catch-branch'
  | 'finally-branch'

/**
 * CF-IDE-2026-08：视觉路由只影响画布，不改变 CloudFlow DSL 的控制流语义。
 * 保留 data.kind 作为唯一语义来源，使源码仍是可编译的唯一事实来源。
 */
export type WorkflowVisualEdgeRoute = 'smoothstep' | 'bezier' | 'straight'
export type WorkflowVisualEdgeLineStyle = 'auto' | 'solid' | 'dashed' | 'dotted'

export interface WorkflowVisualEdgeData {
  kind: WorkflowVisualEdgeKind
  condition?: string
  locked?: boolean
  variableMapping?: Record<string, string>
  label?: string
  description?: string
  route?: WorkflowVisualEdgeRoute
  lineStyle?: WorkflowVisualEdgeLineStyle
  color?: string
}

export type CloudFlowVariableType = 'string' | 'number' | 'boolean' | 'array' | 'object' | 'file' | 'user' | 'space'
export type CloudFlowVariableSource = 'input' | 'local' | 'deferred'

export interface WorkflowVisualPosition {
  x: number
  y: number
}

export interface WorkflowVisualRetry {
  maxAttempts: number
  strategy: 'fixed' | 'linear' | 'exponential'
}

export interface WorkflowVisualAction {
  provider: 'builtin' | 'plugin' | 'api'
  service?: string
  method?: string
  pluginId?: string
  function?: string
  version?: string
  arguments: Record<string, unknown>
}

export interface WorkflowVisualNodeData {
  label: string
  description?: string
  /** CloudFlow step 标识；控制节点由 node.id 唯一标识。 */
  stepId?: string
  action?: WorkflowVisualAction
  expression?: string
  iterator?: string
  collection?: string
  waitType?: string
  catchBinding?: string
  output?: string
  retry?: WorkflowVisualRetry
  timeout?: string
  disabled?: boolean
  locked?: boolean
  collapsed?: boolean
  /** 嵌套控制流归属。未设置表示顶层 flow。 */
  parentId?: string
  /** true/false/body/try/catch/finally；只用于控制容器内部排序。 */
  branch?: string
  order: number
  /** group/note 是不参与 DSL 的图形注释，不允许作为依赖端点。 */
  note?: string
  color?: string
  icon?: string
  errorKeys?: string[]
}

export interface WorkflowVisualNode {
  id: string
  type: WorkflowVisualNodeType
  position: WorkflowVisualPosition
  data: WorkflowVisualNodeData
}

export interface WorkflowVisualEdge {
  id: string
  source: string
  target: string
  type?: string
  label?: string
  animated?: boolean
  data: WorkflowVisualEdgeData
}

export interface CloudFlowVariable {
  name: string
  type: CloudFlowVariableType
  source: CloudFlowVariableSource
  required?: boolean
  value?: unknown
  description?: string
}

export interface CloudFlowTrigger {
  type: 'manual' | 'schedule' | 'event' | 'http'
  cron?: string
  timezone?: string
  event?: string
  path?: string
}

export interface CloudFlowRuntimeSettings {
  timeout?: string
  maxParallel?: number
  retryPolicy?: WorkflowVisualRetry
}

export interface WorkflowVisualMetadata {
  name: string
  displayName: string
  description: string
  version: string
  author?: string
  tags?: string[]
}

export interface WorkflowIdeUiState {
  background: 'dots' | 'lines' | 'none'
  minimapVisible: boolean
  focusMode: boolean
  readOnly: boolean
  leftWidth: number
  rightWidth: number
  bottomHeight: number
  leftCollapsed: boolean
  rightCollapsed: boolean
  bottomCollapsed: boolean
}

export interface WorkflowVisualProject {
  metadata: WorkflowVisualMetadata
  trigger: CloudFlowTrigger
  runtime: CloudFlowRuntimeSettings
  variables: CloudFlowVariable[]
  nodes: WorkflowVisualNode[]
  edges: WorkflowVisualEdge[]
  ui: WorkflowIdeUiState
}

export interface CloudFlowCompileIssue {
  code: string
  message: string
  path?: string
  line?: number | null
  column?: number | null
  severity?: 'ERROR' | 'WARNING' | 'INFO' | string
  suggestions?: string[]
  cliOutput?: string | null
}
