// ============================================================
// protocol/IMCryptoCodec.ts — IM 加密/解密编解码器
// ============================================================
// 企业级双层加密架构的浏览器端实现，与后端 IMCryptoCodec 对齐。
//
// 加密架构：
//   Layer 1（外层）: AES-256-GCM
//     - 密钥: Session Key（ECDH 协商得到）
//     - 加密对象: IMEnvelope 整个 protobuf 消息
//     - IV: 随机生成 12 字节，前置于密文
//     - Auth Tag: 16 字节，由 GCM 自动生成并附在密文末尾
//
//   Layer 2（内层）: AES-256-GCM
//     - 密钥: Derived Key = HKDF(sessionKey, messageType)
//     - 加密对象: 类型特定的 Payload（TextPayload/ImagePayload...）
//
// 安全特性：
//   - AEAD 认证加密，同时保证机密性与完整性
//   - 每次加密使用随机 IV，防止模式重用攻击
//   - GCM 认证标签防止密文篡改
//   - 双层加密隔离：外层泄露不影响内层 payload 安全
//
// Web Crypto API 支持：
//   - AES-GCM：原生支持，硬件加速
//   - ECDH（P-256）：原生支持
//   - HKDF：原生支持
//   - HMAC：原生支持
//   - RSA-OAEP / RSA-PSS：原生支持
// ============================================================

// ============================================================
// 常量
// ============================================================

/** AES-GCM IV 长度（推荐 12 字节） */
export const GCM_IV_LENGTH = 12

/** GCM 认证标签长度（位） */
export const GCM_TAG_LENGTH_BITS = 128

/** AES 密钥长度（位） */
export const AES_KEY_LENGTH_BITS = 256

/** AES-GCM 算法名称 */
const AES_GCM_ALGORITHM = 'AES-GCM'

/** ECDH 曲线名称 */
const ECDH_CURVE = 'P-256'

/** HKDF 信息字符串常量（与后端对齐） */
const HKDF_INFO_LAYER2_KEY = new TextEncoder().encode('pcd-im-layer2-key')

// ============================================================
// 类型定义
// ============================================================

/**
 * 加密结果
 *
 * <p>包含 IV、密文、以及组合后的字节（IV + 密文 + Auth Tag）。
 * 与后端 IMCryptoCodec.EncryptionResult 对齐。</p>
 */
export interface EncryptionResult {
  /** 初始化向量（12 字节） */
  iv: Uint8Array
  /** 密文（含 GCM Auth Tag） */
  ciphertext: Uint8Array
  /** 组合后的字节（IV + 密文） */
  combined: Uint8Array
}

/**
 * 会话密钥集合
 *
 * <p>ECDH 协商得到的会话密钥及其派生密钥的集合。</p>
 */
export interface SessionKeySet {
  /** 会话密钥（用于 Layer 1 加密） */
  sessionKey: CryptoKey
  /** 原始会话密钥字节（用于派生其他密钥） */
  sessionKeyBytes: Uint8Array
  /** HMAC 签名密钥（用于帧签名） */
  hmacKey: CryptoKey
  /** 密钥 ID */
  keyId: number
  /** 过期时间（Unix 毫秒） */
  expireAt: number
}

// ============================================================
// 工具函数
// ============================================================

/**
 * 生成密码学安全的随机字节
 *
 * @param length 字节长度
 * @returns Uint8Array 随机字节
 */
export function generateRandomBytes(length: number): Uint8Array {
  const bytes = new Uint8Array(length)
  crypto.getRandomValues(bytes)
  return bytes
}

/**
 * 生成随机 IV（12 字节）
 */
/**
 * 将 Uint8Array 转换为 ArrayBuffer
 *
 * <p>TypeScript 5.7+ 中 Uint8Array<ArrayBufferLike> 不兼容 BufferSource，
 * 需要显式转换为 ArrayBuffer 以满足 Web Crypto API 的类型要求。
 * 此函数确保 Uint8Array 由常规 ArrayBuffer（非 SharedArrayBuffer）支持。</p>
 *
 * @param bytes Uint8Array 输入
 * @returns 由常规 ArrayBuffer 支持的 ArrayBuffer 视图
 */
