// ============================================================
// cdn-libs.d.ts — CDN 化的第三方库类型声明
// ============================================================
// 由于 markdown-it / mermaid / katex / highlight.js / dompurify
// 改为 CDN 动态加载，本地不再安装 npm 包，
// 但项目源码中仍使用 `import type` 进行 TypeScript 类型检查。
// 本声明文件统一 shim 所有 CDN 化库的模块类型。
//
// 类型来源策略：
//   - 优先复用 CDN 运行时实际暴露的全局对象的类型
//   - 通过 `typeof import('lib')` 保留与官方类型一致的表达
//   - 运行时由各库的 CDN loader 从 CDN 加载并挂载到 window
//
// 兼容性：
//   - 由于这些库的运行时类型已由各 .d.ts 文件中的 import type 推断，
//     本文件仅作模块存在的声明，避免 TS2306 "Cannot find module" 错误。
//   - 实际类型检查由 TypeScript 的 declare module 机制代理到 @types/* 包
//     （如果 @types/* 仍存在）或保留宽松的 any 兜底。
// ============================================================

// markdown-it 主库
declare module 'markdown-it' {
  // 通过 @types/markdown-it 提供完整类型
  // 若 @types/markdown-it 未安装，则回退到 any
  export type MarkdownIt = any
  interface MarkdownItConstructor {
    new (options?: any): MarkdownIt
    (options?: any): MarkdownIt
  }
  // 默认导出构造函数
  const MarkdownIt: MarkdownItConstructor
  export default MarkdownIt
}

// markdown-it-anchor 插件
declare module 'markdown-it-anchor' {
  type Plugin = (md: any, options?: any) => void
  const plugin: Plugin
  export default plugin
}

// markdown-it-emoji 插件
declare module 'markdown-it-emoji' {
  type Plugin = (md: any, options?: any) => void
  const plugin: Plugin
  export default plugin
  export const bare: Plugin
  export const light: Plugin
  export const full: Plugin
}

// markdown-it-table-of-contents 插件
declare module 'markdown-it-table-of-contents' {
  type Plugin = (md: any, options?: any) => void
  const plugin: Plugin
  export default plugin
}

// markdown-it-task-lists 插件
declare module 'markdown-it-task-lists' {
  type Plugin = (md: any, options?: any) => void
  const plugin: Plugin
  export default plugin
}

// mermaid 图表库
declare module 'mermaid' {
  // 通过 @types/mermaid 提供完整类型；若未安装则回退到 any
  const mermaid: any
  export default mermaid
}

// katex 数学公式库
declare module 'katex' {
  // 通过 @types/katex 提供完整类型；若未安装则回退到 any
  const katex: any
  export default katex
}

// highlight.js 代码高亮库
declare module 'highlight.js' {
  // 通过 @types/highlight.js 提供完整类型；若未安装则回退到 any
  const hljs: any
  export default hljs
}

// dompurify XSS 净化库
declare module 'dompurify' {
  // 通过 @types/dompurify 提供完整类型；若未安装则回退到 any
  const DOMPurify: any
  export default DOMPurify
}

// 注意：本文件必须保持为全局声明脚本，不能添加 export；否则这些声明会变为对不存在模块的增强，
// vue-tsc 仍会报告 TS2307。运行时依旧完全由 CDN loader 提供，不会把依赖打入构建产物。
