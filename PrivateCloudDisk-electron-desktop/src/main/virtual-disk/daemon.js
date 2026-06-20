/**
 * virtual-disk/daemon.js - 虚拟磁盘守护进程
 *
 * 作为独立 Node.js 子进程运行，负责:
 * 1. 启动 WebDAV 服务器并通过系统命令挂载虚拟磁盘
 * 2. 通过 IPC (Unix Socket) 与 Electron 主进程通信
 * 3. 管理 CloudFS、CacheManager、SyncManager 的生命周期
 * 4. 处理文件变更同步任务
 *
 * 虚拟磁盘方案: WebDAV (RFC 4918) — 零原生依赖，利用操作系统原生 WebDAV 客户端
 *   - macOS: Finder "连接服务器" (Cmd+K) 或 osascript 自动挂载
 *   - Windows: net use 命令映射网络驱动器
 *   - Linux: davfs2 挂载
 *
 * 进程间通信协议 (JSON over Unix Socket):
 *   → mount    { action: 'mount',    payload: { mountPoint, token, userId, apiBaseUrl, quota } }
 *   → unmount  { action: 'unmount',  payload: {} }
 *   → stats    { action: 'stats',    payload: {} }
 *   → syncAll  { action: 'syncAll',  payload: {} }
 *   → clearCache { action: 'clearCache', payload: {} }
 *   → shutdown { action: 'shutdown', payload: {} }
 *
 *   ← response { action, success, data?, error? }
 *   ← event   { type: 'event', event: 'upload-progress'|'upload-complete'|..., data: {...} }
 */

const net = require('net')
const path = require('path')
const fs = require('fs')
const os = require('os')
const { CloudFS } = require('./cloud-fs')
const { MetadataStore } = require('./metadata-store')
const { CacheManager } = require('./cache-manager')
const { SyncManager } = require('./sync-manager')
const { logger, setLogLevel } = require('./utils')

// ==================== 配置 ====================

const SOCKET_PATH = process.env.VD_SOCKET_PATH || path.join(os.tmpdir(), 'privateclouddisk-vd.sock')
const DATA_DIR = process.env.VD_DATA_DIR || path.join(os.homedir(), '.privateclouddisk', 'vd-data')

// ==================== 全局状态 ====================

let cloudFS = null
let metadataStore = null
let cacheManager = null
let syncManager = null
let isMounted = false

// ==================== 初始化 ====================

function init() {
  // 确保数据目录存在
  if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true })
  }

  // 初始化子模块 (延迟创建，等待 mount 命令带来的配置)
  metadataStore = new MetadataStore(path.join(DATA_DIR, 'metadata.db'))

  logger.info('Daemon', `守护进程已启动, PID=${process.pid}`)
  logger.info('Daemon', `Socket: ${SOCKET_PATH}`)
  logger.info('Daemon', `Data: ${DATA_DIR}`)
}

// ==================== IPC 服务 ====================

function startIpcServer() {
  // 清理旧的 socket 文件
  try { fs.unlinkSync(SOCKET_PATH) } catch { /* 忽略 */ }

  const server = net.createServer((socket) => {
    let buffer = ''

    socket.on('data', (data) => {
      buffer += data.toString()
      // 处理以换行符分隔的 JSON 消息
      const lines = buffer.split('\n')
      buffer = lines.pop()  // 保留未完成的行

      for (const line of lines) {
        if (!line.trim()) continue
        try {
          const message = JSON.parse(line)
          handleMessage(message, socket)
        } catch (e) {
          sendResponse(socket, { success: false, error: `Invalid JSON: ${e.message}` })
        }
      }
    })

    socket.on('error', (err) => {
      logger.error('Daemon', `Socket error: ${err.message}`)
    })

    socket.on('close', () => {
      logger.debug('Daemon', 'Client disconnected')
    })
  })

  server.listen(SOCKET_PATH, () => {
    logger.info('Daemon', `IPC 服务已启动: ${SOCKET_PATH}`)
  })

  server.on('error', (err) => {
    logger.error('Daemon', `IPC 服务启动失败: ${err.message}`)
    process.exit(1)
  })

  // 进程退出时清理
  process.on('exit', () => {
    try { fs.unlinkSync(SOCKET_PATH) } catch { /* 忽略 */ }
  })
  process.on('SIGINT', shutdown)
  process.on('SIGTERM', shutdown)
}

// ==================== 消息处理 ====================

async function handleMessage(message, socket) {
  const { action, payload = {} } = message

  logger.info('Daemon', `收到指令: ${action}`)

  try {
    switch (action) {
      case 'mount':
        await handleMount(payload, socket)
        break
      case 'unmount':
        await handleUnmount(payload, socket)
        break
      case 'stats':
        handleStats(payload, socket)
        break
      case 'syncAll':
        await handleSyncAll(payload, socket)
        break
      case 'clearCache':
        await handleClearCache(payload, socket)
        break
      case 'shutdown':
        await handleShutdown(payload, socket)
        break
      case 'ping':
        sendResponse(socket, { success: true, data: { pong: true, pid: process.pid, mounted: isMounted } })
        break
      default:
        sendResponse(socket, { action, success: false, error: `Unknown action: ${action}` })
    }
  } catch (e) {
    logger.error('Daemon', `处理 ${action} 失败: ${e.message}`)
    sendResponse(socket, { action, success: false, error: e.message })
  }
}

// ==================== 操作处理 ====================

