// ============================================================
// katexCdn.ts — KaTeX 数学公式库的 CDN 动态加载器
// ============================================================
// 将 katex 从本地 npm 包改为 CDN 动态加载。
//
// 加载内容：
//   - KaTeX JS 主包（UMD 全局变量 `katex`）
//   - KaTeX CSS 样式文件（必需，否则公式无法正确渲染）
//
// 使用方式：
//   import { loadKaTeX } from '@/utils/katexCdn'
//   const katex = await loadKaTeX()
//   const html = katex.renderToString('E=mc^2', { displayMode: true })
// ============================================================

import { createSingletonLoader, loadStyle } from './cdnLoader'

// KaTeX 版本与 CDN 配置
const KATEX_VERSION = '0.16.11'
const KATEX_JS_URL = `https://cdn.jsdelivr.net/npm/katex@${KATEX_VERSION}/dist/katex.min.js`
const KATEX_CSS_URL = `https://cdn.jsdelivr.net/npm/katex@${KATEX_VERSION}/dist/katex.min.css`

// CSS 样式是否已加载
let cssLoaded = false

/**
 * 加载 KaTeX CSS 样式文件（仅加载一次）
 */
async function ensureKaTeXCSS(): Promise<void> {
  if (cssLoaded) return
  await loadStyle(KATEX_CSS_URL)
  cssLoaded = true
}

/**
 * 加载 KaTeX（含 CSS 样式）
 * UMD 全局变量：`katex`
 */
export const loadKaTeX = createSingletonLoader<typeof import('katex').default>({
  url: KATEX_JS_URL,
  globalName: 'katex',
  timeout: 30_000,
  // 校验：katex 全局对象必须包含 renderToString 方法
  validate: () => {
    const k = (window as any).katex
    return !!k && typeof k?.renderToString === 'function'
  },
  transform: (val) => {
    // 加载 JS 后并行加载 CSS（不阻塞返回，但保证后续渲染时 CSS 已就绪）
    void ensureKaTeXCSS()
    return val as typeof import('katex').default
  },
})

/**
 * 预加载 KaTeX（在路由预取阶段调用）
 */
export function preloadKaTeX(): void {
  loadKaTeX().catch(() => {
    // 静默处理
  })
}

// 全局变量类型扩展
declare global {
  interface Window {
    katex?: typeof import('katex').default
  }
}

export {}
