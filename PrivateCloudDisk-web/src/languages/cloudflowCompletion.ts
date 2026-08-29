// ============================================================
// cloudflowCompletion.ts — CloudFlow DSL 代码补全与结构提示（需求 15.x）
// ============================================================
// 设计：
//   - 补全规则不在此处硬编码，而是引用生成自 GRAMMAR.pest + AST.rs
//     的统一补全规范 cloudflow.completion.json（唯一事实来源）。
//   - 运行时动态来源（capabilities / vars / steps）由调用方通过 options 注入，
//     规范内仅提供静态类别（关键字、结构、块、类型、内置函数、触发器、片段、错误码）。
//
// 更新流程：运行 runtime 的 generate.py 并把新产物复制到
//   src/languages/cloudflow.completion.json，无需改动本文件。
// ============================================================

// eslint-disable-next-line @typescript-eslint/no-explicit-any
import cloudflowCompletion from '@/languages/cloudflow.completion.json'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export interface CloudFlowCompletionOptions {
  capabilities?: Array<{ capabilityKey: string; description?: string }>
  /** 额外的变量名（如循环变量 item / catch error 等作用域变量）。 */
  variables?: string[]
  /** 已定义步骤 id 列表。 */
  steps?: string[]
}

type Mono = typeof import('monaco-editor')

const SPEC = cloudflowCompletion as {
  items?: Array<{ id: string; label: string; kind: string; category: string; insertText: string; insertTextRules?: string; documentation?: string; detail?: string; parameters?: Array<{ label: string; documentation?: string }> }>
  builtinFunctions?: Array<{ name: string; signature: string; documentation?: string; parameters?: Array<{ label: string; documentation?: string }>; returnType?: string }>
  errorCodes?: Record<string, string>
  retryExceptions?: string[]
}

function monacoKind(monaco: Mono, kind: string): number {
  const k = monaco.languages.CompletionItemKind
  switch (kind) {
    case 'keyword': return k.Keyword
    case 'function': return k.Function
    case 'snippet': return k.Snippet
    case 'type': return k.Class
    case 'constant': return k.Constant
    case 'module': return k.Module
    case 'variable': return k.Variable
    default: return k.Text
  }
}

/**
 * 解析文档中的 CloudFlow 变量（vars 块声明）与步骤（step <id>）。
 * 作用域变量（foreach item / catch error）由调用方通过 options.variables 注入。
 */