async function handleMount(payload, socket) {
  if (isMounted) {
    return sendResponse(socket, { action: 'mount', success: false, error: 'Already mounted' })
  }

  const {
    mountPoint,
    token,
    userId,
    apiBaseUrl = 'http://localhost:8000',
    quota = { total_capacity: 10 * 1024 * 1024 * 1024, used_capacity: 0 },
    cacheMaxSize = 5 * 1024 * 1024 * 1024  // 5GB
  } = payload

  if (!mountPoint) {
    return sendResponse(socket, { action: 'mount', success: false, error: 'mountPoint is required' })
  }

  // 确保挂载点存在
  if (!fs.existsSync(mountPoint)) {
    fs.mkdirSync(mountPoint, { recursive: true })
  }

  // 初始化各模块
  cacheManager = new CacheManager({
    cacheDir: path.join(DATA_DIR, 'cache'),
    maxSize: cacheMaxSize,
    apiBaseUrl,
    token,
    userId
  })

  syncManager = new SyncManager({
    watchDir: mountPoint,
    metadataStore,
    apiBaseUrl,
    token,
    userId,
    debounceMs: 2000,
    onEvent: (event, data) => {
      // 将同步事件转发给 Electron 主进程
      sendEvent(socket, event, data)
    }
  })

  cloudFS = new CloudFS({
    mountPoint,
    metadataStore,
    cacheManager,
    syncManager,
    apiBaseUrl,
    token,
    userId,
    quota
  })

  try {
    await cloudFS.mount({
      force: true,
      mkdir: true
    })
    isMounted = true

    // 启动文件监听
    syncManager.start()

    // 保存挂载状态
    metadataStore.setMountState('lastMount', {
      mountPoint,
      apiBaseUrl,
      mountedAt: Date.now()
    })

    logger.info('Daemon', `挂载成功: ${mountPoint}`)
    sendResponse(socket, {
      action: 'mount',
      success: true,
      data: {
        mountPoint,
        status: 'mounted',
        stats: getStats()
      }
    })
  } catch (e) {
    logger.error('Daemon', `挂载失败: ${e.message}`)
    sendResponse(socket, { action: 'mount', success: false, error: e.message })
  }
}

async function handleUnmount(payload, socket) {
  if (!isMounted) {
    return sendResponse(socket, { action: 'unmount', success: false, error: 'Not mounted' })
  }

  try {
    // 先停止同步
    if (syncManager) await syncManager.stop()

    // 卸载 WebDAV
    if (cloudFS) await cloudFS.unmount()

    isMounted = false
    logger.info('Daemon', `已卸载`)

    sendResponse(socket, {
      action: 'unmount',
      success: true,
      data: { status: 'unmounted' }
    })
  } catch (e) {
    logger.error('Daemon', `卸载失败: ${e.message}`)
    sendResponse(socket, { action: 'unmount', success: false, error: e.message })
  }
}

function handleStats(payload, socket) {
  sendResponse(socket, {
    action: 'stats',
    success: true,
    data: getStats()
  })
}

async function handleSyncAll(payload, socket) {
  if (!syncManager) {
    return sendResponse(socket, { action: 'syncAll', success: false, error: 'Not mounted' })
  }

  await syncManager.syncAll()
  sendResponse(socket, {
    action: 'syncAll',
    success: true,
    data: { message: 'Sync scheduled' }
  })
}

async function handleClearCache(payload, socket) {
  if (cacheManager) cacheManager.clearAll()
  sendResponse(socket, {
    action: 'clearCache',
    success: true,
    data: { message: 'Cache cleared' }
  })
}

async function handleShutdown(payload, socket) {
  sendResponse(socket, { action: 'shutdown', success: true, data: { message: 'Shutting down' } })
  await shutdown()
}

// ==================== 响应发送 ====================

let broadcastSocket = null  // 保持最后一个 socket 引用用于事件广播

function sendResponse(socket, data) {
  if (socket && !socket.destroyed) {
    socket.write(JSON.stringify(data) + '\n')
  }
}

function sendEvent(socket, event, data) {
  const message = { type: 'event', event, data }
  if (socket && !socket.destroyed) {
    socket.write(JSON.stringify(message) + '\n')
  }
}

// ==================== 统计信息 ====================

function getStats() {
  return {
    mounted: isMounted,
    mountPoint: cloudFS ? cloudFS.mountPoint : null,
    webdavUrl: cloudFS && cloudFS.webdavServer ? cloudFS.webdavServer.url : null,
    webdavPort: cloudFS && cloudFS.webdavServer ? cloudFS.webdavServer.port : null,
    metadata: metadataStore ? metadataStore.getStats() : null,
    cache: cacheManager ? cacheManager.getStats() : null,
    sync: syncManager ? syncManager.getStatus() : null,
    openFiles: cloudFS ? cloudFS.openFiles.size : 0,
    pid: process.pid,
    uptime: process.uptime(),
    memoryUsage: process.memoryUsage()
  }
}

// ==================== 关闭 ====================

async function shutdown() {
  logger.info('Daemon', '正在关闭...')

  try {
    if (syncManager) await syncManager.destroy()
    if (isMounted && cloudFS) {
      try { await cloudFS.unmount() } catch { /* 忽略 */ }
    }
    if (metadataStore) metadataStore.close()
    if (cacheManager) cacheManager.destroy()
  } catch (e) {
    logger.error('Daemon', `关闭错误: ${e.message}`)
  }

  try { fs.unlinkSync(SOCKET_PATH) } catch { /* 忽略 */ }

  logger.info('Daemon', '已关闭')
  process.exit(0)
}

// ==================== 启动 ====================

init()
startIpcServer()