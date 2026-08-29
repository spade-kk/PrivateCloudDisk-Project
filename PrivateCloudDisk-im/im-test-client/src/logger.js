// ============================================================
// logger.js — 彩色日志模块
// ============================================================
// 使用 chalk 提供彩色终端输出，区分不同级别和类型的日志。
// 支持 verbose 模式输出详细调试信息（如帧十六进制 dump）。
// ============================================================

import chalk from 'chalk'

/**
 * 日志类
 * 支持级别过滤和彩色输出
 */
export class Logger {
  /**
   * @param {object} options - 日志配置
   * @param {string} options.level - 日志级别 (debug/info/warn/error)
   * @param {boolean} options.verbose - 是否输出详细调试信息
   * @param {boolean} options.colorEnabled - 是否启用彩色输出
   */
  constructor(options = {}) {
    this.level = options.level || 'info'
    this.verbose = options.verbose || false
    this.colorEnabled = options.colorEnabled !== false

    // chalk 颜色级别检测
    if (!this.colorEnabled) {
      chalk.level = 0
    }

    this._levelPriority = { debug: 0, info: 1, warn: 2, error: 3 }
  }

  /**
   * @private
   * 判断是否应输出该级别日志
   */
  _shouldLog(level) {
    const priority = this._levelPriority[level] ?? 1
    const currentPriority = this._levelPriority[this.level] ?? 1
    return priority >= currentPriority
  }

  /**
   * @private
   * 格式化时间戳
   */
  _timestamp() {
    const now = new Date()
    return now.toTimeString().split(' ')[0] + '.' + String(now.getMilliseconds()).padStart(3, '0')
  }

  /** 调试日志 */
  debug(...args) {
    if (!this._shouldLog('debug')) return
    const ts = chalk.gray(this._timestamp())
    const tag = chalk.magenta('[DEBUG]')
    console.log(ts, tag, ...args)
  }

  /** 普通信息 */
  info(...args) {
    if (!this._shouldLog('info')) return
    const ts = chalk.gray(this._timestamp())
    const tag = chalk.cyan('[INFO]')
    console.log(ts, tag, ...args)
  }

  /** 成功信息 */
  success(...args) {
    if (!this._shouldLog('info')) return
    const ts = chalk.gray(this._timestamp())
    const tag = chalk.green('[OK]')
    console.log(ts, tag, ...args)
  }

  /** 警告 */
  warn(...args) {
    if (!this._shouldLog('warn')) return
    const ts = chalk.gray(this._timestamp())
    const tag = chalk.yellow('[WARN]')
    console.warn(ts, tag, ...args)
  }

  /** 错误 */
  error(...args) {
    if (!this._shouldLog('error')) return
    const ts = chalk.gray(this._timestamp())
    const tag = chalk.red('[ERROR]')
    console.error(ts, tag, ...args)
  }

  /** 心跳日志 */
  heartbeat(rttMs) {
    if (!this._shouldLog('debug')) return
    const ts = chalk.gray(this._timestamp())
    const tag = chalk.blue('[HEARTBEAT]')
    const rtt = rttMs < 100
      ? chalk.green(`${rttMs}ms`)
      : rttMs < 300
        ? chalk.yellow(`${rttMs}ms`)
        : chalk.red(`${rttMs}ms`)
    console.log(ts, tag, `Heartbeat OK, rtt=${rtt}`)
  }

  /** 接收消息日志 */
  recv(envelope, contentPreview) {
    const ts = chalk.gray(this._timestamp())
    const tag = chalk.blue('[RECV]')
    const time = new Date(envelope.timestamp || Date.now())
      .toISOString().replace('T', ' ').replace(/\..+/, '')
    const sender = chalk.magenta(envelope.senderId || 'unknown')
    const preview = contentPreview || ''
    console.log(ts, tag, `${time} from ${sender}:`, preview)
  }

  /** 发送消息日志 */
  sent(envelope, contentPreview) {
    const ts = chalk.gray(this._timestamp())
    const tag = chalk.green('[SENT]')
    const msgId = chalk.dim(envelope.messageId?.slice(0, 8) || '????????')
    const preview = contentPreview || ''
    console.log(ts, tag, `msgId=${msgId}`, preview)
  }

  /** ACK 状态变更日志 */
  ackStatus(messageId, oldStatus, newStatus) {
    const ts = chalk.gray(this._timestamp())
    const tag = chalk.cyan('[ACK]')
    const msgId = chalk.dim(messageId?.slice(0, 8) || '????????')
    const statusColor = (s) => {
      const map = {
        sending: chalk.yellow,
        sent: chalk.cyan,
        delivered: chalk.green,
        read: chalk.blue,
        failed: chalk.red,
        recalled: chalk.gray,
      }
      return (map[s] || chalk.white)(s)
    }
    console.log(
      ts, tag,
      `msgId=${msgId}`,
      `${statusColor(oldStatus)} → ${statusColor(newStatus)}`,
    )
  }

  /**
   * 输出二进制数据的十六进制 dump（仅 verbose 模式）
   * @param {Buffer|Uint8Array} data - 二进制数据
   * @param {string} label - 标签
   */
  dump(data, label = '') {
    if (!this.verbose) return
    const buf = Buffer.from(data)
    const hexDump = this._hexDump(buf)
    const tag = chalk.magenta('[DUMP]')
    const ts = chalk.gray(this._timestamp())
    if (label) {
      console.log(ts, tag, `${label} (${buf.length} bytes):`)
    } else {
      console.log(ts, tag, `${buf.length} bytes:`)
    }
    console.log(hexDump)
  }

  /**
   * @private
   * 十六进制 dump 格式化
   */
  _hexDump(buf) {
    const lines = []
    for (let i = 0; i < buf.length; i += 16) {
      const slice = buf.subarray(i, i + 16)
      const hex = Array.from(slice)
        .map((b) => b.toString(16).padStart(2, '0'))
        .join(' ')
        .padEnd(48, ' ')
      const ascii = Array.from(slice)
        .map((b) => (b >= 32 && b < 127 ? String.fromCharCode(b) : '.'))
        .join('')
      lines.push(
        chalk.dim(`${i.toString(16).padStart(8, '0')}  ${hex}  |${ascii}|`),
      )
    }
    return lines.join('\n')
  }
}
