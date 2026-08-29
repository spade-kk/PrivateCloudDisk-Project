// ============================================================
// commands.js — 命令处理器模块
// ============================================================
// 实现交互式命令行支持的各个命令处理器。
// 每个命令处理用户输入并调用 IMConnection 发送/接收消息。
// ============================================================

import {
  MessageType,
  ConversationType,
  MessageStatus,
  getPayloadTypeName,
  decodeMessage,
} from './proto-loader.js'
import chalk from 'chalk'
import { HttpApi } from './http.js'

/**
 * 命令处理器
 */
export class CommandHandler {
  /**
   * @param {import('./connection.js').IMConnection} connection - IM 连接实例
   * @param {import('./logger.js').Logger} logger - 日志实例
   * @param {object} config - 配置对象
   */
  constructor(connection, logger, config) {
    this.conn = connection
    this.log = logger
    this.config = config
    /** @type {Map<string, {status: string, content: string, sentAt: number}>} */
    this.messageTracker = new Map()
    /** @type {Map<string, string>} 当前会话 */
    this.currentConversation = config.user.conversationId || ''
    this.currentReceiver = config.user.receiverId || ''
    // HTTP API（离线消息拉取 / 历史消息查询）
    this.http = new HttpApi(config, logger)
  }

  // ==================== 命令注册 ====================

  /**
   * 获取所有可用命令
   * @returns {Map<string, {description: string, usage: string, handler: Function}>}
   */
  getCommands() {
    return new Map([
      ['help', {
        description: '显示所有可用命令',
        usage: 'help [command]',
        handler: (args) => this.cmdHelp(args),
      }],
      ['send', {
        description: '发送文本消息',
        usage: 'send <content> [--to <userId>] [--conv <conversationId>]',
        handler: (args) => this.cmdSend(args),
      }],
      ['to', {
        description: '设置默认接收方',
        usage: 'to <userId>',
        handler: (args) => this.cmdSetReceiver(args),
      }],
      ['conv', {
        description: '设置/切换当前会话',
        usage: 'conv <conversationId>',
        handler: (args) => this.cmdSetConversation(args),
      }],
      ['read', {
        description: '发送已读回执',
        usage: 'read [<conversationId>] [<messageId1> <messageId2> ...]',
        handler: (args) => this.cmdSendReadReceipt(args),
      }],
      ['typing', {
        description: '发送正在输入状态',
        usage: 'typing [on|off]',
        handler: (args) => this.cmdTyping(args),
      }],
      ['heartbeat', {
        description: '手动发送心跳',
        usage: 'heartbeat',
        handler: () => this.cmdHeartbeat(),
      }],
      ['pause-heartbeat', {
        description: '暂停心跳（测试超时断开）',
        usage: 'pause-heartbeat',
        handler: () => this.cmdPauseHeartbeat(),
      }],
      ['resume-heartbeat', {
        description: '恢复心跳',
        usage: 'resume-heartbeat',
        handler: () => this.cmdResumeHeartbeat(),
      }],
      ['status', {
        description: '显示当前连接状态和统计',
        usage: 'status',
        handler: () => this.cmdStatus(),
      }],
      ['messages', {
        description: '列出已发送消息及其状态',
        usage: 'messages',
        handler: () => this.cmdListMessages(),
      }],
      ['reconnect', {
        description: '手动触发重连',
        usage: 'reconnect',
        handler: () => this.cmdReconnect(),
      }],
      ['disconnect', {
        description: '断开连接',
        usage: 'disconnect',
        handler: () => this.cmdDisconnect(),
      }],
      ['flood', {
        description: '并发发送多条消息（压力测试）',
        usage: 'flood <count> [content]',
        handler: (args) => this.cmdFlood(args),
      }],
      ['offline', {
        description: '通过 HTTP 拉取离线消息（PREPARING，拉取后置 DELIVERED）',
        usage: 'offline [limit]',
        handler: (args) => this.cmdPullOffline(args),
      }],
      ['history', {
        description: '通过 HTTP 游标分页拉取会话历史消息（仅终态）',
        usage: 'history <conversationId> [limit] [cursor]',
        handler: (args) => this.cmdHistory(args),
      }],
      ['quit', {
        description: '退出程序',
        usage: 'quit',
        handler: () => this.cmdQuit(),
      }],
    ])
  }

  // ==================== 命令实现 ====================

  /**
   * help — 显示帮助
   */
  cmdHelp(args) {
    const commands = this.getCommands()
    if (args[0]) {
      const cmd = commands.get(args[0])
      if (cmd) {
        console.log(chalk.cyan(`\n  ${cmd.usage}`))
        console.log(chalk.gray(`  ${cmd.description}\n`))
        return
      }
    }
    console.log(chalk.cyan('\n可用命令:'))
    for (const [name, cmd] of commands) {
      console.log(`  ${chalk.green(name.padEnd(20))} ${chalk.gray(cmd.description)}`)
    }
    console.log('')
  }

