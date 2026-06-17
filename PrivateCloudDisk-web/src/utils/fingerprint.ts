/**
 * src/utils/fingerprint.ts
 * 浏览器指纹服务 - 基于 FingerprintJS 实现
 *
 * 用途：
 * - 未登录用户识别：个性化内容、限流、安全风控
 * - 设备信任标记：记住受信任设备，减少重复验证
 * - 异常登录检测：后端可根据指纹变化判断是否异常设备
 * - 审计追溯：关联用户操作与设备指纹
 */

import FingerprintJS, { type GetResult } from '@fingerprintjs/fingerprintjs'

const FP_STORAGE_KEY = 'cloud_drive_fingerprint'
const FP_VISITOR_ID_KEY = 'cloud_drive_visitor_id'
const FP_CACHE_TTL = 24 * 60 * 60 * 1000 // 24h

interface CachedFingerprint {
  visitorId: string
  fingerprint: GetResult
  timestamp: number
}

let fpAgent: ReturnType<typeof FingerprintJS.load> | null = null
let cachedResult: CachedFingerprint | null = null

function loadFromStorage(): CachedFingerprint | null {
  try {
    const raw = localStorage.getItem(FP_STORAGE_KEY)
    if (!raw) return null
    const parsed: CachedFingerprint = JSON.parse(raw)
    if (Date.now() - parsed.timestamp > FP_CACHE_TTL) {
      localStorage.removeItem(FP_STORAGE_KEY)
      return null
    }
    return parsed
  } catch {
    localStorage.removeItem(FP_STORAGE_KEY)
    return null
  }
}

function saveToStorage(data: CachedFingerprint): void {
  try {
    localStorage.setItem(FP_STORAGE_KEY, JSON.stringify(data))
  } catch {
    // localStorage 不可用时静默忽略
  }
}

async function getFpAgent() {
  if (!fpAgent) {
    fpAgent = FingerprintJS.load()
  }
  return fpAgent
}

/**
 * 获取浏览器指纹
 * 优先返回缓存（24h 有效），缓存过期或不可用时重新采集
 */
export async function getFingerprint(): Promise<CachedFingerprint> {
  if (cachedResult && Date.now() - cachedResult.timestamp < FP_CACHE_TTL) {
    return cachedResult
  }

  const cached = loadFromStorage()
  if (cached) {
    cachedResult = cached
    return cached
  }

  try {
    const agent = await getFpAgent()
    const result = await agent.get()

    const data: CachedFingerprint = {
      visitorId: result.visitorId,
      fingerprint: result,
      timestamp: Date.now(),
    }

    cachedResult = data
    saveToStorage(data)
    return data
  } catch (err) {
    console.warn('[Fingerprint] 采集失败，使用降级策略:', err)
    return getFallbackFingerprint()
  }
}

/**
 * 获取 visitorId（短标识）
 * 适合作为请求头直接发送
 */
export async function getVisitorId(): Promise<string> {
  try {
    const fp = await getFingerprint()
    return fp.visitorId
  } catch {
    return getFallbackVisitorId()
  }
}

/**
 * 降级指纹：基于 navigator 基础属性生成
 * 在 FingerprintJS 不可用时使用
 */
function getFallbackFingerprint(): CachedFingerprint {
  const visitorId = getFallbackVisitorId()
  return {
    visitorId,
    fingerprint: {
      visitorId,
      requestId: '',
      confidence: { score: 0.5 },
      components: {},
      version: 'fallback',
    } as GetResult,
    timestamp: Date.now(),
  }
}

function getFallbackVisitorId(): string {
  let id = localStorage.getItem(FP_VISITOR_ID_KEY)
  if (id) return id

  const components = [
    navigator.hardwareConcurrency || '',
    navigator.deviceMemory || '',
    navigator.userAgent || '',
    screen.width,
    screen.height,
    screen.colorDepth,
    new Date().getTimezoneOffset(),
    navigator.language,
  ].join('|')

  id = 'fallback_' + hashString(components)
  try {
    localStorage.setItem(FP_VISITOR_ID_KEY, id)
  } catch {
    // ignore
  }
  return id
}

/**
 * 简单字符串哈希（djb2）
 */
function hashString(str: string): string {
  let hash = 5381
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) + hash + str.charCodeAt(i)) & 0xffffffff
  }
  return Math.abs(hash).toString(16).padStart(8, '0')
}

/**
 * 获取指纹组件详情（用于调试和风控分析）
 */
export async function getFingerprintComponents(): Promise<Record<string, unknown>> {
  const fp = await getFingerprint()
  return fp.fingerprint.components || {}
}

/**
 * 清除指纹缓存
 * 当用户主动清除数据或登出时可调用
 */
export function clearFingerprintCache(): void {
  cachedResult = null
  localStorage.removeItem(FP_STORAGE_KEY)
  localStorage.removeItem(FP_VISITOR_ID_KEY)
}

/**
 * 检查指纹是否已就绪（非降级）
 */
export async function isFingerprintReady(): Promise<boolean> {
  try {
    const fp = await getFingerprint()
    return fp.fingerprint.version !== 'fallback'
  } catch {
    return false
  }
}