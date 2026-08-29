// ============================================================
// protocol/IMProtocolCodec.ts — IM v2 协议编解码器
// ============================================================
// 企业级二进制协议编解码，与后端 IMProtocolCodec 完全对齐。
//
// Wire Format（二进制帧格式）:
//   ┌────────────────┬────────────────┬──────────────────┬──────────────────┐
//   │  Total Length  │  Header Length │  Encrypted Header│  Encrypted Body  │
//   │   (4 bytes)    │   (4 bytes)    │   (variable)     │   (variable)     │
//   ├────────────────┴────────────────┴──────────────────┴──────────────────┤
//   │                          HMAC Signature (32 bytes)                     │
//   └───────────────────────────────────────────────────────────────────────┘
//
//   - Total Length: 4 字节，大端序，整个帧的总长度（含自身）
//   - Header Length: 4 字节，大端序，加密后的 Envelope 字节长度
//   - Encrypted Header: AES-256-GCM 加密的 IMEnvelope（IV 12B + 密文 + Auth Tag 16B）
//   - HMAC Signature: 32 字节，对 [Total Length + Header Length + Encrypted Header] 的 HMAC-SHA256
//
// 处理流程：
//   编码: IMEnvelope → protobuf 序列化 → AES-GCM 加密 → 拼接长度头 → 附加 HMAC
//   解码: binary frame → 验证 HMAC → 提取长度 → AES-GCM 解密 → protobuf 反序列化 → IMEnvelope
//
// 性能考虑：
//   - 使用 Uint8Array 而非 ArrayBuffer 提供更好的类型安全
//   - 使用 DataView 进行大端序读写，避免平台依赖
//   - 异步 API 适配 Web Crypto API
// ============================================================

import {
  type IMEnvelopeMessage,
  IMEnvelopeType,
} from './protoSchema'
import {
  type SessionKeySet,
  encryptLayer1,
  decryptLayer1,
  encryptLayer2,
  decryptLayer2,
  deriveLayer2KeyForType,
  CODEC_TYPE_NAME_MAP,
} from './IMCryptoCodec'
import { sign, verify, isTimestampValid } from './IMAntiForgeryValidator'

// ============================================================
// 帧格式常量（与后端 IMProtocolCodec 对齐）
// ============================================================

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

/** HMAC 签名大小（字节） */
export const HMAC_SIZE = 32

/** 最小帧大小（帧头 + HMAC + 1 字节数据） */
export const MIN_FRAME_SIZE = FRAME_HEADER_SIZE + HMAC_SIZE + 1

/** 最大帧大小（1MB 数据 + 帧头 + HMAC） */
export const MAX_FRAME_SIZE = 1024 * 1024 + FRAME_HEADER_SIZE + HMAC_SIZE

// ============================================================
// 异常类
// ============================================================

/**
 * 协议编解码异常
 *
 * <p>与后端 IMProtocolCodec.ProtocolCodecException 对齐。</p>
 */
export class ProtocolCodecException extends Error {
  constructor(message: string, public readonly cause?: unknown) {
    super(message)
    this.name = 'ProtocolCodecException'
  }
}

// ============================================================
// 编码（发送方向）
// ============================================================

/**
 * 编码消息为二进制帧
 *
 * <p>流程：
 * 1. 序列化 IMEnvelope 为 protobuf 字节
 * 2. 使用 Session Key 进行 AES-256-GCM 加密（Layer 1）
 * 3. 构建帧头（totalLength + headerLength，大端序）
 * 4. 拼接帧头 + 加密数据
 * 5. 计算 HMAC-SHA256 签名（对帧头+加密数据）
 * 6. 附加 HMAC 签名，返回完整帧</p>
 *
 * @param envelope IMEnvelope 消息对象
 * @param sessionKeys 会话密钥集合
 * @returns 完整的二进制帧
 * @throws ProtocolCodecException 如果编码失败
 */
