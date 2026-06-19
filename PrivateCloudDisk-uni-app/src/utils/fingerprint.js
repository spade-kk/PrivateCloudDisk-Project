/**
 * utils/fingerprint.js - uni-app 设备指纹服务
 *
 * 使用 uni.getSystemInfoSync() 获取跨平台设备信息，
 * 生成唯一的设备指纹标识，与网关 DeviceIdentityFilter 对接。
 *
 * 各平台可获取的设备信息：
 * - App: deviceId, deviceBrand, deviceModel, system, platform 等
 * - 小程序: deviceId(openId), system, platform, brand, model 等
 * - H5: ua, screenWidth, screenHeight 等
 */

import { sha256Hash } from './crypto'
import { STORAGE_KEYS } from './const'

// ============================================================
// 常量
// ============================================================

const FP_CACHE_KEY = 'pcd_device_fingerprint'
const FP_CACHE_TTL = 24 * 60 * 60 * 1000 // 24h

// ============================================================
// 缓存
// ============================================================

let cachedFingerprint = null
let cachedFingerprintTime = 0

// ============================================================
// 获取设备信息
// ============================================================

function getDeviceInfo() {
  try {
    const info = uni.getSystemInfoSync()
    return {
      brand: info.brand || '',
      model: info.model || '',
      system: info.system || '',
      platform: info.platform || '',
      deviceId: info.deviceId || '',
      screenWidth: info.screenWidth || 0,
      screenHeight: info.screenHeight || 0,
      pixelRatio: info.pixelRatio || 0,
      language: info.language || '',
      version: info.version || '',
      SDKVersion: info.SDKVersion || '',
      host: info.host || '',
      appName: info.appName || '',
      appVersion: info.appVersion || '',
      devicePixelRatio: info.devicePixelRatio || 0,
    }
  } catch (e) {
    console.warn('[Fingerprint] getSystemInfoSync 失败:', e)
    return {
      brand: 'unknown',
      model: 'unknown',
      system: 'unknown',
      platform: 'unknown',
    }
  }
}

/**
 * 获取设备指纹
 * 优先返回缓存（24h 有效），过期或不可用时重新采集
 * @returns {Promise<string>} 设备指纹（SHA-256 哈希）
 */
export async function getDeviceFingerprint() {
  const now = Date.now()

  // 内存缓存
  if (cachedFingerprint && now - cachedFingerprintTime < FP_CACHE_TTL) {
    return cachedFingerprint
  }

  // 本地存储缓存
  try {
    const stored = uni.getStorageSync(FP_CACHE_KEY)
    if (stored) {
      const parsed = JSON.parse(stored)
      if (parsed.fingerprint && parsed.timestamp && now - parsed.timestamp < FP_CACHE_TTL) {
        cachedFingerprint = parsed.fingerprint
        cachedFingerprintTime = parsed.timestamp
        return cachedFingerprint
      }
    }
  } catch (e) {
    // 静默忽略
  }

  // 采集设备信息
  const info = getDeviceInfo()
  const components = [
    info.brand,
    info.model,
    info.system,
    info.platform,
    info.deviceId,
    String(info.screenWidth),
    String(info.screenHeight),
    String(info.pixelRatio),
    info.language,
    info.appName,
    info.appVersion,
  ].filter(Boolean).join('|')

  // 生成 SHA-256 指纹
  const fingerprint = await sha256Hash(components)
  cachedFingerprint = fingerprint
  cachedFingerprintTime = now

  // 持久化
  try {
    uni.setStorageSync(FP_CACHE_KEY, JSON.stringify({
      fingerprint,
      timestamp: now,
    }))
  } catch (e) {
    // 静默忽略
  }

  return fingerprint
}

/**
 * 获取 visitorId（与 Vue.js 前端保持一致）
 * @returns {Promise<string>}
 */
export async function getVisitorId() {
  return getDeviceFingerprint()
}

/**
 * 清除指纹缓存（切换账号时调用）
 */
export function clearFingerprintCache() {
  cachedFingerprint = null
  cachedFingerprintTime = 0
  try {
    uni.removeStorageSync(FP_CACHE_KEY)
  } catch (e) {
    // 静默忽略
  }
}