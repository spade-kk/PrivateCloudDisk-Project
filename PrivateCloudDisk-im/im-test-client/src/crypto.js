// ============================================================
// crypto.js — 加密与密钥协商模块
// ============================================================
// 使用 Node.js 原生 crypto 模块实现：
//   - ECDH P-256 密钥对生成与共享密钥协商
//   - AES-256-GCM 加密/解密（Layer 1 信封加密）
//   - HMAC-SHA256 签名/验证（帧完整性校验）
//   - HKDF 密钥派生（HMAC 密钥、Layer 2 密钥、会话密钥）
//
// 与前端 IMCryptoCodec.ts 和后端 IMCryptoCodec.java 完全对齐。
//
// 公钥格式说明：
//   - 网络传输：SPKI (X.509 SubjectPublicKeyInfo) DER 格式，与 Java X509EncodedKeySpec 对齐
//   - 本地 ECDH 运算：原始 EC 点（uncompressed, 0x04||x||y）
//   - 本模块自动完成 raw ↔ SPKI 转换，调用方无需关心
// ============================================================

import crypto from 'node:crypto'

// ==================== 常量 ====================

/** ECDH 曲线名称（与前端/后端一致：P-256 = prime256v1） */
const ECDH_CURVE = 'prime256v1'

/** AES-256-GCM IV 长度（12 字节） */
const GCM_IV_SIZE = 12

/** AES-256-GCM 认证标签长度（16 字节） */
const GCM_TAG_SIZE = 16

/** HMAC-SHA256 输出长度（32 字节） */
const HMAC_SIZE = 32

/** HKDF 信息字符串（与后端 IMSessionKeys / IMAntiForgeryValidator 对齐） */
const HKDF_INFO_SESSION_KEY = 'pcd-im-v2-session-key'
const HKDF_INFO_HMAC = 'pcd-im-hmac-key'
const HKDF_INFO_LAYER2 = 'pcd-im-layer2-key'

// ==================== Base64 URL 编码/解码 ====================

/**
 * Base64 URL 编码（无 padding）
 * @param {Buffer|Uint8Array} bytes - 输入字节
 * @returns {string} Base64 URL 字符串
 */
export function base64UrlEncode(bytes) {
  return Buffer.from(bytes)
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}

/**
 * Base64 URL 解码
 * @param {string} str - Base64 URL 字符串
 * @returns {Buffer} 解码后的字节
 */
export function base64UrlDecode(str) {
  const base64 = str.replace(/-/g, '+').replace(/_/g, '/')
  const padding = '='.repeat((4 - (base64.length % 4)) % 4)
  return Buffer.from(base64 + padding, 'base64')
}

// ==================== SPKI ↔ Raw 公钥格式转换 ====================

/**
 * 将原始 EC 公钥（uncompressed, 0x04||x||y）转换为 SPKI DER 格式
 *
 * <p>后端 Java 使用 X509EncodedKeySpec 解析公钥，需要 SPKI 格式。
 * Node.js ECDH.getPublicKey() 返回原始 EC 点，需通过 JWK 中转转换为 SPKI。</p>
 *
 * @param {Buffer} rawKey - 原始 EC 公钥（65 字节，0x04||x(32B)||y(32B)）
 * @returns {Buffer} SPKI DER 编码的公钥
 */
function rawPublicKeyToSpki(rawKey) {
  // 通过 JWK 格式创建 KeyObject，再导出为 SPKI DER
  const keyObject = crypto.createPublicKey({
    key: {
      kty: 'EC',
      crv: 'P-256',
      x: rawKey.subarray(1, 33).toString('base64url'),
      y: rawKey.subarray(33, 65).toString('base64url'),
    },
    format: 'jwk',
  })
  return keyObject.export({ type: 'spki', format: 'der' })
}

/**
 * 将 SPKI DER 格式公钥转换为原始 EC 点（uncompressed, 0x04||x||y）
 *
 * <p>服务端发送的公钥为 SPKI 格式，但 Node.js ECDH.computeSecret() 需要原始 EC 点。
 * 通过 JWK 中转提取 x/y 坐标，重新组装为 uncompressed 格式。</p>
 *
 * @param {Buffer} spkiKey - SPKI DER 编码的公钥
 * @returns {Buffer} 原始 EC 公钥（65 字节）
 */
function spkiPublicKeyToRaw(spkiKey) {
  const keyObject = crypto.createPublicKey({
    key: spkiKey,
    format: 'der',
    type: 'spki',
  })
  const jwk = keyObject.export({ format: 'jwk' })
  const x = Buffer.from(jwk.x, 'base64url')
  const y = Buffer.from(jwk.y, 'base64url')
  // 组装 uncompressed 格式：0x04 || x || y
  return Buffer.concat([Buffer.from([0x04]), x, y])
}

// ==================== ECDH 密钥协商 ====================

