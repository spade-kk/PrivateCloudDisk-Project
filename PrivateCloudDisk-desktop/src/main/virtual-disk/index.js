/**
 * virtual-disk/index.js - 虚拟磁盘管理器 (Electron 主进程侧)
 *
 * 职责:
 * 1. 以子进程方式启动/管理守护进程
 * 2. 通过 Unix Socket 与守护进程通信
 * 3. 暴露 IPC 接口给渲染进程 (通过 Electron IPC)
 * 4. 管理挂载/卸载生命周期
 *
 * 虚拟磁盘方案: WebDAV (RFC 4918) — 零原生依赖
 *   - macOS: Finder "连接服务器" (Cmd+K) 或 osascript 自动挂载
 *   - Windows: net use 命令映射网络驱动器
 *   - Linux: davfs2 挂载
 *
 * 架构:
 *   Electron Main Process
 *       │
 *       ├── VirtualDiskManager (本模块)
 *       │       │
 *       │       ├── child_process.fork() → daemon.js (守护进程)
 *       │       │       ├── CloudFS (WebDAV Server)
 *       │       │       ├── MetadataStore (SQLite)
 *       │       │       ├── CacheManager (LRU)
 *       │       │       └── SyncManager (chokidar)
 *       │       │
 *       │       └── ipc-bridge.js → IPC Handlers (Electron IPC)
 *       │
 *       └── Renderer Process (React UI)
 */

const { fork } = require('child_process')
const net = require('net')
const path = require('path')
const fs = require('fs')
const os = require('os')
const { app } = require('electron')
const { logger } = require('./utils')

// ==================== 常量 ====================

const SOCKET_PATH = path.join(os.tmpdir(), 'privateclouddisk-vd.sock')

// 守护进程路径: 开发环境用源码, 生产环境用打包后的 extraResources
const DAEMON_SCRIPT = app.isPackaged
  ? path.join(process.resourcesPath, 'virtual-disk', 'daemon.js')
  : path.join(__dirname, 'daemon.js')

const DATA_DIR = path.join(os.homedir(), '.privateclouddisk', 'vd-data')
const DEFAULT_MOUNT_POINT = path.join(os.homedir(), 'PrivateCloudDisk')

// ==================== VirtualDiskManager ====================

class VirtualDiskManager {
  constructor() {
    this.daemonProcess = null
    this.socketClient = null
    this.socketBuffer = ''
    this.pendingRequests = new Map()  // requestId → { resolve, reject }
    this.requestId = 0
    this.eventListeners = new Map()   // eventName → [callback]
    this.isMounted = false
    this.mountPoint = null

    // 配置
    this.config = {
      apiBaseUrl: 'http://localhost:8000',
      token: '',
      userId: '',
      mountOnStartup: false,
      cacheMaxSize: 5 * 1024 * 1024 * 1024  // 5GB
    }
  }

  // ==================== 守护进程管理 ====================

  /**
   * 启动守护进程
   */
  startDaemon() {
    if (this.daemonProcess && !this.daemonProcess.killed) {
      logger.warn('VirtualDiskManager', '守护进程已在运行')
      return
    }

    logger.info('VirtualDiskManager', '正在启动守护进程...')

    this.daemonProcess = fork(DAEMON_SCRIPT, [], {
      env: {
        ...process.env,
        VD_SOCKET_PATH: SOCKET_PATH,
        VD_DATA_DIR: DATA_DIR
      },
      stdio: ['ignore', 'pipe', 'pipe', 'ipc']
    })

    this.daemonProcess.stdout.on('data', (data) => {
      // 守护进程的日志输出
    })

    this.daemonProcess.stderr.on('data', (data) => {
      logger.warn('VirtualDiskManager', `Daemon stderr: ${data.toString().trim()}`)
    })

    this.daemonProcess.on('exit', (code, signal) => {
      logger.warn('VirtualDiskManager', `守护进程退出: code=${code}, signal=${signal}`)
      this.daemonProcess = null
      this.isMounted = false
      this._emit('daemon-exit', { code, signal })
    })

    this.daemonProcess.on('error', (err) => {
      logger.error('VirtualDiskManager', `守护进程错误: ${err.message}`)
      this._emit('daemon-error', { error: err.message })
    })

    logger.info('VirtualDiskManager', `守护进程已启动, PID=${this.daemonProcess.pid}`)
  }

  /**
   * 停止守护进程
   */
  async stopDaemon() {
    if (!this.daemonProcess || this.daemonProcess.killed) return

    try {
      // 先卸载
      if (this.isMounted) {
        await this.unmount().catch(() => {})
      }

      // 发送 shutdown 指令
      await this._sendCommand('shutdown', {}).catch(() => {})
    } catch {
      // 强制杀死
    }

    // 等待进程退出
    await new Promise(resolve => {
      const timeout = setTimeout(() => {
        if (this.daemonProcess && !this.daemonProcess.killed) {
          this.daemonProcess.kill('SIGKILL')
        }
        resolve()
      }, 5000)

      if (this.daemonProcess) {
        this.daemonProcess.on('exit', () => {
          clearTimeout(timeout)
          resolve()
        })
      } else {
        clearTimeout(timeout)
        resolve()
      }
    })

    this.disconnectSocket()
    logger.info('VirtualDiskManager', '守护进程已停止')
  }

