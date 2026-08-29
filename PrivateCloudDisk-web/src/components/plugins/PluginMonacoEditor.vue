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
import { registerCloudFlowLanguage } from '@/languages/cloudflow'
import { registerCloudFlowCompletion } from '@/languages/cloudflowCompletion'
import { CloudFlowLspMonacoBridge, type CloudFlowLspOptions } from '@/languages/cloudflowLspClient'
import type { CapabilityInfo } from '@/api/modules/workflows'

interface ExternalEditorIssue {
  code: string
  message: string
  line?: number | null
  column?: number | null
  severity?: string
}

const props = withDefaults(defineProps<{
  modelValue: string
  // Web IDE 的多文件项目还会打开 Markdown/JSON 等只读资源；Monaco 接受任意
  // 已注册语言标识，因此这里放宽类型，保留原有 python/javascript/yaml 行为。
  language: string
  title?: string
  height?: string
  readOnly?: boolean
  theme?: 'vs' | 'vs-dark' | 'hc-black'
  capabilities?: CapabilityInfo[]
  externalIssues?: ExternalEditorIssue[]
  /** 配置后由 CloudFlow LS 提供动态能力、类型、Hover 与诊断；未配置时保持静态降级。 */
  cloudflowLsp?: CloudFlowLspOptions | null
}>(), {
  title: '插件源码',
  height: '560px',
  readOnly: false,
  theme: 'vs-dark',
  capabilities: () => [],
  externalIssues: () => [],
  cloudflowLsp: null,
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
let cloudflowLspBridge: CloudFlowLspMonacoBridge | null = null
let cloudflowStaticFallbackRegistered = false

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
  applyExternalMarkers(markers.length)
}

function applyExternalMarkers(localCount = 0) {
  if (!monacoApi || !editor?.getModel()) return
  const model = editor.getModel()!
  const markers = props.externalIssues.map((issue) => {
    const line = Math.min(model.getLineCount(), Math.max(1, issue.line || 1))
    const maxColumn = model.getLineContent(line).length + 1
    const column = Math.min(maxColumn, Math.max(1, issue.column || 1))
    const severity = issue.severity === 'WARNING'
      ? monacoApi!.MarkerSeverity.Warning
      : issue.severity === 'INFO' ? monacoApi!.MarkerSeverity.Info : monacoApi!.MarkerSeverity.Error
    return {
      severity,
      message: `[${issue.code}] ${issue.message}`,
      startLineNumber: line,
      startColumn: column,
      endLineNumber: line,
      endColumn: Math.min(maxColumn, column + 1),
    }
  })
  monacoApi.editor.setModelMarkers(model, 'pcd-cloudflow-runtime', markers)
  issueCount.value = localCount + markers.length
  emit('validation-change', issueCount.value === 0, issueCount.value)
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
  }
  // [CLOUDFLOW-LS-WEB-004] CloudFlow 的语法、AST、类型、DAG 与动态能力诊断
  // 只由 cloudflow-ls（或最终 Runtime 校验）产生；浏览器不再保留正则 DSL Parser，
  // 防止与 Compiler Core 的规则漂移。未配置 LS 时仍保留静态高亮/基础补全与保存前校验。
  setMarkers(markers)
}

function registerCompletionProviders(monaco: typeof import('monaco-editor'), useCloudFlowLsp: boolean): void {
  // [CLOUDFLOW-IDE-HIGHLIGHT-001] CloudFlow 是独立语言，不借用 YAML tokenizer。
  // 语法高亮规则由 GRAMMAR.pest + AST.rs 生成的统一规范转换而来（见 src/languages/cloudflow.ts），
  // 前端不硬编码任何 CloudFlow 高亮正则（需求：禁止前端硬编码语法高亮逻辑）。
  registerCloudFlowLanguage(monaco)
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
  // [CLOUDFLOW-LS-WEB-002] 连接 LS 后，动态能力/符号/签名/诊断全部由 Rust
  // Compiler Core + Capability Hub 产出，避免与 syntax-highlight 的静态基础补全重复。
  // WebSocket 不可用时仍保留生成规范驱动的离线静态降级能力。
  if (!useCloudFlowLsp) {
    registerCloudFlowStaticFallback(monaco)
  }
}

/**
 * [CLOUDFLOW-LS-WEB-002] 仅在没有配置 LS 或 LS 握手失败时注册静态补全。
 * 这样 syntax-highlight 继续承担离线基础提示，而已连接的 LS 不会与它重复
 * 返回动态 Capability Hub、类型或符号结果。
 */
function registerCloudFlowStaticFallback(monaco: typeof import('monaco-editor')): void {
  if (cloudflowStaticFallbackRegistered) return
  cloudflowStaticFallbackRegistered = true
  disposables.push(...registerCloudFlowCompletion(monaco, { capabilities: props.capabilities }))
}

async function initialize(): Promise<void> {
  if (!containerRef.value) return
  const monaco = await loadMonaco()
  monacoApi = monaco
  const useCloudFlowLsp = props.language === 'cloudflow' && !!props.cloudflowLsp?.endpoint
  registerCompletionProviders(monaco, useCloudFlowLsp)
  monaco.languages.typescript.javascriptDefaults.addExtraLib(
    CLIENT_SDK_STUB,
    'pcd-client-sdk.d.ts',
  )
  // Monaco 尚未内置 Python 语义服务；保留 SDK stub 给补全提供器和后端 AST 校验使用。
  void PYCLOUD_STUB
  editor = monaco.editor.create(containerRef.value, {
    value: props.modelValue,
    language: languageId(),
    // [AUDIT FIX 1.2]：[CLOUDFLOW-IDE-002] Monaco 主题跟随宿主 IDE，避免亮色界面
    // 中出现固定深色编辑器；未传入时保持原有 vs-dark 行为，兼容插件开发页。
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
  if (useCloudFlowLsp && props.cloudflowLsp && editor) {
    cloudflowLspBridge = new CloudFlowLspMonacoBridge(monaco as any, editor as any, props.cloudflowLsp)
    cloudflowLspBridge.start()
      .then(() => validateContent(editor?.getValue() || props.modelValue))
      .catch((error) => {
        // LSP 是增强层；无法连接时不影响编辑、保存和后端 Runtime 校验。
        console.warn('[PluginMonacoEditor] CloudFlow LS 不可用，当前使用静态降级能力', error)
        registerCloudFlowStaticFallback(monaco)
      })
  }
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

watch(() => props.theme, (value) => {
  if (monacoApi) monacoApi.editor.setTheme(value)
})

watch(() => props.externalIssues, () => {
  if (editor) validateContent(editor.getValue())
}, { deep: true })

watch(() => props.capabilities, () => {
  // 工作流页面已有 Capability Hub 列表刷新链路；其结果发生变化时通知 LS 立即按
  // 当前 token/tenant/space 重取能力，避免等待五分钟 TTL 后才出现新插件或权限变更。
  if (props.language === 'cloudflow') cloudflowLspBridge?.refreshCapabilities()
}, { deep: true })

onMounted(() => {
  initialize().catch((error) => {
    console.error('[PluginMonacoEditor] 初始化失败', error)
    issueCount.value = 1
    emit('validation-change', false, 1)
  })
})

onBeforeUnmount(() => {
  cloudflowLspBridge?.dispose()
  cloudflowLspBridge = null
  cloudflowStaticFallbackRegistered = false
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
