<template>
  <section ref="canvasRef" class="workflow-canvas" :class="{ 'workflow-canvas--read-only': readOnly, 'workflow-canvas--focus': focusMode }" tabindex="0" aria-label="CloudFlow 工作流画布" @dragover.prevent @drop="onDrop" @contextmenu.prevent="openCanvasMenu">
    <VueFlow
      v-if="canvasReady"
      v-model:nodes="flowNodes"
      v-model:edges="flowEdges"
      :min-zoom="0.2"
      :max-zoom="2.2"
      :nodes-draggable="!readOnly"
      :nodes-connectable="!readOnly"
      :elements-selectable="true"
      :select-nodes-on-drag="true"
      :selection-on-drag="true"
      :snap-to-grid="true"
      :snap-grid="[16, 16]"
      :pan-on-drag="[1, 2]"
      :zoom-on-pinch="true"
      :zoom-on-scroll="true"
      :zoom-activation-key-code="['Control', 'Meta']"
      @connect="onConnect"
      @node-click="onNodeClick"
      @edge-click="onEdgeClick"
      @node-context-menu="onNodeContextMenu"
      @edge-context-menu="onEdgeContextMenu"
      @selection-change="onSelectionChange"
      @node-drag-stop="onNodeDragStop"
      @pane-click="$emit('clear-selection')"
      @move="updateViewport"
      @move-end="updateViewport"
    >
      <Background v-if="background !== 'none'" :variant="background === 'lines' ? BackgroundVariant.Lines : BackgroundVariant.Dots" :gap="22" :size="background === 'lines' ? 1 : 1.4" pattern-color="var(--workflow-grid, #d8e1ee)" />
      <Controls position="bottom-left" :show-interactive="false">
        <ControlButton title="适合画布" aria-label="适合画布" @click="fitView"><i class="fa fa-compress"></i></ControlButton>
        <ControlButton title="重置为 100%" aria-label="重置为 100%" @click="resetZoom"><i class="fa fa-crosshairs"></i></ControlButton>
        <ControlButton title="自动布局" aria-label="自动布局" @click="$emit('auto-layout')"><i class="fa fa-magic"></i></ControlButton>
      </Controls>

      <template #node-workflow="{ data }">
        <!--
          [CF-IDE-2026-08 / 4.1-4.8] 旧实现把矩形整体旋转 45 度伪装成条件菱形，
          这会同时旋转文字、Handle 和命中区域。现在以独立 SVG 形状作为真正轮廓，
          内容层不旋转，故视觉、可访问性及连线锚点均保持稳定。
        -->
        <article class="workflow-visual-node" :class="[`workflow-visual-node--${data.visualType}`, { 'is-selected': data.selected, 'is-error': data.errorKeys?.length, 'is-disabled': data.disabled, 'is-locked': data.locked, 'is-collapsed': data.collapsed }]" :style="{ '--node-color': data.color || '#64748b' }" :aria-label="`${data.label} 节点`">
          <svg v-if="nodeShapePath(data.visualType)" class="workflow-visual-node__shape" viewBox="0 0 320 180" preserveAspectRatio="none" aria-hidden="true"><path :d="nodeShapePath(data.visualType)" /></svg>
          <Handle v-if="data.visualType !== 'trigger'" id="target" class="workflow-handle workflow-handle--target" type="target" :position="Position.Left" />
          <div class="workflow-visual-node__content">
            <header class="workflow-visual-node__header">
              <span class="workflow-visual-node__icon"><i :class="`fa ${data.icon || 'fa-cube'}`"></i></span>
              <span class="workflow-visual-node__title"><strong>{{ data.label }}</strong><small>{{ nodeSubtitle(data) }}</small></span>
              <span v-if="data.errorKeys?.length" class="workflow-visual-node__status" title="存在编译问题"><i class="fa fa-exclamation-circle"></i></span>
              <span v-else-if="data.locked" class="workflow-visual-node__status" title="位置已锁定"><i class="fa fa-lock"></i></span>
            </header>
            <p v-if="!data.collapsed && nodeSummary(data)" class="workflow-visual-node__summary">{{ nodeSummary(data) }}</p>
            <footer v-if="!data.collapsed" class="workflow-visual-node__meta"><span v-if="data.retry" title="已设置重试"><i class="fa fa-repeat"></i>{{ data.retry.maxAttempts }}</span><span v-if="data.timeout" title="已设置超时"><i class="fa fa-clock-o"></i>{{ data.timeout }}</span><span v-if="data.output" title="步骤输出"><i class="fa fa-sign-out"></i>{{ data.output }}</span></footer>
            <div v-if="data.visualType === 'try'" class="workflow-visual-node__branches" aria-label="异常处理分支"><span>TRY</span><span>CATCH</span><span>FINALLY</span></div>
          </div>
          <template v-if="isControl(data.visualType)">
            <Handle v-if="data.visualType === 'condition'" id="true" class="workflow-handle workflow-handle--true" type="source" :position="Position.Right" /><span v-if="data.visualType === 'condition'" class="workflow-visual-node__port workflow-visual-node__port--true">真</span>
            <Handle v-if="data.visualType === 'condition'" id="false" class="workflow-handle workflow-handle--false" type="source" :position="Position.Bottom" /><span v-if="data.visualType === 'condition'" class="workflow-visual-node__port workflow-visual-node__port--false">假</span>
            <Handle v-if="['foreach', 'while', 'parallel'].includes(data.visualType)" id="body" class="workflow-handle workflow-handle--body" type="source" :position="Position.Right" />
            <Handle v-if="data.visualType === 'try'" id="try" class="workflow-handle workflow-handle--try" type="source" :position="Position.Right" /><Handle v-if="data.visualType === 'try'" id="catch" class="workflow-handle workflow-handle--catch" type="source" :position="Position.Bottom" /><Handle v-if="data.visualType === 'try'" id="finally" class="workflow-handle workflow-handle--finally" type="source" :position="Position.Top" />
          </template>
          <Handle v-else-if="data.visualType !== 'note' && data.visualType !== 'group'" id="source" class="workflow-handle workflow-handle--source" type="source" :position="Position.Right" />
        </article>
      </template>
    </VueFlow>
    <!-- [AUDIT FIX 3.1] Vue Flow 只能在父容器获得非零尺寸后挂载，避免首帧 0×0 导致整张画布不渲染。 -->
    <div v-else class="workflow-canvas__loading" role="status" aria-live="polite">正在计算画布尺寸…</div>

    <!--
      [CF-IDE-2026-08 / 8.1-8.20] 当前锁定的 Vue Flow 包未导出 MiniMap。
      使用同一节点/视口状态投影的交互式缩略图，支持折叠、点击与拖拽视口；不维护第二份图数据。
    -->
    <aside v-if="minimapVisible" class="workflow-minimap" :class="{ 'workflow-minimap--collapsed': minimapCollapsed }" aria-label="画布导航缩略图">
      <button class="workflow-minimap__toggle" type="button" :title="minimapCollapsed ? '展开小地图' : '折叠小地图'" @click.stop="minimapCollapsed = !minimapCollapsed"><i :class="`fa ${minimapCollapsed ? 'fa-map-o' : 'fa-minus'}`"></i></button>
      <div v-show="!minimapCollapsed" ref="minimapRef" class="workflow-minimap__surface" @pointerdown.prevent="onMiniMapPointerDown" @dblclick.prevent="onMiniMapFocus">
        <span v-for="node in nodes.filter((item) => item.type !== 'note')" :key="node.id" class="workflow-minimap__node" :class="[`workflow-minimap__node--${node.type}`, { 'is-selected': selectedNodeIds.includes(node.id) }]" :style="minimapNodeStyle(node)" :title="node.data.label">{{ minimapNodeText(node) }}</span>
        <span class="workflow-minimap__viewport" :style="minimapViewportStyle"></span>
        <span class="workflow-minimap__hint">拖拽视口</span>
      </div>
    </aside>

    <div v-if="showPerf" class="workflow-canvas__performance" aria-live="polite"><i class="fa fa-tachometer"></i><span>{{ nodes.length }} 节点 · {{ edges.length }} 连线</span><span>缩放 {{ Math.round(viewport.zoom * 100) }}%</span><span>{{ fps }} FPS</span></div>
    <div v-if="menu.visible" class="workflow-canvas__menu" :style="{ left: `${menu.x}px`, top: `${menu.y}px` }" role="menu">
      <template v-if="menu.kind === 'node'">
        <button type="button" @click="selectMenuNode"><i class="fa fa-pencil"></i>编辑节点</button>
        <button type="button" @click="copyMenuNode"><i class="fa fa-copy"></i>复制节点</button>
        <button type="button" @click="duplicateMenuNode"><i class="fa fa-clone"></i>在此处创建副本</button>
        <button type="button" @click="deleteMenuSelection"><i class="fa fa-trash"></i>删除节点</button>
      </template>
      <template v-else-if="menu.kind === 'edge'">
        <button type="button" @click="selectMenuEdge"><i class="fa fa-pencil"></i>编辑连线</button>
        <button type="button" @click="deleteMenuSelection"><i class="fa fa-trash"></i>删除连线</button>
      </template>
      <template v-else>
        <button type="button" @click="$emit('paste', menu.flowPosition); closeMenu()"><i class="fa fa-paste"></i>粘贴</button>
        <button type="button" @click="$emit('auto-layout'); closeMenu()"><i class="fa fa-magic"></i>自动布局</button>
        <button type="button" @click="fitView(); closeMenu()"><i class="fa fa-compress"></i>适合画布</button>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
