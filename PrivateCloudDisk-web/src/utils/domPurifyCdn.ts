// ============================================================
// domPurifyCdn.ts — DOMPurify XSS 净化库的 CDN 动态加载器
// ============================================================
// 将 DOMPurify 从本地 npm 包改为 CDN 动态加载。
//
// 使用场景：
//   - sanitize.ts 工具模块（XSS 净化）
//   - MarkdownPreview.vue 组件（Markdown HTML 净化）
//   - 其他需要安全 HTML 渲染的场景
//
// 加载策略：
//   - UMD 全局变量 `DOMPurify` 暴露（与 npm 包默认导出一致）
//   - 单例 Promise，整个应用只加载一次
//
// 使用方式：
//   import { loadDOMPurify } from '@/utils/domPurifyCdn'
//   const DOMPurify = await loadDOMPurify()
//   const clean = DOMPurify.sanitize('<script>alert(1)</script>')
// ============================================================

import { createSingletonLoader } from './cdnLoader'

// DOMPurify 版本与 CDN 配置
const DOMPURIFY_VERSION = '3.4.11'
const CDN_URL = `https://cdn.jsdelivr.net/npm/dompurify@${DOMPURIFY_VERSION}/dist/purify.min.js`

/**
 * 加载 DOMPurify
 * UMD 全局变量：`DOMPurify`
 */
export const loadDOMPurify = createSingletonLoader<typeof import('dompurify').default>({
  url: CDN_URL,
  globalName: 'DOMPurify',
  timeout: 30_000,
  // 校验：DOMPurify 全局对象必须包含 sanitize 方法
  validate: () => {
    const d = (window as any).DOMPurify
    return !!d && typeof d?.sanitize === 'function'
  },
  transform: (val) => val as typeof import('dompurify').default,
})

/**
 * 同步获取已加载的 DOMPurify 实例（若尚未加载则返回 null）
 * 用于在同步代码路径中安全使用
 */
export function getDOMPurifySync(): typeof import('dompurify').default | null {
  const d = (window as any).DOMPurify
  if (d && typeof d?.sanitize === 'function') {
    return d as typeof import('dompurify').default
  }
  return null
}

/**
 * 预加载 DOMPurify（在应用启动时调用）
 * 因为 sanitize.ts 被多个组件依赖，提前加载避免首次使用时延迟
 */
export function preloadDOMPurify(): void {
  loadDOMPurify().catch(() => {
    // 静默处理
  })
}

// 全局变量类型扩展
declare global {
  interface Window {
    DOMPurify?: typeof import('dompurify').default
  }
}

export {}
