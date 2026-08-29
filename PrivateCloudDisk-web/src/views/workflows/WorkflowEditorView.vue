<template>
  <main class="workflow-editor-page pcd-ide-responsive pcd-workflow-ide" :class="{ 'workflow-editor-page--dark': isDark, 'workflow-editor-page--fullscreen': fullscreen }">
    <section v-if="loading" class="workflow-editor-loading" aria-busy="true" aria-live="polite"><span></span><span></span><span></span><p>正在准备 CloudFlow 开发环境…</p></section>
    <template v-else>
      <div v-if="!store.online" class="workflow-editor-offline" role="alert"><i class="fa fa-wifi"></i><span>网络已断开：已保留本地编辑快照；恢复网络后请重新校验再保存。</span><button type="button" @click="checkOnline">重新连接</button></div>
      <WorkflowIdeShell :ui="store.project.ui" :focus-mode="store.project.ui.focusMode" :fullscreen="fullscreen" :mobile-panel-open="mobilePanelOpen" @resize="store.setPanelSize" @close-mobile-panels="closeMobilePanels">
        <template #toolbar>
          <header class="workflow-toolbar" data-workflow-guide="toolbar">
            <div class="workflow-toolbar__identity"><button class="workflow-toolbar__icon mobile-only" type="button" title="打开节点库" aria-label="打开节点库" @click="togglePanel('leftCollapsed')"><i class="fa fa-bars"></i></button><div class="workflow-toolbar__name"><input v-model.trim="store.project.metadata.displayName" maxlength="80" aria-label="工作流名称" @change="store.updateProject(() => {})" /><span><i class="fa fa-circle" :class="workflowStatusTone"></i>{{ workflowStatusLabel }} · {{ saveLabel }}</span></div></div>
            <div class="workflow-toolbar__modes" role="tablist" aria-label="编辑模式"><button :class="{ active: store.mode === 'visual' }" type="button" role="tab" @click="switchMode('visual')"><i class="fa fa-sitemap"></i><span>画布</span></button><button :class="{ active: store.mode === 'source' }" type="button" role="tab" @click="switchMode('source')"><i class="fa fa-code"></i><span>DSL</span></button></div>
            <div class="workflow-toolbar__actions">
              <button class="workflow-toolbar__icon" type="button" title="命令面板 (Ctrl+Shift+P)" aria-label="命令面板" @click="commandOpen = true"><i class="fa fa-search"></i></button>
              <button class="workflow-toolbar__icon" type="button" title="撤销 (Ctrl+Z)" aria-label="撤销" :disabled="!store.canUndo || store.project.ui.readOnly" @click="store.undo"><i class="fa fa-undo"></i></button>
              <button class="workflow-toolbar__icon" type="button" title="重做 (Ctrl+Shift+Z)" aria-label="重做" :disabled="!store.canRedo || store.project.ui.readOnly" @click="store.redo"><i class="fa fa-repeat"></i></button>
              <button class="workflow-toolbar__button workflow-toolbar__button--secondary" type="button" :disabled="validating || !store.online" @click="validate()"><i class="fa" :class="validating ? 'fa-spinner fa-spin' : 'fa-check-circle' "></i><span>{{ validating ? '校验中' : '校验' }}</span></button>
              <button class="workflow-toolbar__button workflow-toolbar__button--secondary" type="button" :disabled="testRunning || !workflowId || !store.online" :title="workflowId ? '在隔离运行器中执行 dry-run' : '请先保存草稿'" @click="testRun"><i class="fa" :class="testRunning ? 'fa-spinner fa-spin' : 'fa-play-circle' "></i><span>{{ testRunning ? '测试中' : '测试' }}</span></button>
              <button class="workflow-toolbar__button workflow-toolbar__button--primary" type="button" :disabled="saving || !store.online" @click="save"><i class="fa" :class="saving ? 'fa-spinner fa-spin' : 'fa-save' "></i><span>{{ saving ? '保存中' : '保存' }}</span></button>
              <button class="workflow-toolbar__button workflow-toolbar__button--publish" type="button" :disabled="publishing || !store.online" @click="publish"><i class="fa" :class="publishing ? 'fa-spinner fa-spin' : 'fa-paper-plane' "></i><span>发布</span></button>
              <span class="workflow-toolbar__export"><button class="workflow-toolbar__icon" type="button" title="导出画布" aria-label="导出画布" @click="exportOpen = !exportOpen"><i class="fa fa-download"></i></button><span v-if="exportOpen" class="workflow-toolbar__export-menu"><button type="button" @click="exportCanvas('png')"><i class="fa fa-file-image-o"></i>导出 PNG</button><button type="button" @click="exportCanvas('svg')"><i class="fa fa-file-code-o"></i>导出 SVG</button><button type="button" @click="printCanvas"><i class="fa fa-file-pdf-o"></i>打印 / 保存为 PDF</button></span></span>
              <button class="workflow-toolbar__icon hide-mobile" type="button" title="新手指引" aria-label="新手指引" @click="showOnboarding = true"><i class="fa fa-map-signs"></i></button>
              <button class="workflow-toolbar__icon hide-mobile" type="button" :title="store.project.ui.focusMode ? '退出专注模式' : '专注模式'" :aria-label="store.project.ui.focusMode ? '退出专注模式' : '专注模式'" @click="toggleFocus"><i class="fa fa-crosshairs"></i></button>
              <button class="workflow-toolbar__icon" type="button" :title="fullscreen ? '退出全屏编辑 (F11)' : '全屏编辑 (F11)'" :aria-label="fullscreen ? '退出全屏编辑' : '全屏编辑'" @click="fullscreen = !fullscreen"><i :class="fullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i></button>
              <button class="workflow-toolbar__icon mobile-only" type="button" title="打开输出面板" aria-label="打开输出面板" @click="togglePanel('bottomCollapsed')"><i class="fa fa-terminal"></i></button>
              <button class="workflow-toolbar__icon mobile-only" type="button" title="打开属性面板" aria-label="打开属性面板" @click="togglePanel('rightCollapsed')"><i class="fa fa-sliders"></i></button>
              <!-- [IDE-RESP-2026-08 / 2.2、2.13] 窄屏收纳次级操作，避免工具栏溢出。 -->
              <button class="workflow-toolbar__icon mobile-only" type="button" title="更多工作流操作" aria-label="更多工作流操作" @click="mobileMoreOpen = true"><i class="fa fa-ellipsis-h"></i></button>
            </div>
          </header>
          <nav v-if="breadcrumbs.length > 1" class="workflow-breadcrumbs" aria-label="子画布面包屑"><button v-for="item in breadcrumbs" :key="item.id" type="button" @click="focusNode(item.id)"><i class="fa fa-angle-right" aria-hidden="true"></i>{{ item.label }}</button></nav>
        </template>
        <template #left><WorkflowNodeLibrary :capabilities="capabilities" @create="addFromLibrary" @help="showShortcutHelp = true" /></template>
        <template #center>
          <section v-if="store.mode === 'visual'" class="workflow-editor-canvas-host" data-workflow-guide="canvas">
            <WorkflowCanvas
              ref="canvasRef"
              :nodes="store.project.nodes"
              :edges="store.project.edges"
              :selected-node-ids="store.selectedNodeIds"
              :selected-edge-ids="store.selectedEdgeIds"
              :background="store.project.ui.background"
              :minimap-visible="store.project.ui.minimapVisible"
              :focus-mode="store.project.ui.focusMode"
              :read-only="store.project.ui.readOnly || !store.online"
              :show-perf="performancePanel"
              @connect="connect"
              @select-node="selectNode"
              @select-edge="selectEdge"
              @selection-change="store.setSelection($event.nodeIds, $event.edgeIds)"
              @clear-selection="clearSelection"
              @position-change="updateNodePosition"
              @drop="dropNode"
              @auto-layout="autoLayout"
              @paste="store.paste"
              @copy="store.copySelection"
              @delete-selection="confirmDelete"
            />
          </section>
          <section v-else class="workflow-source-mode"><PluginMonacoEditor v-model="sourceDsl" language="cloudflow" title="workflow.flow" height="100%" :theme="isDark ? 'vs-dark' : 'vs'" :capabilities="capabilities" :cloudflow-lsp="cloudflowLsp" :external-issues="store.issues as any" @validation-change="sourceLocalValid = $event" /><footer><span><i class="fa fa-info-circle"></i>源码是唯一发布事实；切回画布时将由 CloudFlow Runtime 编译并重建可视化投影。</span><button type="button" @click="formatSource">格式化预览</button></footer></section>
        </template>
        <template #right><section class="workflow-editor-inspector-host" data-workflow-guide="inspector"><WorkflowInspector :project="store.project" :selected-node="store.selectedNode" :selected-edge="store.selectedEdge" :capabilities="capabilities" @update-node="store.updateNode" @update-edge="store.updateEdge" @update-project="store.updateProject" @insert-reference="store.bottomTab = 'dsl'" /></section></template>
        <template #bottom>
          <BottomPanel v-model="store.bottomTab" :problem-count="store.issues.length" @collapse="togglePanel('bottomCollapsed')">
            <template #output><pre v-if="store.compilerOutput" class="ide-terminal-text workflow-terminal-output workflow-compiler-output">{{ store.compilerOutput }}</pre><div v-else class="workflow-terminal-empty"><i class="fa fa-terminal"></i><p>保存、校验与运行输出会显示在这里。</p></div></template>
            <template #problems><button v-for="issue in store.issues" :key="`${issue.code}-${issue.line}-${issue.column}`" class="workflow-problem" type="button" @click="openIssue(issue)"><i class="fa" :class="issue.severity === 'WARNING' ? 'fa-exclamation-triangle' : 'fa-times-circle'"></i><span>{{ issue.message }}</span><small>{{ issue.line ? `L${issue.line}:${issue.column || 1}` : issue.code }}</small></button><div v-if="!store.issues.length" class="workflow-terminal-empty"><i class="fa fa-check-circle"></i><p>暂无校验问题。</p></div></template>
            <template #execution><pre v-if="store.executionLogs.length" class="ide-terminal-text workflow-terminal-output">{{ store.executionLogs.join('\n') }}</pre><div v-else class="workflow-terminal-empty"><i class="fa fa-play-circle"></i><p>保存草稿后可运行隔离 dry-run。</p></div></template>
            <template #debug><div class="workflow-debug-grid"><span>节点 <b>{{ store.project.nodes.length }}</b></span><span>连线 <b>{{ store.project.edges.length }}</b></span><span>空间上下文 <b>由 X-Space-Id 自动注入</b></span><span>Runtime IR <b>{{ store.normalizedIr ? '已加载' : '待校验' }}</b></span><label><input v-model="performancePanel" type="checkbox" />开发者性能面板</label></div></template>
            <template #dsl><pre class="ide-terminal-text workflow-terminal-output">{{ store.dsl }}</pre></template>
          </BottomPanel>
        </template>
      </WorkflowIdeShell>
    </template>

    <div v-if="commandOpen" class="workflow-modal-mask" role="presentation" @click.self="commandOpen = false"><section class="workflow-command-palette" role="dialog" aria-modal="true" aria-label="命令面板"><header><i class="fa fa-terminal"></i><input v-model.trim="commandQuery" autofocus placeholder="输入命令，例如“自动布局”" @keydown.esc="commandOpen = false" /></header><button v-for="command in filteredCommands" :key="command.id" type="button" @click="runCommand(command.id)"><span><i :class="command.icon"></i>{{ command.label }}</span><kbd>{{ command.shortcut }}</kbd></button><p v-if="!filteredCommands.length">没有匹配的命令</p></section></div>
    <div v-if="showShortcutHelp" class="workflow-modal-mask" role="presentation" @click.self="showShortcutHelp = false"><section class="workflow-shortcuts" role="dialog" aria-modal="true" aria-label="快捷键说明"><header><h2>工作流 IDE 快捷键</h2><button type="button" aria-label="关闭" @click="showShortcutHelp = false"><i class="fa fa-times"></i></button></header><dl><template v-for="item in shortcutItems" :key="item.key"><dt><kbd>{{ item.key }}</kbd></dt><dd>{{ item.description }}</dd></template></dl></section></div>
    <WorkflowOnboarding v-model:open="showOnboarding" @complete="completeOnboarding" />

    <!-- [IDE-RESP-2026-08 / 2.13、9.7] 手机端更多菜单完整保留发布、导出和引导入口。 -->
    <Teleport to="body">
      <div v-if="mobileMoreOpen" class="ide-mobile-sheet-mask" role="presentation" @click.self="mobileMoreOpen = false">
        <section class="ide-mobile-sheet" role="dialog" aria-modal="true" aria-label="更多工作流操作">
          <div class="ide-mobile-sheet__handle" aria-hidden="true"></div>
          <header><strong>更多工作流操作</strong><button type="button" aria-label="关闭更多操作" @click="mobileMoreOpen = false"><i class="fa fa-times"></i></button></header>
          <div class="ide-mobile-sheet__actions">
            <button type="button" :disabled="validating || !store.online" @click="validate(); mobileMoreOpen = false"><i class="fa fa-check-circle"></i>校验 CloudFlow DSL</button>
            <button type="button" :disabled="testRunning || !workflowId || !store.online" @click="testRun(); mobileMoreOpen = false"><i class="fa fa-play-circle"></i>隔离测试运行</button>
            <button type="button" :disabled="publishing || !store.online" @click="publish(); mobileMoreOpen = false"><i class="fa fa-paper-plane"></i>发布工作流</button>
            <button type="button" @click="exportCanvas('png'); mobileMoreOpen = false"><i class="fa fa-file-image-o"></i>导出 PNG</button>
            <button type="button" @click="exportCanvas('svg'); mobileMoreOpen = false"><i class="fa fa-file-code-o"></i>导出 SVG</button>
            <button type="button" @click="printCanvas(); mobileMoreOpen = false"><i class="fa fa-file-pdf-o"></i>打印 / 保存 PDF</button>
            <button type="button" @click="showOnboarding = true; mobileMoreOpen = false"><i class="fa fa-map-signs"></i>查看新手引导</button>
          </div>
        </section>
      </div>
    </Teleport>
  </main>
