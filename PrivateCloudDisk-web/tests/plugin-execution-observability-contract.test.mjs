/** [PLUGIN-EXEC-OBS-001] 前端仅消费 Plugin Service 的真实执行可观测性接口。 */
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const root = new URL('../src/', import.meta.url)
const read = (path) => readFile(new URL(path, root), 'utf8')

test('SDK 覆盖执行详情、日志、审计与受控下载接口，且删除旧伪日志授权路径', async () => {
  const api = await read('api/modules/plugins.ts')
  for (const path of [
    'plugins/executions/${executionId}/logs',
    'plugins/executions/${executionId}/audit-trails',
    'plugins/audit-trails/${auditId}',
    'plugins/executions/${executionId}/logs/download',
  ]) assert.ok(api.includes(path), `缺少接口路径：${path}`)
  assert.doesNotMatch(api, /log-grant/)
})

test('执行详情抽屉通过 Teleport 放置，并保证抽屉层级高于模糊蒙版', async () => {
  const drawer = await read('components/plugins/execution/PluginExecutionDetailDrawer.vue')
  assert.match(drawer, /<Teleport to="body">/)
  assert.match(drawer, /execution-drawer-scrim[\s\S]*z-index: 120/)
  assert.match(drawer, /execution-drawer \{ position: fixed; z-index: 130/)
})

test('Docker 风格日志与摘要/详情审计共用真实 store 数据流', async () => {
  const panel = await read('components/plugins/execution/PluginExecutionDetailPanel.vue')
  const store = await read('stores/pluginExecutionDetailStore.ts')
  const audit = await read('components/plugins/execution/ExecutionAuditTrailViewer.vue')
  assert.match(panel, /ExecutionLogViewer/)
  assert.match(panel, /ExecutionAuditTrailViewer/)
  assert.match(store, /getPluginExecutionLogsApi/)
  assert.match(store, /getPluginExecutionAuditTrailsApi/)
  assert.match(store, /startLogStream/)
  assert.match(audit, /pcd\.plugin-execution\.audit-mode/)
  assert.match(audit, /target_context|输入参数（已脱敏）/)
})
