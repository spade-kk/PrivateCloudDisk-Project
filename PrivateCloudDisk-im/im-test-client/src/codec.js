// ============================================================
// codec.js — 二进制帧编解码模块
// ============================================================
// 与前端 IMProtocolCodec.ts 和后端 IMProtocolCodec.java 完全对齐。
//
// Wire Format（二进制帧格式）:
//   ┌────────────────┬────────────────┬──────────────────┬──────────────────┐
//   │  Total Length  │  Header Length │  Encrypted Header│  HMAC Signature  │
//   │   (4 bytes BE) │   (4 bytes BE) │   (variable)     │   (32 bytes)     │
//   └────────────────┴────────────────┴──────────────────┴──────────────────┘
//
//   - Total Length: 4 字节，大端序，整个帧的总长度（含自身）
//   - Header Length: 4 字节，大端序，加密后的 Envelope 字节长度
//   - Encrypted Header: AES-256-GCM 加密的 IMEnvelope
//   - HMAC Signature: 32 字节，对 [Total Length + Header Length + Encrypted Header] 的 HMAC-SHA256
// ============================================================

import {
  encodeMessage,
  decodeMessage,
  CODEC_TYPE_NAME_MAP,
} from './proto-loader.js'
import {
  encryptAesGcm,
  decryptAesGcm,
  deriveLayer2Key,
  sign,
  verify,
  HMAC_SIZE,
} from './crypto.js'

// 重新导出 HMAC_SIZE 供外部使用
export { HMAC_SIZE }

// ==================== 帧格式常量 ====================

/** 总长度字段偏移 */
export const OFFSET_TOTAL_LENGTH = 0

/** 总长度字段大小（字节） */
export const SIZE_TOTAL_LENGTH = 4

/** 头长度字段偏移 */
export const OFFSET_HEADER_LENGTH = SIZE_TOTAL_LENGTH

/** 头长度字段大小（字节） */
export const SIZE_HEADER_LENGTH = 4

/** 帧头总大小（Total Length + Header Length） */
export const FRAME_HEADER_SIZE = SIZE_TOTAL_LENGTH + SIZE_HEADER_LENGTH

/** 最小帧大小（帧头 + HMAC + 1 字节数据） */
export const MIN_FRAME_SIZE = FRAME_HEADER_SIZE + HMAC_SIZE + 1

/** 最大帧大小（1MB 数据 + 帧头 + HMAC） */
export const MAX_FRAME_SIZE = 1024 * 1024 + FRAME_HEADER_SIZE + HMAC_SIZE

// ==================== 编码（发送方向） ====================

/**
 * 编码 IMEnvelope 为二进制帧
 *
 * 流程：
 * 1. 序列化 IMEnvelope 为 protobuf 字节
 * 2. 使用 Session Key 进行 AES-256-GCM 加密
 * 3. 构建帧头（totalLength + headerLength，大端序）
 * 4. 拼接帧头 + 加密数据
 * 5. 计算 HMAC-SHA256 签名
 * 6. 附加 HMAC 签名，返回完整帧
 *
 * @param {object} envelope - IMEnvelope 消息对象
 * @param {import('./crypto.js').SessionKeySet} sessionKeys - 会话密钥集合
 * @returns {Buffer} 完整的二进制帧
 */
