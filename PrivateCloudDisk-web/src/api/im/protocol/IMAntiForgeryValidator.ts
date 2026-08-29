// ============================================================
// protocol/IMAntiForgeryValidator.ts — 防伪造与完整性验证器
// ============================================================
// 企业级防伪造安全措施的浏览器端实现，与后端 IMAntiForgeryValidator 对齐。
//
// 安全特性：
//   - HMAC-SHA256 消息签名：每条消息附带签名，防止传输过程中被篡改
//   - 时间戳防重放：消息携带时间戳，超出时间窗口的消息被拒绝
//   - 消息 ID 校验：验证 messageId 格式，防止注入攻击
//   - 长度限制：单条消息最大长度限制，防止内存溢出攻击
//
// 与后端对齐的常量：
//   - HMAC 算法: HmacSHA256
//   - HMAC 签名长度: 32 字节
//   - 消息时间窗口: 300 秒（±5 分钟）
//   - 最大消息体大小: 1 MB
//   - 消息 ID 格式: ^[a-zA-Z0-9_\\-]+$
// ============================================================

import { constantTimeEqual } from './IMCryptoCodec'

// ============================================================
// 常量
// ============================================================

/** HMAC 算法名称 */
export const HMAC_ALGORITHM = 'HMAC'

/** HMAC 签名长度（字节） */
export const HMAC_SIGNATURE_LENGTH = 32

/** 消息时间窗口（秒）— 超出此窗口的消息被视为重放 */
export const MESSAGE_TIME_WINDOW_SEC = 300

/** 最大消息体大小（字节）— 默认 1MB */
export const MAX_MESSAGE_BODY_SIZE = 1024 * 1024

/** 最大消息头大小（字节） */
export const MAX_MESSAGE_HEADER_SIZE = 64 * 1024

/** 消息 ID 最大长度 */
export const MAX_MESSAGE_ID_LENGTH = 128

/** 发送者 ID 最大长度 */
export const MAX_SENDER_ID_LENGTH = 64

/** 消息 ID 合法字符正则（字母数字、下划线、连字符） */
const MESSAGE_ID_PATTERN = /^[a-zA-Z0-9_\-]+$/

// ============================================================
// HMAC 签名
// ============================================================

/**
 * 对消息体计算 HMAC-SHA256 签名
 *
 * <p>与后端 IMAntiForgeryValidator.sign 对齐。</p>
 *
 * @param messageBody 消息体（加密后的二进制数据）
 * @param hmacKey HMAC 签名密钥（派生自 Session Key）
 * @returns HMAC 签名（32 字节）
 */
export async function sign(
  messageBody: Uint8Array,
  hmacKey: CryptoKey,
): Promise<Uint8Array> {
  // 注意：Uint8Array<ArrayBufferLike> 不兼容 BufferSource，需转换为 ArrayBuffer
  const dataBuffer = messageBody.buffer.slice(messageBody.byteOffset, messageBody.byteOffset + messageBody.byteLength) as ArrayBuffer
  const signature = await crypto.subtle.sign(
    { name: HMAC_ALGORITHM, hash: 'SHA-256' },
    hmacKey,
    dataBuffer,
  )
  return new Uint8Array(signature)
}

/**
 * 验证 HMAC-SHA256 签名
 *
 * <p>使用常数时间比较，防止时序攻击。
 * 与后端 IMAntiForgeryValidator.verify 对齐。</p>
 *
 * @param messageBody 消息体
 * @param signature 待验证的签名
 * @param hmacKey 签名密钥
 * @returns true 如果签名有效
 */
export async function verify(
  messageBody: Uint8Array,
  signature: Uint8Array,
  hmacKey: CryptoKey,
): Promise<boolean> {
  if (!signature || signature.length !== HMAC_SIGNATURE_LENGTH) {
    return false
  }
  const expected = await sign(messageBody, hmacKey)
  return constantTimeEqual(expected, signature)
}

// ============================================================
// 时间戳防重放
// ============================================================

/**
 * 验证消息时间戳是否在有效窗口内
 *
 * <p>与后端 IMAntiForgeryValidator.isTimestampValid 对齐。
 * 允许 ±5 分钟的时间偏差，应对客户端与服务端时钟不同步。</p>
 *
 * @param messageTimestamp 消息时间戳（Unix 毫秒）
 * @returns true 如果时间戳有效
 */
export function isTimestampValid(messageTimestamp: number): boolean {
  const now = Date.now()
  const diff = Math.abs(now - messageTimestamp)
  return diff <= MESSAGE_TIME_WINDOW_SEC * 1000
}

// ============================================================
// 输入验证（防注入）
// ============================================================

/**
 * 验证消息 ID 格式
 *
 * <p>合法格式: 字母数字、下划线、连字符，长度 1-128。
 * 与后端 IMAntiForgeryValidator.isValidMessageId 对齐。</p>
 */
export function isValidMessageId(messageId: string | undefined): boolean {
  if (!messageId || messageId.length === 0 || messageId.length > MAX_MESSAGE_ID_LENGTH) {
    return false
  }
  return MESSAGE_ID_PATTERN.test(messageId)
}

/**
 * 验证用户/发送者 ID 格式
 *
 * <p>与后端 IMAntiForgeryValidator.isValidUserId 对齐。</p>
 */
export function isValidUserId(userId: string | undefined): boolean {
  if (!userId || userId.length === 0 || userId.length > MAX_SENDER_ID_LENGTH) {
    return false
  }
  return MESSAGE_ID_PATTERN.test(userId)
}

/**
 * 验证消息体大小
 */
export function isValidBodySize(bodySize: number): boolean {
  return bodySize > 0 && bodySize <= MAX_MESSAGE_BODY_SIZE
}

/**
 * 验证消息头大小
 */
export function isValidHeaderSize(headerSize: number): boolean {
  return headerSize > 0 && headerSize <= MAX_MESSAGE_HEADER_SIZE
}

/**
 * 清理危险字符串（防 XSS/注入）
 *
 * <p>与后端 IMAntiForgeryValidator.sanitize 对齐。</p>
 *
 * @param input 原始输入
 * @returns 清理后的字符串
 */
export function sanitize(input: string | null | undefined): string {
  if (!input) return ''
  return input
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;')
    .replace(/&/g, '&amp;')
    .replace(/\0/g, '') // 去除 null 字节
    .replace(/\n/g, ' ')
    .replace(/\r/g, '')
}
