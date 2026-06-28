/**
 * crypto.subtle 安全访问守卫 — 编译时常量降级方案
 *
 * 问题：
 *   crypto.subtle 在非安全上下文（如 http://192.168.x.x）中为 undefined，
 *   导致开发/内网测试阶段 Web Crypto API 调用失败。
 *
 * 方案：
 *   使用 import.meta.env.DEV（Vite 编译时常量）在开发环境通过 crypto-js
 *   成熟库提供降级实现。生产构建时动态 import 被 Terser 死代码消除，
 *   降级代码不进入最终包。
 *
 * 为什么 import.meta.env.DEV 是安全的？
 *   - Vite 在构建时将其替换为字面量 true/false，不是运行时变量
 *   - 生产包中 `if (false) { ... }` 分支被 Terser 完全移除
 *   - 攻击者无法在运行时修改编译时常量
 *
 * 使用方式：
 *   import { subtleDigest, subtleImportKey, subtleDeriveBits } from './cryptoSubtleGuard'
 *   直接替换 crypto.subtle.digest / importKey / deriveBits 调用
 */

// ============================================================
// crypto-js 懒加载（仅开发环境，生产构建时被 tree-shaking 移除）
// ============================================================

type CryptoJSModule = typeof import('crypto-js')

let _CryptoJS: CryptoJSModule | null = null

/**
 * 懒加载 crypto-js 模块。
 * import.meta.env.DEV 是编译时常量，生产构建时此函数整体被移除。
 */
async function _getCryptoJS(): Promise<CryptoJSModule> {
  if (import.meta.env.DEV) {
    if (!_CryptoJS) {
      _CryptoJS = await import('crypto-js')
    }
    return _CryptoJS
  }
  throw new Error('crypto-js is not available in production')
}

// ============================================================
// WordArray ↔ ArrayBuffer 转换工具
// ============================================================

/**
 * crypto-js WordArray → ArrayBuffer
 */
function wordArrayToBuffer(wordArray: import('crypto-js').lib.WordArray): ArrayBuffer {
  const words = wordArray.words
  const sigBytes = wordArray.sigBytes
  const u8 = new Uint8Array(sigBytes)
  for (let i = 0; i < sigBytes; i++) {
    u8[i] = (words[i >>> 2] >>> (24 - (i % 4) * 8)) & 0xff
  }
  return u8.buffer
}

/**
 * ArrayBuffer → crypto-js WordArray
 */
function bufferToWordArray(buf: ArrayBuffer): import('crypto-js').lib.WordArray {
  const C = _CryptoJS!
  const u8 = new Uint8Array(buf)
  const words: number[] = []
  for (let i = 0; i < u8.length; i += 4) {
    words.push(
      ((u8[i] || 0) << 24) |
      ((u8[i + 1] || 0) << 16) |
      ((u8[i + 2] || 0) << 8) |
      (u8[i + 3] || 0)
    )
  }
  return C.lib.WordArray.create(words, u8.length)
}

// ============================================================
// 编译时常量守卫
// ============================================================

/**
 * 安全获取 crypto.subtle
 * 生产环境直接返回；开发环境检查可用性，不可用时抛出明确错误提示。
 */
function getSubtle(): SubtleCrypto {
  if (import.meta.env.DEV) {
    if (!globalThis.crypto || !globalThis.crypto.subtle) {
      throw new Error(
        'crypto.subtle 不可用：当前页面运行在非安全上下文（HTTP）。\n' +
        '请使用 HTTPS 访问或通过 localhost 访问。'
      )
    }
  }
  return globalThis.crypto.subtle!
}

// ============================================================
// 公开 API — 安全调用封装
// ============================================================

/**
 * 安全调用 crypto.subtle.digest
 *
 * 生产环境：直接调用原生 crypto.subtle.digest（HTTPS 保证可用）
 * 开发环境：crypto.subtle 不可用时降级为 crypto-js SHA-256
 *
 * 生产构建时整个降级分支被 tree-shaking 移除。
 */
export async function subtleDigest(
  algorithm: AlgorithmIdentifier,
  data: BufferSource,
): Promise<ArrayBuffer> {
  if (import.meta.env.DEV) {
    if (!globalThis.crypto?.subtle) {
      const C = await _getCryptoJS()
      const input = data instanceof ArrayBuffer
        ? bufferToWordArray(data)
        : bufferToWordArray((data as ArrayBufferView).buffer)
      const hash = C.SHA256(input)
      // crypto-js 的 SHA256 返回 WordArray，内部是 32-bit 大端字
      // 直接使用 words 数组构造结果
      const result = new Uint8Array(32)
      for (let i = 0; i < 8; i++) {
        const w = hash.words[i] || 0
        result[i * 4] = (w >>> 24) & 0xff
        result[i * 4 + 1] = (w >>> 16) & 0xff
        result[i * 4 + 2] = (w >>> 8) & 0xff
        result[i * 4 + 3] = w & 0xff
      }
      return result.buffer
    }
  }
  return getSubtle().digest(algorithm, data)
}

