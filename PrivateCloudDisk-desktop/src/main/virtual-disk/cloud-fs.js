/**
 * virtual-disk/cloud-fs.js - 云端虚拟磁盘文件系统
 *
 * 通过 WebDAV 协议 (RFC 4918) 将云端存储映射为本地可挂载的网络磁盘。
 * macOS 通过 Finder "连接服务器" (Cmd+K → http://localhost:PORT) 挂载，
 * Windows 通过 "映射网络驱动器" 或 "添加网络位置" 挂载。
 *
 * 架构:
 *   CloudFS (本模块)
 *     ├── WebDAVServer     - 本地 WebDAV 服务器 (HTTP, 零原生依赖)
 *     ├── MetadataStore    - 本地元数据存储 (SQLite)
 *     ├── CacheManager     - 文件缓存管理 (LRU)
 *     └── SyncManager      - 文件变更同步 (chokidar)
 *
 * 与 FUSE 方案的对比:
 *   - 无需 macFUSE / WinFsp 等内核扩展
 *   - 零原生 Node.js 模块依赖，兼容所有 Node.js 版本
 *   - 利用操作系统原生 WebDAV 客户端，稳定可靠
 *   - 企业级标准协议 (RFC 4918)，被 SharePoint/NextCloud 等广泛使用
 */

const path = require('path')
const fs = require('fs')
const { WebDAVServer } = require('./webdav-server')
const {
  splitPath, joinPath, getFileName, getParentPath,
  generateNodeId, generateCachePath,
  getFileCategory, getFileCategoryFromMime,
  httpRequest, logger, formatBytes
} = require('./utils')

class CloudFS {
  /**
   * @param {object} options
   * @param {string} options.mountPoint    - 挂载点 (用于 WebDAV mount 命令)
   * @param {object} options.metadataStore - MetadataStore 实例
   * @param {object} options.cacheManager  - CacheManager 实例
   * @param {object} options.syncManager   - SyncManager 实例
   * @param {string} options.apiBaseUrl    - 后端 API 地址
   * @param {string} options.token         - 认证 Token
   * @param {string} options.userId        - 用户 ID
   * @param {object} options.quota         - 配额 { total_capacity, used_capacity }
   */
  constructor(options = {}) {
    this.mountPoint = options.mountPoint
    this.metadataStore = options.metadataStore
    this.cacheManager = options.cacheManager
    this.syncManager = options.syncManager
    this.apiBaseUrl = options.apiBaseUrl || 'http://localhost:8000'
    this.token = options.token || ''
    this.userId = options.userId || ''
    this.quota = options.quota || { total_capacity: 10 * 1024 * 1024 * 1024, used_capacity: 0 }

    // WebDAV 服务器
    this.webdavServer = null

    // 打开的文件句柄 (用于兼容旧接口)
    this.openFiles = new Map()
    this.nextFd = 1

    // 根目录节点 ID
    this.ROOT_ID = 'root'

    this._ensureRootNode()
  }

  _ensureRootNode() {
    const root = this.metadataStore.getNode(this.ROOT_ID)
    if (!root) {
      this.metadataStore.upsertNode({
        node_id: this.ROOT_ID,
        parent_id: this.ROOT_ID,
        name: '/',
        is_folder: true,
        created_at: Math.floor(Date.now() / 1000),
        updated_at: Math.floor(Date.now() / 1000)
      })
      logger.info('CloudFS', '根节点已创建')
    }
  }

  // ==================== 挂载 / 卸载 ====================

  /**
   * 挂载虚拟磁盘
   *
   * 1. 启动本地 WebDAV 服务器
   * 2. 使用系统命令挂载 WebDAV 共享
   *
   * @param {object} options
   * @param {boolean} [options.force]  - 强制挂载
   * @param {boolean} [options.mkdir]  - 自动创建挂载点
   */
  async mount(options = {}) {
    if (this.webdavServer && this.webdavServer.isRunning) {
      throw new Error('Already mounted')
    }

    // 确保挂载点存在
    if (options.mkdir && !fs.existsSync(this.mountPoint)) {
      fs.mkdirSync(this.mountPoint, { recursive: true })
    }

    // 1. 启动 WebDAV 服务器
    this.webdavServer = new WebDAVServer({
      metadataStore: this.metadataStore,
      cacheManager: this.cacheManager,
      syncManager: this.syncManager,
      apiBaseUrl: this.apiBaseUrl,
      token: this.token,
      userId: this.userId,
      quota: this.quota,
      host: '127.0.0.1',
      port: 0 // 随机端口
    })

    const serverInfo = await this.webdavServer.start()
    logger.info('CloudFS', `WebDAV 服务器已启动: ${serverInfo.host}:${serverInfo.port}`)

    // 2. 尝试挂载到系统
    const mountResult = await this._mountSystem(serverInfo)

    logger.info('CloudFS', `挂载成功: ${this.mountPoint} → ${this.webdavServer.url}`)
    return mountResult
  }

