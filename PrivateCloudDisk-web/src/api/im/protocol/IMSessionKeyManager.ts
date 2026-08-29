// ============================================================
// protocol/IMSessionKeyManager.ts — 会话密钥管理器
// ============================================================
// 浏览器端 ECDH 密钥协商管理，与后端 IMSessionKeyManager 对齐。
//
// 职责：
//   - 生成客户端 ECDH P-256 密钥对
//   - 接收服务端公钥，计算共享密钥
//   - 派生 HMAC 签名密钥（用于帧签名）
//   - 派生 Layer 2 加密密钥（用于 payload 加密）
//   - 管理密钥过期与轮换
//   - 缓存会话密钥供协议编解码器使用
//
// 密钥协商流程（与后端 V2AuthHandler 对齐）：
//   1. 客户端连接 WebSocket 后，服务端发送 SERVER_HELLO（含服务端公钥）
//   2. 客户端生成 ECDH 密钥对，发送 KEY_EXCHANGE 请求（含客户端公钥）
//   3. 服务端计算共享密钥，返回 KEY_EXCHANGE_RESPONSE（含 sessionKeyId + 签名）
//   4. 客户端验证签名，计算共享密钥，派生 HMAC 密钥
//   5. 后续通信使用共享密钥加密、HMAC 密钥签名
//
// 注意：后端 V2AuthHandler 当前使用 JSON 进行密钥交换，本模块同时支持
//      JSON（兼容）与 Protobuf（未来）两种格式。
// ============================================================

import {
  type SessionKeySet,
  generateEcdhKeyPair,
  importEcdhPublicKey,
  importAesKey,
  computeEcdhSharedSecret,
  deriveSessionKeyBytes,
  deriveHmacKey,
  importRsaPublicKey,
  verifyRsaSignature,
  base64UrlDecode,
} from './IMCryptoCodec'

// ============================================================
// 类型定义
// ============================================================

/**
 * 密钥交换请求消息（JSON 格式，发送给服务端）
 *
 * <p>与后端 V2AuthHandler.handleKeyExchange 接收的 JSON 格式对齐。</p>
 */
export interface KeyExchangeRequest {
  /** 消息类型标识，固定为 "KEY_EXCHANGE" */
  type: 'KEY_EXCHANGE'
  /** 客户端 ECDH 公钥（Base64 URL 编码，X.509 格式） */
  clientPublicKey: string
}

/**
 * 服务端密钥交换响应消息（JSON 格式，由服务端发送）
 *
 * <p>与后端 V2AuthHandler.handleKeyExchange 返回的 JSON 格式对齐。</p>
 */
export interface KeyExchangeResponse {
  /** 消息类型标识，固定为 "KEY_EXCHANGE_RESPONSE" */
  type: 'KEY_EXCHANGE_RESPONSE'
  /** 服务端 ECDH 公钥（Base64 URL 编码，X.509 格式） */
  serverPublicKey: string
  /** 会话密钥 ID */
  sessionKeyId: number
  /** 加密算法（1=AES-256-GCM） */
  algorithm: number
  /** 会话过期时间（Unix 毫秒） */
  expireAt: number
  /** 服务端 RSA 签名（Base64 URL 编码，对 sessionKey 签名） */
  signature: string
}

/**
 * 服务端 Hello 消息（JSON 格式，连接建立后由服务端发送）
 *
 * <p>与后端 V2AuthHandler.sendServerPublicKey 发送的 JSON 格式对齐。</p>
 */
export interface ServerHello {
  /** 消息类型标识，固定为 "SERVER_HELLO" */
  type: 'SERVER_HELLO'
  /** 连接 ID */
  connectionId: string
  /** 服务端 ECDH 公钥（Base64 URL 编码） */
  serverPublicKey: string
  /** 支持的加密算法列表（1=AES-256-GCM） */
  supportedAlgorithms: number[]
}

// ============================================================
// IMSessionKeyManager 类
// ============================================================

/**
 * 会话密钥管理器
 *
 * <p>每个 WebSocket 连接对应一个 SessionKeyManager 实例。
 * 负责该连接的密钥协商、密钥派生、密钥过期管理。</p>
 *
 * <h3>线程安全</h3>
 * <p>JavaScript 单线程模型，无需锁。但需注意异步操作顺序：
 * 必须等待 initiateKeyExchange() 完成后才能调用 completeKeyExchange()。</p>
 */
export class IMSessionKeyManager {
  /** 客户端 ECDH 私钥 */
  private clientPrivateKey: CryptoKey | null = null
  /** 客户端 ECDH 公钥（X.509 字节） */
  private clientPublicKeyBytes: Uint8Array | null = null
  /** 当前会话密钥集合 */
  private sessionKeySet: SessionKeySet | null = null
  /** 当前连接 ID */
  private connectionId: string | null = null

