// ============================================================
// connection.js — WebSocket 连接管理模块
// ============================================================
// 负责：
//   - WebSocket 连接建立与断开
//   - V2 协议握手流程（SERVER_HELLO → KEY_EXCHANGE → KEY_EXCHANGE_RESPONSE）
//   - ECDH 密钥协商（使用 Node.js crypto 模块）
//   - 心跳保活（定时发送 HEARTBEAT 帧，超时检测）
//   - 断线自动重连（指数退避）
//   - 二进制帧的接收缓冲与完整帧提取
//
// 与后端 V2AuthHandler 握手流程对齐：
//   1. WebSocket 连接（URL 携带 ?token=JWT）
//   2. 服务端发送 SERVER_HELLO（JSON 文本帧）
//   3. 客户端发送 KEY_EXCHANGE（JSON 文本帧）
//   4. 服务端发送 KEY_EXCHANGE_RESPONSE（JSON 文本帧）
//   5. 后续通信使用二进制帧（AES-256-GCM 加密 + HMAC 签名）
// ============================================================

import WebSocket from 'ws'
import crypto from 'node:crypto'
import {
  generateEcdhKeyPair,
  computeSharedSecret,
  buildSessionKeys,
  base64UrlEncode,
  base64UrlDecode,
} from './crypto.js'
import {
  encode,
  decode,
  readFrame,
  ProtocolCodecError,
  FRAME_HEADER_SIZE,
} from './codec.js'
import {
  encodeMessage,
  decodeMessage,
  CommandType,
  MessageType,
  MessageStatus,
  DeviceType,
  ConversationType,
} from './proto-loader.js'

// ==================== 连接状态枚举 ====================

export const ConnectionState = {
  IDLE: 'IDLE',
  CONNECTING: 'CONNECTING',
  WAITING_SERVER_HELLO: 'WAITING_SERVER_HELLO',
  KEY_EXCHANGING: 'KEY_EXCHANGING',
  AUTHENTICATED: 'AUTHENTICATED',
  DISCONNECTING: 'DISCONNECTING',
  DISCONNECTED: 'DISCONNECTED',
  RECONNECTING: 'RECONNECTING',
}

// ==================== IMConnection 类 ====================

/**
 * IM WebSocket 连接管理器
 *
 * 事件：
 *   - 'state' (state: ConnectionState) — 连接状态变更
 *   - 'authenticated' — 密钥协商完成，可发送加密消息
 *   - 'envelope' (envelope: object) — 收到解密后的 IMEnvelope
 *   - 'text' (text: string) — 收到文本帧（握手阶段 JSON 消息）
 *   - 'error' (error: Error) — 发生错误
 *   - 'close' (code: number, reason: string) — 连接关闭
 *   - 'heartbeat' (rttMs: number) — 心跳往返延迟
 */
export class IMConnection extends EventTarget {
  /**
   * @param {object} config - 配置对象
   * @param {object} logger - 日志对象
   */
  constructor(config, logger) {
    super()
    this.config = config
    this.log = logger
    this.state = ConnectionState.IDLE
    this.ws = null
    this.sessionKeys = null
    this.clientPrivateKey = null
    this.clientPublicKey = null
    this.connectionId = null
    this.userId = config.user.userId || ''

    // 心跳
    this.heartbeatTimer = null
    this.heartbeatTimeoutTimer = null
    this.lastHeartbeatSent = 0
    this.heartbeatPaused = false

    // 重连
    this.reconnectAttempts = 0
    this.reconnectTimer = null
    this.shouldReconnect = false

    // 接收缓冲区（用于拼接不完整的二进制帧）
    this.recvBuffer = Buffer.alloc(0)

    // 消息序列号
    this.seq = 0
  }

  // ==================== 状态管理 ====================

  /**
   * @private
   * 设置连接状态并触发事件
   */
  setState(newState) {
    const oldState = this.state
    this.state = newState
    if (oldState !== newState) {
      this.log.info(`连接状态: ${oldState} → ${newState}`)
      this._dispatch('state', { state: newState, oldState })
    }
  }

  /**
   * @private
   * 派发事件
   */
  _dispatch(type, detail = {}) {
    this.dispatchEvent(new CustomEvent(type, { detail }))
  }

  // ==================== 连接 ====================

  /**
   * 建立 WebSocket 连接
   * @param {string} url - WebSocket URL
   */
  connect(url) {
    this.setState(ConnectionState.CONNECTING)
    this.shouldReconnect = true

    this.log.info(`正在连接: ${url.replace(/\?token=.*/, '?token=***')}`)

    this.ws = new WebSocket(url, {
      perMessageDeflate: false,
      handshakeTimeout: 10000,
    })

    this.ws.binaryType = 'arraybuffer'

    this.ws.on('open', () => this._onWsOpen())
    this.ws.on('message', (data, isBinary) =>
      this._onWsMessage(data, isBinary),
    )
    this.ws.on('close', (code, reason) =>
      this._onWsClose(code, reason.toString()),
    )
    this.ws.on('error', (err) => this._onWsError(err))
  }

