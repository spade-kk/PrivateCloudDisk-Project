/**
 * [CLOUDFLOW-IDE-TEST-001] 可视化 IDE 的契约回归测试。
 *
 * 此测试不依赖浏览器渲染器：它验证 DSL 唯一事实来源、控制流节点映射和运行时校验
 * 接入没有在重构中被意外删除。完整交互由人工验收和浏览器 E2E 环境继续覆盖。
 */
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const root = new URL('../src/', import.meta.url)
const read = (path) => readFile(new URL(path, root), 'utf8')

test('可视化模型覆盖 CloudFlow V1 控制流节点', async () => {
  const types = await read('types/cloudflowVisual.ts')
  for (const nodeType of ['condition', 'foreach', 'while', 'parallel', 'try', 'wait', 'assert']) {
    assert.match(types, new RegExp(`'${nodeType}'`))
  }
})

test('画布序列化器将控制流结构输出为 CloudFlow DSL', async () => {
  const serializer = await read('utils/cloudflowVisualDsl.ts')
  for (const keyword of ['foreach ', 'while ', 'parallel {', 'try {', 'wait ', 'assert ']) {
    assert.ok(serializer.includes(keyword), `缺少 ${keyword} 的 DSL 映射`)
  }
  assert.match(serializer, /serializeProjectToCloudFlow/)
  assert.match(serializer, /projectFromCloudFlowIr/)
})

test('编辑页只以 Runtime 编译校验后的 IR 反投影画布', async () => {
  const editor = await read('views/workflows/WorkflowEditorView.vue')
  assert.match(editor, /validateWorkflowApi\(source, store\.project as unknown as Record/)
  assert.match(editor, /syncGraphFromIr/)
  assert.match(editor, /if \(!\(await validate\(source\)\)\) return false/)
})

test('画布使用 Vue Flow 的交互小地图且终端支持 DSL 页签', async () => {
  const canvas = await read('components/workflows/WorkflowCanvas.vue')
  const bottomPanel = await read('components/plugins/ide/BottomPanel.vue')
  assert.match(canvas, /MiniMap/)
  assert.match(canvas, /auto-layout/)
  assert.match(bottomPanel, /DSL 预览/)
  assert.match(bottomPanel, /white-space: pre-wrap/)
})

test('Vue Flow 挂载前具备非零尺寸保护，避免画布 0×0 不渲染', async () => {
  const canvas = await read('components/workflows/WorkflowCanvas.vue')
  const shell = await read('components/workflows/WorkflowIdeShell.vue')
  const editor = await read('views/workflows/WorkflowEditorView.vue')
  assert.match(canvas, /v-if="canvasReady"/)
  assert.match(canvas, /new ResizeObserver\(measureCanvas\)/)
  assert.match(canvas, /width:100%;height:100%;min-width:0;min-height:280px/)
  assert.match(shell, /width:100%;height:100%;grid-template-rows/)
  assert.match(editor, /width:100%;height:100%;min-height:calc\(100dvh - 84px\)/)
})

test('DSL 校验请求不会把编辑器快照对象直接发送给后端', async () => {
  const workflowsApi = await read('api/modules/workflows.ts')
  const editor = await read('views/workflows/WorkflowEditorView.vue')
  assert.match(workflowsApi, /normalizeWorkflowDslSource/)
  assert.match(workflowsApi, /dsl: normalizeWorkflowDslSource\(dsl\)/)
  assert.match(editor, /function currentDslSource\(\)/)
  assert.match(editor, /serializeProjectToCloudFlow\(store\.project\)/)
})

test('条件节点使用真实 SVG 轮廓而不是旋转矩形，边和缩略图支持语义化交互', async () => {
  const canvas = await read('components/workflows/WorkflowCanvas.vue')
  assert.match(canvas, /workflow-visual-node__shape/)
  assert.match(canvas, /M160 5 L315 90 L160 175 L5 90 Z/)
  assert.doesNotMatch(canvas, /workflow-visual-node--condition \{[^}]*transform:rotate\(45deg\)/)
  assert.match(canvas, /condition-true/)
  assert.match(canvas, /parallel-branch/)
  assert.match(canvas, /onMiniMapPointerDown/)
})

test('IDE 提供安全图形导出、新手指引及连线属性编辑，不改变 DSL 唯一事实来源', async () => {
  const editor = await read('views/workflows/WorkflowEditorView.vue')
  const canvas = await read('components/workflows/WorkflowCanvas.vue')
  const inspector = await read('components/workflows/WorkflowInspector.vue')
  assert.match(editor, /WorkflowOnboarding/)
  assert.match(editor, /exportCanvas\('png'\)/)
  assert.match(canvas, /function exportSnapshot\(format: 'svg' \| 'png'\)/)
  assert.match(canvas, /function escapeXml/)
  assert.match(inspector, /连线外观独立于 CloudFlow 控制流语义/)
  assert.match(inspector, /lineStyle/)
  assert.match(editor, /function confirmDelete\(\)/)
})
