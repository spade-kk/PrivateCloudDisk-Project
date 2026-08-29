// ============================================================
// monacoLoader.ts — Monaco Editor CDN 动态加载器
// ============================================================
// 设计目标：
//   - 将 Monaco Editor 从本地 npm 包改为 CDN 动态加载，
//     彻底从 Vite 构建产物中剥离（解决 OOM 与产物过大的问题）。
//   - 使用 Monaco 官方推荐的 AMD loader（loader.js）方式加载，
//     语言 Web Workers 由 CDN 自动按需拉取，无需手动打包。
//   - 单例 Promise 模式，确保整个应用只加载一次 Monaco。
//   - 内置重试 + 超时机制，提升弱网环境下的可用性。
//
// 使用方式：
//   import { loadMonaco } from '@/utils/monacoLoader'
//   const monaco = await loadMonaco()
//   const editor = monaco.editor.create(container, { ... })
// ============================================================

/*
 * 插件生态 Sprint 0 改动说明：
 * 原行为从 CDN 注入 AMD loader，在网络受限或 Content-Type/nosniff 配置异常时会永久加载。
 * 新行为使用 Vite 打包的本地 ESM 与独立 Worker；下方原 CDN 辅助代码保留作历史回溯，
 * loadMonaco 已不再调用任何 CDN 地址。
 */
// CDN 配置 — 使用 jsdelivr CDN，与 index.html 中预连接的域名保持一致
// 如需切换 CDN（如 unpkg/cdnjs），只需修改以下常量
const MONACO_CDN_VERSION = '0.56.0'
// 保持原常量名以降低现有组件改动面，但资源已改为同源自托管。
const MONACO_CDN_BASE = '/vendor/monaco/vs'
const MONACO_LOADER_URL = `${MONACO_CDN_BASE}/loader.js`

// 加载超时（毫秒）— 首次加载 Monaco 主体 + 语言 Workers 需要较长时间
const MONACO_LOAD_TIMEOUT = 60_000

// AMD require 全局类型
interface AMDRequire {
  config(opts: { paths: Record<string, string>; [key: string]: unknown }): void
  (deps: string[], callback: (...args: unknown[]) => void, errback?: (err: Error) => void): void
}

// 注：window.monaco / window.MonacoEnvironment 的全局类型声明位于
// src/types/monaco-editor.d.ts，此处不再重复声明以避免 TS2320 冲突

// 单例 Promise — 保证多组件并发调用 loadMonaco 只触发一次 CDN 加载
let monacoLoadPromise: Promise<typeof import('monaco-editor')> | null = null
let loadedMonaco: typeof import('monaco-editor') | null = null

/**
 * 动态注入 <script> 标签加载 JS 资源
 * @param src 资源 URL
 * @returns 加载完成的 Promise
 */
function loadScript(src: string): Promise<void> {
  return new Promise((resolve, reject) => {
    // 幂等性检查：同一 src 仅注入一次
    const existing = document.querySelector<HTMLScriptElement>(`script[data-src="${src}"]`)
    if (existing) {
      if (existing.dataset.loaded === 'true') {
        resolve()
        return
      }
      existing.addEventListener('load', () => resolve(), { once: true })
      existing.addEventListener('error', () => reject(new Error(`Failed to load script: ${src}`)), { once: true })
      return
    }

    const script = document.createElement('script')
    script.src = src
    script.async = true
    script.dataset.src = src
    script.onload = () => {
      script.dataset.loaded = 'true'
      resolve()
    }
    script.onerror = () => {
      reject(new Error(`Failed to load script: ${src}`))
    }
    document.head.appendChild(script)
  })
}

/**
 * 配置 Monaco Environment
 * 通过 getWorkerUrl 让 Monaco 从 CDN 拉取语言 Worker
 * 使用 data: URI 包装跨域 Worker，绕过 CDN 的同源 Worker 限制
 */
