// ============================================================
// cdnLoader.ts — 通用 CDN 动态加载器基础设施
// ============================================================
// 设计目标：
//   - 提供通用的 <script> / <link> 注入能力，供具体库的加载器复用
//   - 单例 Promise 模式，避免重复加载
//   - 内置超时 + 重试机制，提升弱网环境下的可用性
//   - 类型安全，支持泛型推断
//
// 使用方式（具体库的加载器内部使用本模块）：
//   import { loadScript, createSingletonLoader } from '@/utils/cdnLoader'
//   const loadFoo = createSingletonLoader({
//     url: 'https://cdn.jsdelivr.net/npm/foo@1.0.0/dist/foo.min.js',
//     globalName: 'Foo',           // CDN UMD 包暴露到 window 的全局变量名
//     timeout: 30_000,
//   })
//   const foo = await loadFoo()
// ============================================================

// ============================================================
// 类型定义
// ============================================================

/** 加载超时默认值（毫秒） */
const DEFAULT_TIMEOUT = 30_000

/** 最大重试次数 */
const DEFAULT_MAX_RETRIES = 2

/** 单个库的 CDN 加载配置 */
export interface CDNLoaderConfig<T = unknown> {
  /** CDN 资源 URL */
  url: string
  /**
   * UMD 包加载完成后挂载到 window 的全局变量名
   * 若不提供则仅注入 script，由调用方自行从 window 取值
   */
  globalName?: string
  /** 加载超时（毫秒），默认 30s */
  timeout?: number
  /** 加载失败重试次数，默认 2 次 */
  maxRetries?: number
  /**
   * 自定义校验函数：检查全局变量是否已就绪
   * 用于处理 UMD 包加载完成但初始化未完成的场景
   */
  validate?: () => boolean
  /**
   * 加载完成后的转换函数：从 window 全局变量提取实际导出值
   * 默认行为：返回 window[globalName]
   */
  transform?: (globalValue: unknown) => T
  /** 是否使用 module 类型加载（ESM 模块），默认 false（UMD） */
  module?: boolean
}

// 已加载资源的缓存表（避免重复注入同一 URL）
const loadedScripts = new Map<string, Promise<void>>()
const loadedStyles = new Map<string, Promise<void>>()

// ============================================================
// 基础加载函数
// ============================================================

/**
 * 动态注入 <script> 标签加载 JS 资源
 * 幂等性：同一 URL 仅注入一次，后续调用复用同一个 Promise
 *
 * @param src 资源 URL
 * @param options 加载选项
 * @returns 加载完成的 Promise
 */
export function loadScript(
  src: string,
  options: { module?: boolean; timeout?: number } = {},
): Promise<void> {
  // 缓存命中：返回已存在的 Promise
  const cached = loadedScripts.get(src)
  if (cached) return cached

  const { module = false, timeout = DEFAULT_TIMEOUT } = options

  const promise = new Promise<void>((resolve, reject) => {
    // 幂等性检查：DOM 中已存在相同 src 的 script 标签
    const existing = document.querySelector<HTMLScriptElement>(
      `script[data-cdn-src="${src}"]`,
    )
    if (existing) {
      if (existing.dataset.loaded === 'true') {
        resolve()
        return
      }
      existing.addEventListener('load', () => resolve(), { once: true })
      existing.addEventListener(
        'error',
        () => reject(new Error(`Failed to load script: ${src}`)),
        { once: true },
      )
      return
    }

    // 超时定时器
    const timer = setTimeout(() => {
      reject(new Error(`Script load timeout (${timeout}ms): ${src}`))
    }, timeout)

    const script = document.createElement('script')
    script.src = src
    script.async = true
    script.dataset.cdnSrc = src
    if (module) script.type = 'module'
    script.onload = () => {
      clearTimeout(timer)
      script.dataset.loaded = 'true'
      resolve()
    }
    script.onerror = () => {
      clearTimeout(timer)
      // 加载失败时移除 script 标签，允许后续重试
      script.remove()
      reject(new Error(`Failed to load script: ${src}`))
    }
    document.head.appendChild(script)
  })

  loadedScripts.set(src, promise)
  // 加载失败时清除缓存，允许后续重试
  promise.catch(() => loadedScripts.delete(src))
  return promise
}

/**
 * 动态注入 <link> 标签加载 CSS 资源
 * 幂等性：同一 URL 仅注入一次
 *
 * @param href CSS 资源 URL
 * @returns 加载完成的 Promise
 */
