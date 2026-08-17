// ============================================================
// imageCache.ts — 企业级图片异步加载与缓存管理器
// ============================================================
// 核心设计：
//   1. 使用 axios.get(responseType: 'blob') 加载图片，自动携带 Token
//   2. Blob → URL.createObjectURL() 转换，供 <img> 使用
//   3. LRU 淘汰策略，控制内存占用，淘汰时自动 revoke 旧 URL
//   4. 请求去重：同一 URL 并发请求只发一次，后续复用同一个 Promise
//   5. 批量预加载 API，支持优先级队列
//   6. 自动重试（指数退避）
// ============================================================

import { getDocumentThumbnailUrl, getThumbnailUrl } from '@/api/modules/preview'
import { getVideoThumbnailUrl } from '@/api/modules/video'
import { fetchPreviewContentBlob } from '@/api/modules/previewContent'
// 依赖 axios 封装的 get 方法，自动附带 Token 不需要使用统一封装的axios实例，不使用统一的请求拦截器 响应拦截器实现无感加载
import axios from 'axios'
import { cookie } from '@/utils/cookie'
import { TOKEN_COOKIE_KEY } from '@/utils/request'

// ---- 常量 ----
const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

/** 最大缓存条目数 */
const DEFAULT_MAX_ENTRIES = 200
/** 请求超时（ms） */
const REQUEST_TIMEOUT = 15000
/** 最大重试次数 */
const MAX_RETRIES = 2
/** 重试基础延迟（ms），指数退避 */
const RETRY_BASE_DELAY = 1000

// ---- 类型 ----

export type ThumbnailSize = 'small' | 'medium' | 'large'

interface CacheEntry {
  objectUrl: string
  blob: Blob
  lastAccess: number
  size: number
}

interface PendingRequest {
  promise: Promise<string>
  subscribers: number
}

// ---- LRU 缓存核心 ----

class ImageCacheManager {
  private cache = new Map<string, CacheEntry>()
  private pending = new Map<string, PendingRequest>()
  private maxEntries: number
  private accessOrder: string[] = [] // LRU 顺序

  constructor(maxEntries = DEFAULT_MAX_ENTRIES) {
    this.maxEntries = maxEntries
  }

  /**
   * 加载图片（带缓存、去重、重试）
   *
   * @param fileId - 文件 ID
   * @param size - 缩略图尺寸
   * @returns object URL，可直接用于 <img src>
   */
  async load(fileId: string, size: ThumbnailSize = 'small'): Promise<string> {
    const url = this.buildUrl(fileId, size)
    return this._loadByUrl(url)
  }

  /**
   * 加载视频缩略图（使用独立的视频缩略图接口，ffmpeg 首帧）
   *
   * @param fileId - 文件 ID
   * @param size - 缩略图尺寸
   * @returns object URL，可直接用于 <img src>
   */
  async loadVideo(fileId: string, size: ThumbnailSize = 'small'): Promise<string> {
    const url = this.buildVideoUrl(fileId, size)
    return this._loadByUrl(url)
  }

  /** 加载 Office/PDF 首页预览图，复用图片 LRU 与请求去重能力。 */
  async loadDocument(fileId: string, size: ThumbnailSize = 'small'): Promise<string> {
    return this._loadByUrl(getDocumentThumbnailUrl(fileId, size))
  }

  /** 加载原始图片内容，用于大图灯箱；不会把有损缩略图冒充原图。 */
  async loadOriginal(fileId: string, spaceId?: string): Promise<string> {
    const cacheKey = this.buildOriginalKey(fileId, spaceId)
    const cached = this.cache.get(cacheKey)
    if (cached) {
      cached.lastAccess = Date.now()
      this.touchLRU(cacheKey)
      return cached.objectUrl
    }
    const pending = this.pending.get(cacheKey)
    if (pending) {
      pending.subscribers++
      return pending.promise
    }

    /*
     * 需求三-1/2、四-2：原图首次加载与重试均申请独立 Preview Token。
     * 原行为直接访问无 Preview Token 的 URL，首次进入页面会失败，重试路径却可能因缓存时序不同而成功。
     */
    const promise = this.fetchOriginalWithRetry(fileId, spaceId, cacheKey, 0)
    this.pending.set(cacheKey, { promise, subscribers: 1 })
    try {
      return await promise
    } finally {
      this.pending.delete(cacheKey)
    }
  }

