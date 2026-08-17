<template>
  <div class="workflow-editor-page" :class="{ 'is-fullscreen': fullscreen }">
    <PageHeader
      :title="workflowId ? '编辑工作流' : '创建工作流'"
      description="源码与可视化画布使用同一份 DSL，可随时切换"
    >
      <template #actions>
        <div class="mode-switch">
          <button :class="{ active: mode === 'visual' }" @click="switchMode('visual')"><i class="fa fa-sitemap"></i> 可视化</button>
          <button :class="{ active: mode === 'source' }" @click="switchMode('source')"><i class="fa fa-code"></i> DSL</button>
        </div>
        <button class="secondary-button" @click="validate"><i class="fa fa-check-circle"></i> 校验</button>
        <button class="secondary-button" type="button" :disabled="testRunning" @click="testRun"><i class="fa" :class="testRunning ? 'fa-spinner fa-spin' : 'fa-play-circle'"></i> {{ testRunning ? '测试中…' : '沙盒测试' }}</button>
        <button class="icon-button" type="button" title="撤销" :disabled="historyIndex <= 0" @click="undo"><i class="fa fa-undo"></i></button>
        <button class="icon-button" type="button" title="重做" :disabled="historyIndex >= history.length - 1" @click="redo"><i class="fa fa-repeat"></i></button>
        <button class="icon-button" type="button" title="全屏编辑" @click="fullscreen = !fullscreen"><i :class="fullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i></button>
        <button class="primary-button" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存草稿' }}</button>
        <button class="publish-button" :disabled="saving || publishing" @click="publish">
          <i class="fa" :class="publishing ? 'fa-spinner fa-spin' : 'fa-paper-plane'"></i>
          {{ publishing ? '发布中…' : '发布版本' }}
        </button>
        <button
          v-if="workflowId && workflowStatus === 'PUBLISHED'"
          class="secondary-button"
          :disabled="submittingMarket"
          @click="submitMarketplace"
        >
          <i class="fa fa-shopping-bag"></i>
          {{ submittingMarket ? '提交中…' : '提交模板市场' }}
        </button>
      </template>
    </PageHeader>

    <section class="mb-4 grid gap-3 rounded-2xl border border-neutral-200 bg-white p-4 shadow-sm md:grid-cols-3">
      <label class="form-label">名称<input v-model.trim="meta.name" class="form-input" /></label>
      <label class="form-label">标识<input v-model.trim="meta.slug" class="form-input" :disabled="!!workflowId" /></label>
      <label class="form-label">描述<input v-model.trim="meta.description" class="form-input" /></label>
    </section>

    <div v-if="mode === 'visual'" class="visual-layout">
      <aside v-if="!leftCollapsed" class="capability-panel">
        <div class="sticky top-0 z-10 bg-white pb-3">
          <h2 class="font-bold text-neutral-800">能力中心</h2>
          <p class="mt-1 text-xs text-neutral-400">拖入画布或点击添加</p>
          <input v-model.trim="capabilitySearch" class="form-input mt-3" placeholder="搜索能力" />
        </div>
        <div class="space-y-4">
          <section v-for="group in capabilityGroups" :key="group.type">
            <h3 class="mb-2 text-[11px] font-bold uppercase tracking-wider text-neutral-400">{{ group.label }}</h3>
            <button
              v-for="capability in group.items"
              :key="capability.capabilityKey"
              class="capability-card"
              draggable="true"
              @dragstart="startDrag($event, capability)"
              @click="addCapability(capability)"
            >
              <span class="capability-dot" :class="sourceTone(capability.sourceType)"></span>
              <span class="min-w-0 text-left"><strong>{{ capability.displayName }}</strong><small>{{ capability.capabilityKey }}</small></span>
              <i class="fa fa-plus ml-auto"></i>
            </button>
          </section>
        </div>
      </aside>

      <section class="flow-canvas" @dragover.prevent @drop="dropCapability">
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          fit-view-on-init
          :min-zoom="0.25"
          :max-zoom="1.8"
          @connect="onConnect"
          @node-click="selectNode"
        >
          <Background pattern-color="#dbe4f0" :gap="22" />
          <Controls />
          <template #node-action="{ data }">
            <div class="workflow-node" :class="{ selected: selectedNode?.id === data.id }">
              <span class="node-icon" :class="sourceTone(data.sourceType)"><i class="fa fa-bolt"></i></span>
              <div><strong>{{ data.label }}</strong><small>{{ data.capabilityKey }}</small></div>
              <Handle type="target" :position="Position.Left" />
              <Handle type="source" :position="Position.Right" />
            </div>
          </template>
        </VueFlow>
        <div v-if="!nodes.length" class="canvas-empty"><i class="fa fa-random"></i><p>把左侧能力拖入画布开始编排</p></div>
        <div v-else class="canvas-minimap" aria-label="画布小地图">
          <span v-for="node in nodes" :key="node.id" :style="{ left: `${Math.min(90, Math.max(4, node.position.x / 12))}%`, top: `${Math.min(86, Math.max(4, node.position.y / 10))}%` }"></span>
        </div>
      </section>

      <aside v-if="!rightCollapsed" class="property-panel">
        <template v-if="selectedNode">
          <h2 class="font-bold text-neutral-800">节点属性</h2>
          <p class="mt-1 break-all text-xs text-neutral-400">{{ selectedNode.data.capabilityKey }}</p>
          <label class="form-label mt-4">步骤 ID<input v-model="selectedNode.data.stepId" class="form-input" @change="syncDslFromGraph" /></label>
          <label class="form-label">条件表达式<input v-model="selectedNode.data.condition" class="form-input" placeholder="${{ trigger.file_type == 'pdf' }}" @change="syncDslFromGraph" /></label>
          <label class="form-label">参数（JSON）
            <textarea v-model="selectedNode.data.withJson" class="form-input min-h-36 font-mono text-xs" @change="syncDslFromGraph"></textarea>
          </label>
          <button class="danger-button" @click="removeSelectedNode"><i class="fa fa-trash-o"></i> 删除节点</button>
        </template>
        <div v-else class="py-16 text-center text-sm text-neutral-400"><i class="fa fa-mouse-pointer mb-3 block text-2xl"></i>选择节点配置参数</div>
      </aside>
    </div>

    <PluginMonacoEditor
      v-else
      v-model="dsl"
      language="cloudflow"
      title="workflow.cflow"
      height="min(70vh, 760px)"
      :capabilities="capabilities"
      @validation-change="frontendValid = $event"
    />

    <section v-if="validationIssues.length" class="mt-4 rounded-2xl border border-danger/20 bg-danger/5 p-4">
      <h2 class="text-sm font-bold text-danger">工作流校验问题</h2>
      <ul class="mt-2 space-y-1 text-xs text-danger">
        <li v-for="issue in validationIssues" :key="issue.code + issue.path"><code>{{ issue.path }}</code> · {{ issue.message }}</li>
      </ul>
    </section>

    <BottomPanel v-if="bottomOpen" v-model="activePanel" :problem-count="validationIssues.length" @collapse="bottomOpen = false">
      <template #output><p class="workflow-console-empty">保存、校验和画布操作的输出会显示在这里。</p></template>
      <template #problems><button v-for="issue in validationIssues" :key="issue.code + issue.path" class="workflow-problem" type="button" @click="mode = 'source'"><i class="fa fa-warning"></i><span>{{ issue.message }}</span><small>{{ issue.path }}</small></button><p v-if="!validationIssues.length" class="workflow-console-empty">暂无校验问题</p></template>
      <template #execution><div v-for="(line, index) in executionLogs" :key="index" class="workflow-log-line">{{ line }}</div><p v-if="!executionLogs.length" class="workflow-console-empty">尚未运行工作流</p></template>
      <template #debug><p class="workflow-console-empty">execution_id={{ executionId || 'none' }} · 节点 {{ nodes.length }} · 连线 {{ edges.length }}</p></template>
    </BottomPanel>
    <button v-else class="bottom-reopen" type="button" @click="bottomOpen = true"><i class="fa fa-terminal"></i> 打开输出面板</button>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useRoute, useRouter } from 'vue-router'