</template>

<script setup lang="ts">
// ============================================================
// WorkflowEditorView.vue — CloudFlow 可视化工作流开发环境
// ============================================================
// 原页面已从 YAML-like 文本迁移到 CloudFlow 块结构；本次
// 继续把“单一 action 画布 + 正则回填”升级为
// Runtime IR 驱动的可逆 IDE。旧接口、If-Match 乐观锁、X-Space-Id 请求头和
// PluginMonacoEditor 的外部诊断路径均保持不变。
// ============================================================
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import BottomPanel from '@/components/plugins/ide/BottomPanel.vue'
import PluginMonacoEditor from '@/components/plugins/PluginMonacoEditor.vue'
import WorkflowCanvas from '@/components/workflows/WorkflowCanvas.vue'
import WorkflowIdeShell from '@/components/workflows/WorkflowIdeShell.vue'
import WorkflowInspector from '@/components/workflows/WorkflowInspector.vue'
import WorkflowNodeLibrary, { type WorkflowNodeLibraryItem } from '@/components/workflows/WorkflowNodeLibrary.vue'
import WorkflowOnboarding from '@/components/workflows/WorkflowOnboarding.vue'
import {
  createWorkflowApi,
  getLatestWorkflowVersionApi,
  getWorkflowApi,
  listCapabilitiesApi,
  publishWorkflowApi,
  runWorkflowTestApi,
  updateWorkflowApi,
  validateWorkflowApi,
  type CapabilityInfo,
  type WorkflowValidationIssue,
} from '@/api/modules/workflows'
import { useSettingsStore } from '@/stores/settingsStore'
import { useToastStore } from '@/stores/toastStore'
import { useWorkflowIdeStore } from '@/stores/workflowIdeStore'
import { useAuthStore } from '@/stores/authStore'
import { useSpaceStore } from '@/stores/spaceStore'
import { nodeLabel, serializeProjectToCloudFlow } from '@/utils/cloudflowVisualDsl'
import type { CloudFlowCompileIssue, WorkflowVisualEdge, WorkflowVisualNodeType, WorkflowVisualProject } from '@/types/cloudflowVisual'