  /**
   * 通过 URL 加载图片（内部共享方法，含缓存、去重、重试）
   */
  private async _loadByUrl(url: string): Promise<string> {
    const cacheKey = url

    // 1. 命中缓存 → 更新 LRU 并返回
    const cached = this.cache.get(cacheKey)
    if (cached) {
      cached.lastAccess = Date.now()
      this.touchLRU(cacheKey)
      return cached.objectUrl
    }

    // 2. 正在请求中 → 复用同一个 Promise（去重）
    const pending = this.pending.get(cacheKey)
    if (pending) {
      pending.subscribers++
      return pending.promise
    }

    // 3. 发起新请求
    const promise = this.fetchWithRetry(url, 0)
    this.pending.set(cacheKey, { promise, subscribers: 1 })

    try {
      const objectUrl = await promise
      return objectUrl
    } finally {
      this.pending.delete(cacheKey)
    }
  }

  /**
   * 预加载一批图片
   *
   * @param files - { fileId, size } 列表
   * @returns 全部加载完成后 resolve
   */
  async preload(files: Array<{ fileId: string; size: ThumbnailSize }>): Promise<void> {
    const promises = files.map((f) =>
      this.load(f.fileId, f.size).catch(() => {
        /* 预加载失败静默忽略 */
      }),
    )
    await Promise.allSettled(promises)
  }

  /**
   * 预加载单个图片（不关心结果）
   */
  preloadOne(fileId: string, size: ThumbnailSize = 'small'): void {
    this.load(fileId, size).catch(() => {})
  }

  /**
   * 获取缓存的 object URL（不触发加载）
   */
  getCached(fileId: string, size: ThumbnailSize = 'small'): string | null {
    const url = this.buildUrl(fileId, size)
    const cached = this.cache.get(url)
    return cached ? cached.objectUrl : null
  }

  /**
   * 清除特定文件的缓存
   */
  evict(fileId: string, size: ThumbnailSize = 'small'): void {
    const url = this.buildUrl(fileId, size)
    this.removeEntry(url)
  }

  evictOriginal(fileId: string, spaceId?: string): void {
    this.removeEntry(this.buildOriginalKey(fileId, spaceId))
  }

  /**
   * 清空全部缓存
   */
  clear(): void {
    for (const [, entry] of this.cache) {
      URL.revokeObjectURL(entry.objectUrl)
    }
    this.cache.clear()
    this.accessOrder = []
    this.pending.clear()
  }

  /**
   * 获取缓存统计
   */
  getStats() {
    const totalSize = Array.from(this.cache.values()).reduce((sum, e) => sum + e.size, 0)
    return {
      entries: this.cache.size,
      maxEntries: this.maxEntries,
      totalSizeBytes: totalSize,
      totalSizeMB: (totalSize / 1024 / 1024).toFixed(2),
      pendingCount: this.pending.size,
    }
  }

  // ---- 私有方法 ----

  private buildUrl(fileId: string, size: ThumbnailSize): string {
    return getThumbnailUrl(fileId, size)
  }

  private buildVideoUrl(fileId: string, size: ThumbnailSize): string {
    return getVideoThumbnailUrl(fileId, size)
  }

  private buildOriginalKey(fileId: string, spaceId?: string): string {
    return `preview-source:${spaceId || 'personal'}:${fileId}`
  }

  private async fetchOriginalWithRetry(fileId: string, spaceId: string | undefined, cacheKey: string, attempt: number): Promise<string> {
    try {
      const blob = await fetchPreviewContentBlob(fileId, spaceId)
      const objectUrl = URL.createObjectURL(blob)
      this.addToCache(cacheKey, objectUrl, blob)
      return objectUrl
    } catch (error) {
      if (attempt < MAX_RETRIES) {
        const delay = RETRY_BASE_DELAY * Math.pow(2, attempt)
        await new Promise((resolve) => setTimeout(resolve, delay))
        return this.fetchOriginalWithRetry(fileId, spaceId, cacheKey, attempt + 1)
      }
      throw error
    }
  }

  /**
   * 带重试的图片加载
   */
  private async fetchWithRetry(url: string, attempt: number): Promise<string> {
    try {
      const blob = await this.fetchBlob(url)
      const objectUrl = URL.createObjectURL(blob)
      this.addToCache(url, objectUrl, blob)
      return objectUrl
    } catch (err) {
      if (attempt < MAX_RETRIES) {
        const delay = RETRY_BASE_DELAY * Math.pow(2, attempt)
        await new Promise((resolve) => setTimeout(resolve, delay))
        return this.fetchWithRetry(url, attempt + 1)
      }
      throw err
    }
  }

