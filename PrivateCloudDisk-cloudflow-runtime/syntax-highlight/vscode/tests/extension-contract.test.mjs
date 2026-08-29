import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'

const root = new URL('../', import.meta.url)
const source = async (file) => readFile(new URL(file, root), 'utf8')

test('CloudFlow extension registers the LSP execute command only once', async () => {
  const extension = await source('src/extension.js')
  const packageJson = JSON.parse(await source('package.json'))
  assert.equal((extension.match(/registerCommand\('cloudflow\.clearCapabilityCache'/g) || []).length, 0)
  assert.match(extension, /resolveServerCommand/)
  assert.equal(packageJson.contributes.commands.some((item) => item.command === 'cloudflow.clearCapabilityCache'), true)
  assert.equal(packageJson.contributes.configuration.properties['cloudflow.lsp.serverPath'].default, 'bundled')
})

test('packaged DSL grammar stays synchronized with the generated grammar', async () => {
  const generated = await source('../build/cloudflow.tmLanguage.json')
  const packaged = await source('syntaxes/cloudflow.tmLanguage.json')
  assert.equal(packaged, generated)
  assert.match(packaged, /variable\.other\.env\.cloudflow/)
  assert.match(packaged, /constant\.language\.null\.cloudflow/)
})

test('extension packaging script bundles a platform-specific LS and keeps the custom override', async () => {
  const script = await source('scripts/prepare-extension.mjs')
  const ignore = await source('.vscodeignore')
  assert.match(script, /CLOUDFLOW_LS_BIN/)
  assert.match(script, /CLOUDFLOW_LS_TARGET/)
  assert.match(script, /bin.*bundledPlatformDirectory/)
  assert.doesNotMatch(ignore, /^bin\//m)
  assert.equal(existsSync(new URL('scripts/prepare-extension.mjs', root)), true)
})
