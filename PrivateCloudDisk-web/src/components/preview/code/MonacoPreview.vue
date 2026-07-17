<template>
  <!-- ============================================================ -->
  <!-- MonacoPreview.vue — 基于 Monaco Editor 的企业级代码预览组件 -->
  <!-- ============================================================ -->
  <!-- Monaco Editor 是 Microsoft 开源的 VS Code 核心编辑器引擎， -->
  <!-- 提供与 VS Code 完全一致的代码编辑和预览体验： -->
  <!--                                                              -->
  <!--   语法高亮      — 内置 60+ 语言的精确语法高亮（TextMate 语法） -->
  <!--   智能提示      — TypeScript/JavaScript/CSS/HTML/JSON 原生 IntelliSense -->
  <!--                   悬停提示（Hover Provider）显示类型、签名、文档 -->
  <!--   代码折叠      — 基于缩进和语法结构的代码折叠 -->
  <!--   括号匹配      — 自动高亮匹配的括号对 -->
  <!--   缩进参考线    — 显示缩进对齐的垂直线 -->
  <!--   小地图        — 代码缩略图导航 -->
  <!--   行号          — 相对/绝对行号显示 -->
  <!--   搜索替换      — 内置搜索（Ctrl+F）和替换（Ctrl+H） -->
  <!--   多光标编辑    — 支持多光标操作（预览模式下禁用） -->
  <!--   差异对比      — 支持 diff 编辑器模式 -->
  <!--   主题切换      — 内置 VS Code Dark/Light/High Contrast 主题 -->
  <!--                                                              -->
  <!-- 组件职责：                                                    -->
  <!--   1. 初始化 Monaco Editor 环境（Web Workers）                 -->
  <!--   2. 根据文件扩展名自动检测语言并配置编辑器                   -->
  <!--   3. 提供工具栏（搜索、跳转到行、自动换行、复制、下载）       -->
  <!--   4. 提供状态栏（语言、行数、字符数、编码、光标位置）         -->
  <!--   5. 对外暴露 API（setContent、getEditor、revealLine 等）     -->
  <!-- ============================================================ -->
  <div class="monaco-preview-root" ref="rootRef">
    <!-- ======================================================== -->
    <!-- 加载状态 -->
    <!-- ======================================================== -->
    <div v-if="loading" class="monaco-loading-overlay">
      <div class="monaco-loading-content">
        <div class="monaco-loading-spinner">
          <div class="spinner-ring"></div>
        </div>
        <h3 class="monaco-loading-title">正在初始化编辑器...</h3>
        <p class="monaco-loading-subtitle">Monaco Editor 加载中</p>
      </div>
    </div>

    <!-- ======================================================== -->
    <!-- 初始化失败状态 -->
    <!-- ======================================================== -->
    <div v-else-if="initError" class="monaco-error-overlay">
      <div class="monaco-error-content">
        <div class="monaco-error-icon">
          <i class="fa fa-exclamation-triangle"></i>
        </div>
        <h3 class="monaco-error-title">编辑器初始化失败</h3>
        <p class="monaco-error-message">{{ initError }}</p>
        <button @click="retryInit" class="monaco-error-retry-btn">
          <i class="fa fa-refresh"></i> 重新加载
        </button>
      </div>
    </div>

    <!-- ======================================================== -->
    <!-- 正常状态 — 编辑器已就绪 -->
    <!-- ======================================================== -->
    <template v-else>
      <!-- ---- 工具栏 ---- -->
      <div class="monaco-toolbar">
        <!-- 左侧：文件信息 -->
        <div class="monaco-toolbar-left">
          <span class="monaco-file-name" :title="fileName">{{ fileName }}</span>
          <span class="monaco-file-ext">{{ fileExtension.toUpperCase() }}</span>
          <span class="monaco-language-label" v-if="detectedLanguage">
            {{ detectedLanguage }}
          </span>
        </div>

        <!-- 右侧：工具按钮 -->
        <div class="monaco-toolbar-right">
          <!-- 搜索（委托给 Monaco 内置搜索） -->
          <button
            @click="triggerMonacoSearch"
            class="monaco-tool-btn"
            title="搜索 (Ctrl+F)"
          >
            <i class="fa fa-search"></i>
          </button>

          <!-- 跳转到行 -->
          <button
            @click="openGoToLine"
            class="monaco-tool-btn"
            title="跳转到行 (Ctrl+G)"
          >
            <i class="fa fa-arrow-right"></i>
          </button>

          <!-- 代码结构导航 -->
          <button
            @click="toggleOutline"
            class="monaco-tool-btn"
            :class="{ active: showOutline }"
            title="代码结构"
          >
            <i class="fa fa-list-ul"></i>
          </button>

          <div class="monaco-toolbar-divider"></div>

          <!-- 自动换行 -->
          <button
            @click="toggleWordWrap"
            class="monaco-tool-btn"
            :class="{ active: wordWrapEnabled }"
            title="自动换行 (Alt+Z)"
          >
            <i class="fa fa-paragraph"></i>
          </button>

          <!-- 小地图 -->
          <button
            @click="toggleMinimap"
            class="monaco-tool-btn"
            :class="{ active: minimapEnabled }"
            title="小地图"
          >
            <i class="fa fa-map-o"></i>
          </button>

          <div class="monaco-toolbar-divider"></div>

          <!-- 复制代码 -->
          <button
            @click="copyCode"
            class="monaco-tool-btn"
            :title="copied ? '已复制' : '复制代码'"
          >
            <i :class="copied ? 'fa fa-check' : 'fa fa-copy'"></i>
          </button>

          <!-- 下载 -->
          <button
            @click="downloadCode"
            class="monaco-tool-btn"
            title="下载"
          >
            <i class="fa fa-download"></i>
          </button>

          <div class="monaco-toolbar-divider"></div>

          <!-- 全屏 -->
          <button
            @click="toggleFullscreen"
            class="monaco-tool-btn"
            title="全屏"
          >
            <i :class="isFullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i>
          </button>
        </div>
      </div>

      <!-- ---- 跳转到行对话框 ---- -->
      <Transition name="goto-slide">
        <div v-if="showGoToLineDialog" class="monaco-goto-dialog">
          <div class="monaco-goto-input-wrapper">
            <input
              ref="gotoInputRef"
              v-model="gotoLineValue"
              type="number"
              :min="1"
              :max="totalLines"
              placeholder="输入行号"
              class="monaco-goto-input"
              @keydown.enter="executeGoToLine"
              @keydown.escape="showGoToLineDialog = false"
            />
            <span class="monaco-goto-hint">共 {{ totalLines }} 行</span>
          </div>
        </div>
      </Transition>

      <!-- ---- 主内容区 ---- -->
      <div class="monaco-body">
        <!-- 代码结构导航侧边栏 -->
        <Transition name="outline-slide">
          <div v-if="showOutline" class="monaco-outline-panel">
            <div class="monaco-outline-header">
              <span>代码结构</span>
              <button @click="showOutline = false" class="monaco-outline-close">
                <i class="fa fa-times"></i>
              </button>
            </div>
            <div class="monaco-outline-content">
              <!-- 函数/方法 -->
              <div v-if="codeSymbols.functions.length > 0" class="monaco-outline-section">
                <div class="monaco-outline-section-title">
                  <i class="fa fa-cube"></i>
                  方法 ({{ codeSymbols.functions.length }})
                </div>
                <div
                  v-for="func in codeSymbols.functions"
                  :key="'func-' + func.line"
                  class="monaco-outline-item"
                  @click="revealLine(func.line)"
                >
                  <span class="monaco-outline-icon monaco-icon-method">ƒ</span>
                  <span class="monaco-outline-name">{{ func.name }}</span>
                  <span class="monaco-outline-line">L{{ func.line }}</span>
                </div>
              </div>

              <!-- 类 -->
              <div v-if="codeSymbols.classes.length > 0" class="monaco-outline-section">
                <div class="monaco-outline-section-title">
                  <i class="fa fa-cubes"></i>
                  类 ({{ codeSymbols.classes.length }})
                </div>
                <div
                  v-for="cls in codeSymbols.classes"
                  :key="'cls-' + cls.line"
                  class="monaco-outline-item"
                  @click="revealLine(cls.line)"
                >
                  <span class="monaco-outline-icon monaco-icon-class">C</span>
                  <span class="monaco-outline-name">{{ cls.name }}</span>
                  <span class="monaco-outline-line">L{{ cls.line }}</span>
                </div>
              </div>

              <!-- 变量 -->
              <div v-if="codeSymbols.variables.length > 0" class="monaco-outline-section">
                <div class="monaco-outline-section-title">
                  <i class="fa fa-tag"></i>
                  变量 ({{ codeSymbols.variables.length }})
                </div>
                <div
                  v-for="v in codeSymbols.variables"
                  :key="'var-' + v.line"
                  class="monaco-outline-item"
                  @click="revealLine(v.line)"
                >
                  <span class="monaco-outline-icon monaco-icon-var">V</span>
                  <span class="monaco-outline-name">{{ v.name }}</span>
                  <span class="monaco-outline-line">L{{ v.line }}</span>
                </div>
              </div>

              <!-- 空状态 -->
              <div
                v-if="
                  codeSymbols.functions.length === 0 &&
                  codeSymbols.classes.length === 0 &&
                  codeSymbols.variables.length === 0
                "
                class="monaco-outline-empty"
              >
                未检测到代码结构
              </div>
            </div>
          </div>
        </Transition>

        <!-- Monaco Editor 挂载容器 -->
        <div
          class="monaco-editor-container"
          ref="editorContainerRef"
        ></div>
      </div>

      <!-- ---- 底部状态栏 ---- -->
      <div class="monaco-status-bar">
        <div class="monaco-status-left">
          <span class="monaco-status-item">
            <i class="fa fa-file-code-o"></i>
            {{ fileExtension.toUpperCase() }}
          </span>
          <span class="monaco-status-item">
            <i class="fa fa-language"></i>
            {{ detectedLanguage }}
          </span>
          <span class="monaco-status-item">
            <i class="fa fa-file"></i>
            {{ formattedFileSize }}
          </span>
          <span class="monaco-status-item">
            <i class="fa fa-code"></i>
            {{ totalLines }} 行
          </span>
          <span class="monaco-status-item">
            <i class="fa fa-text-width"></i>
            {{ totalChars }} 字符
          </span>
        </div>
        <div class="monaco-status-right">
          <span class="monaco-status-item">
            <i class="fa fa-indent"></i>
            {{ detectedIndent }}
          </span>
          <span class="monaco-status-item">
            <i class="fa fa-font"></i>
            UTF-8
          </span>
          <span class="monaco-status-item" v-if="cursorPosition">
            <i class="fa fa-map-marker"></i>
            Ln {{ cursorPosition.lineNumber }}, Col {{ cursorPosition.column }}
          </span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