  /**
   * send — 发送文本消息
   * 用法: send <content> [--to <userId>] [--conv <conversationId>]
   */
  cmdSend(args) {
    // 解析参数
    let receiverId = this.currentReceiver
    let conversationId = this.currentConversation
    const contentParts = []
    let i = 0
    while (i < args.length) {
      if (args[i] === '--to' && args[i + 1]) {
        receiverId = args[i + 1]
        i += 2
      } else if (args[i] === '--conv' && args[i + 1]) {
        conversationId = args[i + 1]
        i += 2
      } else {
        contentParts.push(args[i])
        i++
      }
    }

    const content = contentParts.join(' ')
    if (!content) {
      this.log.error('消息内容不能为空')
      return
    }
    if (!receiverId) {
      this.log.error('未指定接收方，请使用 "to <userId" 设置或 --to 参数')
      return
    }

    const envelope = this.conn.sendTextMessage(
      receiverId,
      conversationId,
      content,
    )

    if (envelope) {
      this.log.sent(envelope, `→ ${receiverId}: ${content}`)
      this.messageTracker.set(envelope.messageId, {
        status: 'sent',
        content,
        sentAt: Date.now(),
        receiverId,
      })
    }
  }

  /**
   * to — 设置默认接收方
   */
  cmdSetReceiver(args) {
    if (!args[0]) {
      this.log.info(`当前接收方: ${this.currentReceiver || '(未设置)'}`)
      return
    }
    this.currentReceiver = args[0]
    this.log.success(`接收方已设置为: ${this.currentReceiver}`)
  }

  /**
   * conv — 设置/切换会话
   */
  cmdSetConversation(args) {
    if (!args[0]) {
      this.log.info(`当前会话: ${this.currentConversation || '(未设置)'}`)
      return
    }
    this.currentConversation = args[0]
    this.log.success(`会话已切换为: ${this.currentConversation}`)
  }

  /**
   * read — 发送已读回执
   */
  cmdSendReadReceipt(args) {
    let conversationId = this.currentConversation
    let messageIds = []

    if (args[0] && !args[0].startsWith('--')) {
      conversationId = args[0]
      messageIds = args.slice(1)
    } else {
      messageIds = args
    }

    if (!conversationId) {
      this.log.error('未指定会话 ID')
      return
    }

    this.conn.sendReadReceipt(conversationId, messageIds)
    this.log.info(`已发送已读回执: conv=${conversationId}, ${messageIds.length} 条消息`)
  }

  /**
   * typing — 发送正在输入状态
   */
  cmdTyping(args) {
    const isTyping = args[0] !== 'off'
    if (!this.currentConversation) {
      this.log.error('未设置当前会话')
      return
    }
    this.conn.sendTyping(this.currentConversation, isTyping)
    this.log.info(`已发送输入状态: ${isTyping ? 'typing' : 'stopped'}`)
  }

  /**
   * heartbeat — 手动发送心跳
   */
  cmdHeartbeat() {
    this.conn.sendHeartbeat()
    this.log.info('已手动发送心跳')
  }

  /**
   * pause-heartbeat — 暂停心跳
   */
  cmdPauseHeartbeat() {
    this.conn.pauseHeartbeat()
  }

  /**
   * resume-heartbeat — 恢复心跳
   */
  cmdResumeHeartbeat() {
    this.conn.resumeHeartbeat()
  }

  /**
   * status — 显示状态
   */
  cmdStatus() {
    const state = this.conn.state
    const keyId = this.conn.sessionKeys?.keyId || 'N/A'
    const connId = this.conn.connectionId || 'N/A'
    const sentCount = this.messageTracker.size
    const delivered = Array.from(this.messageTracker.values())
      .filter((m) => m.status === 'delivered' || m.status === 'read').length

    console.log(chalk.cyan('\n=== 连接状态 ==='))
    console.log(`  状态:         ${chalk.yellow(state)}`)
    console.log(`  连接 ID:      ${connId}`)
    console.log(`  密钥 ID:      ${keyId}`)
    console.log(`  用户 ID:      ${this.conn.userId || 'N/A'}`)
    console.log(`  当前接收方:   ${this.currentReceiver || 'N/A'}`)
    console.log(`  当前会话:     ${this.currentConversation || 'N/A'}`)
    console.log(`  已发送消息:   ${sentCount}`)
    console.log(`  已送达/已读:  ${delivered}`)
    console.log(`  心跳间隔:     ${this.config.heartbeat.intervalMs}ms`)
    console.log(`  重连次数:     ${this.conn.reconnectAttempts}`)
    console.log('')
  }

