<template>
  <section class="plugin-editor-shell" :class="{ 'is-fullscreen': fullscreen }">
    <header class="plugin-editor-toolbar">
      <div class="flex min-w-0 items-center gap-2">
        <span class="h-2.5 w-2.5 rounded-full bg-success"></span>
        <strong class="truncate text-sm text-neutral-700">{{ title }}</strong>
        <span class="rounded bg-neutral-100 px-2 py-1 text-[11px] font-semibold uppercase text-neutral-500">
          {{ language }}
        </span>
      </div>
      <div class="flex items-center gap-2">
        <span class="hidden text-xs text-neutral-400 sm:inline">
          {{ issueCount ? `${issueCount} 个问题` : '语法检查通过' }}
        </span>
        <button class="editor-tool-button" type="button" @click="formatDocument">
          <i class="fa fa-magic"></i><span>格式化</span>
        </button>
        <button class="editor-tool-button" type="button" @click="fullscreen = !fullscreen">
          <i :class="fullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i>
          <span>{{ fullscreen ? '退出全屏' : '全屏' }}</span>
        </button>
      </div>
    </header>
    <div ref="containerRef" class="plugin-editor-container" :style="{ height }"></div>
    <footer class="plugin-editor-status">
      <span>Ln {{ cursor.lineNumber }}, Col {{ cursor.column }}</span>
      <span>UTF-8</span>
      <span>{{ readOnly ? '只读' : '可编辑' }}</span>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { parseDocument } from 'yaml'
import { loadMonaco } from '@/utils/monacoLoader'
import type { CapabilityInfo } from '@/api/modules/workflows'

