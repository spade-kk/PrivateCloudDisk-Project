<template>
  <section class="ai-markdown-renderer" :class="{ 'is-streaming': streaming }">
    <div v-if="!content && streaming" class="ai-markdown-placeholder" aria-live="polite">
      <i class="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i>
      正在生成回复…
    </div>
    <div
      v-else
      ref="contentRef"
      class="ai-markdown-content"
      v-html="renderedHtml"
      @click="onContentClick"
    ></div>

    <div v-if="showActions && content && !streaming" class="ai-response-actions" aria-label="回复操作">
      <button type="button" title="复制完整回复" aria-label="复制完整回复" @click="emit('copy', content)">
        <i class="fa fa-copy" aria-hidden="true"></i>
        <span>复制回复</span>
      </button>
    </div>

    <Teleport to="body">
      <Transition name="ai-modal-fade">
        <div
          v-if="expandedTableHtml"
          class="ai-table-modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="ai-table-modal-title"
          tabindex="-1"
          @click.self="closeTable"
          @keydown.esc="closeTable"
        >
          <div class="ai-table-modal-card">
            <header class="ai-table-modal-header">
              <div>
                <span class="ai-table-modal-kicker">AI 回复</span>
                <h2 id="ai-table-modal-title">表格预览</h2>
              </div>
              <div class="ai-table-modal-actions">
                <button type="button" title="复制表格" aria-label="复制表格" @click="emit('copy', expandedTableText)">
                  <i class="fa fa-copy" aria-hidden="true"></i>
                  <span>复制</span>
                </button>
                <button type="button" title="关闭表格预览" aria-label="关闭表格预览" @click="closeTable">
                  <i class="fa fa-times" aria-hidden="true"></i>
                </button>
              </div>
            </header>
            <div class="ai-table-modal-body" v-html="expandedTableHtml"></div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { renderAiMarkdown, sanitizeAiMarkdownHtml, sanitizeMermaidSvg, tableToText } from '@/utils/aiMarkdown'
import { injectHighlightTheme } from '@/utils/HighlightCdn'

const props = withDefaults(defineProps<{
  content: string
  streaming?: boolean
  showActions?: boolean
}>(), {
  streaming: false,
  showActions: true,
})

const emit = defineEmits<{ copy: [value: string] }>()
const contentRef = ref<HTMLElement | null>(null)
const renderedHtml = ref('')
const expandedTableHtml = ref('')
const expandedTableText = ref('')
let renderSequence = 0
let mermaidSequence = 0

watch(() => props.content, () => {
  renderedHtml.value = renderAiMarkdown(props.content)
  void enhanceRenderedContent()
}, { immediate: true })

async function enhanceRenderedContent(): Promise<void> {
  const sequence = ++renderSequence
  await nextTick()
  if (sequence !== renderSequence || !contentRef.value) return
  await renderFormulas(contentRef.value, sequence)
  await renderMermaid(contentRef.value, sequence)
}

async function renderFormulas(root: HTMLElement, sequence: number): Promise<void> {
  const formulaNodes = [...root.querySelectorAll<HTMLElement>('[data-math]')]
  if (!formulaNodes.length) return
  try {
    const katex = (await import('katex')).default
    if (sequence !== renderSequence) return
    for (const node of formulaNodes) {
      const source = node.dataset.math || ''
      try {
        katex.render(source, node, {
          displayMode: node.dataset.mathDisplay === 'true',
          throwOnError: false,
          trust: false,
          output: 'htmlAndMathml',
        })
        node.innerHTML = sanitizeAiMarkdownHtml(node.innerHTML)
      } catch {
        node.textContent = source
      }
    }
  } catch {
    formulaNodes.forEach((node) => { node.classList.add('ai-math-fallback') })
  }
}