const route = useRoute(); const router = useRouter(); const toast = useToastStore(); const settings = useSettingsStore(); const store = useWorkflowIdeStore(); const auth = useAuthStore(); const spaceStore = useSpaceStore()
const workflowId = computed(() => route.params.workflowId as string | undefined)
const loading = ref(true); const saving = ref(false); const publishing = ref(false); const validating = ref(false); const testRunning = ref(false); const fullscreen = ref(false); const sourceDsl = ref(''); const sourceLocalValid = ref(true); const capabilities = ref<CapabilityInfo[]>([]); const rowVersion = ref(0); const currentVersion = ref(1); const workflowStatus = ref('DRAFT'); const commandOpen = ref(false); const commandQuery = ref(''); const showShortcutHelp = ref(false); const showOnboarding = ref(false); const exportOpen = ref(false); const performancePanel = ref(false); const mobileMoreOpen = ref(false); const compactViewport = ref(false); const canvasRef = ref<InstanceType<typeof WorkflowCanvas> | null>(null)
let autosaveTimer: number | undefined
let sourceSyncTimer: number | undefined

const isDark = computed(() => settings.appearance.theme === 'dark')
// [CLOUDFLOW-LS-WEB-003] 仅当部署显式提供 WSS 地址时启用远端 LS；否则保持
// syntax-highlight 静态规则和 Runtime 校验，避免把登录 Token 暴露给任意 URL。
const cloudflowLsp = computed(() => {
  const endpoint = import.meta.env.VITE_CLOUDFLOW_LSP_URL || ''
  return endpoint ? { endpoint, accessToken: auth.token, spaceId: spaceStore.currentSpaceId || undefined } : null
})
const mobilePanelOpen = computed(() => compactViewport.value && (!store.project.ui.leftCollapsed || !store.project.ui.rightCollapsed || !store.project.ui.bottomCollapsed))
const workflowStatusLabel = computed(() => workflowStatus.value === 'PUBLISHED' ? '已发布' : store.dirty ? '草稿未保存' : '草稿')
const workflowStatusTone = computed(() => workflowStatus.value === 'PUBLISHED' ? 'is-published' : store.dirty ? 'is-dirty' : 'is-draft')
const saveLabel = computed(() => store.saveState === 'saving' ? '正在保存' : store.dirty ? '有未保存更改' : store.saveState === 'saved' ? '已保存' : '就绪')
const breadcrumbs = computed(() => [{ id: '__trigger__', label: '工作流' }, ...(store.selectedNode?.data.parentId ? [{ id: store.selectedNode.data.parentId, label: store.project.nodes.find((node) => node.id === store.selectedNode?.data.parentId)?.data.label || '控制流' }] : []), ...(store.selectedNode ? [{ id: store.selectedNode.id, label: store.selectedNode.data.label }] : [])])
const commands = computed(() => [
  { id: 'save', label: '保存工作流草稿', icon: 'fa fa-save', shortcut: 'Ctrl+S' }, { id: 'validate', label: '校验 CloudFlow DSL', icon: 'fa fa-check-circle', shortcut: '—' }, { id: 'layout', label: '自动布局', icon: 'fa fa-magic', shortcut: '—' }, { id: 'focus', label: store.project.ui.focusMode ? '退出专注模式' : '进入专注模式', icon: 'fa fa-crosshairs', shortcut: '—' }, { id: 'fit', label: '适合画布', icon: 'fa fa-compress', shortcut: '—' }, { id: 'source', label: '切换到 DSL 源码', icon: 'fa fa-code', shortcut: '—' }, { id: 'visual', label: '切换到可视化画布', icon: 'fa fa-sitemap', shortcut: '—' }, { id: 'shortcuts', label: '打开快捷键说明', icon: 'fa fa-keyboard-o', shortcut: 'F1 / ?' },
])
const filteredCommands = computed(() => { const q = commandQuery.value.toLocaleLowerCase(); return commands.value.filter((command) => !q || `${command.label} ${command.id}`.toLocaleLowerCase().includes(q)) })
const shortcutItems = [{ key: 'Ctrl/Cmd + S', description: '保存通过 Runtime 校验的草稿' }, { key: 'Ctrl/Cmd + Z', description: '撤销画布操作（最多 50 步）' }, { key: 'Ctrl/Cmd + Shift + Z', description: '重做画布操作' }, { key: 'Delete', description: '删除当前选中节点或连线' }, { key: 'Ctrl/Cmd + C / V', description: '复制、粘贴节点' }, { key: 'Ctrl/Cmd + Shift + P', description: '打开命令面板' }, { key: 'Space + 拖拽', description: '平移无限画布' }, { key: 'Ctrl/Cmd + 滚轮', description: '缩放画布' }]

