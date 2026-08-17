<template>
  <section class="monaco-wrapper" :class="{ 'monaco-wrapper--fullscreen': fullscreen }" :aria-label="`${path || '代码'}编辑器`">
    <header class="monaco-wrapper__toolbar">
      <div class="monaco-wrapper__path"><i class="fa fa-file-code-o" aria-hidden="true"></i><span>{{ path || '未命名文件' }}</span><em v-if="dirty">未保存</em></div>
      <div class="monaco-wrapper__tools">
        <span class="monaco-wrapper__issues" role="status" aria-live="polite">{{ issues.length ? `${issues.length} 个问题` : '校验通过' }}</span>
        <button class="monaco-wrapper__tool" type="button" :disabled="readOnly" title="格式化文档" @click="formatDocument"><i class="fa fa-magic" aria-hidden="true"></i><span class="hidden sm:inline">格式化</span></button>
        <button class="monaco-wrapper__tool" type="button" :title="fullscreen ? '退出全屏' : '全屏编辑'" :aria-label="fullscreen ? '退出全屏' : '全屏编辑'" @click="fullscreen = !fullscreen"><i :class="fullscreen ? 'fa fa-compress' : 'fa fa-expand'" aria-hidden="true"></i></button>
      </div>
    </header>
    <div ref="containerRef" class="monaco-wrapper__container" :style="{ height }"></div>
    <footer class="monaco-wrapper__status">
      <span>Ln {{ cursor.lineNumber }}, Col {{ cursor.column }}</span>
      <span>{{ language.toUpperCase() }}</span>
      <span>UTF-8</span>
      <span>{{ readOnly ? '只读' : '可编辑' }}</span>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { parseDocument } from 'yaml'
import { loadMonaco } from '@/utils/monacoLoader'

export interface MonacoCompletionItem {
  label: string
  insertText?: string
  documentation?: string
  kind?: 'method' | 'function' | 'snippet' | 'property' | 'variable'
}

export interface MonacoValidationIssue {
  message: string
  severity: 'error' | 'warning' | 'info'
  line?: number
  column?: number
  path?: string
}