  /**
   * messages — 列出已发送消息
   */
  cmdListMessages() {
    if (this.messageTracker.size === 0) {
      this.log.info('暂无已发送消息')
      return
    }

    console.log(chalk.cyan('\n=== 已发送消息 ==='))
    for (const [msgId, info] of this.messageTracker) {
      const shortId = msgId.slice(0, 8)
      const statusColor = {
        sent: chalk.yellow,
        delivered: chalk.green,
        read: chalk.blue,
        failed: chalk.red,
        recalled: chalk.gray,
      }[info.status] || chalk.white

      console.log(
        `  ${chalk.dim(shortId)}  ${statusColor(info.status.padEnd(10))}  → ${info.receiverId}: ${info.content.slice(0, 50)}`,
      )
    }
    console.log('')
  }

  /**
   * reconnect — 手动重连
   */
  cmdReconnect() {
    this.log.info('手动触发重连...')
    this.conn._forceReconnect()
  }

  /**
   * disconnect — 断开连接
   */
  cmdDisconnect() {
    this.conn.disconnect('user requested')
  }

  /**
   * flood — 并发发送多条消息（压力测试）
   */
  cmdFlood(args) {
    const count = parseInt(args[0], 10) || 100
    const content = args.slice(1).join(' ') || 'flood-test'
    const receiverId = this.currentReceiver
    const conversationId = this.currentConversation

    if (!receiverId) {
      this.log.error('未指定接收方')
      return
    }

    this.log.info(`开始压力测试: 发送 ${count} 条消息...`)
    const startTime = Date.now()
    let successCount = 0
    let failCount = 0

    for (let i = 0; i < count; i++) {
      const msgContent = `${content}-${i + 1}`
      const envelope = this.conn.sendTextMessage(
        receiverId,
        conversationId,
        msgContent,
      )
      if (envelope) {
        successCount++
        this.messageTracker.set(envelope.messageId, {
          status: 'sent',
          content: msgContent,
          sentAt: Date.now(),
          receiverId,
        })
      } else {
        failCount++
      }
    }

    const elapsed = Date.now() - startTime
    this.log.success(
      `压力测试完成: ${successCount} 成功, ${failCount} 失败, 耗时 ${elapsed}ms, ` +
      `平均 ${Math.round((successCount / elapsed) * 1000)} msg/s`,
    )
  }

  /**
   * quit — 退出程序
   */
  cmdQuit() {
    this.conn.disconnect('user quit')
    setTimeout(() => process.exit(0), 500)
  }

  // ==================== HTTP 离线拉取 / 历史查询 ====================

  /**
   * offline — 通过 HTTP 拉取离线消息
   * 用法: offline [limit]
   */
  async cmdPullOffline(args) {
    const limit = parseInt(args[0], 10) || 100
    try {
      await this.pullOffline(limit)
    } catch (e) {
      this.log.error(`离线消息拉取失败: ${e.message}`)
    }
  }

  /**
   * history — 通过 HTTP 游标分页拉取会话历史消息
   * 用法: history <conversationId> [limit] [cursor]
   */
  async cmdHistory(args) {
    const conversationId = args[0] || this.currentConversation
    if (!conversationId) {
      this.log.error('请指定会话 ID 或先使用 conv <conversationId> 设置当前会话')
      return
    }
    const limit = parseInt(args[1], 10) || 20
    const cursor = args[2] ? parseInt(args[2], 10) : undefined
    const userId = this.config.user.userId

    this.log.info(`拉取会话历史: conversationId=${conversationId}, limit=${limit}, cursor=${cursor || '首屏'}`)
    try {
      const messages = await this.http.getHistory(conversationId, userId, limit, cursor)
      if (!messages || messages.length === 0) {
        this.log.info('未获取到历史消息（空）')
        return
      }
      messages.forEach((m) => this.displayHttpMessage(m, 'history'))
      this.log.success(`历史消息 ${messages.length} 条（最新在前，如需更早可用 cursor=${messages[messages.length - 1]?.serverSeq}）`)
    } catch (e) {
      this.log.error(`历史消息拉取失败: ${e.message}`)
    }
  }

  /**
   * 拉取离线消息并展示（连接就绪后自动调用，也可通过 offline 命令手动触发）
   * @param {number} limit - 最大拉取条数
   */
  async pullOffline(limit = 100) {
    const userId = this.config.user.userId
    if (!userId) {
      this.log.error('未配置用户 ID，无法拉取离线消息')
      return 0
    }
    this.log.info('通过 HTTP 拉取离线消息...')
    const messages = await this.http.getOfflineMessages(userId, limit)
    if (!messages || messages.length === 0) {
      this.log.info('暂无离线消息')
      return 0
    }
    messages.forEach((m) => this.displayHttpMessage(m, 'offline'))
    this.log.success(`离线消息 ${messages.length} 条（已置为 DELIVERED）`)
    return messages.length
  }

