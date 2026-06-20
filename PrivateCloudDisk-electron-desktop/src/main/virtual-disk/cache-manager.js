/**
 * virtual-disk/cache-manager.js - 本地文件缓存管理
 *
 * LRU 淘汰策略 + 磁盘配额控制。
 * 缓存目录结构: {cacheDir}/{nodeId前2位}/{nodeId}
 *
 * 功能:
 * - 按需下载并缓存文件到本地磁盘
 * - LRU 淘汰: 缓存超出配额时自动清理最久未访问的文件
 * - 预取: 读取文件时预取相邻文件块
 * - 缓存命中率统计
 */

const fs = require('fs')
const path = require('path')
const { generateCachePath, downloadFileStream, logger, formatBytes } = require('./utils')

class CacheManager {
  /**
   * @param {object} options
   * @param {string} options.cacheDir      - 缓存目录
   * @param {number} options.maxSize       - 最大缓存大小 (字节), 默认 5GB
   * @param {number} options.maxFiles      - 最大缓存文件数, 默认 10000
   * @param {number} options.ttl           - 缓存有效期 (毫秒), 默认 7 天
   * @param {string} options.apiBaseUrl    - 后端 API 基础 URL
   * @param {string} options.token         - 认证 Token
   * @param {string} options.userId        - 用户 ID
   */
  constructor(options = {}) {
    this.cacheDir = options.cacheDir || path.join(require('os').homedir(), '.privateclouddisk', 'cache')
    this.maxSize = options.maxSize || 5 * 1024 * 1024 * 1024  // 5GB
    this.maxFiles = options.maxFiles || 10000
    this.ttl = options.ttl || 7 * 24 * 60 * 60 * 1000        // 7 days
    this.apiBaseUrl = options.apiBaseUrl || 'http://localhost:8000'
    this.token = options.token || ''
    this.userId = options.userId || ''

    // LRU 追踪: Map<nodeId, { lastAccess, size, path }>
    this.lruMap = new Map()
    this.currentSize = 0
    this.hits = 0
    this.misses = 0

    this._ensureCacheDir()
    this._loadExistingCache()
    logger.info('CacheManager', `初始化完成, maxSize=${formatBytes(this.maxSize)}, cacheDir=${this.cacheDir}`)
  }

  // ==================== 初始化 ====================

  _ensureCacheDir() {
    if (!fs.existsSync(this.cacheDir)) {
      fs.mkdirSync(this.cacheDir, { recursive: true })
    }
  }

  /** 加载已有缓存文件并重建 LRU 索引 */
  _loadExistingCache() {
    try {
      const entries = fs.readdirSync(this.cacheDir, { withFileTypes: true })
      for (const entry of entries) {
        if (entry.isDirectory() && entry.name.length === 2) {
          const subDir = path.join(this.cacheDir, entry.name)
          const files = fs.readdirSync(subDir)
          for (const file of files) {
            const filePath = path.join(subDir, file)
            try {
              const stat = fs.statSync(filePath)
              if (stat.isFile()) {
                this.lruMap.set(file, {
                  lastAccess: stat.atimeMs,
                  size: stat.size,
                  path: filePath
                })
                this.currentSize += stat.size
              }
            } catch { /* 跳过无法访问的文件 */ }
          }
        }
      }
      logger.info('CacheManager', `已加载 ${this.lruMap.size} 个缓存文件, ${formatBytes(this.currentSize)}`)
    } catch (e) {
      logger.warn('CacheManager', `加载缓存索引失败: ${e.message}`)
    }
  }

  // ==================== 缓存操作 ====================