export async function encode(
  envelope: IMEnvelopeMessage,
  sessionKeys: SessionKeySet,
): Promise<Uint8Array> {
  try {
    // 1. 验证消息 ID（防注入）
    const messageId = envelope.messageId
    if (!messageId || typeof messageId !== 'string') {
      throw new ProtocolCodecException('Invalid message ID: missing or wrong type')
    }

    // 2. Layer 2 加密（如果存在 payload）
    //    与后端 MessageTypeDispatcher.encodePayload 对齐：
    //    derivedKey = HKDF(sessionKey, "pcd-im-v2-derived:" + typeName)
    let encryptedPayload: Uint8Array = new Uint8Array(0)
    if (envelope.encryptedPayload && envelope.encryptedPayload.length > 0) {
      const typeName = CODEC_TYPE_NAME_MAP[envelope.messageType ?? 0]
      if (!typeName) {
        throw new ProtocolCodecException(
          `Unknown message type for Layer 2 encryption: ${envelope.messageType}`,
        )
      }
      const layer2Key = await deriveLayer2KeyForType(sessionKeys.sessionKeyBytes, typeName)
      const result = await encryptLayer2(envelope.encryptedPayload, layer2Key)
      encryptedPayload = result.combined // IV(12) + ciphertext + tag(16)
    }

    // 3. 构建 IMEnvelope（设置 Layer 2 加密后的 payload）
    const imEnvelope: IMEnvelopeMessage = {
      version: envelope.version || 2,
      messageId: envelope.messageId,
      command: envelope.command || 0,
      messageType: envelope.messageType || 0,
      senderId: envelope.senderId || '',
      receiverId: envelope.receiverId || '',
      conversationId: envelope.conversationId || '',
      conversationType: envelope.conversationType || 0,
      seq: envelope.seq || 0,
      serverSeq: envelope.serverSeq || 0,
      timestamp: envelope.timestamp || Date.now(),
      status: envelope.status || 0,
      innerEncryption: envelope.innerEncryption,
      encryptedPayload: encryptedPayload,
      replyTo: envelope.replyTo || '',
      expireAt: envelope.expireAt || 0,
      extraJson: envelope.extraJson || '',
      senderDeviceType: envelope.senderDeviceType || 0,
      senderPlatform: envelope.senderPlatform || '',
      senderAppVersion: envelope.senderAppVersion || '',
      traceId: envelope.traceId || '',
      priority: envelope.priority || 0,
    }

    // 4. 序列化 IMEnvelope 为 protobuf 字节
    const EnvelopeType = IMEnvelopeType()
    // 验证消息并编码
    const errMsg = EnvelopeType.verify(imEnvelope)
    if (errMsg) {
      throw new ProtocolCodecException(`IMEnvelope verify failed: ${errMsg}`)
    }
    const messageInstance = EnvelopeType.create(imEnvelope)
    const envelopeBytes: Uint8Array = EnvelopeType.encode(messageInstance).finish()

    // 5. Layer 1 加密（AES-256-GCM）
    const encrypted = await encryptLayer1(envelopeBytes, sessionKeys.sessionKey)
    const encryptedData = encrypted.combined

    // 6. 验证加密后大小
    if (encryptedData.length > MAX_FRAME_SIZE - FRAME_HEADER_SIZE - HMAC_SIZE) {
      throw new ProtocolCodecException(
        `Encrypted data too large: ${encryptedData.length} bytes`,
      )
    }

    // 7. 构建帧（帧头 + 加密数据 + HMAC）
    const totalLength = FRAME_HEADER_SIZE + encryptedData.length + HMAC_SIZE
    const frame = new Uint8Array(totalLength)
    const view = new DataView(frame.buffer)

    // 总长度（大端序）
    view.setUint32(OFFSET_TOTAL_LENGTH, totalLength, false)
    // 加密头长度（大端序）
    view.setUint32(OFFSET_HEADER_LENGTH, encryptedData.length, false)

    // 加密后的 envelope
    frame.set(encryptedData, FRAME_HEADER_SIZE)

    // 8. 计算 HMAC 签名（对 [帧头 + 加密数据] 签名）
    const frameData = frame.slice(0, FRAME_HEADER_SIZE + encryptedData.length)
    const hmac = await sign(frameData, sessionKeys.hmacKey)

    // 附加 HMAC
    frame.set(hmac, FRAME_HEADER_SIZE + encryptedData.length)

    return frame
  } catch (e) {
    if (e instanceof ProtocolCodecException) throw e
    throw new ProtocolCodecException('Encode failed', e)
  }
}

// ============================================================
// 解码（接收方向）
// ============================================================

/**
 * 从二进制帧解码为 IMEnvelope
 *
 * <p>流程：
 * 1. 验证帧大小与格式
 * 2. 验证 HMAC 签名（防篡改）
 * 3. 提取加密数据
 * 4. 使用 Session Key 进行 AES-256-GCM 解密
 * 5. 反序列化 IMEnvelope</p>
 *
 * @param frame 二进制帧
 * @param sessionKeys 会话密钥集合
 * @returns 解码后的 IMEnvelope 消息对象
 * @throws ProtocolCodecException 如果格式错误、签名无效或解密失败
 */
