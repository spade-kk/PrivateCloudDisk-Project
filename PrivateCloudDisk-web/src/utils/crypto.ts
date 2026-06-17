/**
 * src/utils/crypto.ts
 * Web Crypto API 加密工具集
 *
 * 功能：
 * 1. PBKDF2 密码哈希 - 客户端预哈希，后端存储 bcrypt/scrypt 二次哈希
 * 2. AES-256-GCM 对称加密 - 敏感数据加密存储/传输
 * 3. 密码强度评估
 * 4. 安全随机数生成
 *
 * 安全设计原则：
 * - 密码永远不在网络中以明文传输
 * - 客户端预哈希 + 服务端二次哈希 = 双重保护
 * - 所有加密操作使用 Web Crypto API（硬件加速、不可被劫持）
 */

// ============================================================
// 常量配置
// ============================================================

/** PBKDF2 迭代次数（建议 600,000+，根据设备性能调整） */
const PBKDF2_ITERATIONS = 600_000

/** PBKDF2 输出密钥长度（bits） */
const PBKDF2_KEY_LENGTH = 256

/** PBKDF2 哈希算法 */
const PBKDF2_HASH = 'SHA-256'

/** AES-GCM 密钥长度（bits） */
const AES_KEY_LENGTH = 256

/** AES-GCM IV 长度（bytes） */
const AES_IV_LENGTH = 12

/** 密码最小长度 */
export const PASSWORD_MIN_LENGTH = 8

/** 密码最大长度 */
export const PASSWORD_MAX_LENGTH = 128

/** 密码强度等级 */
export enum PasswordStrength {
  WEAK = 'weak',
  FAIR = 'fair',
  GOOD = 'good',
  STRONG = 'strong',
}

// ============================================================
// 编码工具
// ============================================================

function buf2hex(buffer: ArrayBuffer): string {
  return Array.from(new Uint8Array(buffer))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

function hex2buf(hex: string): ArrayBuffer {
  const bytes = new Uint8Array(hex.length / 2)
  for (let i = 0; i < hex.length; i += 2) {
    bytes[i / 2] = parseInt(hex.substring(i, i + 2), 16)
  }
  return bytes.buffer
}

function str2buf(str: string): Uint8Array {
  return new TextEncoder().encode(str)
}

function buf2str(buffer: ArrayBuffer): string {
  return new TextDecoder().decode(buffer)
}

function buf2base64(buffer: ArrayBuffer): string {
  return btoa(String.fromCharCode(...new Uint8Array(buffer)))
}

function base642buf(base64: string): ArrayBuffer {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes.buffer
}

// ============================================================
// PBKDF2 密码哈希
// ============================================================

/**
 * 使用 PBKDF2 对密码进行客户端预哈希
 *
 * 为什么需要客户端预哈希？
 * 1. 密码明文永不离开浏览器内存
 * 2. 即使 TLS 被中间人攻击（企业内部证书），密码也不是明文
 * 3. 后端收到的是 hash 后的值，再进行 bcrypt/scrypt 二次哈希
 *
 * @param password - 明文密码
 * @param salt - 盐值（可从服务端获取或使用固定盐 + 用户标识组合）
 * @returns 格式为 "pbkdf2:sha256:iterations$salt$hash" 的字符串
 */
export async function pbkdf2HashPassword(
  password: string,
  salt?: string
): Promise<string> {
  // 如果没有提供 salt，生成随机 salt
  const saltBytes = salt ? hex2buf(salt) : crypto.getRandomValues(new Uint8Array(16))

  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    str2buf(password),
    'PBKDF2',
    false,
    ['deriveBits']
  )

  const derivedBits = await crypto.subtle.deriveBits(
    {
      name: 'PBKDF2',
      salt: saltBytes,
      iterations: PBKDF2_ITERATIONS,
      hash: PBKDF2_HASH,
    },
    keyMaterial,
    PBKDF2_KEY_LENGTH
  )

  const saltHex = buf2hex(saltBytes)
  const hashHex = buf2hex(derivedBits)

  return `pbkdf2:sha256:${PBKDF2_ITERATIONS}$${saltHex}$${hashHex}`
}

/**
 * 简化版密码哈希 - 用于 API 传输
 * 返回可发送给后端的密码哈希值
 */
export async function hashPasswordForTransport(
  password: string,
  phoneOrEmail: string
): Promise<string> {
  // 使用用户标识作为 salt 的一部分，确保同一密码在不同用户间 hash 不同
  const saltInput = `clouddrive:v1:${phoneOrEmail}`
  const saltHash = await crypto.subtle.digest('SHA-256', str2buf(saltInput))
  const salt = buf2hex(saltHash).substring(0, 32)

  return pbkdf2HashPassword(password, salt)
}

// ============================================================
// AES-256-GCM 对称加密
// ============================================================

/**
 * 使用 AES-256-GCM 加密数据
 *
 * @param plaintext - 明文
 * @param key - 256-bit 密钥（hex 字符串，64 字符）
 * @returns Base64 编码的密文（格式: iv + ciphertext + authTag）
 */
export async function aesEncrypt(plaintext: string, keyHex: string): Promise<string> {
  const keyBytes = hex2buf(keyHex)
  const iv = crypto.getRandomValues(new Uint8Array(AES_IV_LENGTH))

  const cryptoKey = await crypto.subtle.importKey(
    'raw',
    keyBytes,
    { name: 'AES-GCM' },
    false,
    ['encrypt']
  )

  const encrypted = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    cryptoKey,
    str2buf(plaintext)
  )

  // 将 IV + 密文 拼接后 Base64 编码
  const combined = new Uint8Array(iv.length + encrypted.byteLength)
  combined.set(iv)
  combined.set(new Uint8Array(encrypted), iv.length)

  return buf2base64(combined.buffer)
}