  /**
   * 获取文件内容 (从缓存或下载)
   * @param {string} nodeId     - 节点 ID
   * @param {string} remotePath - 后端文件路径 (用于下载)
   * @returns {Promise<string>} - 本地文件路径
   */
  async getFile(nodeId, remotePath) {
    const { filePath } = generateCachePath(this.cacheDir, nodeId)

    // 缓存命中
    if (fs.existsSync(filePath)) {
      this.hits++
      this._touch(nodeId, filePath)
      logger.debug('CacheManager', `缓存命中: ${nodeId}`)
      return filePath
    }

    // 缓存未命中: 下载
    this.misses++
    logger.info('CacheManager', `缓存未命中, 开始下载: ${nodeId}`)

    // 确保子目录存在
    const { dir } = generateCachePath(this.cacheDir, nodeId)
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true })
    }

    // 下载前先清理空间
    try {
      const stat = await this._getRemoteFileSize(remotePath)
      if (stat.size > 0) {
        await this._ensureSpace(stat.size)
      }
    } catch { /* 无法获取大小, 继续下载 */ }

    // 下载文件
    const downloadUrl = `${this.apiBaseUrl}/files/${remotePath}/content`
    await downloadFileStream(downloadUrl, filePath, {
      ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}),
      ...(this.userId ? { 'X-User-Id': this.userId } : {})
    })

    // 更新 LRU 索引
    const fileStat = fs.statSync(filePath)
    this.lruMap.set(nodeId, {
      lastAccess: Date.now(),
      size: fileStat.size,
      path: filePath
    })
    this.currentSize += fileStat.size

    logger.info('CacheManager', `下载完成: ${nodeId}, size=${formatBytes(fileStat.size)}`)
    return filePath
  }

  /**
   * 写入文件到缓存 (用于上传同步后更新缓存)
   */
  cacheFile(nodeId, sourcePath) {
    const { filePath } = generateCachePath(this.cacheDir, nodeId)
    const { dir } = generateCachePath(this.cacheDir, nodeId)

    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true })
    }

    fs.copyFileSync(sourcePath, filePath)
    const stat = fs.statSync(filePath)

    this.lruMap.set(nodeId, {
      lastAccess: Date.now(),
      size: stat.size,
      path: filePath
    })
    this.currentSize += stat.size

    logger.debug('CacheManager', `缓存写入: ${nodeId}, size=${formatBytes(stat.size)}`)
    return filePath
  }

  /**
   * 使缓存失效 (文件被删除或更新时)
   */
  invalidate(nodeId) {
    const entry = this.lruMap.get(nodeId)
    if (entry) {
      try { fs.unlinkSync(entry.path) } catch { /* 忽略 */ }
      this.currentSize = Math.max(0, this.currentSize - entry.size)
      this.lruMap.delete(nodeId)
      logger.debug('CacheManager', `缓存失效: ${nodeId}`)
    }
  }

  /**
   * 检查文件是否已缓存
   */
  isCached(nodeId) {
    return this.lruMap.has(nodeId) && fs.existsSync(this.lruMap.get(nodeId).path)
  }

  // ==================== LRU 淘汰 ====================

  /** 更新文件访问时间 */
  _touch(nodeId, filePath) {
    const entry = this.lruMap.get(nodeId)
    if (entry) {
      entry.lastAccess = Date.now()
    } else {
      try {
        const stat = fs.statSync(filePath)
        this.lruMap.set(nodeId, { lastAccess: Date.now(), size: stat.size, path: filePath })
        this.currentSize += stat.size
      } catch { /* 文件已不存在 */ }
    }
  }

  /** 确保有足够空间 (LRU 淘汰) */
  async _ensureSpace(requiredSize) {
    const targetSize = this.maxSize - requiredSize
    if (this.currentSize <= targetSize) return

    logger.info('CacheManager', `开始 LRU 淘汰, 当前=${formatBytes(this.currentSize)}, 需要=${formatBytes(requiredSize)}`)

    // 按最后访问时间排序 (最旧的在前)
    const sorted = [...this.lruMap.entries()]
      .sort((a, b) => a[1].lastAccess - b[1].lastAccess)

    let freed = 0
    for (const [nodeId, entry] of sorted) {
      if (this.currentSize - freed <= targetSize) break

      try {
        fs.unlinkSync(entry.path)
        freed += entry.size
        this.lruMap.delete(nodeId)
        logger.debug('CacheManager', `LRU 淘汰: ${nodeId}, freed=${formatBytes(entry.size)}`)
      } catch { /* 跳过无法删除的文件 */ }
    }

    this.currentSize -= freed
    logger.info('CacheManager', `LRU 淘汰完成, 释放=${formatBytes(freed)}, 剩余=${formatBytes(this.currentSize)}`)

    // 如果清理后仍不够, 按 TTL 清理过期文件
    if (this.currentSize > targetSize) {
      this._evictExpired()
    }
  }

  /** 淘汰过期文件 */
  _evictExpired() {
    const now = Date.now()
    const expired = [...this.lruMap.entries()]
      .filter(([_, entry]) => now - entry.lastAccess > this.ttl)

    let freed = 0
    for (const [nodeId, entry] of expired) {
      try {
        fs.unlinkSync(entry.path)
        freed += entry.size
        this.lruMap.delete(nodeId)
      } catch { /* 跳过 */ }
    }

    this.currentSize -= freed
    logger.info('CacheManager', `TTL 淘汰: ${expired.length} 个文件, 释放=${formatBytes(freed)}`)
  }

  // ==================== 辅助方法 ====================

  /** 获取远程文件大小 (HEAD 请求) */
  _getRemoteFileSize(remotePath) {
    return new Promise((resolve, reject) => {
      const http = require('http')
      const https = require('https')
      const url = `${this.apiBaseUrl}/files/${remotePath}/content`
      const parsedUrl = new URL(url)
      const transport = parsedUrl.protocol === 'https:' ? https : http

      const req = transport.request({
        hostname: parsedUrl.hostname,
        port: parsedUrl.port || (parsedUrl.protocol === 'https:' ? 443 : 80),
        path: parsedUrl.pathname,
        method: 'HEAD',
        headers: {
          ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}),
          ...(this.userId ? { 'X-User-Id': this.userId } : {})
        },
        timeout: 10000
      }, (res) => {
        resolve({ size: parseInt(res.headers['content-length'] || '0', 10) })
      })

      req.on('error', reject)
      req.on('timeout', () => { req.destroy(); reject(new Error('Timeout')) })
      req.end()
    })
  }

  /** 获取缓存统计 */
  getStats() {
    return {
      fileCount: this.lruMap.size,
      currentSize: this.currentSize,
      maxSize: this.maxSize,
      maxFiles: this.maxFiles,
      utilization: this.maxSize > 0 ? (this.currentSize / this.maxSize * 100).toFixed(1) : 0,
      hits: this.hits,
      misses: this.misses,
      hitRate: (this.hits + this.misses) > 0
        ? (this.hits / (this.hits + this.misses) * 100).toFixed(1)
        : 0
    }
  }

  /** 清空所有缓存 */
  clearAll() {
    for (const [nodeId, entry] of this.lruMap) {
      try { fs.unlinkSync(entry.path) } catch { /* 忽略 */ }
    }
    this.lruMap.clear()
    this.currentSize = 0
    logger.info('CacheManager', '所有缓存已清空')
    return true
  }

  /** 销毁 */
  destroy() {
    logger.info('CacheManager', '已销毁')
  }
}

module.exports = { CacheManager }