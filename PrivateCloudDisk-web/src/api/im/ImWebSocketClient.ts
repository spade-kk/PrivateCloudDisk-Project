// ============================================================
// im/ImWebSocketClient.ts — WebSocket 长连接客户端 SDK
// ============================================================
// 企业级 WebSocket 客户端 SDK，与后端 im-server Netty 服务完全对接。
//
// 核心能力：
// - 自动连接/重连（指数退避 + 抖动）
// - 心跳保活（30s 间隔，90s 超时）
// - 命令字路由（消息/ACK/已读/正在输入/离线同步）
// - 请求-响应匹配（基于 seq 的 Promise 回调）
// - 连接状态机（idle → connecting → connected → disconnected）
// - 多事件监听器（onMessage / onStatusChange / onError / onKicked）
// - 消息去重（基于 messageId）
// - 离线消息自动同步
//
// 后端对应：
//   org.project.im.server.netty.NettyWebSocketServer
//   org.project.im.server.netty.handler.AuthHandler
//   org.project.im.server.netty.handler.MessageHandler
// ============================================================

import {
  type MessageProtocol,
  type MessageDTO,
  CommandType,
  PROTOCOL_VERSION,
  HEARTBEAT_INTERVAL,
  HEARTBEAT_TIMEOUT,
  RECONNECT_BASE_INTERVAL,
  RECONNECT_MAX_INTERVAL,
  MAX_RECONNECT_ATTEMPTS,
  MAX_MESSAGE_LENGTH,
  isMessageProtocol,
} from './types'

// ==================== 类型定义 ====================

/** 连接状态 */
export enum ConnectionState {
  IDLE = 'idle',
  CONNECTING = 'connecting',
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

// ==================== WebSocket 客户端 ====================

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

  // ---- 请求管理 ----
  private pendingRequests = new Map<number, PendingRequest>()

  // ---- 消息去重 ----
  private receivedMessageIds = new Set<string>()
  private readonly maxCachedMessageIds = 1000