import { VueFlow, Handle, Position, type Connection } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import PageHeader from '@/components/common/PageHeader.vue'
import BottomPanel from '@/components/plugins/ide/BottomPanel.vue'
import PluginMonacoEditor from '@/components/plugins/PluginMonacoEditor.vue'
import {
  createWorkflowApi,
  getLatestWorkflowVersionApi,
  getWorkflowApi,
  listCapabilitiesApi,
  publishWorkflowApi,
  runWorkflowTestApi,
  submitWorkflowMarketplaceApi,
  updateWorkflowApi,
  validateWorkflowApi,
  type CapabilityInfo,
} from '@/api/modules/workflows'
import { useToastStore } from '@/stores/toastStore'

const route = useRoute()
const router = useRouter()
const toast = useToastStore()
const workflowId = computed(() => route.params.workflowId as string | undefined)
const mode = ref<'visual' | 'source'>('visual')
const saving = ref(false)
const publishing = ref(false)
const submittingMarket = ref(false)
const testRunning = ref(false)
const dirty = ref(false)
const activePanel = ref<'output' | 'problems' | 'execution' | 'debug'>('output')
const bottomOpen = ref(true)
const executionLogs = ref<string[]>([])
const executionId = ref<string | null>(null)
const leftCollapsed = ref(false)
const rightCollapsed = ref(false)
const fullscreen = ref(false)
const workflowStatus = ref('DRAFT')
const loadingState = ref(true)
const currentVersion = ref(1)
const frontendValid = ref(true)
const validationIssues = ref<Array<{ code: string; path: string; message: string }>>([])
const capabilitySearch = ref('')
const capabilities = ref<CapabilityInfo[]>([])
interface WorkflowCanvasNode {
  id: string
  type: string
  position: { x: number; y: number }
  data: Record<string, any>
}
interface WorkflowCanvasEdge {
  id: string
  source: string
  target: string
  animated?: boolean
}
// Vue Flow 的公开 Node 泛型存在递归联合，Vue 3.5 模板推断会触发 TS2589；
// 画布只持久化平台定义的最小字段，因此在边界处使用稳定的本地 DTO。
const nodes = ref<WorkflowCanvasNode[]>([])
const edges = ref<WorkflowCanvasEdge[]>([])
const selectedNode = ref<WorkflowCanvasNode | null>(null)
const meta = reactive({ name: '新建自动化', slug: `workflow-${Date.now()}`, description: '' })
const rowVersion = ref(0)
const history = ref<Array<{ nodes: WorkflowCanvasNode[]; edges: WorkflowCanvasEdge[] }>>([])
const historyIndex = ref(-1)