// 使用 Vue Flow 受控画布；缩略图从同一节点模型投影，避免维护第二份图数据。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Background, BackgroundVariant } from '@vue-flow/background'
import { ControlButton, Controls } from '@vue-flow/controls'
import { Handle, MarkerType, Position, VueFlow, useVueFlow, type Connection } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
// 当前 @vue-flow/background 版本把背景图案样式内联在组件中，
// 不导入不存在的 dist/style.css，避免生产构建解析失败。
import type { WorkflowVisualEdge, WorkflowVisualEdgeKind, WorkflowVisualNode } from '@/types/cloudflowVisual'

const props = withDefaults(defineProps<{
  nodes: WorkflowVisualNode[]
  edges: WorkflowVisualEdge[]
  selectedNodeIds?: string[]
  selectedEdgeIds?: string[]
  background?: 'dots' | 'lines' | 'none'
  minimapVisible?: boolean
  focusMode?: boolean
  readOnly?: boolean
  showPerf?: boolean
}>(), { selectedNodeIds: () => [], selectedEdgeIds: () => [], background: 'dots', minimapVisible: true, focusMode: false, readOnly: false, showPerf: false })
const emit = defineEmits<{
  connect: [edge: WorkflowVisualEdge, control?: { parentId: string; branch: string }]
  'select-node': [id: string]
  'select-edge': [id: string]
  'selection-change': [selection: { nodeIds: string[]; edgeIds: string[] }]
  'clear-selection': []
  'position-change': [id: string, position: { x: number; y: number }]
  drop: [payload: { raw: string; position: { x: number; y: number } }]
  'auto-layout': []
  paste: [position: { x: number; y: number }]
  copy: []
  'delete-selection': []
}>()
const canvasRef = ref<HTMLElement | null>(null)
const minimapRef = ref<HTMLElement | null>(null)
// [AUDIT FIX 3.1] 记录根容器是否已经完成尺寸测量；ResizeObserver 解决路由切换、面板折叠和字体加载后的延迟布局。
const canvasReady = ref(false)
const flowNodes = ref<any[]>([])
const flowEdges = ref<any[]>([])
const viewport = ref({ x: 0, y: 0, zoom: 1 })
const canvasSize = ref({ width: 1, height: 1 })
const fps = ref(60)
const minimapCollapsed = ref(false)
const menu = ref({ visible: false, kind: 'canvas' as 'canvas' | 'node' | 'edge', targetId: '', x: 0, y: 0, flowPosition: { x: 0, y: 0 } })
let frame = 0
let lastFrame = performance.now()
let frameCount = 0
let layoutObserver: ResizeObserver | undefined
let minimapDragging = false
const { fitView: rawFitView, screenToFlowCoordinate, setCenter } = useVueFlow()
const minimapBounds = computed(() => {
  if (!props.nodes.length) return { minX: 0, minY: 0, width: 1, height: 1 }
  const xs = props.nodes.map((node) => node.position.x)
  const ys = props.nodes.map((node) => node.position.y)
  const minX = Math.min(...xs) - 100
  const minY = Math.min(...ys) - 100
  const maxX = Math.max(...xs) + 360
  const maxY = Math.max(...ys) + 220
  return { minX, minY, width: Math.max(1, maxX - minX), height: Math.max(1, maxY - minY) }
})
const minimapViewportStyle = computed(() => {
  const bounds = minimapBounds.value
  const zoom = Math.max(viewport.value.zoom, 0.2)
  const visibleWidth = canvasSize.value.width / zoom
  const visibleHeight = canvasSize.value.height / zoom
  return {
    left: `${clamp(((-viewport.value.x / zoom - bounds.minX) / bounds.width) * 100, 0, 100)}%`,
    top: `${clamp(((-viewport.value.y / zoom - bounds.minY) / bounds.height) * 100, 0, 100)}%`,
    width: `${clamp((visibleWidth / bounds.width) * 100, 6, 100)}%`,
    height: `${clamp((visibleHeight / bounds.height) * 100, 6, 100)}%`,
  }
})

