/**
 * utils/crypto.js - 纯 JS 加密工具集（uni-app 跨平台兼容）
 *
 * 由于 uni-app 在 App/小程序/H5 各端 Web Crypto API 可用性不一致，
 * 使用纯 JS 实现 HMAC-SHA256，确保所有平台行为一致。
 *
 * 与网关 SecurityService 对接，签名密钥保持一致。
 */

// ============================================================
// SHA-256 纯 JS 实现
// ============================================================

const K = [
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
]

function sha256(message) {
  const msgBytes = typeof message === 'string' ? stringToBytes(message) : new Uint8Array(message)
  return sha256Raw(msgBytes)
}

function sha256Raw(msgBytes) {
  const msgBitLen = msgBytes.length * 8
  const padded = padMessage(msgBytes, msgBitLen)

  let H = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19]

  for (let i = 0; i < padded.length; i += 64) {
    const chunk = padded.slice(i, i + 64)
    const W = new Array(64)

    for (let t = 0; t < 16; t++) {
      W[t] = (chunk[t * 4] << 24) | (chunk[t * 4 + 1] << 16) | (chunk[t * 4 + 2] << 8) | chunk[t * 4 + 3]
    }

    for (let t = 16; t < 64; t++) {
      const s0 = rotr(W[t - 15], 7) ^ rotr(W[t - 15], 18) ^ (W[t - 15] >>> 3)
      const s1 = rotr(W[t - 2], 17) ^ rotr(W[t - 2], 19) ^ (W[t - 2] >>> 10)
      W[t] = (W[t - 16] + s0 + W[t - 7] + s1) | 0
    }

    let [a, b, c, d, e, f, g, h] = H

    for (let t = 0; t < 64; t++) {
      const S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)
      const ch = (e & f) ^ (~e & g)
      const temp1 = (h + S1 + ch + K[t] + W[t]) | 0
      const S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)
      const maj = (a & b) ^ (a & c) ^ (b & c)
      const temp2 = (S0 + maj) | 0

      h = g; g = f; f = e; e = (d + temp1) | 0
      d = c; c = b; b = a; a = (temp1 + temp2) | 0
    }

    H = [H[0] + a, H[1] + b, H[2] + c, H[3] + d, H[4] + e, H[5] + f, H[6] + g, H[7] + h].map((x) => x | 0)
  }

  const result = new Uint8Array(32)
  for (let i = 0; i < 8; i++) {
    result[i * 4] = (H[i] >>> 24) & 0xff
    result[i * 4 + 1] = (H[i] >>> 16) & 0xff
    result[i * 4 + 2] = (H[i] >>> 8) & 0xff
    result[i * 4 + 3] = H[i] & 0xff
  }
  return result
}

function rotr(x, n) {
  return (x >>> n) | (x << (32 - n))
}

function padMessage(msgBytes, msgBitLen) {
  const padLen = (448 - (msgBitLen + 1) % 512 + 512) % 512
  const totalLen = msgBitLen + 1 + padLen + 64
  const padded = new Uint8Array(totalLen / 8)

  padded.set(msgBytes)
  padded[msgBytes.length] = 0x80

  // Write bit length as 64-bit big-endian
  const hi = Math.floor(msgBitLen / 0x100000000)
  const lo = msgBitLen % 0x100000000
  const lenIdx = padded.length - 8
  padded[lenIdx] = (hi >>> 24) & 0xff
  padded[lenIdx + 1] = (hi >>> 16) & 0xff
  padded[lenIdx + 2] = (hi >>> 8) & 0xff
  padded[lenIdx + 3] = hi & 0xff
  padded[lenIdx + 4] = (lo >>> 24) & 0xff
  padded[lenIdx + 5] = (lo >>> 16) & 0xff
  padded[lenIdx + 6] = (lo >>> 8) & 0xff
  padded[lenIdx + 7] = lo & 0xff

  return padded
}

function stringToBytes(str) {
  const bytes = new Uint8Array(str.length)
  for (let i = 0; i < str.length; i++) {
    bytes[i] = str.charCodeAt(i) & 0xff
  }
  return bytes
}

// ============================================================
// HMAC-SHA256
// ============================================================

const BLOCK_SIZE = 64

