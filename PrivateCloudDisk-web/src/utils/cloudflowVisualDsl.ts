// ============================================================
// cloudflowVisualDsl.ts — DSL 与可视化投影的确定性转换
// ============================================================
// [AUDIT FIX 2.4/7.1] 原页面用正则仅抽取 step，切回画布会丢失控制流、
// 类型变量及分支信息。新实现只序列化 CloudFlow V1.1 已定义的节点，并且
// 源码回投由 Runtime IR 驱动；不在浏览器复制一套 DSL 解析器。
// ============================================================

import type {
  CloudFlowRuntimeSettings,
  CloudFlowTrigger,
  CloudFlowVariable,
  WorkflowIdeUiState,
  WorkflowVisualAction,
  WorkflowVisualEdge,
  WorkflowVisualNode,
  WorkflowVisualNodeData,
  WorkflowVisualNodeType,
  WorkflowVisualProject,
} from '@/types/cloudflowVisual'

const IDENTIFIER = /^[A-Za-z_][A-Za-z0-9_-]*$/
const STEP_IDENTIFIER = /^[A-Za-z_][A-Za-z0-9_]*$/
const REFERENCE = /^(?:vars\.[A-Za-z_][\w-]*(?:\.[A-Za-z_][\w-]*)*|steps\.[A-Za-z_][\w-]*\.output(?:\.[A-Za-z_][\w-]*)*)$/

export const DEFAULT_WORKFLOW_UI: WorkflowIdeUiState = {
  background: 'dots',
  minimapVisible: true,
  focusMode: false,
  readOnly: false,
  leftWidth: 286,
  rightWidth: 348,
  bottomHeight: 238,
  leftCollapsed: false,
  rightCollapsed: false,
  bottomCollapsed: false,
}

function randomId(prefix: string) {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return `${prefix}-${crypto.randomUUID()}`
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

export function escapeCloudFlowString(value: string) {
  return String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\r?\n/g, '\\n')
}

export function createVisualNode(type: WorkflowVisualNodeType, order: number, position?: { x: number; y: number }): WorkflowVisualNode {
  const id = type === 'trigger' ? '__trigger__' : randomId(type)
  const base: WorkflowVisualNodeData = {
    label: nodeLabel(type),
    order,
    icon: nodeIcon(type),
    color: nodeColor(type),
  }
  const data: WorkflowVisualNodeData = {
    ...base,
    ...(type === 'task' || type === 'api' || type === 'plugin' ? {
      stepId: `step_${order + 1}`,
      action: defaultAction(type),
      output: '',
    } : {}),
    ...(type === 'condition' || type === 'assert' || type === 'while' ? { expression: 'vars.enabled == true' } : {}),
    ...(type === 'foreach' ? { iterator: 'item', collection: 'vars.items' } : {}),
    ...(type === 'wait' ? { waitType: 'approval', timeout: '24h' } : {}),
    ...(type === 'try' ? { catchBinding: 'error' } : {}),
    ...(type === 'note' ? { note: '说明…' } : {}),
  }
  return {
    id,
    type,
    position: position || { x: 180 + (order % 4) * 260, y: 120 + Math.floor(order / 4) * 154 },
    data,
  }
}

function defaultAction(type: WorkflowVisualNodeType): WorkflowVisualAction {
  if (type === 'plugin') return { provider: 'plugin', pluginId: '', function: 'run', version: '1', arguments: {} }
  if (type === 'api') return { provider: 'api', service: 'notification', method: 'send', arguments: {} }
  return { provider: 'builtin', service: 'file', method: 'list', arguments: {} }
}

export function nodeLabel(type: WorkflowVisualNodeType) {
  return ({
    trigger: '触发器', task: '任务', plugin: '插件函数', api: '平台 API', condition: '条件判断',
    foreach: '遍历集合', while: '条件循环', parallel: '并行分支', try: '异常处理',
    wait: '等待审批', assert: '条件断言', group: '分组', note: '便签',
  } as Record<WorkflowVisualNodeType, string>)[type]
}

export function nodeIcon(type: WorkflowVisualNodeType) {
  return ({
    trigger: 'fa-bolt', task: 'fa-cube', plugin: 'fa-puzzle-piece', api: 'fa-exchange', condition: 'fa-code-fork',
    foreach: 'fa-repeat', while: 'fa-refresh', parallel: 'fa-random', try: 'fa-shield', wait: 'fa-clock-o',
    assert: 'fa-check-square-o', group: 'fa-object-group', note: 'fa-sticky-note-o',
  } as Record<WorkflowVisualNodeType, string>)[type]
}