// [CLOUDFLOW-DSL-002] 新建实例使用 Demo 规定的块结构，不再生成旧 YAML-like 语法。
const dsl = ref(`workflow "weekly_sales_report" {
    metadata {
        display_name = "销售周报"
        description = "每周一自动生成销售周报"
        version = "1.0"
    }
    trigger { schedule { cron = "0 8 * * 1" timezone = "Asia/Shanghai" } }
    runtime { timeout = 30m max_parallel = 4 retry_policy { max_attempts = 3 strategy = "exponential" } }
    variables {
        sales_node_id = input.string(required = true)
        template_file_id = input.string(required = true)
        report_node_id = input.string(required = true)
    }
    step collect_files {
        name = "收集销售文件"
        action file.list { node = vars.sales_node_id filter { extension = "xlsx" } }
        output excel_files
    }
    step aggregate_data {
        name = "销售数据统计"
        depends_on collect_files
        action data.aggregate_excel { input { files = collect_files.output } group_by = "region" metrics { sum("sales") average("profit") } }
        output report_data
    }
    step generate_report {
        name = "生成销售报告"
        depends_on aggregate_data
        condition { aggregate_data.output.row_count > 0 }
        action plugin { id = "8ae47c8d-41c5-4b9d-87e7-2f93b74d34d7" function = "generate_report" version = "1" input { data = aggregate_data.output template = vars.template_file_id } }
        retry { max_attempts = 2 backoff = exponential }
        output report_file
    }
    step save_report { name = "保存报告" depends_on generate_report action file.save { source = generate_report.output.file_id target = vars.report_node_id } }
}`)

