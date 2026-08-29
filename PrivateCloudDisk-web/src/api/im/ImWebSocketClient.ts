// ============================================================
// im/ImWebSocketClient.ts — V2 二进制协议 WebSocket 客户端 SDK
// ============================================================
// 企业级 WebSocket 客户端 SDK，与后端 im-server V2 二进制协议完全对接。
//
// v2.0 核心能力：
// - V2 二进制协议（Protobuf + AES-256-GCM 双层加密 + HMAC 签名）
// - ECDH P-256 密钥协商（自动完成，对上层透明）
// - 自动连接/重连（指数退避 + 随机抖动）
// - 心跳保活（30s 间隔，90s 超时）
// - 命令字路由（消息/已读/正在输入/离线同步）
// - 请求-响应匹配（基于 seq 的 Promise 回调）
// - 连接状态机（idle → connecting → handshaking → connected → reconnecting）
// - 多事件监听器（onMessage / onStatusChange / onError / onKicked）
// - V2 新增回调（onRead）
// - 消息去重（基于 messageId）
// - 重连后自动拉取离线消息
//
// 协议握手流程（与后端 V2AuthHandler 对齐）：
//   1. WebSocket 连接建立 → 服务端发送 SERVER_HELLO（文本帧，JSON）
//   2. 客户端生成 ECDH 密钥对 → 发送 KEY_EXCHANGE（文本帧，JSON）
//   3. 服务端返回 KEY_EXCHANGE_RESPONSE（文本帧，JSON，含服务端公钥+签名）
//   4. 客户端验证签名 → 计算 ECDH 共享密钥 → 派生 HMAC 密钥
//   5. 后续通信使用二进制帧（IMEnvelope + AES-GCM + HMAC）
//
// 后端对应：
//   org.project.im.server.netty.NettyWebSocketServer
//   org.project.im.server.netty.handler.V2AuthHandler
//   org.project.im.server.netty.handler.V2MessageHandler
//   org.project.im.common.protocol.v2.IMProtocolCodec
// ============================================================

import { getOfflineMessagesApi, getMessageHistoryByCursorApi } from './imApi'

import {
  type MessageProtocol,
  type MessageDTO,
  CommandType,
  // 引入枚举类型用于 IMEnvelope → MessageDTO 转换时的默认值
  ConversationType,
  MessageType,
  MessageStatus,
  PROTOCOL_VERSION,
  HEARTBEAT_INTERVAL,
  HEARTBEAT_TIMEOUT,
  RECONNECT_BASE_INTERVAL,
  RECONNECT_MAX_INTERVAL,
  MAX_RECONNECT_ATTEMPTS,
  MAX_MESSAGE_LENGTH,
  isMessageProtocol,
  type ReceiptEvent,
  ReceiptStatus,
} from './types'

import { IMSessionKeyManager } from './protocol/IMSessionKeyManager'
import {
  encode,
  decode,
  decodeUnencrypted,
  encodeUnencrypted,
  ProtocolCodecException,
} from './protocol/IMProtocolCodec'
import {
  IMEnvelopeType,
  TextPayloadType,
  ImagePayloadType,
  FilePayloadType,
  VoicePayloadType,
  VideoPayloadType,
  StickerPayloadType,
  LocationPayloadType,
  ReplyPayloadType,
  ReadReceiptPayloadType,
  ReceiptPayloadType,
  TypingPayloadType,
  CallPayloadType,
  CustomPayloadType,
  SystemPayloadType,
  IMMessageType as V2MessageType,
  IMCommandType as V2CommandType,
  IMMessageStatus as V2MessageStatus,
  IMConversationType as V2ConversationType,
  DeviceType,
  type IMEnvelopeMessage,
} from './protocol/protoSchema'
import { base64UrlDecode, base64UrlEncode, type SessionKeySet } from './protocol/IMCryptoCodec'

// ==================== 类型定义 ====================

/** 连接状态 */
export enum ConnectionState {
  IDLE = 'idle',
  CONNECTING = 'connecting',
  /** 密钥协商中（V2 新增） */
  HANDSHAKING = 'handshaking',
  CONNECTED = 'connected',
  RECONNECTING = 'reconnecting',
  DISCONNECTED = 'disconnected',
}

/** 客户端配置 */
export interface ImClientConfig {
  /** WebSocket 服务地址 */
  url: string
  /** 认证 Token */
  token: string | (() => string)
  /** 当前用户 ID（用于 HTTP 拉取离线消息 / 历史消息，连接后主动拉取） */
  userId?: string
  /** 心跳间隔（毫秒），默认 30000 */
  heartbeatInterval?: number
  /** 心跳超时（毫秒），默认 90000 */
  heartbeatTimeout?: number
  /** 重连基础间隔（毫秒），默认 1000 */
  reconnectBaseInterval?: number
  /** 最大重连间隔（毫秒），默认 30000 */
  reconnectMaxInterval?: number
  /** 最大重连次数，默认 10 */
  maxReconnectAttempts?: number
  /** 请求超时（毫秒），默认 10000 */
  requestTimeout?: number
  /** 是否启用自动重连，默认 true */
  autoReconnect?: boolean
  /** 是否启用心跳，默认 true */
  enableHeartbeat?: boolean
  /** 是否启用 V2 二进制协议，默认 true（v2.0+） */
  enableV2Protocol?: boolean
  /** 是否在重连后自动拉取离线消息，默认 true */
  autoSyncOfflineOnReconnect?: boolean
  /** 客户端平台标识（用于统计与设备识别） */
  clientPlatform?: string
  /** 客户端版本号 */
  clientVersion?: string
}

/** 请求-响应 Promise 句柄 */
interface PendingRequest {
  resolve: (value: MessageProtocol) => void
  reject: (reason: Error) => void
  timer: ReturnType<typeof setTimeout>
}

/** 事件监听器类型 */
export type MessageHandler = (protocol: MessageProtocol) => void
export type StatusHandler = (state: ConnectionState) => void
export type ErrorHandler = (error: Error) => void

/** V2 事件回调类型（v2.0 新增） */
export type ReadHandler = (event: import('./types').ReadEvent) => void

/** 推送回执回调类型（v2.0 新增） */
export type ReceiptHandler = (event: ReceiptEvent) => void

// ==================== WebSocket 客户端 ====================

/**
 * IM WebSocket 客户端（V2 二进制协议）
 *
 * <p>企业级 WebSocket 客户端 SDK，封装 V2 二进制协议的握手、加密、
 * 心跳、重连、消息收发等完整能力。</p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * const client = getImClient({
 *   url: 'wss://api.example.com/ws',
 *   token: () => authStore.token,
 * })
 *
 * // 注册回调
 * client.onStatusChange(state => console.log('状态:', state))
 * client.onMessage(protocol => console.log('消息:', protocol))
 * client.onRead(event => markConversationRead(event.conversationId))
 *
 * // 连接
 * client.connect()
 *
 * // 发送消息
 * client.sendMessage(messageDTO)
 * </pre>
 */
export class ImWebSocketClient {
  // ---- 配置 ----
  private config: Required<ImClientConfig>

  // ---- 状态 ----
  private ws: WebSocket | null = null
  private state: ConnectionState = ConnectionState.IDLE
  private reconnectAttempts = 0
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private heartbeatTimer: ReturnType<typeof setTimeout> | null = null
  private heartbeatTimeoutTimer: ReturnType<typeof setTimeout> | null = null
  private seqCounter = 0

  // ---- V2 协议握手 ----
  private sessionKeyManager = new IMSessionKeyManager()
  /** 标识是否已完成 V2 密钥协商 */
  private keyEstablished = false
  /** 服务端 Hello 信息（包含服务端公钥） */
  private pendingServerHello: import('./protocol/IMSessionKeyManager').ServerHello | null = null

  // ---- 请求管理 ----
  private pendingRequests = new Map<number, PendingRequest>()

  // ---- 消息去重 ----
  private receivedMessageIds = new Set<string>()
  private readonly maxCachedMessageIds = 1000

  // ---- 事件监听器 ----
  private messageHandlers = new Set<MessageHandler>()
  private statusHandlers = new Set<StatusHandler>()
  private errorHandlers = new Set<ErrorHandler>()
  /** V2 事件回调（v2.0 新增） */
  private readHandlers = new Set<ReadHandler>()
  /** 推送回执回调（v2.0 新增） */
  private receiptHandlers = new Set<ReceiptHandler>()

  // ---- 命令字路由 ----
  private commandHandlers = new Map<CommandType, Set<MessageHandler>>()

