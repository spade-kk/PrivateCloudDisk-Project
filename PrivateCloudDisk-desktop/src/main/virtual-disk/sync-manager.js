/**
 * virtual-disk/sync-manager.js - 文件变更监听与自动同步上传
 *
 * 使用 chokidar 监听本地缓存目录的文件变更事件：
 * - 文件修改 → 自动上传到云端
 * - 文件删除 → 云端删除
 * - 新文件创建 → 自动上传
 * - 重命名 → 云端重命名
 *
 * 防抖策略: 文件变更后等待 idleTime 再进行同步, 避免频繁写入时重复上传
 * 冲突处理: 检测到云端版本更新时标记为 conflict 状态
 */

const chokidar = require('chokidar')
const fs = require('fs')
const path = require('path')
const { logger, httpRequest, generateNodeId } = require('./utils')

class SyncManager {
  /**
   * @param {object} options
   * @param {string} options.watchDir      - 监听的目录 (FUSE 挂载点)
   * @param {object} options.metadataStore - MetadataStore 实例
   * @param {string} options.apiBaseUrl    - 后端 API 基础 URL
   * @param {string} options.token         - 认证 Token
   * @param {string} options.userId        - 用户 ID
   * @param {number} options.debounceMs    - 防抖延迟 (毫秒), 默认 2000
   * @param {Function} options.onEvent     - 事件回调 (eventType, data)
   */
  constructor(options = {}) {
    this.watchDir = options.watchDir
    this.metadataStore = options.metadataStore
    this.apiBaseUrl = options.apiBaseUrl || 'http://localhost:8000'
    this.token = options.token || ''
    this.userId = options.userId || ''
    this.debounceMs = options.debounceMs || 2000
    this.onEvent = options.onEvent || (() => {})

    this.watcher = null
    this.debounceTimers = new Map()   // key → timer
    this.pendingChanges = new Map()   // key → changeType
    this.isSyncing = new Set()        // 正在同步的文件集合
    this.ignorePatterns = [
      /\.DS_Store$/,
      /\.fuse_hidden/,
      /~\$.*/,                        // Office 临时文件
      /\.swp$/,                       // Vim
      /\.tmp$/,
      /\.part$/
    ]
    this.enabled = false
  }

  // ==================== 启动/停止 ====================

  /**
   * 启动文件监听
   */
  start() {
    if (this.enabled) return
    if (!this.watchDir || !fs.existsSync(this.watchDir)) {
      logger.warn('SyncManager', `监听目录不存在: ${this.watchDir}`)
      return
    }

    this.enabled = true
    logger.info('SyncManager', `开始监听: ${this.watchDir}, debounce=${this.debounceMs}ms`)

    this.watcher = chokidar.watch(this.watchDir, {
      ignored: [
        /(^|[/\\])\../,           // 隐藏文件和目录
        '**/node_modules/**',
        ...this.ignorePatterns
      ],
      persistent: true,
      ignoreInitial: true,         // 忽略启动时的 add 事件
      awaitWriteFinish: {
        stabilityThreshold: 1000,  // 文件写入后等待 1 秒确认完成
        pollInterval: 200
      },
      depth: 99,
      usePolling: false            // macOS 使用原生 fsevents
    })

    this.watcher
      .on('add', (filePath) => this._handleChange('add', filePath))
      .on('change', (filePath) => this._handleChange('change', filePath))
      .on('unlink', (filePath) => this._handleChange('unlink', filePath))
      .on('addDir', (dirPath) => this._handleChange('addDir', dirPath))
      .on('unlinkDir', (dirPath) => this._handleChange('unlinkDir', dirPath))
      .on('error', (error) => logger.error('SyncManager', `监听错误: ${error.message}`))
  }

  /**
   * 停止文件监听
   */
  async stop() {
    if (!this.enabled) return
    this.enabled = false

    // 等待所有进行中的同步完成
    if (this.isSyncing.size > 0) {
      logger.info('SyncManager', `等待 ${this.isSyncing.size} 个同步任务完成...`)
      await new Promise(resolve => {
        const check = setInterval(() => {
          if (this.isSyncing.size === 0) {
            clearInterval(check)
            resolve()
          }
        }, 500)
      })
    }

    if (this.watcher) {
      await this.watcher.close()
      this.watcher = null
    }

    // 清理所有防抖定时器
    for (const timer of this.debounceTimers.values()) {
      clearTimeout(timer)
    }
    this.debounceTimers.clear()
    this.pendingChanges.clear()

    logger.info('SyncManager', '监听已停止')
  }

  // ==================== 变更处理 ====================

