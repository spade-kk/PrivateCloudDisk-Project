// ============================================================
// cloudflowLspClient.ts — Web Studio Monaco ↔ CloudFlow Language Server
// ============================================================
// [CLOUDFLOW-LS-WEB-001]
// Monaco 的 Monarch/TextMate 等静态规则仍由 syntax-highlight 生成；本桥接层只把
// 打开文档、编辑增量和动态语义请求转为 LSP JSON-RPC，绝不在浏览器复制 Rust Parser、
// AST、类型系统或 Capability Hub 权限逻辑。未配置 WebSocket 时调用方保持静态降级。
// ============================================================

export interface CloudFlowLspOptions {
  endpoint: string
  accessToken?: string
  tenantId?: string
  spaceId?: string
}

interface RpcPending {
  resolve: (value: any) => void
  reject: (error: Error) => void
}

type Disposable = { dispose(): void }

function asWsEndpoint(value: string): string {
  if (!value) return ''
  if (/^wss?:\/\//i.test(value)) return value
  const base = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${base}//${window.location.host}${value.startsWith('/') ? value : `/${value}`}`
}

function lspPosition(position: { lineNumber: number; column: number }) {
  return { line: Math.max(0, position.lineNumber - 1), character: Math.max(0, position.column - 1) }
}

function monacoRange(range: any) {
  return {
    startLineNumber: Number(range?.start?.line || 0) + 1,
    startColumn: Number(range?.start?.character || 0) + 1,
    endLineNumber: Number(range?.end?.line || 0) + 1,
    endColumn: Number(range?.end?.character || 0) + 1,
  }
}

function markerSeverity(monaco: any, severity: number): number {
  if (severity === 2) return monaco.MarkerSeverity.Warning
  if (severity === 3) return monaco.MarkerSeverity.Info
  if (severity === 4 && monaco.MarkerSeverity.Hint) return monaco.MarkerSeverity.Hint
  return monaco.MarkerSeverity.Error
}

function completionKind(monaco: any, kind: number): number {
  // LSP CompletionItemKind 与 Monaco 的 numeric enum 不保证逐项一致，显式映射。
  if (kind === 6) return monaco.languages.CompletionItemKind.Variable
  if (kind === 2 || kind === 3) return monaco.languages.CompletionItemKind.Function
  if (kind === 14) return monaco.languages.CompletionItemKind.Keyword
  return monaco.languages.CompletionItemKind.Text
}

/**
 * 仅在配置 LSP endpoint 时创建。连接失败会 reject，由调用方保留静态规则/静态补全，
 * 不会影响已经打开的 CloudFlow 编辑器。
 */
export class CloudFlowLspMonacoBridge {
  private socket: WebSocket | null = null
  private sequence = 0
  private readonly pending = new Map<number, RpcPending>()
  private readonly disposables: Disposable[] = []
  private opened = false
  private ready = false

  constructor(
    private readonly monaco: any,
    private readonly editor: any,
    private readonly options: CloudFlowLspOptions,
  ) {}

  async start(): Promise<void> {
    const endpoint = asWsEndpoint(this.options.endpoint)
    if (!endpoint) throw new Error('未配置 CloudFlow LS WebSocket 地址')
    await new Promise<void>((resolve, reject) => {
      const socket = new WebSocket(endpoint)
      const timer = window.setTimeout(() => {
        socket.close()
        reject(new Error('CloudFlow LS 连接超时'))
      }, 5000)
      socket.onopen = () => {
        window.clearTimeout(timer)
        this.socket = socket
        resolve()
      }
      socket.onerror = () => {
        window.clearTimeout(timer)
        reject(new Error('CloudFlow LS WebSocket 连接失败'))
      }
    })
    this.socket!.onmessage = (event) => this.receive(event.data)
    this.socket!.onclose = () => {
      this.ready = false
      this.failPending(new Error('CloudFlow LS 连接已关闭'))
    }
    await this.request('initialize', {
      processId: null,
      rootUri: null,
      capabilities: { textDocument: { completion: { completionItem: { snippetSupport: true } } } },
      initializationOptions: {
        // 浏览器 WebSocket 不能安全地自由设置 Authorization header；TLS/WSS +
        // 初始化消息传递短生命周期 Token，服务端不写日志/缓存原文。
        accessToken: this.options.accessToken || undefined,
        tenantId: this.options.tenantId || undefined,
        spaceId: this.options.spaceId || undefined,
      },
    })
    this.notify('initialized', {})
    this.openDocument()
    this.installProviders()
    this.ready = true
  }

  dispose(): void {
    if (this.opened && this.isOpen()) this.notify('textDocument/didClose', { textDocument: { uri: this.uri() } })
    this.opened = false
    this.ready = false
    this.disposables.splice(0).forEach((item) => item.dispose())
    this.socket?.close()
    this.socket = null
    this.failPending(new Error('CloudFlow LS bridge 已释放'))
  }

  /** 供宿主判断是否应清除本地 DSL 正则标记，避免与 LS 诊断重复。 */
  isReady(): boolean { return this.ready && this.isOpen() }

  /** 平台插件/权限事件到达后可调用；服务端只失效当前认证会话的能力缓存。 */
  refreshCapabilities(): void {
    if (this.isOpen()) this.notify('cloudflow/capabilitiesChanged', {})
  }

  private uri(): string {
    const model = this.editor.getModel?.()
    return model?.uri?.toString?.() || `inmemory://cloudflow/${encodeURIComponent(this.editor.getId?.() || 'workflow')}.flow`
  }

  private openDocument(): void {
    const model = this.editor.getModel?.()
    if (!model) return
    this.notify('textDocument/didOpen', {
      textDocument: { uri: this.uri(), languageId: 'cloudflow', version: model.getVersionId(), text: model.getValue() },
    })
    this.opened = true
    this.disposables.push(this.editor.onDidChangeModelContent((event: any) => {
      const current = this.editor.getModel?.()
      if (!current) return
      this.notify('textDocument/didChange', {
        textDocument: { uri: this.uri(), version: current.getVersionId() },
        contentChanges: (event.changes || []).map((change: any) => ({
          range: {
            start: { line: change.range.startLineNumber - 1, character: change.range.startColumn - 1 },
            end: { line: change.range.endLineNumber - 1, character: change.range.endColumn - 1 },
          },
          text: change.text,
        })),
      })
    }))
  }

  private installProviders(): void {
    this.disposables.push(this.monaco.languages.registerCompletionItemProvider('cloudflow', {
      triggerCharacters: [' ', '.', ':', '(', ','],
      provideCompletionItems: async (_model: any, position: any) => {
        const result = await this.safeRequest('textDocument/completion', { textDocument: { uri: this.uri() }, position: lspPosition(position) })
        const entries = Array.isArray(result) ? result : result?.items || []
        return { suggestions: entries.map((item: any) => ({
          label: item.label,
          kind: completionKind(this.monaco, item.kind),
          detail: item.detail || '',
          documentation: item.documentation?.value || item.documentation || '',
          insertText: item.insertText || item.label,
          insertTextRules: item.insertTextFormat === 2 ? this.monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
          range: item.textEdit?.range ? monacoRange(item.textEdit.range) : undefined,
        })) }
      },
    }))
    this.disposables.push(this.monaco.languages.registerHoverProvider('cloudflow', {
      provideHover: async (_model: any, position: any) => {
        const result = await this.safeRequest('textDocument/hover', { textDocument: { uri: this.uri() }, position: lspPosition(position) })
        if (!result?.contents) return null
        const contents = Array.isArray(result.contents) ? result.contents : [result.contents]
        return { contents: contents.map((item: any) => ({ value: item.value || item })), range: result.range ? monacoRange(result.range) : undefined }
      },
    }))
    this.disposables.push(this.monaco.languages.registerDefinitionProvider('cloudflow', {
      provideDefinition: async (_model: any, position: any) => {
        const result = await this.safeRequest('textDocument/definition', { textDocument: { uri: this.uri() }, position: lspPosition(position) })
        return (Array.isArray(result) ? result : []).map((item: any) => ({ uri: this.monaco.Uri.parse(item.uri), range: monacoRange(item.range) }))
      },
    }))
    this.disposables.push(this.monaco.languages.registerReferenceProvider('cloudflow', {
      provideReferences: async (_model: any, position: any, context: any) => {
        const result = await this.safeRequest('textDocument/references', {
          textDocument: { uri: this.uri() }, position: lspPosition(position), context: { includeDeclaration: !!context.includeDeclaration },
        })
        return (Array.isArray(result) ? result : []).map((item: any) => ({ uri: this.monaco.Uri.parse(item.uri), range: monacoRange(item.range) }))
      },
    }))
    this.disposables.push(this.monaco.languages.registerRenameProvider('cloudflow', {
      provideRenameEdits: async (_model: any, position: any, newName: string) => {
        const result = await this.safeRequest('textDocument/rename', { textDocument: { uri: this.uri() }, position: lspPosition(position), newName })
        const edits: Array<{ resource: any; edit: any }> = []
        Object.entries(result?.changes || {}).forEach(([uri, changes]: [string, any]) => {
          ;(changes || []).forEach((change: any) => edits.push({ resource: this.monaco.Uri.parse(uri), edit: { range: monacoRange(change.range), text: change.newText } }))
        })
        return { edits }
      },
      resolveRenameLocation: async (_model: any, position: any) => {
        const range = await this.safeRequest('textDocument/prepareRename', { textDocument: { uri: this.uri() }, position: lspPosition(position) })
        return range ? { range: monacoRange(range), text: this.editor.getModel()?.getValueInRange?.(monacoRange(range)) || '' } : undefined
      },
    }))
  }

  private receive(raw: unknown): void {
    let message: any
    try { message = JSON.parse(String(raw)) } catch { return }
    if (message.id !== undefined && message.id !== null) {
      const pending = this.pending.get(Number(message.id))
      if (!pending) return
      this.pending.delete(Number(message.id))
      if (message.error) pending.reject(new Error(message.error.message || 'CloudFlow LS 请求失败'))
      else pending.resolve(message.result)
      return
    }
    if (message.method === 'textDocument/publishDiagnostics') {
      const model = this.editor.getModel?.()
      if (!model || message.params?.uri !== this.uri()) return
      const markers = (message.params?.diagnostics || []).map((item: any) => ({
        severity: markerSeverity(this.monaco, item.severity),
        message: item.code ? `[${item.code}] ${item.message}` : item.message,
        ...monacoRange(item.range),
      }))
      this.monaco.editor.setModelMarkers(model, 'cloudflow-ls', markers)
    }
  }

  private request(method: string, params: unknown): Promise<any> {
    const id = ++this.sequence
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      try {
        this.send({ jsonrpc: '2.0', id, method, params })
      } catch (error) {
        this.pending.delete(id)
        reject(error instanceof Error ? error : new Error(String(error)))
      }
    })
  }

  private notify(method: string, params: unknown): void { this.send({ jsonrpc: '2.0', method, params }) }

  private async safeRequest(method: string, params: unknown): Promise<any> {
    try { return await this.request(method, params) } catch { return null }
  }

  private isOpen(): boolean { return this.socket?.readyState === WebSocket.OPEN }

  private send(value: unknown): void {
    if (!this.isOpen()) throw new Error('CloudFlow LS 尚未连接')
    this.socket.send(JSON.stringify(value))
  }

  private failPending(error: Error): void {
    this.pending.forEach((pending) => pending.reject(error))
    this.pending.clear()
  }
}
