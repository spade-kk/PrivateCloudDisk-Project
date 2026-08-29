import MarkdownIt from 'markdown-it'
import { full as emoji } from 'markdown-it-emoji'
import taskLists from 'markdown-it-task-lists'
import hljs from 'highlight.js/lib/common'
import DOMPurify from 'dompurify'

export type AiMarkdownRenderer = ReturnType<typeof createAiMarkdown>

const SAFE_LANGUAGE = /^[a-z0-9_+-]{1,32}$/i

const AI_MARKDOWN_CONFIG = {
  ALLOWED_TAGS: [
    'a', 'abbr', 'b', 'br', 'button', 'code', 'del', 'details', 'div', 'em', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'hr', 'i', 'input', 'label', 'li', 'mark', 'ol', 'p', 'pre', 's', 'small', 'span', 'strong', 'summary', 'table',
    'tbody', 'td', 'th', 'thead', 'tr', 'u', 'ul', 'blockquote', 'dl', 'dt', 'dd', 'svg', 'g', 'path', 'rect', 'text',
    'tspan', 'line', 'circle', 'ellipse', 'polygon', 'polyline', 'defs', 'marker', 'use', 'linearGradient', 'radialGradient',
    'stop', 'clipPath', 'foreignObject', 'switch', 'symbol', 'title', 'desc', 'math', 'semantics', 'annotation', 'mrow',
    'mfrac', 'msqrt', 'mi', 'mo', 'mn', 'mspace', 'mtext', 'mstyle', 'merror', 'mphantom', 'menclose', 'mpadded', 'mover',
    'munder', 'munderover', 'msub', 'msup', 'msubsup', 'mtable', 'mtr', 'mtd', 'mlabeledtr', 'mstack', 'msgroup', 'msrow',
  ],
  ALLOWED_ATTR: [
    'aria-label', 'aria-expanded', 'aria-hidden', 'aria-live', 'class', 'checked', 'colspan', 'data-*', 'disabled', 'd',
    'fill', 'fill-opacity', 'fill-rule', 'height', 'href', 'id', 'lang', 'marker-end', 'marker-start', 'name', 'open',
    'points', 'rel', 'role', 'r', 'rx', 'ry', 'scope', 'stroke', 'stroke-dasharray', 'stroke-linecap', 'stroke-linejoin',
    'stroke-opacity', 'stroke-width', 'style', 'target', 'text-anchor', 'title', 'transform', 'type', 'viewBox', 'width',
    'x', 'x1', 'x2', 'y', 'y1', 'y2',
  ],
  ALLOW_DATA_ATTR: true,
  ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto|tel):|[^a-z]|[a-z+.-]+(?:[^a-z+.-:]|$))/i,
  ADD_ATTR: ['target', 'rel'],
  USE_PROFILES: { html: true },
  SANITIZE_DOM: true,
}

const SVG_CONFIG = {
  USE_PROFILES: { svg: true, svgFilters: true },
  ALLOW_DATA_ATTR: false,
  ADD_ATTR: ['class', 'aria-label', 'role', 'viewBox'],
}

function escapeAttribute(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

export function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;')
}

function safeLanguage(value: string): string {
  const normalized = value.trim().toLowerCase()
  return SAFE_LANGUAGE.test(normalized) ? normalized : ''
}

function highlightCode(code: string, language: string): string {
  const lang = safeLanguage(language)
  if (lang && hljs.getLanguage(lang)) {
    try {
      return hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
    } catch {
      return escapeHtml(code)
    }
  }
  return escapeHtml(code)
}