export function nodeColor(type: WorkflowVisualNodeType) {
  return ({
    trigger: '#0ea5e9', task: '#10b981', plugin: '#8b5cf6', api: '#3b82f6', condition: '#f59e0b',
    foreach: '#14b8a6', while: '#06b6d4', parallel: '#6366f1', try: '#ef4444', wait: '#a855f7',
    assert: '#f97316', group: '#64748b', note: '#eab308',
  } as Record<WorkflowVisualNodeType, string>)[type]
}

export function createDefaultWorkflowProject(): WorkflowVisualProject {
  const trigger = createVisualNode('trigger', -1, { x: 80, y: 300 })
  const firstTask = createVisualNode('task', 0, { x: 390, y: 260 })
  firstTask.data.label = '列出文件'
  firstTask.data.stepId = 'list_files'
  return {
    metadata: { name: `workflow_${Date.now()}`, displayName: '新建自动化', description: '', version: '1.0' },
    trigger: { type: 'manual' },
    runtime: { maxParallel: 4, timeout: '30m', retryPolicy: { maxAttempts: 3, strategy: 'exponential' } },
    variables: [],
    nodes: [trigger, firstTask],
    edges: [{ id: randomId('edge'), source: trigger.id, target: firstTask.id, animated: true, data: { kind: 'dependency' } }],
    ui: { ...DEFAULT_WORKFLOW_UI },
  }
}

export function serializeProjectToCloudFlow(project: WorkflowVisualProject): string {
  const workflowName = validWorkflowName(project.metadata.name)
  const lines = [`workflow "${escapeCloudFlowString(workflowName)}" {`]
  lines.push('    metadata {')
  lines.push(`        display_name = "${escapeCloudFlowString(project.metadata.displayName || workflowName)}"`)
  if (project.metadata.description) lines.push(`        description = "${escapeCloudFlowString(project.metadata.description)}"`)
  lines.push(`        version = "${escapeCloudFlowString(project.metadata.version || '1.0')}"`)
  if (project.metadata.author) lines.push(`        author = "${escapeCloudFlowString(project.metadata.author)}"`)
  if (project.metadata.tags?.length) lines.push(`        tags = [${project.metadata.tags.map((tag) => `"${escapeCloudFlowString(tag)}"`).join(', ')}]`)
  lines.push('    }')
  lines.push(...serializeTrigger(project.trigger))
  lines.push(...serializeRuntime(project.runtime))
  if (project.variables.length) lines.push(...serializeVariables(project.variables))
  const executable = project.nodes.filter((node) => !['trigger', 'note', 'group'].includes(node.type) && !node.data.disabled)
  lines.push(...serializeFlow(project, executable, undefined, undefined, 4))
  lines.push('}')
  return `${lines.join('\n')}\n`
}

function serializeTrigger(trigger: CloudFlowTrigger): string[] {
  if (trigger.type === 'schedule') return [
    '    trigger {', '        schedule {',
    `            cron = "${escapeCloudFlowString(trigger.cron || '0 8 * * 1')}"`,
    ...(trigger.timezone ? [`            timezone = "${escapeCloudFlowString(trigger.timezone)}"`] : []),
    '        }', '    }',
  ]
  if (trigger.type === 'event') return ['    trigger {', '        event {', `            name = "${escapeCloudFlowString(trigger.event || 'FileUploaded')}"`, '        }', '    }']
  if (trigger.type === 'http') return ['    trigger {', '        http {', `            path = "${escapeCloudFlowString(trigger.path || '/webhook/start')}"`, '        }', '    }']
  return ['    trigger { manual {} }']
}

function serializeRuntime(runtime: CloudFlowRuntimeSettings): string[] {
  if (!runtime.timeout && !runtime.maxParallel && !runtime.retryPolicy) return []
  const lines = ['    runtime {']
  if (runtime.timeout) lines.push(`        timeout = ${safeDuration(runtime.timeout)}`)
  if (runtime.maxParallel) lines.push(`        max_parallel = ${Math.max(1, Math.min(100, Math.floor(runtime.maxParallel)))}`)
  if (runtime.retryPolicy) {
    lines.push('        retry_policy {')
    lines.push(`            max_attempts = ${Math.max(1, Math.min(20, Math.floor(runtime.retryPolicy.maxAttempts)))}`)
    lines.push(`            strategy = ${runtime.retryPolicy.strategy}`)
    lines.push('        }')
  }
  lines.push('    }')
  return lines
}