  /**
   * 使用 axios 以 blob 方式加载图片，自动携带 Token
   */
  private async fetchBlob(fullUrl: string): Promise<Blob> {
    // 从完整 URL 中提取相对路径（去掉 baseURL 前缀）
    const relativeUrl = fullUrl.startsWith(BASE_URL)
      ? fullUrl.slice(BASE_URL.length)
      : fullUrl

    const token = cookie.get(TOKEN_COOKIE_KEY)

    const response = await axios.get(`${BASE_URL}${relativeUrl}`, {
      responseType: 'blob',
      timeout: REQUEST_TIMEOUT,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    })

    // axios blob 响应：response.data 是 Blob
    const blob: Blob = response.data
    return blob
  }

  /**
   * 添加到 LRU 缓存
   */
  private addToCache(key: string, objectUrl: string, blob: Blob): void {
    // 淘汰最旧的条目
    while (this.cache.size >= this.maxEntries) {
      const oldest = this.accessOrder.shift()
      if (oldest) {
        this.removeEntry(oldest)
      }
    }

    this.cache.set(key, {
      objectUrl,
      blob,
      lastAccess: Date.now(),
      size: blob.size,
    })

    // 更新 LRU 顺序
    this.accessOrder = this.accessOrder.filter((k) => k !== key)
    this.accessOrder.push(key)
  }

  /**
   * 更新 LRU 访问顺序
   */
  private touchLRU(key: string): void {
    this.accessOrder = this.accessOrder.filter((k) => k !== key)
    this.accessOrder.push(key)
  }

  /**
   * 移除缓存条目并释放 object URL
   */
  private removeEntry(key: string): void {
    const entry = this.cache.get(key)
    if (entry) {
      URL.revokeObjectURL(entry.objectUrl)
      this.cache.delete(key)
    }
    this.accessOrder = this.accessOrder.filter((k) => k !== key)
  }
}

// ---- 单例导出 ----

export const imageCache = new ImageCacheManager()

// ---- 便捷函数 ----

/**
 * 异步加载图片缩略图，返回 object URL
 */
export function loadThumbnail(
  fileId: string,
  size: ThumbnailSize = 'small',
): Promise<string> {
  return imageCache.load(fileId, size)
}

/**
 * 异步加载视频缩略图（使用独立视频缩略图接口），返回 object URL
 */
export function loadVideoThumbnail(
  fileId: string,
  size: ThumbnailSize = 'small',
): Promise<string> {
  return imageCache.loadVideo(fileId, size)
}

/**
 * AUDIT FIX [5.2]（需求五-6）：加载 Office/PDF 首页预览图。
 */
export function loadDocumentThumbnail(
  fileId: string,
  size: ThumbnailSize = 'small',
): Promise<string> {
  return imageCache.loadDocument(fileId, size)
}

/**
 * 加载需要鉴权的短媒体 Blob。返回的 URL 由调用组件负责 revoke，避免 30 秒视频长期占用 LRU 内存。
 */
export async function loadAuthenticatedMedia(
  fullUrl: string,
  signal?: AbortSignal,
): Promise<string> {
  const relativeUrl = fullUrl.startsWith(BASE_URL) ? fullUrl.slice(BASE_URL.length) : fullUrl
  const token = cookie.get(TOKEN_COOKIE_KEY)
  const response = await axios.get(`${BASE_URL}${relativeUrl}`, {
    responseType: 'blob',
    timeout: 30000,
    signal,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })
  return URL.createObjectURL(response.data as Blob)
}

/**
 * AUDIT FIX [3.1]: 大图预览直接获取原文件 Blob，确保缩放时保持原始清晰度。
 */
export function loadOriginalImage(fileId: string, spaceId?: string): Promise<string> {
  return imageCache.loadOriginal(fileId, spaceId)
}

/**
 * 预加载图片缩略图（不关心结果）
 */
export function preloadThumbnail(
  fileId: string,
  size: ThumbnailSize = 'small',
): void {
  imageCache.preloadOne(fileId, size)
}

/**
 * 批量预加载
 */
export function preloadThumbnails(
  files: Array<{ fileId: string; size: ThumbnailSize }>,
): Promise<void> {
  return imageCache.preload(files)
}
