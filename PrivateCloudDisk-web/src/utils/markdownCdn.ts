// ============================================================
// markdownCdn.ts — Markdown 渲染相关库的 CDN 动态加载器
// ============================================================
// 将 markdown-it 及其插件从本地 npm 包改为 CDN 动态加载，
// 彻底从 Vite 构建产物中剥离（解决 OOM 与产物过大的问题）。
//
// 加载策略：
//   - markdown-it 主包通过 UMD 全局变量 `markdownit` 暴露
//   - markdown-it-anchor / -emoji / -table-of-contents / -task-lists
//     通过 UMD 全局变量（首字母大写驼峰）暴露
//   - 各插件加载完成后通过 `md.use(plugin)` 注册到 markdown-it 实例
//
// 使用方式：
//   import { loadMarkdownItWithPlugins } from '@/utils/markdownCdn'
//   const md = await loadMarkdownItWithPlugins()
//   md.render('# Hello')
// ============================================================

import { createSingletonLoader } from './cdnLoader'

// ============================================================
// 版本与 CDN 配置
// ============================================================
// 使用 jsdelivr CDN，与 index.html 中预连接的域名保持一致
// AUDIT FIX [2.3]（需求一-4/5）:
// 原版本/文件名组合中 anchor@9.2.1 不存在，且插件全局名大小写不匹配，会返回 404 文本并被 nosniff 拒绝。
// 按需求固定 markdown-it 13.0.1，并选用各包真实发布的 UMD 构建。
const MARKDOWN_IT_VERSION = '13.0.1'
const MARKDOWN_IT_ANCHOR_VERSION = '8.6.7'
const MARKDOWN_IT_EMOJI_VERSION = '2.0.2'
const MARKDOWN_IT_TOC_VERSION = '1.2.0'
const MARKDOWN_IT_TASK_LISTS_VERSION = '2.1.1'

const CDN_BASE = 'https://cdn.jsdelivr.net/npm'

// ============================================================
// 单库加载器（每个库独立加载，支持按需使用）
// ============================================================

/**
 * 加载 markdown-it 主库
 * UMD 全局变量：`markdownit`
 */
export const loadMarkdownIt = createSingletonLoader<typeof import('markdown-it').default>({
  url: `${CDN_BASE}/markdown-it@${MARKDOWN_IT_VERSION}/dist/markdown-it.min.js`,
  globalName: 'markdownit',
  timeout: 12_000,
  maxRetries: 1,
  // transform：UDM 包暴露的是构造函数本身，window.markdownit 即构造函数
  transform: (val) => val as typeof import('markdown-it').default,
})

/**
 * 加载 markdown-it-anchor 插件
 * UMD 全局变量：`markdownItAnchor`
 */
export const loadMarkdownItAnchor =
  createSingletonLoader<typeof import('markdown-it-anchor').default>({
    url: `${CDN_BASE}/markdown-it-anchor@${MARKDOWN_IT_ANCHOR_VERSION}/dist/markdownItAnchor.umd.js`,
    globalName: 'markdownItAnchor',
    timeout: 10_000,
    maxRetries: 1,
    transform: (val) => val as typeof import('markdown-it-anchor').default,
  })

/**
 * 加载 markdown-it-emoji 插件
 * UMD 全局变量：`markdownItEmoji`
 */
export const loadMarkdownItEmoji =
  createSingletonLoader<typeof import('markdown-it-emoji').default>({
    url: `https://cdnjs.cloudflare.com/ajax/libs/markdown-it-emoji/${MARKDOWN_IT_EMOJI_VERSION}/markdown-it-emoji.min.js`,
    globalName: 'markdownItEmoji',
    timeout: 10_000,
    maxRetries: 1,
    transform: (val) => val as typeof import('markdown-it-emoji').default,
  })

/** (已经放弃 不加载 函数不使用  插件不加载)
 * 加载 markdown-it-table-of-contents 插件
 * UMD 全局变量：`markdownItToc`
 *
 * 注意：此包默认 UMD 名称为 `markdownItTableOfContents`，
 * 但实际 CDN bundle 暴露为 `markdownItToc`，需根据实际验证。
 * 若全局变量名不匹配，可通过 transform 兼容。
 */
export const loadMarkdownItToc = createSingletonLoader<
  typeof import('markdown-it-table-of-contents').default