  // ---- 事件监听器 ----
  private messageHandlers = new Set<MessageHandler>()
  private statusHandlers = new Set<StatusHandler>()
  private errorHandlers = new Set<ErrorHandler>()

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
      ...config,
    }
  }

  // ==================== 连接管理 ====================

  /** 获取当前连接状态 */
  get connectionState(): ConnectionState {
    return this.state
  }

  /** 是否已连接 */
  get isConnected(): boolean {
    return this.state === ConnectionState.CONNECTED && this.ws?.readyState === WebSocket.OPEN
  }

  /** 建立连接 */
  connect(): void {
    if (this.state === ConnectionState.CONNECTING || this.state === ConnectionState.CONNECTED) {
      return
    }
    this.setState(ConnectionState.CONNECTING)
    this.doConnect()
  }

  /** 断开连接 */
  disconnect(): void {
    this.clearTimers()
    this.rejectAllPending(new Error('客户端主动断开'))
    if (this.ws) {
      this.ws.onclose = null
      this.ws.onerror = null
      this.ws.onmessage = null
      this.ws.onopen = null
      this.ws.close(1000, '客户端主动断开')
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

  // ==================== 消息发送 ====================

  /**
   * 发送消息（请求-响应模式）
   * @param command 命令类型
   * @param payload 消息体
   * @param receiverId 接收者 ID
   * @returns 响应协议
   */
  sendRequest(
    command: CommandType,
    payload: unknown,
    receiverId?: string,
  ): Promise<MessageProtocol> {
    return new Promise((resolve, reject) => {
      const seq = this.nextSeq()
      const protocol: MessageProtocol = {
        version: PROTOCOL_VERSION,
        command,
        seq,
        timestamp: Date.now(),
        receiverId,
        payload,
      }

      // 注册请求超时
      const timer = setTimeout(() => {
        this.pendingRequests.delete(seq)
        reject(new Error(`请求超时: command=${command}, seq=${seq}`))
      }, this.config.requestTimeout)

      this.pendingRequests.set(seq, { resolve, reject, timer })

      // 发送
      try {
        this.sendRaw(protocol)
      } catch (e) {
        clearTimeout(timer)
        this.pendingRequests.delete(seq)
        reject(e)
      }
    })
  }

  /**
   * 发送消息（仅发送，不等待响应）
   */
  sendMessage(payload: MessageDTO): void {
    this.sendRaw({
      version: PROTOCOL_VERSION,
      command: CommandType.SEND_MESSAGE,
      seq: this.nextSeq(),
      timestamp: Date.now(),
      senderId: payload.senderId,
      receiverId: payload.receiverId,
      payload,
    })
  }

  /**
   * 发送消息确认（ACK）
   */
  sendAck(originalSeq: number): void {
    this.sendRaw({
      version: PROTOCOL_VERSION,
      command: CommandType.MESSAGE_ACK,
      seq: originalSeq,
      timestamp: Date.now(),
    })
  }

  /**
   * 发送已读回执
   */
  sendReadReceipt(conversationId: string): void {
    this.sendRaw({
      version: PROTOCOL_VERSION,
      command: CommandType.READ_MESSAGE,
      timestamp: Date.now(),
      payload: conversationId,
    })
  }

  /**
   * 发送正在输入状态
   */
  sendTyping(receiverId: string): void {
    this.sendRaw({
      version: PROTOCOL_VERSION,
      command: CommandType.TYPING,
      timestamp: Date.now(),
      receiverId,
    })
  }

  /**
   * 发送撤回消息请求
   */
  sendRecallMessage(messageId: string): void {
    this.sendRaw({
      version: PROTOCOL_VERSION,
      command: CommandType.RECALL_MESSAGE,
      timestamp: Date.now(),
      payload: { messageId },
    })
  }

  /**
   * 请求同步离线消息
   */
  requestSyncOffline(): void {
    this.sendRaw({
      version: PROTOCOL_VERSION,
      command: CommandType.SYNC_OFFLINE_MESSAGES,
      timestamp: Date.now(),
    })
  }

  // ==================== 私有方法 ====================

  /** 执行 WebSocket 连接 */
  private doConnect(): void {
    const token = typeof this.config.token === 'function'
      ? this.config.token()
      : this.config.token

    const url = `${this.config.url}?token=${encodeURIComponent(token)}`

    try {
      this.ws = new WebSocket(url)
    } catch (e) {
      this.handleConnectError(new Error(`WebSocket 创建失败: ${(e as Error).message}`))
      return
    }

    this.ws.onopen = () => {
      this.setState(ConnectionState.CONNECTED)
      this.reconnectAttempts = 0
      if (this.config.enableHeartbeat) {
        this.startHeartbeat()
      }
      // 连接成功后自动同步离线消息
      this.requestSyncOffline()
    }

    this.ws.onmessage = (event: MessageEvent) => {
      this.handleMessage(event.data)
    }

    this.ws.onerror = () => {
      this.handleConnectError(new Error('WebSocket 连接错误'))
    }

    this.ws.onclose = (event: CloseEvent) => {
      this.clearTimers()
      if (event.code !== 1000) {
        this.handleConnectError(new Error(`WebSocket 连接关闭: code=${event.code}, reason=${event.reason}`))
      }
    }
  }

  /** 处理收到的消息 */
  private handleMessage(data: string): void {
    let protocol: MessageProtocol

    try {
      protocol = JSON.parse(data) as MessageProtocol
    } catch {
      this.emitError(new Error('消息解析失败: 无效的 JSON'))
      return
    }

    if (!isMessageProtocol(protocol)) {
      this.emitError(new Error('消息格式错误: 不符合 MessageProtocol'))
      return
    }

    // 重置心跳超时
    this.resetHeartbeatTimeout()

    // 处理请求-响应匹配
    if (protocol.seq !== undefined && this.pendingRequests.has(protocol.seq)) {
      const pending = this.pendingRequests.get(protocol.seq)!
      clearTimeout(pending.timer)
      this.pendingRequests.delete(protocol.seq)
      pending.resolve(protocol)
      return
    }

    // 消息去重：基于 messageId
    if (protocol.payload && typeof protocol.payload === 'object') {
      const messageId = (protocol.payload as Record<string, unknown>).messageId as string | undefined
      if (messageId) {
        if (this.receivedMessageIds.has(messageId)) return
        this.receivedMessageIds.add(messageId)
        if (this.receivedMessageIds.size > this.maxCachedMessageIds) {
          const keys = Array.from(this.receivedMessageIds)
          for (let i = 0; i < keys.length / 2; i++) {
            this.receivedMessageIds.delete(keys[i])
          }
        }
      }
    }

    // 处理错误通知
    if (protocol.command === CommandType.ERROR_NOTIFY) {
      const errorPayload = protocol.payload as { code?: number; message?: string } | undefined
      this.emitError(new Error(errorPayload?.message ?? '服务端错误'))
      if (errorPayload?.code === 2004) {
        this.emitKicked()
      }
      return
    }

    // 处理被踢下线
    if (protocol.command === CommandType.SYSTEM_NOTIFY && protocol.payload) {
      const payload = protocol.payload as Record<string, unknown>
      if (payload.eventType === 'KICKED') {
        this.emitKicked()
        return
      }
    }

    // 广播到所有消息监听器
    this.messageHandlers.forEach(handler => {
      try { handler(protocol) } catch { /* 静默 */ }
    })

    // 路由到命令字处理器
    const commandHandlers = this.commandHandlers.get(protocol.command)
    if (commandHandlers) {
      commandHandlers.forEach(handler => {
        try { handler(protocol) } catch { /* 静默 */ }
      })
    }
  }

  /** 发送原始消息 */
  private sendRaw(protocol: MessageProtocol): void {
    if (!this.isConnected) {
      throw new Error('WebSocket 未连接')
    }
    // 校验消息长度
    const json = JSON.stringify(protocol)
    if (json.length > MAX_MESSAGE_LENGTH * 2) {
      throw new Error(`消息长度超过限制: ${json.length} > ${MAX_MESSAGE_LENGTH * 2}`)
    }
    this.ws!.send(json)
  }

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
    const actualDelay = delay + jitter

    this.reconnectAttempts++
    this.reconnectTimer = setTimeout(() => {
      this.doConnect()
    }, actualDelay)
  }

  /** 启动心跳 */
  private startHeartbeat(): void {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      this.sendRaw({
        version: PROTOCOL_VERSION,
        command: CommandType.HEARTBEAT,
        timestamp: Date.now(),
      })
    }, this.config.heartbeatInterval)
    this.resetHeartbeatTimeout()
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

  /** 生成下一个序列号 */
  private nextSeq(): number {
    this.seqCounter = (this.seqCounter + 1) & 0x7fffffff
    return this.seqCounter
  }
}

// ==================== 单例管理器 ====================

let defaultClient: ImWebSocketClient | null = null

/**
 * 获取或创建默认的 IM WebSocket 客户端实例
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