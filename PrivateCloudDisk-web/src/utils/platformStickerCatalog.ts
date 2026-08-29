// ============================================================
// platformStickerCatalog.ts — 平台表情目录适配器
// ============================================================
// AUDIT FIX [4.1-4.10] / IM-EMOJI-SESSION-20260810：Unicode Emoji 由
// emoji-picker-element 独立处理；本文件只负责平台专属、可动画的贴纸资源。
// 平台消息仅持久化稳定标识和经过 HTTPS 校验的渲染 URL，不新增后端表情表，
// 因此不会改变既有 IM Protobuf 或消息表结构。
// ============================================================

import { GiphyFetch } from '@giphy/js-fetch-api'
import type { IGif } from '@giphy/js-types'

export interface PlatformSticker {
  id: string
  packId: string
  title: string
  url: string
  thumbnailUrl: string
  width: number
  height: number
  isAnimated: boolean
  format: 'gif' | 'webp'
}

interface StickerSearchResult {
  items: PlatformSticker[]
  configurationRequired: boolean
}

const CACHE_KEY = 'pcd-im-platform-sticker-cache-v1'
const FAVORITE_KEY = 'pcd-im-platform-sticker-favorites-v1'
const MAX_CACHE_ENTRIES = 80
const apiKey = import.meta.env.VITE_GIPHY_API_KEY?.trim()
const giphy = apiKey ? new GiphyFetch(apiKey) : null

function toSticker(gif: IGif): PlatformSticker {
  const animated = gif.images.fixed_width
  const thumbnail = gif.images.fixed_width_still
  return {
    id: String(gif.id),
    packId: 'giphy-stickers',
    title: gif.alt_text || gif.title || '平台表情',
    // WebP 优先可降低动画贴纸带宽；原始 GIF URL 作为不支持 WebP 时的后备。
    url: animated.webp || animated.url,
    thumbnailUrl: thumbnail.url || animated.url,
    width: animated.width,
    height: animated.height,
    isAnimated: true,
    format: animated.webp ? 'webp' : 'gif',
  }
}

function load(key: string): PlatformSticker[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(key) || '[]')
    return Array.isArray(parsed) ? parsed.filter(isSticker) : []
  } catch {
    return []
  }
}

function isSticker(value: unknown): value is PlatformSticker {
  if (!value || typeof value !== 'object') return false
  const sticker = value as Partial<PlatformSticker>
  return typeof sticker.id === 'string' && typeof sticker.url === 'string'
    && /^https:\/\//i.test(sticker.url)
}

function save(key: string, stickers: PlatformSticker[]): void {
  localStorage.setItem(key, JSON.stringify(stickers.slice(0, MAX_CACHE_ENTRIES)))
}

/**
 * 查询成熟第三方贴纸目录；未配置 API Key 时仅返回本地已缓存/收藏资源，
 * 不把网络配置异常伪装成可发送的表情，保证离线和降级场景可预期。
 */
export async function searchPlatformStickers(keyword = ''): Promise<StickerSearchResult> {
  const cached = load(CACHE_KEY)
  if (!giphy) return { items: filterCached(cached, keyword), configurationRequired: true }

  try {
    const response = await giphy.search(keyword.trim() || 'hello', {
      type: 'stickers',
      rating: 'g',
      limit: 30,
      lang: 'zh-CN',
    })
    const items = response.data.map(toSticker)
    save(CACHE_KEY, deduplicate([...items, ...cached]))
    return { items, configurationRequired: false }
  } catch {
    // 网络不可用时复用浏览器已缓存的目录；图片资源本身由浏览器 HTTP 缓存复用。
    return { items: filterCached(cached, keyword), configurationRequired: false }
  }
}

export function getFavoritePlatformStickers(): PlatformSticker[] {
  return load(FAVORITE_KEY)
}

export function toggleFavoritePlatformSticker(sticker: PlatformSticker): PlatformSticker[] {
  const current = getFavoritePlatformStickers()
  const next = current.some(item => item.id === sticker.id)
    ? current.filter(item => item.id !== sticker.id)
    : [sticker, ...current]
  save(FAVORITE_KEY, next)
  return next
}

function filterCached(stickers: PlatformSticker[], keyword: string): PlatformSticker[] {
  const query = keyword.trim().toLocaleLowerCase()
  if (!query) return stickers
  return stickers.filter(item => `${item.title} ${item.id}`.toLocaleLowerCase().includes(query))
}

function deduplicate(stickers: PlatformSticker[]): PlatformSticker[] {
  const ids = new Set<string>()
  return stickers.filter(item => !ids.has(item.id) && ids.add(item.id))
}