  /**
   * 主动断开连接
   * @param {string} reason - 断开原因
   */
  disconnect(reason = 'client disconnect') {
    this.shouldReconnect = false
    this.setState(ConnectionState.DISCONNECTING)
    this._stopHeartbeat()
    this._clearReconnectTimer()

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }

    if (this.ws) {
      this.ws.close(1000, reason)
      this.ws = null
    }

    this.sessionKeys = null
    this.clientPrivateKey = null
    this.clientPublicKey = null
    this.recvBuffer = Buffer.alloc(0)
    this.setState(ConnectionState.DISCONNECTED)
  }

  // ==================== WebSocket 事件处理 ====================

  /**
   * @private
   * WebSocket 连接建立
   */
  _onWsOpen() {
    this.log.success('WebSocket 连接已建立，等待服务端 SERVER_HELLO...')
    this.setState(ConnectionState.WAITING_SERVER_HELLO)
    this.reconnectAttempts = 0
  }

  /**
   * @private
   * 收到 WebSocket 消息
   */
  _onWsMessage(data, isBinary) {
    if (!isBinary) {
      // 文本帧 — 握手阶段的 JSON 消息
      const text = data.toString('utf-8')
      this._handleTextMessage(text)
      return
    }

    // 二进制帧 — 加密业务消息
    this._handleBinaryMessage(Buffer.from(data))
  }

  /**
   * @private
   * WebSocket 关闭
   */
  _onWsClose(code, reason) {
    this.log.warn(`连接关闭: code=${code}, reason=${reason}`)
    this._stopHeartbeat()
    this.sessionKeys = null
    this.setState(ConnectionState.DISCONNECTED)
    this._dispatch('close', { code, reason })

    if (this.shouldReconnect && this.config.reconnect.enabled) {
      this._scheduleReconnect()
    }
  }

  /**
   * @private
   * WebSocket 错误
   */
  _onWsError(err) {
    this.log.error(`WebSocket 错误: ${err.message}`)
    this._dispatch('error', { error: err })
  }

  // ==================== 握手流程 ====================

  /**
   * @private
   * 处理文本帧（握手阶段 JSON 消息）
   */
  _handleTextMessage(text) {
    let msg
    try {
      msg = JSON.parse(text)
    } catch (e) {
      this.log.error(`JSON 解析失败: ${text}`)
      return
    }

    switch (msg.type) {
      case 'SERVER_HELLO':
        this._handleServerHello(msg)
        break
      case 'KEY_EXCHANGE_RESPONSE':
        this._handleKeyExchangeResponse(msg)
        break
      case 'ERROR':
        this.log.error(`服务端错误: code=${msg.code}, message=${msg.message}`)
        this._dispatch('error', { error: new Error(msg.message), code: msg.code })
        break
      default:
        this.log.warn(`未知消息类型: ${msg.type}`)
        this._dispatch('text', { text, message: msg })
    }
  }

  /**
   * @private
   * 处理 SERVER_HELLO
   */
  _handleServerHello(msg) {
    this.log.info(`收到 SERVER_HELLO: connectionId=${msg.connectionId}`)
    this.connectionId = msg.connectionId

    // 生成 ECDH 密钥对
    const keyPair = generateEcdhKeyPair()
    this.clientPrivateKey = keyPair.privateKey
    this.clientPublicKey = keyPair.publicKey

    // 发送 KEY_EXCHANGE 请求
    const request = {
      type: 'KEY_EXCHANGE',
      clientPublicKey: base64UrlEncode(keyPair.publicKey),
    }

    this.setState(ConnectionState.KEY_EXCHANGING)
    this.ws.send(JSON.stringify(request))
    this.log.info('已发送 KEY_EXCHANGE 请求')
  }

  /**
   * @private
   * 处理 KEY_EXCHANGE_RESPONSE
   */
  _handleKeyExchangeResponse(msg) {
    this.log.info(
      `收到 KEY_EXCHANGE_RESPONSE: keyId=${msg.sessionKeyId}, algorithm=${msg.algorithm}`,
    )

    // 计算共享密钥
    const serverPublicKey = base64UrlDecode(msg.serverPublicKey)
    const sharedSecret = computeSharedSecret(this.clientPrivateKey, serverPublicKey)

    // 构建会话密钥
    this.sessionKeys = buildSessionKeys(
      sharedSecret,
      msg.sessionKeyId,
      msg.expireAt,
    )

    this.setState(ConnectionState.AUTHENTICATED)
    this.log.success('密钥协商完成，连接已认证')

    // 启动心跳
    this._startHeartbeat()

    // 触发已认证事件
    this._dispatch('authenticated', {
      connectionId: this.connectionId,
      keyId: msg.sessionKeyId,
    })
  }

  // ==================== 二进制消息处理 ====================

  /**
   * @private
   * 处理二进制帧
   */
  _handleBinaryMessage(data) {
    // 拼接到接收缓冲区
    this.recvBuffer = Buffer.concat([this.recvBuffer, data])

    // 循环提取完整帧
    while (this.recvBuffer.length >= FRAME_HEADER_SIZE) {
      let frame
      let remaining
      try {
        const result = readFrame(this.recvBuffer)
        if (!result) break // 数据不完整，等待更多数据
        ;[frame, remaining] = result
      } catch (e) {
        this.log.error(`帧读取失败: ${e.message}`)
        this.recvBuffer = Buffer.alloc(0)
        return
      }

      this.recvBuffer = remaining

      // 解码帧
      try {
        if (!this.sessionKeys) {
          this.log.error('收到二进制帧但密钥未协商，丢弃')
          continue
        }

        const envelope = decode(frame, this.sessionKeys)

        // 处理心跳响应
        if (envelope.command === CommandType.HEARTBEAT) {
          this._handleHeartbeatResponse()
          continue
        }

        // 触发事件
        this._dispatch('envelope', { envelope })
      } catch (e) {
        if (e instanceof ProtocolCodecError) {
          this.log.error(`帧解码失败: ${e.message}`)
        } else {
          this.log.error(`消息处理异常: ${e.message}`)
        }
      }
    }
  }

  // ==================== 消息发送 ====================

  /**
   * 生成下一个序列号
   * @returns {number}
   */
  nextSeq() {
    return ++this.seq
  }

  /**
   * 生成消息 ID
   * @returns {string}
   */
  generateMessageId() {
    return crypto.randomUUID().replace(/-/g, '')
  }

  /**
   * 发送 IMEnvelope（加密二进制帧）
   * @param {object} envelope - IMEnvelope 消息对象
   * @returns {boolean} 是否发送成功
   */
  sendEnvelope(envelope) {
    if (this.state !== ConnectionState.AUTHENTICATED) {
      this.log.error(`连接未认证，无法发送 (state=${this.state})`)
      return false
    }
    if (!this.sessionKeys) {
      this.log.error('会话密钥不可用')
      return false
    }
    if (this.ws?.readyState !== WebSocket.OPEN) {
      this.log.error('WebSocket 未连接')
      return false
    }

    try {
      const frame = encode(envelope, this.sessionKeys)
      this.ws.send(frame)
      return true
    } catch (e) {
      this.log.error(`发送失败: ${e.message}`)
      return false
    }
  }

  /**
   * 构建并发送文本消息
   * @param {string} receiverId - 接收者 ID
   * @param {string} conversationId - 会话 ID
   * @param {string} content - 文本内容
   * @param {object} [options] - 额外选项
   * @returns {object|null} 发送的 envelope（成功）或 null（失败）
   */
  sendTextMessage(receiverId, conversationId, content, options = {}) {
    const messageId = this.generateMessageId()
    const seq = this.nextSeq()

    // 构建 TextPayload
    const textPayload = encodeMessage('TextPayload', {
      content,
      isMarkdown: options.isMarkdown || false,
    })

    const envelope = {
      version: 2,
      messageId,
      command: CommandType.SEND_MESSAGE,
      messageType: MessageType.TEXT,
      senderId: this.userId,
      receiverId,
      conversationId,
      conversationType: options.conversationType || ConversationType.PRIVATE,
      seq,
      timestamp: Date.now(),
      status: MessageStatus.SENDING,
      encryptedPayload: textPayload,
      senderDeviceType: DeviceType.DESKTOP,
      senderPlatform: 'node',
      senderAppVersion: '1.0.0',
      ...options.extra,
    }

    if (this.sendEnvelope(envelope)) {
      return envelope
    }
    return null
  }

  /**
   * 发送心跳
   */
  sendHeartbeat() {
    if (this.heartbeatPaused) return

    const envelope = {
      version: 2,
      messageId: this.generateMessageId(),
      command: CommandType.HEARTBEAT,
      messageType: MessageType.MSG_HEARTBEAT,
      senderId: this.userId,
      receiverId: 'server',
      conversationId: '',
      timestamp: Date.now(),
    }

    this.lastHeartbeatSent = Date.now()
    this.sendEnvelope(envelope)

    // 设置心跳超时检测
    if (this.heartbeatTimeoutTimer) clearTimeout(this.heartbeatTimeoutTimer)
    this.heartbeatTimeoutTimer = setTimeout(() => {
      this.log.error('心跳超时，服务端未响应')
      this._forceReconnect()
    }, this.config.heartbeat.timeoutMs)
  }

  /**
   * 发送已读回执
   * @param {string} conversationId - 会话 ID
   * @param {string[]} messageIds - 已读消息 ID 列表
   */
  sendReadReceipt(conversationId, messageIds) {
    const receiptPayload = encodeMessage('ReadReceiptPayload', {
      messageIds,
      conversationId,
    })

    const envelope = {
      version: 2,
      messageId: this.generateMessageId(),
      command: CommandType.READ_MESSAGE,
      messageType: MessageType.READ_RECEIPT,
      senderId: this.userId,
      receiverId: 'server',
      conversationId,
      timestamp: Date.now(),
      encryptedPayload: receiptPayload,
    }

    this.sendEnvelope(envelope)
  }

  /**
   * 发送正在输入状态
   * @param {string} conversationId - 会话 ID
   * @param {boolean} isTyping - 是否正在输入
   */
  sendTyping(conversationId, isTyping) {
    const typingPayload = encodeMessage('TypingPayload', {
      conversationId,
      isTyping,
    })

    const envelope = {
      version: 2,
      messageId: this.generateMessageId(),
      command: CommandType.TYPING,
      messageType: MessageType.MSG_TYPING,
      senderId: this.userId,
      receiverId: 'server',
      conversationId,
      timestamp: Date.now(),
      encryptedPayload: typingPayload,
    }

    this.sendEnvelope(envelope)
  }

  // ==================== 心跳管理 ====================

  /**
   * @private
   * 启动心跳定时器
   */
  _startHeartbeat() {
    this._stopHeartbeat()
    const interval = this.config.heartbeat.intervalMs
    this.heartbeatTimer = setInterval(() => {
      this.sendHeartbeat()
    }, interval)
    this.log.info(`心跳已启动，间隔 ${interval}ms`)
  }

  /**
   * @private
   * 停止心跳
   */
  _stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
    if (this.heartbeatTimeoutTimer) {
      clearTimeout(this.heartbeatTimeoutTimer)
      this.heartbeatTimeoutTimer = null
    }
  }

  /**
   * @private
   * 处理心跳响应
   */
  _handleHeartbeatResponse() {
    if (this.heartbeatTimeoutTimer) {
      clearTimeout(this.heartbeatTimeoutTimer)
      this.heartbeatTimeoutTimer = null
    }
    const rtt = Date.now() - this.lastHeartbeatSent
    this.log.heartbeat(rtt)
    this._dispatch('heartbeat', { rttMs: rtt })
  }

  /**
   * 暂停心跳（用于测试心跳超时场景）
   */
  pauseHeartbeat() {
    this.heartbeatPaused = true
    this.log.warn('心跳已暂停（用于测试超时场景）')
  }

  /**
   * 恢复心跳
   */
  resumeHeartbeat() {
    this.heartbeatPaused = false
    this.log.info('心跳已恢复')
  }

  // ==================== 重连 ====================

  /**
   * @private
   * 安排重连
   */
  _scheduleReconnect() {
    const { maxAttempts, baseIntervalMs, maxIntervalMs } = this.config.reconnect

    if (this.reconnectAttempts >= maxAttempts) {
      this.log.error(`已达最大重连次数 ${maxAttempts}，停止重连`)
      this.setState(ConnectionState.DISCONNECTED)
      return
    }

    this.reconnectAttempts++
    const delay = Math.min(
      baseIntervalMs * Math.pow(2, this.reconnectAttempts - 1),
      maxIntervalMs,
    )

    this.setState(ConnectionState.RECONNECTING)
    this.log.info(
      `第 ${this.reconnectAttempts}/${maxAttempts} 次重连，${delay}ms 后执行...`,
    )

    this.reconnectTimer = setTimeout(() => {
      const url = this._buildUrl()
      this.connect(url)
    }, delay)
  }

  /**
   * @private
   * 强制重连
   */
  _forceReconnect() {
    this._stopHeartbeat()
    if (this.ws) {
      this.ws.removeAllListeners()
      this.ws.terminate()
      this.ws = null
    }
    this.sessionKeys = null
    if (this.shouldReconnect && this.config.reconnect.enabled) {
      this._scheduleReconnect()
    }
  }

  /**
   * @private
   * 清除重连定时器
   */
  _clearReconnectTimer() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  /**
   * @private
   * 构建 WebSocket URL
   */
  _buildUrl() {
    const { host, port, path: wsPath, useTls } = this.config.server
    const protocol = useTls ? 'wss' : 'ws'
    const token = this.config.auth.token
    return `${protocol}://${host}:${port}${wsPath}?token=${encodeURIComponent(token)}`
  }
}