function serializeVariables(variables: CloudFlowVariable[]): string[] {
  const lines = ['    variables {']
  variables.forEach((variable) => {
    const name = validIdentifier(variable.name, 'variable')
    const type = variable.type
    if (variable.source === 'input') {
      lines.push(`        ${name} = input.${type}(required = ${variable.required ? 'true' : 'false'})`)
      return
    }
    if (variable.source === 'deferred' && variable.value === undefined) {
      lines.push(`        ${name}: ${type}`)
      return
    }
    lines.push(`        ${name}: ${type} = ${cloudFlowValue(variable.value)}`)
  })
  lines.push('    }')
  return lines
}

function serializeFlow(project: WorkflowVisualProject, executable: WorkflowVisualNode[], parentId: string | undefined, branch: string | undefined, indent: number): string[] {
  const scoped = executable
    .filter((node) => node.data.parentId === parentId && node.data.branch === branch)
    .sort((left, right) => left.data.order - right.data.order || left.position.y - right.position.y || left.position.x - right.position.x)
  return scoped.flatMap((node) => serializeNode(project, executable, node, indent))
}

function serializeNode(project: WorkflowVisualProject, executable: WorkflowVisualNode[], node: WorkflowVisualNode, indent: number): string[] {
  const pad = ' '.repeat(indent)
  if (node.type === 'task' || node.type === 'plugin' || node.type === 'api') return serializeTask(project, node, indent)
  if (node.type === 'condition') {
    const lines = [`${pad}if { ${safeExpression(node.data.expression)} } {`]
    lines.push(...serializeFlow(project, executable, node.id, 'true', indent + 4))
    const hasFalse = executable.some((child) => child.data.parentId === node.id && child.data.branch === 'false')
    lines.push(`${pad}}${hasFalse ? ' else {' : ''}`)
    if (hasFalse) { lines.push(...serializeFlow(project, executable, node.id, 'false', indent + 4)); lines.push(`${pad}}`) }
    return lines
  }
  if (node.type === 'foreach') {
    const iterator = validIdentifier(node.data.iterator || 'item', 'iterator')
    const lines = [`${pad}foreach ${iterator} in ${safeExpression(node.data.collection)} {`]
    lines.push(...serializeFlow(project, executable, node.id, 'body', indent + 4), `${pad}}`)
    return lines
  }
  if (node.type === 'while') {
    const lines = [`${pad}while { ${safeExpression(node.data.expression)} } {`]
    lines.push(...serializeFlow(project, executable, node.id, 'body', indent + 4), `${pad}}`)
    return lines
  }
  if (node.type === 'parallel') {
    const lines = [`${pad}parallel {`]
    lines.push(...serializeFlow(project, executable, node.id, 'body', indent + 4), `${pad}}`)
    return lines
  }
  if (node.type === 'try') {
    const lines = [`${pad}try {`]
    lines.push(...serializeFlow(project, executable, node.id, 'try', indent + 4), `${pad}}`)
    const hasCatch = executable.some((child) => child.data.parentId === node.id && child.data.branch === 'catch')
    if (hasCatch) {
      const binding = node.data.catchBinding ? ` ${validIdentifier(node.data.catchBinding, 'catch binding')}` : ''
      lines.push(`${pad}catch${binding} {`, ...serializeFlow(project, executable, node.id, 'catch', indent + 4), `${pad}}`)
    }
    const hasFinally = executable.some((child) => child.data.parentId === node.id && child.data.branch === 'finally')
    if (hasFinally) lines.push(`${pad}finally {`, ...serializeFlow(project, executable, node.id, 'finally', indent + 4), `${pad}}`)
    return lines
  }
  if (node.type === 'wait') return [`${pad}wait ${validIdentifier(node.data.waitType || 'approval', 'wait type')} {${node.data.timeout ? ` timeout = ${safeDuration(node.data.timeout)} ` : ''}}`]
  if (node.type === 'assert') return [`${pad}assert { ${safeExpression(node.data.expression)} }`]
  return []
}