const capabilityGroups = computed(() => {
  const labels: Record<string, string> = {
    BUILTIN: '内置函数',
    API: '平台 API',
    PLATFORM_API: '平台 API',
    PLUGIN: '云插件函数',
    LOCAL_PLUGIN: '在线客户端能力',
  }
  const query = capabilitySearch.value.toLowerCase()
  const grouped = new Map<string, CapabilityInfo[]>()
  capabilities.value.filter((item) => !query
    || item.displayName.toLowerCase().includes(query)
    || item.capabilityKey.toLowerCase().includes(query))
    .forEach((item) => grouped.set(item.sourceType, [...(grouped.get(item.sourceType) || []), item]))
  return Array.from(grouped, ([type, items]) => ({ type, label: labels[type] || type, items }))
})

function uniqueStepId(capability: CapabilityInfo) {
  const base = capability.capabilityKey.split(/[:.@]/).filter(Boolean).pop()?.replace(/-/g, '_') || 'step'
  let candidate = base.replace(/^[^a-z]+/, 'step_').slice(0, 48)
  let suffix = 2
  while (nodes.value.some((node) => node.data.stepId === candidate)) candidate = `${base}_${suffix++}`
  return candidate
}

function addCapability(capability: CapabilityInfo, position?: { x: number; y: number }) {
  const id = crypto.randomUUID()
  nodes.value.push({
    id,
    type: 'action',
    position: position || { x: 120 + nodes.value.length * 36, y: 100 + nodes.value.length * 90 },
    data: {
      id,
      label: capability.displayName,
      capabilityKey: capability.capabilityKey,
      sourceType: capability.sourceType,
      stepId: uniqueStepId(capability),
      condition: '',
      withJson: '{}',
    },
  })
  recordHistory()
  syncDslFromGraph()
}

function startDrag(event: DragEvent, capability: CapabilityInfo) {
  event.dataTransfer?.setData('application/pcd-capability', capability.capabilityKey)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy'
}

function dropCapability(event: DragEvent) {
  const key = event.dataTransfer?.getData('application/pcd-capability')
  const capability = capabilities.value.find((item) => item.capabilityKey === key)
  if (!capability) return
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect()
  addCapability(capability, { x: event.clientX - bounds.left - 100, y: event.clientY - bounds.top - 35 })
}

function onConnect(connection: Connection) {
  if (!connection.source || !connection.target) return
  if (edges.value.some((edge) => edge.source === connection.source && edge.target === connection.target)) return
  edges.value.push({ id: crypto.randomUUID(), source: connection.source, target: connection.target, animated: true })
  recordHistory()
  syncDslFromGraph()
}

function selectNode(event: any) { selectedNode.value = event.node }
function removeSelectedNode() {
  if (!selectedNode.value) return
  const id = selectedNode.value.id
  const nodeIndex = nodes.value.findIndex((node) => node.id === id)
  if (nodeIndex >= 0) nodes.value.splice(nodeIndex, 1)
  for (let index = edges.value.length - 1; index >= 0; index -= 1) {
    const edge = edges.value[index]
    if (edge && (edge.source === id || edge.target === id)) edges.value.splice(index, 1)
  }
  selectedNode.value = null
  recordHistory()
  syncDslFromGraph()
}

/**
 * [IDE-WORKFLOW-UNDO] 画布操作采用轻量快照历史，避免把 Vue Flow 内部对象直接
 * 暴露给页面状态；快照只包含 DSL 所需字段，支持 Ctrl/Cmd+Z 与重做。
 */