  /**
   * 卸载虚拟磁盘
   */
  async unmount() {
    if (this.webdavServer && this.webdavServer.isRunning) {
      // 先尝试系统卸载
      await this._unmountSystem().catch(() => {})

      // 停止 WebDAV 服务器
      await this.webdavServer.stop()
      this.webdavServer = null
    }

    logger.info('CloudFS', '已卸载')
  }

  /**
   * 使用系统命令挂载 WebDAV 共享
   */
  async _mountSystem(serverInfo) {
    const { execSync } = require('child_process')
    const platform = process.platform
    const url = `http://${serverInfo.host}:${serverInfo.port}`

    try {
      if (platform === 'darwin') {
        return await this._mountMacOS(url)
      } else if (platform === 'win32') {
        return await this._mountWindows(url)
      } else {
        // Linux: 使用 davfs2
        return await this._mountLinux(url)
      }
    } catch (e) {
      logger.warn('CloudFS', `系统挂载失败 (WebDAV 服务器仍运行): ${e.message}`)
      logger.info('CloudFS', `请手动挂载: 在 Finder 中按 Cmd+K 连接 ${url}`)
      return {
        mounted: 'server_only',
        url,
        mountPoint: this.mountPoint,
        manualMount: true,
        hint: `在 Finder 中按 Cmd+K，输入 ${url} 即可挂载`
      }
    }
  }

  /**
   * macOS 挂载: 使用 osascript 通过 Finder 挂载 WebDAV
   */
  async _mountMacOS(url) {
    const { execSync } = require('child_process')

    try {
      // 方法 1: 使用 mount_webdav (需要 sudo)
      // 方法 2: 使用 osascript 通过 Finder 挂载 (推荐)
      const script = `
        try
          mount volume "${url}"
        on error errMsg
          return "ERROR: " & errMsg
        end try
        return "OK"
      `
      const result = execSync(`osascript -e '${script.replace(/'/g, "'\\''")}'`, {
        encoding: 'utf-8',
        timeout: 15000
      }).trim()

      if (result.startsWith('ERROR')) {
        throw new Error(result)
      }

      logger.info('CloudFS', `macOS Finder 挂载成功: ${url}`)
      return { mounted: true, url, method: 'osascript', result }
    } catch (e) {
      logger.warn('CloudFS', `osascript 挂载失败，尝试 mount_webdav: ${e.message}`)

      // 回退: 尝试 mount_webdav
      try {
        // mount_webdav 需要管理员权限，使用 -S 跳过证书验证
        execSync(`/sbin/mount_webdav -S ${url} "${this.mountPoint}"`, {
          encoding: 'utf-8',
          timeout: 15000
        })
        return { mounted: true, url, method: 'mount_webdav' }
      } catch (e2) {
        throw new Error(`macOS 挂载失败: ${e2.message}`)
      }
    }
  }

  /**
   * Windows 挂载: 使用 net use 命令
   */
  async _mountWindows(url) {
    const { execSync } = require('child_process')

    try {
      // 获取可用盘符
      const driveLetter = this._findAvailableDriveLetter()
      execSync(`net use ${driveLetter}: ${url}`, {
        encoding: 'utf-8',
        timeout: 15000
      })
      logger.info('CloudFS', `Windows 挂载成功: ${driveLetter}: → ${url}`)
      return { mounted: true, url, method: 'net_use', drive: driveLetter }
    } catch (e) {
      throw new Error(`Windows 挂载失败: ${e.message}`)
    }
  }

  /**
   * Linux 挂载: 使用 mount.davfs
   */
  async _mountLinux(url) {
    const { execSync } = require('child_process')

    try {
      execSync(`mount -t davfs ${url} "${this.mountPoint}" -o noexec,rw,user`, {
        encoding: 'utf-8',
        timeout: 15000
      })
      return { mounted: true, url, method: 'davfs' }
    } catch (e) {
      throw new Error(`Linux 挂载失败: ${e.message}. 请安装 davfs2: sudo apt install davfs2`)
    }
  }