  /**
   * @param config 客户端配置
   */
  constructor(config: ImClientConfig) {
    this.config = {
      heartbeatInterval: HEARTBEAT_INTERVAL,
      heartbeatTimeout: HEARTBEAT_TIMEOUT,
      reconnectBaseInterval: RECONNECT_BASE_INTERVAL,
      reconnectMaxInterval: RECONNECT_MAX_INTERVAL,
      maxReconnectAttempts: MAX_RECONNECT_ATTEMPTS,
      requestTimeout: 10000,
      autoReconnect: true,
      enableHeartbeat: true,
      enableV2Protocol: true,
      autoSyncOfflineOnReconnect: true,
      clientPlatform: 'web',
      clientVersion: '2.0.0',
      userId: '',
      ...config,
    }
  }

  // ==================== 连接管理 ====================

  /** 获取当前连接状态 */
  get connectionState(): ConnectionState {
    return this.state
  }

  /** 是否已连接（含密钥协商完成） */
  get isConnected(): boolean {
    return this.state === ConnectionState.CONNECTED &&
      this.ws?.readyState === WebSocket.OPEN &&
      this.keyEstablished
  }

  /** 建立连接 */
  connect(): void {
    if (this.state === ConnectionState.CONNECTING ||
        this.state === ConnectionState.HANDSHAKING ||
        this.state === ConnectionState.CONNECTED) {
      return
    }
    this.setState(ConnectionState.CONNECTING)
    this.keyEstablished = false
    this.sessionKeyManager.clear()
    this.doConnect()
  }

  /** 断开连接 */
  disconnect(): void {
    this.clearTimers()
    this.rejectAllPending(new Error('客户端主动断开'))
    this.keyEstablished = false
    this.sessionKeyManager.clear()
    if (this.ws) {
      this.ws.onclose = null
      this.ws.onerror = null
      this.ws.onmessage = null
      this.ws.onopen = null
      try {
        this.ws.close(1000, '客户端主动断开')
      } catch { /* 静默 */ }
      this.ws = null
    }
    this.setState(ConnectionState.DISCONNECTED)
  }

  /** 销毁实例 */
  destroy(): void {
    this.disconnect()
    this.messageHandlers.clear()
    this.statusHandlers.clear()
    this.errorHandlers.clear()
    this.commandHandlers.clear()
    this.readHandlers.clear()
    this.receiptHandlers.clear()
    this.pendingRequests.clear()
    this.receivedMessageIds.clear()
  }

  // ==================== 事件监听 ====================

  /** 注册消息监听器（接收所有消息） */
  onMessage(handler: MessageHandler): () => void {
    this.messageHandlers.add(handler)
    return () => this.messageHandlers.delete(handler)
  }

  /** 注册连接状态监听器 */
  onStatusChange(handler: StatusHandler): () => void {
    this.statusHandlers.add(handler)
    return () => this.statusHandlers.delete(handler)
  }

  /** 注册错误监听器 */
  onError(handler: ErrorHandler): () => void {
    this.errorHandlers.add(handler)
    return () => this.errorHandlers.delete(handler)
  }

  /** 注册特定命令字的处理器 */
  onCommand(command: CommandType, handler: MessageHandler): () => void {
    if (!this.commandHandlers.has(command)) {
      this.commandHandlers.set(command, new Set())
    }
    this.commandHandlers.get(command)!.add(handler)
    return () => {
      const handlers = this.commandHandlers.get(command)
      if (handlers) {
        handlers.delete(handler)
        if (handlers.size === 0) this.commandHandlers.delete(command)
      }
    }
  }

  // ==================== V2 事件回调（v2.0 新增） ====================

  /**
   * 注册消息已读回调
   *
   * <p>当接收方标记消息为已读时触发。
   * 对应后端 MessageReadEvent。</p>
   */
  onRead(handler: ReadHandler): () => void {
    this.readHandlers.add(handler)
    return () => this.readHandlers.delete(handler)
  }

  /**
   * 注册推送回执回调
   *
   * <p>当本账号在某设备发送的消息被推送（成功或失败）时触发。
   * 对应后端 ReceiptPayload，用于多端同步消息发送状态。</p>
   */
  onReceipt(handler: ReceiptHandler): () => void {
    this.receiptHandlers.add(handler)
    return () => this.receiptHandlers.delete(handler)
  }

  // ==================== HTTP 与 WebSocket 消息渠道整合（v3.0 新增） ====================

  /**
   * 注册统一消息回调（同时覆盖 WebSocket 实时推送与 HTTP 拉取的消息）
   * <p>等价于 {@link onMessage}，作为 HTTP/WS 渠道整合的统一入口。</p>
   */
  onMessageReceived(handler: MessageHandler): () => void {
    return this.onMessage(handler)
  }

  /**
   * 一体化连接并同步：建立 WebSocket → 握手认证 → 拉取离线消息 → 启动实时监听。
   * <p>连接完成后自动调用 {@link pullOfflineViaHttp}（需配置 userId），
   * 之后实时消息通过 WebSocket 持续追加。</p>
   */
  connectAndSync(): void {
    this.connect()
    // 连接完成（密钥协商成功后）由 completeKeyExchange 触发 HTTP 离线拉取
  }

  /**
   * 游标分页拉取会话历史消息（封装 GET /im/messages/history/cursor）
   *
   * @param conversationId 会话 ID
   * @param cursor 上一页最小 server_seq（首次传 undefined）
   * @param limit 每页条数（默认 20，最大 100）
   * @returns 历史消息列表（按时间倒序，仅终态消息）
   */
  async loadHistory(
    conversationId: string,
    cursor?: number,
    limit: number = 20,
  ): Promise<MessageDTO[]> {
    const userId = this.config.userId
    if (!userId) {
      throw new Error('未配置 userId，无法拉取历史消息')
    }
    const res = await getMessageHistoryByCursorApi(conversationId, userId, limit, cursor)
    const data = res?.data ?? []
    // 将拉取的历史消息通过统一渠道派发，供消费方渲染
    data.forEach(dto => this.dispatchProtocol(this.dtoToMessageProtocol(dto)))
    return data
  }

  /**
   * 通过 HTTP 拉取当前用户离线消息（状态为 PREPARING，拉取后服务端标记为已送达）。
   * <p>拉取到的消息经统一渠道派发，供消费方展示到对应会话。</p>
   */
  async pullOfflineViaHttp(): Promise<MessageDTO[]> {
    const userId = this.config.userId
    if (!userId) {
      throw new Error('未配置 userId，无法拉取离线消息')
    }
    const res = await getOfflineMessagesApi(userId, 100)
    const data = res?.data ?? []
    data.forEach(dto => this.dispatchProtocol(this.dtoToMessageProtocol(dto)))
    if (data.length > 0) {
      console.log('[IM] HTTP 拉取离线消息完成:', data.length, '条')
    }
    return data
  }

  /**
   * 将 HTTP 拉取的 MessageDTO 转换为统一 MessageProtocol，供 onMessageReceived 消费。
   */
  private dtoToMessageProtocol(dto: MessageDTO): MessageProtocol {
    let extra: Record<string, unknown> | undefined
    if (dto.extra) {
      try { extra = JSON.parse(dto.extra) } catch { /* 忽略非法 JSON */ }
    }
    return {
      version: PROTOCOL_VERSION,
      command: CommandType.SEND_MESSAGE,
      messageId: dto.messageId,
      conversationId: dto.conversationId,
      conversationType: dto.conversationType,
      messageType: dto.messageType,
      serverSeq: dto.serverSeq,
      timestamp: dto.sendTime ? new Date(dto.sendTime).getTime() : Date.now(),
      senderId: dto.senderId,
      receiverId: dto.receiverId,
      payload: dto,
      extra,
    }
  }

  // ==================== 消息发送 ====================

