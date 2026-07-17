// ============================================================
// mermaidCdn.ts — Mermaid 图表库的 CDN 动态加载器
// ============================================================
// 将 mermaid 从本地 npm 包（83MB+，含大量 diagram 类型源码）
// 改为 CDN 动态加载，彻底从 Vite 构建产物中剥离。
//
// 加载策略：
//   - 主包通过 UMD 全局变量 `mermaid` 暴露
//   - 单例 Promise，整个应用只加载一次
//   - 加载完成后由调用方自行 `mermaid.initialize()` 配置
//
// 使用方式：
//   import { loadMermaid } from '@/utils/mermaidCdn'
//   const mermaid = await loadMermaid()
//   mermaid.initialize({ startOnLoad: false, theme: 'dark' })
//   const { svg } = await mermaid.render('graph-id', code)
// ============================================================

import { createSingletonLoader } from './cdnLoader'

// Mermaid 版本与 CDN 配置
const MERMAID_VERSION = '11.16.0'
const CDN_URL = `https://cdn.jsdelivr.net/npm/mermaid@${MERMAID_VERSION}/dist/mermaid.min.js`

/**
 * 加载 Mermaid
 * UMD 全局变量：`mermaid`
 */
export const loadMermaid = createSingletonLoader<typeof import('mermaid').default>({
  url: CDN_URL,
  globalName: 'mermaid',
  timeout: 60_000, // mermaid 体积较大，超时延长到 60s
  maxRetries: 2,
  // 校验：mermaid 全局对象必须包含 initialize 方法
  validate: () => {
    const m = (window as any).mermaid
    return !!m && typeof m?.initialize === 'function' && typeof m?.render === 'function'
  },
  transform: (val) => val as typeof import('mermaid').default,
})

/**
 * 预加载 Mermaid（在路由预取阶段调用）
 */
export function preloadMermaid(): void {
  loadMermaid().catch(() => {
    // 静默处理
  })
}

// 全局变量类型扩展
declare global {
  interface Window {
    mermaid?: typeof import('mermaid').default
  }
}

export {}
