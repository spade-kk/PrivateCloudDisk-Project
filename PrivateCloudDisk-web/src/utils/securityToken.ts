/**
 * src/utils/securityToken.ts
 * 安全令牌 / 请求签名服务
 *
 * 用途：
 * - 请求防重放：每次请求携带唯一 nonce + 时间戳
 * - 请求防篡改：对关键请求体进行 HMAC 签名
 * - 设备绑定：将请求与设备指纹关联
 *
 * 后端应验证：
 * 1. 时间戳与服务器时间偏差 < 5 分钟
 * 2. nonce 在 5 分钟内未被使用过（Redis 缓存）
 * 3. 签名与请求体一致
 */

import { hmacSign } from './crypto'
import { getVisitorId } from './fingerprint'

const NONCE_STORAGE_KEY = 'cloud_drive_nonce_counter'
const SIGN_SECRET_PREFIX = 'clouddrive-v1-request'

/**
 * 生成请求安全头
 *
 * @param body - 请求体（JSON 字符串或 undefined）
 * @returns 包含 X-Timestamp, X-Nonce, X-Signature 的对象
 */
export async function generateSecurityHeaders(
  body?: string
): Promise<Record<string, string>> {
  const timestamp = Date.now().toString()
  const nonce = generateNonce()
  const visitorId = await getVisitorId()

  // 签名内容: timestamp + nonce + visitorId + body
  const signPayload = [timestamp, nonce, visitorId, body || ''].join(':')
  const signSecret = SIGN_SECRET_PREFIX
  const signature = await hmacSign(signPayload, signSecret)

  return {
    'X-Timestamp': timestamp,
    'X-Nonce': nonce,
    'X-Device-Fingerprint': visitorId,
    'X-Request-Signature': signature,
  }
}

/**
 * 生成唯一 nonce
 * 格式: 计数器 + 随机数，确保全局唯一
 */
function generateNonce(): string {
  const counter = getNextCounter()
  const random = generateRandomHex(8)
  return `${counter}_${random}`
}

function getNextCounter(): number {
  try {
    let counter = parseInt(localStorage.getItem(NONCE_STORAGE_KEY) || '0', 10)
    counter = (counter + 1) % 999999
    localStorage.setItem(NONCE_STORAGE_KEY, counter.toString())
    return counter
  } catch {
    return Math.floor(Math.random() * 999999)
  }
}

function generateRandomHex(length: number): string {
  const bytes = crypto.getRandomValues(new Uint8Array(length))
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

/**
 * 验证安全头是否有效（客户端侧快速校验）
 * 完整校验应在后端进行
 */
export function validateSecurityHeaders(headers: Record<string, string>): {
  valid: boolean
  reason?: string
} {
  const timestamp = headers['X-Timestamp'] || headers['x-timestamp']
  const nonce = headers['X-Nonce'] || headers['x-nonce']
  const fingerprint = headers['X-Device-Fingerprint'] || headers['x-device-fingerprint']

  if (!timestamp) return { valid: false, reason: '缺少时间戳' }
  if (!nonce) return { valid: false, reason: '缺少 nonce' }
  if (!fingerprint) return { valid: false, reason: '缺少设备指纹' }

  // 检查时间戳是否在合理范围（5 分钟）
  const ts = parseInt(timestamp, 10)
  if (isNaN(ts)) return { valid: false, reason: '时间戳格式错误' }
  if (Math.abs(Date.now() - ts) > 5 * 60 * 1000) {
    return { valid: false, reason: '时间戳偏差过大' }
  }

  return { valid: true }
}