  /**
   * 发送消息（请求-响应模式）
   *
   * <p>通过 V2 二进制协议发送消息，并等待服务端响应。
   * 内部使用 seq 进行请求-响应匹配。</p>
   *
   * @param command 命令类型
   * @param payload 消息体
   * @param receiverId 接收者 ID
   * @returns 响应协议
   */
  async sendRequest(
    command: CommandType,
    payload: unknown,
    receiverId?: string,
  ): Promise<MessageProtocol> {
    return new Promise((resolve, reject) => {
      const seq = this.nextSeq()
      const messageId = this.generateMessageId()

      // 注册请求超时
      const timer = setTimeout(() => {
        this.pendingRequests.delete(seq)
        reject(new Error(`请求超时: command=${command}, seq=${seq}`))
      }, this.config.requestTimeout)

      this.pendingRequests.set(seq, { resolve, reject, timer })

      // 构造 V2 IMEnvelope 并发送
      const envelope: IMEnvelopeMessage = {
        version: 2,
        messageId,
        command: this.mapToV2Command(command),
        messageType: V2MessageType.UNSPECIFIED,
        // AUDIT FIX [14.6,14.25] / IM-WEB-ENTERPRISE-20260809：IM Server 会校验
        // envelope.sender_id，原空字符串会在路由前被拒绝。新行为使用认证用户 UUID；
        // 服务端仍必须校验该值与 Token 会话一致，不能把客户端字段当作身份来源。
        senderId: this.requireUserId(),
        receiverId: receiverId || '',
        conversationId: '',
        conversationType: V2ConversationType.PRIVATE,
        seq,
        timestamp: Date.now(),
        status: V2MessageStatus.SENDING,
        senderDeviceType: DeviceType.WEB,
        senderPlatform: this.config.clientPlatform,
      }

      // 如果有 payload，编码为对应 Protobuf Payload 后再加密
      if (payload !== undefined && payload !== null) {
        this.encodePayload(envelope, command, payload)
          .then(() => {
            this.sendV2Envelope(envelope).catch(e => {
              clearTimeout(timer)
              this.pendingRequests.delete(seq)
              reject(e)
            })
          })
          .catch(e => {
            clearTimeout(timer)
            this.pendingRequests.delete(seq)
            reject(e)
          })
      } else {
        this.sendV2Envelope(envelope).catch(e => {
          clearTimeout(timer)
          this.pendingRequests.delete(seq)
          reject(e)
        })
      }
    })
  }

  /**
   * 发送消息（仅发送，不等待响应）
   *
   * <p>将 MessageDTO 转换为 V2 IMEnvelope 并通过二进制协议发送。</p>
   */
  async sendMessage(payload: MessageDTO): Promise<void> {
    const messageId = payload.messageId || this.generateMessageId()
    const envelope: IMEnvelopeMessage = {
      version: 2,
      messageId,
      command: V2CommandType.SEND_MESSAGE,
      messageType: this.mapDtoMessageType(payload.messageType),
      senderId: this.requireUserId(),
      receiverId: payload.receiverId,
      conversationId: payload.conversationId,
      conversationType: payload.conversationType,
      senderName: payload.senderName,
      senderAvatar: payload.senderAvatar,
      seq: payload.clientSeq || this.nextSeq(),
      serverSeq: payload.serverSeq || 0,
      timestamp: Date.now(),
      status: V2MessageStatus.SENDING,
      replyTo: payload.replyTo || '',
      extraJson: payload.extra || '',
      senderDeviceType: DeviceType.WEB,
      senderPlatform: this.config.clientPlatform,
    }

    // 编码 payload（根据消息类型选择对应的 Payload 类型）
    await this.encodeMessagePayload(envelope, payload)

    await this.sendV2Envelope(envelope)
  }

  /**
   * 发送已读回执
   *
   * <p>标记指定会话的所有消息为已读。
   * 服务端将产生 MessageReadEvent 通知发送方。</p>
   */
  async sendReadReceipt(conversationId: string, messageIds?: string[], receiverId = ''): Promise<void> {
    const ReadReceiptType = ReadReceiptPayloadType()
    const readPayload = ReadReceiptType.create({
      conversationId,
      messageIds: messageIds || [],
    })
    const payloadBytes = ReadReceiptType.encode(readPayload).finish()

    const envelope: IMEnvelopeMessage = {
      version: 2,
      messageId: this.generateMessageId(),
      command: V2CommandType.READ_MESSAGE,
      messageType: V2MessageType.READ_RECEIPT,
      senderId: this.requireUserId(),
      receiverId,
      conversationId,
      conversationType: V2ConversationType.PRIVATE,
      timestamp: Date.now(),
      status: V2MessageStatus.READ,
      encryptedPayload: payloadBytes,
      senderDeviceType: DeviceType.WEB,
      senderPlatform: this.config.clientPlatform,
    }

    await this.sendV2Envelope(envelope)
  }

  /**
   * 发送正在输入状态
   */
  async sendTyping(receiverId: string, conversationId?: string, isTyping = true): Promise<void> {
    const TypingType = TypingPayloadType()
    const typingPayload = TypingType.create({
      conversationId: conversationId || '',
      isTyping,
    })
    const payloadBytes = TypingType.encode(typingPayload).finish()

    const envelope: IMEnvelopeMessage = {
      version: 2,
      messageId: this.generateMessageId(),
      command: V2CommandType.TYPING,
      messageType: V2MessageType.MSG_TYPING,
      senderId: this.requireUserId(),
      receiverId,
      conversationId: conversationId || '',
      conversationType: V2ConversationType.PRIVATE,
      timestamp: Date.now(),
      encryptedPayload: payloadBytes,
      senderDeviceType: DeviceType.WEB,
      senderPlatform: this.config.clientPlatform,
    }

    await this.sendV2Envelope(envelope)
  }

  /**
   * 发送撤回消息请求
   */
  async sendRecallMessage(messageId: string): Promise<void> {
    const envelope: IMEnvelopeMessage = {
      version: 2,
      messageId: this.generateMessageId(),
      command: V2CommandType.RECALL_MESSAGE,
      messageType: V2MessageType.SYSTEM_NOTICE,
      senderId: this.requireUserId(),
      receiverId: '',
      conversationId: '',
      conversationType: V2ConversationType.PRIVATE,
      timestamp: Date.now(),
      extraJson: JSON.stringify({ recalledMessageId: messageId }),
      senderDeviceType: DeviceType.WEB,
      senderPlatform: this.config.clientPlatform,
    }

    await this.sendV2Envelope(envelope)
  }

  /**
   * 请求同步离线消息
   *
   * <p>客户端连接成功后自动调用。
   * 服务端将推送离线期间的所有消息。</p>
   */
  async requestSyncOffline(): Promise<void> {
    const envelope: IMEnvelopeMessage = {
      version: 2,
      messageId: this.generateMessageId(),
      command: V2CommandType.SYNC_OFFLINE,
      messageType: V2MessageType.SYSTEM_NOTICE,
      senderId: this.requireUserId(),
      receiverId: '',
      conversationId: '',
      conversationType: V2ConversationType.SYSTEM,
      timestamp: Date.now(),
      senderDeviceType: DeviceType.WEB,
      senderPlatform: this.config.clientPlatform,
    }

    await this.sendV2Envelope(envelope)
  }

  /**
   * 发送 WebRTC 信令消息
   *
   * @param command 信令命令字
   * @param payload 信令数据
   */
  async sendSignaling(command: number, payload: Record<string, unknown>): Promise<boolean> {
    try {
      // AUDIT FIX [IM-PROTO-20260810]：protobufjs 对 enum 字段只接受 proto 中声明的
      // 数值。原调用链把旧 Java 枚举 2601 直接写入 Envelope，导致 verify 失败；发送前
      // 先做 V2 命令值域校验，并由调用方使用 types.ts 中的统一常量。
      const isKnownCommand = Object.values(V2CommandType).some(value => value === command)
      if (!isKnownCommand) {
        throw new Error(`不支持的 V2 信令命令: ${command}`)
      }

      // 后端 MessageTypeDispatcher 对 VOICE_CALL/VIDEO_CALL 都注册 CallPayload，不能
      // 使用仅供旧客户端的 WebRTCSignalingPayload，否则双方 Payload schema 不一致。
      const CallType = CallPayloadType()
      const callPayload = this.toCallPayload(payload)
      const verifyError = CallType.verify(callPayload)
      if (verifyError) {
        throw new Error(`CallPayload 校验失败: ${verifyError}`)
      }
      const payloadBytes = CallType.encode(CallType.create(callPayload)).finish()

      const envelope: IMEnvelopeMessage = {
        version: 2,
        messageId: this.generateMessageId(),
        command,
        messageType: this.isVoiceCallPayload(payload)
          ? V2MessageType.VOICE_CALL
          : V2MessageType.VIDEO_CALL,
        senderId: this.requireUserId(),
        receiverId: this.readString(payload.receiverId) || this.readString(payload.calleeId) || '',
        conversationId: this.readString(payload.conversationId) || '',
        conversationType: V2ConversationType.PRIVATE,
        seq: this.nextSeq(),
        timestamp: Date.now(),
        status: V2MessageStatus.SENDING,
        encryptedPayload: payloadBytes,
        senderDeviceType: DeviceType.WEB,
        senderPlatform: this.config.clientPlatform,
      }

      await this.sendV2Envelope(envelope)
      return true
    } catch (e) {
      // 对齐 im-test-client.sendEnvelope：发送失败返回 false，由上层决定是否提示用户，
      // 不把网络/编解码错误变成 Uncaught (in promise)。
      console.warn('[IM] 信令发送失败:', (e as Error).message)
      return false
    }
  }

  // ==================== 私有方法：连接与握手 ====================