export async function decode(
  frame: Uint8Array,
  sessionKeys: SessionKeySet,
): Promise<IMEnvelopeMessage> {
  // 1. 验证帧大小
  if (!frame || frame.length < MIN_FRAME_SIZE) {
    throw new ProtocolCodecException(
      `Frame too small: ${frame ? frame.length : 0} bytes`,
    )
  }
  if (frame.length > MAX_FRAME_SIZE) {
    throw new ProtocolCodecException(`Frame too large: ${frame.length} bytes`)
  }

  const view = new DataView(frame.buffer, frame.byteOffset, frame.byteLength)

  // 2. 读取帧头（大端序）
  const totalLength = view.getUint32(OFFSET_TOTAL_LENGTH, false)
  const headerLength = view.getUint32(OFFSET_HEADER_LENGTH, false)

  // 验证长度一致性
  if (totalLength !== frame.length) {
    throw new ProtocolCodecException(
      `Total length mismatch: declared=${totalLength}, actual=${frame.length}`,
    )
  }
  if (headerLength <= 0 || headerLength > totalLength - FRAME_HEADER_SIZE - HMAC_SIZE) {
    throw new ProtocolCodecException(`Invalid header length: ${headerLength}`)
  }

  // 3. 提取帧数据部分（不含 HMAC）
  const dataEnd = totalLength - HMAC_SIZE
  const frameData = frame.slice(0, dataEnd)

  // 4. 验证 HMAC
  const hmac = frame.slice(dataEnd, totalLength)
  const hmacValid = await verify(frameData, hmac, sessionKeys.hmacKey)
  if (!hmacValid) {
    throw new ProtocolCodecException(
      'HMAC verification failed — message tampered or wrong key',
    )
  }

  // 5. 提取加密数据
  const encryptedData = frame.slice(FRAME_HEADER_SIZE, dataEnd)

  // 6. Layer 1 解密
  let envelopeBytes: Uint8Array
  try {
    envelopeBytes = await decryptLayer1(encryptedData, sessionKeys.sessionKey)
  } catch (e) {
    throw new ProtocolCodecException(
      `Decryption failed: ${(e as Error).message}`,
      e,
    )
  }

  // 7. 反序列化 IMEnvelope，并按 test-im-client 的同一顺序完成 Layer 2 解密。
  // AUDIT FIX [IM-PROTO-20260810]：原实现把 Layer 2 密文直接交给
  // ImWebSocketClient.decodePayload，密文首字节被 protobuf 当作 field tag，最终产生
  // "invalid wire type 7"。新行为先按 messageType 派生密钥并解密，再回调上层 Payload 解码。
  try {
    const EnvelopeType = IMEnvelopeType()
    const message = EnvelopeType.decode(envelopeBytes)
    // 转换为普通对象（protobufjs Message 实例的 toJSON 方法）
    const envelope = EnvelopeType.toObject(message, {
      longs: Number,
      enums: Number,
      bytes: Uint8Array,
      defaults: true,
    }) as IMEnvelopeMessage

    if (envelope.encryptedPayload && envelope.encryptedPayload.length > 0) {
      const typeName = CODEC_TYPE_NAME_MAP[envelope.messageType ?? 0]
      if (typeName) {
        try {
          const layer2Key = await deriveLayer2KeyForType(sessionKeys.sessionKeyBytes, typeName)
          envelope.encryptedPayload = await decryptLayer2(envelope.encryptedPayload, layer2Key)
        } catch (e) {
          throw new ProtocolCodecException(
            `Layer 2 payload decryption failed: ${(e as Error).message}`,
            e,
          )
        }
      }
    }
    return envelope
  } catch (e) {
    throw new ProtocolCodecException(
      `Protobuf deserialization failed: ${(e as Error).message}`,
      e,
    )
  }
}

// ============================================================
// 错误帧（无加密，用于握手阶段错误通知）
// ============================================================

/**
 * 解码未加密的 IMEnvelope（用于握手阶段的错误通知）
 *
 * <p>后端在密钥协商完成前可能发送未加密的错误消息。
 * 此函数直接反序列化 protobuf 字节，不进行解密和 HMAC 验证。</p>
 *
 * @param frame 未加密的 protobuf 字节
 * @returns IMEnvelope 消息对象
 */