async function renderMermaid(root: HTMLElement, sequence: number): Promise<void> {
  const blocks = [...root.querySelectorAll<HTMLElement>('[data-ai-mermaid]')]
  if (!blocks.length) return
  try {
    const mermaid = (await import('mermaid')).default
    mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: document.documentElement.classList.contains('dark') ? 'dark' : 'default' })
    for (const block of blocks) {
      if (sequence !== renderSequence) return
      const source = block.querySelector('pre code')?.textContent || ''
      const canvas = block.querySelector<HTMLElement>('.ai-mermaid-canvas')
      if (!canvas || !source.trim()) continue
      try {
        mermaidSequence += 1
        const result = await mermaid.render(`ai-mermaid-${Date.now()}-${mermaidSequence}`, source)
        canvas.innerHTML = sanitizeMermaidSvg(result.svg)
        block.querySelector<HTMLElement>('.ai-mermaid-source')?.setAttribute('hidden', 'true')
        const status = block.querySelector<HTMLElement>('.ai-mermaid-status')
        if (status) status.textContent = '图表已渲染'
        if (result.bindFunctions) result.bindFunctions(canvas)
      } catch {
        const status = block.querySelector<HTMLElement>('.ai-mermaid-status')
        if (status) status.textContent = '图表无法渲染，已保留 Mermaid 源码'
      }
    }
  } catch {
    blocks.forEach((block) => {
      const status = block.querySelector<HTMLElement>('.ai-mermaid-status')
      if (status) status.textContent = '图表库加载失败，已保留 Mermaid 源码'
    })
  }
}

function onContentClick(event: MouseEvent): void {
  const target = event.target instanceof Element ? event.target : null
  if (!target) return
  const copyCodeButton = target.closest<HTMLElement>('[data-copy-code]')
  if (copyCodeButton) {
    const code = copyCodeButton.closest('[data-ai-code-block]')?.querySelector('code')?.textContent || ''
    emit('copy', code)
    return
  }
  const copyTableButton = target.closest<HTMLElement>('[data-copy-table]')
  if (copyTableButton) {
    emit('copy', tableToText(copyTableButton.closest('[data-ai-table]')?.querySelector('table') || null))
    return
  }
  const expandTableButton = target.closest<HTMLElement>('[data-expand-table]')
  if (expandTableButton) {
    const table = expandTableButton.closest('[data-ai-table]')?.querySelector('table')
    if (!table) return
    expandedTableHtml.value = sanitizeAiMarkdownHtml(table.outerHTML)
    expandedTableText.value = tableToText(table)
  }
}

function closeTable(): void {
  expandedTableHtml.value = ''
  expandedTableText.value = ''
}

function onGlobalKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && expandedTableHtml.value) closeTable()
}

onMounted(async () => {
  injectHighlightTheme('vs2015')
  window.addEventListener('keydown', onGlobalKeydown) 
})
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKeydown))
</script>