/**
 * 安全调用 crypto.subtle.importKey
 *
 * 开发环境降级：返回模拟 CryptoKey，供 subtleDeriveBits 降级使用
 * 生产环境：直接调用原生 API
 */
export async function subtleImportKey(
  format: KeyFormat,
  keyData: BufferSource,
  algorithm: AlgorithmIdentifier | RsaHashedImportParams | EcKeyImportParams | HmacImportParams | AesKeyAlgorithm,
  extractable: boolean,
  keyUsages: KeyUsage[],
): Promise<CryptoKey> {
  if (import.meta.env.DEV) {
    if (!globalThis.crypto?.subtle) {
      return createDevCryptoKey(keyData, algorithm)
    }
  }
  return getSubtle().importKey(format, keyData, algorithm, extractable, keyUsages)
}

/**
 * 安全调用 crypto.subtle.deriveBits
 *
 * 开发环境降级：使用 crypto-js PBKDF2 实现
 * 生产环境：直接调用原生 API
 */
export async function subtleDeriveBits(
  algorithm: AlgorithmIdentifier | EcdhKeyDeriveParams | HkdfParams | Pbkdf2Params,
  baseKey: CryptoKey,
  length: number,
): Promise<ArrayBuffer> {
  if (import.meta.env.DEV) {
    if (!globalThis.crypto?.subtle) {
      return devPbkdf2DeriveBits(algorithm as Pbkdf2Params, baseKey as DevCryptoKey, length)
    }
  }
  return getSubtle().deriveBits(algorithm, baseKey, length)
}

/**
 * 安全调用 crypto.subtle.encrypt
 *
 * 开发环境降级：crypto-js 不支持 AES-GCM，使用 AES-CBC 提供基本加密能力
 * 生产环境：直接调用原生 API
 */
export async function subtleEncrypt(
  algorithm: AlgorithmIdentifier | RsaOaepParams | AesCtrParams | AesCbcParams | AesGcmParams,
  key: CryptoKey,
  data: BufferSource,
): Promise<ArrayBuffer> {
  if (import.meta.env.DEV) {
    if (!globalThis.crypto?.subtle) {
      const C = await _getCryptoJS()
      const alg = algorithm as AesGcmParams
      const plaintext = data instanceof ArrayBuffer
        ? new Uint8Array(data)
        : new Uint8Array((data as ArrayBufferView).buffer)

      const keyData = (key as unknown as DevCryptoKey)._keyData
      const keyHex = C.enc.Hex.stringify(C.lib.WordArray.create(keyData))
      const ivHex = C.enc.Hex.stringify(C.lib.WordArray.create(alg.iv as Uint8Array))

      const encrypted = C.AES.encrypt(
        C.lib.WordArray.create(plaintext),
        C.enc.Hex.parse(keyHex),
        { iv: C.enc.Hex.parse(ivHex), mode: C.mode.CBC, padding: C.pad.Pkcs7 },
      )

      const ciphertext = C.enc.Base64.stringify(encrypted.ciphertext)
      const decoded = Uint8Array.from(atob(ciphertext), (c) => c.charCodeAt(0))
      return decoded.buffer
    }
  }
  return getSubtle().encrypt(algorithm, key, data)
}

/**
 * 安全调用 crypto.subtle.decrypt
 */
export async function subtleDecrypt(
  algorithm: AlgorithmIdentifier | RsaOaepParams | AesCtrParams | AesCbcParams | AesGcmParams,
  key: CryptoKey,
  data: BufferSource,
): Promise<ArrayBuffer> {
  if (import.meta.env.DEV) {
    if (!globalThis.crypto?.subtle) {
      const C = await _getCryptoJS()
      const alg = algorithm as AesGcmParams
      const cipherData = data instanceof ArrayBuffer
        ? new Uint8Array(data)
        : new Uint8Array((data as ArrayBufferView).buffer)

      const keyData = (key as unknown as DevCryptoKey)._keyData
      const keyHex = C.enc.Hex.stringify(C.lib.WordArray.create(keyData))
      const ivHex = C.enc.Hex.stringify(C.lib.WordArray.create(alg.iv as Uint8Array))

      const cipherB64 = btoa(String.fromCharCode(...cipherData))
      const cipherParams = C.lib.CipherParams.create({
        ciphertext: C.enc.Base64.parse(cipherB64),
      })

      const decrypted = C.AES.decrypt(cipherParams, C.enc.Hex.parse(keyHex), {
        iv: C.enc.Hex.parse(ivHex),
        mode: C.mode.CBC,
        padding: C.pad.Pkcs7,
      })

      const result = wordArrayToBuffer(decrypted)
      return result
    }
  }
  return getSubtle().decrypt(algorithm, key, data)
}