function serializeTask(project: WorkflowVisualProject, node: WorkflowVisualNode, indent: number): string[] {
  const pad = ' '.repeat(indent)
  const action = node.data.action || defaultAction(node.type)
  const stepId = validStepIdentifier(node.data.stepId || node.id.replace(/[^A-Za-z0-9_]/g, '_'))
  const lines = [`${pad}step ${stepId} {`]
  if (node.data.label) lines.push(`${pad}    name = "${escapeCloudFlowString(node.data.label)}"`)
  const incoming = project.edges
    .filter((edge) => edge.data.kind === 'dependency' && edge.target === node.id && edge.source !== '__trigger__')
    .map((edge) => project.nodes.find((candidate) => candidate.id === edge.source)?.data.stepId)
    .filter((value): value is string => !!value && STEP_IDENTIFIER.test(value))
  if (incoming.length) lines.push(`${pad}    depends_on ${[...new Set(incoming)].join(', ')}`)
  if (node.data.expression) lines.push(`${pad}    condition { ${safeExpression(node.data.expression)} }`)
  lines.push(...serializeAction(action, indent + 4))
  if (node.data.retry) {
    lines.push(`${pad}    retry {`, `${pad}        max_attempts = ${Math.max(1, Math.min(20, Math.floor(node.data.retry.maxAttempts)) )}`, `${pad}        strategy = ${node.data.retry.strategy}`, `${pad}    }`)
  }
  if (node.data.timeout) lines.push(`${pad}    timeout = ${safeDuration(node.data.timeout)}`)
  if (node.data.output) lines.push(`${pad}    output ${validIdentifier(node.data.output, 'output')}`)
  lines.push(`${pad}}`)
  return lines
}

function serializeAction(action: WorkflowVisualAction, indent: number): string[] {
  const pad = ' '.repeat(indent)
  if (action.provider === 'plugin') {
    const lines = [`${pad}action plugin {`, `${pad}    id = "${escapeCloudFlowString(action.pluginId || '')}"`, `${pad}    function = "${escapeCloudFlowString(action.function || 'run')}"`]
    if (action.version) lines.push(`${pad}    version = "${escapeCloudFlowString(action.version)}"`)
    if (Object.keys(action.arguments || {}).length) {
      lines.push(`${pad}    input {`, ...serializeActionArguments(action.arguments, indent + 8), `${pad}    }`)
    }
    lines.push(`${pad}}`)
    return lines
  }
  const qualified = `${action.provider === 'builtin' ? '' : `${action.provider}.`}${validIdentifier(action.service || 'file', 'service')}.${validIdentifier(action.method || 'list', 'method')}`
  return [`${pad}action ${qualified} {`, ...serializeActionArguments(action.arguments || {}, indent + 4), `${pad}}`]
}

function serializeActionArguments(value: Record<string, unknown>, indent: number): string[] {
  const pad = ' '.repeat(indent)
  const lines: string[] = []
  Object.entries(value).forEach(([key, child]) => {
    const safeKey = validIdentifier(key, 'parameter')
    if (isPlainObject(child) && !('$ref' in child) && !('$expr' in child) && !('$call' in child)) {
      lines.push(`${pad}${safeKey} {`, ...serializeActionArguments(child as Record<string, unknown>, indent + 4), `${pad}}`)
    } else lines.push(`${pad}${safeKey} = ${cloudFlowValue(child)}`)
  })
  return lines
}

export function cloudFlowValue(value: unknown): string {
  if (value === null || value === undefined) return '""'
  if (typeof value === 'string') return REFERENCE.test(value) ? value : `"${escapeCloudFlowString(value)}"`
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  if (Array.isArray(value)) return `[${value.map(cloudFlowValue).join(', ')}]`
  if (isPlainObject(value)) {
    if (typeof value.$ref === 'string') return value.$ref
    if (value.$expr) return expressionIrToCloudFlow(value.$expr)
    if (value.$call && isPlainObject(value.$call)) {
      const call = value.$call as Record<string, unknown>
      const args = Array.isArray(call.arguments) ? call.arguments.map(cloudFlowValue) : []
      return `${String(call.function || 'call')}(${args.join(', ')})`
    }
    return `{${Object.entries(value).map(([key, child]) => `"${escapeCloudFlowString(key)}": ${cloudFlowValue(child)}`).join(', ')}}`
  }
  return `"${escapeCloudFlowString(String(value))}"`
}