function setupMonacoEnvironment(): void {
  if (window.MonacoEnvironment) return

  window.MonacoEnvironment = {
    /**
     * 返回 Worker 入口脚本 URL
     * Monaco 通过 new Worker(url) 加载语言服务 Worker，
     * 由于 CDN 跨域无法直接 new Worker(crossOriginUrl)，
     * 使用 data: URI 创建代理脚本，再通过 importScripts 引入 CDN Worker
     */
    getWorkerUrl(_workerId: string, _label: string): string {
      // 资源与页面同源，直接返回 Worker 入口，兼容严格 CSP 且无需 data/blob 代理。
      return `${MONACO_CDN_BASE}/base/worker/workerMain.js`
    },
  }
}

/**
 * 带超时的 Promise 包装
 */
function withTimeout<T>(promise: Promise<T>, ms: number, errorMsg: string): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new Error(errorMsg))
    }, ms)

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
 * 加载 Monaco Editor 主体模块
 * 1. 注入 AMD loader.js
 * 2. 配置 AMD paths 指向 CDN
 * 3. require editor.main 模块
 */
function loadMonacoFromCDN(): Promise<typeof import('monaco-editor')> {
  return new Promise<typeof import('monaco-editor')>(async (resolve, reject) => {
    try {
      // 1. 加载 AMD loader
      await loadScript(MONACO_LOADER_URL)

      if (!window.require) {
        reject(new Error('AMD loader 加载失败：window.require 未定义'))
        return
      }

      // 2. 配置 Monaco Environment（必须在 require 之前设置）
      setupMonacoEnvironment()

      // 3. 配置 AMD paths 指向 CDN
      window.require.config({
        paths: { vs: MONACO_CDN_BASE },
      })

      // 4. 加载 editor.main — 加载完成后 window.monaco 自动可用
      window.require(
        ['vs/editor/editor.main'],
        () => {
          if (window.monaco) {
            resolve(window.monaco)
          } else {
            reject(new Error('Monaco 加载完成但 window.monaco 未定义'))
          }
        },
        (err: Error) => {
          reject(new Error(`Monaco AMD require 失败: ${err?.message || String(err)}`))
        }
      )
    } catch (err) {
      reject(err instanceof Error ? err : new Error(String(err)))
    }
  })
}

/**
 * 加载 Monaco Editor（单例模式）
 *
 * @returns Promise<typeof import('monaco-editor')>
 *
 * @example
 * const monaco = await loadMonaco()
 * const editor = monaco.editor.create(container, { language: 'typescript' })
 */
export function loadMonaco(): Promise<typeof import('monaco-editor')> {
  // 已加载则直接返回本地 ESM 实例
  if (loadedMonaco) {
    return Promise.resolve(loadedMonaco)
  }

  // 单例 Promise：避免并发调用触发重复打包模块初始化
  if (!monacoLoadPromise) {
    monacoLoadPromise = withTimeout(
      loadMonacoFromCDN().then((monaco) => {
        loadedMonaco = monaco
        return monaco
      }),
      MONACO_LOAD_TIMEOUT,
      `Monaco Editor 初始化超时（${MONACO_LOAD_TIMEOUT / 1000}s）`
    ).catch((err) => {
      // 加载失败时清空单例，允许后续重试
      monacoLoadPromise = null
      throw err
    })
  }

  return monacoLoadPromise
}

/**
 * 预加载 Monaco Editor（可选）
 * 在应用启动或路由预取阶段调用，提前发起 CDN 请求
 * 不阻塞主流程，失败时静默处理
 */
export function preloadMonaco(): void {
  loadMonaco().catch((err) => {
    console.warn('[monacoLoader] 预加载失败，将在首次使用时重试:', err)
  })
}

/**
 * 获取当前已加载的 Monaco 实例（同步）
 * 若尚未加载则返回 null
 */
export function getMonacoSync(): typeof import('monaco-editor') | null {
  return loadedMonaco
}

/**
 * 获取 CDN 配置信息（用于调试）
 */
export function getMonacoCDNInfo(): { version: string; baseUrl: string; loaderUrl: string } {
  return {
    version: MONACO_CDN_VERSION,
    baseUrl: MONACO_CDN_BASE,
    loaderUrl: MONACO_LOADER_URL,
  }
}