function clamp(value: number, min: number, max: number) { return Math.min(max, Math.max(min, value)) }
function toFlowNode(node: WorkflowVisualNode) {
  return { id: node.id, type: 'workflow', position: node.position, selected: props.selectedNodeIds.includes(node.id), data: { ...node.data, selected: props.selectedNodeIds.includes(node.id), visualType: node.type } }
}
function defaultEdgeStyle(kind: WorkflowVisualEdgeKind) {
  if (kind === 'condition-true') return { stroke: '#16a34a', strokeWidth: 2.25, strokeDasharray: undefined }
  if (kind === 'condition-false') return { stroke: '#dc2626', strokeWidth: 2.25, strokeDasharray: '7 4' }
  if (kind === 'parallel-branch') return { stroke: '#7c3aed', strokeWidth: 2.25, strokeDasharray: '2 4' }
  if (['catch-branch', 'finally-branch'].includes(kind)) return { stroke: '#f97316', strokeWidth: 2.25, strokeDasharray: '3 4' }
  if (kind === 'loop-body') return { stroke: '#0f766e', strokeWidth: 2.25, strokeDasharray: '5 3' }
  return { stroke: '#64748b', strokeWidth: 1.9, strokeDasharray: undefined }
}
function edgeStyle(edge: WorkflowVisualEdge) {
  const style = { ...defaultEdgeStyle(edge.data.kind), stroke: edge.data.color || defaultEdgeStyle(edge.data.kind).stroke }
  if (edge.data.lineStyle === 'solid') style.strokeDasharray = undefined
  if (edge.data.lineStyle === 'dashed') style.strokeDasharray = '7 4'
  if (edge.data.lineStyle === 'dotted') style.strokeDasharray = '2 4'
  return style
}
function toFlowEdge(edge: WorkflowVisualEdge) {
  return {
    ...edge,
    type: edge.data.route || edge.type || 'smoothstep',
    label: edge.data.label || edge.label || edge.data.condition || edgeLabel(edge.data.kind),
    labelStyle: { fill: edge.data.color || defaultEdgeStyle(edge.data.kind).stroke, fontWeight: 700 },
    style: edgeStyle(edge),
    markerEnd: MarkerType.ArrowClosed,
    selected: props.selectedEdgeIds.includes(edge.id),
    animated: edge.animated || ['loop-body', 'parallel-branch'].includes(edge.data.kind),
    data: edge.data,
  }
}
function syncElements() { flowNodes.value = props.nodes.map(toFlowNode); flowEdges.value = props.edges.map(toFlowEdge) }
watch(() => [props.nodes, props.edges, props.selectedNodeIds, props.selectedEdgeIds], syncElements, { deep: true, immediate: true })
function isControl(type: string) { return ['condition', 'foreach', 'while', 'parallel', 'try'].includes(type) }
function nodeSubtitle(data: any) { if (data.visualType === 'trigger') return '工作流入口'; if (data.action?.provider === 'plugin') return `${data.action.pluginId || 'plugin'} · ${data.action.function || 'run'}`; if (data.action) return `${data.action.provider}.${data.action.service || ''}.${data.action.method || ''}`.replace('..', '.'); return data.visualType }
function nodeSummary(data: any) { return data.expression || (data.iterator ? `${data.iterator} in ${data.collection || ''}` : data.note || '') }
function edgeLabel(kind: WorkflowVisualEdgeKind) { return ({ 'condition-true': '条件为真', 'condition-false': '条件为假', 'loop-body': '循环体', 'parallel-branch': '并行分支', 'try-branch': 'try', 'catch-branch': 'catch', 'finally-branch': 'finally', dependency: '' } as Record<WorkflowVisualEdgeKind, string>)[kind] }
function nodeShapePath(type: string) {
  if (type === 'condition') return 'M160 5 L315 90 L160 175 L5 90 Z'
  if (type === 'trigger') return 'M42 5 L278 5 L315 90 L278 175 L42 175 L5 90 Z'
  if (type === 'parallel') return 'M34 5 L286 5 L315 175 L5 175 Z'
  if (type === 'assert') return 'M48 5 L272 5 L315 48 L315 132 L272 175 L48 175 L5 132 L5 48 Z'
  return ''
}
function onConnect(connection: Connection) {
  if (props.readOnly || !connection.source || !connection.target) return
  const source = props.nodes.find((node) => node.id === connection.source)
  const sourceHandle = connection.sourceHandle || 'source'
  const kind: WorkflowVisualEdgeKind = source?.type === 'condition' && sourceHandle === 'true' ? 'condition-true' : source?.type === 'condition' && sourceHandle === 'false' ? 'condition-false' : source?.type === 'try' && sourceHandle === 'catch' ? 'catch-branch' : source?.type === 'try' && sourceHandle === 'finally' ? 'finally-branch' : source?.type === 'try' ? 'try-branch' : ['foreach', 'while'].includes(source?.type || '') ? 'loop-body' : source?.type === 'parallel' ? 'parallel-branch' : 'dependency'
  const branch = kind === 'condition-true' ? 'true' : kind === 'condition-false' ? 'false' : kind === 'catch-branch' ? 'catch' : kind === 'finally-branch' ? 'finally' : kind === 'try-branch' ? 'try' : ['loop-body', 'parallel-branch'].includes(kind) ? 'body' : undefined
  emit('connect', { id: `edge-${crypto.randomUUID()}`, source: connection.source, target: connection.target, type: 'smoothstep', animated: ['loop-body', 'parallel-branch'].includes(kind), data: { kind } }, branch ? { parentId: connection.source, branch } : undefined)
}
function onNodeClick(event: any) { closeMenu(); emit('select-node', event.node.id) }
function onEdgeClick(event: any) { closeMenu(); emit('select-edge', event.edge.id) }
function onSelectionChange(event: any) { emit('selection-change', { nodeIds: (event.nodes || []).map((node: any) => node.id), edgeIds: (event.edges || []).map((edge: any) => edge.id) }) }
function onNodeDragStop(event: any) { if (!props.readOnly) emit('position-change', event.node.id, event.node.position) }
function onDrop(event: DragEvent) { const raw = event.dataTransfer?.getData('application/pcd-workflow-node'); if (!raw || !canvasRef.value) return; emit('drop', { raw, position: screenToFlowCoordinate({ x: event.clientX, y: event.clientY }) }) }
function fitView() { void nextTick(() => rawFitView({ padding: 0.18, duration: 220 })) }
function resetZoom() { const bounds = minimapBounds.value; void setCenter(bounds.minX + bounds.width / 2, bounds.minY + bounds.height / 2, { zoom: 1, duration: 160 }) }
function focusNode(id: string) { const node = props.nodes.find((item) => item.id === id); if (node) void setCenter(node.position.x + 120, node.position.y + 55, { zoom: Math.max(1, viewport.value.zoom), duration: 180 }) }
function minimapNodeStyle(node: WorkflowVisualNode) {
  const bounds = minimapBounds.value
  return { left: `${((node.position.x - bounds.minX) / bounds.width) * 100}%`, top: `${((node.position.y - bounds.minY) / bounds.height) * 100}%`, background: node.data.color || '#64748b' }
}
function minimapNodeText(node: WorkflowVisualNode) { return node.data.label.slice(0, 1) }
function navigateMiniMapAt(clientX: number, clientY: number, zoom = viewport.value.zoom) {
  const element = minimapRef.value
  if (!element) return
  const rect = element.getBoundingClientRect()
  const bounds = minimapBounds.value
  const x = bounds.minX + ((clientX - rect.left) / rect.width) * bounds.width
  const y = bounds.minY + ((clientY - rect.top) / rect.height) * bounds.height
  void setCenter(x, y, { zoom: clamp(zoom, .25, 2.2), duration: minimapDragging ? 0 : 180 })
}
function onMiniMapPointerDown(event: PointerEvent) {
  minimapDragging = true
  navigateMiniMapAt(event.clientX, event.clientY)
  const move = (moveEvent: PointerEvent) => navigateMiniMapAt(moveEvent.clientX, moveEvent.clientY)
  const stop = () => { minimapDragging = false; window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', stop) }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', stop, { once: true })
}
function onMiniMapFocus(event: MouseEvent) { navigateMiniMapAt(event.clientX, event.clientY, Math.min(2.2, viewport.value.zoom + .3)) }
function updateViewport(event: any) { viewport.value = event.viewport || event || viewport.value }
function menuPosition(event: MouseEvent) {
  const rect = canvasRef.value?.getBoundingClientRect()
  return { x: rect ? event.clientX - rect.left : event.offsetX, y: rect ? event.clientY - rect.top : event.offsetY, flowPosition: screenToFlowCoordinate({ x: event.clientX, y: event.clientY }) }
}
function openCanvasMenu(event: MouseEvent) { if (props.readOnly) return; menu.value = { visible: true, kind: 'canvas', targetId: '', ...menuPosition(event) } }
function onNodeContextMenu(event: any) { if (props.readOnly) return; event.event?.preventDefault?.(); emit('select-node', event.node.id); menu.value = { visible: true, kind: 'node', targetId: event.node.id, ...menuPosition(event.event || event) } }
function onEdgeContextMenu(event: any) { if (props.readOnly) return; event.event?.preventDefault?.(); emit('select-edge', event.edge.id); menu.value = { visible: true, kind: 'edge', targetId: event.edge.id, ...menuPosition(event.event || event) } }
function selectMenuNode() { emit('select-node', menu.value.targetId); closeMenu() }
function selectMenuEdge() { emit('select-edge', menu.value.targetId); closeMenu() }
function copyMenuNode() { emit('select-node', menu.value.targetId); emit('copy'); closeMenu() }
function duplicateMenuNode() { emit('select-node', menu.value.targetId); emit('copy'); emit('paste', menu.value.flowPosition); closeMenu() }
function deleteMenuSelection() { if (menu.value.kind === 'node') emit('select-node', menu.value.targetId); if (menu.value.kind === 'edge') emit('select-edge', menu.value.targetId); emit('delete-selection'); closeMenu() }
function closeMenu() { menu.value.visible = false }
function updateFps(now: number) { frameCount += 1; if (now - lastFrame > 500) { fps.value = Math.round((frameCount * 1000) / (now - lastFrame)); frameCount = 0; lastFrame = now }; frame = requestAnimationFrame(updateFps) }
function measureCanvas() {
  const element = canvasRef.value
  if (!element) return
  const rect = element.getBoundingClientRect()
  canvasSize.value = { width: Math.max(1, rect.width), height: Math.max(1, rect.height) }
  const ready = rect.width > 1 && rect.height > 1
  if (ready && !canvasReady.value) {
    canvasReady.value = true
    // Vue Flow 内部节点测量依赖真实 DOM 尺寸；再等一个绘制帧后适配视口，避免节点落在画布外。
    void nextTick(() => requestAnimationFrame(() => rawFitView({ padding: 0.18, duration: 0 })))
  } else if (!ready && canvasReady.value) {
    // 面板被折叠或路由暂时隐藏时卸载 Vue Flow，防止它缓存 0×0 的 viewport。
    canvasReady.value = false
  }
}

