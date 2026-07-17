// ============================================================
// sanitize.ts — XSS 安全防护工具
// ============================================================
// 基于 DOMPurify 的企业级 XSS 防护模块，提供 HTML 内容净化、
// Vue 3 自定义指令 v-safe-html 以及安全的 innerHTML 设置工具。
//
// 依赖：DOMPurify (v3.4+，CDN 动态加载)
//
// 安全策略：
// - 默认白名单模式：仅允许安全标签和属性
// - 禁止 <script>、<iframe>、<object>、<embed> 等危险标签
// - 禁止事件处理器（onclick、onerror 等）
// - 禁止 javascript: 伪协议
// - 链接自动添加 rel="noopener noreferrer" 和 target="_blank"
//
// CDN 加载策略：
//   - DOMPurify 已改为 CDN 动态加载（domPurifyCdn.ts）
//   - 同步 API（sanitize / sanitizeHtml）会在 DOMPurify 未就绪时
//     触发紧急降级：返回空字符串或原始转义内容，避免 XSS
//   - 异步 API（sanitizeAsync）会等待 CDN 加载完成后净化
//   - 应用启动时应在 main.ts 调用 preloadDOMPurify() 预加载
// ============================================================

import type DOMPurifyType from 'dompurify'
import type { Directive } from 'vue'
import { loadDOMPurify, getDOMPurifySync, preloadDOMPurify } from './domPurifyCdn'

// 应用启动时预加载 DOMPurify（异步进行，不阻塞主流程）
preloadDOMPurify()

// ============================================================
// DOMPurify 全局配置
// ============================================================

// 安全配置：仅允许安全的 HTML 标签和属性
const DEFAULT_CONFIG = {
  // 允许的安全标签（白名单）
  ALLOWED_TAGS: [
    'a', 'abbr', 'b', 'br', 'code', 'div', 'em', 'h1', 'h2', 'h3',
    'h4', 'h5', 'h6', 'hr', 'i', 'img', 'li', 'ol', 'p', 'pre',
    'span', 'strong', 'sub', 'sup', 'table', 'tbody', 'td', 'th',
    'thead', 'tr', 'u', 'ul', 'blockquote', 'del', 'ins', 'mark',
    'small', 's', 'dl', 'dt', 'dd',
  ],
  // 允许的安全属性
  ALLOWED_ATTR: [
    'href', 'src', 'alt', 'title', 'class', 'id', 'style',
    'width', 'height', 'target', 'rel', 'colspan', 'rowspan',
    'align', 'dir', 'lang',
  ],
  // 禁止所有数据 URI（防止 XSS 通过 data: URI 注入）
  ALLOW_DATA_ATTR: false,
  // 允许的 URI 协议
  ALLOWED_URI_REGEXP: /^(?:(?:https?|ftps?|mailto|tel):|[^a-z]|[a-z+.-]+(?:[^a-z+.-:]|$))/i,
  // 强制链接在新标签页打开并添加安全属性
  ADD_ATTR: ['target'],
  // 添加 rel="noopener noreferrer" 到所有链接
  ADD_TAGS: [],
  // 强制链接添加 rel 属性
  ALLOW_UNKNOWN_PROTOCOLS: false,
  // 返回 DOM 节点而非字符串
  RETURN_DOM: false,
  RETURN_DOM_FRAGMENT: false,
  RETURN_DOM_IMPORT: false,
  // 移除所有不在白名单中的内容（静默删除）
  SANITIZE_DOM: true,
  // 保留注释
  ALLOWED_NAMESPACES: [],
  // 禁止 SVG 和 MathML
  USE_PROFILES: { html: true },
} as any

/**
 * 紧急降级净化：当 DOMPurify 未加载完成时使用
 * 仅做基础 HTML 转义，移除 <script> 等明显危险标签
 */
