import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = (path) => readFile(new URL(path, import.meta.url), 'utf8')

test('CloudFlow 终端保留换行、折叠长行且不使用不可信 HTML', async () => {
  const [panel, editor] = await Promise.all([
    readSource('../src/components/plugins/ide/BottomPanel.vue'),
    readSource('../src/views/workflows/WorkflowEditorView.vue'),
  ])
  assert.match(panel, /white-space:\s*pre-wrap/)
  assert.match(panel, /overflow-wrap:\s*anywhere/)
  assert.match(editor, /class="[^"]*workflow-compiler-output[^"]*"/)
  assert.match(editor, /issue\.cliOutput/)
  assert.doesNotMatch(editor, /v-html\s*=/)
  assert.doesNotMatch(editor, /with \$\{key\}/)
  assert.match(editor, /title="workflow\.flow"/)
  assert.match(editor, /syncGraphFromIr/)
})

test('Runtime 结构化诊断会映射为 Monaco marker', async () => {
  const [source, language] = await Promise.all([
    readSource('../src/components/plugins/PluginMonacoEditor.vue'),
    readSource('../src/languages/cloudflow.ts'),
  ])
  assert.match(source, /externalIssues/)
  assert.match(source, /pcd-cloudflow-runtime/)
  assert.match(language, /setMonarchTokensProvider\('cloudflow'/)
  assert.match(source, /MarkerSeverity\.Error/)
})
