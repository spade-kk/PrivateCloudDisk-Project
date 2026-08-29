import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

const root = new URL('../', import.meta.url)
const source = async (path) => readFile(new URL(path, root), 'utf8')

test('AI Markdown renderer uses local mature parsers and an escape-first sanitization boundary', async () => {
  const renderer = await source('src/utils/aiMarkdown.ts')
  assert.match(renderer, /import MarkdownIt from 'markdown-it'/)
  assert.match(renderer, /full as emoji/)
  assert.match(renderer, /markdown-it-task-lists/)
  assert.match(renderer, /html:\s*false/)
  assert.match(renderer, /DOMPurify\.sanitize/)
  assert.match(renderer, /highlight\.js\/lib\/common/)
  assert.match(renderer, /ai_math_inline/)
  assert.match(renderer, /ai_math_block/)
  assert.match(renderer, /data-ai-table/)
  assert.match(renderer, /data-copy-code/)
  assert.match(renderer, /target', '_blank'/)
})

test('AI Markdown component exposes Codex-style reply, code, table and modal actions', async () => {
  const component = await source('src/components/ai/AiMarkdownRenderer.vue')
  assert.match(component, /复制回复/)
  assert.match(component, /data-copy-code/)
  assert.match(component, /data-copy-table/)
  assert.match(component, /data-expand-table/)
  assert.match(component, /role="dialog"/)
  assert.match(component, /import\('katex'\)/)
  assert.match(component, /import\('mermaid'\)/)
  assert.match(component, /securityLevel: 'strict'/)
  assert.match(component, /sanitizeMermaidSvg/)
  assert.match(component, /@keydown\.esc="closeTable"/)
})

test('all Agent-visible Markdown blocks share the same renderer and streaming chunks are preserved', async () => {
  const view = await source('src/views/AiAssistantView.vue')
  const output = await source('src/components/ai/task/OutputBlock.vue')
  const summary = await source('src/components/ai/task/SummaryBlock.vue')
  const thinking = await source('src/components/ai/task/ThinkingBlock.vue')
  const store = await source('src/stores/agentTaskStore.ts')
  assert.match(view, /<AiMarkdownRenderer/)
  assert.match(output, /<AiMarkdownRenderer/)
  assert.match(summary, /<AiMarkdownRenderer/)
  assert.match(thinking, /<AiMarkdownRenderer/)
  assert.match(store, /typeof event\.data\.delta === 'string' \? event\.data\.delta : event\.data\.output_text/)
  assert.doesNotMatch(view, /renderSafeMarkdown\(message\.content\)/)
})