export function expressionIrToCloudFlow(value: unknown): string {
  if (!isPlainObject(value)) return cloudFlowValue(value)
  if (typeof value.$ref === 'string') return value.$ref
  const expression = value.$expr
  if (!isPlainObject(expression)) return cloudFlowValue(value)
  if (typeof expression.operator === 'string') {
    if ('operand' in expression) return `${expression.operator}${expressionIrToCloudFlow(expression.operand)}`
    return `(${expressionIrToCloudFlow(expression.left)} ${expression.operator} ${expressionIrToCloudFlow(expression.right)})`
  }
  if ('whenTrue' in expression) return `(${expressionIrToCloudFlow(expression.condition)} ? ${expressionIrToCloudFlow(expression.whenTrue)} : ${expressionIrToCloudFlow(expression.whenFalse)})`
  if (typeof expression.function === 'string') return `${expression.function}(${Array.isArray(expression.arguments) ? expression.arguments.map(expressionIrToCloudFlow).join(', ') : ''})`
  return 'true'
}

export function projectFromCloudFlowIr(ir: Record<string, any>, persistedGraph?: Partial<WorkflowVisualProject>): WorkflowVisualProject {
  const base = createDefaultWorkflowProject()
  const metadata = ir.metadata || {}
  base.metadata = {
    name: metadata.name || base.metadata.name,
    displayName: metadata.displayName || metadata.display_name || metadata.name || base.metadata.displayName,
    description: metadata.description || '', version: metadata.version || '1.0', author: metadata.owner,
    tags: metadata.labels ? Object.entries(metadata.labels).map(([key, value]) => `${key}:${value}`) : [],
  }
  const trigger = ir.spec?.trigger || { type: 'manual' }
  base.trigger = trigger.type === 'schedule' ? { type: 'schedule', cron: trigger.cron, timezone: trigger.timezone }
    : trigger.type === 'event' ? { type: 'event', event: trigger.event }
      : trigger.type === 'http' ? { type: 'http', path: trigger.path } : { type: 'manual' }
  const runtime = ir.runtime || {}
  base.runtime = {
    timeout: runtime.timeoutSeconds ? `${runtime.timeoutSeconds}s` : undefined,
    maxParallel: runtime.maxParallel,
    retryPolicy: runtime.retryPolicy ? { maxAttempts: runtime.retryPolicy.maxAttempts, strategy: normalizeStrategy(runtime.retryPolicy.strategy) } : undefined,
  }
  base.variables = Object.entries(ir.spec?.variables || {}).map(([name, variable]: [string, any]) => ({
    name,
    type: variable.type || 'string',
    source: variable.source || 'input',
    required: !!variable.required,
    value: variable.value ?? variable.default,
  })) as CloudFlowVariable[]
  const priorNodes = new Map((persistedGraph?.nodes || []).map((node) => [node.id, node]))
  const priorEdges = new Map((persistedGraph?.edges || []).map((edge) => [edge.id, edge]))
  const irNodes = Array.isArray(ir.spec?.graph?.nodes) ? ir.spec.graph.nodes : []
  const triggerNode = priorNodes.get('__trigger__') || createVisualNode('trigger', -1, { x: 80, y: 300 })
  const nodes: WorkflowVisualNode[] = [triggerNode]
  irNodes.forEach((node: any, index: number) => {
    const previous = priorNodes.get(node.id)
    nodes.push(nodeFromIr(node, index, previous))
  })
  const edges = (ir.spec?.graph?.edges || []).map((edge: any, index: number) => {
    const same = [...priorEdges.values()].find((candidate) => candidate.source === edge.from && candidate.target === edge.to)
    return same || { id: `ir-edge-${index}-${edge.from}-${edge.to}`, source: edge.from, target: edge.to, animated: true, data: { kind: edgeKindFromIr(edge, irNodes) } }
  }) as WorkflowVisualEdge[]
  const roots = nodes.filter((node) => node.id !== '__trigger__' && !node.data.parentId)
  if (roots.length && !edges.some((edge) => edge.source === '__trigger__')) edges.unshift({ id: randomId('trigger-edge'), source: '__trigger__', target: roots[0].id, animated: true, data: { kind: 'dependency' } })
  base.nodes = nodes
  base.edges = edges
  base.ui = { ...DEFAULT_WORKFLOW_UI, ...(persistedGraph?.ui || {}) }
  return base
}