function toAB(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer
}

export function generateIV(): Uint8Array {
  return generateRandomBytes(GCM_IV_LENGTH)
}

/**
 * Base64 编码（URL-safe，无填充）
 *
 * <p>用于密钥交换阶段的公钥/签名传输。
 * 与后端 Base64.getUrlEncoder().withoutPadding() 对齐。</p>
 */
export function base64UrlEncode(bytes: Uint8Array): string {
  // 使用浏览器原生 btoa + 字符串转换
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  const base64 = btoa(binary)
  // 转为 URL-safe 并去除 padding
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/**
 * Base64 解码（URL-safe，无 padding）
 */
export function base64UrlDecode(str: string): Uint8Array {
  // 还原标准 Base64
  let base64 = str.replace(/-/g, '+').replace(/_/g, '/')
  // 补齐 padding
  const pad = base64.length % 4
  if (pad) {
    base64 += '='.repeat(4 - pad)
  }
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

/**
 * 将 Uint8Array 拼接为单个 Uint8Array
 */
export function concatBytes(...arrays: Uint8Array[]): Uint8Array {
  let totalLength = 0
  for (const arr of arrays) totalLength += arr.length
  const result = new Uint8Array(totalLength)
  let offset = 0
  for (const arr of arrays) {
    result.set(arr, offset)
    offset += arr.length
  }
  return result
}

/**
 * 比较两个 Uint8Array 是否相等（常数时间比较，防时序攻击）
 */
export function constantTimeEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false
  let result = 0
  for (let i = 0; i < a.length; i++) {
    result |= a[i] ^ b[i]
  }
  return result === 0
}

// ============================================================
// AES-GCM 加密/解密
// ============================================================

/**
 * AES-256-GCM 加密
 *
 * <p>流程：
 * 1. 生成随机 12 字节 IV
 * 2. 使用 Web Crypto API 的 subtle.encrypt 进行 AEAD 加密
 * 3. 返回 IV + 密文（含 Auth Tag）的组合字节</p>
 *
 * @param plaintext 明文数据
 * @param key AES-256 CryptoKey
 * @returns 加密结果（含 IV、密文、组合字节）
 */
export async function encryptAesGcm(
  plaintext: Uint8Array,
  key: CryptoKey,
): Promise<EncryptionResult> {
  const iv = generateIV()
  // 注意：Uint8Array<ArrayBufferLike> 不兼容 BufferSource，需转换为 ArrayBuffer
  const plaintextBuffer = plaintext.buffer.slice(plaintext.byteOffset, plaintext.byteOffset + plaintext.byteLength) as ArrayBuffer
  const ciphertext = await crypto.subtle.encrypt(
    {
      name: AES_GCM_ALGORITHM,
      iv: toAB(iv),
      tagLength: GCM_TAG_LENGTH_BITS,
    },
    key,
    plaintextBuffer,
  )

  const ciphertextBytes = new Uint8Array(ciphertext)
  const combined = concatBytes(iv, ciphertextBytes)

  return { iv, ciphertext: ciphertextBytes, combined }
}

/**
 * AES-256-GCM 解密
 *
 * <p>输入为 IV + 密文（含 Auth Tag）的组合字节。
 * 如果认证标签验证失败（密文被篡改），将抛出异常。</p>
 *
 * @param combined IV + 密文（含 Auth Tag）
 * @param key AES-256 CryptoKey
 * @returns 解密后的明文
 * @throws Error 如果认证标签验证失败
 */
export async function decryptAesGcm(
  combined: Uint8Array,
  key: CryptoKey,
): Promise<Uint8Array> {
  if (combined.length < GCM_IV_LENGTH) {
    throw new Error('AES-GCM 解密失败: 数据长度不足（缺少 IV）')
  }

  const iv = combined.slice(0, GCM_IV_LENGTH)
  const ciphertext = combined.slice(GCM_IV_LENGTH)
  // 注意：Uint8Array<ArrayBufferLike> 不兼容 BufferSource，需转换为 ArrayBuffer
  const ciphertextBuffer = ciphertext.buffer.slice(ciphertext.byteOffset, ciphertext.byteOffset + ciphertext.byteLength) as ArrayBuffer

  try {
    const plaintext = await crypto.subtle.decrypt(
      {
        name: AES_GCM_ALGORITHM,
        iv: toAB(iv),
        tagLength: GCM_TAG_LENGTH_BITS,
      },
      key,
      ciphertextBuffer,
    )
    return new Uint8Array(plaintext)
  } catch {
    throw new Error('AES-GCM 解密失败: 认证标签不匹配（密文可能被篡改）')
  }
}

// ============================================================
// Layer 1 / Layer 2 加密封装
// ============================================================

/**
 * Layer 1 加密：使用 Session Key 加密 IMEnvelope
 *
 * @param envelopeBytes IMEnvelope 序列化字节
 * @param sessionKey 会话密钥
 * @returns 加密结果
 */
export async function encryptLayer1(
  envelopeBytes: Uint8Array,
  sessionKey: CryptoKey,
): Promise<EncryptionResult> {
  return encryptAesGcm(envelopeBytes, sessionKey)
}

/**
 * Layer 1 解密：使用 Session Key 解密 IMEnvelope
 *
 * @param encryptedData 加密数据（IV + 密文）
 * @param sessionKey 会话密钥
 * @returns IMEnvelope 序列化字节
 */
export async function decryptLayer1(
  encryptedData: Uint8Array,
  sessionKey: CryptoKey,
): Promise<Uint8Array> {
  return decryptAesGcm(encryptedData, sessionKey)
}

/**
 * Layer 2 加密：使用派生密钥加密类型特定 Payload
 *
 * <p>派生密钥通过 HKDF(sessionKey, "pcd-im-layer2-key") 生成。</p>
 *
 * @param payloadBytes Payload 序列化字节
 * @param derivedKey 派生密钥
 * @returns 加密结果
 */
export async function encryptLayer2(
  payloadBytes: Uint8Array,
  derivedKey: CryptoKey,
): Promise<EncryptionResult> {
  return encryptAesGcm(payloadBytes, derivedKey)
}

/**
 * Layer 2 解密：使用派生密钥解密类型特定 Payload
 */
export async function decryptLayer2(
  encryptedData: Uint8Array,
  derivedKey: CryptoKey,
): Promise<Uint8Array> {
  return decryptAesGcm(encryptedData, derivedKey)
}

// ============================================================
// CryptoKey 辅助函数
// ============================================================

/**
 * 从原始字节导入 AES-256 CryptoKey
 *
 * @param keyBytes 32 字节密钥
 * @param usages 密钥用途，默认为 ['encrypt', 'decrypt']
 * @returns AES-256 CryptoKey
 */
export async function importAesKey(
  keyBytes: Uint8Array,
  usages: KeyUsage[] = ['encrypt', 'decrypt'],
): Promise<CryptoKey> {
  if (keyBytes.length !== 32) {
    throw new Error(`AES-256 密钥长度必须为 32 字节，实际为 ${keyBytes.length}`)
  }
  // 注意：Uint8Array<ArrayBufferLike> 不兼容 BufferSource，需转换为 ArrayBuffer
  const keyBuffer = keyBytes.buffer.slice(keyBytes.byteOffset, keyBytes.byteOffset + keyBytes.byteLength) as ArrayBuffer
  return crypto.subtle.importKey('raw', keyBuffer, { name: AES_GCM_ALGORITHM }, false, usages)
}

/**
 * 从原始字节导入 HMAC CryptoKey
 *
 * @param keyBytes 密钥字节
 * @returns HMAC CryptoKey
 */
export async function importHmacKey(keyBytes: Uint8Array): Promise<CryptoKey> {
  // 注意：Uint8Array<ArrayBufferLike> 不兼容 BufferSource，需转换为 ArrayBuffer
  const keyBuffer = keyBytes.buffer.slice(keyBytes.byteOffset, keyBytes.byteOffset + keyBytes.byteLength) as ArrayBuffer
  return crypto.subtle.importKey(
    'raw',
    keyBuffer,
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign', 'verify'],
  )
}

/**
 * 通过 HKDF 从 ECDH 共享密钥派生会话密钥字节
 *
 * <p>与后端 IMSessionKeys.negotiate 对齐：
 * sessionKeyBytes = HKDF-SHA256(ikm=sharedSecret, info="pcd-im-v2-session-key", len=32)</p>
 *
 * @param sharedSecret ECDH 共享密钥（32 字节）
 * @returns 会话密钥原始字节（32 字节）
 */
export async function deriveSessionKeyBytes(sharedSecret: Uint8Array): Promise<Uint8Array> {
  const baseKey = await crypto.subtle.importKey(
    'raw',
    toAB(sharedSecret),
    'HKDF',
    false,
    ['deriveBits'],
  )

  const derivedBytes = await crypto.subtle.deriveBits(
    {
      name: 'HKDF',
      hash: 'SHA-256',
      salt: new Uint8Array(0),
      info: new TextEncoder().encode('pcd-im-v2-session-key'),
    },
    baseKey,
    256, // 32 字节
  )

  return new Uint8Array(derivedBytes)
}

/**
 * 从会话密钥派生 HMAC 签名密钥
 *
 * <p>与后端 IMAntiForgeryValidator.deriveHmacKey 对齐：
 * hmacKey = SHA-256(sessionKeyBytes || "pcd-im-hmac-key")</p>
 *
 * @param sessionKeyBytes 会话密钥原始字节（HKDF 派生后的 32 字节）
 * @returns HMAC CryptoKey
 */
export async function deriveHmacKey(sessionKeyBytes: Uint8Array): Promise<CryptoKey> {
  // SHA-256(sessionKeyBytes || "pcd-im-hmac-key")
  const infoBytes = new TextEncoder().encode('pcd-im-hmac-key')
  const combined = concatBytes(sessionKeyBytes, infoBytes)
  const hash = await crypto.subtle.digest('SHA-256', toAB(combined))
  return importHmacKey(new Uint8Array(hash))
}

/**
 * 通过 HKDF 从 Session Key 派生 Layer 2 加密密钥（按消息类型）
 *
 * <p>与后端 IMSessionKeys.deriveKeyForType 对齐：
 * layer2Key = HKDF(sessionKey, "pcd-im-v2-derived:" + typeName, 32)</p>
 *
 * @param sessionKeyBytes 会话密钥原始字节（HKDF 派生后的 32 字节）
 * @param typeName PayloadCodec 类型名（如 "TEXT", "IMAGE", "FILE"）
 * @returns AES-256 CryptoKey
 */
export async function deriveLayer2KeyForType(
  sessionKeyBytes: Uint8Array,
  typeName: string,
): Promise<CryptoKey> {
  const baseKey = await crypto.subtle.importKey(
    'raw',
    // 注意：Uint8Array<ArrayBufferLike> 不兼容 BufferSource，需转换为 ArrayBuffer
    toAB(sessionKeyBytes),
    'HKDF',
    false,
    ['deriveBits'],
  )

  const derivedBytes = await crypto.subtle.deriveBits(
    {
      name: 'HKDF',
      hash: 'SHA-256',
      salt: new Uint8Array(0),
      info: new TextEncoder().encode(`pcd-im-v2-derived:${typeName}`),
    },
    baseKey,
    256,
  )

  return importAesKey(new Uint8Array(derivedBytes))
}

/**
 * 消息类型编号 → Codec TypeName 映射
 *
 * <p>与后端 MessageTypeDispatcher.PayloadCodec.typeName 对齐，
 * 用于 Layer 2 密钥派生。</p>
 */
export const CODEC_TYPE_NAME_MAP: Record<number, string> = {
  1: 'TEXT',       // TEXT
  2: 'IMAGE',      // IMAGE
  3: 'FILE',       // FILE
  4: 'VOICE',      // VOICE
  5: 'VIDEO',      // VIDEO
  10: 'STICKER',   // STICKER
  11: 'LOCATION',  // LOCATION
  12: 'REPLY',     // REPLY
  // AUDIT FIX [IM-PROTO-20260810]：后端 MessageTypeDispatcher 为通话类型注册
  // 独立 PayloadCodec。原映射遗漏 13/14，导致通话 Layer 2 密钥无法派生。
  13: 'VOICE_CALL',   // VOICE_CALL
  14: 'VIDEO_CALL',   // VIDEO_CALL
  50: 'SYSTEM_NOTICE', // SYSTEM_NOTICE
  51: 'READ_RECEIPT',  // READ_RECEIPT
  52: 'TYPING',    // MSG_TYPING
  91: 'ACK',       // ACK
  93: 'RECEIPT',   // RECEIPT
  100: 'CUSTOM',   // CUSTOM
}

// ============================================================
// ECDH 密钥协商
// ============================================================

/**
 * 生成 ECDH P-256 密钥对
 *
 * <p>用于客户端密钥交换：将公钥发送给服务端，
 * 服务端返回其公钥后，使用私钥 + 服务端公钥计算共享密钥。</p>
 *
 * @returns ECDH 密钥对（含 CryptoKey 与导出的 X.509 公钥字节）
 */
export async function generateEcdhKeyPair(): Promise<{
  privateKey: CryptoKey
  publicKey: CryptoKey
  publicKeyBytes: Uint8Array
}> {
  const keyPair = await crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: ECDH_CURVE },
    true,
    ['deriveBits'],
  )

  // 导出 X.509 公钥字节（与后端 X509EncodedKeySpec 对齐）
  const publicKeyBuf = await crypto.subtle.exportKey('spki', keyPair.publicKey)
  return {
    privateKey: keyPair.privateKey,
    publicKey: keyPair.publicKey,
    publicKeyBytes: new Uint8Array(publicKeyBuf),
  }
}