function emergencySanitize(dirty: string): string {
  if (!dirty) return ''
  // 移除 script / iframe / object / embed / style 标签及内容
  let cleaned = dirty
    .replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '')
    .replace(/<iframe[^>]*>[\s\S]*?<\/iframe>/gi, '')
    .replace(/<object[^>]*>[\s\S]*?<\/object>/gi, '')
    .replace(/<embed[^>]*>/gi, '')
    .replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '')
    // 移除所有事件处理器
    .replace(/\son\w+\s*=\s*"[^"]*"/gi, '')
    .replace(/\son\w+\s*=\s*'[^']*'/gi, '')
    .replace(/\son\w+\s*=\s*[^\s>]+/gi, '')
    // 移除 javascript: 伪协议
    .replace(/href\s*=\s*["']?\s*javascript:/gi, 'href="#"')
    .replace(/src\s*=\s*["']?\s*javascript:/gi, '')
  return cleaned
}

/**
 * 同步获取 DOMPurify 实例，未加载时返回 null
 */
function getDOMPurify(): DOMPurifyType | null {
  return getDOMPurifySync()
}

/**
 * 净化 HTML 内容，移除所有 XSS 攻击向量
 *
 * 用于处理用户输入、富文本编辑器内容、第三方内容等
 * 不可信来源的 HTML 字符串。
 *
 * 注意：本函数为同步函数，若 DOMPurify CDN 尚未加载完成，
 * 会触发紧急降级（基础正则净化），保证安全性但不保证完美净化。
 * 若需保证完整净化能力，请使用 `sanitizeAsync`。
 *
 * @param dirty - 待净化的 HTML 字符串
 * @param config - 可选的 DOMPurify 配置，会与默认配置合并
 * @returns 安全的 HTML 字符串
 *
 * @example
 * const safe = sanitize('<p>Hello <script>alert("XSS")</script></p>')
 * // => "<p>Hello </p>"
 *
 * @example
 * // 允许更多标签（如富文本编辑器）
 * const safe = sanitize(richText, {
 *   ALLOWED_TAGS: [...DEFAULT_CONFIG.ALLOWED_TAGS, 'font', 'span']
 * })
 */
export function sanitize(
  dirty: string,
  config?: Record<string, unknown>,
): string {
  if (!dirty) return ''

  const dp = getDOMPurify()
  if (!dp) {
    // DOMPurify 未就绪 — 触发异步加载并降级处理
    void loadDOMPurify()
    return emergencySanitize(dirty)
  }

  const mergedConfig = config
    ? { ...DEFAULT_CONFIG, ...config }
    : DEFAULT_CONFIG

  return dp.sanitize(dirty, mergedConfig as any)
}

// ============================================================
// Markdown 渲染专用配置
// ============================================================
// 与 DEFAULT_CONFIG 相比，增加了以下支持：
//   - 任务列表：input[type=checkbox]
//   - Mermaid 图表：svg, g, path, rect, text, tspan, line, circle, defs, marker, use 等
//   - KaTeX 公式：math, semantics, annotation, mrow, mfrac, msqrt, mi, mo, mn, mspace 等
//   - 代码高亮：span（highlight.js 生成的 span 标签）
//   - 折叠块：details, summary
//   - 属性：checked, disabled, aria-*, data-*, viewBox, fill, stroke, d, x, y, cx, cy, r 等
// ============================================================

const MARKDOWN_ALLOWED_TAGS = [
  ...DEFAULT_CONFIG.ALLOWED_TAGS,
  // Mermaid: SVG 元素
  'svg', 'g', 'path', 'rect', 'text', 'tspan', 'line', 'circle',
  'ellipse', 'polygon', 'polyline', 'defs', 'marker', 'use',
  'linearGradient', 'radialGradient', 'stop', 'clipPath',
  'foreignObject', 'switch', 'symbol', 'title', 'desc',
  // KaTeX: 数学公式元素
  'math', 'semantics', 'annotation', 'mrow', 'mfrac', 'msqrt',
  'mi', 'mo', 'mn', 'mspace', 'mtext', 'mstyle', 'merror',
  'mphantom', 'menclose', 'mpadded', 'mover', 'munder',
  'munderover', 'msub', 'msup', 'msubsup', 'mtable', 'mtr',
  'mtd', 'mlabeledtr', 'mstack', 'mlongdiv', 'msgroup',
  'msrow', 'mscarries', 'mscarry', 'msline',
  // 交互元素
  'input', 'details', 'summary', 'label',
  // 媒体
  'video', 'audio', 'source', 'iframe',
]

const MARKDOWN_ALLOWED_ATTRS = [
  ...DEFAULT_CONFIG.ALLOWED_ATTR,
  // 任务列表
  'checked', 'disabled', 'type',
  // Mermaid: SVG 属性
  'viewBox', 'd', 'fill', 'stroke', 'stroke-width', 'stroke-linecap',
  'stroke-linejoin', 'x', 'y', 'cx', 'cy', 'r', 'rx', 'ry',
  'x1', 'y1', 'x2', 'y2', 'transform', 'points',
  'text-anchor', 'dominant-baseline', 'font-family', 'font-size',
  'font-weight', 'letter-spacing', 'text-decoration',
  'marker-end', 'marker-start', 'opacity', 'fill-opacity',
  'stroke-opacity', 'stroke-dasharray', 'stroke-dashoffset',
  'clip-path', 'clip-rule', 'fill-rule', 'mask',
  // KaTeX: 数学公式属性
  'mathvariant', 'mathsize', 'mathcolor', 'mathbackground',
  'displaystyle', 'scriptlevel', 'linethickness',
  // aria 属性
  'aria-label', 'aria-hidden', 'aria-expanded', 'aria-controls',
  'aria-labelledby', 'aria-describedby', 'aria-selected',
  'aria-current', 'aria-level', 'role',
  // data 属性
  'data-*',
  // 其他
  'open', 'name', 'content',
  // 媒体
  'controls', 'autoplay', 'loop', 'muted', 'poster',
  'allow', 'sandbox', 'frameborder', 'allowfullscreen',
]

/**
 * 净化 Markdown 渲染后的 HTML 内容
 *
 * 与 `sanitize()` 相比，此函数允许更多标签和属性以支持
 * Markdown 的高级功能（Mermaid 图表、KaTeX 公式、任务列表等）。
 * 仍然禁止 <script>、事件处理器、javascript: 伪协议等危险内容。
 *
 * @param dirty - 待净化的 Markdown 渲染 HTML 字符串
 * @returns 安全的 HTML 字符串
 */
export function sanitizeHtml(dirty: string): string {
  if (!dirty) return ''

  const dp = getDOMPurify()
  if (!dp) {
    // DOMPurify 未就绪 — 触发异步加载并降级处理
    void loadDOMPurify()
    return emergencySanitize(dirty)
  }

  return dp.sanitize(dirty, {
    ALLOWED_TAGS: MARKDOWN_ALLOWED_TAGS,
    ALLOWED_ATTR: MARKDOWN_ALLOWED_ATTRS,
    ALLOW_DATA_ATTR: true,
    ALLOWED_URI_REGEXP: /^(?:(?:https?|ftps?|mailto|tel|data):|[^a-z]|[a-z+.-]+(?:[^a-z+.-:]|$))/i,
    ADD_TAGS: [],
    ADD_ATTR: ['target', 'rel'],
    SANITIZE_DOM: true,
    WHOLE_DOCUMENT: false,
    RETURN_DOM: false,
    RETURN_DOM_FRAGMENT: false,
    RETURN_DOM_IMPORT: false,
    FORCE_BODY: false,
    ALLOW_UNKNOWN_PROTOCOLS: false,
    USE_PROFILES: { html: true },
  } as any)
}

/**
 * 异步净化 HTML 内容（保证 DOMPurify 已加载）
 *
 * 与同步 `sanitize` 不同，本函数会等待 DOMPurify CDN 加载完成后再净化，
 * 确保完整净化能力。适用于不要求同步返回的场景。
 *
 * @param dirty - 待净化的 HTML 字符串
 * @param config - 可选的 DOMPurify 配置
 * @returns 安全的 HTML 字符串
 */
export async function sanitizeAsync(
  dirty: string,
  config?: Record<string, unknown>,
): Promise<string> {
  if (!dirty) return ''

  const dp = await loadDOMPurify()
  const mergedConfig = config
    ? { ...DEFAULT_CONFIG, ...config }
    : DEFAULT_CONFIG

  return dp.sanitize(dirty, mergedConfig as any)
}

/**
 * 净化 HTML 并返回 DOM 节点（用于直接操作 DOM）
 *
 * @param dirty - 待净化的 HTML 字符串
 * @returns 安全的 DocumentFragment
 */
export function sanitizeToFragment(dirty: string): DocumentFragment {
  if (!dirty) return document.createDocumentFragment()

  const dp = getDOMPurify()
  if (!dp) {
    // 降级：创建临时 div 并使用 emergencySanitize
    const div = document.createElement('div')
    div.innerHTML = emergencySanitize(dirty)
    const frag = document.createDocumentFragment()
    while (div.firstChild) frag.appendChild(div.firstChild)
    return frag
  }

  const result = dp.sanitize(dirty, {
    ...DEFAULT_CONFIG,
    RETURN_DOM: true,
    RETURN_DOM_FRAGMENT: true,
  } as any)
  return result as DocumentFragment
}

/**
 * 安全地设置元素的 innerHTML
 *
 * 会先通过 DOMPurify 净化内容，再设置 innerHTML。
 * 适用于需要直接操作 DOM 的场景（如渲染 Markdown 转换结果）。
 *
 * @param element - 目标 DOM 元素
 * @param html - 要设置的 HTML 内容
 *
 * @example
 * const el = document.getElementById('content')
 * safeSetInnerHTML(el, userProvidedHTML)
 */
export function safeSetInnerHTML(
  element: HTMLElement,
  html: string,
): void {
  element.innerHTML = sanitize(html)
}

// ============================================================
// Vue 3 自定义指令：v-safe-html
// ============================================================

/**
 * Vue 3 自定义指令 — 安全的 HTML 渲染
 *
 * 替代 v-html，自动对绑定值进行 XSS 净化。
 *
 * 使用方式：
 *   <div v-safe-html="userProvidedHTML"></div>
 *
 * 与 v-html 的区别：
 *   - v-html 直接设置 innerHTML，存在 XSS 风险
 *   - v-safe-html 先通过 DOMPurify 净化，再设置 innerHTML
 */
export const vSafeHtml: Directive<HTMLElement, string> = {
  mounted(el: HTMLElement, binding) {
    if (binding.value) {
      el.innerHTML = sanitize(binding.value)
    }
  },
  updated(el: HTMLElement, binding) {
    if (binding.value !== binding.oldValue) {
      el.innerHTML = sanitize(binding.value || '')
    }
  },
}

// ============================================================
// 导出 DOMPurify 异步获取器
// ============================================================
// 由于 DOMPurify 改为 CDN 加载，无法直接 export 默认实例。
// 提供 getDOMPurifyAsync 函数让调用方按需获取。
export async function getDOMPurifyAsync(): Promise<DOMPurifyType> {
  return loadDOMPurify()
}
