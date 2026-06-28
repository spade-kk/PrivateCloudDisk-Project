/**
 * SHA-256 哈希计算 Web Worker（模块化）
 *
 * 此 Worker 在独立线程中计算文件的 SHA-256 哈希值，避免阻塞主线程 UI。
 *
 * 降级方案：
 *   优先使用原生 crypto.subtle.digest（硬件加速），不可用时降级为
 *   crypto-js 纯 JS 实现。import.meta.env.DEV 是编译时常量，
 *   生产构建时 crypto-js 动态 import 被 tree-shaking 移除。
 *
 * 使用方式（helpers.ts）：
 *   new Worker(new URL('./sha256.worker.ts', import.meta.url), { type: 'module' })
 */

type CryptoJSModule = typeof import('crypto-js')

let _CryptoJS: CryptoJSModule | null = null

async function getCryptoJS(): Promise<CryptoJSModule | null> {
  if (import.meta.env.DEV) {
    if (!_CryptoJS) {
      _CryptoJS = await import('crypto-js')
    }
    return _CryptoJS
  }
  return null
}

/**
 * 将 ArrayBuffer 转换为 crypto-js WordArray
 */
function bufferToWordArray(buf: ArrayBuffer, C: CryptoJSModule): import('crypto-js').lib.WordArray {
  const u8 = new Uint8Array(buf)
  const words: number[] = []
  for (let i = 0; i < u8.length; i += 4) {
    words.push(
      ((u8[i] || 0) << 24) |
      ((u8[i + 1] || 0) << 16) |
      ((u8[i + 2] || 0) << 8) |
      (u8[i + 3] || 0),
    )
  }
  return C.lib.WordArray.create(words, u8.length)
}

/**
 * 计算 SHA-256 哈希
 * 优先原生 crypto.subtle，不可用时降级为 crypto-js
 */
async function computeHash(buf: ArrayBuffer): Promise<string> {
  // 优先使用原生 Web Crypto API
  if (typeof crypto !== 'undefined' && crypto.subtle) {
    try {
      const digest = await crypto.subtle.digest('SHA-256', buf)
      const arr = Array.from(new Uint8Array(digest))
      return arr.map((b) => b.toString(16).padStart(2, '0')).join('')
    } catch {
      // 原生 API 失败，降级到 crypto-js
    }
  }

  // 降级：crypto-js（仅开发环境）
  const C = await getCryptoJS()
  if (C) {
    const wordArray = bufferToWordArray(buf, C)
    return C.SHA256(wordArray).toString()
  }

  throw new Error('SHA-256 不可用：crypto.subtle 不可用且无降级方案')
}

self.onmessage = async (e: MessageEvent) => {
  try {
    const file: File = e.data
    const reader = new FileReader()

    reader.onload = async (ev) => {
      try {
        const hash = await computeHash(ev.target!.result as ArrayBuffer)
        self.postMessage({ hash })
      } catch (err) {
        self.postMessage({ error: (err as Error).message || 'SHA-256 compute failed' })
      }
    }
    reader.onerror = () => {
      self.postMessage({ error: 'File read failed' })
    }
    reader.readAsArrayBuffer(file)
  } catch (err) {
    self.postMessage({ error: (err as Error).message || 'Worker error' })
  }
}