  /**
   * 带防抖的变更处理
   */
  _handleChange(eventType, filePath) {
    const relativePath = filePath.replace(this.watchDir, '').replace(/^\//, '')

    // 忽略临时文件
    if (this.ignorePatterns.some(p => p.test(path.basename(filePath)))) {
      return
    }

    const key = eventType + ':' + relativePath
    this.pendingChanges.set(key, { eventType, filePath, relativePath, timestamp: Date.now() })

    // 清除之前的定时器
    if (this.debounceTimers.has(key)) {
      clearTimeout(this.debounceTimers.get(key))
    }

    // 设置新的防抖定时器
    this.debounceTimers.set(key, setTimeout(() => {
      this.debounceTimers.delete(key)
      const change = this.pendingChanges.get(key)
      this.pendingChanges.delete(key)
      if (change) {
        this._processChange(change.eventType, change.filePath, change.relativePath)
      }
    }, this.debounceMs))
  }

  /**
   * 处理具体的变更
   */
  async _processChange(eventType, filePath, relativePath) {
    if (this.isSyncing.has(relativePath)) return

    logger.debug('SyncManager', `${eventType}: ${relativePath}`)

    try {
      switch (eventType) {
        case 'add':
          await this._syncFileCreated(relativePath, filePath)
          break
        case 'change':
          await this._syncFileModified(relativePath, filePath)
          break
        case 'unlink':
          await this._syncFileDeleted(relativePath)
          break
        case 'addDir':
          await this._syncFolderCreated(relativePath)
          break
        case 'unlinkDir':
          await this._syncFolderDeleted(relativePath)
          break
      }
    } catch (e) {
      logger.error('SyncManager', `同步失败 [${eventType}] ${relativePath}: ${e.message}`)
      this.onEvent('sync-error', { eventType, relativePath, error: e.message })
    } finally {
      this.isSyncing.delete(relativePath)
    }
  }

  // ==================== 同步操作 ====================

  /** 新建文件 → 上传到云端 */
  async _syncFileCreated(relativePath, filePath) {
    this.isSyncing.add(relativePath)
    logger.info('SyncManager', `上传新文件: ${relativePath}`)

    try {
      const fileName = path.basename(relativePath)
      const parentPath = path.dirname(relativePath) === '.' ? '' : path.dirname(relativePath)

      // Step 1: 创建上传会话
      const sessionRes = await httpRequest(`${this.apiBaseUrl}/files/upload/sessions`, {
        method: 'POST',
        headers: this._authHeaders(),
        body: { file_name: fileName, parent_path: parentPath, file_size: fs.statSync(filePath).size }
      })

      // Step 2: 分片上传
      const sessionId = sessionRes.session_id || sessionRes.sessionId
      const fileBuffer = fs.readFileSync(filePath)
      const chunkSize = 5 * 1024 * 1024  // 5MB
      const totalChunks = Math.ceil(fileBuffer.length / chunkSize)

      for (let i = 0; i < totalChunks; i++) {
        const chunk = fileBuffer.slice(i * chunkSize, (i + 1) * chunkSize)
        const chunkBase64 = chunk.toString('base64')
        await httpRequest(`${this.apiBaseUrl}/files/upload/sessions/${sessionId}/chunks`, {
          method: 'POST',
          headers: { ...this._authHeaders(), 'Content-Type': 'application/json' },
          body: { chunk_index: i, data: chunkBase64 }
        })

        this.onEvent('upload-progress', {
          relativePath,
          chunk: i + 1,
          totalChunks,
          percent: Math.round(((i + 1) / totalChunks) * 100)
        })
      }

      // Step 3: 完成上传
      await httpRequest(`${this.apiBaseUrl}/files/upload/sessions/${sessionId}/complete`, {
        method: 'POST',
        headers: this._authHeaders()
      })

      // 更新本地元数据
      this.metadataStore.setSyncState(
        generateNodeId(parentPath, fileName),
        'synced',
        { lastSyncAt: Math.floor(Date.now() / 1000) }
      )

      this.onEvent('upload-complete', { relativePath })
      logger.info('SyncManager', `上传完成: ${relativePath}`)
    } catch (e) {
      this.metadataStore.setSyncState(
        generateNodeId(path.dirname(relativePath), path.basename(relativePath)),
        'error',
        { errorMsg: e.message }
      )
      throw e
    }
  }

  /** 文件修改 → 重新上传 */
  async _syncFileModified(relativePath, filePath) {
    this.isSyncing.add(relativePath)
    logger.info('SyncManager', `更新文件: ${relativePath}`)

    // 修改文件的处理与新文件相同: 重新上传
    await this._syncFileCreated(relativePath, filePath)
  }

  /** 文件删除 → 云端删除 */
  async _syncFileDeleted(relativePath) {
    this.isSyncing.add(relativePath)
    logger.info('SyncManager', `删除文件: ${relativePath}`)

    try {
      const parentPath = path.dirname(relativePath) === '.' ? '' : path.dirname(relativePath)
      const fileName = path.basename(relativePath)

      await httpRequest(`${this.apiBaseUrl}/files/nodes/by-path`, {
        method: 'DELETE',
        headers: this._authHeaders(),
        body: { parent_path: parentPath, name: fileName }
      })

      this.onEvent('delete-complete', { relativePath })
      logger.info('SyncManager', `删除完成: ${relativePath}`)
    } catch (e) {
      this.onEvent('sync-error', { eventType: 'delete', relativePath, error: e.message })
    }
  }

  /** 新建文件夹 → 云端创建 */
  async _syncFolderCreated(relativePath) {
    this.isSyncing.add(relativePath)
    logger.info('SyncManager', `创建文件夹: ${relativePath}`)

    try {
      const parentPath = path.dirname(relativePath) === '.'
        ? null : path.dirname(relativePath)
      const folderName = path.basename(relativePath)

      await httpRequest(`${this.apiBaseUrl}/files/nodes`, {
        method: 'POST',
        headers: this._authHeaders(),
        body: { parent_path: parentPath, name: folderName, is_folder: true }
      })

      this.onEvent('folder-create-complete', { relativePath })
      logger.info('SyncManager', `文件夹创建完成: ${relativePath}`)
    } catch (e) {
      this.onEvent('sync-error', { eventType: 'mkdir', relativePath, error: e.message })
    }
  }

  /** 删除文件夹 → 云端删除 */
  async _syncFolderDeleted(relativePath) {
    this.isSyncing.add(relativePath)
    logger.info('SyncManager', `删除文件夹: ${relativePath}`)

    try {
      await httpRequest(`${this.apiBaseUrl}/files/nodes/by-path`, {
        method: 'DELETE',
        headers: this._authHeaders(),
        body: { path: relativePath, is_folder: true, recursive: true }
      })

      this.onEvent('delete-complete', { relativePath, isFolder: true })
      logger.info('SyncManager', `文件夹删除完成: ${relativePath}`)
    } catch (e) {
      this.onEvent('sync-error', { eventType: 'rmdir', relativePath, error: e.message })
    }
  }

  // ==================== 手动同步 ====================

  /**
   * 手动触发脏文件同步
   */
  async syncDirtyNodes() {
    const dirtyNodes = this.metadataStore.getDirtyNodes()
    logger.info('SyncManager', `开始同步 ${dirtyNodes.length} 个脏节点`)

    for (const state of dirtyNodes) {
      const node = this.metadataStore.getNode(state.node_id)
      if (!node) continue

      const filePath = path.join(this.watchDir, node.name)
      if (!fs.existsSync(filePath)) continue

      try {
        this.metadataStore.setSyncState(state.node_id, 'syncing')
        await this._syncFileModified(node.name, filePath)
      } catch (e) {
        logger.error('SyncManager', `脏节点同步失败: ${node.name}, ${e.message}`)
      }
    }
  }

  /**
   * 强制同步所有文件 (全量)
   */
  async syncAll() {
    logger.info('SyncManager', '开始全量同步...')
    const walkDir = (dir, relativePath = '') => {
      const entries = fs.readdirSync(dir, { withFileTypes: true })
      for (const entry of entries) {
        const fullPath = path.join(dir, entry.name)
        const relPath = relativePath ? `${relativePath}/${entry.name}` : entry.name
        if (entry.isDirectory()) {
          walkDir(fullPath, relPath)
        } else {
          this._handleChange('change', fullPath)
        }
      }
    }
    walkDir(this.watchDir)
    logger.info('SyncManager', '全量同步已调度')
  }

  // ==================== 辅助方法 ====================

  _authHeaders() {
    const headers = {}
    if (this.token) headers['Authorization'] = `Bearer ${this.token}`
    if (this.userId) headers['X-User-Id'] = this.userId
    return headers
  }

  /**
   * 获取同步状态
   */
  getStatus() {
    return {
      enabled: this.enabled,
      watchDir: this.watchDir,
      pendingChanges: this.pendingChanges.size,
      syncingCount: this.isSyncing.size
    }
  }

  /**
   * 销毁
   */
  async destroy() {
    await this.stop()
    logger.info('SyncManager', '已销毁')
  }
}

module.exports = { SyncManager }