function hmacSha256(key, message) {
  const keyBytes = typeof key === 'string' ? stringToBytes(key) : new Uint8Array(key)

  // 如果 key 比 block size 长，先 hash
  let actualKey = keyBytes
  if (keyBytes.length > BLOCK_SIZE) {
    actualKey = sha256Raw(keyBytes)
  }

  // 填充到 block size
  if (actualKey.length < BLOCK_SIZE) {
    const padded = new Uint8Array(BLOCK_SIZE)
    padded.set(actualKey)
    actualKey = padded
  }

  // o_key_pad
  const oKeyPad = new Uint8Array(BLOCK_SIZE)
  for (let i = 0; i < BLOCK_SIZE; i++) {
    oKeyPad[i] = actualKey[i] ^ 0x5c
  }

  // i_key_pad
  const iKeyPad = new Uint8Array(BLOCK_SIZE)
  for (let i = 0; i < BLOCK_SIZE; i++) {
    iKeyPad[i] = actualKey[i] ^ 0x36
  }

  // 内层 hash
  const innerMsg = new Uint8Array(BLOCK_SIZE + (typeof message === 'string' ? message.length : message.length))
  innerMsg.set(iKeyPad)
  if (typeof message === 'string') {
    const msgBytes = stringToBytes(message)
    innerMsg.set(msgBytes, BLOCK_SIZE)
  } else {
    innerMsg.set(new Uint8Array(message), BLOCK_SIZE)
  }
  const innerHash = sha256Raw(innerMsg)

  // 外层 hash
  const outerMsg = new Uint8Array(BLOCK_SIZE + 32)
  outerMsg.set(oKeyPad)
  outerMsg.set(innerHash, BLOCK_SIZE)

  return sha256Raw(outerMsg)
}

// ============================================================
// 工具函数
// ============================================================

function bytesToHex(bytes) {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

// ============================================================
// 导出
// ============================================================

/**
 * HMAC-SHA256 签名
 * @param {string} data - 要签名的数据
 * @param {string} secret - 密钥
 * @returns {Promise<string>} 十六进制签名
 */
export async function hmacSign(data, secret) {
  return bytesToHex(hmacSha256(secret, data))
}

/**
 * SHA-256 哈希
 * @param {string} message
 * @returns {Promise<string>} 十六进制哈希
 */
export async function sha256Hash(message) {
  return bytesToHex(sha256(message))
}

/**
 * 生成安全随机字符串
 * @param {number} length - 字节数
 * @returns {string} 十六进制字符串
 */
export function randomHex(length) {
  const bytes = new Uint8Array(length)
  // uni-app 兼容：尝试使用 crypto.getRandomValues，不可用时回退
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    crypto.getRandomValues(bytes)
  } else {
    for (let i = 0; i < length; i++) {
      bytes[i] = Math.floor(Math.random() * 256)
    }
  }
  return bytesToHex(bytes)
}

// ============================================================
// PBKDF2-SHA256 密码预哈希（v5.0）
// ============================================================

/**
 * 使用 PBKDF2-SHA256 对密码进行客户端预哈希。
 * 与网关 PBKDF2PasswordEncoder 兼容。
 * 密码明文永不离开客户端。
 *
 * 格式：pbkdf2:sha256:{iterations}${saltHex}${hashHex}
 *
 * @param {string} password - 明文密码
 * @param {string} salt - 用户特定的 salt（如用户名/手机号）
 * @returns {Promise<string>} 预哈希后的密码字符串
 */
export async function hashPasswordForTransport(password, salt) {
  const iterations = 600000
  const keyLength = 32 // 256 bits

  // 使用提供的 salt，通过 SHA-256 派生
  const saltBytes = sha256(salt)

  // PBKDF2-HMAC-SHA256
  const derivedKey = pbkdf2(password, saltBytes, iterations, keyLength)
  const saltHex = bytesToHex(saltBytes)
  const hashHex = bytesToHex(derivedKey)

  return `pbkdf2:sha256:${iterations}$${saltHex}$${hashHex}`
}

/**
 * PBKDF2-HMAC-SHA256 实现
 * @param {string} password - 密码
 * @param {Uint8Array} salt - salt
 * @param {number} iterations - 迭代次数
 * @param {number} keyLength - 输出密钥长度（字节）
 * @returns {Uint8Array} 派生密钥
 */
function pbkdf2(password, salt, iterations, keyLength) {
  const hLen = 32 // SHA-256 输出 32 字节
  const blockCount = Math.ceil(keyLength / hLen)
  const result = new Uint8Array(blockCount * hLen)

  for (let i = 1; i <= blockCount; i++) {
    const block = pbkdf2Block(password, salt, iterations, i)
    result.set(block, (i - 1) * hLen)
  }

  return result.slice(0, keyLength)
}

function pbkdf2Block(password, salt, iterations, blockIndex) {
  const hLen = 32

  // 将 blockIndex 编码为 4 字节大端
  const blockIndexBytes = new Uint8Array(4)
  blockIndexBytes[0] = (blockIndex >>> 24) & 0xff
  blockIndexBytes[1] = (blockIndex >>> 16) & 0xff
  blockIndexBytes[2] = (blockIndex >>> 8) & 0xff
  blockIndexBytes[3] = blockIndex & 0xff

  // U = salt || INT(i)
  const saltWithIndex = new Uint8Array(salt.length + 4)
  saltWithIndex.set(salt)
  saltWithIndex.set(blockIndexBytes, salt.length)

  // 第一次 PRF
  let u = hmacSha256(password, saltWithIndex)
  let result = new Uint8Array(u)

  // 迭代
  for (let j = 1; j < iterations; j++) {
    u = hmacSha256(password, u)
    for (let k = 0; k < hLen; k++) {
      result[k] ^= u[k]
    }
  }

  return result
}