function scanModel(value: string): { variables: string[]; steps: string[] } {
  const variables: string[] = []
  const steps: string[] = []
  let inVars = false
  for (const raw of value.split(/\r?\n/)) {
    const line = raw.trim()
    if (line === 'variables {') { inVars = true; continue }
    if (inVars) {
      if (line === '}') { inVars = false; continue }
      const v = line.match(/^([A-Za-z_][A-Za-z0-9_-]*)\s*[:=]/)
      if (v) variables.push(v[1])
      continue
    }
    const s = line.match(/^step\s+([A-Za-z_][A-Za-z0-9_-]*)\s*\{/)
    if (s) steps.push(s[1])
  }
  return { variables, steps }
}

/**
 * 注册 CloudFlow 补全 + 签名帮助（幂等，可多次调用；disposable 交给调用方 dispose）。
 * @param monaco 已加载的 Monaco 实例
 * @param options 运行时动态来源（capabilities / variables / steps）
 * @returns 可 dispose 的注册项数组
 */
export function registerCloudFlowCompletion(
  monaco: Mono,
  options: CloudFlowCompletionOptions = {},
): Array<{ dispose(): void }> {
  const disposables: Array<{ dispose(): void }> = []

  disposables.push(monaco.languages.registerCompletionItemProvider('cloudflow', {
    triggerCharacters: [' ', '.', '"', '(', ':'],
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position)
      const linePrefix = model.getValueInRange({ startLineNumber: position.lineNumber, startColumn: 1, endLineNumber: position.lineNumber, endColumn: position.column })
      const typed = word.word || ''
      const suggestions: import('monaco-editor').languages.CompletionItem[] = []
      const seen = new Set<string>()

      const push = (
        label: string,
        kindName: string,
        insertText: string,
        doc?: string,
        detail?: string,
        snippet = false,
      ) => {
        if (!label || seen.has(label)) return
        // 前缀过滤（大小写不敏感）
        if (typed && !label.toLowerCase().startsWith(typed.toLowerCase()) && !(label + '.').toLowerCase().startsWith(typed.toLowerCase())) {
          if (!label.includes(typed.toLowerCase())) return
        }
        seen.add(label)
        suggestions.push({
          label,
          kind: monacoKind(monaco, kindName),
          insertText,
          insertTextRules: snippet ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
          documentation: doc || '',
          detail: detail || '',
          range: {
            startLineNumber: position.lineNumber,
            startColumn: word.startColumn,
            endLineNumber: position.lineNumber,
            endColumn: word.endColumn,
          },
        })
      }

      // 静态类别来自统一补全规范（关键字/块/结构/类型/片段）
      for (const item of SPEC.items || []) {
        push(item.label, item.kind || 'text', item.insertText, item.documentation, item.detail, (item.insertTextRules || '') === 'snippet')
      }

      // 动态：文档变量 + 传入作用域变量（需求 15.11 / 15.13）
      const scanned = scanModel(model.getValue())
      const varNames = [...scanned.variables, ...(options.variables || [])]
      if (linePrefix.trim() === 'vars.' || /vars\.$/.test(linePrefix) || /vars\.[A-Za-z_][A-Za-z0-9_-]*$/.test(linePrefix)) {
        for (const name of new Set(varNames)) {
          push(`vars.${name}`, 'variable', `vars.${name}`, '当前作用域变量引用', 'variable')
        }
      }

      // 动态：步骤输出引用（需求 15.12 / 15.19）
      const stepIds = [...scanned.steps, ...(options.steps || [])]
      if (/steps\.$/.test(linePrefix) || /steps\.[A-Za-z_][A-Za-z0-9_-]*\.outp?u?t?$/.test(linePrefix)) {
        for (const id of new Set(stepIds)) {
          push(`steps.${id}.output`, 'module', `steps.${id}.output`, '步骤输出引用', 'step output')
        }
      }

      // 动态：depends_on 后提示已定义步骤 ID（需求 15.19）
      if (/^\s*depends_on\s+[A-Za-z_][A-Za-z0-9_-]*$/.test(linePrefix)) {
        for (const id of new Set(stepIds)) {
          push(id, 'module', id, '条件依赖的步骤 ID', 'depends_on')
        }
      }

      // 动态：retry_on 数组内提示可重试异常白名单
      if (/retry_on\s*\[/.test(linePrefix) || /retry_on\s*\[[A-Za-z]*$/.test(linePrefix)) {
        for (const exc of (SPEC.retryExceptions || [])) {
          push(exc, 'keyword', exc, '可重试异常类型（白名单）', 'retry_on')
        }
      }

      // 动态：能力补全（需求 15.14-15.15），使用 Capability Hub schema 的运行时来源
      if (/action\s+$/.test(linePrefix)) {
        for (const cap of options.capabilities || []) {
          push(cap.capabilityKey, 'function', cap.capabilityKey, cap.description, 'capability')
        }
      }
      if (/action\s+plugin\.$/.test(linePrefix)) {
        for (const cap of options.capabilities || []) {
          if (cap.capabilityKey.startsWith('plugin.')) {
            push(cap.capabilityKey, 'function', cap.capabilityKey, cap.description, 'plugin capability')
          }
        }
      }

      return { suggestions }
    },
  }))

  disposables.push(monaco.languages.registerSignatureHelpProvider('cloudflow', {
    triggerCharacters: ['(', ','],
    signatureHelpTriggerCharacters: ['(', ','],
    provideSignatureHelp: (model, position) => {
      const lineText = model.getLineContent(position.lineNumber)
      const prefix = lineText.slice(0, position.column - 1)
      const m = prefix.match(/([A-Za-z_][A-Za-z0-9_-]*)\s*\($/)
      if (!m) return null
      const fn = (SPEC.builtinFunctions || []).find((f) => f.name === m[1])
      if (!fn) return null
      return {
        signatures: [{
          label: fn.signature || `${fn.name}()`,
          documentation: {
            value: fn.documentation || `CloudFlow 内置函数 ${fn.name}。`,
          },
          parameters: (fn.parameters || []).map((p) => ({
            label: p.label,
            documentation: { value: p.documentation || '' },
          })),
        }],
        activeSignature: 0,
        activeParameter: 0,
      }
    },
  }))

  return disposables
}