/**
 * 安全调用 crypto.subtle.sign
 *
 * 开发环境降级：使用 crypto-js HMAC-SHA256
 * 生产环境：直接调用原生 API
 */
export async function subtleSign(
  algorithm: AlgorithmIdentifier | RsaPssParams | EcdsaParams,
  key: CryptoKey,
  data: BufferSource,
): Promise<ArrayBuffer> {
  if (import.meta.env.DEV) {
    if (!globalThis.crypto?.subtle) {
      const C = await _getCryptoJS()
      const message = data instanceof ArrayBuffer
        ? new Uint8Array(data)
        : new Uint8Array((data as ArrayBufferView).buffer)
      const keyBytes = (key as unknown as DevCryptoKey)._keyData

      const hmac = C.HmacSHA256(
        C.lib.WordArray.create(message),
        C.lib.WordArray.create(keyBytes),
      )

      return wordArrayToBuffer(hmac)
    }
  }
  return getSubtle().sign(algorithm, key, data)
}

// ============================================================
// 开发环境降级：模拟 CryptoKey + crypto-js PBKDF2
// ============================================================
// 仅在 import.meta.env.DEV 且 crypto.subtle 不可用时使用
// 生产构建时以下代码被 Terser 完全移除

interface DevCryptoKey {
  _dev: true
  _keyData: Uint8Array
  _algorithm: AlgorithmIdentifier | RsaHashedImportParams | EcKeyImportParams | HmacImportParams | AesKeyAlgorithm
}

function createDevCryptoKey(
  keyData: BufferSource,
  algorithm: AlgorithmIdentifier | RsaHashedImportParams | EcKeyImportParams | HmacImportParams | AesKeyAlgorithm,
): DevCryptoKey {
  return {
    _dev: true,
    _keyData: keyData instanceof Uint8Array ? keyData : new Uint8Array(keyData as ArrayBuffer),
    _algorithm: algorithm,
  }
}

/**
 * crypto-js PBKDF2 降级实现
 */
async function devPbkdf2DeriveBits(
  params: Pbkdf2Params,
  baseKey: DevCryptoKey,
  length: number,
): Promise<ArrayBuffer> {
  const C = await _getCryptoJS()

  const password = C.lib.WordArray.create(baseKey._keyData)
  const salt = params.salt instanceof Uint8Array
    ? C.lib.WordArray.create(params.salt)
    : C.lib.WordArray.create(new Uint8Array(params.salt as ArrayBuffer))

  const keySize = Math.ceil(length / 32) // WordArray 以 32-bit 字为单位

  const derived = C.PBKDF2(password, salt, {
    keySize,
    iterations: params.iterations,
    hasher: C.algo.SHA256,
  })

  // 截取需要的字节数
  const byteLen = Math.ceil(length / 8)
  const result = new Uint8Array(byteLen)
  const words = derived.words
  for (let i = 0; i < byteLen; i++) {
    result[i] = (words[i >>> 2] >>> (24 - (i % 4) * 8)) & 0xff
  }
  return result.buffer
}

/**
 * 安全调用 crypto.getRandomValues
 *
 * 生产环境：直接调用原生 crypto.getRandomValues（始终可用）
 * 开发环境：不可用时降级为 crypto-js 随机数生成
 *
 * crypto.getRandomValues 在 HTTP 下仍然可用，此降级仅为防御性编程。
 * 注意：crypto-js 的随机数基于 Math.random()，非密码学安全，仅开发环境使用。
 *
 * 生产构建时降级分支被 tree-shaking 移除。
 */
export function getRandomValues<T extends ArrayBufferView | null>(array: T): T {
  if (import.meta.env.DEV) {
    if (!globalThis.crypto?.getRandomValues) {
      const byteLength = (array as unknown as Uint8Array).byteLength
      const u8 = array as unknown as Uint8Array
      if (_CryptoJS) {
        // crypto-js 已加载，使用其随机数
        const random = _CryptoJS.lib.WordArray.random(byteLength)
        for (let i = 0; i < byteLength; i++) {
          u8[i] = (random.words[i >>> 2] >>> (24 - (i % 4) * 8)) & 0xff
        }
      } else {
        // crypto-js 尚未加载（同步函数先于异步操作被调用），
        // 降级使用 Math.random() — 仅开发环境，不可用于生产
        for (let i = 0; i < byteLength; i++) {
          u8[i] = Math.floor(Math.random() * 256)
        }
      }
      return array
    }
  }
  return globalThis.crypto.getRandomValues(array)
}

/**
 * 检查当前环境是否支持 crypto.subtle
 * 仅在开发环境有意义，生产环境始终返回 true
 */
export function isSubtleAvailable(): boolean {
  if (import.meta.env.DEV) {
    return !!(globalThis.crypto && globalThis.crypto.subtle)
  }
  return true
}