/**
 * 生成 ECDH P-256 密钥对
 *
 * <p>返回的公钥已转换为 SPKI 格式（与后端 X509EncodedKeySpec 对齐），
 * 可直接 Base64 编码后发送给服务端。</p>
 *
 * @returns {{publicKey: Buffer, privateKey: Buffer, ecdh: crypto.ECDH}} 密钥对
 *   - publicKey: SPKI DER 格式（用于网络传输）
 *   - privateKey: 原始私钥字节（用于 ECDH 计算）
 */
export function generateEcdhKeyPair() {
  const ecdh = crypto.createECDH(ECDH_CURVE)
  ecdh.generateKeys()

  const rawPublicKey = ecdh.getPublicKey()
  const spkiPublicKey = rawPublicKeyToSpki(rawPublicKey)

  return {
    publicKey: spkiPublicKey, // SPKI 格式，可直接发送给服务端
    privateKey: ecdh.getPrivateKey(),
    ecdh,
  }
}

/**
 * 计算 ECDH 共享密钥
 *
 * <p>服务端公钥为 SPKI 格式，内部自动转换为原始 EC 点后再进行 ECDH 计算。</p>
 *
 * @param {Buffer} clientPrivateKey - 客户端私钥（原始字节）
 * @param {Buffer|string} serverPublicKey - 服务端公钥（SPKI DER 格式，Buffer 或 Base64 URL 字符串）
 * @returns {Buffer} 共享密钥（32 字节）
 */
export function computeSharedSecret(clientPrivateKey, serverPublicKey) {
  const ecdh = crypto.createECDH(ECDH_CURVE)
  ecdh.setPrivateKey(clientPrivateKey)

  const serverPubKeySpki = Buffer.isBuffer(serverPublicKey)
    ? serverPublicKey
    : base64UrlDecode(serverPublicKey)

  // 将 SPKI 格式转换为原始 EC 点
  const rawServerKey = spkiPublicKeyToRaw(serverPubKeySpki)

  return ecdh.computeSecret(rawServerKey)
}

// ==================== HKDF 密钥派生 ====================

/**
 * HKDF-SHA256 密钥派生
 * @param {Buffer} ikm - 输入密钥材料（共享密钥）
 * @param {string} info - 上下文信息字符串
 * @param {number} length - 输出密钥长度（字节）
 * @returns {Buffer} 派生密钥
 */
function hkdf(ikm, info, length = 32) {
  const hkdfKey = crypto.hkdfSync('sha256', ikm, Buffer.alloc(0), info, length)
  return Buffer.from(hkdfKey)
}

/**
 * 从共享密钥派生会话密钥（AES-256，用于 Layer 1 加密）
 *
 * <p>与后端 IMSessionKeys.negotiate 中的 HKDF 派生对齐：
 * sessionKey = HKDF-SHA256(ikm=sharedSecret, info="pcd-im-v2-session-key", len=32)</p>
 *
 * @param {Buffer} sharedSecret - ECDH 共享密钥
 * @returns {Buffer} 会话密钥（32 字节）
 */
export function deriveSessionKey(sharedSecret) {
  return hkdf(sharedSecret, HKDF_INFO_SESSION_KEY, 32)
}

/**
 * 从会话密钥派生 HMAC 签名密钥
 *
 * <p>与后端 IMAntiForgeryValidator.deriveHmacKey 对齐：
 * hmacKey = SHA-256(sessionKey || "pcd-im-hmac-key")[0:32]</p>
 *
 * @param {Buffer} sessionKey - 会话密钥（HKDF 派生后的 32 字节密钥）
 * @returns {Buffer} HMAC 密钥（32 字节）
 */
export function deriveHmacKey(sessionKey) {
  const hash = crypto.createHash('sha256')
  hash.update(sessionKey)
  hash.update(Buffer.from('pcd-im-hmac-key'))
  return hash.digest() // SHA-256 输出正好 32 字节
}

/**
 * 从会话密钥派生 Layer 2 加密密钥
 *
 * <p>与后端 IMSessionKeys.deriveKeyForType 对齐：
 * layer2Key = HKDF(sessionKey, "pcd-im-v2-derived:" + typeName, 32)
 * 其中 typeName 为 PayloadCodec 类型名（如 "TEXT", "IMAGE", "FILE" 等）</p>
 *
 * @param {Buffer} sessionKey - 会话密钥（HKDF 派生后的 32 字节密钥）
 * @param {string} typeName - PayloadCodec 类型名（如 "TEXT", "IMAGE"）
 * @returns {Buffer} Layer 2 AES 密钥（32 字节）
 */
export function deriveLayer2Key(sessionKey, typeName) {
  return hkdf(sessionKey, `pcd-im-v2-derived:${typeName}`, 32)
}

// ==================== AES-256-GCM 加密/解密 ====================