  /**
   * 查找可用的 Windows 盘符
   */
  _findAvailableDriveLetter() {
    const used = new Set()
    for (let i = 65; i <= 90; i++) {
      const letter = String.fromCharCode(i)
      try {
        require('fs').accessSync(`${letter}:\\`)
        used.add(letter)
      } catch {
        // 不可访问
      }
    }
    // 从 Z 开始反向查找可用盘符
    for (let i = 90; i >= 68; i--) {
      const letter = String.fromCharCode(i)
      if (!used.has(letter)) return letter
    }
    return 'Z'
  }

  /**
   * 系统卸载
   */
  async _unmountSystem() {
    const { execSync } = require('child_process')
    const platform = process.platform

    try {
      if (platform === 'darwin') {
        // 卸载 Finder 挂载的卷
        execSync(`diskutil unmount "${this.mountPoint}" 2>/dev/null || umount "${this.mountPoint}" 2>/dev/null`, {
          encoding: 'utf-8',
          timeout: 10000
        })
      } else if (platform === 'win32') {
        execSync(`net use * /delete /y 2>nul`, {
          encoding: 'utf-8',
          timeout: 10000
        })
      } else {
        execSync(`umount "${this.mountPoint}"`, {
          encoding: 'utf-8',
          timeout: 10000
        })
      }
    } catch (e) {
      logger.warn('CloudFS', `系统卸载失败 (可能已手动卸载): ${e.message}`)
    }
  }

  // ==================== 目录操作 (兼容旧接口) ====================

  /**
   * 同步远程目录内容
   */
  async syncDirectory(nodeId) {
    try {
      const response = await httpRequest(
        `${this.apiBaseUrl}/files/nodes/children?node_id=${nodeId}&limit=500`,
        { headers: this._authHeaders() }
      )
      const nodes = response.nodes || response.data || []
      if (nodes.length > 0) {
        this.metadataStore.bulkSyncNodes(nodeId, nodes)
      }
      return nodes
    } catch (e) {
      logger.warn('CloudFS', `同步目录失败: ${e.message}`)
      return []
    }
  }

  /**
   * 获取目录子节点
   */
  getChildren(nodeId) {
    return this.metadataStore.getChildren(nodeId)
  }

  /**
   * 解析路径到节点
   */
  async resolveNode(fusePath) {
    if (fusePath === '/' || fusePath === '') {
      return this.metadataStore.getNode(this.ROOT_ID)
    }

    const parts = splitPath(fusePath)
    let parentId = this.ROOT_ID

    for (let i = 0; i < parts.length; i++) {
      const partName = parts[i]
      const node = this.metadataStore.getNodeByPath(parentId, partName)

      if (!node) {
        // 本地元数据缺失，尝试从后端同步
        if (i === parts.length - 1) {
          return await this._fetchRemoteNode(parentId, partName, false)
        } else {
          await this.syncDirectory(parentId)
          const refetched = this.metadataStore.getNodeByPath(parentId, partName)
          if (!refetched) return null
          parentId = refetched.node_id
        }
      } else {
        parentId = node.node_id
      }
    }

    return this.metadataStore.getNode(parentId)
  }

  async _fetchRemoteNode(parentId, name, isFolder) {
    try {
      const response = await httpRequest(
        `${this.apiBaseUrl}/files/nodes/lookup?parent_id=${parentId}&name=${encodeURIComponent(name)}`,
        { headers: this._authHeaders() }
      )
      const data = response.node || response.data
      if (data) {
        this.metadataStore.upsertNode(data)
        return this.metadataStore.getNode(data.node_id || data.id)
      }
      return null
    } catch {
      return null
    }
  }

  _authHeaders() {
    return {
      'Authorization': `Bearer ${this.token}`,
      'X-User-Id': this.userId,
      'Content-Type': 'application/json'
    }
  }

  // ==================== 统计信息 ====================

  getStatus() {
    return {
      mounted: !!(this.webdavServer && this.webdavServer.isRunning),
      mountPoint: this.mountPoint,
      url: this.webdavServer ? this.webdavServer.url : null,
      port: this.webdavServer ? this.webdavServer.port : null,
      openFiles: this.openFiles.size
    }
  }
}

module.exports = { CloudFS }