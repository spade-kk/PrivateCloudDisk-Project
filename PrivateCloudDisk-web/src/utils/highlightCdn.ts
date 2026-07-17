// ============================================================
// highlightCdn.ts — highlight.js 代码高亮库的 CDN 动态加载器
// ============================================================
// 将 highlight.js 从本地 npm 包改为 CDN 动态加载。
//
// 加载策略：
//   - 使用 highlight.js 官方 CDN bundle（含 190+ 语言）
//   - UMD 全局变量 `hljs` 暴露
//   - 单例 Promise，整个应用只加载一次
//
// 使用方式：
//   import { loadHighlight } from '@/utils/highlightCdn'
//   const hljs = await loadHighlight()
//   const result = hljs.highlight(code, { language: 'typescript' })
//   const html = result.value
// ============================================================

import { createSingletonLoader } from './cdnLoader'

// highlight.js 版本与 CDN 配置
const HIGHLIGHT_JS_VERSION = '11.11.1'
const CDN_URL = `https://cdn.jsdelivr.net/npm/@highlightjs/cdn-assets@${HIGHLIGHT_JS_VERSION}/highlight.min.js`

/**
 * 加载 highlight.js
 * UMD 全局变量：`hljs`
 */
export const loadHighlight = createSingletonLoader<typeof import('highlight.js').default>({
  url: CDN_URL,
  globalName: 'hljs',
  timeout: 30_000,
  // 校验：hljs 全局对象必须包含 highlight 和 highlightAuto 方法
  validate: () => {
    const h = (window as any).hljs
    return !!h && typeof h?.highlight === 'function' && typeof h?.highlightAuto === 'function'
  },
  transform: (val) => val as typeof import('highlight.js').default,
})

/**
 * 同步获取已加载的 highlight.js 实例（若尚未加载则返回 null）
 * 用于在 markdown-it 的 highlight 回调中同步使用
 */
export function getHighlightSync(): typeof import('highlight.js').default | null {
  const h = (window as any).hljs
  if (h && typeof h?.highlight === 'function' && typeof h?.highlightAuto === 'function') {
    return h as typeof import('highlight.js').default
  }
  return null
}

/**
 * 预加载 highlight.js（在路由预取阶段调用）
 */
export function preloadHighlight(): void {
  loadHighlight().catch(() => {
    // 静默处理
  })
}

// 全局变量类型扩展
declare global {
  interface Window {
    hljs?: typeof import('highlight.js').default
  }
}

export {}