/**
 * 从 X.509 编码字节导入 ECDH 公钥
 *
 * @param publicKeyBytes X.509 编码的公钥字节
 * @returns ECDH 公钥 CryptoKey
 */
export async function importEcdhPublicKey(publicKeyBytes: Uint8Array): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    'spki',
    // 注意：Uint8Array<ArrayBufferLike> 不兼容 BufferSource，需转换为 ArrayBuffer
    toAB(publicKeyBytes),
    { name: 'ECDH', namedCurve: ECDH_CURVE },
    false,
    [],
  )
}

/**
 * ECDH 共享密钥计算
 *
 * <p>使用本地私钥 + 对端公钥计算 32 字节共享密钥，
 * 作为 AES-256 Session Key 使用。</p>
 *
 * @param privateKey 本地 ECDH 私钥
 * @param publicKey 对端 ECDH 公钥
 * @returns 32 字节共享密钥
 */
export async function computeEcdhSharedSecret(
  privateKey: CryptoKey,
  publicKey: CryptoKey,
): Promise<Uint8Array> {
  const sharedBits = await crypto.subtle.deriveBits(
    { name: 'ECDH', public: publicKey },
    privateKey,
    256, // P-256 曲线 = 32 字节
  )
  return new Uint8Array(sharedBits)
}