// 校验、保存和发布共用同一个源码归一化入口，禁止把编辑器
// 快照对象误当作 DSL 字符串发送到后端；无法归一化时回退到当前画布的确定性序列化结果。
function currentDslSource(): string {
  const candidate: unknown = store.mode === 'source' ? sourceDsl.value : store.dsl
  if (typeof candidate === 'string') return candidate
  if (candidate && typeof candidate === 'object') {
    const payload = candidate as Record<string, unknown>
    for (const key of ['source', 'dsl', 'text']) {
      if (typeof payload[key] === 'string') return payload[key] as string
    }
  }
  return serializeProjectToCloudFlow(store.project)
}

/** 源码回投必须使用 Rust Runtime 产生的 IR，而不是在浏览器用正则解析。 */
async function syncGraphFromIr(ir: Record<string, any>, markDirty = false) { store.applyRuntimeIr(ir, store.project, markDirty); await nextTick(); canvasRef.value?.fitView() }
async function validate(source = currentDslSource()) {
  validating.value = true
  try {
    const response = await validateWorkflowApi(source, store.project as unknown as Record<string, unknown>)
    const result = response.data
    const issues = (result.issues || []) as CloudFlowCompileIssue[]
    const cliOutput = issues.map((issue) => issue.cliOutput || `${issue.severity || 'ERROR'} ${issue.code}\n${issue.path || `line ${issue.line || 1}:${issue.column || 1}`}\n${issue.message}${issue.suggestions?.length ? `\n建议：${issue.suggestions.join('；')}` : ''}`).join('\n\n') || 'CloudFlow Runtime 校验通过。'
    store.setIssueState(issues, cliOutput)
    if (result.valid) { store.normalizedIr = result.normalized || null; store.bottomTab = 'output'; toast.showToast('CloudFlow 语法、能力引用与 DAG 校验通过', 'success') } else { store.bottomTab = 'problems'; toast.showToast('请修复 CloudFlow 校验问题', 'warning') }
    return result.valid
  } catch (error: any) {
    store.setIssueState([{ code: 'CF-RUNTIME-UNAVAILABLE', message: error?.message || 'CloudFlow Runtime 暂不可用，请恢复网络后重试。', severity: 'ERROR' }], `ERROR CF-RUNTIME-UNAVAILABLE\n\n${error?.message || 'CloudFlow Runtime 暂不可用'}\n\n请检查服务连接后重新校验。`)
    store.bottomTab = 'output'; toast.showToast(error?.message || '校验服务暂时不可用', 'error'); return false
  } finally { validating.value = false }
}
async function switchMode(next: 'visual' | 'source') {
  if (next === store.mode) return
  if (next === 'source') { sourceDsl.value = currentDslSource(); store.mode = 'source'; return }
  if (!sourceLocalValid.value || !(await validate(sourceDsl.value))) return
  if (store.normalizedIr) await syncGraphFromIr(store.normalizedIr as Record<string, any>, true)
  store.mode = 'visual'
}
function addFromLibrary(item: WorkflowNodeLibraryItem, position?: { x: number; y: number }) {
  if (store.project.ui.readOnly || !store.online) return
  if (item.nodeType === 'trigger') {
    store.updateProject((draft) => { draft.trigger = { type: 'manual' } })
    store.selectNode('__trigger__')
    if (compactViewport.value) closeMobilePanels()
    return
  }
  const node = store.addNode(item.nodeType, position)
  if (item.capability) {
    const [provider, ...rest] = item.capability.capabilityKey.split(':'); const identifier = rest.join(':')
    if (provider === 'plugin') { const [pluginId, func] = identifier.split(':'); store.updateNode(node.id, { label: item.capability.displayName, action: { provider: 'plugin', pluginId, function: func || 'run', version: item.capability.sourceVersion || '1', arguments: {} } }) }
    else { const [service, method] = identifier.split('.'); store.updateNode(node.id, { label: item.capability.displayName, action: { provider: provider === 'api' ? 'api' : 'builtin', service, method, arguments: {} } }) }
  }
  // [IDE-RESP-2026-08 / 3.8、8.11] 触摸设备通过点击直接把节点加入画布中心；
  // 加入后收起节点库并打开属性抽屉，避免依赖桌面 DragEvent。
  if (compactViewport.value) {
    toast.showToast(`已添加“${item.label}”，可在属性面板继续配置`, 'success')
    selectNode(node.id)
  }
}
function dropNode(payload: { raw: string; position: { x: number; y: number } }) { try { const item = JSON.parse(payload.raw) as { nodeType: WorkflowVisualNodeType; capabilityKey?: string }; const capability = capabilities.value.find((entry) => entry.capabilityKey === item.capabilityKey); addFromLibrary({ key: item.capabilityKey || item.nodeType, nodeType: item.nodeType, label: capability?.displayName || nodeLabel(item.nodeType), description: capability?.description || '', icon: '', color: '', capability }, payload.position) } catch { toast.showToast('拖入的节点数据无效，请从节点库重新拖拽', 'warning') } }
function connect(edge: WorkflowVisualEdge, control?: { parentId: string; branch: string }) { if (control) store.assignNodeToControl(edge.target, control.parentId, control.branch); const result = store.addEdge(edge); if (!result.accepted) toast.showToast(result.reason || '无法创建连线', 'warning') }
function selectNode(id: string) {
  store.selectNode(id)
  // [IDE-RESP-2026-08 / 4.1] 触屏选择节点后自动展示属性抽屉，减少二次点击。
  if (compactViewport.value) {
    store.updateProject((draft) => { draft.ui.leftCollapsed = true; draft.ui.bottomCollapsed = true; draft.ui.rightCollapsed = false })
  }
}
function selectEdge(id: string) { store.selectEdge(id) }
function clearSelection() { store.setSelection([], []) }
/** CF-IDE-2026-08 / 3.1、13.8：删除节点/连线会改变 DSL 拓扑，统一二次确认后再写入历史。 */
function confirmDelete() {
  const count = store.selectedNodeIds.length + store.selectedEdgeIds.length
  if (!count || confirm(`确定删除选中的 ${count} 个节点或连线吗？此操作可通过撤销恢复。`)) store.removeSelection()
}
function updateNodePosition(id: string, position: { x: number; y: number }) { const node = store.project.nodes.find((entry) => entry.id === id); if (!node || node.data.locked) return; store.updateProject((draft) => { const target = draft.nodes.find((entry) => entry.id === id); if (target) target.position = position }) }
function autoLayout() {
  store.updateProject((draft) => {
    const nodes = draft.nodes.filter((node) => !['trigger', 'note', 'group'].includes(node.type)); const layers = new Map<string, number>(); layers.set('__trigger__', 0)
    const dependencies = draft.edges.filter((edge) => edge.data.kind === 'dependency')
    // [AUDIT FIX 3.4]：[CLOUDFLOW-IDE-003] 自动布局仅根据普通依赖边计算 DAG 层级；
    // 补齐 set/Math.max/map 的闭合调用，避免构建阶段将布局能力整体阻断。
    nodes.forEach((node) => { const incoming = dependencies.filter((edge) => edge.target === node.id); layers.set(node.id, Math.max(1, ...incoming.map((edge) => (layers.get(edge.source) || 0) + 1))) })
    const byLayer = new Map<number, typeof nodes>(); nodes.forEach((node) => { const layer = layers.get(node.id) || 1; byLayer.set(layer, [...(byLayer.get(layer) || []), node]) })
    byLayer.forEach((items, layer) => items.forEach((node, index) => { node.position = { x: 120 + layer * 295, y: 100 + index * 164 } }))
    nodes.filter((node) => node.data.parentId).forEach((node, index) => { const parent = draft.nodes.find((entry) => entry.id === node.data.parentId); if (parent) node.position = { x: parent.position.x + 310, y: parent.position.y + 68 + index * 118 } })
  }); void nextTick(() => canvasRef.value?.fitView())
}
function togglePanel(key: 'leftCollapsed' | 'rightCollapsed' | 'bottomCollapsed') {
  store.updateProject((draft) => {
    const next = !draft.ui[key]
    if (compactViewport.value && next) {
      draft.ui.leftCollapsed = key !== 'leftCollapsed'
      draft.ui.rightCollapsed = key !== 'rightCollapsed'
      draft.ui.bottomCollapsed = key !== 'bottomCollapsed'
    }
    draft.ui[key] = next
  })
}
/** [IDE-RESP-2026-08 / 1.5] 点击遮罩仅收起移动端抽屉，不影响桌面布局偏好。 */
function closeMobilePanels() { store.updateProject((draft) => { draft.ui.leftCollapsed = true; draft.ui.rightCollapsed = true; draft.ui.bottomCollapsed = true }) }
function syncResponsiveLayout() {
  const wasCompact = compactViewport.value
  compactViewport.value = window.innerWidth < 1024
  if (compactViewport.value && !wasCompact) closeMobilePanels()
  if (!compactViewport.value && wasCompact) {
    store.updateProject((draft) => { draft.ui.leftCollapsed = false; draft.ui.rightCollapsed = false; draft.ui.bottomCollapsed = false })
  }
}
function toggleFocus() { store.updateProject((draft) => { draft.ui.focusMode = !draft.ui.focusMode }) }
function focusNode(id: string) { if (id !== '__trigger__') { selectNode(id); canvasRef.value?.focusNode(id) } }
function exportCanvas(format: 'png' | 'svg') { canvasRef.value?.exportSnapshot(format); exportOpen.value = false }
function printCanvas() { canvasRef.value?.printSnapshot(); exportOpen.value = false }
function completeOnboarding() { try { localStorage.setItem('pcd.workflow-ide.onboarding.v1', 'complete') } catch { /* 存储不可用时不影响引导关闭。 */ } }
function openIssue(issue: CloudFlowCompileIssue) { sourceDsl.value = currentDslSource(); store.mode = 'source'; store.bottomTab = 'problems'; store.compilerOutput = issue.cliOutput || issue.message }
function formatSource() { sourceDsl.value = sourceDsl.value.split('\n').map((line) => line.trim() ? `    ${line.trim()}` : '').join('\n').replace(/^    workflow/, 'workflow') }
function runCommand(id: string) { commandOpen.value = false; if (id === 'save') void save(); else if (id === 'validate') void validate(); else if (id === 'layout') autoLayout(); else if (id === 'focus') toggleFocus(); else if (id === 'fit') canvasRef.value?.fitView(); else if (id === 'source' || id === 'visual') void switchMode(id); else if (id === 'shortcuts') showShortcutHelp.value = true }
async function save() {
  if (!store.online) return
  if (!store.project.metadata.displayName || !store.project.metadata.name) { toast.showToast('请填写工作流名称和标识', 'warning'); return false }
  const source = currentDslSource()
  if (!(await validate(source))) return false
  store.markSaving(); saving.value = true
  try {
    if (workflowId.value) { const result = await updateWorkflowApi(workflowId.value, rowVersion.value, { name: store.project.metadata.displayName, description: store.project.metadata.description, dsl: source, graph: store.project as unknown as Record<string, unknown> }); rowVersion.value = result.data.rowVersion; workflowStatus.value = result.data.status; currentVersion.value = (await getLatestWorkflowVersionApi(workflowId.value)).data.version }
    else { const result = await createWorkflowApi({ name: store.project.metadata.displayName, slug: store.project.metadata.name.replace(/_/g, '-').toLocaleLowerCase(), description: store.project.metadata.description, dsl: source, graph: store.project as unknown as Record<string, unknown> }); rowVersion.value = result.data.rowVersion; workflowStatus.value = result.data.status; currentVersion.value = 1; await router.replace(`/app/workflows/${result.data.workflowId}/edit`) }
    store.markSaved(); toast.showToast('工作流草稿已保存', 'success'); return true
  } catch (error: any) { store.markSaveError(); toast.showToast(error?.message || '保存失败，请稍后重试', 'error'); return false } finally { saving.value = false }
}
async function publish() { if (publishing.value) return; publishing.value = true; try { if (!(await save())) return; if (!workflowId.value) throw new Error('工作流标识尚未生成'); await publishWorkflowApi(workflowId.value, currentVersion.value); workflowStatus.value = 'PUBLISHED'; toast.showToast(`工作流 v${currentVersion.value} 已发布为不可变版本`, 'success') } catch (error: any) { toast.showToast(error?.message || '发布失败', 'error') } finally { publishing.value = false } }
async function testRun() { if (!workflowId.value || !(await validate())) return; testRunning.value = true; store.bottomTab = 'execution'; store.executionLogs = ['已提交工作流 dry-run，等待隔离运行器响应…']; try { const result = (await runWorkflowTestApi(workflowId.value, {}, currentVersion.value)).data; store.executionLogs.push(`execution_id=${result.executionId}`, `status=${result.status}`); toast.showToast('测试运行已进入异步队列', 'success') } catch (error: any) { store.executionLogs.push(`测试运行失败：${error?.message || '服务暂不可用'}`); toast.showToast('测试接口暂不可用，未触发正式工作流', 'warning') } finally { testRunning.value = false } }
function checkOnline() { store.setOnline(navigator.onLine); if (navigator.onLine) toast.showToast('网络已恢复，请重新校验工作流', 'success') }
function onNetworkChange() { store.setOnline(navigator.onLine); if (navigator.onLine) toast.showToast('网络已恢复，建议重新校验后保存', 'success') }
async function load() {
  loading.value = true
  try {
    capabilities.value = (await listCapabilitiesApi()).data
    if (workflowId.value) {
      const [workflow, version] = await Promise.all([getWorkflowApi(workflowId.value), getLatestWorkflowVersionApi(workflowId.value)])
      rowVersion.value = workflow.data.rowVersion; workflowStatus.value = workflow.data.status; currentVersion.value = version.data.version; sourceDsl.value = version.data.dslText
      let graph: Partial<WorkflowVisualProject> | undefined
      try { graph = version.data.graphJson ? JSON.parse(version.data.graphJson) : undefined } catch { /* 历史 graphJson 无法解析时完全依赖 Runtime IR。 */ }
      store.initialize(workflowId.value, graph)
      const valid = await validate(version.data.dslText)
      if (valid && store.normalizedIr) await syncGraphFromIr(store.normalizedIr as Record<string, any>, false)
    } else { store.initialize(); sourceDsl.value = store.dsl; await validate(sourceDsl.value) }
  } catch (error: any) { toast.showToast(error?.message || '工作流编辑器数据加载失败', 'error') } finally {
    loading.value = false
    try { showOnboarding.value = !localStorage.getItem('pcd.workflow-ide.onboarding.v1') } catch { showOnboarding.value = false }
  }
}
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { commandOpen.value = false; showShortcutHelp.value = false; mobileMoreOpen.value = false; exportOpen.value = false; return }
  const modifier = event.ctrlKey || event.metaKey
  if (event.key === 'F1' || (!modifier && event.key === '?')) { event.preventDefault(); showShortcutHelp.value = true; return }
  if (event.key === 'F11') { event.preventDefault(); fullscreen.value = !fullscreen.value; return }
  if (!modifier) { if (event.key === 'Delete' && !store.project.ui.readOnly) { event.preventDefault(); confirmDelete() }; return }
  const key = event.key.toLocaleLowerCase()
  if (key === 's') { event.preventDefault(); void save() } else if (key === 'z') { event.preventDefault(); event.shiftKey ? store.redo() : store.undo() } else if (key === 'y') { event.preventDefault(); store.redo() } else if (key === 'c') { event.preventDefault(); store.copySelection() } else if (key === 'v') { event.preventDefault(); store.paste() } else if (key === 'a') { event.preventDefault(); store.setSelection(store.project.nodes.filter((node) => node.id !== '__trigger__').map((node) => node.id), store.project.edges.map((edge) => edge.id)) } else if (key === 'p' && event.shiftKey) { event.preventDefault(); commandOpen.value = true }
}
watch(() => store.dsl, (value) => { if (store.mode === 'visual') { if (sourceSyncTimer) clearTimeout(sourceSyncTimer); sourceSyncTimer = window.setTimeout(() => { sourceDsl.value = value }, 300) } })
onMounted(() => { syncResponsiveLayout(); void load(); window.addEventListener('keydown', onKeydown); window.addEventListener('online', onNetworkChange); window.addEventListener('offline', onNetworkChange); window.addEventListener('resize', syncResponsiveLayout, { passive: true }); autosaveTimer = window.setInterval(() => { if (workflowId.value && store.dirty && store.online && !saving.value && !publishing.value) void save() }, 30000) })
onBeforeUnmount(() => { window.removeEventListener('keydown', onKeydown); window.removeEventListener('online', onNetworkChange); window.removeEventListener('offline', onNetworkChange); window.removeEventListener('resize', syncResponsiveLayout); if (autosaveTimer) clearInterval(autosaveTimer); if (sourceSyncTimer) clearTimeout(sourceSyncTimer); store.reset() })
onBeforeRouteLeave(() => { if (store.dirty && !confirm('当前工作流有未保存更改，确定离开吗？')) return false })
</script>