const props = withDefaults(defineProps<{
  modelValue: string
  language: string
  path?: string
  height?: string
  readOnly?: boolean
  theme?: 'vs' | 'vs-dark' | 'hc-black' | string
  dirty?: boolean
  completionItems?: MonacoCompletionItem[]
  extraLibs?: Array<{ content: string; filePath: string }>
  /** 开发 IDE 默认 400ms，避免每次按键都触发昂贵的校验。 */
  validationDebounce?: number
}>(), {
  path: '',
  height: '560px',
  readOnly: false,
  theme: 'vs-dark',
  dirty: false,
  completionItems: () => [],
  extraLibs: () => [],
  validationDebounce: 400,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'validation-change': [valid: boolean, issues: MonacoValidationIssue[]]
  'cursor-change': [position: { lineNumber: number; column: number }]
  ready: []
}>()

const containerRef = ref<HTMLElement | null>(null)
const fullscreen = ref(false)
const cursor = ref({ lineNumber: 1, column: 1 })
const issues = ref<MonacoValidationIssue[]>([])
let monacoApi: typeof import('monaco-editor') | null = null
let editor: import('monaco-editor').editor.IStandaloneCodeEditor | null = null
let changeGuard = false
let validationTimer: ReturnType<typeof setTimeout> | null = null
const disposables: Array<{ dispose(): void }> = []

function severityValue(monaco: typeof import('monaco-editor'), severity: MonacoValidationIssue['severity']) {
  if (severity === 'error') return monaco.MarkerSeverity.Error
  if (severity === 'warning') return monaco.MarkerSeverity.Warning
  return monaco.MarkerSeverity.Info
}

function lineColumn(value: string, offset: number) {
  const before = value.slice(0, Math.max(0, offset))
  const lines = before.split('\n')
  return { line: lines.length, column: (lines[lines.length - 1]?.length || 0) + 1 }
}

/**
 * 本地即时检查仅用于即时反馈，后端 AST/安全校验仍是提交前的最终事实源。
 * 规则与插件沙盒约束保持一致，避免把“编辑器通过”误当作“可安全执行”。
 */
function collectIssues(value: string): MonacoValidationIssue[] {
  const result: MonacoValidationIssue[] = []
  if (props.language === 'python') {
    const rules = [
      { regex: /(^|\n)\s*(?:from|import)\s+(os|sys|subprocess|socket|shutil)\b/gm, message: '沙盒禁止导入系统、进程、网络或高危文件模块' },
      { regex: /\b(eval|exec|compile)\s*\(/gm, message: '沙盒禁止动态执行代码' },
      { regex: /__import__|__subclasses__|__globals__/gm, message: '沙盒禁止使用反射逃逸入口' },
    ]
    rules.forEach(({ regex, message }) => {
      const match = regex.exec(value)
      if (match?.index === undefined) return
      const position = lineColumn(value, match.index)
      result.push({ message, severity: 'error', line: position.line, column: position.column })
    })
    if (!/\b(def\s+(preprocess|on_available|main)|async\s+def\s+(preprocess|on_available|main))\s*\(/.test(value)) {
      result.push({ message: '建议至少声明 preprocess、on_available 或 main 入口函数', severity: 'warning', line: 1, column: 1 })
    }
  } else if (props.language === 'javascript' || props.language === 'typescript') {
    const rules = [
      { regex: /\beval\s*\(/gm, message: '插件沙盒禁止使用 eval' },
      { regex: /\bnew\s+Function\s*\(/gm, message: '插件沙盒禁止动态构造函数' },
      { regex: /document\.write\s*\(/gm, message: '插件沙盒禁止 document.write' },
    ]
    rules.forEach(({ regex, message }) => {
      const match = regex.exec(value)
      if (match?.index === undefined) return
      const position = lineColumn(value, match.index)
      result.push({ message, severity: 'error', line: position.line, column: position.column })
    })
  } else if (props.language === 'yaml') {
    const document = parseDocument(value, { prettyErrors: false })
    document.errors.forEach((error: any) => {
      const position = error.linePos?.[0] ?? { line: 1, col: 1 }
      result.push({ message: error.message, severity: 'error', line: position.line, column: position.col })
    })
  }
  return result
}

function applyIssues(nextIssues: MonacoValidationIssue[]) {
  issues.value = nextIssues
  if (monacoApi && editor?.getModel()) {
    monacoApi.editor.setModelMarkers(editor.getModel()!, 'pcd-plugin-ide', nextIssues.map((issue) => {
      const line = issue.line || 1
      const column = issue.column || 1
      return {
        message: issue.message,
        severity: severityValue(monacoApi!, issue.severity),
        startLineNumber: line,
        startColumn: column,
        endLineNumber: line,
        endColumn: column + 1,
      }
    }))
  }
  emit('validation-change', nextIssues.every((issue) => issue.severity !== 'error'), nextIssues)
}

function validateNow(value = editor?.getValue() ?? props.modelValue) {
  if (validationTimer) clearTimeout(validationTimer)
  applyIssues(collectIssues(value))
}

function scheduleValidation(value: string) {
  if (validationTimer) clearTimeout(validationTimer)
  validationTimer = setTimeout(() => applyIssues(collectIssues(value)), props.validationDebounce)
}

function completionKind(monaco: typeof import('monaco-editor'), kind: MonacoCompletionItem['kind']) {
  const kinds = monaco.languages.CompletionItemKind
  if (kind === 'method') return kinds.Method
  if (kind === 'function') return kinds.Function
  if (kind === 'snippet') return kinds.Snippet
  // 不同 Monaco 类型声明版本暴露的枚举成员略有差异，使用安全回退保持
  // 0.56.x 与旧版类型包均可构建；实际运行时优先使用 Property/Variable。
  if (kind === 'property') return (kinds as any).Property ?? (kinds as any).Field ?? (kinds as any).Text ?? 18
  return (kinds as any).Variable ?? (kinds as any).Text ?? 18
}

function registerCompletionProvider(monaco: typeof import('monaco-editor')) {
  if (!props.completionItems.length || !props.language) return
  disposables.push(monaco.languages.registerCompletionItemProvider(props.language, {
    triggerCharacters: ['.', ':', '(', ' '],
    provideCompletionItems: () => ({
      suggestions: props.completionItems.map((item) => ({
        label: item.label,
        insertText: item.insertText || item.label,
        documentation: item.documentation,
        kind: completionKind(monaco, item.kind || 'function'),
        insertTextRules: item.kind === 'snippet' ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
      })),
    }),
  }))
}

async function initialize() {
  if (!containerRef.value) return
  const monaco = await loadMonaco()
  monacoApi = monaco
  registerCompletionProvider(monaco)
  if (props.extraLibs.length && (props.language === 'javascript' || props.language === 'typescript')) {
    props.extraLibs.forEach((lib) => monaco.languages.typescript.javascriptDefaults.addExtraLib(lib.content, lib.filePath))
  }
  editor = monaco.editor.create(containerRef.value, {
    value: props.modelValue,
    language: props.language,
    theme: props.theme,
    readOnly: props.readOnly,
    automaticLayout: true,
    minimap: { enabled: true },
    fontSize: 14,
    lineHeight: 22,
    tabSize: 2,
    insertSpaces: true,
    scrollBeyondLastLine: false,
    smoothScrolling: true,
    wordWrap: 'on',
    padding: { top: 14, bottom: 14 },
    fixedOverflowWidgets: true,
  })
  disposables.push(editor.onDidChangeModelContent(() => {
    if (changeGuard || !editor) return
    const value = editor.getValue()
    emit('update:modelValue', value)
    scheduleValidation(value)
  }))
  disposables.push(editor.onDidChangeCursorPosition((event) => {
    cursor.value = event.position
    emit('cursor-change', { lineNumber: event.position.lineNumber, column: event.position.column })
  }))
  validateNow(props.modelValue)
  emit('ready')
}

function formatDocument() { void editor?.getAction('editor.action.formatDocument')?.run() }
function focus() { editor?.focus() }
function getValue() { return editor?.getValue() ?? props.modelValue }

watch(() => props.modelValue, (value) => {
  if (!editor || editor.getValue() === value) return
  changeGuard = true
  editor.setValue(value)
  changeGuard = false
  validateNow(value)
})
watch(() => props.language, (value) => {
  if (!editor?.getModel() || !monacoApi) return
  monacoApi.editor.setModelLanguage(editor.getModel()!, value)
  validateNow(editor.getValue())
})
watch(() => props.theme, (value) => { if (monacoApi && value) monacoApi.editor.setTheme(value) })

defineExpose({ focus, formatDocument, getValue, validateNow })

onMounted(() => {
  initialize().catch((error) => {
    const message = error?.message || 'Monaco 编辑器初始化失败'
    applyIssues([{ message, severity: 'error', line: 1, column: 1 }])
    console.error('[MonacoEditorWrapper] 初始化失败', error)
  })
})
onBeforeUnmount(() => {
  if (validationTimer) clearTimeout(validationTimer)
  disposables.splice(0).forEach((disposable) => disposable.dispose())
  editor?.dispose()
  editor = null
})
</script>

<style scoped>
.monaco-wrapper { display: flex; min-height: 0; flex-direction: column; overflow: hidden; border: 1px solid #273244; border-radius: 12px; background: #111827; color: #cbd5e1; }
.monaco-wrapper--fullscreen { position: fixed; inset: 0; z-index: 130; border-radius: 0; }
.monaco-wrapper--fullscreen .monaco-wrapper__container { height: calc(100dvh - 80px) !important; }
.monaco-wrapper__toolbar,
.monaco-wrapper__status { display: flex; min-height: 40px; align-items: center; justify-content: space-between; gap: 10px; padding: 0 12px; background: #172033; }
.monaco-wrapper__toolbar { border-bottom: 1px solid #273244; }
.monaco-wrapper__path { display: flex; min-width: 0; align-items: center; gap: 7px; color: #cbd5e1; font-size: 11px; }
.monaco-wrapper__path span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.monaco-wrapper__path i { color: #60a5fa; }
.monaco-wrapper__path em { padding: 2px 5px; border-radius: 4px; background: #78350f; color: #fcd34d; font-size: 9px; font-style: normal; }
.monaco-wrapper__tools { display: flex; align-items: center; gap: 4px; }
.monaco-wrapper__issues { color: #94a3b8; font-size: 10px; }
.monaco-wrapper__tool { display: inline-flex; min-height: 30px; align-items: center; gap: 5px; padding: 0 8px; border-radius: 7px; color: #cbd5e1; font-size: 11px; }
.monaco-wrapper__tool:hover:not(:disabled),
.monaco-wrapper__tool:focus-visible:not(:disabled) { background: #273244; color: #fff; outline: none; }
.monaco-wrapper__tool:disabled { cursor: not-allowed; opacity: .5; }
.monaco-wrapper__container { width: 100%; min-height: 180px; flex: 1; }
.monaco-wrapper__status { justify-content: flex-end; border-top: 1px solid #273244; color: #94a3b8; font-size: 10px; }
@media (prefers-reduced-motion: reduce) { .monaco-wrapper__tool { transition: none; } }
</style>