export function loadStyle(href: string): Promise<void> {
  const cached = loadedStyles.get(href)
  if (cached) return cached

  const promise = new Promise<void>((resolve, reject) => {
    const existing = document.querySelector<HTMLLinkElement>(
      `link[data-cdn-href="${href}"]`,
    )
    if (existing) {
      if (existing.dataset.loaded === 'true') {
        resolve()
        return
      }
      existing.addEventListener('load', () => resolve(), { once: true })
      existing.addEventListener(
        'error',
        () => reject(new Error(`Failed to load style: ${href}`)),
        { once: true },
      )
      return
    }

    const link = document.createElement('link')
    link.rel = 'stylesheet'
    link.href = href
    link.dataset.cdnHref = href
    link.onload = () => {
      link.dataset.loaded = 'true'
      resolve()
    }
    link.onerror = () => {
      link.remove()
      reject(new Error(`Failed to load style: ${href}`))
    }
    document.head.appendChild(link)
  })

  loadedStyles.set(href, promise)
  promise.catch(() => loadedStyles.delete(href))
  return promise
}

/**
 * 带超时的 Promise 包装
 */
function withTimeout<T>(promise: Promise<T>, ms: number, errorMsg: string): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(errorMsg)), ms)
    promise
      .then((result) => {
        clearTimeout(timer)
        resolve(result)
      })
      .catch((err) => {
        clearTimeout(timer)
        reject(err)
      })
  })
}

/**
 * 延迟函数（用于重试间隔）
 */
function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

// ============================================================
// 单例加载器工厂
// ============================================================

/**
 * 创建单例加载器
 *
 * 工作流：
 *   1. 首次调用：注入 <script> → 等待加载 → 校验全局变量 → 返回
 *   2. 后续调用：直接返回缓存的 Promise
 *   3. 失败时：重试 maxRetries 次，每次间隔 500ms 退避
 *   4. 全部失败：清空缓存允许后续重试，抛出错误
 *
 * @param config 加载配置
 * @returns 加载函数，返回 Promise<T>
 */
export function createSingletonLoader<T>(config: CDNLoaderConfig<T>): () => Promise<T> {
  let loadPromise: Promise<T> | null = null

  const {
    url,
    globalName,
    timeout = DEFAULT_TIMEOUT,
    maxRetries = DEFAULT_MAX_RETRIES,
    validate,
    transform,
    module = false,
  } = config

  const attemptLoad = async (): Promise<T> => {
    // 1. 先检查全局变量是否已存在（可能被其他脚本预先加载）
    if (globalName && (window as any)[globalName]) {
      const val = (window as any)[globalName]
      if (!validate || validate()) {
        return transform ? transform(val) : (val as T)
      }
    }

    // 2. 注入 script 标签
    await loadScript(url, { module, timeout })

    // 3. 校验全局变量已就绪
    if (globalName) {
      const val = (window as any)[globalName]
      if (!val) {
        throw new Error(`CDN 加载完成但 window.${globalName} 未定义: ${url}`)
      }
      if (validate && !validate()) {
        throw new Error(`CDN 全局变量校验失败: ${globalName}`)
      }
      return transform ? transform(val) : (val as T)
    }

    // 无 globalName 时返回 undefined（调用方应通过 transform 自行取值）
    return undefined as unknown as T
  }

  const loadWithRetry = async (): Promise<T> => {
    let lastError: Error | null = null

    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return await withTimeout(
          attemptLoad(),
          timeout,
          `CDN 加载超时 (${timeout}ms): ${url}`,
        )
      } catch (err) {
        lastError = err instanceof Error ? err : new Error(String(err))
        // 最后一次尝试不再等待
        if (attempt < maxRetries) {
          await delay(500 * (attempt + 1)) // 退避：500ms, 1000ms
        }
      }
    }

    throw lastError ?? new Error(`CDN 加载失败: ${url}`)
  }

  return (): Promise<T> => {
    // 已加载完成则直接返回（同步检查全局变量）
    if (globalName && (window as any)[globalName]) {
      const val = (window as any)[globalName]
      if (!validate || validate()) {
        return Promise.resolve(transform ? transform(val) : (val as T))
      }
    }

    // 单例 Promise：避免并发调用触发重复加载
    if (!loadPromise) {
      loadPromise = loadWithRetry().catch((err) => {
        loadPromise = null // 失败时清空，允许后续重试
        throw err
      })
    }

    return loadPromise
  }
}

// ============================================================
// 便捷工具
// ============================================================

/**
 * 批量并行加载多个库
 *
 * @example
 * const [md, mermaid] = await loadAll([loadMarkdownIt, loadMermaid])
 */
export async function loadAll<T extends Array<() => Promise<unknown>>>(
  loaders: [...T],
): Promise<{ [K in keyof T]: Awaited<ReturnType<T[K]>> }> {
  const results = await Promise.all(loaders.map((loader) => loader()))
  return results as { [K in keyof T]: Awaited<ReturnType<T[K]>> }
}

/**
 * 预加载资源（不阻塞主流程，失败时静默处理）
 */
export function preload(loader: () => Promise<unknown>): void {
  loader().catch((err) => {
    console.warn('[cdnLoader] 预加载失败:', err)
  })
}
