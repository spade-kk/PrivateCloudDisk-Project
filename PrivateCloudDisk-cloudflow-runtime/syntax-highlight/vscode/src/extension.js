// ============================================================
// CloudFlow DSL VS Code 扩展 —— 静态规则宿主 + CloudFlow LS 客户端
// ============================================================
// [CLOUDFLOW-LS-VSCODE-001]
// syntax-highlight 仍是唯一的静态 TextMate / Snippet / 基础补全规范生成器；
// 本文件绝不实现 Parser、AST、类型系统或 Capability Hub 权限判断。可执行文件
// cloudflow-ls --stdio 复用 Compiler Frontend 并提供动态诊断、符号和能力智能。
// 当 LS 未配置、离线或登录失效时，下面的静态 provider 自动作为降级能力保留。
// ============================================================
'use strict'

const vscode = require('vscode')
const fs = require('fs')
const path = require('path')
const { LanguageClient, State } = require('vscode-languageclient/node')
const childProcess = require('child_process')

/** @type {Record<string, any>|null} */
let completionSpec = null
/** @type {import('vscode-languageclient/node').LanguageClient|null} */
let languageClient = null
let languageServerReady = false
let statusBar = null
let extensionContext = null

function loadCompletionSpec(context) {
  if (completionSpec) return completionSpec
  const file = path.join(context.extensionPath, 'syntaxes', 'cloudflow.completion.json')
  try {
    completionSpec = JSON.parse(fs.readFileSync(file, 'utf8'))
  } catch (err) {
    console.error('[cloudflow] 无法加载基础补全规范:', err, file)
    completionSpec = { items: [], errorCodes: {} }
  }
  return completionSpec
}

/** @param {string} kind @param {typeof vscode} vsc */
function toKind(kind, vsc) {
  switch (kind) {
    case 'keyword': return vsc.CompletionItemKind.Keyword
    case 'function': return vsc.CompletionItemKind.Function
    case 'type': return vsc.CompletionItemKind.Class
    case 'snippet': return vsc.CompletionItemKind.Snippet
    case 'constant': return vsc.CompletionItemKind.Constant
    case 'module': return vsc.CompletionItemKind.Module
    case 'variable': return vsc.CompletionItemKind.Variable
    default: return vsc.CompletionItemKind.Text
  }
}

/**
 * 仅生成离线降级建议。动态变量、步骤、类型、Capability Hub Action 均由 LS 提供，
 * 避免把静态生成规范误当作运行时语义系统。
 */