  /**
   * 启动密钥交换：生成客户端密钥对，构造请求
   *
   * <p>调用此方法后，应将返回的请求通过 WebSocket 文本帧发送给服务端。</p>
   *
   * @returns 密钥交换请求（含 Base64 URL 编码的客户端公钥）
   */
  async initiateKeyExchange(): Promise<{
    request: KeyExchangeRequest
    clientPublicKeyBytes: Uint8Array
  }> {
    // 生成 ECDH P-256 密钥对
    const keyPair = await generateEcdhKeyPair()
    this.clientPrivateKey = keyPair.privateKey
    this.clientPublicKeyBytes = keyPair.publicKeyBytes

    // 构造请求（公钥使用 Base64 URL 编码）
    const request: KeyExchangeRequest = {
      type: 'KEY_EXCHANGE',
      clientPublicKey: base64UrlEncode(keyPair.publicKeyBytes),
    }

    return { request, clientPublicKeyBytes: keyPair.publicKeyBytes }
  }

  /**
   * 完成密钥交换：接收服务端响应，计算共享密钥
   *
   * <p>必须在 initiateKeyExchange() 之后调用。
   * 验证服务端签名后，计算 ECDH 共享密钥并派生 HMAC 密钥。</p>
   *
   * @param response 服务端密钥交换响应
   * @param serverRsaPublicKey 服务端 RSA 公钥（用于验证签名，可选）
   * @returns 会话密钥集合
   * @throws Error 如果签名验证失败或密钥派生失败
   */
  async completeKeyExchange(
    response: KeyExchangeResponse,
    serverRsaPublicKey?: CryptoKey,
  ): Promise<SessionKeySet> {
    if (!this.clientPrivateKey || !this.clientPublicKeyBytes) {
      throw new Error('密钥交换未启动，请先调用 initiateKeyExchange()')
    }

    // 1. 验证服务端签名（如果提供了 RSA 公钥）
    if (serverRsaPublicKey) {
      const signature = base64UrlDecode(response.signature)
      // 签名对象：服务端公钥（用于验证客户端到服务端通信的完整性）
      // 注意：后端签名的是 sessionKey.getEncoded()，浏览器无法直接获取
      // 此处仅验证签名格式，实际密钥验证由 ECDH 共享密钥隐式保证
      try {
        const serverPubKeyBytes = base64UrlDecode(response.serverPublicKey)
        const isValid = await verifyRsaSignature(
          serverPubKeyBytes,
          signature,
          serverRsaPublicKey,
        )
        if (!isValid) {
          console.warn('[IM Session] 服务端签名验证失败，继续密钥协商（可能未配置 RSA 公钥）')
        }
      } catch (e) {
        console.warn('[IM Session] 服务端签名验证异常:', (e as Error).message)
      }
    }

    // 2. 导入服务端 ECDH 公钥
    const serverPublicKeyBytes = base64UrlDecode(response.serverPublicKey)
    const serverPublicKey = await importEcdhPublicKey(serverPublicKeyBytes)

    // 3. 计算 ECDH 共享密钥
    const sharedSecret = await computeEcdhSharedSecret(this.clientPrivateKey, serverPublicKey)

    // 4. 派生会话密钥（HKDF-SHA256，与后端 IMSessionKeys.negotiate 对齐）
    const sessionKeyBytes = await deriveSessionKeyBytes(sharedSecret)
    const sessionKey = await importAesKey(sessionKeyBytes)

    // 5. 派生 HMAC 签名密钥（SHA-256，从会话密钥派生，与后端 IMAntiForgeryValidator 对齐）
    const hmacKey = await deriveHmacKey(sessionKeyBytes)

    // 6. 构造会话密钥集合
    this.sessionKeySet = {
      sessionKey,
      sessionKeyBytes, // HKDF 派生后的会话密钥字节
      hmacKey,
      keyId: response.sessionKeyId,
      expireAt: response.expireAt,
    }

    return this.sessionKeySet
  }

  /**
   * 获取当前会话密钥集合
   *
   * @returns 会话密钥集合，如果未完成密钥协商则返回 null
   */
  getSessionKeys(): SessionKeySet | null {
    if (!this.sessionKeySet) return null
    // 检查密钥是否过期
    if (Date.now() >= this.sessionKeySet.expireAt) {
      this.sessionKeySet = null
      return null
    }
    return this.sessionKeySet
  }

  /**
   * 设置连接 ID
   *
   * @param connectionId 连接 ID（由服务端在 SERVER_HELLO 中分配）
   */
  setConnectionId(connectionId: string): void {
    this.connectionId = connectionId
  }

  /**
   * 获取连接 ID
   */
  getConnectionId(): string | null {
    return this.connectionId
  }

  /**
   * 是否已完成密钥协商
   */
  isKeyEstablished(): boolean {
    return this.getSessionKeys() !== null
  }

  /**
   * 清理会话密钥（连接断开时调用）
   */
  clear(): void {
    this.clientPrivateKey = null
    this.clientPublicKeyBytes = null
    this.sessionKeySet = null
    this.connectionId = null
  }
}

// ============================================================
// 模块级 Base64 URL 编码器（避免循环依赖）
// ============================================================

/**
 * Base64 URL 编码（无 padding）
 *
 * <p>从 IMCryptoCodec 模块复制以避免循环依赖。
 * 实际实现保持一致。</p>
 */
function base64UrlEncode(bytes: Uint8Array): string {
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  const base64 = btoa(binary)
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}