// ============================================================
// MonacoPreview.vue — 基于 Monaco Editor 的企业级代码预览组件
// ============================================================
// 使用 Microsoft Monaco Editor（VS Code 核心编辑器引擎）提供：
//   - 内置 60+ 语言的精确语法高亮（TextMate 语法）
//   - TypeScript/JavaScript/CSS/HTML/JSON 原生 IntelliSense 悬停提示
//   - 代码折叠、括号匹配、缩进参考线、小地图
//   - 内置搜索（Ctrl+F）、跳转到行（Ctrl+G）
//   - VS Code Dark / Light / High Contrast 主题
// ============================================================

import {
  ref,
  computed,
  watch,
  onMounted,
  onBeforeUnmount,
  nextTick,
  shallowRef,
} from 'vue'
import * as monaco from 'monaco-editor'

// ============================================================
// Monaco Editor Web Workers 配置（Vite 环境）
// ============================================================
// Monaco Editor 使用 Web Workers 进行语法高亮和语言服务，
// 在 Vite 中需要显式配置 Worker 加载路径。
// 通过 ?worker 后缀让 Vite 将 Worker 打包为独立的 chunk。
// ============================================================

// 预加载 Worker 模块（Vite 会在构建时处理 ?worker 导入）
// 使用动态 import 避免在非 Vite 环境中报错
let EditorWorker: new () => Worker
let TsWorker: new () => Worker
let JsonWorker: new () => Worker
let CssWorker: new () => Worker
let HtmlWorker: new () => Worker

/**
 * 动态加载 Monaco Worker 模块
 * 使用 try-catch 包裹以兼容不同的构建环境
 */
const loadMonacoWorkers = async (): Promise<void> => {
  try {
    // Vite 环境下通过 ?worker 后缀导入 Worker 构造函数
    const editorWorkerMod = await import(
      'monaco-editor/esm/vs/editor/editor.worker?worker'
    )
    EditorWorker = editorWorkerMod.default

    const tsWorkerMod = await import(
      'monaco-editor/esm/vs/language/typescript/ts.worker?worker'
    )
    TsWorker = tsWorkerMod.default

    const jsonWorkerMod = await import(
      'monaco-editor/esm/vs/language/json/json.worker?worker'
    )
    JsonWorker = jsonWorkerMod.default

    const cssWorkerMod = await import(
      'monaco-editor/esm/vs/language/css/css.worker?worker'
    )
    CssWorker = cssWorkerMod.default

    const htmlWorkerMod = await import(
      'monaco-editor/esm/vs/language/html/html.worker?worker'
    )
    HtmlWorker = htmlWorkerMod.default
  } catch {
    // 降级方案：不使用 Web Workers（如 Jest 测试环境）
    // Monaco 会回退到主线程执行
    console.warn('[MonacoPreview] Worker 加载失败，使用主线程模式')
  }
}