// ============================================================
// RSA 签名验证（用于密钥交换响应的服务端签名验证）
// ============================================================

/**
 * 从 X.509 / SPKI 编码字节导入 RSA 公钥
 *
 * <p>用于验证服务端在 KeyExchangeResponse 中的签名。
 * 后端使用 SHA256withRSA 算法签名。</p>
 *
 * @param publicKeyBytes RSA 公钥字节
 * @returns RSA-PSS 公钥 CryptoKey
 */
export async function importRsaPublicKey(publicKeyBytes: Uint8Array): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    'spki',
    // 注意：Uint8Array<ArrayBufferLike> 不兼容 BufferSource，需转换为 ArrayBuffer
    toAB(publicKeyBytes),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['verify'],
  )
}

/**
 * 验证 RSA-SHA256 签名
 *
 * @param data 原始数据
 * @param signature 签名字节
 * @param publicKey RSA 公钥
 * @returns 签名是否有效
 */
export async function verifyRsaSignature(
  data: Uint8Array,
  signature: Uint8Array,
  publicKey: CryptoKey,
): Promise<boolean> {
  return crypto.subtle.verify(
    'RSASSA-PKCS1-v1_5',
    publicKey,
    // 注意：Uint8Array<ArrayBufferLike> 不兼容 BufferSource，需转换为 ArrayBuffer
    toAB(signature),
    toAB(data),
  )
}
