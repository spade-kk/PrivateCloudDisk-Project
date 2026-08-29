// ============================================================
// monaco-editor.d.ts — Monaco Editor 类型声明（替代 npm 包类型）
// ============================================================
// 说明：
//   由于 monaco-editor 改为 CDN 动态加载，本地不再安装 npm 包，
//   但项目源码中仍使用 `import type * as monaco from 'monaco-editor'`
//   进行 TypeScript 类型检查。本声明文件提供最小化的类型 shim，
//   仅声明项目实际使用到的 API，避免引入完整的 monaco 类型定义（5MB+）。
//
//   如需扩展类型，参考官方类型定义：
//   https://github.com/microsoft/monaco-editor/tree/main/monaco-editor
//
// 类型来源策略：
//   - 优先复用 CDN 运行时实际暴露的全局 monaco 对象的类型
//   - 通过 `typeof import('monaco-editor')` 保留与官方类型一致的表达
//   - 运行时由 monacoLoader.ts 从 CDN 加载并挂载到 window.monaco
// ============================================================

declare module 'monaco-editor' {
  export enum MarkerSeverity {
    Hint = 1,
    Info = 2,
    Warning = 4,
    Error = 8,
  }

  // ---- Editor 命名空间 ----
  export namespace editor {
    export interface IMarkerData {
      severity: MarkerSeverity
      message: string
      startLineNumber: number
      startColumn: number
      endLineNumber: number
      endColumn: number
    }

    /**
     * 标准 Code Editor 实例接口
     * 仅声明项目实际调用的 API，避免维护完整接口
     */
    export interface IStandaloneCodeEditor {
      // 模型
      getModel(): ITextModel | null
      // 内容
      getValue(): string
      setValue(value: string): void
      // 配置
      updateOptions(opts: Partial<IStandaloneEditorConstructionOptions> & { [key: string]: unknown }): void
      getOptions(): IEditorOptions
      // 视图
      layout(): void
      revealLineInCenter(lineNumber: number): void
      setPosition(position: IPosition): void
      focus(): void
      // Action
      getAction(id: string): { run(): Promise<void> } | null
      // 事件
      onDidChangeModelContent(cb: () => void): IDisposable
      onDidChangeCursorPosition(cb: (e: { position: IPosition }) => void): IDisposable
      // 生命周期
      dispose(): void
    }

    export interface ITextModel {
      setValue(value: string): void
      getValue(): string
      getLineCount(): number
      getLineContent(lineNumber: number): string
    }

    export interface IPosition {
      lineNumber: number
      column: number
    }

    export interface IDisposable {
      dispose(): void
    }

    export interface IEditorOptions {
      get<T extends EditorOption>(option: T): T extends EditorOption.tabSize ? number : T extends EditorOption.insertSpaces ? boolean : unknown
    }

    /**
     * 编辑器配置项枚举（仅声明项目使用的项）
     */
    export enum EditorOption {
      tabSize = 8,
      insertSpaces = 13,
    }

    /**
     * 创建编辑器实例
     */
    export function create(
      domElement: HTMLElement,
      options: IStandaloneEditorConstructionOptions & { [key: string]: unknown }
    ): IStandaloneCodeEditor

    /**
     * 切换 model 的语言
     */
    export function setModelLanguage(model: ITextModel, languageId: string): void

    export function setModelMarkers(model: ITextModel, owner: string, markers: IMarkerData[]): void

    /**
     * 切换主题
     */
    export function setTheme(theme: string): void