/**
 * 配置 Monaco Environment 的 Worker 获取策略
 * 根据语言标签返回对应的 Web Worker 实例
 */
const setupMonacoEnvironment = (): void => {
  ;(self as any).MonacoEnvironment = {
    getWorker(_workerId: string, label: string): Worker {
      try {
        switch (label) {
          case 'typescript':
          case 'javascript':
            return TsWorker ? new TsWorker() : new EditorWorker()
          case 'json':
            return JsonWorker ? new JsonWorker() : new EditorWorker()
          case 'css':
          case 'scss':
          case 'less':
            return CssWorker ? new CssWorker() : new EditorWorker()
          case 'html':
          case 'handlebars':
          case 'razor':
            return HtmlWorker ? new HtmlWorker() : new EditorWorker()
          default:
            return new EditorWorker()
        }
      } catch {
        // 如果特定 Worker 创建失败，使用通用编辑器 Worker
        return new EditorWorker()
      }
    },
  }
}

// ============================================================
// Props 定义
// ============================================================

interface Props {
  /** 代码内容（原始文本） */
  codeContent: string
  /** 文件扩展名（小写，不含点），如 'js', 'py', 'java' */
  fileExtension: string
  /** 文件名（用于展示） */
  fileName?: string
  /** 文件大小（字节） */
  fileSize?: number
  /** 是否只读（默认 true） */
  readOnly?: boolean
  /** 主题（默认 'vs-dark'） */
  theme?: 'vs' | 'vs-dark' | 'hc-black' | 'hc-light'
  /** 字体大小（px） */
  fontSize?: number
  /** 是否显示小地图 */
  showMinimap?: boolean
  /** 跳转到指定行（从 1 开始） */
  targetLine?: number
}

const props = withDefaults(defineProps<Props>(), {
  fileName: '',
  fileSize: 0,
  readOnly: true,
  theme: 'vs-dark',
  fontSize: 14,
  showMinimap: true,
  targetLine: 0,
})

// ============================================================
// Emits 定义
// ============================================================

const emit = defineEmits<{
  (e: 'ready', editor: monaco.editor.IStandaloneCodeEditor): void
  (e: 'content-change', content: string): void
  (e: 'cursor-change', position: { lineNumber: number; column: number }): void
  (e: 'error', error: Error): void
}>()

// ============================================================
// 模板引用
// ============================================================

const rootRef = ref<HTMLDivElement>()
const editorContainerRef = ref<HTMLDivElement>()
const gotoInputRef = ref<HTMLInputElement>()

// ============================================================
// 响应式状态
// ============================================================

/** Monaco Editor 实例（使用 shallowRef 避免深度响应式） */
const editorInstance = shallowRef<monaco.editor.IStandaloneCodeEditor | null>(null)

/** 编辑器是否正在加载 */
const loading = ref(true)

/** 初始化错误信息 */
const initError = ref<string | null>(null)

/** 是否已初始化完成 */
const initialized = ref(false)

/** 编辑器是否已销毁 */
const disposed = ref(false)

// UI 状态
const showOutline = ref(false)
const showGoToLineDialog = ref(false)
const wordWrapEnabled = ref(false)
const minimapEnabled = ref(props.showMinimap)
const isFullscreen = ref(false)
const copied = ref(false)
const gotoLineValue = ref('')

/** 光标位置 */
const cursorPosition = ref<{ lineNumber: number; column: number } | null>(null)

/** 编辑器总行数 */
const totalLines = ref(0)

/** 编辑器总字符数 */
const totalChars = ref(0)

/** 检测到的缩进方式 */
const detectedIndent = ref('Spaces: 4')

// ============================================================
// 语言映射表
// ============================================================

/**
 * 文件扩展名 → Monaco Editor 语言 ID 映射
 * Monaco Editor 支持 60+ 语言，通过 language ID 指定
 * 参考：https://microsoft.github.io/monaco-editor/docs.html#interfaces/editor.ITextModel.html
 */
const EXTENSION_TO_MONACO_LANG: Record<string, string> = {
  // Web 前端
  js: 'javascript', jsx: 'javascript', mjs: 'javascript', cjs: 'javascript',
  ts: 'typescript', tsx: 'typescript',
  html: 'html', htm: 'html', xhtml: 'html',
  css: 'css', scss: 'scss', less: 'less', sass: 'scss',
  vue: 'html', svelte: 'html',
  // 后端
  py: 'python', pyw: 'python', pyx: 'python',
  java: 'java', class: 'java',
  go: 'go',
  rs: 'rust', rlib: 'rust',
  php: 'php', phtml: 'php',
  rb: 'ruby', rake: 'ruby',
  cs: 'csharp', csx: 'csharp',
  kt: 'kotlin', kts: 'kotlin',
  scala: 'scala', sc: 'scala',
  swift: 'swift',
  dart: 'dart',
  // C/C++ 家族
  c: 'c', h: 'c',
  cpp: 'cpp', cxx: 'cpp', cc: 'cpp', hpp: 'cpp', hxx: 'cpp',
  // 数据格式
  json: 'json', jsonc: 'json',
  xml: 'xml', xsd: 'xml', xsl: 'xml', wsdl: 'xml',
  yaml: 'yaml', yml: 'yaml',
  toml: 'ini', ini: 'ini', cfg: 'ini', conf: 'ini',
  // 标记语言
  md: 'markdown', mdx: 'markdown', markdown: 'markdown',
  rst: 'restructuredtext',
  tex: 'latex', sty: 'latex',
  // Shell / 脚本
  sh: 'shell', bash: 'shell', zsh: 'shell', fish: 'shell',
  ps1: 'powershell', psd1: 'powershell', psm1: 'powershell',
  bat: 'bat', cmd: 'bat',
  // 数据库
  sql: 'sql', mysql: 'sql', pgsql: 'sql',
  // 函数式
  hs: 'haskell', lhs: 'haskell',
  clj: 'clojure', cljs: 'clojure', edn: 'clojure',
  elm: 'elm',
  ex: 'elixir', exs: 'elixir',
  erl: 'erlang', hrl: 'erlang',
  fs: 'fsharp', fsi: 'fsharp',
  ml: 'ocaml', mli: 'ocaml',
  // 其他
  lua: 'lua',
  r: 'r', rmd: 'r',
  pl: 'perl', pm: 'perl',
  groovy: 'groovy', gvy: 'groovy',
  jl: 'julia',
  dart: 'dart',
  sol: 'solidity',
  nim: 'nim',
  zig: 'zig',
  v: 'v',
  cr: 'crystal',
  m: 'objective-c', mm: 'objective-c',
  f: 'fortran', f90: 'fortran', f95: 'fortran',
  proto: 'protobuf',
  graphql: 'graphql', gql: 'graphql',
  cmake: 'cmake',
  dockerfile: 'dockerfile',
  nginx: 'nginx',
  makefile: 'makefile', mk: 'makefile',
  diff: 'diff', patch: 'diff',
  // 纯文本
  txt: 'plaintext', log: 'plaintext', text: 'plaintext',
}