  /**
   * 展示一条通过 HTTP 拉取的消息（离线/历史）
   */
  displayHttpMessage(msg, source) {
    const time = msg.sendTime ? new Date(msg.sendTime).toLocaleTimeString() : ''
    const sender = msg.senderId || '-'
    const conv = msg.conversationId || '-'
    const content = (msg.content || '').slice(0, 120)
    console.log(
      chalk.gray(`[${source}] ${time} ${sender} → ${conv}`) +
      '\n  ' + chalk.white(content || '(无内容)') +
      chalk.gray(`  (status=${msg.status}, seq=${msg.serverSeq})`),
    )
  }

  // ==================== 接收消息处理 ====================

  /**
   * 处理收到的 IMEnvelope
   * @param {object} envelope - IMEnvelope 消息对象
   */
  handleReceivedEnvelope(envelope) {
    // 尝试解码 payload
    let contentPreview = ''
    let payloadObj = null

    if (envelope.encryptedPayload && envelope.encryptedPayload.length > 0) {
      let payloadTypeName = getPayloadTypeName(envelope.messageType)
      if (payloadTypeName) {
        try {
          payloadObj = decodeMessage(payloadTypeName, envelope.encryptedPayload)
        } catch (e) {
          this.log.info(`Payload 解码失败 (${payloadTypeName}): ${e.message}`)
        }
      }
    }

    // 根据 messageType 格式化预览
    switch (envelope.messageType) {
      case MessageType.TEXT:
        contentPreview = payloadObj?.content || '(无法解码文本)'
        break
      case MessageType.READ_RECEIPT: {
        const msgIds = payloadObj?.messageIds || []
        for (const id of msgIds) {
          const oldStatus = this.messageTracker.get(id)?.status || 'sent'
          this.log.ackStatus(id, oldStatus, 'read')
          if (this.messageTracker.has(id)) {
            this.messageTracker.get(id).status = 'read'
          }
        }
        return
      }
      case MessageType.MSG_TYPING:
        contentPreview = `[正在输入: ${payloadObj?.isTyping ? 'on' : 'off'}]`
        break
      case MessageType.SYSTEM_NOTICE:
        contentPreview = `[系统通知: ${payloadObj?.noticeType || ''} ${payloadObj?.content || ''}]`
        break
      case MessageType.MSG_HEARTBEAT:
        return // 心跳已在 connection 层处理
      case MessageType.ERROR: {
        // 送达失败等回执通知：解析 extraJson 展示原因
        let detail = ''
        if (envelope.extraJson) {
          try {
            const info = JSON.parse(envelope.extraJson)
            if (info?.type === 'DELIVERY_FAILED') {
              detail = `送达失败: ${info.messageId || ''}${info.reason ? ' (' + info.reason + ')' : ''}`
            } else if (info?.reason) {
              detail = info.reason
            }
          } catch (_) {
            /* ignore */
          }
        }
        contentPreview = detail ? `[${detail}]` : `[错误消息]`
        break
      }
      case MessageType.RECEIPT: {
        // 服务端推送回执：通知发送方消息是否送达/推送失败/发送失败
        // 回执可能指向本客户端发送的消息，也可能来自多端同步（本客户端未发送）
        const statusName = {
          1: 'DELIVERED',
          2: 'PUSH_FAILED',
          3: 'SEND_FAILED',
        }[payloadObj?.status] || `UNKNOWN(${payloadObj?.status})`

        const originalMessageId = payloadObj?.originalMessageId || ''
        const reason = payloadObj?.failReason ? ` 原因=${payloadObj.failReason}` : ''
        const localNote = this.messageTracker.has(originalMessageId)
          ? ''
          : ' (非本客户端发送，多端同步回执)'

        this.log.info(
          `[回执] 消息 ${originalMessageId} 状态=${statusName}${reason}${localNote}`,
        )

        // 若为本客户端发送的消息，同步更新本地状态跟踪
        if (originalMessageId && this.messageTracker.has(originalMessageId)) {
          const trackedStatus = {
            1: 'delivered',
            2: 'failed',
            3: 'failed',
          }[payloadObj?.status] || 'sent'
          this.messageTracker.get(originalMessageId).status = trackedStatus
        }
        return
      }
      case MessageType.IMAGE:
        contentPreview = `[图片: ${payloadObj?.url || ''}]`
        break
      case MessageType.FILE:
        contentPreview = `[文件: ${payloadObj?.fileName || ''} (${payloadObj?.size || 0} bytes)]`
        break
      default:
        contentPreview = `[消息类型: ${envelope.messageType}]`
    }

    this.log.recv(envelope, contentPreview)
  }
}