export function encode(envelope, sessionKeys) {
  // 1. 验证消息 ID
  if (!envelope.messageId || typeof envelope.messageId !== 'string') {
    throw new ProtocolCodecError('Invalid message ID: missing or wrong type')
  }

  // 2. Layer 2 加密（如果存在 payload）
  //    与后端 MessageTypeDispatcher.encodePayload 对齐：
  //    derivedKey = HKDF(sessionKey, "pcd-im-v2-derived:" + typeName)
  let encryptedPayload = new Uint8Array(0)
  if (envelope.encryptedPayload && envelope.encryptedPayload.length > 0) {
    const typeName = CODEC_TYPE_NAME_MAP[envelope.messageType]
    if (!typeName) {
      throw new ProtocolCodecError(
        `Unknown message type for Layer 2 encryption: ${envelope.messageType}`,
      )
    }
    const layer2Key = deriveLayer2Key(sessionKeys.sessionKey, typeName)
    const result = encryptAesGcm(envelope.encryptedPayload, layer2Key)
    encryptedPayload = result.combined // IV(12) + ciphertext + tag(16)
  }

  // 3. 构建 IMEnvelope（设置 Layer 2 加密后的 payload）
  const imEnvelope = {
    version: envelope.version || 2,
    messageId: envelope.messageId,
    command: envelope.command || 0,
    messageType: envelope.messageType || 0,
    senderId: envelope.senderId || '',
    receiverId: envelope.receiverId || '',
    conversationId: envelope.conversationId || '',
    conversationType: envelope.conversationType || 0,
    seq: envelope.seq || 0,
    timestamp: envelope.timestamp || Date.now(),
    status: envelope.status || 0,
    encryptedPayload: encryptedPayload,
    senderDeviceType: envelope.senderDeviceType || 0,
    senderPlatform: envelope.senderPlatform || '',
    senderAppVersion: envelope.senderAppVersion || '',
  }

  // 4. 序列化 IMEnvelope 为 protobuf 字节
  const envelopeBytes = encodeMessage('IMEnvelope', imEnvelope)

  // 5. Layer 1 加密（AES-256-GCM）
  const encrypted = encryptAesGcm(envelopeBytes, sessionKeys.sessionKey)
  const encryptedData = encrypted.combined

  // 6. 验证加密后大小
  if (encryptedData.length > MAX_FRAME_SIZE - FRAME_HEADER_SIZE - HMAC_SIZE) {
    throw new ProtocolCodecError(
      `Encrypted data too large: ${encryptedData.length} bytes`,
    )
  }

  // 7. 构建帧（帧头 + 加密数据 + HMAC）
  const totalLength = FRAME_HEADER_SIZE + encryptedData.length + HMAC_SIZE
  const frame = Buffer.alloc(totalLength)

  // 总长度（大端序）
  frame.writeUInt32BE(totalLength, OFFSET_TOTAL_LENGTH)
  // 加密头长度（大端序）
  frame.writeUInt32BE(encryptedData.length, OFFSET_HEADER_LENGTH)
  // 加密后的 envelope
  encryptedData.copy(frame, FRAME_HEADER_SIZE)

  // 8. 计算 HMAC 签名（对 [帧头 + 加密数据] 签名）
  const frameData = frame.subarray(0, FRAME_HEADER_SIZE + encryptedData.length)
  const hmac = sign(frameData, sessionKeys.hmacKey)
  hmac.copy(frame, FRAME_HEADER_SIZE + encryptedData.length)

  return frame
}

// ==================== 解码（接收方向） ====================

/**
 * 从二进制帧解码为 IMEnvelope
 *
 * 流程：
 * 1. 验证帧大小与格式
 * 2. 验证 HMAC 签名（防篡改）
 * 3. 提取加密数据
 * 4. 使用 Session Key 进行 AES-256-GCM 解密（Layer 1）
 * 5. 反序列化 IMEnvelope
 * 6. 使用派生密钥解密 payload（Layer 2）
 *
 * @param {Buffer|Uint8Array} frame - 二进制帧
 * @param {import('./crypto.js').SessionKeySet} sessionKeys - 会话密钥集合
 * @returns {object} IMEnvelope 消息对象（含 Layer 2 解密后的 payload）
 */
