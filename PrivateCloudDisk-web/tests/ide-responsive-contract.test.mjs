/**
 * [IDE-RESP-2026-08] 三类 IDE 共享响应式布局的回归契约。
 * 真实设备交互留给已登录 E2E 环境验收。
 */
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const root = new URL('../src/', import.meta.url)
const read = (path) => readFile(new URL(path, root), 'utf8')

test('响应式样式集中在独立 IDE 文件且覆盖五档断点', async () => {
  const css = await read('assets/ide-responsive.css')
  const lines = css.split(/\r?\n/).length
  assert.ok(lines >= 400, `响应式 CSS 应不少于 400 行，当前 ${lines} 行`)
  for (const breakpoint of ['85.4375rem', '64.0625rem', '48rem', '30rem', '29.9375rem']) assert.ok(css.includes(breakpoint), `缺少断点 ${breakpoint}`)
  assert.match(css, /\.pcd-ide-responsive/)
  assert.match(css, /touch-action: manipulation/)
  assert.match(css, /prefers-reduced-motion/)
})

test('插件 IDE 在窄屏使用抽屉遮罩与更多操作，而不删除保存发布能力', async () => {
  const page = await read('views/plugins/PluginIdeView.vue')
  const shell = await read('components/plugins/ide/IdeShell.vue')
  assert.match(page, /mobile-panel-open/)
  assert.match(page, /function syncResponsiveLayout\(\)/)
  assert.match(page, /function toggleDrawer/)
  assert.match(page, /更多插件操作/)
  assert.match(page, /publish\(\); mobileMoreOpen = false/)
  assert.match(shell, /ide-shell__mobile-scrim/)
  assert.match(shell, /close-mobile-panels/)
})

test('工作流 IDE 在窄屏收纳三面板，节点点击仍能进入属性配置', async () => {
  const page = await read('views/workflows/WorkflowEditorView.vue')
  const shell = await read('components/workflows/WorkflowIdeShell.vue')
  assert.match(page, /mobilePanelOpen/)
  assert.match(page, /function closeMobilePanels\(\)/)
  assert.match(page, /function syncResponsiveLayout\(\)/)
  assert.match(page, /draft\.ui\.rightCollapsed = false/)
  assert.match(page, /打开输出面板/)
  assert.match(page, /更多工作流操作/)
  assert.match(shell, /workflow-ide-shell__mobile-scrim/)
})

test('Monaco 在手机宽度关闭 minimap，并保留自动换行和 14px 字号', async () => {
  const editor = await read('components/plugins/ide/MonacoEditorWrapper.vue')
  assert.match(editor, /function syncResponsiveEditorOptions\(\)/)
  assert.match(editor, /window\.innerWidth < 768/)
  assert.match(editor, /minimap: \{ enabled: !compactViewport\.value \}/)
  assert.match(editor, /fontSize: compactViewport\.value \? 14 : 14/)
  assert.match(editor, /wordWrap: compactViewport\.value \? 'on' : 'on'/)
})

test('底部面板支持移动端全屏查看，避免长日志阻塞画布', async () => {
  const panel = await read('components/plugins/ide/BottomPanel.vue')
  const css = await read('assets/ide-responsive.css')
  assert.match(panel, /bottom-panel--fullscreen/)
  assert.match(panel, /全屏查看当前面板/)
  assert.match(css, /\.bottom-panel--fullscreen/)
})

test('抽屉始终位于模糊蒙版上方，Monaco 将可用高度交给根编辑器容器', async () => {
  const pluginShell = await read('components/plugins/ide/IdeShell.vue')
  const workflowShell = await read('components/workflows/WorkflowIdeShell.vue')
  const editor = await read('components/plugins/ide/MonacoEditorWrapper.vue')
  const page = await read('views/plugins/PluginIdeView.vue')
  assert.match(pluginShell, /z-index: var\(--ide-z-drawer, 90\)/)
  assert.match(workflowShell, /z-index:var\(--ide-z-drawer,90\)/)
  assert.match(editor, /:style="\{ height \}"/)
  assert.doesNotMatch(editor, /monaco-wrapper__container" :style="\{ height \}"/)
  assert.match(editor, /\.monaco-wrapper__container \{ width: 100%; min-height: 0; flex: 1;/)
  assert.match(page, /\.editor-main \{ min-height: 0; overflow: hidden; \}/)
})