<style scoped>
.ai-markdown-renderer{min-width:0;color:inherit}.ai-markdown-placeholder{display:flex;align-items:center;gap:8px;color:var(--ai-markdown-muted,#64748b);font-size:13px}.ai-markdown-placeholder i{color:#2563eb}.ai-markdown-content{min-width:0;overflow-wrap:anywhere;font-size:14px;line-height:1.78}.ai-markdown-content :deep(p){margin:0 0 12px}.ai-markdown-content :deep(p:last-child){margin-bottom:0}.ai-markdown-content :deep(h1),.ai-markdown-content :deep(h2),.ai-markdown-content :deep(h3),.ai-markdown-content :deep(h4),.ai-markdown-content :deep(h5),.ai-markdown-content :deep(h6){margin:24px 0 10px;color:inherit;font-weight:760;line-height:1.3;scroll-margin-top:16px}.ai-markdown-content :deep(h1){padding-bottom:9px;border-bottom:1px solid rgba(148,163,184,.24);font-size:1.72em;letter-spacing:-.025em}.ai-markdown-content :deep(h2){padding-bottom:7px;border-bottom:1px solid rgba(148,163,184,.18);font-size:1.42em}.ai-markdown-content :deep(h3){font-size:1.2em}.ai-markdown-content :deep(h4){font-size:1.08em}.ai-markdown-content :deep(h5),.ai-markdown-content :deep(h6){font-size:1em}.ai-markdown-content :deep(a){color:#2563eb;text-decoration:none}.ai-markdown-content :deep(a:hover){text-decoration:underline}.ai-markdown-content :deep(strong){font-weight:750}.ai-markdown-content :deep(blockquote){margin:14px 0;padding:4px 0 4px 16px;border-left:3px solid #93c5fd;color:var(--ai-markdown-muted,#475569)}.ai-markdown-content :deep(hr){margin:22px 0;border:0;border-top:1px solid rgba(148,163,184,.28)}.ai-markdown-content :deep(ul),.ai-markdown-content :deep(ol){margin:8px 0 14px;padding-left:24px}.ai-markdown-content :deep(li){margin:4px 0;padding-left:3px}.ai-markdown-content :deep(li > p){margin:0}.ai-markdown-content :deep(.contains-task-list){padding-left:0;list-style:none}.ai-markdown-content :deep(.task-list-item){display:flex;align-items:flex-start;gap:8px}.ai-markdown-content :deep(.task-list-item input){width:15px;height:15px;margin-top:5px;accent-color:#2563eb}.ai-markdown-content :deep(img){max-width:100%;border-radius:9px}.ai-markdown-content :deep(code){border-radius:5px;background:rgba(148,163,184,.16);padding:2px 5px;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:.88em}.ai-markdown-content :deep(.ai-code-block){position:relative;margin:14px 0;overflow:hidden;border:1px solid rgba(148,163,184,.2);border-radius:10px;background:#0f172a;color:#e2e8f0}.ai-markdown-content :deep(.ai-code-toolbar){display:flex;align-items:center;justify-content:space-between;padding:7px 10px;border-bottom:1px solid rgba(226,232,240,.12);color:#94a3b8;font-size:11px}.ai-markdown-content :deep(.ai-code-toolbar button),.ai-markdown-content :deep(.ai-table-toolbar button){border:0;border-radius:5px;background:transparent;padding:4px 7px;color:inherit;font-size:11px;cursor:pointer}.ai-markdown-content :deep(.ai-code-toolbar button:hover),.ai-markdown-content :deep(.ai-table-toolbar button:hover){background:rgba(148,163,184,.18);color:#fff}.ai-markdown-content :deep(.ai-code-content){max-width:100%;margin:0;overflow:auto;padding:13px 15px;background:transparent;color:#e2e8f0;font:12px/1.65 ui-monospace,SFMono-Regular,Menlo,monospace;white-space:pre}.ai-markdown-content :deep(.ai-code-content code){padding:0;background:transparent;color:inherit}.ai-markdown-content :deep(.ai-table-shell){margin:14px 0;border:1px solid rgba(148,163,184,.24);border-radius:10px;background:rgba(148,163,184,.045)}.ai-markdown-content :deep(.ai-table-toolbar){display:flex;align-items:center;justify-content:space-between;padding:6px 9px;color:var(--ai-markdown-muted,#64748b);font-size:11px}.ai-markdown-content :deep(.ai-table-actions){display:flex;gap:3px}.ai-markdown-content :deep(.ai-table-scroll){max-width:100%;overflow:auto}.ai-markdown-content :deep(table){width:max-content;min-width:100%;border-collapse:collapse;font-size:12px}.ai-markdown-content :deep(th),.ai-markdown-content :deep(td){padding:8px 11px;border:1px solid rgba(148,163,184,.22);text-align:left;vertical-align:top}.ai-markdown-content :deep(th){background:rgba(148,163,184,.1);font-weight:700}.ai-markdown-content :deep(tr:nth-child(even) td){background:rgba(148,163,184,.035)}.ai-markdown-content :deep(.ai-mermaid){margin:14px 0;border:1px solid rgba(148,163,184,.22);border-radius:10px;background:rgba(148,163,184,.05);padding:10px}.ai-markdown-content :deep(.ai-mermaid-canvas){display:flex;min-height:60px;align-items:center;justify-content:center;overflow:auto}.ai-markdown-content :deep(.ai-mermaid-canvas svg){max-width:100%;height:auto}.ai-markdown-content :deep(.ai-mermaid-source){margin:8px 0 0;overflow:auto;border-radius:7px;background:rgba(15,23,42,.85);padding:9px;color:#e2e8f0;font:11px/1.6 ui-monospace,SFMono-Regular,Menlo,monospace}.ai-markdown-content :deep(.ai-mermaid-status){margin:7px 0 0;color:var(--ai-markdown-muted,#64748b);font-size:11px}.ai-markdown-content :deep(.ai-math){overflow-x:auto}.ai-markdown-content :deep(.ai-math-inline){display:inline-block;vertical-align:middle}.ai-markdown-content :deep(.ai-math-block){margin:16px 0;padding:8px;line-height:1.5;text-align:center}.ai-markdown-content :deep(.ai-math-fallback){font-family:ui-monospace,SFMono-Regular,Menlo,monospace;color:var(--ai-markdown-muted,#64748b)}.ai-response-actions{display:flex;gap:4px;margin-top:12px;padding-top:9px;border-top:1px solid rgba(148,163,184,.18)}.ai-response-actions button{display:inline-flex;align-items:center;gap:6px;border:0;border-radius:6px;background:transparent;padding:5px 7px;color:var(--ai-markdown-muted,#64748b);font-size:11px;cursor:pointer}.ai-response-actions button:hover{background:rgba(148,163,184,.12);color:#2563eb}.ai-table-modal{position:fixed;z-index:3000;inset:0;display:grid;place-items:center;background:rgba(15,23,42,.54);backdrop-filter:blur(8px);padding:24px}.ai-table-modal-card{display:flex;width:min(1120px,100%);max-height:min(82vh,760px);flex-direction:column;overflow:hidden;border:1px solid rgba(148,163,184,.25);border-radius:16px;background:var(--el-bg-color,#fff);box-shadow:0 24px 80px rgba(15,23,42,.24);color:var(--el-text-color-primary,#0f172a)}.ai-table-modal-header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:16px 18px;border-bottom:1px solid rgba(148,163,184,.2)}.ai-table-modal-kicker{display:block;color:#64748b;font-size:10px;letter-spacing:.08em;text-transform:uppercase}.ai-table-modal-header h2{margin:4px 0 0;font-size:16px}.ai-table-modal-actions{display:flex;align-items:center;gap:5px}.ai-table-modal-actions button{display:inline-flex;align-items:center;gap:6px;border:0;border-radius:7px;background:transparent;padding:7px 9px;color:#64748b;cursor:pointer}.ai-table-modal-actions button:hover{background:rgba(148,163,184,.14);color:#2563eb}.ai-table-modal-body{min-height:0;overflow:auto;padding:18px}.ai-table-modal-body :deep(table){width:max-content;min-width:100%;border-collapse:collapse;font-size:13px}.ai-table-modal-body :deep(th),.ai-table-modal-body :deep(td){padding:10px 13px;border:1px solid rgba(148,163,184,.25);text-align:left;vertical-align:top}.ai-table-modal-body :deep(th){background:rgba(148,163,184,.1)}.dark .ai-table-modal-card{background:#111827;color:#e5e7eb}.dark .ai-markdown-content :deep(a){color:#60a5fa}.dark .ai-markdown-content :deep(blockquote){border-left-color:#3b82f6;color:#94a3b8}.dark .ai-markdown-content :deep(.ai-table-shell){background:rgba(30,41,59,.38)}@media (max-width:640px){.ai-markdown-content{font-size:13px}.ai-table-modal{padding:10px}.ai-table-modal-card{max-height:90vh;border-radius:13px}.ai-table-modal-header{padding:13px}.ai-table-modal-body{padding:12px}.ai-table-modal-actions button span{display:none}}@media (prefers-reduced-motion:reduce){.ai-modal-fade-enter-active,.ai-modal-fade-leave-active{transition:none!important}}
</style>