>({
  url: `${CDN_BASE}/markdown-it-table-of-contents@${MARKDOWN_IT_TOC_VERSION}/dist/markdown-it-table-of-contents.min.js`,
  globalName: 'markdownItToc',
  timeout: 20_000,
  transform: (val) => {
    // 兼容多个全局变量名
    const w = window as any
    return (
      val ||
      w.markdownItToc ||
      w.markdownItTableOfContents ||
      w.markdownitTableOfContents
    ) as typeof import('markdown-it-table-of-contents').default
  },
})

/**
 * 加载 markdown-it-task-lists 插件
 * UMD 全局变量：`markdownItTaskLists`
 */
export const loadMarkdownItTaskLists = createSingletonLoader<
  typeof import('markdown-it-task-lists').default
>({
    url: `${CDN_BASE}/markdown-it-task-lists@${MARKDOWN_IT_TASK_LISTS_VERSION}/dist/markdown-it-task-lists.min.js`,
    globalName: 'markdownItTaskLists',
    timeout: 10_000,
    maxRetries: 1,
  transform: (val) => val as typeof import('markdown-it-task-lists').default,
})

// ============================================================
// 组合加载器：一次加载 markdown-it + 所有插件并组装
// ============================================================

/**
 * 创建并配置好的 markdown-it 实例（含所有插件）
 *
 * 使用方式：
 *   const md = await loadMarkdownItWithPlugins()
 *   const html = md.render('# Hello')
 *
 * 配置内容：
 *   - html: false（禁止原始 HTML，安全）
 *   - breaks: true（换行符 → <br>）
 *   - linkify: true（自动识别 URL）
 *   - typographer: true（智能引号、破折号）
 *   - 插件：anchor / toc / emoji / taskLists
 *
 * @param options 可选配置：
 *   - highlightFn: 代码高亮函数（由调用方注入，避免循环依赖 highlight.js）
 */
export async function loadMarkdownItWithPlugins(options?: {
  highlightFn?: (code: string, lang: string) => string
}): Promise<import('markdown-it').MarkdownIt> {
  // 并行加载 markdown-it 主库与所有插件
  const [MarkdownIt, anchor, emoji, taskLists] = await Promise.all([
    loadMarkdownIt(),
    loadMarkdownItAnchor().catch(() => null),
    loadMarkdownItEmoji().catch(() => null),
    loadMarkdownItTaskLists().catch(() => null),
  ])

  // 创建实例
  const md = new MarkdownIt({
    html: false,
    breaks: true,
    linkify: true,
    typographer: true,
    xhtmlOut: false,
    langPrefix: 'language-',
    highlight: options?.highlightFn || ((code: string, lang: string) => {
      // 默认无高亮，仅做 HTML 转义
      const escaped = code
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
      const langClass = lang ? ` class="language-${lang}"` : ''
      return `<pre><code${langClass}>${escaped}</code></pre>`
    }),
  })

  // 注册插件（失败则跳过，保持容错）
  if (anchor) {
    md.use(anchor, {
      level: [1, 2, 3, 4, 5, 6],
      permalink: true,
      permalinkClass: 'header-anchor',
      permalinkSymbol: '#',
      permalinkBefore: true,
    })
  }

  // 原有 markdown-it-table-of-contents 包未发布浏览器 UMD 构建，因此不再向页面注入一个必定 404 的脚本。
  // 目录仍由 MarkdownPreview.extractOutline 从最终 DOM 构建，功能保持完整且锚点来源唯一。

  if (emoji) {
    md.use(emoji)
  }

  if (taskLists) {
    md.use(taskLists)
  }

  return md
}

/**
 * 预加载 markdown-it 全套（在路由预取阶段调用）
 */
export function preloadMarkdownLibs(): void {
  Promise.allSettled([
    loadMarkdownIt(),
    loadMarkdownItAnchor(),
    loadMarkdownItEmoji(),
    loadMarkdownItTaskLists(),
  ]).catch(() => {
    // 静默处理
  })
}

// ============================================================
// 全局变量类型扩展（用于类型安全）
// ============================================================
declare global {
  interface Window {
    markdownit?: typeof import('markdown-it').default
    markdownItAnchor?: typeof import('markdown-it-anchor').default
    markdownItEmoji?: typeof import('markdown-it-emoji').default
    markdownItToc?: typeof import('markdown-it-table-of-contents').default
    markdownItTableOfContents?: typeof import('markdown-it-table-of-contents').default
    markdownItTaskLists?: typeof import('markdown-it-task-lists').default
  }
}

export {}