function snapshot() {
  return {
    nodes: JSON.parse(JSON.stringify(nodes.value)) as WorkflowCanvasNode[],
    edges: JSON.parse(JSON.stringify(edges.value)) as WorkflowCanvasEdge[],
  }
}
function recordHistory() {
  const next = snapshot()
  history.value = history.value.slice(0, historyIndex.value + 1)
  history.value.push(next)
  if (history.value.length > 50) history.value.shift()
  historyIndex.value = history.value.length - 1
  dirty.value = true
}
function restoreHistory(index: number) {
  const item = history.value[index]
  if (!item) return
  nodes.value = JSON.parse(JSON.stringify(item.nodes))
  edges.value = JSON.parse(JSON.stringify(item.edges))
  historyIndex.value = index
  selectedNode.value = null
  syncDslFromGraph()
}
function undo() { if (historyIndex.value > 0) restoreHistory(historyIndex.value - 1) }
function redo() { if (historyIndex.value < history.value.length - 1) restoreHistory(historyIndex.value + 1) }

function syncDslFromGraph() {
  const steps = nodes.value.map((node) => {
    const incoming = edges.value.filter((edge) => edge.target === node.id)
      .map((edge) => nodes.value.find((candidate) => candidate.id === edge.source)?.data.stepId)
      .filter(Boolean)
    let withValues: Record<string, unknown> = {}
    try { withValues = JSON.parse(node.data.withJson || '{}') } catch { /* 后端校验会给出更精确提示 */ }
    const capabilityParts = node.data.capabilityKey.split(':')
    const action = capabilityParts[0] === 'plugin'
      ? 'plugin'
      : (capabilityParts[1] || node.data.capabilityKey)
    const lines = [`    step ${node.data.stepId} {`]
    if (incoming.length) lines.push(`        depends_on ${incoming.join(', ')}`)
    lines.push(`        action ${action} {`)
    Object.entries(withValues).forEach(([key, value]) => {
      const serialized = typeof value === 'string' ? `"${value.replaceAll('"', '\\"')}"` : String(value)
      lines.push(`        with ${key} = ${serialized}`)
    })
    lines.push('        }', '    }')
    return lines.join('\n')
  })
  // [CLOUDFLOW-DSL-001] 画布现在生成 CloudFlow 自定义 DSL，不再生成 YAML。
  const workflowName = (meta.slug || 'new_automation').replace(/[^A-Za-z0-9_]/g, '_')
  dsl.value = [`workflow "${workflowName}" {`, '    metadata {', `        display_name = "${meta.name.replaceAll('"', '\\"')}"`, '        version = "1.0"', '    }', '    trigger { manual {} }', ...steps, '}', ''].join('\n')
}