export function decode(frame, sessionKeys) {
  const buf = Buffer.from(frame)

  // 1. 验证帧大小
  if (!buf || buf.length < MIN_FRAME_SIZE) {
    throw new ProtocolCodecError(`Frame too small: ${buf ? buf.length : 0} bytes`)
  }
  if (buf.length > MAX_FRAME_SIZE) {
    throw new ProtocolCodecError(`Frame too large: ${buf.length} bytes`)
  }

  // 2. 读取帧头（大端序）
  const totalLength = buf.readUInt32BE(OFFSET_TOTAL_LENGTH)
  const headerLength = buf.readUInt32BE(OFFSET_HEADER_LENGTH)

  // 验证长度一致性
  if (totalLength !== buf.length) {
    throw new ProtocolCodecError(
      `Total length mismatch: declared=${totalLength}, actual=${buf.length}`,
    )
  }
  if (headerLength <= 0 || headerLength > totalLength - FRAME_HEADER_SIZE - HMAC_SIZE) {
    throw new ProtocolCodecError(`Invalid header length: ${headerLength}`)
  }

  // 3. 提取帧数据部分（不含 HMAC）
  const dataEnd = totalLength - HMAC_SIZE
  const frameData = buf.subarray(0, dataEnd)

  // 4. 验证 HMAC
  const hmac = buf.subarray(dataEnd, totalLength)
  const hmacValid = verify(frameData, hmac, sessionKeys.hmacKey)
  if (!hmacValid) {
    throw new ProtocolCodecError(
      'HMAC verification failed — message tampered or wrong key',
    )
  }

  // 5. 提取加密数据并 Layer 1 解密
  const encryptedData = buf.subarray(FRAME_HEADER_SIZE, dataEnd)
  const envelopeBytes = decryptAesGcm(encryptedData, sessionKeys.sessionKey)

  // 6. 反序列化 IMEnvelope
  const envelope = decodeMessage('IMEnvelope', envelopeBytes)

  // 7. Layer 2 解密（如果存在加密 payload）
  //    与后端 MessageTypeDispatcher.dispatch 对齐
  if (envelope.encryptedPayload && envelope.encryptedPayload.length > 0) {
    const typeName = CODEC_TYPE_NAME_MAP[envelope.messageType]
    if (typeName) {
      const layer2Key = deriveLayer2Key(sessionKeys.sessionKey, typeName)
      const decryptedPayload = decryptAesGcm(envelope.encryptedPayload, layer2Key)
      envelope.encryptedPayload = decryptedPayload
    }
    // 如果 typeName 未找到，保留原始加密 payload（调用方可能自行处理）
  }

  return envelope
}

/**
 * 解码未加密的 IMEnvelope（用于握手阶段的错误通知）
 * 直接反序列化 protobuf 字节，不进行解密和 HMAC 验证
 * @param {Buffer|Uint8Array} data - 未加密的 protobuf 字节
 * @returns {object} IMEnvelope 消息对象
 */
export function decodeUnencrypted(data) {
  return decodeMessage('IMEnvelope', Buffer.from(data))
}

// ==================== 流式帧读取 ====================

/**
 * 检查 Buffer 是否包含完整帧
 * @param {Buffer} data - 接收到的数据
 * @returns {boolean} true 如果是完整帧
 */
export function isCompleteFrame(data) {
  if (data.length < FRAME_HEADER_SIZE) return false
  const totalLength = data.readUInt32BE(OFFSET_TOTAL_LENGTH)
  return data.length >= totalLength
}

/**
 * 从缓冲区读取一个完整帧
 * @param {Buffer} buffer - 累积缓冲区
 * @returns {[Buffer, Buffer] | null} [完整帧, 剩余缓冲区] 或 null（数据不完整）
 */
export function readFrame(buffer) {
  if (buffer.length < FRAME_HEADER_SIZE) return null

  const totalLength = buffer.readUInt32BE(OFFSET_TOTAL_LENGTH)

  if (totalLength < MIN_FRAME_SIZE || totalLength > MAX_FRAME_SIZE) {
    throw new ProtocolCodecError(`Invalid total length in frame: ${totalLength}`)
  }

  if (buffer.length < totalLength) return null

  const frame = buffer.subarray(0, totalLength)
  const remaining = buffer.subarray(totalLength)
  return [frame, remaining]
}

// ==================== 异常类 ====================

/** 协议编解码异常 */
export class ProtocolCodecError extends Error {
  constructor(message, cause) {
    super(message)
    this.name = 'ProtocolCodecError'
    if (cause) this.cause = cause
  }
}