const props = withDefaults(defineProps<{
  modelValue: string
  // Web IDE 的多文件项目还会打开 Markdown/JSON 等只读资源；Monaco 接受任意
  // 已注册语言标识，因此这里放宽类型，保留原有 python/javascript/yaml 行为。
  language: string
  title?: string
  height?: string
  readOnly?: boolean
  capabilities?: CapabilityInfo[]
}>(), {
  title: '插件源码',
  height: '560px',
  readOnly: false,
  capabilities: () => [],
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'validation-change': [valid: boolean, issues: number]
}>()

const containerRef = ref<HTMLElement | null>(null)
const fullscreen = ref(false)
const issueCount = ref(0)
const cursor = ref({ lineNumber: 1, column: 1 })
let monacoApi: typeof import('monaco-editor') | null = null
let editor: import('monaco-editor').editor.IStandaloneCodeEditor | null = null
let changeGuard = false
const disposables: Array<{ dispose(): void }> = []

const PYCLOUD_STUB = `
from typing import Any, Dict, List, Optional
class FileAPI:
    def read(self, file_id: str) -> bytes: ...
    def write_pre_activation(self, content: bytes) -> None: ...
    def update_metadata(self, values: Dict[str, Any]) -> None: ...
    def move(self, target_node_id: str) -> None: ...
class SpaceAPI:
    def context(self) -> Dict[str, Any]: ...
    def members(self) -> List[Dict[str, Any]]: ...
class Logger:
    def info(self, message: str, fields: Optional[Dict[str, Any]] = None) -> None: ...
    def warning(self, message: str, fields: Optional[Dict[str, Any]] = None) -> None: ...
file: FileAPI
space: SpaceAPI
log: Logger
`

const CLIENT_SDK_STUB = `
declare namespace plugin {
  namespace file {
    function read(options: { fileId: string }): Promise<ArrayBuffer>
    function upload(options: { name: string; content: ArrayBuffer }): Promise<{ fileId: string }>
  }
  namespace ui { function show(view: unknown): Promise<void> }
  namespace clipboard { function write(text: string): Promise<void> }
  namespace system { function notify(title: string, body: string): Promise<void> }
}
`

function languageId(): string {
  return props.language === 'yaml' ? 'yaml' : props.language
}

function setMarkers(markers: import('monaco-editor').editor.IMarkerData[]) {
  if (!monacoApi || !editor?.getModel()) return
  monacoApi.editor.setModelMarkers(editor.getModel()!, 'pcd-plugin-validation', markers)
  issueCount.value = markers.length
  emit('validation-change', markers.length === 0, markers.length)
}

function validateContent(value: string): void {
  if (!monacoApi || !editor?.getModel()) return
  const markers: import('monaco-editor').editor.IMarkerData[] = []
  if (props.language === 'python') {
    const forbidden = [
      { expression: /^\s*(?:from|import)\s+(os|sys|subprocess|socket|shutil)\b/m, message: '沙盒禁止导入系统、进程、网络或高危文件模块' },
      { expression: /\b(eval|exec|compile)\s*\(/m, message: '沙盒禁止动态执行代码' },
      { expression: /__import__|__subclasses__|__globals__/m, message: '沙盒禁止使用反射逃逸入口' },
    ]
    forbidden.forEach(({ expression, message }) => {
      const match = expression.exec(value)
      if (!match) return
      const before = value.slice(0, match.index)
      const line = before.split('\n').length
      const column = match.index - before.lastIndexOf('\n')
      markers.push({
        severity: monacoApi!.MarkerSeverity.Error,
        message,
        startLineNumber: line,
        startColumn: column,
        endLineNumber: line,
        endColumn: column + Math.max(1, match[0].length),
      })
    })
    if (!/\bdef\s+(preprocess|on_available|main)\s*\(/.test(value)) {
      markers.push({
        severity: monacoApi.MarkerSeverity.Warning,
        message: '建议至少声明 preprocess、on_available 或 main 入口函数',
        startLineNumber: 1, startColumn: 1, endLineNumber: 1, endColumn: 2,
      })
    }
  } else if (props.language === 'yaml') {
    const document = parseDocument(value, { prettyErrors: false })
    document.errors.forEach((error: any) => {
      const position = error.linePos?.[0] ?? { line: 1, col: 1 }
      markers.push({
        severity: monacoApi!.MarkerSeverity.Error,
        message: error.message,
        startLineNumber: position.line,
        startColumn: position.col,
        endLineNumber: position.line,
        endColumn: position.col + 1,
      })
    })
  } else if (props.language === 'cloudflow') {
    // [CLOUDFLOW-DSL-001] 前端只做轻量即时反馈，完整语法、能力和 DAG 语义由后端校验。
    if (!/^\s*workflow\s+[A-Za-z][A-Za-z0-9_]*\s*\{/m.test(value)) {
      markers.push({ severity: monacoApi.MarkerSeverity.Error, message: 'CloudFlow DSL 必须以 workflow <Name> { 开始', startLineNumber: 1, startColumn: 1, endLineNumber: 1, endColumn: 2 })
    }
    if ((value.match(/\{/g) || []).length !== (value.match(/\}/g) || []).length) {
      markers.push({ severity: monacoApi.MarkerSeverity.Error, message: 'CloudFlow DSL 的大括号未配对', startLineNumber: 1, startColumn: 1, endLineNumber: 1, endColumn: 2 })
    }
    const lines = value.split(/\r?\n/)
    lines.forEach((line, index) => {
      if (/^\s*apiVersion:|^\s*kind:|^\s*spec:/.test(line)) {
        markers.push({ severity: monacoApi!.MarkerSeverity.Error, message: '旧 YAML DSL 已停止支持，请改写为 CloudFlow DSL', startLineNumber: index + 1, startColumn: 1, endLineNumber: index + 1, endColumn: line.length + 1 })
      }
      if (/^\s*step\s+/.test(line) && !/^\s*step\s+[a-z][a-z0-9_]*\s+uses\s+"[^"]+"/.test(line)) {
        markers.push({ severity: monacoApi!.MarkerSeverity.Error, message: 'step 语法应为 step <id> uses "<capability>" {', startLineNumber: index + 1, startColumn: 1, endLineNumber: index + 1, endColumn: line.length + 1 })
      }
    })
  }
  setMarkers(markers)
}

function registerCompletionProviders(monaco: typeof import('monaco-editor')): void {
  if (!monaco.languages.getLanguages().some((language) => language.id === 'cloudflow')) {
    monaco.languages.register({ id: 'cloudflow' })
  }
  disposables.push(monaco.languages.registerCompletionItemProvider('python', {
    triggerCharacters: ['.', '('],
    provideCompletionItems: () => ({
      suggestions: [
        {
          label: 'pycloud.file.read',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'pycloud.file.read(${1:file_id})',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: '在授权范围内读取文件内容。',
        },
        {
          label: 'preprocess',
          kind: monaco.languages.CompletionItemKind.Snippet,
          insertText: 'def preprocess(context):\\n    ${1:# 仅在激活前允许回写内容}\\n    return {"modified": False}',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: 'file.content.ready 生命周期入口。',
        },
        {
          label: 'on_available',
          kind: monaco.languages.CompletionItemKind.Snippet,
          insertText: 'def on_available(context):\\n    ${1:# 文件已激活，只允许元数据与通知操作}\\n    return {}',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: 'file.available 生命周期入口。',
        },
      ],
    }),
  }))
  disposables.push(monaco.languages.registerCompletionItemProvider('yaml', {
    triggerCharacters: [':', ' '],
    provideCompletionItems: () => ({
      suggestions: props.capabilities.map((capability) => ({
        label: capability.capabilityKey,
        kind: monaco.languages.CompletionItemKind.Function,
        insertText: capability.capabilityKey,
        documentation: capability.description,
      })),
    }),
  }))
  disposables.push(monaco.languages.registerCompletionItemProvider('cloudflow', {
    triggerCharacters: [' ', '"'],
    provideCompletionItems: () => ({
      suggestions: [
        { label: 'workflow', kind: monaco.languages.CompletionItemKind.Keyword, insertText: 'workflow ${1:Name} {\n    trigger: manual()\n    ${2}\n}', insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet, documentation: '定义 CloudFlow 工作流。' },
        { label: 'step', kind: monaco.languages.CompletionItemKind.Keyword, insertText: 'step ${1:step_id} uses "${2:capability}" {\n    ${3}\n}', insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet, documentation: '调用 Capability Hub 能力。' },
        ...props.capabilities.map((capability) => ({ label: capability.capabilityKey, kind: monaco.languages.CompletionItemKind.Function, insertText: capability.capabilityKey, documentation: capability.description })),
      ],
    }),
  }))
}

async function initialize(): Promise<void> {
  if (!containerRef.value) return
  const monaco = await loadMonaco()
  monacoApi = monaco
  registerCompletionProviders(monaco)
  monaco.languages.typescript.javascriptDefaults.addExtraLib(
    CLIENT_SDK_STUB,
    'pcd-client-sdk.d.ts',
  )
  // Monaco 尚未内置 Python 语义服务；保留 SDK stub 给补全提供器和后端 AST 校验使用。
  void PYCLOUD_STUB
  editor = monaco.editor.create(containerRef.value, {
    value: props.modelValue,
    language: languageId(),
    theme: 'vs-dark',
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
    validateContent(value)
  }))
  disposables.push(editor.onDidChangeCursorPosition((event) => {
    cursor.value = event.position
  }))
  validateContent(props.modelValue)
}

function formatDocument(): void {
  editor?.getAction('editor.action.formatDocument')?.run()
}

watch(() => props.modelValue, (value) => {
  if (!editor || editor.getValue() === value) return
  changeGuard = true
  editor.setValue(value)
  changeGuard = false
  validateContent(value)
})

watch(() => props.language, (value) => {
  if (editor?.getModel() && monacoApi) {
    monacoApi.editor.setModelLanguage(editor.getModel()!, value)
    validateContent(editor.getValue())
  }
})

onMounted(() => {
  initialize().catch((error) => {
    console.error('[PluginMonacoEditor] 初始化失败', error)
    issueCount.value = 1
    emit('validation-change', false, 1)
  })
})

onBeforeUnmount(() => {
  disposables.splice(0).forEach((disposable) => disposable.dispose())
  editor?.dispose()
  editor = null
})
</script>

<style scoped>
.plugin-editor-shell {
  overflow: hidden;
  border: 1px solid #273244;
  border-radius: 14px;
  background: #111827;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.2);
}
.plugin-editor-shell.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 120;
  border-radius: 0;
}
.plugin-editor-shell.is-fullscreen .plugin-editor-container {
  height: calc(100dvh - 88px) !important;
}
.plugin-editor-toolbar,
.plugin-editor-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 0 14px;
  color: #94a3b8;
  background: #172033;
}
.plugin-editor-toolbar { border-bottom: 1px solid #273244; }
.plugin-editor-status {
  min-height: 28px;
  justify-content: flex-end;
  gap: 16px;
  border-top: 1px solid #273244;
  font-size: 11px;
}
.editor-tool-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 9px;
  border-radius: 8px;
  color: #cbd5e1;
  font-size: 12px;
  transition: background-color 150ms ease, color 150ms ease;
}
.editor-tool-button:hover { background: #273244; color: #fff; }
.plugin-editor-container { width: 100%; }
</style>