/** CF-IDE-2026-08 / 2.25、13.9：不依赖未审计的第三方导出包，按图模型生成安全 SVG，再转 PNG 或交给浏览器保存为 PDF。 */
function escapeXml(value: unknown) { return String(value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;') }
function snapshotNodeShape(node: WorkflowVisualNode, x: number, y: number, width: number, height: number) {
  const color = node.data.color || '#64748b'
  const fill = '#ffffff'
  if (node.type === 'condition') return `<path d="M ${x + width / 2} ${y} L ${x + width} ${y + height / 2} L ${x + width / 2} ${y + height} L ${x} ${y + height / 2} Z" fill="${fill}" stroke="${color}" stroke-width="2"/>`
  if (node.type === 'trigger') return `<path d="M ${x + 28} ${y} L ${x + width - 28} ${y} L ${x + width} ${y + height / 2} L ${x + width - 28} ${y + height} L ${x + 28} ${y + height} L ${x} ${y + height / 2} Z" fill="${fill}" stroke="${color}" stroke-width="2"/>`
  if (node.type === 'parallel') return `<path d="M ${x + 22} ${y} L ${x + width - 22} ${y} L ${x + width} ${y + height} L ${x} ${y + height} Z" fill="${fill}" stroke="${color}" stroke-width="2"/>`
  if (node.type === 'assert') return `<path d="M ${x + 25} ${y} L ${x + width - 25} ${y} L ${x + width} ${y + 25} L ${x + width} ${y + height - 25} L ${x + width - 25} ${y + height} L ${x + 25} ${y + height} L ${x} ${y + height - 25} L ${x} ${y + 25} Z" fill="${fill}" stroke="${color}" stroke-width="2"/>`
  return `<rect x="${x}" y="${y}" width="${width}" height="${height}" rx="13" fill="${node.type === 'note' ? '#fef9c3' : fill}" stroke="${color}" stroke-width="${node.type === 'try' ? 2 : 1.5}" ${node.type === 'try' ? 'stroke-dasharray="6 4"' : ''}/>`
}
function createSnapshotSvg() {
  const bounds = minimapBounds.value
  const padding = 38
  const width = Math.ceil(bounds.width + padding * 2)
  const height = Math.ceil(bounds.height + padding * 2)
  const offsetX = padding - bounds.minX
  const offsetY = padding - bounds.minY
  const byId = new Map(props.nodes.map((node) => [node.id, node]))
  const edgeMarkup = props.edges.map((edge) => {
    const source = byId.get(edge.source); const target = byId.get(edge.target)
    if (!source || !target) return ''
    const sx = source.position.x + 238 + offsetX; const sy = source.position.y + 52 + offsetY
    const tx = target.position.x + offsetX; const ty = target.position.y + 52 + offsetY
    const stroke = edge.data.color || defaultEdgeStyle(edge.data.kind).stroke
    const dash = edgeStyle(edge).strokeDasharray ? `stroke-dasharray="${edgeStyle(edge).strokeDasharray}"` : ''
    const label = edge.data.label || edge.label || edge.data.condition || edgeLabel(edge.data.kind)
    const lx = (sx + tx) / 2; const ly = (sy + ty) / 2 - 5
    return `<path d="M ${sx} ${sy} C ${sx + 48} ${sy}, ${tx - 48} ${ty}, ${tx} ${ty}" fill="none" stroke="${stroke}" stroke-width="2" ${dash} marker-end="url(#arrow)"/><text x="${lx}" y="${ly}" text-anchor="middle" fill="${stroke}" font-family="Arial, sans-serif" font-size="10" font-weight="700">${escapeXml(label)}</text>`
  }).join('')
  const nodeMarkup = props.nodes.map((node) => {
    const x = node.position.x + offsetX; const y = node.position.y + offsetY
    const width = node.type === 'condition' ? 258 : 238; const height = node.type === 'condition' ? 140 : 96
    const titleX = x + (node.type === 'condition' ? width / 2 : 16); const anchor = node.type === 'condition' ? 'middle' : 'start'
    const subtitle = nodeSubtitle({ ...node.data, visualType: node.type })
    return `${snapshotNodeShape(node, x, y, width, height)}<text x="${titleX}" y="${y + 35}" text-anchor="${anchor}" fill="#1e293b" font-family="Arial, sans-serif" font-size="13" font-weight="700">${escapeXml(node.data.label)}</text><text x="${titleX}" y="${y + 55}" text-anchor="${anchor}" fill="#64748b" font-family="monospace" font-size="10">${escapeXml(subtitle)}</text>`
  }).join('')
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><defs><marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#64748b"/></marker><pattern id="grid" width="22" height="22" patternUnits="userSpaceOnUse"><circle cx="1" cy="1" r="1" fill="#d8e1ee"/></pattern></defs><rect width="100%" height="100%" fill="#f8fafc"/><rect width="100%" height="100%" fill="url(#grid)"/>${edgeMarkup}${nodeMarkup}</svg>`
}
function downloadBlob(blob: Blob, filename: string) { const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click(); window.setTimeout(() => URL.revokeObjectURL(url), 0) }
function exportSnapshot(format: 'svg' | 'png') {
  const svg = createSnapshotSvg()
  const filename = `cloudflow-${Date.now()}`
  if (format === 'svg') { downloadBlob(new Blob([svg], { type: 'image/svg+xml;charset=utf-8' }), `${filename}.svg`); return }
  const image = new Image()
  const source = URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml;charset=utf-8' }))
  image.onload = () => {
    const canvas = document.createElement('canvas'); canvas.width = image.width * 2; canvas.height = image.height * 2
    const context = canvas.getContext('2d'); context?.scale(2, 2); context?.drawImage(image, 0, 0)
    canvas.toBlob((blob) => { if (blob) downloadBlob(blob, `${filename}.png`); URL.revokeObjectURL(source) }, 'image/png')
  }
  image.src = source
}
function printSnapshot() {
  const printWindow = window.open('', '_blank', 'noopener,noreferrer')
  if (!printWindow) return
  printWindow.document.write(`<!doctype html><html><head><title>CloudFlow 画布</title><style>html,body{margin:0;padding:0;background:#fff}svg{display:block;width:100%;height:auto}@page{size:auto;margin:12mm}</style></head><body>${createSnapshotSvg()}<script>window.onload=()=>window.print()<\/script></body></html>`)
  printWindow.document.close()
}
onMounted(() => {
  if (props.showPerf) frame = requestAnimationFrame(updateFps)
  if (typeof ResizeObserver !== 'undefined') {
    layoutObserver = new ResizeObserver(measureCanvas)
    if (canvasRef.value) layoutObserver.observe(canvasRef.value)
  }
  void nextTick(() => requestAnimationFrame(measureCanvas))
})
onBeforeUnmount(() => { if (frame) cancelAnimationFrame(frame); layoutObserver?.disconnect() })
defineExpose({ fitView, resetZoom, focusNode, exportSnapshot, printSnapshot })
</script>

<style scoped>
.workflow-canvas { --workflow-grid:#d8e1ee;position:relative;width:100%;height:100%;min-width:0;min-height:280px;overflow:hidden;background:var(--workflow-canvas,#f8fafc);outline:none;box-sizing:border-box; }.workflow-canvas :deep(.vue-flow) { width:100%;height:100%;min-height:280px; }.workflow-canvas__loading { display:grid;width:100%;height:100%;min-height:280px;place-items:center;color:var(--workflow-muted,#64748b);font-size:12px; }.workflow-canvas:focus-visible { box-shadow:inset 0 0 0 2px var(--workflow-primary,#2563eb); }.workflow-canvas--read-only { cursor:default; }.workflow-canvas--focus :deep(.vue-flow__controls),.workflow-canvas--focus .workflow-minimap { opacity:.12;transition:opacity .18s ease; }.workflow-canvas--focus:hover :deep(.vue-flow__controls),.workflow-canvas--focus:hover .workflow-minimap { opacity:1; }
.workflow-visual-node { position:relative;width:238px;min-height:78px;border:1px solid color-mix(in srgb,var(--node-color) 36%,#cbd5e1);border-radius:13px;background:var(--workflow-node,#fff);box-shadow:0 8px 24px rgb(15 23 42 / .09);transition:border-color .16s ease,box-shadow .16s ease,opacity .16s ease;isolation:isolate; }.workflow-visual-node__shape { position:absolute;z-index:-1;inset:0;width:100%;height:100%;filter:drop-shadow(0 8px 15px rgb(15 23 42 / .08)); }.workflow-visual-node__shape path { fill:var(--workflow-node,#fff);stroke:var(--node-color);stroke-width:2;vector-effect:non-scaling-stroke; }.workflow-visual-node:hover,.workflow-visual-node.is-selected { border-color:var(--node-color);box-shadow:0 0 0 3px color-mix(in srgb,var(--node-color) 17%,transparent),0 12px 28px rgb(15 23 42 / .11); }.workflow-visual-node.is-error { border-color:#dc2626;--node-color:#dc2626; }.workflow-visual-node.is-disabled { opacity:.42;filter:grayscale(.7); }.workflow-visual-node.is-locked { background-image:linear-gradient(135deg,rgb(100 116 139 / .05) 25%,transparent 25%,transparent 50%,rgb(100 116 139 / .05) 50%,rgb(100 116 139 / .05) 75%,transparent 75%);background-size:12px 12px; }.workflow-visual-node--condition { width:258px;min-height:140px;border-color:transparent!important;border-radius:0;background:transparent;box-shadow:none; }.workflow-visual-node--condition:hover,.workflow-visual-node--condition.is-selected { box-shadow:none; }.workflow-visual-node--condition .workflow-visual-node__content { display:grid;min-height:140px;place-content:center;padding:16px 52px; }.workflow-visual-node--condition .workflow-visual-node__header { min-height:auto;padding:0; }.workflow-visual-node--condition .workflow-visual-node__summary { max-width:138px;margin:8px auto 0;text-align:center; }.workflow-visual-node--condition .workflow-visual-node__meta { display:none; }.workflow-visual-node--trigger,.workflow-visual-node--parallel,.workflow-visual-node--assert { border-color:transparent!important;border-radius:0;background:transparent; }.workflow-visual-node--parallel,.workflow-visual-node--try { border-style:dashed;border-width:2px; }.workflow-visual-node--foreach,.workflow-visual-node--while { border-radius:28px 28px 13px 13px; }.workflow-visual-node--foreach::before,.workflow-visual-node--while::before { position:absolute;top:5px;right:9px;left:9px;height:8px;border:1px solid color-mix(in srgb,var(--node-color) 42%,transparent);border-radius:50%;content:'';opacity:.55;pointer-events:none; }.workflow-visual-node--note { width:190px;border-color:#eab308;background:#fef9c3;box-shadow:0 8px 20px rgb(202 138 4 / .13); }.workflow-visual-node--group { width:280px;min-height:92px;border-style:dashed;background:rgb(148 163 184 / .08); }
.workflow-visual-node__content { position:relative;z-index:1; }.workflow-visual-node__header { display:flex;min-height:50px;align-items:center;gap:9px;padding:10px 11px 7px; }.workflow-visual-node__icon { display:grid;width:29px;height:29px;flex:0 0 29px;place-items:center;border-radius:8px;background:color-mix(in srgb,var(--node-color) 14%,transparent);color:var(--node-color);font-size:13px; }.workflow-visual-node__title { min-width:0;flex:1; }.workflow-visual-node__title strong,.workflow-visual-node__title small { display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }.workflow-visual-node__title strong { color:var(--workflow-text,#1e293b);font-size:12px; }.workflow-visual-node__title small { margin-top:2px;color:var(--workflow-muted,#94a3b8);font:9px ui-monospace,SFMono-Regular,Menlo,monospace; }.workflow-visual-node__status { color:#dc2626;font-size:13px; }.workflow-visual-node__summary { display:-webkit-box;overflow:hidden;margin:0 11px 8px;color:var(--workflow-muted,#64748b);font:10px/1.45 ui-monospace,SFMono-Regular,Menlo,monospace;-webkit-box-orient:vertical;-webkit-line-clamp:2; }.workflow-visual-node__meta { display:flex;min-height:25px;align-items:center;gap:8px;padding:0 11px 8px;color:var(--workflow-muted,#64748b);font-size:9px; }.workflow-visual-node__meta span { display:inline-flex;align-items:center;gap:3px; }.workflow-visual-node__branches { display:flex;justify-content:space-between;padding:0 10px 9px;color:var(--node-color);font-size:8px;font-weight:800;letter-spacing:.04em; }.workflow-handle { z-index:3;width:9px!important;height:9px!important;border:2px solid var(--workflow-node,#fff)!important;background:var(--node-color)!important; }.workflow-handle--target { left:-5px!important; }.workflow-handle--source,.workflow-handle--true,.workflow-handle--body,.workflow-handle--try { right:-5px!important; }.workflow-handle--false,.workflow-handle--catch { bottom:-5px!important; }.workflow-handle--finally { top:-5px!important; }.workflow-visual-node__port { position:absolute;z-index:3;color:var(--node-color);font-size:8px;font-weight:800; }.workflow-visual-node__port--true { right:31px;top:35px; }.workflow-visual-node__port--false { bottom:23px;left:42px; }
.workflow-canvas :deep(.vue-flow__edge-path) { transition:stroke-width .16s ease,filter .16s ease; }.workflow-canvas :deep(.vue-flow__edge:hover .vue-flow__edge-path),.workflow-canvas :deep(.vue-flow__edge.selected .vue-flow__edge-path) { stroke-width:3.5px!important;filter:drop-shadow(0 1px 2px rgb(15 23 42 / .2)); }.workflow-canvas :deep(.vue-flow__edge-text) { fill:var(--workflow-text,#334155);font-size:10px;font-weight:700; }.workflow-canvas :deep(.vue-flow__edge-textbg) { fill:var(--workflow-node,#fff);stroke:var(--workflow-border,#cbd5e1);stroke-width:.5px; }.workflow-canvas :deep(.vue-flow__controls) { overflow:hidden;border:1px solid var(--workflow-border,#cbd5e1);border-radius:10px;box-shadow:0 8px 24px rgb(15 23 42 / .12); }.workflow-canvas :deep(.vue-flow__controls-button) { background:var(--workflow-node,#fff);color:var(--workflow-text,#334155); }
.workflow-minimap { position:absolute;z-index:4;right:12px;bottom:12px;width:198px;overflow:hidden;border:1px solid var(--workflow-border,#cbd5e1);border-radius:10px;background:color-mix(in srgb,var(--workflow-node,#fff) 90%,transparent);box-shadow:0 8px 24px rgb(15 23 42 / .14);backdrop-filter:blur(5px); }.workflow-minimap--collapsed { width:30px; }.workflow-minimap__toggle { position:absolute;z-index:2;top:3px;right:3px;display:grid;width:22px;height:22px;place-items:center;border:1px solid rgb(148 163 184 / .42);border-radius:5px;background:rgb(255 255 255 / .9);color:var(--workflow-muted,#64748b);font-size:10px; }.workflow-minimap__surface { position:relative;height:126px;overflow:hidden;cursor:grab;touch-action:none; }.workflow-minimap__surface:active { cursor:grabbing; }.workflow-minimap__node { position:absolute;display:grid;min-width:8px;height:8px;place-items:center;border-radius:2px;color:rgb(255 255 255 / .8);font-size:5px;line-height:1;transform:translate(-50%,-50%);pointer-events:none; }.workflow-minimap__node--condition { width:8px;height:8px;border-radius:0;transform:translate(-50%,-50%) rotate(45deg); }.workflow-minimap__node--condition::first-letter { transform:rotate(-45deg); }.workflow-minimap__node--parallel,.workflow-minimap__node--try { border:1px dashed rgb(15 23 42 / .35); }.workflow-minimap__node.is-selected { box-shadow:0 0 0 2px #2563eb; }.workflow-minimap__viewport { position:absolute;border:1px solid rgb(37 99 235 / .88);background:rgb(37 99 235 / .11);pointer-events:none; }.workflow-minimap__hint { position:absolute;right:6px;bottom:4px;color:var(--workflow-muted,#64748b);font-size:8px;pointer-events:none; }.workflow-canvas__performance { position:absolute;top:10px;right:12px;display:flex;gap:7px;padding:6px 8px;border:1px solid rgb(148 163 184 / .45);border-radius:8px;background:rgb(15 23 42 / .78);color:#e2e8f0;font:10px ui-monospace,SFMono-Regular,Menlo,monospace;pointer-events:none; }.workflow-canvas__menu { position:absolute;z-index:10;display:grid;min-width:166px;padding:5px;border:1px solid var(--workflow-border,#cbd5e1);border-radius:9px;background:var(--workflow-node,#fff);box-shadow:0 14px 34px rgb(15 23 42 / .18); }.workflow-canvas__menu button { display:flex;min-height:31px;align-items:center;gap:7px;padding:0 8px;border-radius:6px;color:var(--workflow-text,#334155);font-size:11px;text-align:left; }.workflow-canvas__menu button:hover { background:var(--workflow-hover,#f1f5f9); }
/* CF-IDE-2026-08：缩略图也使用裁剪菱形覆盖旧版本旋转提示，避免任何节点投影依赖 DOM 旋转。 */
.workflow-minimap__node--condition { transform:translate(-50%,-50%)!important;clip-path:polygon(50% 0,100% 50%,50% 100%,0 50%); }
@media (prefers-reduced-motion: reduce) { .workflow-visual-node,.workflow-canvas :deep(.vue-flow__edge-path),.workflow-canvas--focus :deep(.vue-flow__controls),.workflow-canvas--focus .workflow-minimap { transition:none; } }
</style>
