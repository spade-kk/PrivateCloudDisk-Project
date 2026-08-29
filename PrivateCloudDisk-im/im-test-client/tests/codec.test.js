// ============================================================
// codec.test.js — 二进制帧编解码单元测试
// ============================================================
// 测试内容：
//   1. Protobuf 消息序列化/反序列化
//   2. AES-256-GCM 加密/解密
//   3. HMAC-SHA256 签名/验证
//   4. ECDH 密钥协商
//   5. 完整帧编解码（encode → decode 往返）
//   6. 帧格式校验（长度、HMAC 完整性）
//   7. 异常场景（篡改、截断、错误密钥）
// ============================================================

import { describe, it, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import {
  encodeMessage,
  decodeMessage,
  CommandType,
  MessageType,
  ConversationType,
  MessageStatus,
  DeviceType,
} from '../src/proto-loader.js'
import {
  generateEcdhKeyPair,
  computeSharedSecret,
  buildSessionKeys,
  deriveHmacKey,
  encryptAesGcm,
  decryptAesGcm,
  sign,
  verify,
  base64UrlEncode,
  base64UrlDecode,
} from '../src/crypto.js'
import {
  encode,
  decode,
  readFrame,
  ProtocolCodecError,
  FRAME_HEADER_SIZE,
  HMAC_SIZE,
  MIN_FRAME_SIZE,
  isCompleteFrame,
} from '../src/codec.js'
import crypto from 'node:crypto'

// ==================== 测试辅助 ====================

/** 生成测试用会话密钥 */
function createTestSessionKeys() {
  const alice = generateEcdhKeyPair()
  const bob = generateEcdhKeyPair()
  const sharedSecret = computeSharedSecret(alice.privateKey, bob.publicKey)
  return buildSessionKeys(sharedSecret, 1, Date.now() + 3600000)
}

/** 生成测试用 IMEnvelope */
function createTestEnvelope(overrides = {}) {
  return {
    version: 2,
    messageId: crypto.randomUUID().replace(/-/g, ''),
    command: CommandType.SEND_MESSAGE,
    messageType: MessageType.TEXT,
    senderId: 'test-user-a',
    receiverId: 'test-user-b',
    conversationId: 'conv-001',
    conversationType: ConversationType.PRIVATE,
    seq: 1,
    timestamp: Date.now(),
    status: MessageStatus.SENDING,
    senderDeviceType: DeviceType.DESKTOP,
    senderPlatform: 'node-test',
    senderAppVersion: '1.0.0',
    ...overrides,
  }
}

// ==================== Protobuf 测试 ====================

describe('Protobuf 消息序列化', () => {
  it('IMEnvelope 序列化/反序列化往返一致', () => {
    const envelope = createTestEnvelope()
    const bytes = encodeMessage('IMEnvelope', envelope)
    const decoded = decodeMessage('IMEnvelope', bytes)

    assert.equal(decoded.messageId, envelope.messageId)
    assert.equal(decoded.command, envelope.command)
    assert.equal(decoded.messageType, envelope.messageType)
    assert.equal(decoded.senderId, envelope.senderId)
    assert.equal(decoded.receiverId, envelope.receiverId)
    assert.equal(decoded.conversationId, envelope.conversationId)
    assert.equal(decoded.conversationType, envelope.conversationType)
    assert.equal(decoded.seq, envelope.seq)
    assert.equal(decoded.senderDeviceType, envelope.senderDeviceType)
  })

  it('TextPayload 序列化/反序列化往返一致', () => {
    const payload = {
      content: 'Hello, 世界! 🎉',
      isMarkdown: false,
      mentionedUserIds: ['user1', 'user2'],
      isAtAll: false,
    }
    const bytes = encodeMessage('TextPayload', payload)
    const decoded = decodeMessage('TextPayload', bytes)

    assert.equal(decoded.content, payload.content)
    assert.equal(decoded.isMarkdown, false)
    assert.deepEqual(decoded.mentionedUserIds, payload.mentionedUserIds)
  })

  it('空文本内容正确处理', () => {
    const payload = { content: '' }
    const bytes = encodeMessage('TextPayload', payload)
    const decoded = decodeMessage('TextPayload', bytes)
    assert.equal(decoded.content, '')
  })

  it('ReceiptPayload 序列化/反序列化往返一致', () => {
    const payload = {
      originalMessageId: 'msg-123456',
      conversationId: 'conv-001',
      senderId: 'user-a',
      receiverId: 'user-b',
      status: 2, // RECEIPT_PUSH_FAILED
      failCode: 1001,
      failReason: '未找到在线连接',
      receiptAt: 1780000000000,
    }
    const bytes = encodeMessage('ReceiptPayload', payload)
    const decoded = decodeMessage('ReceiptPayload', bytes)

    assert.equal(decoded.originalMessageId, payload.originalMessageId)
    assert.equal(decoded.conversationId, payload.conversationId)
    assert.equal(decoded.senderId, payload.senderId)
    assert.equal(decoded.receiverId, payload.receiverId)
    assert.equal(decoded.status, payload.status)
    assert.equal(decoded.failCode, payload.failCode)
    assert.equal(decoded.failReason, payload.failReason)
    assert.equal(decoded.receiptAt, payload.receiptAt)
  })
})

// ==================== 加密测试 ====================

describe('AES-256-GCM 加密', () => {
  it('加密/解密往返一致', () => {
    const key = crypto.randomBytes(32)
    const plaintext = Buffer.from('Hello, IM v2 Protocol!', 'utf-8')
    const encrypted = encryptAesGcm(plaintext, key)
    const decrypted = decryptAesGcm(encrypted.combined, key)
    assert.deepEqual(decrypted, plaintext)
  })

  it('不同 IV 每次加密结果不同', () => {
    const key = crypto.randomBytes(32)
    const plaintext = Buffer.from('same content', 'utf-8')
    const enc1 = encryptAesGcm(plaintext, key)
    const enc2 = encryptAesGcm(plaintext, key)
    assert.notDeepEqual(enc1.iv, enc2.iv)
    assert.notDeepEqual(enc1.ciphertext, enc2.ciphertext)
  })

  it('篡改密文导致解密失败', () => {
    const key = crypto.randomBytes(32)
    const plaintext = Buffer.from('important message', 'utf-8')
    const encrypted = encryptAesGcm(plaintext, key)

    // 篡改密文中间一个字节
    const tampered = Buffer.from(encrypted.combined)
    tampered[15] ^= 0xFF

    assert.throws(() => {
      decryptAesGcm(tampered, key)
    })
  })
})

describe('HMAC-SHA256 签名', () => {
  it('签名验证成功', () => {
    const key = crypto.randomBytes(32)
    const data = Buffer.from('test data for HMAC', 'utf-8')
    const signature = sign(data, key)
    assert.equal(signature.length, HMAC_SIZE)
    assert.ok(verify(data, signature, key))
  })

  it('篡改数据后验证失败', () => {
    const key = crypto.randomBytes(32)
    const data = Buffer.from('original data', 'utf-8')
    const signature = sign(data, key)

    const tampered = Buffer.from('tampered data', 'utf-8')
    assert.ok(!verify(tampered, signature, key))
  })

  it('错误密钥验证失败', () => {
    const key1 = crypto.randomBytes(32)
    const key2 = crypto.randomBytes(32)
    const data = Buffer.from('test data', 'utf-8')
    const signature = sign(data, key1)
    assert.ok(!verify(data, signature, key2))
  })
})

// ==================== ECDH 密钥协商测试 ====================

describe('ECDH 密钥协商', () => {
  it('双方计算的共享密钥一致', () => {
    const alice = generateEcdhKeyPair()
    const bob = generateEcdhKeyPair()

    const aliceShared = computeSharedSecret(alice.privateKey, bob.publicKey)
    const bobShared = computeSharedSecret(bob.privateKey, alice.publicKey)

    assert.deepEqual(aliceShared, bobShared)
    assert.equal(aliceShared.length, 32)
  })

  it('派生的 HMAC 密钥一致', () => {
    const alice = generateEcdhKeyPair()
    const bob = generateEcdhKeyPair()

    const aliceShared = computeSharedSecret(alice.privateKey, bob.publicKey)
    const bobShared = computeSharedSecret(bob.privateKey, alice.publicKey)

    const aliceHmacKey = deriveHmacKey(aliceShared)
    const bobHmacKey = deriveHmacKey(bobShared)

    assert.deepEqual(aliceHmacKey, bobHmacKey)
    assert.equal(aliceHmacKey.length, 32)
  })
})

// ==================== Base64 URL 测试 ====================

describe('Base64 URL 编码', () => {
  it('编码/解码往返一致', () => {
    const data = crypto.randomBytes(65) // 非对齐长度
    const encoded = base64UrlEncode(data)
    const decoded = base64UrlDecode(encoded)
    assert.deepEqual(decoded, data)
  })

  it('不包含 +, /, = 字符', () => {
    const data = Buffer.from('test+data/with=special', 'utf-8')
    const encoded = base64UrlEncode(data)
    assert.ok(!encoded.includes('+'))
    assert.ok(!encoded.includes('/'))
    assert.ok(!encoded.includes('='))
  })
})

// ==================== 完整帧编解码测试 ====================

describe('二进制帧编解码', () => {
  let sessionKeys

  beforeEach(() => {
    sessionKeys = createTestSessionKeys()
  })

  it('encode → decode 往返一致', () => {
    const envelope = createTestEnvelope()
    const frame = encode(envelope, sessionKeys)
    const decoded = decode(frame, sessionKeys)

    assert.equal(decoded.messageId, envelope.messageId)
    assert.equal(decoded.command, envelope.command)
    assert.equal(decoded.messageType, envelope.messageType)
    assert.equal(decoded.senderId, envelope.senderId)
    assert.equal(decoded.receiverId, envelope.receiverId)
    assert.equal(decoded.conversationId, envelope.conversationId)
    assert.equal(decoded.timestamp, envelope.timestamp)
  })

  it('帧格式正确（长度头 + HMAC）', () => {
    const envelope = createTestEnvelope()
    const frame = encode(envelope, sessionKeys)

    // 验证总长度
    const totalLength = frame.readUInt32BE(0)
    assert.equal(totalLength, frame.length)

    // 验证最小帧大小
    assert.ok(frame.length >= MIN_FRAME_SIZE)

    // 验证 HMAC 位置（最后 32 字节）
    const hmac = frame.subarray(frame.length - HMAC_SIZE)
    assert.equal(hmac.length, HMAC_SIZE)
  })

  it('帧头长度字段正确', () => {
    const envelope = createTestEnvelope()
    const frame = encode(envelope, sessionKeys)

    const totalLength = frame.readUInt32BE(0)
    const headerLength = frame.readUInt32BE(4)

    // headerLength = totalLength - FRAME_HEADER_SIZE - HMAC_SIZE
    assert.equal(headerLength, totalLength - FRAME_HEADER_SIZE - HMAC_SIZE)
  })

  it('篡改帧数据导致 HMAC 验证失败', () => {
    const envelope = createTestEnvelope()
    const frame = encode(envelope, sessionKeys)

    // 篡改加密数据部分
    const tampered = Buffer.from(frame)
    tampered[FRAME_HEADER_SIZE + 5] ^= 0xFF

    assert.throws(
      () => decode(tampered, sessionKeys),
      /HMAC verification failed/,
    )
  })

  it('使用错误密钥解码失败', () => {
    const envelope = createTestEnvelope()
    const frame = encode(envelope, sessionKeys)

    // 生成另一组会话密钥
    const otherKeys = createTestSessionKeys()

    assert.throws(
      () => decode(frame, otherKeys),
      /HMAC verification failed|Decryption failed/,
    )
  })

  it('帧过小抛出异常', () => {
    const tinyFrame = Buffer.alloc(5)
    assert.throws(
      () => decode(tinyFrame, sessionKeys),
      /Frame too small/,
    )
  })

  it('总长度不匹配抛出异常', () => {
    const envelope = createTestEnvelope()
    const frame = encode(envelope, sessionKeys)

    // 修改总长度字段
    const tampered = Buffer.from(frame)
    tampered.writeUInt32BE(frame.length + 100, 0)

    assert.throws(
      () => decode(tampered, sessionKeys),
      /Total length mismatch/,
    )
  })
})

// ==================== 流式帧读取测试 ====================

describe('流式帧读取', () => {
  let sessionKeys

  beforeEach(() => {
    sessionKeys = createTestSessionKeys()
  })

  it('完整帧返回 frame + remaining', () => {
    const envelope = createTestEnvelope()
    const frame = encode(envelope, sessionKeys)

    const result = readFrame(frame)
    assert.ok(result)
    const [extracted, remaining] = result
    assert.equal(extracted.length, frame.length)
    assert.equal(remaining.length, 0)
  })

  it('不完整帧返回 null', () => {
    const envelope = createTestEnvelope()
    const frame = encode(envelope, sessionKeys)

    const partial = frame.subarray(0, 10)
    const result = readFrame(partial)
    assert.equal(result, null)
  })

  it('多个帧拼接可逐个提取', () => {
    const env1 = createTestEnvelope({ messageId: 'msg0001' })
    const env2 = createTestEnvelope({ messageId: 'msg0002' })
    const frame1 = encode(env1, sessionKeys)
    const frame2 = encode(env2, sessionKeys)

    let buffer = Buffer.concat([frame1, frame2])

    // 提取第一帧
    const result1 = readFrame(buffer)
    assert.ok(result1)
    const [, remaining1] = result1
    assert.equal(remaining1.length, frame2.length)

    // 提取第二帧
    const result2 = readFrame(remaining1)
    assert.ok(result2)
    const [, remaining2] = result2
    assert.equal(remaining2.length, 0)
  })

  it('isCompleteFrame 正确判断', () => {
    const envelope = createTestEnvelope()
    const frame = encode(envelope, sessionKeys)

    assert.ok(isCompleteFrame(frame))
    assert.ok(!isCompleteFrame(frame.subarray(0, frame.length - 1)))
    assert.ok(!isCompleteFrame(frame.subarray(0, 5)))
  })
})

// ==================== 带 Payload 的完整消息测试 ====================

describe('完整消息编解码（含 Payload）', () => {
  let sessionKeys

  beforeEach(() => {
    sessionKeys = createTestSessionKeys()
  })

  it('文本消息 encode → decode 往返', () => {
    const textPayload = encodeMessage('TextPayload', {
      content: 'Hello, IM v2!',
      isMarkdown: false,
    })

    const envelope = createTestEnvelope({
      encryptedPayload: textPayload,
    })

    const frame = encode(envelope, sessionKeys)
    const decoded = decode(frame, sessionKeys)

    assert.ok(decoded.encryptedPayload)
    const decodedPayload = decodeMessage('TextPayload', decoded.encryptedPayload)
    assert.equal(decodedPayload.content, 'Hello, IM v2!')
  })

  it('心跳消息 encode → decode 往返', () => {
    const envelope = createTestEnvelope({
      command: CommandType.HEARTBEAT,
      messageType: MessageType.MSG_HEARTBEAT,
      receiverId: 'server',
      conversationId: '',
    })

    const frame = encode(envelope, sessionKeys)
    const decoded = decode(frame, sessionKeys)

    assert.equal(decoded.command, CommandType.HEARTBEAT)
    assert.equal(decoded.messageType, MessageType.MSG_HEARTBEAT)
  })
})