/**
 * 使用 AES-256-GCM 解密数据
 *
 * @param ciphertextBase64 - Base64 编码的密文（格式: iv + ciphertext）
 * @param keyHex - 256-bit 密钥（hex 字符串）
 * @returns 明文
 */
export async function aesDecrypt(ciphertextBase64: string, keyHex: string): Promise<string> {
  const keyBytes = hex2buf(keyHex)
  const combined = new Uint8Array(base642buf(ciphertextBase64))

  const iv = combined.slice(0, AES_IV_LENGTH)
  const ciphertext = combined.slice(AES_IV_LENGTH)

  const cryptoKey = await crypto.subtle.importKey(
    'raw',
    keyBytes,
    { name: 'AES-GCM' },
    false,
    ['decrypt']
  )

  const decrypted = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv },
    cryptoKey,
    ciphertext
  )

  return buf2str(decrypted)
}

/**
 * 生成随机 AES-256 密钥
 * @returns hex 编码的密钥字符串
 */
export function generateAesKey(): string {
  const key = crypto.getRandomValues(new Uint8Array(32))
  return buf2hex(key.buffer)
}

// ============================================================
// 密码强度评估
// ============================================================

/**
 * 评估密码强度
 * @returns 0-4 的分数和等级描述
 */
export function evaluatePasswordStrength(password: string): {
  score: number
  strength: PasswordStrength
  feedback: string
} {
  if (!password || password.length < PASSWORD_MIN_LENGTH) {
    return { score: 0, strength: PasswordStrength.WEAK, feedback: '密码长度不足' }
  }

  let score = 0

  // 长度评分
  if (password.length >= PASSWORD_MIN_LENGTH) score += 1
  if (password.length >= 12) score += 1
  if (password.length >= 16) score += 1

  // 字符多样性评分
  if (/[a-z]/.test(password)) score += 1
  if (/[A-Z]/.test(password)) score += 1
  if (/[0-9]/.test(password)) score += 1
  if (/[^a-zA-Z0-9]/.test(password)) score += 1

  // 常见弱密码模式检测
  if (/^(123456|password|qwerty|admin|letmein|welcome)/i.test(password)) {
    score = 0
  }
  // 重复字符
  if (/(.)\1{3,}/.test(password)) {
    score = Math.max(0, score - 2)
  }
  // 连续字符
  if (/0123|1234|2345|3456|4567|5678|6789|abcd|bcde|cdef|defg/i.test(password)) {
    score = Math.max(0, score - 1)
  }

  // 归一化到 0-4
  const normalizedScore = Math.min(4, Math.floor(score / 2))

  const strengthMap: Record<number, PasswordStrength> = {
    0: PasswordStrength.WEAK,
    1: PasswordStrength.WEAK,
    2: PasswordStrength.FAIR,
    3: PasswordStrength.GOOD,
    4: PasswordStrength.STRONG,
  }

  const feedbackMap: Record<number, string> = {
    0: '密码过于简单，容易被破解',
    1: '密码较弱，建议增加长度和特殊字符',
    2: '密码强度一般，可以继续加强',
    3: '密码强度良好',
    4: '密码强度很高',
  }

  return {
    score: normalizedScore,
    strength: strengthMap[normalizedScore],
    feedback: feedbackMap[normalizedScore],
  }
}

// ============================================================
// 安全随机数
// ============================================================

/**
 * 生成密码学安全的随机字符串
 * @param length - 字符长度
 * @param charset - 字符集（默认字母数字混合）
 */
export function generateSecureRandom(
  length: number = 32,
  charset: string = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
): string {
  const bytes = crypto.getRandomValues(new Uint8Array(length))
  let result = ''
  for (let i = 0; i < length; i++) {
    result += charset[bytes[i] % charset.length]
  }
  return result
}

/**
 * 生成安全随机数字验证码
 * @param digits - 验证码位数，默认 6
 */
export function generateSecureCode(digits: number = 6): string {
  const bytes = crypto.getRandomValues(new Uint8Array(digits))
  let code = ''
  for (let i = 0; i < digits; i++) {
    code += (bytes[i] % 10).toString()
  }
  return code
}

// ============================================================
// HMAC 签名
// ============================================================

/**
 * 使用 HMAC-SHA256 对数据进行签名
 * 用于请求防篡改和反重放攻击
 */
export async function hmacSign(
  data: string,
  secret: string
): Promise<string> {
  const key = await crypto.subtle.importKey(
    'raw',
    str2buf(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  )

  const signature = await crypto.subtle.sign('HMAC', key, str2buf(data))
  return buf2hex(signature)
}

/**
 * 验证 HMAC 签名
 */
export async function hmacVerify(
  data: string,
  signature: string,
  secret: string
): Promise<boolean> {
  const expected = await hmacSign(data, secret)
  return expected === signature
}

// ============================================================
// Token 加密存储
// ============================================================

/**
 * 加密 token 后存储到 localStorage
 * 使用设备指纹派生的密钥加密
 */
export async function encryptToken(token: string, fingerprint: string): Promise<string> {
  // 从指纹派生加密密钥
  const keyDerived = await crypto.subtle.digest('SHA-256', str2buf(fingerprint))
  const keyHex = buf2hex(keyDerived)
  return aesEncrypt(token, keyHex)
}

/**
 * 解密从 localStorage 读取的加密 token
 */
export async function decryptToken(
  encryptedToken: string,
  fingerprint: string
): Promise<string> {
  const keyDerived = await crypto.subtle.digest('SHA-256', str2buf(fingerprint))
  const keyHex = buf2hex(keyDerived)
  return aesDecrypt(encryptedToken, keyHex)
}