/**
 * 语言 ID → 显示名称映射
 */
const MONACO_LANG_DISPLAY_NAMES: Record<string, string> = {
  javascript: 'JavaScript', typescript: 'TypeScript',
  python: 'Python', java: 'Java', go: 'Go', rust: 'Rust',
  c: 'C', cpp: 'C++', csharp: 'C#',
  php: 'PHP', ruby: 'Ruby', kotlin: 'Kotlin', scala: 'Scala',
  swift: 'Swift', dart: 'Dart', lua: 'Lua',
  html: 'HTML', css: 'CSS', scss: 'SCSS', less: 'Less',
  json: 'JSON', xml: 'XML', yaml: 'YAML', ini: 'INI/TOML',
  markdown: 'Markdown', latex: 'LaTeX',
  shell: 'Shell', powershell: 'PowerShell', bat: 'Batch',
  sql: 'SQL', graphql: 'GraphQL', protobuf: 'Protobuf',
  haskell: 'Haskell', clojure: 'Clojure', elm: 'Elm',
  elixir: 'Elixir', erlang: 'Erlang', fsharp: 'F#', ocaml: 'OCaml',
  r: 'R', perl: 'Perl', groovy: 'Groovy', julia: 'Julia',
  solidity: 'Solidity', nim: 'Nim', zig: 'Zig', crystal: 'Crystal',
  'objective-c': 'Objective-C', fortran: 'Fortran',
  cmake: 'CMake', dockerfile: 'Dockerfile', makefile: 'Makefile',
  diff: 'Diff', plaintext: 'Plain Text',
}

// ============================================================
// 计算属性
// ============================================================

/** 检测到的 Monaco 语言 ID */
const monacoLanguageId = computed(() => {
  return EXTENSION_TO_MONACO_LANG[props.fileExtension] || 'plaintext'
})

/** 检测到的语言显示名称 */
const detectedLanguage = computed(() => {
  return MONACO_LANG_DISPLAY_NAMES[monacoLanguageId.value] || props.fileExtension.toUpperCase()
})

/** 格式化文件大小 */
const formattedFileSize = computed(() => {
  const bytes = props.fileSize
  if (bytes === 0) return '未知大小'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
})

// ============================================================
// 代码结构解析（从编辑器模型提取符号信息）
// ============================================================

/** 代码符号信息 */
interface CodeSymbol {
  name: string
  line: number
  kind: string
  detail?: string
}

/** 代码符号分组 */
interface CodeSymbols {
  functions: CodeSymbol[]
  classes: CodeSymbol[]
  variables: CodeSymbol[]
}

/** 代码符号状态 */
const codeSymbols = ref<CodeSymbols>({
  functions: [],
  classes: [],
  variables: [],
})

/**
 * 从编辑器文本模型中解析代码符号结构
 * 使用正则表达式匹配，兼容所有语言的基础符号识别
 *
 * 注：Monaco 本身提供 DocumentSymbolProvider，但需要特定语言服务支持。
 * 这里使用通用正则解析作为 fallback，确保所有语言都有代码结构导航。
 */
const parseCodeSymbols = (): void => {
  const editor = editorInstance.value
  if (!editor) return

  const model = editor.getModel()
  if (!model) return

  const symbols: CodeSymbols = {
    functions: [],
    classes: [],
    variables: [],
  }

  const langId = monacoLanguageId.value
  const lineCount = model.getLineCount()

  for (let i = 1; i <= lineCount; i++) {
    const line = model.getLineContent(i).trim()
    if (!line) continue

    // 根据语言类型选择解析策略
    if (langId === 'python') {
      parsePythonLine(line, i, symbols)
    } else if (langId === 'java') {
      parseJavaLine(line, i, symbols)
    } else if (langId === 'rust') {
      parseRustLine(line, i, symbols)
    } else if (langId === 'go') {
      parseGoLine(line, i, symbols)
    } else {
      // 通用解析（JavaScript/TypeScript/C/C++/C#/Kotlin/Swift 等 C-like 语言）
      parseGenericLine(line, i, symbols)
    }
  }

  codeSymbols.value = symbols
}

/**
 * 解析 Python 代码行
 */
