/**
 * utils/crypto.js - 企业级密码加密工具
 *
 * 核心能力:
 *   - PBKDF2-SHA256 密码预哈希（60万次迭代）
 *   - HMAC-SHA256 请求签名
 *   - AES-256-GCM 敏感数据加密
 *   - CSPRNG 安全随机数生成
 *
 * 网关安全要求:
 *   密码传输前必须通过 PBKDF2-SHA256（60万次迭代）预哈希
 *   请求签名使用 HMAC-SHA256 算法
 *   Token 加密存储使用 AES-256-GCM（基于指纹派生密钥）
 */

// ─── 常量 ───

const PBKDF2_ITERATIONS = 600000
const PBKDF2_KEY_LENGTH = 256
const PBKDF2_HASH = 'SHA-256'

const SIGNING_SECRET = 'pcd-native-signing-secret-v5' // 与后端 NativeAppVerifier 配置一致

// ─── 工具函数 ───

/**
 * ArrayBuffer → Hex 字符串
 */
function bufferToHex(buffer) {
  return Array.from(new Uint8Array(buffer))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('')
}

/**
 * Hex 字符串 → ArrayBuffer
 */
function hexToBuffer(hex) {
  const bytes = new Uint8Array(hex.length / 2)
  for (let i = 0; i < hex.length; i += 2) {
    bytes[i / 2] = parseInt(hex.substring(i, i + 2), 16)
  }
  return bytes.buffer
}

/**
 * 字符串 → ArrayBuffer (UTF-8)
 */
function strToBuffer(str) {
  return new TextEncoder().encode(str)
}

// ─── PBKDF2 密码预哈希 ───

/**
 * 使用 PBKDF2-SHA256 对密码进行预哈希。
 * 60 万次迭代，生成 256 位密钥，输出 64 位十六进制字符串。
 *
 * @param {string} password - 原始密码
 * @param {string} salt - 盐值（可选，默认使用固定 salt）
 * @returns {Promise<string>} 64 位十六进制预哈希密码
 */
export async function pbkdf2Hash(password, salt) {
  const encoder = new TextEncoder()
  const passwordBuffer = encoder.encode(password)

  // 导入密码作为密钥材料
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    passwordBuffer,
    'PBKDF2',
    false,
    ['deriveBits']
  )

  // 使用固定 salt（生产环境应使用用户唯一 salt）
  const saltBuffer = salt
    ? encoder.encode(salt)
    : encoder.encode('PrivateCloudDisk-PBKDF2-Salt-2024')

  // 执行 PBKDF2
  const derivedBits = await crypto.subtle.deriveBits(
    {
      name: 'PBKDF2',
      salt: saltBuffer,
      iterations: PBKDF2_ITERATIONS,
      hash: PBKDF2_HASH
    },
    keyMaterial,
    PBKDF2_KEY_LENGTH
  )

  return bufferToHex(derivedBits)
}

/**
 * 对密码进行预哈希后传输。
 * 这是登录和注册时应该使用的函数。
 *
 * @param {string} password - 原始密码
 * @returns {Promise<string>} 64 位十六进制预哈希密码
 */
export async function hashPasswordForTransmission(password) {
  return pbkdf2Hash(password)
}

// ─── HMAC-SHA256 签名 ───

/**
 * 生成 HMAC-SHA256 签名。
 *
 * @param {string} data - 待签名数据
 * @param {string} secret - 密钥
 * @returns {Promise<string>} 十六进制签名
 */
export async function hmacSha256(data, secret) {
  const encoder = new TextEncoder()
  const keyData = encoder.encode(secret)

  const key = await crypto.subtle.importKey(
    'raw',
    keyData,
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  )

  const signature = await crypto.subtle.sign(
    'HMAC',
    key,
    encoder.encode(data)
  )

  return bufferToHex(signature)
}