<style scoped>
.workflow-editor-page { --workflow-primary:#2563eb;--workflow-border:#dbe4f0;--workflow-panel:#fff;--workflow-canvas:#f8fafc;--workflow-node:#fff;--workflow-input:#fff;--workflow-hover:#f1f5f9;--workflow-text:#1e293b;--workflow-muted:#64748b;display:block;width:100%;height:100%;min-height:calc(100dvh - 84px);padding:12px;background:#f1f5f9;color:var(--workflow-text);box-sizing:border-box; }.workflow-editor-page--dark { --workflow-border:#334155;--workflow-panel:#172033;--workflow-canvas:#0f172a;--workflow-node:#1e293b;--workflow-input:#0f172a;--workflow-hover:#273244;--workflow-text:#e2e8f0;--workflow-muted:#94a3b8;background:#0b1120; }.workflow-editor-page--fullscreen { position:fixed;inset:0;z-index:130;padding:0;overflow:hidden; }.workflow-editor-loading { display:grid;min-height:calc(100dvh - 90px);place-content:center;grid-template-columns:repeat(3,12px);gap:8px;color:#64748b;text-align:center; }.workflow-editor-loading span { width:12px;height:12px;border-radius:999px;background:#2563eb;animation:workflow-loading 1s ease-in-out infinite; }.workflow-editor-loading span:nth-child(2){animation-delay:.15s}.workflow-editor-loading span:nth-child(3){animation-delay:.3s}.workflow-editor-loading p { grid-column:1/-1;margin:8px 0 0;font-size:13px; }.workflow-editor-offline { display:flex;min-height:40px;align-items:center;gap:8px;margin:0 0 8px;padding:0 12px;border:1px solid #f59e0b;border-radius:10px;background:#fffbeb;color:#92400e;font-size:12px; }.workflow-editor-offline button { margin-left:auto;color:#1d4ed8;font-weight:700; }.workflow-toolbar { display:flex;min-height:59px;align-items:center;justify-content:space-between;gap:10px;padding:0 12px; }.workflow-toolbar__identity,.workflow-toolbar__actions,.workflow-toolbar__modes { display:flex;min-width:0;align-items:center;gap:5px; }.workflow-toolbar__identity { flex:1 1 280px; }.workflow-toolbar__name { min-width:0; }.workflow-toolbar__name input { width:min(260px,36vw);border:0;outline:0;background:transparent;color:var(--workflow-text);font-size:14px;font-weight:800; }.workflow-toolbar__name span { display:block;overflow:hidden;margin-top:2px;color:var(--workflow-muted);font-size:9px;text-overflow:ellipsis;white-space:nowrap; }.workflow-toolbar__name .is-published { color:#16a34a; }.workflow-toolbar__name .is-dirty { color:#f59e0b; }.workflow-toolbar__name .is-draft { color:#64748b; }.workflow-toolbar__modes { padding:3px;border-radius:9px;background:var(--workflow-hover); }.workflow-toolbar__modes button { display:inline-flex;min-height:30px;align-items:center;gap:5px;padding:0 9px;border-radius:7px;color:var(--workflow-muted);font-size:11px;font-weight:700; }.workflow-toolbar__modes button.active { background:var(--workflow-panel);color:var(--workflow-primary);box-shadow:0 1px 3px rgb(15 23 42 / .12); }.workflow-toolbar__actions { justify-content:flex-end; }.workflow-toolbar__icon { display:inline-grid;width:34px;height:34px;place-items:center;border-radius:8px;color:var(--workflow-muted); }.workflow-toolbar__icon:hover:not(:disabled),.workflow-toolbar__icon:focus-visible:not(:disabled) { background:var(--workflow-hover);color:var(--workflow-primary);outline:none; }.workflow-toolbar__icon:disabled { opacity:.42; }.workflow-toolbar__button { display:inline-flex;min-height:34px;align-items:center;gap:6px;padding:0 10px;border-radius:8px;font-size:11px;font-weight:800; }.workflow-toolbar__button:disabled { cursor:not-allowed;opacity:.5; }.workflow-toolbar__button--secondary { border:1px solid var(--workflow-border);color:var(--workflow-text); }.workflow-toolbar__button--primary { background:var(--workflow-primary);color:#fff; }.workflow-toolbar__button--publish { background:#0f172a;color:#fff; }.workflow-editor-page--dark .workflow-toolbar__button--publish { background:#e2e8f0;color:#0f172a; }.mobile-only { display:none; }.workflow-breadcrumbs { position:absolute;z-index:9;top:66px;left:calc(var(--workflow-left-width,286px) + 16px);display:flex;gap:5px;max-width:calc(100% - 720px);padding:5px 8px;border:1px solid var(--workflow-border);border-radius:8px;background:color-mix(in srgb,var(--workflow-panel) 92%,transparent);box-shadow:0 4px 12px rgb(15 23 42 / .08); }.workflow-breadcrumbs button { overflow:hidden;color:var(--workflow-muted);font-size:10px;text-overflow:ellipsis;white-space:nowrap; }.workflow-breadcrumbs button:last-child { color:var(--workflow-primary);font-weight:800; }.workflow-source-mode { display:grid;height:100%;grid-template-rows:minmax(0,1fr) auto;padding:12px;background:var(--workflow-canvas); }.workflow-source-mode footer { display:flex;align-items:center;justify-content:space-between;gap:8px;padding:9px 5px 0;color:var(--workflow-muted);font-size:10px; }.workflow-source-mode footer span { display:flex;gap:5px; }.workflow-source-mode footer button { color:var(--workflow-primary);font-weight:700; }.workflow-terminal-output { margin:0;color:#dbeafe;font:11px/1.55 ui-monospace,SFMono-Regular,Menlo,monospace;white-space:pre-wrap;overflow-wrap:anywhere;word-break:break-word; }.workflow-terminal-empty { display:grid;min-height:96px;place-content:center;gap:7px;color:#64748b;text-align:center;font-size:11px; }.workflow-terminal-empty i { font-size:18px; }.workflow-terminal-empty p { margin:0; }.workflow-problem { display:grid;width:100%;grid-template-columns:auto minmax(0,1fr) auto;gap:8px;padding:7px;border-radius:7px;color:#cbd5e1;text-align:left;font-size:11px; }.workflow-problem:hover,.workflow-problem:focus-visible { background:#273244;outline:none; }.workflow-problem i { color:#f87171; }.workflow-problem small { color:#94a3b8;font:9px ui-monospace,monospace; }.workflow-debug-grid { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;color:#cbd5e1;font-size:11px; }.workflow-debug-grid span,.workflow-debug-grid label { padding:8px;border:1px solid #334155;border-radius:7px;background:#172033; }.workflow-debug-grid b { color:#93c5fd;font-weight:700; }.workflow-modal-mask { position:fixed;inset:0;z-index:160;display:grid;place-items:start center;padding-top:min(18dvh,140px);background:rgb(15 23 42 / .42); }.workflow-command-palette,.workflow-shortcuts { width:min(94vw,560px);overflow:hidden;border:1px solid var(--workflow-border);border-radius:14px;background:var(--workflow-panel);box-shadow:0 24px 70px rgb(15 23 42 / .35); }.workflow-command-palette header { display:flex;align-items:center;gap:9px;padding:11px;border-bottom:1px solid var(--workflow-border);color:var(--workflow-primary); }.workflow-command-palette input { width:100%;border:0;outline:0;background:transparent;color:var(--workflow-text);font-size:13px; }.workflow-command-palette button { display:flex;width:100%;min-height:42px;align-items:center;justify-content:space-between;padding:0 13px;color:var(--workflow-text);font-size:12px;text-align:left; }.workflow-command-palette button:hover { background:var(--workflow-hover); }.workflow-command-palette button span { display:flex;align-items:center;gap:9px; }.workflow-command-palette kbd,.workflow-shortcuts kbd { border:1px solid var(--workflow-border);border-radius:4px;padding:2px 5px;color:var(--workflow-muted);font:9px ui-monospace,monospace; }.workflow-command-palette>p { padding:18px;color:var(--workflow-muted);font-size:12px;text-align:center; }.workflow-shortcuts header { display:flex;align-items:center;justify-content:space-between;padding:14px 16px;border-bottom:1px solid var(--workflow-border); }.workflow-shortcuts h2 { margin:0;font-size:15px; }.workflow-shortcuts header button { width:30px;height:30px;border-radius:7px;color:var(--workflow-muted); }.workflow-shortcuts dl { display:grid;grid-template-columns:minmax(135px,.8fr) 1.2fr;gap:10px;margin:0;padding:16px;color:var(--workflow-muted);font-size:11px; }.workflow-shortcuts dt { text-align:right; }.workflow-shortcuts dd { margin:0;line-height:1.5; }
@keyframes workflow-loading { 0%,100%{transform:translateY(0);opacity:.45}50%{transform:translateY(-8px);opacity:1} }
@media (max-width:1279px) { .workflow-breadcrumbs { left:calc(var(--workflow-left-width,286px) + 12px);max-width:calc(100% - 360px); }.hide-mobile { display:none; } }
@media (max-width:767px) { .workflow-editor-page { padding:4px; }.mobile-only { display:inline-grid; }.workflow-toolbar { padding:0 7px; }.workflow-toolbar__name input { width:110px; }.workflow-toolbar__modes span,.workflow-toolbar__button span { display:none; }.workflow-toolbar__button { width:34px;justify-content:center;padding:0; }.workflow-toolbar__modes button { min-width:34px;justify-content:center;padding:0 7px; }.workflow-breadcrumbs { display:none; }.workflow-source-mode { padding:4px; }.workflow-source-mode footer { align-items:flex-start;flex-direction:column; }.workflow-debug-grid { grid-template-columns:1fr; }.workflow-modal-mask { padding-top:9dvh; }.workflow-shortcuts dl { grid-template-columns:1fr; }.workflow-shortcuts dt { text-align:left; } }
/* [CF-IDE-2026-08 / 2.25、13.9] 导出菜单与画布宿主只服务交互，不进入工作流 DSL。 */
.workflow-editor-canvas-host,.workflow-editor-inspector-host { width:100%;height:100%;min-width:0;min-height:0; }.workflow-toolbar__export { position:relative;display:inline-grid; }.workflow-toolbar__export-menu { position:absolute;z-index:30;top:39px;right:0;display:grid;width:164px;padding:5px;border:1px solid var(--workflow-border);border-radius:9px;background:var(--workflow-panel);box-shadow:0 12px 30px rgb(15 23 42 / .18); }.workflow-toolbar__export-menu button { display:flex;min-height:32px;align-items:center;gap:7px;padding:0 8px;border-radius:6px;color:var(--workflow-text);font-size:11px;text-align:left;white-space:nowrap; }.workflow-toolbar__export-menu button:hover { background:var(--workflow-hover);color:var(--workflow-primary); }
@media (prefers-reduced-motion:reduce) { .workflow-editor-loading span { animation:none; } }
</style>