const parsePythonLine = (line: string, lineNum: number, symbols: CodeSymbols): void => {
  // 函数定义：def name(params):
  const funcMatch = line.match(/^def\s+(\w+)\s*\(/)
  if (funcMatch) {
    symbols.functions.push({ name: funcMatch[1], line: lineNum, kind: 'method' })
    return
  }
  // 异步函数：async def name(params):
  const asyncFuncMatch = line.match(/^async\s+def\s+(\w+)\s*\(/)
  if (asyncFuncMatch) {
    symbols.functions.push({ name: asyncFuncMatch[1], line: lineNum, kind: 'method' })
    return
  }
  // 类定义：class Name(Base):
  const classMatch = line.match(/^class\s+(\w+)/)
  if (classMatch) {
    symbols.classes.push({ name: classMatch[1], line: lineNum, kind: 'class' })
    return
  }
  // 变量赋值（排除关键字）
  if (!line.startsWith('def ') && !line.startsWith('class ') && !line.startsWith('@') &&
      !line.startsWith('import ') && !line.startsWith('from ') && !line.startsWith('#')) {
    const varMatch = line.match(/^(\w+)\s*[:=]\s*/)
    if (varMatch) {
      const reserved = new Set(['if', 'elif', 'else', 'for', 'while', 'try', 'except',
        'finally', 'with', 'and', 'or', 'not', 'in', 'is', 'return', 'yield', 'raise',
        'pass', 'break', 'continue', 'global', 'nonlocal', 'assert', 'del', 'lambda',
        'True', 'False', 'None', 'async', 'await'])
      if (!reserved.has(varMatch[1])) {
        symbols.variables.push({ name: varMatch[1], line: lineNum, kind: 'variable' })
      }
    }
  }
}

/**
 * 解析 Java 代码行
 */
const parseJavaLine = (line: string, lineNum: number, symbols: CodeSymbols): void => {
  // 方法定义（修饰符 + 返回类型 + 方法名 + 括号）
  const methodMatch = line.match(
    /(?:public|private|protected|static|final|abstract|synchronized|native|\s)+[\w<>[\],\s]+\s+(\w+)\s*\(/
  )
  if (methodMatch && !line.includes('class ') && !line.includes('interface ') && !line.includes('enum ')) {
    symbols.functions.push({ name: methodMatch[1], line: lineNum, kind: 'method' })
    return
  }
  // 类定义
  const classMatch = line.match(/(?:public|private|protected)?\s*(?:abstract|final|static)?\s*class\s+(\w+)/)
  if (classMatch) {
    symbols.classes.push({ name: classMatch[1], line: lineNum, kind: 'class' })
    return
  }
  // 接口定义
  const interfaceMatch = line.match(/(?:public|private|protected)?\s*interface\s+(\w+)/)
  if (interfaceMatch) {
    symbols.classes.push({ name: interfaceMatch[1], line: lineNum, kind: 'interface' })
    return
  }
  // 枚举定义
  const enumMatch = line.match(/(?:public|private|protected)?\s*enum\s+(\w+)/)
  if (enumMatch) {
    symbols.classes.push({ name: enumMatch[1], line: lineNum, kind: 'enum' })
    return
  }
  // 字段声明
  const fieldMatch = line.match(/(?:private|public|protected)\s+(?:static|final)?\s*[\w<>[\],\s]+\s+(\w+)\s*[=;]/)
  if (fieldMatch && !line.includes('(')) {
    symbols.variables.push({ name: fieldMatch[1], line: lineNum, kind: 'field' })
  }
}

/**
 * 解析 Rust 代码行
 */
const parseRustLine = (line: string, lineNum: number, symbols: CodeSymbols): void => {
  // 函数定义：fn name(params)
  const funcMatch = line.match(/^(\s*)fn\s+(\w+)\s*[<(]/)
  if (funcMatch) {
    symbols.functions.push({ name: funcMatch[2], line: lineNum, kind: 'method' })
    return
  }
  // 结构体定义：struct Name
  const structMatch = line.match(/^(\s*)struct\s+(\w+)/)
  if (structMatch) {
    symbols.classes.push({ name: structMatch[2], line: lineNum, kind: 'struct' })
    return
  }
  // 枚举定义：enum Name
  const enumMatch = line.match(/^(\s*)enum\s+(\w+)/)
  if (enumMatch) {
    symbols.classes.push({ name: enumMatch[1], line: lineNum, kind: 'enum' })
  }
  // Trait 定义：trait Name
  const traitMatch = line.match(/^(\s*)trait\s+(\w+)/)
  if (traitMatch) {
    symbols.classes.push({ name: traitMatch[1], line: lineNum, kind: 'trait' })
  }
  // 变量绑定：let name = ...
  const letMatch = line.match(/^(\s*)let\s+(mut\s+)?(\w+)\s*[:=]/)
  if (letMatch) {
    symbols.variables.push({ name: letMatch[3], line: lineNum, kind: 'variable' })
  }
  // 常量：const NAME: type = ...
  const constMatch = line.match(/^(\s*)const\s+(\w+)\s*:/)
  if (constMatch) {
    symbols.variables.push({ name: constMatch[2], line: lineNum, kind: 'constant' })
  }
}

/**
 * 解析 Go 代码行
 */
const parseGoLine = (line: string, lineNum: number, symbols: CodeSymbols): void => {
  // 函数定义：func Name(params)
  const funcMatch = line.match(/^func\s+(?:\([^)]+\)\s+)?(\w+)\s*\(/)
  if (funcMatch) {
    symbols.functions.push({ name: funcMatch[1], line: lineNum, kind: 'method' })
    return
  }
  // 结构体定义：type Name struct
  const structMatch = line.match(/^type\s+(\w+)\s+struct/)
  if (structMatch) {
    symbols.classes.push({ name: structMatch[1], line: lineNum, kind: 'struct' })
    return
  }
  // 接口定义：type Name interface
  const interfaceMatch = line.match(/^type\s+(\w+)\s+interface/)
  if (interfaceMatch) {
    symbols.classes.push({ name: interfaceMatch[1], line: lineNum, kind: 'interface' })
    return
  }
  // 变量声明：var name type 或 name := value
  const varMatch = line.match(/^var\s+(\w+)\s+/)
  if (varMatch) {
    symbols.variables.push({ name: varMatch[1], line: lineNum, kind: 'variable' })
  }
  const shortVarMatch = line.match(/^(\w+)\s*:=/)
  if (shortVarMatch) {
    symbols.variables.push({ name: shortVarMatch[1], line: lineNum, kind: 'variable' })
  }
}

/**
 * 解析通用 C-like 语言代码行（JS/TS/C/C++/C#/Kotlin/Swift 等）
 */
const parseGenericLine = (line: string, lineNum: number, symbols: CodeSymbols): void => {
  // 函数声明（C-like）：function name(params) 或 returnType name(params)
  const funcDecl1 = line.match(/(?:async\s+)?function\s+(\w+)\s*\(/)
  if (funcDecl1) {
    symbols.functions.push({ name: funcDecl1[1], line: lineNum, kind: 'method' })
    return
  }

  // 箭头函数赋值：const/let/var name = (params) =>
  const arrowMatch = line.match(/(?:const|let|var)\s+(\w+)\s*=\s*(?:async\s+)?\(/)
  if (arrowMatch && line.includes('=>')) {
    symbols.functions.push({ name: arrowMatch[1], line: lineNum, kind: 'method' })
    return
  }

  // 类定义：class Name
  const classMatch = line.match(/\bclass\s+(\w+)/)
  if (classMatch) {
    symbols.classes.push({ name: classMatch[1], line: lineNum, kind: 'class' })
    return
  }

  // 接口定义：interface Name
  const interfaceMatch = line.match(/\binterface\s+(\w+)/)
  if (interfaceMatch) {
    symbols.classes.push({ name: interfaceMatch[1], line: lineNum, kind: 'interface' })
    return
  }

  // 变量声明：const/let/var name 或 type name
  const varMatch = line.match(/(?:const|let|var)\s+(\w+)\s*[:=]/)
  if (varMatch && !line.includes('=>')) {
    symbols.variables.push({ name: varMatch[1], line: lineNum, kind: 'variable' })
  }
}

// ============================================================
// Monaco Editor 初始化
// ============================================================

/**
 * 初始化 Monaco Editor 实例
 *
 * 配置说明：
 *   - readOnly: true — 预览模式，禁止编辑
 *   - minimap — 小地图（代码缩略图）
 *   - folding — 代码折叠
 *   - bracketPairColorization — 括号对着色
 *   - guides.indentation — 缩进参考线
 *   - renderWhitespace — 渲染空白字符
 *   - scrollBeyondLastLine — 禁止滚动超过最后一行
 *   - fontFamily — 等宽字体栈
 */
const initMonacoEditor = async (): Promise<void> => {
  if (!editorContainerRef.value) {
    initError.value = '无法找到编辑器容器元素'
    return
  }

  try {
    // 创建编辑器实例
    const editor = monaco.editor.create(editorContainerRef.value, {
      // ---- 基础配置 ----
      value: props.codeContent,
      language: monacoLanguageId.value,
      readOnly: props.readOnly,
      theme: props.theme,

      // ---- 显示配置 ----
      fontSize: props.fontSize,
      fontFamily: "'Cascadia Code', 'Fira Code', 'JetBrains Mono', 'Source Code Pro', 'Consolas', 'Courier New', monospace",
      fontLigatures: true,
      lineHeight: 22,
      letterSpacing: 0.5,

      // ---- 编辑器特性 ----
      lineNumbers: 'on',                    // 行号
      lineNumbersMinChars: 4,               // 行号最小宽度
      glyphMargin: true,                    // 装订线（断点图标等）
      folding: true,                        // 代码折叠
      foldingStrategy: 'indentation',       // 折叠策略：基于缩进
      foldingHighlight: true,               // 折叠区域高亮
      renderLineHighlight: 'line',          // 当前行高亮
      renderLineHighlightOnlyWhenFocus: false,

      // ---- 小地图 ----
      minimap: {
        enabled: props.showMinimap,
        scale: 1,
        showSlider: 'mouseover',
        renderCharacters: true,
        maxColumn: 120,
      },

      // ---- 括号匹配 ----
      matchBrackets: 'always',
      bracketPairColorization: {
        enabled: true,
        independentColorPoolPerBracketType: true,
      },

      // ---- 缩进参考线 ----
      guides: {
        indentation: true,
        bracketPairs: true,
        bracketPairsHorizontal: 'active',
        highlightActiveIndentation: true,
      },

      // ---- 空白字符 ----
      renderWhitespace: 'selection',        // 选中时显示空白字符
      renderControlCharacters: true,        // 显示控制字符

      // ---- 滚动 ----
      scrollBeyondLastLine: false,
      scrollBeyondLastColumn: 5,
      smoothScrolling: true,
      cursorSmoothCaretAnimation: 'on',

      // ---- 自动换行 ----
      wordWrap: 'off',
      wrappingIndent: 'same',
      wrappingStrategy: 'advanced',

      // ---- 光标 ----
      cursorBlinking: 'smooth',
      cursorStyle: 'line',
      cursorWidth: 2,

      // ---- 选择 ----
      selectionHighlight: true,
      occurrencesHighlight: 'singleFile',
      renderLineHighlightOnlyWhenFocus: false,

      // ---- 搜索 ----
      find: {
        addExtraSpaceOnTop: false,
        autoFindInSelection: 'never',
        seedSearchStringFromSelection: 'always',
      },

      // ---- 内联提示 ----
      inlayHints: {
        enabled: 'on',
      },

      // ---- 粘贴 ----
      links: true,
      contextmenu: true,

      // ---- 其他 ----
      autoClosingBrackets: 'never',         // 预览模式不自动闭合括号
      autoClosingQuotes: 'never',
      autoSurround: 'never',
      acceptSuggestionOnCommitCharacter: false,
      tabCompletion: 'off',
      wordBasedSuggestions: 'off',
      suggestOnTriggerCharacters: false,
      quickSuggestions: false,
      parameterHints: { enabled: false },
      hover: {
        enabled: true,                      // 启用悬停提示（IntelliSense）
        delay: 300,                         // 悬停延迟 300ms
        sticky: true,                       // 粘性悬停（鼠标移入提示框不消失）
      },
      lightbulb: { enabled: 'off' },
      padding: {
        top: 12,
        bottom: 12,
      },
      overviewRulerLanes: 2,
      overviewRulerBorder: false,
      hideCursorInOverviewRuler: true,
      stickyScroll: {
        enabled: true,
        maxLineCount: 5,
      },
      automaticLayout: true,                // 自动响应容器大小变化
      fixedOverflowWidgets: true,           // 溢出组件固定在视图内
    })

    editorInstance.value = editor

    // 更新行数和字符数
    updateModelStats()

    // 监听内容变化
    editor.onDidChangeModelContent(() => {
      updateModelStats()
      // 动态解析代码符号
      parseCodeSymbols()
      // 通知父组件内容变化
      const content = editor.getValue()
      emit('content-change', content)
      totalChars.value = content.length
    })

    // 监听光标位置变化
    editor.onDidChangeCursorPosition((e) => {
      cursorPosition.value = {
        lineNumber: e.position.lineNumber,
        column: e.position.column,
      }
      emit('cursor-change', cursorPosition.value)
    })

    // 检测缩进方式
    detectIndentation()

    // 初始解析代码符号
    parseCodeSymbols()

    // 通知父组件编辑器已就绪
    initialized.value = true
    loading.value = false
    emit('ready', editor)

    // 如果需要跳转到指定行
    if (props.targetLine > 0) {
      revealLine(props.targetLine)
    }
  } catch (err: any) {
    console.error('[MonacoPreview] 编辑器初始化失败:', err)
    initError.value = err?.message || '编辑器初始化失败'
    loading.value = false
    emit('error', err instanceof Error ? err : new Error(String(err)))
  }
}

/**
 * 更新模型统计信息（行数、字符数）
 */
const updateModelStats = (): void => {
  const editor = editorInstance.value
  if (!editor) return

  const model = editor.getModel()
  if (!model) return

  totalLines.value = model.getLineCount()
  totalChars.value = model.getValue().length
}

/**
 * 检测缩进方式
 */
const detectIndentation = (): void => {
  const editor = editorInstance.value
  if (!editor) return

  const options = editor.getOptions()
  const tabSize = options.get(monaco.editor.EditorOption.tabSize)
  const insertSpaces = options.get(monaco.editor.EditorOption.insertSpaces)

  if (insertSpaces) {
    detectedIndent.value = `Spaces: ${tabSize}`
  } else {
    detectedIndent.value = `Tab Size: ${tabSize}`
  }
}

// ============================================================
// 编辑器操作方法
// ============================================================

/**
 * 触发 Monaco 内置搜索
 */
const triggerMonacoSearch = (): void => {
  const editor = editorInstance.value
  if (!editor) return

  editor.getAction('actions.find')?.run()
}

/**
 * 跳转到指定行
 * Monaco Editor 中行号从 1 开始
 */
const revealLine = (lineNumber: number): void => {
  const editor = editorInstance.value
  if (!editor) return

  editor.revealLineInCenter(lineNumber)
  editor.setPosition({ lineNumber, column: 1 })
  editor.focus()
}

/**
 * 打开跳转到行对话框
 */
const openGoToLine = (): void => {
  showGoToLineDialog.value = true
  gotoLineValue.value = ''
  nextTick(() => {
    gotoInputRef.value?.focus()
  })
}

/**
 * 执行跳转到行
 */
const executeGoToLine = (): void => {
  const lineNumber = parseInt(gotoLineValue.value, 10)
  if (isNaN(lineNumber) || lineNumber < 1) return

  revealLine(Math.min(lineNumber, totalLines.value))
  showGoToLineDialog.value = false
}

/**
 * 切换代码结构导航
 */
const toggleOutline = (): void => {
  showOutline.value = !showOutline.value
}

/**
 * 切换自动换行
 */
const toggleWordWrap = (): void => {
  const editor = editorInstance.value
  if (!editor) return

  wordWrapEnabled.value = !wordWrapEnabled.value
  editor.updateOptions({
    wordWrap: wordWrapEnabled.value ? 'on' : 'off',
  })
}

/**
 * 切换小地图
 */
const toggleMinimap = (): void => {
  const editor = editorInstance.value
  if (!editor) return

  minimapEnabled.value = !minimapEnabled.value
  editor.updateOptions({
    minimap: { enabled: minimapEnabled.value },
  })
}

/**
 * 复制全部代码到剪贴板
 */
const copyCode = async (): Promise<void> => {
  const editor = editorInstance.value
  if (!editor) return

  const content = editor.getValue()
  try {
    await navigator.clipboard.writeText(content)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    // 降级方案：使用 textarea
    const textarea = document.createElement('textarea')
    textarea.value = content
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
  }
}

/**
 * 下载代码文件
 */
const downloadCode = (): void => {
  const editor = editorInstance.value
  if (!editor) return

  const content = editor.getValue()
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)

  const a = document.createElement('a')
  a.href = url
  a.download = props.fileName || `code.${props.fileExtension}`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/**
 * 切换全屏
 */
const toggleFullscreen = (): void => {
  isFullscreen.value = !isFullscreen.value
  const editor = editorInstance.value
  if (editor) {
    editor.layout()
  }
}

/**
 * 重新初始化编辑器
 */
const retryInit = (): void => {
  initError.value = null
  loading.value = true
  initMonacoEditor()
}

// ============================================================
// 对外暴露的 API
// ============================================================

/**
 * 获取 Monaco Editor 实例
 */
const getEditor = (): monaco.editor.IStandaloneCodeEditor | null => {
  return editorInstance.value
}

/**
 * 设置编辑器内容
 * 用于外部动态更新代码内容
 */
const setContent = (content: string): void => {
  const editor = editorInstance.value
  if (!editor) return

  const model = editor.getModel()
  if (model) {
    model.setValue(content)
    updateModelStats()
    parseCodeSymbols()
  }
}

/**
 * 设置编辑器语言
 */
const setLanguage = (extension: string): void => {
  const editor = editorInstance.value
  if (!editor) return

  const langId = EXTENSION_TO_MONACO_LANG[extension] || 'plaintext'
  const model = editor.getModel()
  if (model) {
    monaco.editor.setModelLanguage(model, langId)
    parseCodeSymbols()
  }
}

/**
 * 设置编辑器主题
 */
const setTheme = (theme: 'vs' | 'vs-dark' | 'hc-black' | 'hc-light'): void => {
  monaco.editor.setTheme(theme)
}

/**
 * 布局刷新（响应容器大小变化）
 */
const layout = (): void => {
  const editor = editorInstance.value
  if (editor) {
    editor.layout()
  }
}

/**
 * 销毁编辑器实例
 */
const disposeEditor = (): void => {
  if (editorInstance.value) {
    editorInstance.value.dispose()
    editorInstance.value = null
    disposed.value = true
  }
}

// 暴露给父组件
defineExpose({
  getEditor,
  setContent,
  setLanguage,
  setTheme,
  revealLine,
  layout,
  disposeEditor,
  triggerMonacoSearch,
  copyCode,
  downloadCode,
})

// ============================================================
// 生命周期
// ============================================================

onMounted(async () => {
  // 1. 加载 Monaco Worker 模块
  await loadMonacoWorkers()

  // 2. 配置 Monaco Environment
  setupMonacoEnvironment()

  // 3. 初始化编辑器
  await nextTick()
  await initMonacoEditor()
})

onBeforeUnmount(() => {
  disposeEditor()
})

// ============================================================
// 监听 Props 变化
// ============================================================

/** 监听代码内容变化 — 外部更新代码内容 */
watch(
  () => props.codeContent,
  (newContent) => {
    const editor = editorInstance.value
    if (!editor) return

    const model = editor.getModel()
    if (!model) return

    // 仅在内容确实不同时更新（避免循环更新）
    if (model.getValue() !== newContent) {
      model.setValue(newContent)
      updateModelStats()
      parseCodeSymbols()
    }
  }
)

/** 监听文件扩展名变化 — 更新语言模式 */
watch(
  () => props.fileExtension,
  (newExt) => {
    const langId = EXTENSION_TO_MONACO_LANG[newExt] || 'plaintext'
    const editor = editorInstance.value
    if (!editor) return

    const model = editor.getModel()
    if (model) {
      monaco.editor.setModelLanguage(model, langId)
      parseCodeSymbols()
    }
  }
)

/** 监听 targetLine 变化 — 跳转到指定行 */
watch(
  () => props.targetLine,
  (line) => {
    if (line > 0) {
      revealLine(line)
    }
  }
)

/** 监听全屏状态变化 — 刷新编辑器布局 */
watch(isFullscreen, () => {
  nextTick(() => {
    const editor = editorInstance.value
    if (editor) {
      editor.layout()
    }
  })
})
</script>

<style scoped>
/* ============================================================ */
/* MonacoPreview.vue — 样式表 */
/* ============================================================ */
/* 设计语言：VS Code 风格深色主题 */
/* 颜色变量与 VS Code Dark+ 主题保持一致 */
/* ============================================================ */

/* ---- 根容器 ---- */
.monaco-preview-root {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background-color: #1e1e1e;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* ---- 加载状态覆盖层 ---- */
.monaco-loading-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #1e1e1e;
  z-index: 100;
}

.monaco-loading-content {
  text-align: center;
  color: #cccccc;
}

.monaco-loading-spinner {
  margin-bottom: 16px;
}

.spinner-ring {
  width: 48px;
  height: 48px;
  border: 3px solid rgba(255, 255, 255, 0.1);
  border-top-color: #007acc;
  border-radius: 50%;
  animation: monaco-spin 0.8s linear infinite;
  margin: 0 auto;
}

@keyframes monaco-spin {
  to { transform: rotate(360deg); }
}

.monaco-loading-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 8px;
  color: #e0e0e0;
}

.monaco-loading-subtitle {
  font-size: 13px;
  margin: 0;
  color: #888888;
}

/* ---- 错误状态覆盖层 ---- */
.monaco-error-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #1e1e1e;
  z-index: 100;
}

.monaco-error-content {
  text-align: center;
  color: #cccccc;
  max-width: 420px;
  padding: 32px;
}

.monaco-error-icon {
  font-size: 48px;
  color: #f48771;
  margin-bottom: 16px;
}

.monaco-error-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 12px;
  color: #e0e0e0;
}

.monaco-error-message {
  font-size: 13px;
  margin: 0 0 24px;
  color: #888888;
  line-height: 1.5;
}

.monaco-error-retry-btn {
  padding: 8px 24px;
  border: 1px solid #007acc;
  border-radius: 4px;
  background: transparent;
  color: #007acc;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.monaco-error-retry-btn:hover {
  background: rgba(0, 122, 204, 0.1);
}

/* ---- 工具栏 ---- */
.monaco-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  padding: 0 8px;
  background-color: #252526;
  border-bottom: 1px solid #3c3c3c;
  flex-shrink: 0;
  user-select: none;
}

.monaco-toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.monaco-file-name {
  font-size: 13px;
  color: #cccccc;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 240px;
}

.monaco-file-ext {
  font-size: 11px;
  color: #888888;
  background: rgba(255, 255, 255, 0.06);
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 500;
  text-transform: uppercase;
}

.monaco-language-label {
  font-size: 11px;
  color: #569cd6;
  background: rgba(86, 156, 214, 0.1);
  padding: 1px 6px;
  border-radius: 3px;
}

.monaco-toolbar-right {
  display: flex;
  align-items: center;
  gap: 2px;
}

.monaco-tool-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #cccccc;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.1s;
}

.monaco-tool-btn:hover {
  background: rgba(255, 255, 255, 0.08);
}

.monaco-tool-btn.active {
  background: rgba(0, 122, 204, 0.15);
  color: #007acc;
}

.monaco-toolbar-divider {
  width: 1px;
  height: 20px;
  background: #3c3c3c;
  margin: 0 4px;
}

/* ---- 跳转到行对话框 ---- */
.monaco-goto-dialog {
  position: absolute;
  top: 44px;
  right: 12px;
  z-index: 50;
  background: #252526;
  border: 1px solid #3c3c3c;
  border-radius: 6px;
  padding: 8px 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
}

.monaco-goto-input-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.monaco-goto-input {
  width: 120px;
  padding: 4px 8px;
  border: 1px solid #3c3c3c;
  border-radius: 3px;
  background: #1e1e1e;
  color: #cccccc;
  font-size: 13px;
  font-family: 'Cascadia Code', monospace;
  outline: none;
}

.monaco-goto-input:focus {
  border-color: #007acc;
}

.monaco-goto-hint {
  font-size: 11px;
  color: #888888;
  white-space: nowrap;
}

/* 过渡动画 */
.goto-slide-enter-active,
.goto-slide-leave-active {
  transition: all 0.15s ease;
}
.goto-slide-enter-from,
.goto-slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ---- 主内容区 ---- */
.monaco-body {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* ---- 代码结构导航侧边栏 ---- */
.monaco-outline-panel {
  width: 240px;
  min-width: 200px;
  background: #252526;
  border-right: 1px solid #3c3c3c;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.monaco-outline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 11px;
  font-weight: 600;
  color: #888888;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid #3c3c3c;
}

.monaco-outline-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: #888888;
  font-size: 11px;
  cursor: pointer;
}

.monaco-outline-close:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #cccccc;
}

