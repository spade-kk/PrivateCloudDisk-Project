// ============================================================
// CloudFlow DSL Language 扩展入口 —— 代码补全 + 签名帮助（需求 15.26）
// ============================================================
// 由统一补全规范 cloudflow.completion.json 驱动（唯一事实来源），
// 该文件由 syntax-highlight/generator/completion_builder.py 从
// GRAMMAR.pest + AST.rs 自动生成。改 DSL 后运行 generate.py 重新同步。
//
// 纯 JavaScript，无需编译；vsce package 可直接打包。
// ============================================================
'use strict'

const vscode = require('vscode')
const fs = require('fs')
const path = require('path')

/** @type {Record<string, any>|null} */
let completionSpec = null

function loadCompletionSpec(context) {
  if (completionSpec) return completionSpec
  const file = path.join(context.extensionPath, 'syntaxes', 'cloudflow.completion.json')
  try {
    completionSpec = JSON.parse(fs.readFileSync(file, 'utf8'))
  } catch (err) {
    console.error('[cloudflow] 无法加载补全规范:', err, file)
    completionSpec = { items: [], errorCodes: {} }
  }
  return completionSpec
}

/**
 * 把规范中的 kind 名字映射为 VS Code CompletionItemKind 数值。
 * @param {string} kind
 * @param {typeof vscode} vsc
 * @returns {number}
 */
function toKind(kind, vsc) {
  switch (kind) {
    case 'keyword': return vsc.CompletionItemKind.Keyword
    case 'function': return vsc.CompletionItemKind.Function
    case 'type': return vsc.CompletionItemKind.Class
    case 'snippet': return vsc.CompletionItemKind.Snippet
    case 'constant': return vsc.CompletionItemKind.Constant
    case 'module': return vsc.CompletionItemKind.Module
    default: return vsc.CompletionItemKind.Text
  }
}

/**
 * 生成补全项列表：关键字/块/结构/类型 来自规范；
 * variables / steps / capabilities 由运行时按当前文档动态解析（需求 15.11-15.15）。
 * @param {import('vscode').TextDocument} document
 * @param {any} spec
 * @param {typeof vscode} vsc
 */
function buildSuggestions(document, spec, vsc) {
  const items = []
  const seen = new Set()

  const push = (label, kind, insertText, doc, detail, snippet) => {
    if (seen.has(label)) return
    seen.add(label)
    const c = new vsc.CompletionItem(label, toKind(kind || 'text', vsc))
    c.insertText = snippet && insertText && insertText.includes('${')
      ? new vsc.SnippetString(insertText)
      : insertText
    c.documentation = doc
    if (detail) c.detail = detail
    items.push(c)
  }

  for (const it of spec.items || []) {
    const label = it.label || it.id
    push(label, it.kind || 'text', it.insertText || label,
      it.documentation, it.detail, (it.insertTextRules || '') === 'snippet')
  }

  // 运行时变量：从文档 vars 声明提取（需求 15.11）
  const docText = document.getText()
  const varNames = new Set()
  let inVars = false
  for (const line of docText.split('\n')) {
    const t = line.trim()
    if (t === 'variables {') inVars = true
    else if (inVars && t === '}') inVars = false
    else if (inVars) {
      const m = t.match(/^([A-Za-z_][A-Za-z0-9_-]*)\s*[:=]/)
      if (m) varNames.add(m[1])
    }
  }
  for (const name of varNames) {
    push(`vars.${name}`, 'variable', `vars.${name}`, '当前作用域变量引用', 'variable')
  }

  // 运行时步骤（需求 15.12 / 15.19）
  const stepIds = new Set()
  for (const line of docText.split('\n')) {
    const m = line.trim().match(/^step\s+([A-Za-z_][A-Za-z0-9_-]*)\s*\{/)
    if (m) stepIds.add(m[1])
  }
  for (const id of stepIds) {
    push(`steps.${id}.output`, 'module', `steps.${id}.output`, '步骤输出引用', 'step output')
  }

  // 能力补全（需求 15.14）：规范内 capability 为动态占位，无运行时 schema 时至少提示 action。
  if (!docText.split('\n').some((l) => /^action /.test(l.trim()))) {
    push('action', 'keyword', 'action ', '调用 Capability Hub 能力', 'action')
  }

  return { items }
}

function activate(context) {
  const spec = loadCompletionSpec(context)
  context.subscriptions.push(
    vscode.languages.registerCompletionItemProvider(
      { language: 'cloudflow', scheme: 'file' },
      {
        provideCompletionItems(document) {
          return buildSuggestions(document, spec, vscode)
        },
      },
      ' ', '.', '"'
    ),
    vscode.languages.registerSignatureHelpProvider(
      { language: 'cloudflow', scheme: 'file' },
      {
        provideSignatureHelp(document, position) {
          const lineText = document.lineAt(position.line).text
          const fnMatch = lineText.slice(0, position.character).match(/([A-Za-z_][A-Za-z0-9_-]*)\s*\($/)
          if (!fnMatch) return null
          const fn = (spec.builtinFunctions || []).find((f) => f.name === fnMatch[1])
          if (!fn) return null
          const params = (fn.parameters || []).map((p) => p.label || p)
          const help = new vscode.SignatureHelp()
          help.signatures = [{
            label: fn.signature || fnMatch[1],
            documentation: fn.doc,
            parameters: params.map((p) => ({ label: p })),
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

function deactivate() {}

module.exports = { activate, deactivate, buildSuggestions, loadCompletionSpec }