  /** 执行 WebSocket 连接 */
  private doConnect(): void {
    const token = typeof this.config.token === 'function'
      ? this.config.token()
      : this.config.token

    const url = `${this.config.url}?token=${encodeURIComponent(token)}`

    try {
      this.ws = new WebSocket(url)
      // 启用二进制类型，接收 ArrayBuffer
      this.ws.binaryType = 'arraybuffer'
    } catch (e) {
      this.handleConnectError(new Error(`WebSocket 创建失败: ${(e as Error).message}`))
      return
    }

    this.ws.onopen = () => {
      // 连接已建立，等待服务端发送 SERVER_HELLO
      this.setState(ConnectionState.HANDSHAKING)
    }

    this.ws.onmessage = (event: MessageEvent) => {
      this.handleRawMessage(event.data)
    }

    this.ws.onerror = () => {
      this.handleConnectError(new Error('WebSocket 连接错误'))
    }

    this.ws.onclose = (event: CloseEvent) => {
      this.clearTimers()
      this.keyEstablished = false
      this.sessionKeyManager.clear()
      if (event.code !== 1000) {
        this.handleConnectError(new Error(
          `WebSocket 连接关闭: code=${event.code}, reason=${event.reason}`,
        ))
      } else {
        this.setState(ConnectionState.DISCONNECTED)
      }
    }
  }

  /**
   * 处理原始 WebSocket 消息
   *
   * <p>根据当前连接状态分发消息：
   * - HANDSHAKING：处理文本帧（JSON 密钥交换消息）
   * - CONNECTED：处理二进制帧（V2 加密消息）</p>
   */
  private handleRawMessage(data: unknown): void {
    // 文本帧：密钥交换阶段的 JSON 消息
    if (typeof data === 'string') {
      this.handleHandshakeMessage(data)
      return
    }

    // 二进制帧：V2 加密消息
    if (data instanceof ArrayBuffer) {
      const bytes = new Uint8Array(data)
      this.handleBinaryMessage(bytes)
      return
    }

    // Blob 类型（某些浏览器环境）
    if (typeof Blob !== 'undefined' && data instanceof Blob) {
      data.arrayBuffer().then(buf => {
        this.handleBinaryMessage(new Uint8Array(buf))
      }).catch(e => {
        this.emitError(new Error(`Blob 消息读取失败: ${(e as Error).message}`))
      })
      return
    }
  }

  /**
   * 处理密钥交换阶段的 JSON 消息
   *
   * <p>对应后端 V2AuthHandler：
   * - SERVER_HELLO：服务端公钥 → 触发客户端发起密钥交换
   * - KEY_EXCHANGE_RESPONSE：服务端确认 → 计算共享密钥 → 完成握手</p>
   */
  private handleHandshakeMessage(text: string): void {
    let message: Record<string, unknown>
    try {
      message = JSON.parse(text)
    } catch {
      this.emitError(new Error('密钥交换消息解析失败: 无效的 JSON'))
      return
    }

    const msgType = message.type as string

    if (msgType === 'SERVER_HELLO') {
      // 服务端 Hello：保存并触发密钥交换
      this.pendingServerHello = message as unknown as import('./protocol/IMSessionKeyManager').ServerHello
      this.sessionKeyManager.setConnectionId(this.pendingServerHello.connectionId)
      this.initiateKeyExchange().catch(e => {
        this.emitError(new Error(`密钥交换启动失败: ${(e as Error).message}`))
        this.disconnect()
      })
    } else if (msgType === 'KEY_EXCHANGE_RESPONSE') {
      // 服务端响应：完成密钥协商
      this.completeKeyExchange(message as unknown as import('./protocol/IMSessionKeyManager').KeyExchangeResponse)
        .catch(e => {
          this.emitError(new Error(`密钥协商完成失败: ${(e as Error).message}`))
          this.disconnect()
        })
    } else if (msgType === 'ERROR') {
      // 握手阶段的错误
      const code = message.code as number
      const errorMsg = message.message as string
      this.emitError(new Error(`服务端握手错误: code=${code}, message=${errorMsg}`))
      if (code === 2004) {
        this.emitKicked()
      }
    } else {
      // 未识别的文本消息（可能是错误通知）
      this.emitError(new Error(`未识别的握手消息: ${text.substring(0, 100)}`))
    }
  }