/**
 * 生成请求签名（用于 X-Request-Signature 头）。
 * 签名格式: HTTP_METHOD + "\n" + PATH + "\n" + DEVICE_ID + "\n" + TIMESTAMP + "\n" + NONCE
 *
 * @param {string} method - HTTP 方法
 * @param {string} path - 请求路径
 * @param {string} deviceId - 设备 ID
 * @param {string} timestamp - 时间戳
 * @param {string} nonce - 随机数
 * @returns {Promise<string>} 十六进制签名
 */
export async function generateRequestSignature(method, path, deviceId, timestamp, nonce) {
  const signingString = `${method}\n${path}\n${deviceId}\n${timestamp}\n${nonce}`
  return hmacSha256(signingString, SIGNING_SECRET)
}

// ─── AES-256-GCM 加密 ───

/**
 * 使用 AES-256-GCM 加密数据。
 * 用于 Token 等敏感数据本地存储加密。
 *
 * @param {string} plaintext - 明文
 * @param {string} keyHex - 256 位密钥（64 位十六进制字符串）
 * @returns {Promise<{ciphertext: string, iv: string}>} 密文和 IV
 */
export async function aesEncrypt(plaintext, keyHex) {
  const keyBuffer = hexToBuffer(keyHex)
  const iv = crypto.getRandomValues(new Uint8Array(12)) // 96-bit IV for GCM

  const key = await crypto.subtle.importKey(
    'raw',
    keyBuffer,
    { name: 'AES-GCM' },
    false,
    ['encrypt']
  )

  const encrypted = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    key,
    strToBuffer(plaintext)
  )

  return {
    ciphertext: bufferToHex(encrypted),
    iv: bufferToHex(iv)
  }
}

/**
 * 使用 AES-256-GCM 解密数据。
 *
 * @param {string} ciphertextHex - 密文（十六进制）
 * @param {string} ivHex - IV（十六进制）
 * @param {string} keyHex - 256 位密钥（64 位十六进制字符串）
 * @returns {Promise<string>} 明文
 */
export async function aesDecrypt(ciphertextHex, ivHex, keyHex) {
  const keyBuffer = hexToBuffer(keyHex)
  const ivBuffer = hexToBuffer(ivHex)
  const ciphertextBuffer = hexToBuffer(ciphertextHex)

  const key = await crypto.subtle.importKey(
    'raw',
    keyBuffer,
    { name: 'AES-GCM' },
    false,
    ['decrypt']
  )

  const decrypted = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: ivBuffer },
    key,
    ciphertextBuffer
  )

  return new TextDecoder().decode(decrypted)
}

/**
 * 从设备指纹派生 AES 密钥。
 *
 * @param {string} fingerprint - 设备指纹
 * @returns {Promise<string>} 64 位十六进制密钥
 */
export async function deriveKeyFromFingerprint(fingerprint) {
  const hashBuffer = await crypto.subtle.digest(
    'SHA-256',
    strToBuffer(fingerprint)
  )
  return bufferToHex(hashBuffer)
}

// ─── CSPRNG 随机数 ───

/**
 * 生成 CSPRNG 安全随机数（十六进制）。
 *
 * @param {number} byteLength - 字节长度（默认 16）
 * @returns {string} 十六进制随机数
 */
export function generateNonce(byteLength = 16) {
  const bytes = crypto.getRandomValues(new Uint8Array(byteLength))
  return bufferToHex(bytes)
}

/**
 * 生成 UUID v4（使用 CSPRNG）。
 *
 * @returns {string} UUID v4
 */
export function generateUUID() {
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40 // version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80 // variant
  return [
    bytes.slice(0, 4),
    bytes.slice(4, 6),
    bytes.slice(6, 8),
    bytes.slice(8, 10),
    bytes.slice(10, 16)
  ].map(b => bufferToHex(b)).join('-')
}

// ─── SHA-256 ───

/**
 * SHA-256 哈希。
 *
 * @param {string} data - 待哈希数据
 * @returns {Promise<string>} 十六进制哈希
 */
export async function sha256(data) {
  const hashBuffer = await crypto.subtle.digest('SHA-256', strToBuffer(data))
  return bufferToHex(hashBuffer)
}