export function decodeUnencrypted(frame: Uint8Array): IMEnvelopeMessage {
  try {
    const EnvelopeType = IMEnvelopeType()
    const message = EnvelopeType.decode(frame)
    return EnvelopeType.toObject(message, {
      longs: Number,
      enums: Number,
      bytes: Uint8Array,
      defaults: true,
    }) as IMEnvelopeMessage
  } catch (e) {
    throw new ProtocolCodecException(
      'Unencrypted protobuf deserialization failed',
      e,
    )
  }
}

/**
 * 编码未加密的 IMEnvelope（用于客户端在密钥协商前的请求）
 *
 * <p>注意：仅用于密钥协商前的特殊场景，常规消息必须使用 {@link encode} 加密。</p>
 *
 * @param envelope IMEnvelope 消息对象
 * @returns 未加密的 protobuf 字节
 */
export function encodeUnencrypted(envelope: IMEnvelopeMessage): Uint8Array {
  try {
    const EnvelopeType = IMEnvelopeType()
    const messageInstance = EnvelopeType.create(envelope)
    return EnvelopeType.encode(messageInstance).finish()
  } catch (e) {
    throw new ProtocolCodecException(
      'Unencrypted protobuf serialization failed',
      e,
    )
  }
}

// ============================================================
// 消息校验
// ============================================================

/**
 * 验证 IMEnvelope 的合法性
 *
 * <p>与后端 V2MessageHandler.validateEnvelope 对齐：
 *  - 验证消息 ID 格式
 *  - 验证发送者 ID 格式
 *  - 验证时间戳（防重放）</p>
 *
 * @param envelope IMEnvelope 消息对象
 * @throws ProtocolCodecException 如果校验失败
 */
export function validateEnvelope(envelope: IMEnvelopeMessage): void {
  const messageId = envelope.messageId
  if (!messageId || typeof messageId !== 'string') {
    throw new ProtocolCodecException(`Invalid message ID: ${messageId}`)
  }
  if (!/^[a-zA-Z0-9_\-]+$/.test(messageId)) {
    throw new ProtocolCodecException(`Invalid message ID format: ${messageId}`)
  }

  const senderId = envelope.senderId
  if (senderId && !/^[a-zA-Z0-9_\-]+$/.test(senderId)) {
    throw new ProtocolCodecException(`Invalid sender ID: ${senderId}`)
  }

  const timestamp = envelope.timestamp
  if (typeof timestamp === 'number' && !isTimestampValid(timestamp)) {
    throw new ProtocolCodecException(
      `Message timestamp out of window: ${timestamp} (current: ${Date.now()})`,
    )
  }
}

// ============================================================
// 工具函数
// ============================================================

/**
 * 检查 Uint8Array 是否为完整的二进制帧
 *
 * <p>用于流式数据场景，判断是否已收到完整帧。</p>
 *
 * @param data 接收到的数据
 * @returns true 如果是完整帧
 */
export function isCompleteFrame(data: Uint8Array): boolean {
  if (data.length < FRAME_HEADER_SIZE) return false
  const view = new DataView(data.buffer, data.byteOffset, data.byteLength)
  const totalLength = view.getUint32(OFFSET_TOTAL_LENGTH, false)
  return data.length >= totalLength
}

/**
 * 从缓冲区读取完整帧
 *
 * <p>用于流式数据场景，从缓冲区头部提取一个完整帧。
 * 如果数据不完整，返回 null。</p>
 *
 * @param buffer 累积缓冲区
 * @returns [完整帧字节, 剩余字节] 或 null（如果数据不完整）
 * @throws ProtocolCodecException 如果帧长度无效
 */
export function readFrame(
  buffer: Uint8Array,
): [Uint8Array, Uint8Array] | null {
  if (buffer.length < FRAME_HEADER_SIZE) return null

  const view = new DataView(buffer.buffer, buffer.byteOffset, buffer.byteLength)
  const totalLength = view.getUint32(OFFSET_TOTAL_LENGTH, false)

  if (totalLength < MIN_FRAME_SIZE || totalLength > MAX_FRAME_SIZE) {
    throw new ProtocolCodecException(`Invalid total length in frame: ${totalLength}`)
  }

  if (buffer.length < totalLength) return null

  const frame = buffer.slice(0, totalLength)
  const remaining = buffer.slice(totalLength)
  return [frame, remaining]
}