  // ==================== Socket 通信 ====================

  /**
   * 连接到守护进程的 IPC Socket
   */
  connectSocket() {
    if (this.socketClient && !this.socketClient.destroyed) return

    return new Promise((resolve, reject) => {
      const maxRetries = 10
      let retries = 0

      const tryConnect = () => {
        this.socketClient = net.createConnection(SOCKET_PATH, () => {
          logger.info('VirtualDiskManager', '已连接到守护进程')
          resolve()
        })

        this.socketClient.on('data', (data) => {
          this._handleSocketData(data)
        })

        this.socketClient.on('error', (err) => {
          retries++
          if (retries < maxRetries) {
            logger.debug('VirtualDiskManager', `重试连接 (${retries}/${maxRetries}): ${err.message}`)
            setTimeout(tryConnect, 500)
          } else {
            reject(new Error(`无法连接到守护进程: ${err.message}`))
          }
        })

        this.socketClient.on('close', () => {
          this.socketBuffer = ''
          logger.debug('VirtualDiskManager', 'Socket 连接已关闭')
        })
      }

      tryConnect()
    })
  }

  /**
   * 断开 Socket 连接
   */
  disconnectSocket() {
    if (this.socketClient) {
      try { this.socketClient.destroy() } catch { /* 忽略 */ }
      this.socketClient = null
    }
    this.socketBuffer = ''
    this.pendingRequests.clear()
  }

  /**
   * 处理 Socket 接收的数据
   */
  _handleSocketData(data) {
    this.socketBuffer += data.toString()
    const lines = this.socketBuffer.split('\n')
    this.socketBuffer = lines.pop()

    for (const line of lines) {
      if (!line.trim()) continue
      try {
        const message = JSON.parse(line)
        if (message.type === 'event') {
          this._emit(message.event, message.data)
        } else if (message.action) {
          // 查找对应的 Promise
          const pending = this.pendingRequests.get(message.action)
          if (pending) {
            this.pendingRequests.delete(message.action)
            if (message.success) {
              pending.resolve(message.data)
            } else {
              pending.reject(new Error(message.error || 'Unknown error'))
            }
          }
        }
      } catch (e) {
        logger.warn('VirtualDiskManager', `无效的 Socket 消息: ${line.substring(0, 100)}`)
      }
    }
  }

  /**
   * 发送命令到守护进程
   */
  _sendCommand(action, payload = {}) {
    return new Promise((resolve, reject) => {
      this.pendingRequests.set(action, { resolve, reject })
      const message = JSON.stringify({ action, payload }) + '\n'
      if (this.socketClient && !this.socketClient.destroyed) {
        this.socketClient.write(message)
      } else {
        this.pendingRequests.delete(action)
        reject(new Error('Not connected to daemon'))
      }
    })
  }

  // ==================== 公开 API ====================

  /**
   * 挂载虚拟磁盘
   */
  async mount(options = {}) {
    if (this.isMounted) {
      throw new Error('Already mounted')
    }

    this.mountPoint = options.mountPoint || DEFAULT_MOUNT_POINT
    this.config = { ...this.config, ...options }

    // 确保守护进程在运行
    if (!this.daemonProcess || this.daemonProcess.killed) {
      this.startDaemon()
      // 等待守护进程就绪
      await new Promise(resolve => setTimeout(resolve, 1000))
    }

    // 连接 Socket
    await this.connectSocket()

    // 发送挂载命令
    const result = await this._sendCommand('mount', {
      mountPoint: this.mountPoint,
      token: this.config.token,
      userId: this.config.userId,
      apiBaseUrl: this.config.apiBaseUrl,
      quota: options.quota,
      cacheMaxSize: this.config.cacheMaxSize
    })

    this.isMounted = true
    this._emit('mounted', { mountPoint: this.mountPoint, ...result })
    return result
  }

  /**
   * 卸载虚拟磁盘
   */
  async unmount() {
    if (!this.isMounted) return

    const result = await this._sendCommand('unmount', {})
    this.isMounted = false
    this._emit('unmounted', { mountPoint: this.mountPoint })
    return result
  }

  /**
   * 获取统计信息
   */
  async getStats() {
    return this._sendCommand('stats', {})
  }

  /**
   * 强制全量同步
   */
  async syncAll() {
    return this._sendCommand('syncAll', {})
  }

  /**
   * 清空缓存
   */
  async clearCache() {
    return this._sendCommand('clearCache', {})
  }

  /**
   * Ping 守护进程
   */
  async ping() {
    return this._sendCommand('ping', {})
  }