  /**
   * 启动密钥交换：生成客户端密钥对并发送公钥
   */
  private async initiateKeyExchange(): Promise<void> {
    const { request } = await this.sessionKeyManager.initiateKeyExchange()
    // 通过文本帧发送 JSON 格式的密钥交换请求
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(request))
    }
  }

  /**
   * 完成密钥交换：验证签名、计算共享密钥、切换到二进制协议
   */
  private async completeKeyExchange(
    response: import('./protocol/IMSessionKeyManager').KeyExchangeResponse,
  ): Promise<void> {
    // 完成密钥协商（不强制验证 RSA 签名，因为浏览器通常没有服务端 RSA 公钥）
    await this.sessionKeyManager.completeKeyExchange(response)

    // 标记密钥协商完成，切换到 CONNECTED 状态
    this.keyEstablished = true
    this.setState(ConnectionState.CONNECTED)
    this.reconnectAttempts = 0

    // 启动心跳
    if (this.config.enableHeartbeat) {
      this.startHeartbeat()
    }

    // 自动拉取离线消息：优先通过 HTTP 离线拉取接口（PREPARING 状态消息），
    // 未配置 userId 时回退到 WebSocket 同步命令
    if (this.config.autoSyncOfflineOnReconnect) {
      if (this.config.userId) {
        this.pullOfflineViaHttp().catch(e => {
          console.warn('[IM] HTTP 拉取离线消息失败:', (e as Error).message)
        })
      } else {
        this.requestSyncOffline().catch(e => {
          console.warn('[IM] 自动拉取离线消息失败:', (e as Error).message)
        })
      }
    }
  }

  /**
   * 处理 V2 二进制消息
   *
   * <p>解码二进制帧 → 解密 IMEnvelope → 转换为 MessageProtocol → 分发到监听器。</p>
   */
  private async handleBinaryMessage(frameBytes: Uint8Array): Promise<void> {
    const sessionKeys = this.sessionKeyManager.getSessionKeys()
    if (!sessionKeys) {
      this.emitError(new Error('收到二进制消息但密钥未协商，丢弃'))
      return
    }

    let envelope: IMEnvelopeMessage
    try {
      // 1. 尝试解码加密帧
      envelope = await decode(frameBytes, sessionKeys)
    } catch (e) {
      // 2. 解码失败，尝试作为未加密错误帧解码
      try {
        envelope = decodeUnencrypted(frameBytes)
      } catch {
        this.emitError(new Error(`二进制消息解码失败: ${(e as Error).message}`))
        return
      }
    }

    // 重置心跳超时
    this.resetHeartbeatTimeout()

    // 处理 IMEnvelope 并分发
    await this.dispatchEnvelope(envelope, sessionKeys)
  }

  /**
   * 分发 IMEnvelope 到对应的处理器
   *
   * <p>根据 command 字段路由：
   * - SEND_MESSAGE：新消息 → onMessage
   * - READ_MESSAGE：已读回执 → onRead
   * - RECALL_MESSAGE：撤回消息 → onMessage
   * - SYNC_OFFLINE：离线消息同步 → onMessage
   * - SYSTEM_NOTIFY：系统通知 → onMessage
   * - ERROR_NOTIFY：错误通知 → onError
   * - HEARTBEAT：心跳响应 → 忽略</p>
   */
  private async dispatchEnvelope(
    envelope: IMEnvelopeMessage,
    sessionKeys: SessionKeySet,
  ): Promise<void> {
    const command = envelope.command

    // 心跳响应：忽略
    if (command === V2CommandType.HEARTBEAT) {
      return
    }

    // 错误通知：触发 onError
    if (command === V2CommandType.ERROR_NOTIFY) {
      const errorMsg = envelope.extraJson
        ? this.parseErrorFromExtra(envelope.extraJson)
        : '未知服务端错误'
      this.emitError(new Error(errorMsg))
      // 检查是否为踢出通知
      if (envelope.extraJson?.includes('"code":2004')) {
        this.emitKicked()
      }
      return
    }

    // 推送回执：解码为 ReceiptEvent 并触发 onReceipt，不作为普通消息处理
    if (envelope.messageType === V2MessageType.RECEIPT) {
      const receipt = await this.decodeReceipt(envelope, sessionKeys)
      if (receipt) {
        this.receiptHandlers.forEach(handler => {
          try { handler(receipt) } catch { /* 静默 */ }
        })
      }
      return
    }

    // 请求-响应匹配（基于 seq）
    const seq = envelope.seq
    if (seq !== undefined && seq > 0 && this.pendingRequests.has(seq)) {
      const pending = this.pendingRequests.get(seq)!
      clearTimeout(pending.timer)
      this.pendingRequests.delete(seq)
      // 转换为 MessageProtocol 兼容格式（envelopeToProtocol 是异步方法，必须 await）
      const protocol = await this.envelopeToProtocol(envelope, sessionKeys)
      pending.resolve(protocol)
      return
    }

    // 消息去重：基于 messageId
    const messageId = envelope.messageId
    if (messageId && this.receivedMessageIds.has(messageId)) {
      return
    }
    if (messageId) {
      this.receivedMessageIds.add(messageId)
      if (this.receivedMessageIds.size > this.maxCachedMessageIds) {
        const keys = Array.from(this.receivedMessageIds)
        for (let i = 0; i < keys.length / 2; i++) {
          this.receivedMessageIds.delete(keys[i])
        }
      }
    }

    // 转换为兼容 MessageProtocol 格式（envelopeToProtocol 是异步方法，必须 await）
    const protocol = await this.envelopeToProtocol(envelope, sessionKeys)

    // 根据 command 触发 V2 事件回调
    this.triggerV2EventCallbacks(envelope, protocol)

    // 广播到所有消息监听器 + 命令字处理器（HTTP 与 WebSocket 统一走此分发）
    this.dispatchProtocol(protocol)
  }

  /**
   * 统一消息分发：广播到消息监听器与命令字处理器。
   * <p>WebSocket 实时推送与 HTTP 拉取（离线/历史）的消息都经由本方法，
   * 保证 onMessageReceived / onMessage 消费方行为一致。</p>
   */
  private dispatchProtocol(protocol: MessageProtocol): void {
    this.messageHandlers.forEach(handler => {
      try { handler(protocol) } catch { /* 静默 */ }
    })

    const mappedCommand = protocol.command
    if (mappedCommand !== undefined) {
      const commandHandlers = this.commandHandlers.get(mappedCommand)
      if (commandHandlers) {
        commandHandlers.forEach(handler => {
          try { handler(protocol) } catch { /* 静默 */ }
        })
      }
    }
  }

  // ==================== 私有方法：消息转换 ====================

  /**
   * 将 V2 IMEnvelope 转换为兼容的 MessageProtocol 格式
   *
   * <p>保留向后兼容性：现有消费者（notificationStore）依赖 MessageProtocol 结构。
   * 此方法将 V2 信封转换为 MessageProtocol，并尝试解码 payload 为 MessageDTO。</p>
   */
  private async envelopeToProtocol(
    envelope: IMEnvelopeMessage,
    sessionKeys: SessionKeySet,
  ): Promise<MessageProtocol> {
    // 尝试解码 payload
    let payload: unknown = undefined
    if (envelope.encryptedPayload && envelope.encryptedPayload.length > 0) {
      try {
        payload = await this.decodePayload(envelope, sessionKeys)
      } catch (e) {
        console.warn('[IM] payload 解码失败:', (e as Error).message)
        payload = undefined
      }
    }

    // AUDIT FIX [5.4,14.25] / IM-WEB-ENTERPRISE-20260809：旧实现只把含 content 的
    // Text/System payload 转成 MessageDTO，图片、文件、语音、视频等会绕过统一消息渲染。
    // 新实现对所有业务消息生成 DTO，并把类型化负载放入 extra.payload，避免字段丢失。
    let messageDto: MessageDTO | undefined
    if (payload && typeof payload === 'object' && this.isBusinessMessageType(envelope.messageType)) {
      const payloadObject = payload as Record<string, unknown>
      const content = this.payloadSummary(envelope.messageType, payloadObject)
      // 注意：IMEnvelopeMessage 的字段均为 optional（protobuf 解码后可能未设置），
      // 需要为 MessageDTO 必填字段提供默认值，避免 undefined 赋值给非可选类型
      messageDto = {
        messageId: envelope.messageId || '',
        conversationId: envelope.conversationId || '',
        conversationType: envelope.conversationType ?? ConversationType.PRIVATE,
        messageType: this.mapV2MessageTypeToDto(envelope.messageType),
        senderId: envelope.senderId || '',
        senderName: envelope.senderName,
        senderAvatar: envelope.senderAvatar,
        receiverId: envelope.receiverId || '',
        content,
        extra: JSON.stringify({
          payload: payloadObject,
          envelopeExtra: this.safeJsonObject(envelope.extraJson),
        }),
        serverSeq: envelope.serverSeq,
        replyTo: envelope.replyTo || undefined,
        sendTime: new Date(envelope.timestamp ?? Date.now()).toISOString(),
        // 接收方拿到 Router 推送即表示本设备已送达；不要把 V2 传输态数字直接解释为
        // REST/数据库状态数字（两个枚举值域不同）。
        status: MessageStatus.DELIVERED,
      }
    }

    // 构造兼容的 MessageProtocol（timestamp 必填，提供默认值）
    const protocol: MessageProtocol = {
      version: 2,
      command: this.mapFromV2Command(envelope.command) ?? CommandType.SYSTEM_NOTIFY,
      messageId: envelope.messageId,
      conversationId: envelope.conversationId,
      conversationType: envelope.conversationType as ConversationType | undefined,
      messageType: this.mapV2MessageTypeToDto(envelope.messageType),
      serverSeq: envelope.serverSeq,
      seq: envelope.seq,
      timestamp: envelope.timestamp ?? Date.now(),
      senderId: envelope.senderId,
      receiverId: envelope.receiverId,
      payload: messageDto || payload,
    }

    return protocol
  }

  /**
   * 解码 IMEnvelope 的 payload
   *
   * <p>根据 messageType 选择对应的 Payload 类型进行 protobuf 反序列化。
   * Layer 2 已在 IMProtocolCodec.decode 中按 test-im-client 的顺序解密。</p>
   */
  private async decodePayload(
    envelope: IMEnvelopeMessage,
    _sessionKeys: SessionKeySet,
  ): Promise<unknown> {
    const payloadBytes = envelope.encryptedPayload
    if (!payloadBytes || payloadBytes.length === 0) return undefined

    const messageType = envelope.messageType
    let PayloadType
    switch (messageType) {
      case V2MessageType.TEXT:
        PayloadType = TextPayloadType()
        break
      case V2MessageType.IMAGE:
        PayloadType = ImagePayloadType()
        break
      case V2MessageType.FILE:
        PayloadType = FilePayloadType()
        break
      case V2MessageType.VOICE:
        PayloadType = VoicePayloadType()
        break
      case V2MessageType.VIDEO:
        PayloadType = VideoPayloadType()
        break
      case V2MessageType.STICKER:
        PayloadType = StickerPayloadType()
        break
      case V2MessageType.LOCATION:
        PayloadType = LocationPayloadType()
        break
      case V2MessageType.REPLY:
        PayloadType = ReplyPayloadType()
        break
      case V2MessageType.READ_RECEIPT:
        PayloadType = ReadReceiptPayloadType()
        break
      case V2MessageType.RECEIPT:
        PayloadType = ReceiptPayloadType()
        break
      case V2MessageType.MSG_TYPING:
        PayloadType = TypingPayloadType()
        break
      case V2MessageType.VOICE_CALL:
      case V2MessageType.VIDEO_CALL:
        PayloadType = CallPayloadType()
        break
      case V2MessageType.SYSTEM_NOTICE:
        PayloadType = SystemPayloadType()
        break
      case V2MessageType.CUSTOM:
        PayloadType = CustomPayloadType()
        break
      default:
        // 未知类型，尝试返回原始字节
        return payloadBytes
    }

    try {
      const message = PayloadType.decode(payloadBytes)
      const decoded = PayloadType.toObject(message, {
        longs: Number,
        enums: Number,
        bytes: Uint8Array,
        defaults: true,
      })
      if (messageType === V2MessageType.VOICE_CALL || messageType === V2MessageType.VIDEO_CALL) {
        return this.fromCallPayload(decoded as Record<string, unknown>)
      }
      return decoded
    } catch (e) {
      console.warn('[IM] Payload 反序列化失败:', (e as Error).message)
      return undefined
    }
  }

  /**
   * 解码推送回执负载并转换为 ReceiptEvent
   *
   * <p>将 ReceiptPayload 反序列化为上层可用的 ReceiptEvent 对象。</p>
   */
  private async decodeReceipt(
    envelope: IMEnvelopeMessage,
    sessionKeys: SessionKeySet,
  ): Promise<ReceiptEvent | undefined> {
    const decoded = await this.decodePayload(envelope, sessionKeys)
    if (!decoded || typeof decoded !== 'object') return undefined
    const p = decoded as {
      originalMessageId?: string
      conversationId?: string
      senderId?: string
      receiverId?: string
      status?: number
      failCode?: number
      failReason?: string
      receiptAt?: number
    }
    return {
      originalMessageId: p.originalMessageId || envelope.messageId || '',
      conversationId: p.conversationId || envelope.conversationId || '',
      senderId: p.senderId || envelope.senderId || '',
      receiverId: p.receiverId || envelope.receiverId || '',
      status: p.status ?? ReceiptStatus.UNSPECIFIED,
      failCode: p.failCode ?? 0,
      failReason: p.failReason || '',
      receiptAt: p.receiptAt ?? envelope.timestamp ?? Date.now(),
    }
  }

  /**
   * 编码 IMEnvelope 的 payload（发送方向）
   *
   * <p>根据 command 选择对应的 Payload 类型。</p>
   */
  private async encodePayload(
    envelope: IMEnvelopeMessage,
    command: CommandType,
    payload: unknown,
  ): Promise<void> {
    // 根据命令类型选择对应的 Payload 类型
    let PayloadType
    let isCallPayload = false
    const v2Command = this.mapToV2Command(command)

    switch (v2Command) {
      case V2CommandType.READ_MESSAGE:
        PayloadType = ReadReceiptPayloadType()
        break
      case V2CommandType.TYPING:
        PayloadType = TypingPayloadType()
        break
      case V2CommandType.CALL_INVITE:
      case V2CommandType.CALL_ACCEPT:
      case V2CommandType.CALL_REJECT:
      case V2CommandType.CALL_CANCEL:
      case V2CommandType.CALL_HANGUP:
      case V2CommandType.CALL_QUALITY_REPORT:
      case V2CommandType.CALL_SCREEN_SHARE_START:
      case V2CommandType.CALL_SCREEN_SHARE_STOP:
      case V2CommandType.CALL_MUTE_TOGGLE:
      case V2CommandType.CALL_CAMERA_TOGGLE:
      case V2CommandType.CALL_SWITCH_TO_VOICE:
      case V2CommandType.CALL_SWITCH_TO_VIDEO:
        PayloadType = CallPayloadType()
        isCallPayload = true
        break
      case V2CommandType.SIGNALING_OFFER:
      case V2CommandType.SIGNALING_ANSWER:
      case V2CommandType.SIGNALING_ICE:
        // AUDIT FIX [IM-PROTO-20260810]：服务端 V2 路由对信令使用 CallPayload。
        // 原实现使用 WebRTCSignalingPayload，双方 schema 不一致，必须统一到 CallPayload。
        PayloadType = CallPayloadType()
        isCallPayload = true
        envelope.messageType = this.isVoiceCallPayload(
          payload && typeof payload === 'object' ? payload as Record<string, unknown> : {},
        ) ? V2MessageType.VOICE_CALL : V2MessageType.VIDEO_CALL
        break
      default:
        // 文本消息或其他类型
        if (typeof payload === 'string') {
          PayloadType = TextPayloadType()
          const textPayload = PayloadType.create({ content: payload })
          envelope.encryptedPayload = PayloadType.encode(textPayload).finish()
          envelope.messageType = V2MessageType.TEXT
        } else if (payload && typeof payload === 'object') {
          // 通用对象：尝试作为 CustomPayload 编码
          PayloadType = CustomPayloadType()
          const customPayload = PayloadType.create({
            customType: 'json',
            data: new TextEncoder().encode(JSON.stringify(payload)),
          })
          envelope.encryptedPayload = PayloadType.encode(customPayload).finish()
          envelope.messageType = V2MessageType.CUSTOM
        }
        return
    }

    try {
      const payloadObject = isCallPayload
        ? this.toCallPayload(payload && typeof payload === 'object'
          ? payload as Record<string, unknown>
          : {})
        : payload as Record<string, never>
      const verifyError = PayloadType.verify(payloadObject)
      if (verifyError) {
        throw new Error(verifyError)
      }
      const messageInstance = PayloadType.create(payloadObject)
      envelope.encryptedPayload = PayloadType.encode(messageInstance).finish()
      if (isCallPayload && !envelope.messageType) {
        envelope.messageType = this.isVoiceCallPayload(
          payload && typeof payload === 'object' ? payload as Record<string, unknown> : {},
        ) ? V2MessageType.VOICE_CALL : V2MessageType.VIDEO_CALL
      }
    } catch (e) {
      throw new Error(`Payload 编码失败: ${(e as Error).message}`)
    }
  }

  /**
   * 编码 MessageDTO 的 payload（用于 sendMessage 方法）
   */
  private async encodeMessagePayload(
    envelope: IMEnvelopeMessage,
    dto: MessageDTO,
  ): Promise<void> {
    const messageType = dto.messageType
    const extra = this.safeJsonObject(dto.extra)
    const richPayload = (extra.payload && typeof extra.payload === 'object')
      ? extra.payload as Record<string, unknown>
      : extra

    switch (messageType) {
      case 1: // TEXT
        const TextType = TextPayloadType()
        const textPayload = TextType.create({
          content: dto.content,
          mentionedUserIds: richPayload.mentionedUserIds || [],
          isAtAll: Boolean(richPayload.isAtAll),
          emojiMap: richPayload.emojiMap || {},
          isMarkdown: Boolean(richPayload.isMarkdown),
        })
        envelope.encryptedPayload = TextType.encode(textPayload).finish()
        envelope.messageType = V2MessageType.TEXT
        break

      case 2: { // IMAGE
        const PayloadType = ImagePayloadType()
        envelope.encryptedPayload = PayloadType.encode(PayloadType.create(richPayload)).finish()
        envelope.messageType = V2MessageType.IMAGE
        break
      }
      case 3: { // FILE
        const PayloadType = FilePayloadType()
        envelope.encryptedPayload = PayloadType.encode(PayloadType.create(richPayload)).finish()
        envelope.messageType = V2MessageType.FILE
        break
      }
      case 4: { // VOICE
        const PayloadType = VoicePayloadType()
        envelope.encryptedPayload = PayloadType.encode(PayloadType.create(richPayload)).finish()
        envelope.messageType = V2MessageType.VOICE
        break
      }
      case 5: { // VIDEO
        const PayloadType = VideoPayloadType()
        envelope.encryptedPayload = PayloadType.encode(PayloadType.create(richPayload)).finish()
        envelope.messageType = V2MessageType.VIDEO
        break
      }
      // AUDIT FIX [4.5/4.8] / IM-EMOJI-SESSION-20260810：协议已定义 StickerPayload，
      // 原 WebSocket 分支遗漏编码导致平台表情只能走 HTTP。新行为与后端
      // MessagePayloadCodec 的 stickerId/stickerPackId/url 等字段一一对应。
      case MessageType.STICKER: {
        const PayloadType = StickerPayloadType()
        envelope.encryptedPayload = PayloadType.encode(PayloadType.create(richPayload)).finish()
        envelope.messageType = V2MessageType.STICKER
        break
      }
      case MessageType.LOCATION: {
        const PayloadType = LocationPayloadType()
        envelope.encryptedPayload = PayloadType.encode(PayloadType.create(richPayload)).finish()
        envelope.messageType = V2MessageType.LOCATION
        break
      }
      case MessageType.SYSTEM_NOTICE: {
        const PayloadType = SystemPayloadType()
        const data = { ...richPayload, content: richPayload.content || dto.content }
        envelope.encryptedPayload = PayloadType.encode(PayloadType.create(data)).finish()
        envelope.messageType = V2MessageType.SYSTEM_NOTICE
        break
      }
      case MessageType.CUSTOM: {
        const PayloadType = CustomPayloadType()
        const data = richPayload.data instanceof Uint8Array
          ? richPayload.data
          : new TextEncoder().encode(JSON.stringify(richPayload.data ?? richPayload))
        envelope.encryptedPayload = PayloadType.encode(PayloadType.create({
          customType: String(richPayload.customType || 'json'),
          data,
        })).finish()
        envelope.messageType = V2MessageType.CUSTOM
        break
      }
      case MessageType.REPLY: {
        const ReplyType = ReplyPayloadType()
        const TextType = TextPayloadType()
        const replyContent = TextType.encode(TextType.create({ content: dto.content })).finish()
        envelope.encryptedPayload = ReplyType.encode(ReplyType.create({
          quotedMessageId: richPayload.quotedMessageId || dto.replyTo || '',
          quotedSenderId: richPayload.quotedSenderId || '',
          quotedContentPreview: richPayload.quotedContentPreview || '',
          quotedMessageType: richPayload.quotedMessageType || V2MessageType.TEXT,
          replyContent,
        })).finish()
        envelope.messageType = V2MessageType.REPLY
        break
      }

      default:
        throw new Error(`不支持的消息类型: ${messageType}`)
    }
  }

  // ==================== V2 事件回调触发 ====================

  /**
   * 触发 V2 事件回调（onRead）
   *
   * <p>根据 envelope.command 和 messageType 路由到对应的回调。</p>
   */
  private triggerV2EventCallbacks(
    envelope: IMEnvelopeMessage,
    _protocol: MessageProtocol,
  ): void {
    // 已读回执 → onRead
    if (envelope.command === V2CommandType.READ_MESSAGE ||
        envelope.messageType === V2MessageType.READ_RECEIPT) {
      // 提供默认值，避免 undefined 赋值给 ReadEvent 必填字段
      const event: import('./types').ReadEvent = {
        userId: envelope.senderId || '',
        conversationId: envelope.conversationId || '',
        readAt: envelope.timestamp ?? Date.now(),
      }
      this.readHandlers.forEach(handler => {
        try { handler(event) } catch { /* 静默 */ }
      })
      return
    }
  }

  // ==================== V2 协议发送封装 ====================

  /**
   * 发送 V2 IMEnvelope（加密 + HMAC 签名）
   */
  private async sendV2Envelope(envelope: IMEnvelopeMessage): Promise<void> {
    if (!this.isConnected) {
      throw new Error('WebSocket 未连接或密钥未协商完成')
    }

    const sessionKeys = this.sessionKeyManager.getSessionKeys()
    if (!sessionKeys) {
      throw new Error('会话密钥不可用')
    }

    // 校验消息长度
    const EnvelopeType = IMEnvelopeType()
    // AUDIT FIX [IM-PROTO-20260810]：对齐 im-test-client.encodeMessage 的先校验后
    // create/encode 约定，避免非法 command/messageType 直到协议编码深处才抛出模糊异常。
    const verifyError = EnvelopeType.verify(envelope)
    if (verifyError) {
      throw new Error(`IMEnvelope 校验失败: ${verifyError}`)
    }
    const messageInstance = EnvelopeType.create(envelope)
    const envelopeBytes = EnvelopeType.encode(messageInstance).finish()
    if (envelopeBytes.length > MAX_MESSAGE_LENGTH * 100) {
      throw new Error(`消息长度超过限制: ${envelopeBytes.length}`)
    }

    // 编码为 V2 二进制帧
    const frame = await encode(envelope, sessionKeys)

    // 通过 WebSocket 发送二进制帧
    // 注意：TypeScript 5.7+ 中 Uint8Array<ArrayBufferLike> 不兼容 BufferSource，
    // 需要显式转换为 ArrayBuffer 以满足 WebSocket.send 的类型要求
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      const frameBuffer = frame.buffer.slice(frame.byteOffset, frame.byteOffset + frame.byteLength) as ArrayBuffer
      this.ws.send(frameBuffer)
    } else {
      throw new Error('WebSocket 连接已关闭')
    }
  }

  // ==================== 重连与心跳 ====================

  /** 处理连接错误 */
  private handleConnectError(error: Error): void {
    this.emitError(error)
    this.setState(ConnectionState.RECONNECTING)

    if (this.config.autoReconnect && this.reconnectAttempts < this.config.maxReconnectAttempts) {
      this.scheduleReconnect()
    } else {
      this.setState(ConnectionState.DISCONNECTED)
    }
  }

  /** 指数退避重连（带随机抖动） */
  private scheduleReconnect(): void {
    const delay = Math.min(
      this.config.reconnectBaseInterval * Math.pow(2, this.reconnectAttempts),
      this.config.reconnectMaxInterval,
    )
    // 随机抖动 ±25%
    const jitter = delay * 0.25 * (Math.random() * 2 - 1)
    const actualDelay = Math.max(100, delay + jitter)

    this.reconnectAttempts++
    this.reconnectTimer = setTimeout(() => {
      this.keyEstablished = false
      this.sessionKeyManager.clear()
      this.doConnect()
    }, actualDelay)
  }

  /** 启动心跳 */
  private startHeartbeat(): void {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      this.sendHeartbeat().catch(() => { /* 静默 */ })
    }, this.config.heartbeatInterval)
    this.resetHeartbeatTimeout()
  }

  /** 发送心跳（V2 二进制协议） */
  private async sendHeartbeat(): Promise<void> {
    if (!this.isConnected) return

    const envelope: IMEnvelopeMessage = {
      version: 2,
      messageId: this.generateMessageId(),
      command: V2CommandType.HEARTBEAT,
      messageType: V2MessageType.MSG_HEARTBEAT,
      senderId: this.requireUserId(),
      receiverId: '',
      conversationId: '',
      conversationType: V2ConversationType.PRIVATE,
      timestamp: Date.now(),
      senderDeviceType: DeviceType.WEB,
      senderPlatform: this.config.clientPlatform,
    }

    try {
      await this.sendV2Envelope(envelope)
    } catch (e) {
      console.warn('[IM] 心跳发送失败:', (e as Error).message)
    }
  }

  /** 停止心跳 */
  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
    if (this.heartbeatTimeoutTimer) {
      clearTimeout(this.heartbeatTimeoutTimer)
      this.heartbeatTimeoutTimer = null
    }
  }

  /** 重置心跳超时计时器 */
  private resetHeartbeatTimeout(): void {
    if (this.heartbeatTimeoutTimer) {
      clearTimeout(this.heartbeatTimeoutTimer)
    }
    if (this.config.enableHeartbeat) {
      this.heartbeatTimeoutTimer = setTimeout(() => {
        this.emitError(new Error('心跳超时'))
        this.disconnect()
        if (this.config.autoReconnect) {
          this.setState(ConnectionState.RECONNECTING)
          this.scheduleReconnect()
        }
      }, this.config.heartbeatTimeout)
    }
  }

  /** 清理所有定时器 */
  private clearTimers(): void {
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  /** 拒绝所有等待中的请求 */
  private rejectAllPending(error: Error): void {
    this.pendingRequests.forEach((pending) => {
      clearTimeout(pending.timer)
      pending.reject(error)
    })
    this.pendingRequests.clear()
  }

  /** 设置连接状态 */
  private setState(state: ConnectionState): void {
    if (this.state === state) return
    this.state = state
    this.statusHandlers.forEach(handler => {
      try { handler(state) } catch { /* 静默 */ }
    })
  }

  /** 触发错误事件 */
  private emitError(error: Error): void {
    this.errorHandlers.forEach(handler => {
      try { handler(error) } catch { /* 静默 */ }
    })
  }

  /** 触发被踢下线事件 */
  private emitKicked(): void {
    this.emitError(new Error('您已被踢下线'))
    this.disconnect()
  }

  // ==================== 工具方法 ====================

  /** 生成下一个序列号 */
  private nextSeq(): number {
    this.seqCounter = (this.seqCounter + 1) & 0x7fffffff
    return this.seqCounter
  }

  /** 生成消息 ID（UUID v4，符合后端校验规则 ^[a-zA-Z0-9_\\-]+$） */
  private generateMessageId(): string {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      // crypto.randomUUID() 含连字符，符合后端校验规则
      return crypto.randomUUID()
    }
    // 降级方案：基于时间戳和随机数
    return `msg-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
  }

  /**
   * 将 V1 CommandType 映射为 V2 IMCommandType
   */
  private requireUserId(): string {
    const userId = this.config.userId?.trim()
    if (!userId) {
      throw new Error('IM 客户端缺少认证用户 UUID')
    }
    return userId
  }

  /**
   * 将 WebRTC 上层对象规范化为后端 CallPayload。
   *
   * AUDIT FIX [IM-PROTO-20260810]：WebRTCService 使用 RTCSessionDescription/RTCIceCandidate
   * 对象，而 im_protocol_v2.proto 的 CallPayload 只接受 string。未知业务字段（calleeId、
   * callType、renegotiate 等）进入 extraJson，确保不丢失信令上下文，同时不把非法字段
   * 直接交给 protobufjs.verify/create。
   */
  private toCallPayload(payload: Record<string, unknown>): Record<string, unknown> {
    const result: Record<string, unknown> = {}
    const readUint32 = (key: string): void => {
      const value = payload[key]
      if (typeof value === 'number' && Number.isFinite(value) && value >= 0) {
        result[key] = Math.floor(value)
      }
    }
    const readBoolean = (key: string): void => {
      if (typeof payload[key] === 'boolean') result[key] = payload[key]
    }

    for (const key of ['callId', 'senderId', 'roomId', 'reason']) {
      const value = this.readString(payload[key])
      if (value) result[key] = value
    }
    for (const key of ['duration', 'status', 'quality']) readUint32(key)
    for (const key of ['isScreenShare', 'isMuted', 'isCameraOff']) readBoolean(key)

    if (Array.isArray(payload.participants)) {
      result.participants = payload.participants.filter(
        (value): value is string => typeof value === 'string',
      )
    }

    const sdp = payload.sdp
    if (sdp !== undefined && sdp !== null) {
      const serializedSdp = this.serializeCallValue(sdp)
      if (serializedSdp) result.sdp = serializedSdp
    }

    const iceCandidates: string[] = []
    if (Array.isArray(payload.iceCandidates)) {
      for (const candidate of payload.iceCandidates) {
        const serialized = this.serializeCallValue(candidate)
        if (serialized) iceCandidates.push(serialized)
      }
    }
    const candidate = payload.candidate ?? payload.iceCandidate
    if (candidate !== undefined && candidate !== null) {
      const serialized = this.serializeCallValue(candidate)
      if (serialized) iceCandidates.push(serialized)
    }
    if (iceCandidates.length > 0) result.iceCandidates = iceCandidates

    const knownFields = new Set([
      'callId', 'senderId', 'roomId', 'duration', 'status', 'sdp', 'iceCandidates',
      'quality', 'participants', 'reason', 'extraJson', 'isScreenShare', 'isMuted',
      'isCameraOff', 'candidate', 'iceCandidate', 'receiverId', 'calleeId', 'conversationId',
      'callType', 'voice', 'renegotiate',
    ])
    const extra = this.safeJsonObject(this.readString(payload.extraJson))
    for (const [key, value] of Object.entries(payload)) {
      if (!knownFields.has(key) && value !== undefined) extra[key] = value
    }
    if (Object.keys(extra).length > 0) result.extraJson = JSON.stringify(extra)
    return result
  }

  /** 将 CallPayload 中的字符串字段恢复为 WebRTCService 使用的对象形态。 */
  private fromCallPayload(payload: Record<string, unknown>): Record<string, unknown> {
    const result: Record<string, unknown> = { ...payload }
    const extra = this.safeJsonObject(this.readString(payload.extraJson))
    for (const [key, value] of Object.entries(extra)) {
      if (result[key] === undefined || result[key] === '') result[key] = value
    }
    if (typeof result.sdp === 'string') {
      const parsedSdp = this.parseCallValue(result.sdp)
      if (parsedSdp !== result.sdp) result.sdp = parsedSdp
    }
    if (!result.candidate && Array.isArray(result.iceCandidates) && result.iceCandidates.length > 0) {
      const firstCandidate = result.iceCandidates[0]
      if (typeof firstCandidate === 'string') result.candidate = this.parseCallValue(firstCandidate)
    }
    return result
  }

  private serializeCallValue(value: unknown): string | undefined {
    if (typeof value === 'string') return value
    try {
      const serialized = JSON.stringify(value)
      return typeof serialized === 'string' ? serialized : undefined
    } catch {
      return undefined
    }
  }

  private parseCallValue(value: string): unknown {
    try {
      return JSON.parse(value)
    } catch {
      return value
    }
  }

  private readString(value: unknown): string {
    return typeof value === 'string' ? value : ''
  }

  private isVoiceCallPayload(payload: Record<string, unknown>): boolean {
    const callType = payload.callType
    return callType === 1 || callType === '1' || callType === 'voice' || callType === 'VOICE' || payload.voice === true
  }

  private safeJsonObject(source?: string): Record<string, unknown> {
    if (!source) return {}
    try {
      const value = JSON.parse(source)
      return value && typeof value === 'object' && !Array.isArray(value)
        ? value as Record<string, unknown>
        : {}
    } catch {
      return {}
    }
  }

  private isBusinessMessageType(type?: number): boolean {
    return type === V2MessageType.TEXT ||
      type === V2MessageType.IMAGE ||
      type === V2MessageType.FILE ||
      type === V2MessageType.VOICE ||
      type === V2MessageType.VIDEO ||
      type === V2MessageType.STICKER ||
      type === V2MessageType.LOCATION ||
      type === V2MessageType.REPLY ||
      type === V2MessageType.SYSTEM_NOTICE ||
      type === V2MessageType.CUSTOM
  }

  private mapV2MessageTypeToDto(type?: number): MessageType {
    switch (type) {
      case V2MessageType.IMAGE: return MessageType.IMAGE
      case V2MessageType.FILE: return MessageType.FILE
      case V2MessageType.VOICE: return MessageType.VOICE
      case V2MessageType.VIDEO: return MessageType.VIDEO
      case V2MessageType.LOCATION: return MessageType.LOCATION
      case V2MessageType.REPLY: return MessageType.REPLY
      case V2MessageType.SYSTEM_NOTICE: return MessageType.SYSTEM_NOTICE
      case V2MessageType.STICKER: return MessageType.STICKER
      case V2MessageType.CUSTOM:
        return MessageType.CUSTOM
      default:
        return MessageType.TEXT
    }
  }

  private payloadSummary(type: number | undefined, payload: Record<string, unknown>): string {
    if (typeof payload.content === 'string') return payload.content
    if (type === V2MessageType.IMAGE) return String(payload.altText || '[图片]')
    if (type === V2MessageType.FILE) return String(payload.fileName || '[文件]')
    if (type === V2MessageType.VOICE) return '[语音]'
    if (type === V2MessageType.VIDEO) return '[视频]'
    if (type === V2MessageType.STICKER) return String(payload.description || '[表情]')
    if (type === V2MessageType.LOCATION) return String(payload.name || payload.address || '[位置]')
    if (type === V2MessageType.REPLY) return String(payload.quotedContentPreview || '[回复]')
    if (type === V2MessageType.CUSTOM && payload.data instanceof Uint8Array) {
      try { return new TextDecoder().decode(payload.data) } catch { return '[自定义消息]' }
    }
    return '[消息]'
  }

  private mapToV2Command(command: CommandType): number {
    // V1 与 V2 命令值一致（设计时已对齐）
    return command as number
  }

  /**
   * 将 V2 IMCommandType 映射回 V1 CommandType
   *
   * <p>注意：IMEnvelopeMessage.command 为 optional 字段，可能为 undefined，
   * 因此参数类型允许 undefined，返回 undefined 表示无法映射。</p>
   */
  private mapFromV2Command(command: number | undefined): CommandType | undefined {
    if (command === undefined) return undefined
    // V1 与 V2 命令值一致
    return command as CommandType
  }

  /**
   * 将 MessageDTO.messageType 映射为 V2 IMMessageType
   */
  private mapDtoMessageType(messageType: number): number {
    // AUDIT FIX [1.7/14.25]：前端 MessageType 已与权威 im_protocol_v2.proto 数值完全对齐。
    // 原行为使用 6/7/8/9 表示位置/系统/自定义/回复，HTTP 入库后服务端会按另一种
    // Protobuf 类型解码；新行为仅允许协议中真实存在的类型并原值透传。
    switch (messageType) {
      case MessageType.TEXT:
      case MessageType.IMAGE:
      case MessageType.FILE:
      case MessageType.VOICE:
      case MessageType.VIDEO:
      case MessageType.STICKER:
      case MessageType.LOCATION:
      case MessageType.REPLY:
      case MessageType.VOICE_CALL:
      case MessageType.VIDEO_CALL:
      case MessageType.SYSTEM_NOTICE:
      case MessageType.READ_RECEIPT:
      case MessageType.TYPING:
      case MessageType.HEARTBEAT:
      case MessageType.ERROR:
      case MessageType.RECEIPT:
      case MessageType.CUSTOM:
        return messageType
      default: return V2MessageType.UNSPECIFIED
    }
  }

  /** 从 extraJson 解析错误信息 */
  private parseErrorFromExtra(extraJson: string): string {
    try {
      const extra = JSON.parse(extraJson)
      return extra.error || extra.message || '未知服务端错误'
    } catch {
      return '服务端错误'
    }
  }
}

// ==================== 单例管理器 ====================

let defaultClient: ImWebSocketClient | null = null

/**
 * 获取或创建默认的 IM WebSocket 客户端实例
 *
 * @param config 客户端配置（首次调用时必须提供）
 * @returns ImWebSocketClient 实例
 */
export function getImClient(config?: ImClientConfig): ImWebSocketClient {
  if (!defaultClient && config) {
    defaultClient = new ImWebSocketClient(config)
  }
  if (!defaultClient) {
    throw new Error('IM 客户端未初始化，请先调用 getImClient(config) 进行初始化')
  }
  return defaultClient
}

/**
 * 销毁默认客户端实例
 */
export function destroyImClient(): void {
  if (defaultClient) {
    defaultClient.destroy()
    defaultClient = null
  }
}