function buildSuggestions(document, spec, vsc) {
  const items = []
  const seen = new Set()
  const push = (label, kind, insertText, doc, detail, snippet) => {
    if (seen.has(label)) return
    seen.add(label)
    const item = new vsc.CompletionItem(label, toKind(kind || 'text', vsc))
    item.insertText = snippet && insertText && insertText.includes('${')
      ? new vsc.SnippetString(insertText)
      : insertText
    item.documentation = doc
    if (detail) item.detail = detail
    items.push(item)
  }
  for (const item of spec.items || []) {
    const label = item.label || item.id
    push(label, item.kind || 'text', item.insertText || label,
      item.documentation, item.detail, (item.insertTextRules || '') === 'snippet')
  }
  // 静态降级也可从当前文档安全地识别最基础的变量和步骤；它不会访问平台能力。
  const docText = document.getText()
  const variableNames = new Set()
  let inVariables = false
  for (const line of docText.split('\n')) {
    const trimmed = line.trim()
    if (trimmed === 'variables {') inVariables = true
    else if (inVariables && trimmed === '}') inVariables = false
    else if (inVariables) {
      const match = trimmed.match(/^([A-Za-z_][A-Za-z0-9_-]*)\s*[:=]/)
      if (match) variableNames.add(match[1])
    }
  }
  for (const name of variableNames) push(`vars.${name}`, 'variable', `vars.${name}`, '当前文档变量（离线降级）', 'variable')
  const stepIds = new Set()
  for (const line of docText.split('\n')) {
    const match = line.trim().match(/^step\s+([A-Za-z_][A-Za-z0-9_-]*)\s*\{/)
    if (match) stepIds.add(match[1])
  }
  for (const id of stepIds) push(`steps.${id}.output`, 'module', `steps.${id}.output`, '当前文档步骤输出（离线降级）', 'step output')
  return { items }
}

function isLanguageServerEnabled() {
  return vscode.workspace.getConfiguration('cloudflow.lsp').get('enabled', true)
}

function updateStatus(text, command, tooltip) {
  if (!statusBar) return
  statusBar.text = text
  statusBar.command = command
  statusBar.tooltip = tooltip
  statusBar.show()
}

async function currentToken() {
  return extensionContext ? extensionContext.secrets.get('cloudflow.accessToken') : undefined
}

function bundledServerPath(context) {
  const executable = process.platform === 'win32' ? 'cloudflow-ls.exe' : 'cloudflow-ls'
  const platformDirectory = `${process.platform}-${process.arch}`
  const candidates = [
    path.join(context.extensionPath, 'bin', platformDirectory, executable),
    path.join(context.extensionPath, 'bin', executable),
  ]
  return candidates.find((candidate) => fs.existsSync(candidate)) || null
}

function resolveServerCommand(context) {
  const configured = String(vscode.workspace.getConfiguration('cloudflow.lsp').get('serverPath', 'bundled') || '').trim()
  if (configured && configured.toLowerCase() !== 'bundled') {
    const home = process.env.HOME || process.env.USERPROFILE || ''
    return configured.replace(/^~(?=\/|\\)/, home)
  }
  return bundledServerPath(context) || 'cloudflow-ls'
}

function serverOptions() {
  const configuration = vscode.workspace.getConfiguration('cloudflow.lsp')
  const command = resolveServerCommand(extensionContext)
  const args = ['--stdio']
  const tokenFile = configuration.get('tokenFile', '')
  const environment = { ...process.env }
  if (tokenFile) environment.CLOUDFLOW_TOKEN_FILE = tokenFile
  return async () => {
    const token = await currentToken()
    if (token) environment.CLOUDFLOW_TOKEN = token
    else delete environment.CLOUDFLOW_TOKEN
    // LanguageClient 接管该 ChildProcess 的 stdin/stdout 生命周期；不使用 shell，
    // 避免路径或工作区配置注入额外命令。清理能力缓存由
    // vscode-languageclient 根据 LS 的 executeCommandProvider 自动注册，不能在此
    // 再次 registerCommand，否则会触发 “command already exists” 初始化失败。
    return childProcess.spawn(command, args, { env: environment, shell: false })
  }
}

async function startLanguageServer() {
  if (!extensionContext || !isLanguageServerEnabled()) {
    languageServerReady = false
    updateStatus('$(code) CloudFlow：静态规则', 'cloudflow.login', 'CloudFlow LS 已禁用；使用 syntax-highlight 静态规则')
    return
  }
  if (languageClient) return
  languageServerReady = false
  const command = resolveServerCommand(extensionContext)
  updateStatus('$(sync~spin) CloudFlow LS：连接中', undefined, `正在启动 ${command} --stdio`)
  const configuration = vscode.workspace.getConfiguration('cloudflow.lsp')
  const clientOptions = {
    documentSelector: [
      { language: 'cloudflow', scheme: 'file' },
      { language: 'yaml', scheme: 'file', pattern: '**/*.{flow,workflow}.yaml' },
      { language: 'yaml', scheme: 'file', pattern: '**/*.{flow,workflow}.yml' },
    ],
    initializationOptions: {
      tenantId: configuration.get('tenantId', ''),
      spaceId: configuration.get('spaceId', ''),
    },
    outputChannelName: 'CloudFlow Language Server',
    traceOutputChannel: vscode.window.createOutputChannel('CloudFlow LSP Trace'),
  }
  const client = new LanguageClient('cloudflowLanguageServer', 'CloudFlow Language Server', serverOptions(), clientOptions)
  client.onDidChangeState((event) => {
    if (event.newState === State.Running) {
      languageServerReady = true
      updateStatus('$(check) CloudFlow LS：已连接', 'cloudflow.showCapabilities', '动态诊断、类型、符号和已授权能力补全已启用')
    } else if (event.newState === State.Stopped) {
      languageServerReady = false
      updateStatus('$(warning) CloudFlow：静态降级', 'cloudflow.restartLanguageServer', 'CloudFlow LS 未连接，当前使用静态规则')
    }
  })
  languageClient = client
  try {
    await client.start()
  } catch (error) {
    languageClient = null
    languageServerReady = false
    updateStatus('$(warning) CloudFlow：静态降级', 'cloudflow.restartLanguageServer', 'CloudFlow LS 启动失败；保留静态语法和补全')
    vscode.window.showWarningMessage(`CloudFlow LS 无法启动，已回退静态规则：${String(error)}`)
  }
}

async function stopLanguageServer() {
  const client = languageClient
  languageClient = null
  languageServerReady = false
  if (client) await client.stop()
}

async function restartLanguageServer() {
  await stopLanguageServer()
  await startLanguageServer()
}

function registerStaticFallbackProviders(context, spec) {
  context.subscriptions.push(
    vscode.languages.registerCompletionItemProvider(
      { language: 'cloudflow', scheme: 'file' },
      {
        provideCompletionItems(document) {
          // LS 已连接时只由它给出完整语义结果，避免重复动态能力补全。
          return languageServerReady ? undefined : buildSuggestions(document, spec, vscode)
        },
      },
      ' ', '.', '"', ':'
    ),
    vscode.languages.registerSignatureHelpProvider(
      { language: 'cloudflow', scheme: 'file' },
      {
        provideSignatureHelp(document, position) {
          if (languageServerReady) return null
          const lineText = document.lineAt(position.line).text
          const functionMatch = lineText.slice(0, position.character).match(/([A-Za-z_][A-Za-z0-9_-]*)\s*\($/)
          if (!functionMatch) return null
          const fn = (spec.builtinFunctions || []).find((item) => item.name === functionMatch[1])
          if (!fn) return null
          const parameters = (fn.parameters || []).map((parameter) => parameter.label || parameter)
          const help = new vscode.SignatureHelp()
          help.signatures = [{
            label: fn.signature || functionMatch[1],
            documentation: fn.doc,
            parameters: parameters.map((label) => ({ label })),
          }]
          help.activeSignature = 0
          help.activeParameter = 0
          return help
        },
      },
      '(', ','
    )
  )
}

function registerCommands(context) {
  context.subscriptions.push(
    vscode.commands.registerCommand('cloudflow.login', async () => {
      const loginUrl = vscode.workspace.getConfiguration('cloudflow.auth').get('loginUrl', '')
      if (loginUrl) await vscode.env.openExternal(vscode.Uri.parse(loginUrl))
      const token = await vscode.window.showInputBox({
        title: '登录 CloudFlow 平台',
        prompt: loginUrl ? '请在浏览器完成 OAuth2 授权后粘贴 Access Token' : '粘贴 CloudFlow Access Token（可在设置 cloudflow.auth.loginUrl 配置 OAuth2 授权页）',
        password: true,
        ignoreFocusOut: true,
        validateInput: (value) => value.trim() ? undefined : 'Access Token 不能为空',
      })
      if (!token || !extensionContext) return
      await extensionContext.secrets.store('cloudflow.accessToken', token.trim())
      await restartLanguageServer()
      vscode.window.showInformationMessage('CloudFlow Access Token 已保存到 VS Code Secret Storage。')
    }),
    vscode.commands.registerCommand('cloudflow.logout', async () => {
      if (extensionContext) await extensionContext.secrets.delete('cloudflow.accessToken')
      await restartLanguageServer()
      vscode.window.showInformationMessage('已退出 CloudFlow 平台；LS 将仅提供无需平台能力的本地语义与静态降级。')
    }),
    vscode.commands.registerCommand('cloudflow.showCapabilities', async () => {
      if (!languageServerReady) {
        vscode.window.showWarningMessage('CloudFlow LS 未连接；当前仅可使用静态规则。')
        return
      }
      vscode.window.showInformationMessage(
        'CloudFlow LS 已连接：补全仅展示当前用户/租户/空间获授权的 Capability Hub 能力。'
      )
    }),
    vscode.commands.registerCommand('cloudflow.restartLanguageServer', restartLanguageServer)
  )
}

function activate(context) {
  extensionContext = context
  const spec = loadCompletionSpec(context)
  statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100)
  context.subscriptions.push(statusBar)
  registerStaticFallbackProviders(context, spec)
  registerCommands(context)
  void startLanguageServer()
}

async function deactivate() {
  await stopLanguageServer()
}

module.exports = {
  activate,
  deactivate,
  buildSuggestions,
  loadCompletionSpec,
}