  /**
   * 更新认证信息
   */
  updateAuth({ token, userId, apiBaseUrl }) {
    if (token !== undefined) this.config.token = token
    if (userId !== undefined) this.config.userId = userId
    if (apiBaseUrl !== undefined) this.config.apiBaseUrl = apiBaseUrl
  }

  // ==================== 事件系统 ====================

  on(event, callback) {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, [])
    }
    this.eventListeners.get(event).push(callback)
    return () => this.off(event, callback)  // 返回取消订阅函数
  }

  off(event, callback) {
    const listeners = this.eventListeners.get(event)
    if (listeners) {
      const idx = listeners.indexOf(callback)
      if (idx >= 0) listeners.splice(idx, 1)
    }
  }

  _emit(event, data) {
    const listeners = this.eventListeners.get(event)
    if (listeners) {
      for (const cb of listeners) {
        try { cb(data) } catch (e) { logger.error('VirtualDiskManager', `事件处理错误: ${e.message}`) }
      }
    }
  }

  // ==================== WebDAV 兼容性检查 ====================

  /**
   * 检查虚拟磁盘功能是否可用
   *
   * WebDAV 方案使用操作系统原生 WebDAV 客户端，无需额外安装内核扩展。
   * - macOS: Finder 内置 WebDAV 支持 (macOS 10.0+)
   * - Windows: WebClient 服务 (Windows 2000+)
   * - Linux: davfs2 (可选安装)
   *
   * @returns {{ available: boolean, platform: string, method: string, note?: string }}
   */
  static checkAvailability() {
    const platform = process.platform

    if (platform === 'darwin') {
      return {
        available: true,
        platform: 'macOS',
        method: 'Finder WebDAV (osascript / mount_webdav)',
        mountInstruction: '在 Finder 中按 Cmd+K，输入服务器地址即可挂载'
      }
    }

    if (platform === 'win32') {
      return {
        available: true,
        platform: 'Windows',
        method: 'WebClient Service (net use)',
        mountInstruction: '使用 net use 命令映射网络驱动器，或通过"添加网络位置"向导'
      }
    }

    if (platform === 'linux') {
      // Linux 需要 davfs2 包
      const { execSync } = require('child_process')
      let davfsAvailable = false
      try {
        execSync('which mount.davfs', { encoding: 'utf-8', stdio: 'ignore' })
        davfsAvailable = true
      } catch { /* 未安装 */ }

      return {
        available: davfsAvailable,
        platform: 'Linux',
        method: 'davfs2',
        mountInstruction: davfsAvailable
          ? '使用 mount -t davfs 挂载'
          : '请安装 davfs2: sudo apt install davfs2'
      }
    }

    return {
      available: false,
      platform,
      method: 'unsupported',
      note: '当前平台不支持虚拟磁盘功能'
    }
  }

  /**
   * 获取虚拟磁盘挂载指南
   * @returns {{ title: string, description: string, steps: Array<{ method: string, instruction: string }> }}
   */
  static getMountGuide() {
    const platform = process.platform

    if (platform === 'darwin') {
      return {
        title: 'macOS 虚拟磁盘挂载',
        description: 'PrivateCloudDisk 使用 WebDAV 协议提供虚拟磁盘功能，通过 macOS Finder 即可挂载。',
        steps: [
          {
            method: '自动挂载 (推荐)',
            instruction: '在应用内点击"挂载虚拟磁盘"按钮，系统将自动通过 Finder 挂载'
          },
          {
            method: '手动挂载',
            instruction: '打开 Finder → 前往 → 连接服务器 (Cmd+K) → 输入服务器地址 → 连接'
          }
        ],
        note: '无需安装任何额外软件，macOS 原生支持 WebDAV'
      }
    }

    if (platform === 'win32') {
      return {
        title: 'Windows 虚拟磁盘挂载',
        description: 'PrivateCloudDisk 使用 WebDAV 协议提供虚拟磁盘功能。',
        steps: [
          {
            method: '自动挂载 (推荐)',
            instruction: '在应用内点击"挂载虚拟磁盘"按钮，系统将自动映射网络驱动器'
          },
          {
            method: '手动挂载',
            instruction: '文件资源管理器 → 此电脑 → 映射网络驱动器 → 输入服务器地址'
          }
        ],
        note: '确保 WebClient 服务已启动 (services.msc → WebClient)'
      }
    }

    return {
      title: 'Linux 虚拟磁盘挂载',
      description: '需要安装 davfs2 包来支持 WebDAV 挂载。',
      steps: [
        {
          method: '安装依赖',
          instruction: 'sudo apt install davfs2'
        },
        {
          method: '挂载',
          instruction: 'sudo mount -t davfs http://localhost:PORT /mnt/clouddisk'
        }
      ]
    }
  }

  // ==================== 生命周期 ====================

  /**
   * 销毁管理器
   */
  async destroy() {
    await this.stopDaemon()
    logger.info('VirtualDiskManager', '已销毁')
  }
}

module.exports = { VirtualDiskManager }