    /**
     * 创建 model
     */
    export interface IStandaloneEditorConstructionOptions {
      value?: string
      language?: string
      theme?: string
      readOnly?: boolean
      fontSize?: number
      fontFamily?: string
      fontLigatures?: boolean
      lineHeight?: number
      letterSpacing?: number
      lineNumbers?: 'on' | 'off' | 'relative' | 'interval'
      lineNumbersMinChars?: number
      glyphMargin?: boolean
      folding?: boolean
      foldingStrategy?: string
      foldingHighlight?: boolean
      renderLineHighlight?: string
      renderLineHighlightOnlyWhenFocus?: boolean
      minimap?: {
        enabled?: boolean
        scale?: number
        showSlider?: string
        renderCharacters?: boolean
        maxColumn?: number
      }
      matchBrackets?: string
      bracketPairColorization?: {
        enabled?: boolean
        independentColorPoolPerBracketType?: boolean
      }
      guides?: {
        indentation?: boolean
        bracketPairs?: boolean
        bracketPairsHorizontal?: string
        highlightActiveIndentation?: boolean
      }
      renderWhitespace?: string
      renderControlCharacters?: boolean
      scrollBeyondLastLine?: boolean
      scrollBeyondLastColumn?: number
      smoothScrolling?: boolean
      cursorSmoothCaretAnimation?: string
      wordWrap?: string
      wrappingIndent?: string
      wrappingStrategy?: string
      cursorBlinking?: string
      cursorStyle?: string
      cursorWidth?: number
      selectionHighlight?: boolean
      occurrencesHighlight?: string
      find?: {
        addExtraSpaceOnTop?: boolean
        autoFindInSelection?: string
        seedSearchStringFromSelection?: string
      }
      inlayHints?: { enabled?: string }
      links?: boolean
      contextmenu?: boolean
      autoClosingBrackets?: string
      autoClosingQuotes?: string
      autoSurround?: string
      acceptSuggestionOnCommitCharacter?: boolean
      tabCompletion?: string
      wordBasedSuggestions?: string
      suggestOnTriggerCharacters?: boolean
      quickSuggestions?: boolean
      parameterHints?: { enabled?: boolean }
      hover?: {
        enabled?: boolean
        delay?: number
        sticky?: boolean
      }
      lightbulb?: { enabled?: string }
      padding?: { top?: number; bottom?: number }
      overviewRulerLanes?: number
      overviewRulerBorder?: boolean
      hideCursorInOverviewRuler?: boolean
      stickyScroll?: { enabled?: boolean; maxLineCount?: number }
      automaticLayout?: boolean
      fixedOverflowWidgets?: boolean
    }
  }

  export namespace languages {
    export enum CompletionItemKind {
      Method = 0,
      Function = 1,
      Keyword = 17,
      Snippet = 27,
    }

    export interface ILanguageExtensionPoint {
      id: string
    }

    /**
     * CloudFlow IDE 合规改造：补齐项目实际使用的语言注册 API，
     * 保持 CDN Monaco 运行时与本地最小类型声明一致。
     */
    export function getLanguages(): ILanguageExtensionPoint[]

    export function register(language: { id: string }): void

    export function setMonarchTokensProvider(
      languageId: string,
      languageDefinition: Record<string, unknown>,
    ): { dispose(): void }

    export enum CompletionItemInsertTextRule {
      None = 0,
      KeepWhitespace = 1,
      InsertAsSnippet = 4,
    }

    export interface CompletionItem {
      label: string
      kind: CompletionItemKind
      insertText: string
      insertTextRules?: CompletionItemInsertTextRule
      documentation?: string
    }

    export interface CompletionList {
      suggestions: CompletionItem[]
    }

    export function registerCompletionItemProvider(
      languageId: string,
      provider: {
        triggerCharacters?: string[]
        provideCompletionItems(...args: unknown[]): CompletionList
      },
    ): { dispose(): void }

    export namespace typescript {
      export const javascriptDefaults: {
        addExtraLib(content: string, filePath?: string): { dispose(): void }
      }
    }
  }
}

// ============================================================
// 全局 window 类型扩展
// ============================================================
// 加载完成后，CDN loader 将 monaco 挂载到 window.monaco
// AMD loader.js 加载后会注入 window.require
// 此处复用模块类型，避免重复定义
interface AMDRequire {
  config(opts: { paths: Record<string, string>; [key: string]: unknown }): void
  (deps: string[], callback: (...args: unknown[]) => void, errback?: (err: Error) => void): void
}

interface Window {
  monaco?: typeof import('monaco-editor')
  require?: AMDRequire
  MonacoEnvironment?: {
    getWorkerUrl?(workerId: string, label: string): string
    getWorker?(workerId: string, label: string): Worker
    [key: string]: unknown
  }
}