.monaco-outline-content {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.monaco-outline-section {
  margin-bottom: 8px;
}

.monaco-outline-section-title {
  padding: 4px 12px;
  font-size: 11px;
  font-weight: 600;
  color: #888888;
  text-transform: uppercase;
}

.monaco-outline-item {
  display: flex;
  align-items: center;
  padding: 3px 12px;
  font-size: 12px;
  color: #cccccc;
  cursor: pointer;
  transition: background 0.1s;
  gap: 6px;
}

.monaco-outline-item:hover {
  background: rgba(255, 255, 255, 0.06);
}

.monaco-outline-icon {
  font-size: 11px;
  width: 16px;
  text-align: center;
  flex-shrink: 0;
}

.monaco-icon-method {
  color: #dcdcaa;
}

.monaco-icon-class {
  color: #4ec9b0;
}

.monaco-icon-var {
  color: #9cdcfe;
}

.monaco-outline-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.monaco-outline-line {
  font-size: 10px;
  color: #6a6a6a;
  flex-shrink: 0;
}

.monaco-outline-empty {
  padding: 24px 12px;
  text-align: center;
  font-size: 12px;
  color: #6a6a6a;
}

/* 侧边栏过渡动画 */
.outline-slide-enter-active,
.outline-slide-leave-active {
  transition: all 0.2s ease;
}
.outline-slide-enter-from,
.outline-slide-leave-to {
  width: 0 !important;
  min-width: 0 !important;
  opacity: 0;
}

/* ---- Monaco Editor 容器 ---- */
.monaco-editor-container {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

/* ---- 状态栏 ---- */
.monaco-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 24px;
  padding: 0 8px;
  background-color: #007acc;
  color: #ffffff;
  flex-shrink: 0;
  user-select: none;
}

.monaco-status-left,
.monaco-status-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.monaco-status-item {
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0.9;
}

.monaco-status-item i {
  font-size: 11px;
  opacity: 0.7;
}

/* ============================================================ */
/* 全屏模式 */
/* ============================================================ */
.monaco-preview-root:fullscreen {
  background-color: #1e1e1e;
}

.monaco-preview-root:fullscreen .monaco-toolbar {
  height: 40px;
}

.monaco-preview-root:fullscreen .monaco-status-bar {
  height: 28px;
}

/* ============================================================ */
/* 响应式：移动端适配 */
/* ============================================================ */
@media (max-width: 768px) {
  .monaco-file-name {
    max-width: 120px;
  }

  .monaco-outline-panel {
    position: absolute;
    top: 36px;
    left: 0;
    bottom: 24px;
    z-index: 40;
    box-shadow: 2px 0 12px rgba(0, 0, 0, 0.5);
  }

  .monaco-toolbar {
    padding: 0 4px;
  }

  .monaco-tool-btn {
    width: 24px;
    height: 24px;
    font-size: 12px;
  }
}
</style>