function nodeFromIr(node: any, order: number, previous?: WorkflowVisualNode): WorkflowVisualNode {
  const type = irNodeType(node)
  const action = node.action || {}
  const data: WorkflowVisualNodeData = {
    label: node.name || previous?.data.label || nodeLabel(type),
    stepId: ['task', 'plugin'].includes(node.type) ? node.id : previous?.data.stepId,
    action: ['task', 'plugin'].includes(node.type) ? {
      provider: action.provider === 'plugin' ? 'plugin' : action.provider === 'api' ? 'api' : 'builtin',
      service: action.service, method: action.method, pluginId: action.pluginId, function: action.function,
      version: action.version, arguments: action.arguments || {},
    } : undefined,
    expression: node.condition ? expressionIrToCloudFlow(node.condition) : undefined,
    iterator: node.loopConfig?.iterator,
    collection: node.loopConfig?.collection ? expressionIrToCloudFlow(node.loopConfig.collection) : undefined,
    waitType: node.errorHandler?.waitType,
    catchBinding: node.errorHandler?.catchBinding,
    output: Object.keys(node.outputs || {})[0] || undefined,
    retry: node.retry ? { maxAttempts: node.retry.maxAttempts, strategy: normalizeStrategy(node.retry.strategy) } : undefined,
    timeout: node.timeout || undefined,
    parentId: node.controlParent || undefined,
    branch: normalizeControlBranch(node.controlBranch, type),
    order: previous?.data.order ?? order,
    icon: nodeIcon(type), color: nodeColor(type),
  }
  if (type === 'wait') data.waitType = node.errorHandler?.waitType || 'approval'
  return { id: node.id, type, position: previous?.position || { x: 320 + (order % 4) * 245, y: 120 + Math.floor(order / 4) * 150 }, data }
}

function irNodeType(node: any): WorkflowVisualNodeType {
  if (node.type === 'plugin') return 'plugin'
  if (node.type === 'task') return node.action?.provider === 'api' ? 'api' : 'task'
  if (node.type === 'loop') return node.loopConfig?.kind === 'while' ? 'while' : 'foreach'
  return ['condition', 'parallel', 'try', 'wait', 'assert'].includes(node.type) ? node.type : 'task'
}

function edgeKindFromIr(edge: any, nodes: any[]): WorkflowVisualEdge['data']['kind'] {
  const target = nodes.find((node) => node.id === edge.to)
  if (target?.controlBranch === 'true') return 'condition-true'
  if (target?.controlBranch === 'false') return 'condition-false'
  if (target?.controlBranch === 'catch') return 'catch-branch'
  if (target?.controlBranch === 'finally') return 'finally-branch'
  if (target?.controlBranch === 'loop' || target?.controlBranch === 'while') return 'loop-body'
  return 'dependency'
}

function normalizeControlBranch(branch: string | undefined, type: WorkflowVisualNodeType) {
  if (!branch) return undefined
  if (branch === 'loop' || branch === 'while') return 'body'
  if (branch === 'true' || branch === 'false' || branch === 'try' || branch === 'catch' || branch === 'finally' || branch === 'body') return branch
  return type === 'parallel' ? 'body' : branch
}

function normalizeStrategy(value: string): WorkflowVisualRetry['strategy'] {
  return value === 'fixed' || value === 'linear' || value === 'exponential' ? value : 'exponential'
}

function isPlainObject(value: unknown): value is Record<string, any> { return !!value && typeof value === 'object' && !Array.isArray(value) }
function validIdentifier(value: string, label: string) { return IDENTIFIER.test(value) ? value : `${label.replace(/\W/g, '_')}_value` }
function validStepIdentifier(value: string) { return STEP_IDENTIFIER.test(value) ? value : 'step_value' }
function validWorkflowName(value: string) { return value.replace(/[^A-Za-z0-9_]/g, '_').replace(/^([^A-Za-z_])/, '_$1') || 'workflow' }
function safeDuration(value: string) { return /^\d+(?:ms|s|m|h|d)$/.test(value) ? value : '30s' }
function safeExpression(value?: string) { return value?.trim() || 'true' }