/**
 * AES-256-GCM 加密
 * @param {Buffer|Uint8Array} plaintext - 明文
 * @param {Buffer} key - 256 位密钥
 * @returns {{iv: Buffer, ciphertext: Buffer, tag: Buffer, combined: Buffer}}
 *   combined = iv(12B) + ciphertext + tag(16B)
 */
export function encryptAesGcm(plaintext, key) {
  const iv = crypto.randomBytes(GCM_IV_SIZE)
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv)
  const encrypted = Buffer.concat([
    cipher.update(Buffer.from(plaintext)),
    cipher.final(),
  ])
  const tag = cipher.getAuthTag()
  // 组合格式：IV(12B) + 密文 + AuthTag(16B)
  const combined = Buffer.concat([iv, encrypted, tag])
  return { iv, ciphertext: encrypted, tag, combined }
}

/**
 * AES-256-GCM 解密
 * @param {Buffer|Uint8Array} combinedData - 组合格式数据（IV + 密文 + Tag）
 * @param {Buffer} key - 256 位密钥
 * @returns {Buffer} 明文
 */
export function decryptAesGcm(combinedData, key) {
  const data = Buffer.from(combinedData)
  if (data.length < GCM_IV_SIZE + GCM_TAG_SIZE) {
    throw new Error(`加密数据过短: ${data.length} 字节`)
  }

  const iv = data.subarray(0, GCM_IV_SIZE)
  const tag = data.subarray(data.length - GCM_TAG_SIZE)
  const ciphertext = data.subarray(GCM_IV_SIZE, data.length - GCM_TAG_SIZE)

  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv)
  decipher.setAuthTag(tag)
  const plaintext = Buffer.concat([
    decipher.update(ciphertext),
    decipher.final(),
  ])
  return plaintext
}

// ==================== HMAC-SHA256 签名/验证 ====================

/**
 * 计算 HMAC-SHA256 签名
 * @param {Buffer|Uint8Array} data - 待签名数据
 * @param {Buffer} key - HMAC 密钥
 * @returns {Buffer} 签名（32 字节）
 */
export function sign(data, key) {
  return crypto.createHmac('sha256', key).update(Buffer.from(data)).digest()
}

/**
 * 验证 HMAC-SHA256 签名（恒定时间比较）
 * @param {Buffer|Uint8Array} data - 原始数据
 * @param {Buffer|Uint8Array} signature - 待验证的签名
 * @param {Buffer} key - HMAC 密钥
 * @returns {boolean} 验证结果
 */
export function verify(data, signature, key) {
  const expected = sign(data, key)
  return crypto.timingSafeEqual(expected, Buffer.from(signature))
}

// ==================== 会话密钥集合 ====================

/**
 * 会话密钥集合
 * @typedef {object} SessionKeySet
 * @property {Buffer} sessionKey - Layer 1 AES-256 会话密钥
 * @property {Buffer} hmacKey - HMAC-SHA256 签名密钥
 * @property {number} keyId - 会话密钥 ID
 * @property {number} expireAt - 过期时间（Unix 毫秒）
 */

/**
 * 从共享密钥构建会话密钥集合
 *
 * <p>与后端 IMSessionKeys.negotiate 对齐：
 * - sessionKey = HKDF(sharedSecret, "pcd-im-v2-session-key") 而非直接使用共享密钥
 * - hmacKey = HKDF(sharedSecret, "im-v2-hmac-key")</p>
 *
 * @param {Buffer} sharedSecret - ECDH 共享密钥
 * @param {number} keyId - 服务端分配的密钥 ID
 * @param {number} expireAt - 过期时间（Unix 毫秒）
 * @returns {SessionKeySet} 会话密钥集合
 */
export function buildSessionKeys(sharedSecret, keyId, expireAt) {
  const sessionKey = deriveSessionKey(sharedSecret) // HKDF 派生，与后端一致
  return {
    sessionKey: sessionKey,
    hmacKey: deriveHmacKey(sessionKey), // 从 sessionKey 派生，SHA-256(sessionKey || "pcd-im-hmac-key")
    keyId,
    expireAt,
  }
}

// ==================== 工具函数 ====================

/**
 * 十六进制 dump（用于 debug 模式）
 * @param {Buffer|Uint8Array} data - 二进制数据
 * @returns {string} 十六进制格式字符串
 */
export function hexDump(data) {
  const buf = Buffer.from(data)
  const lines = []
  for (let i = 0; i < buf.length; i += 16) {
    const slice = buf.subarray(i, i + 16)
    const hex = Array.from(slice)
      .map((b) => b.toString(16).padStart(2, '0'))
      .join(' ')
    const ascii = Array.from(slice)
      .map((b) => (b >= 32 && b < 127 ? String.fromCharCode(b) : '.'))
      .join('')
    lines.push(
      `${i.toString(16).padStart(8, '0')}  ${hex.padEnd(48, ' ')}  |${ascii}|`,
    )
  }
  return lines.join('\n')
}

export { HMAC_SIZE, GCM_IV_SIZE, GCM_TAG_SIZE }