function syncGraphFromDsl() {
  try {
    // [CLOUDFLOW-DSL-001] 前端只解析 CloudFlow 的 step 行；复杂语义仍由后端/Rust Runtime 校验。
    const stepPattern = /^\s*step\s+([a-z][a-z0-9_]*)\s*\{[\s\S]*?action\s+([^\s{]+)/gm
    const steps: Array<{ id: string; uses: string; needs: string[] }> = []
    let match: RegExpExecArray | null
    while ((match = stepPattern.exec(dsl.value)) !== null) {
      const uses = match[2].includes('.') ? `${match[2].split('.')[0]}:${match[2].split('.').slice(1).join('.')}` : match[2]
      steps.push({ id: match[1], uses, needs: [] })
    }
    if (!steps.length) return
    const nextNodes: WorkflowCanvasNode[] = steps.map((step, index) => {
      const capability = capabilities.value.find((item) => item.capabilityKey === step.uses)
      const id = crypto.randomUUID()
      return {
        id,
        type: 'action',
        position: { x: 100 + (index % 3) * 260, y: 80 + Math.floor(index / 3) * 150 },
        data: {
          id,
          label: capability?.displayName || step.uses,
          capabilityKey: step.uses,
          sourceType: capability?.sourceType || 'BUILTIN',
          stepId: step.id,
          condition: '',
          withJson: '{}',
          needs: step.needs,
        },
      }
    })
    const byStep = new Map(nextNodes.map((node) => [node.data.stepId, node.id]))
    const nextEdges: WorkflowCanvasEdge[] = []
    nextNodes.forEach((node) => {
      const needs = Array.isArray(node.data.needs) ? node.data.needs : node.data.needs ? [node.data.needs] : []
      needs.forEach((need: string) => {
        const source = byStep.get(need)
        if (source) nextEdges.push({ id: crypto.randomUUID(), source, target: node.id, animated: true })
      })
    })
    nodes.value = nextNodes
    edges.value = nextEdges
  } catch { /* Monaco CloudFlow 标记负责展示解析错误 */ }
}

function switchMode(next: 'visual' | 'source') {
  if (next === 'visual') syncGraphFromDsl()
  else syncDslFromGraph()
  mode.value = next
  dirty.value = true
}

async function validate() {
  if (mode.value === 'visual') syncDslFromGraph()
  try {
    const result = (await validateWorkflowApi(dsl.value, graphSnapshot())).data
    validationIssues.value = result.issues || []
    frontendValid.value = result.valid
    toast.showToast(result.valid ? '工作流结构、能力引用与 DAG 校验通过' : '请修复工作流校验问题', result.valid ? 'success' : 'warning')
    return result.valid
  } catch (error: any) {
    toast.showToast(error?.message || '校验服务暂时不可用', 'error')
    return false
  }
}

async function testRun() {
  if (!workflowId.value) {
    toast.showToast('请先保存工作流草稿，再进行沙盒测试', 'warning')
    return
  }
  if (!(await validate())) return
  testRunning.value = true
  activePanel.value = 'execution'
  executionLogs.value = ['已提交工作流 dry-run，等待隔离执行器响应…']
  try {
    const result = (await runWorkflowTestApi(workflowId.value, {}, currentVersion.value)).data
    executionId.value = result.executionId
    executionLogs.value.push(`execution_id=${result.executionId}`, `status=${result.status}`)
    toast.showToast('测试运行已进入异步队列', 'success')
  } catch (error: any) {
    executionLogs.value.push(`测试运行失败：${error?.message || '服务暂不可用'}`)
    toast.showToast('测试接口暂不可用，未触发正式工作流', 'warning')
  } finally { testRunning.value = false }
}

function graphSnapshot() {
  return {
    nodes: nodes.value.map(({ id, type, position, data }) => ({ id, type, position, data })),
    edges: edges.value.map(({ id, source, target }) => ({ id, source, target })),
  }
}

async function save(): Promise<boolean> {
  if (!meta.name || !/^[a-z0-9][a-z0-9-]{1,118}[a-z0-9]$/.test(meta.slug)) {
    toast.showToast('请填写名称和合法的工作流标识', 'warning')
    return false
  }
  if (!(await validate())) return false
  saving.value = true
  try {
    if (workflowId.value) {
      const updated = await updateWorkflowApi(workflowId.value, rowVersion.value, {
        name: meta.name, description: meta.description, dsl: dsl.value, graph: graphSnapshot(),
      })
      rowVersion.value = updated.data.rowVersion
      workflowStatus.value = updated.data.status
      currentVersion.value = (await getLatestWorkflowVersionApi(workflowId.value)).data.version
      toast.showToast('工作流新版本已保存', 'success')
    } else {
      const result = await createWorkflowApi({
        name: meta.name, slug: meta.slug, description: meta.description,
        dsl: dsl.value, graph: graphSnapshot(),
      })
      toast.showToast('工作流草稿已创建', 'success')
      await router.replace(`/app/workflows/${result.data.workflowId}/edit`)
      rowVersion.value = result.data.rowVersion
      workflowStatus.value = result.data.status
      currentVersion.value = 1
    }
    dirty.value = false
    return true
  } catch (error: any) {
    toast.showToast(error?.message || '保存失败', 'error')
    return false
  } finally { saving.value = false }
}

async function publish() {
  if (publishing.value) return
  publishing.value = true
  try {
    if (!(await save())) return
    const id = workflowId.value
    if (!id) throw new Error('工作流标识尚未生成')
    await publishWorkflowApi(id, currentVersion.value)
    workflowStatus.value = 'PUBLISHED'
    toast.showToast(`工作流 v${currentVersion.value} 已发布为不可变版本`, 'success')
  } catch (error: any) {
    toast.showToast(error?.message || '发布失败', 'error')
  } finally {
    publishing.value = false
  }
}

async function submitMarketplace() {
  const id = workflowId.value
  if (!id || submittingMarket.value) return
  if (!confirm('提交当前已发布工作流到模板市场审核？')) return
  submittingMarket.value = true
  try {
    await submitWorkflowMarketplaceApi(id)
    toast.showToast('工作流模板已提交市场审核', 'success')
  } catch (error: any) {
    toast.showToast(error?.message || '提交审核失败', 'error')
  } finally {
    submittingMarket.value = false
  }
}

function sourceTone(type: string) {
  return {
    BUILTIN: 'bg-emerald-100 text-emerald-600',
    API: 'bg-blue-100 text-blue-600',
    PLATFORM_API: 'bg-blue-100 text-blue-600',
    PLUGIN: 'bg-violet-100 text-violet-600',
    LOCAL_PLUGIN: 'bg-amber-100 text-amber-600',
  }[type] || 'bg-neutral-100 text-neutral-600'
}

async function load() {
  loadingState.value = true
  try {
    capabilities.value = (await listCapabilitiesApi()).data
    if (workflowId.value) {
      const [workflow, version] = await Promise.all([
        getWorkflowApi(workflowId.value),
        getLatestWorkflowVersionApi(workflowId.value),
      ])
      Object.assign(meta, {
        name: workflow.data.name,
        slug: workflow.data.slug,
        description: workflow.data.description || '',
      })
      rowVersion.value = workflow.data.rowVersion
      workflowStatus.value = workflow.data.status
      currentVersion.value = version.data.version
      dsl.value = version.data.dslText
      if (version.data.graphJson) {
        const graph = JSON.parse(version.data.graphJson)
        nodes.value = graph.nodes || []
        edges.value = graph.edges || []
      } else syncGraphFromDsl()
    } else syncGraphFromDsl()
    history.value = [snapshot()]
    historyIndex.value = 0
    dirty.value = false
  } catch (error: any) {
    toast.showToast(error?.message || '编辑器数据加载失败', 'error')
  } finally {
    loadingState.value = false
  }
}
function onKeydown(event: KeyboardEvent) {
  const modifier = event.ctrlKey || event.metaKey
  if (!modifier) return
  if (event.key.toLowerCase() === 'z') { event.preventDefault(); event.shiftKey ? redo() : undo() }
  if (event.key.toLowerCase() === 'y') { event.preventDefault(); redo() }
  if (event.key.toLowerCase() === 's') { event.preventDefault(); void save() }
}
watch([dsl, nodes, edges], () => { if (historyIndex.value >= 0 && !loadingState.value) dirty.value = true }, { deep: true, flush: 'sync' })
onMounted(() => { load(); window.addEventListener('keydown', onKeydown) })
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
onBeforeRouteLeave(() => { if (dirty.value && !confirm('当前工作流有未保存更改，确定离开吗？')) return false })
</script>

<style scoped>
.workflow-editor-page.is-fullscreen { position: fixed; inset: 0; z-index: 120; overflow: auto; padding: 12px; background: #eef2f7; }
.visual-layout { display: grid; grid-template-columns: 260px minmax(0, 1fr) 280px; min-height: 680px; overflow: hidden; border: 1px solid #e5e7eb; border-radius: 16px; background: #fff; box-shadow: 0 8px 28px rgba(15, 23, 42, .07); }
.capability-panel,.property-panel { overflow-y: auto; padding: 16px; background: #fff; }
.capability-panel { border-right: 1px solid #e5e7eb; }
.property-panel { border-left: 1px solid #e5e7eb; }
.flow-canvas { position: relative; min-width: 0; background: #f8fafc; }
.capability-card { @apply mb-2 flex w-full items-center gap-2 rounded-xl border border-neutral-200 p-3 text-neutral-600 transition hover:border-primary/30 hover:bg-primary/[0.03]; }
.capability-card strong { @apply block truncate text-xs text-neutral-700; }
.capability-card small { @apply mt-0.5 block truncate font-mono text-[9px] text-neutral-400; }
.capability-dot { @apply h-3 w-3 shrink-0 rounded-full; }
.workflow-node { display:flex;align-items:center;gap:10px;width:220px;border:1px solid #dbe4f0;border-radius:14px;background:#fff;padding:13px;box-shadow:0 8px 24px rgba(15,23,42,.08); }
.workflow-node.selected { border-color:#165dff;box-shadow:0 0 0 3px rgba(22,93,255,.12); }
.workflow-node strong { display:block;max-width:150px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px;color:#334155; }
.workflow-node small { display:block;max-width:150px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font:9px monospace;color:#94a3b8; }
.node-icon { @apply flex h-8 w-8 shrink-0 items-center justify-center rounded-lg; }
.canvas-empty { position:absolute;inset:0;display:flex;pointer-events:none;align-items:center;justify-content:center;flex-direction:column;color:#94a3b8;gap:10px;font-size:13px; }
.canvas-empty i { font-size:28px; }
.canvas-minimap { position:absolute;right:14px;bottom:14px;width:150px;height:94px;border:1px solid #cbd5e1;border-radius:10px;background:rgba(255,255,255,.86);box-shadow:0 5px 16px rgba(15,23,42,.12); }
.canvas-minimap span { position:absolute;width:8px;height:6px;border-radius:2px;background:#2563eb; }
.workflow-console-empty { padding:16px; color:#64748b; font-size:12px; }
.workflow-log-line { padding:3px 0; color:#cbd5e1; font:11px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace; }
.workflow-problem { display:grid;width:100%;grid-template-columns:auto minmax(0,1fr) auto;gap:8px;padding:6px 4px;color:#cbd5e1;text-align:left;font-size:11px; }
.workflow-problem:hover { background:#273244; }
.workflow-problem i { color:#f87171; }.workflow-problem small { color:#64748b; }
.bottom-reopen { display:inline-flex;min-height:36px;align-items:center;gap:7px;margin-top:8px;border-radius:9px;background:#273244;padding:0 12px;color:#cbd5e1;font-size:12px; }
.mode-switch { @apply flex rounded-xl bg-neutral-100 p-1; }
.mode-switch button { @apply inline-flex min-h-8 items-center gap-1.5 rounded-lg px-3 text-xs font-semibold text-neutral-500; }
.mode-switch button.active { @apply bg-white text-primary shadow-sm; }
.form-label { @apply block text-xs font-semibold text-neutral-500; }
.form-input { @apply mt-1.5 w-full rounded-xl border border-neutral-200 bg-white px-3 py-2.5 text-sm text-neutral-700 outline-none focus:border-primary focus:ring-2 focus:ring-primary/10 disabled:bg-neutral-50; }
.primary-button,.secondary-button,.publish-button,.danger-button { @apply inline-flex min-h-10 items-center gap-2 rounded-xl px-4 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50; }
.primary-button { @apply bg-primary text-white; }
.publish-button { @apply bg-neutral-900 text-white hover:bg-neutral-800; }
.secondary-button { @apply border border-neutral-200 bg-white text-neutral-600; }
.danger-button { @apply w-full justify-center bg-danger/10 text-danger; }
@media (max-width: 1100px) { .visual-layout { grid-template-columns: 220px minmax(0, 1fr); } .property-panel { position:fixed;right:0;top:68px;bottom:0;z-index:80;width:300px;box-shadow:-12px 0 30px rgba(15,23,42,.15); } }
@media (max-width: 767px) { .visual-layout { grid-template-columns:1fr;min-height:760px; } .capability-panel { max-height:220px;border-right:0;border-bottom:1px solid #e5e7eb; } .property-panel { top:0;width:min(88vw,320px); } }
</style>