function installMathRules(md: any): void {
  md.inline.ruler.before('escape', 'ai_math_inline', (state: any, silent: boolean) => {
    const start = state.pos
    const source = state.src
    const marker = source[start] === '$' ? '$' : source.slice(start, start + 2) === '\\('
      ? '\\('
      : ''
    if (!marker || (marker === '$' && source[start + 1] === '$')) return false
    const close = marker === '$' ? '$' : '\\)'
    const end = source.indexOf(close, start + marker.length)
    if (end <= start + marker.length || /\n/.test(source.slice(start + marker.length, end))) return false
    if (!silent) {
      const token = state.push('ai_math_inline', 'span', 0)
      token.content = source.slice(start + marker.length, end)
    }
    state.pos = end + close.length
    return true
  })

  md.block.ruler.before('fence', 'ai_math_block', (state: any, startLine: number, endLine: number, silent: boolean) => {
    const start = state.bMarks[startLine] + state.tShift[startLine]
    const end = state.eMarks[startLine]
    const firstLine = state.src.slice(start, end).trim()
    const marker = firstLine.startsWith('$$') ? '$$' : firstLine.startsWith('\\[') ? '\\[' : ''
    if (!marker) return false
    const close = marker === '$$' ? '$$' : '\\]'
    let nextLine = startLine
    let content = firstLine.slice(marker.length).trim()
    if (content.endsWith(close) && content.length > close.length) {
      content = content.slice(0, -close.length).trim()
    } else {
      content = ''
      nextLine = startLine + 1
      while (nextLine < endLine) {
        const lineStart = state.bMarks[nextLine] + state.tShift[nextLine]
        const lineEnd = state.eMarks[nextLine]
        const line = state.src.slice(lineStart, lineEnd)
        if (line.trim() === close) break
        content += `${line}\n`
        nextLine += 1
      }
      if (nextLine >= endLine) return false
    }
    if (silent) return true
    const token = state.push('ai_math_block', 'div', 0)
    token.block = true
    token.map = [startLine, nextLine + 1]
    token.content = content.trim()
    state.line = nextLine + 1
    return true
  })

  md.renderer.rules.ai_math_inline = (tokens: any[], index: number) => {
    const value = tokens[index].content
    return `<span class="ai-math ai-math-inline" data-math="${escapeAttribute(value)}">${escapeHtml(value)}</span>`
  }
  md.renderer.rules.ai_math_block = (tokens: any[], index: number) => {
    const value = tokens[index].content
    return `<div class="ai-math ai-math-block" data-math="${escapeAttribute(value)}" data-math-display="true">${escapeHtml(value)}</div>`
  }
}

function createFenceRenderer(md: any): void {
  md.renderer.rules.fence = (tokens: any[], index: number) => {
    const token = tokens[index]
    const language = safeLanguage((token.info || '').split(/\s+/)[0])
    const source = token.content.replace(/\n$/, '')
    if (language === 'mermaid' || language === 'mmd') {
      return `<div class="ai-mermaid" data-ai-mermaid><div class="ai-mermaid-canvas" aria-label="Mermaid 图表"></div><pre class="ai-mermaid-source"><code>${escapeHtml(source)}</code></pre><p class="ai-mermaid-status" aria-live="polite">正在绘制图表…</p></div>`
    }
    const languageClass = language ? ` language-${escapeAttribute(language)}` : ''
    const label = language || 'text'
    const highlighted = highlightCode(source, language)
    return `<div class="ai-code-block" data-ai-code-block><div class="ai-code-toolbar"><span class="ai-code-language">${escapeHtml(label)}</span><button type="button" data-copy-code aria-label="复制代码" title="复制代码">复制</button></div><pre class="ai-code-content"><code class="hljs${languageClass}">${highlighted}</code></pre></div>`
  }
}

function createTableRenderer(md: any): void {
  md.renderer.rules.table_open = () => '<div class="ai-table-shell" data-ai-table><div class="ai-table-toolbar"><span class="ai-table-label">表格</span><div class="ai-table-actions"><button type="button" data-copy-table aria-label="复制表格" title="复制表格">复制</button><button type="button" data-expand-table aria-label="展开表格" title="展开表格">展开</button></div></div><div class="ai-table-scroll"><table>'
  md.renderer.rules.table_close = () => '</table></div></div>'
}

function createLinkRenderer(md: any): void {
  const defaultLinkOpen = md.renderer.rules.link_open || ((tokens: any[], index: number, options: any) => md.renderer.renderToken(tokens, index, options))
  md.renderer.rules.link_open = (tokens: any[], index: number, options: any, env: any) => {
    const token = tokens[index]
    const href = token.attrGet('href') || ''
    if (/^(?:https?:|mailto:|tel:)/i.test(href)) {
      token.attrSet('target', '_blank')
      token.attrSet('rel', 'noreferrer noopener')
    }
    return defaultLinkOpen(tokens, index, options, env)
  }
}

export function createAiMarkdown(): any {
  const md = new MarkdownIt({
    html: false,
    breaks: true,
    linkify: true,
    typographer: true,
    langPrefix: 'language-',
  })
  md.use(emoji)
  md.use(taskLists, { enabled: true })
  installMathRules(md)
  createFenceRenderer(md)
  createTableRenderer(md)
  createLinkRenderer(md)
  return md
}

const markdown = createAiMarkdown()

export function sanitizeAiMarkdownHtml(html: string): string {
  return String(DOMPurify.sanitize(html, AI_MARKDOWN_CONFIG as any))
}

export function renderAiMarkdown(value: string): string {
  if (!value) return ''
  return sanitizeAiMarkdownHtml(markdown.render(value))
}

export function tableToText(table: HTMLTableElement | null): string {
  if (!table) return ''
  return [...table.rows].map((row) => [...row.cells].map((cell) => (cell.textContent || '').replace(/\s+/g, ' ').trim()).join('\t')).join('\n')
}

export function sanitizeMermaidSvg(svg: string): string {
  return String(DOMPurify.sanitize(svg, SVG_CONFIG as any))
}
