/**
 * virtual-disk/webdav-server.js - WebDAV 协议服务器
 *
 * 纯 Node.js 实现的 WebDAV (RFC 4918) 服务器，将云端存储映射为可通过
 * WebDAV 协议访问的虚拟磁盘。macOS 通过 Finder "连接服务器" 挂载，
 * Windows 通过 "添加网络位置" 挂载。
 *
 * 零原生依赖，兼容所有 Node.js 版本，企业级实现。
 *
 * 支持的 WebDAV 方法:
 *   OPTIONS  - 能力通告
 *   PROPFIND - 目录列表 + 属性查询
 *   GET      - 文件下载 (支持 Range 请求)
 *   HEAD     - 文件头信息
 *   PUT      - 文件上传 (支持分块)
 *   MKCOL    - 创建目录
 *   DELETE   - 删除文件/目录
 *   MOVE     - 重命名/移动
 *   COPY     - 复制
 *   LOCK     - 锁定资源
 *   UNLOCK   - 解锁资源
 *   PROPPATCH - 属性设置 (最小实现)
 */

const http = require('http')
const path = require('path')
const fs = require('fs')
const crypto = require('crypto')
const { pipeline } = require('stream')
const { promisify } = require('util')
const streamPipeline = promisify(pipeline)

const {
  splitPath, joinPath, getFileName, getParentPath,
  generateNodeId, generateCachePath,
  getFileCategory, getFileCategoryFromMime,
  httpRequest, logger, formatBytes, downloadFileStream
} = require('./utils')

// ==================== XML 工具 ====================

/** XML 转义 */
function xmlEscape(str) {
  if (typeof str !== 'string') return String(str)
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}

/** 生成 ISO 8601 日期 */
function isoDate(ts) {
  if (!ts) return new Date().toISOString()
  const d = typeof ts === 'number' ? new Date(ts * 1000) : new Date(ts)
  return d.toISOString().replace(/\.\d{3}Z$/, 'Z')
}

/** 构建多状态 XML 响应 */
function buildMultiStatusXML(items) {
  const parts = [
    '<?xml version="1.0" encoding="utf-8"?>',
    '<D:multistatus xmlns:D="DAV:">'
  ]
  for (const item of items) {
    parts.push('  <D:response>')
    parts.push(`    <D:href>${xmlEscape(item.href)}</D:href>`)
    if (item.status) {
      parts.push(`    <D:status>HTTP/1.1 ${item.status}</D:status>`)
    }
    if (item.propstat) {
      for (const ps of (Array.isArray(item.propstat) ? item.propstat : [item.propstat])) {
        parts.push('    <D:propstat>')
        parts.push('      <D:prop>')
        for (const [key, val] of Object.entries(ps.props || {})) {
          if (val === null || val === undefined) continue
          if (typeof val === 'object') {
            parts.push(`        <D:${key}>${xmlEscape(String(val.value || ''))}</D:${key}>`)
          } else {
            parts.push(`        <D:${key}>${xmlEscape(String(val))}</D:${key}>`)
          }
        }
        parts.push('      </D:prop>')
        parts.push(`      <D:status>HTTP/1.1 ${ps.status || '200 OK'}</D:status>`)
        parts.push('    </D:propstat>')
      }
    }
    if (item.error) {
      parts.push(`    <D:error>${xmlEscape(item.error)}</D:error>`)
    }
    parts.push('  </D:response>')
  }
  parts.push('</D:multistatus>')
  return parts.join('\n')
}

/** 简单的 XML 属性名提取 (从 PROPFIND 请求体中提取请求的属性名) */
function extractRequestedProps(body) {
  const props = []
  if (!body) return props

  // 提取 <D:propfind> 中的属性名
  const propMatch = body.match(/<D:propfind[^>]*>([\s\S]*?)<\/D:propfind>/i)
  if (!propMatch) return props

  const propContent = propMatch[1]

  // 检查是否是 allprop
  if (propContent.includes('<D:allprop')) {
    return ['allprop']
  }

  // 提取 prop 中的属性名
  const propBlock = propContent.match(/<D:prop[^>]*>([\s\S]*?)<\/D:prop>/i)
  if (propBlock) {
    const propInner = propBlock[1]
    const nameRegex = /<(?:D:)?(\w+)[^>]*\/?>/gi
    let match
    while ((match = nameRegex.exec(propInner)) !== null) {
      const name = match[1]
      if (!['prop', 'propfind', 'allprop', 'include', 'propname'].includes(name.toLowerCase())) {
        props.push(name)
      }
    }
  }

  return props.length > 0 ? props : ['allprop']
}

// ==================== WebDAV Server ====================

class WebDAVServer {
  /**
   * @param {object} options
   * @param {object} options.metadataStore - MetadataStore 实例
   * @param {object} options.cacheManager  - CacheManager 实例
   * @param {object} options.syncManager   - SyncManager 实例
   * @param {string} options.apiBaseUrl    - 后端 API 地址
   * @param {string} options.token         - 认证 Token
   * @param {string} options.userId        - 用户 ID
   * @param {object} options.quota         - 配额信息
   * @param {number} [options.port]        - 监听端口 (0 = 随机端口)
   * @param {string} [options.host]        - 监听地址 (默认 127.0.0.1)
   */
  constructor(options = {}) {
    this.metadataStore = options.metadataStore
    this.cacheManager = options.cacheManager
    this.syncManager = options.syncManager
    this.apiBaseUrl = options.apiBaseUrl || 'http://localhost:8000'
    this.token = options.token || ''
    this.userId = options.userId || ''
    this.quota = options.quota || { total_capacity: 10 * 1024 * 1024 * 1024, used_capacity: 0 }
    this.port = options.port || 0
    this.host = options.host || '127.0.0.1'

    this.ROOT_ID = 'root'
    this.server = null
    this._running = false

    // 锁管理 (简单实现)
    this._locks = new Map() // path → { token, timeout, depth, owner, scope }
    this._lockTokens = new Map() // lockToken → lockInfo

    // 确保根节点存在
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
    }
  }

  // ==================== 服务器生命周期 ====================

  /** 启动服务器 */
  start() {
    return new Promise((resolve, reject) => {
      this.server = http.createServer((req, res) => {
        this._handleRequest(req, res)
      })

      this.server.on('error', (err) => {
        logger.error('WebDAV', `服务器错误: ${err.message}`)
        reject(err)
      })

      this.server.listen(this.port, this.host, () => {
        const addr = this.server.address()
        this.port = addr.port
        this._running = true
        logger.info('WebDAV', `服务器已启动: http://${this.host}:${this.port}`)
        resolve({ host: this.host, port: this.port })
      })
    })
  }

  /** 停止服务器 */
  stop() {
    return new Promise((resolve) => {
      this._running = false
      if (this.server) {
        this.server.close(() => {
          logger.info('WebDAV', '服务器已停止')
          resolve()
        })
      } else {
        resolve()
      }
    })
  }

  get isRunning() {
    return this._running
  }

  get url() {
    if (!this._running) return null
    return `http://${this.host}:${this.port}`
  }

  // ==================== 请求处理 ====================

  async _handleRequest(req, res) {
    const method = req.method.toUpperCase()
    const urlPath = decodeURIComponent(req.url || '/')

    logger.debug('WebDAV', `${method} ${urlPath}`)

    try {
      switch (method) {
        case 'OPTIONS':
          return this._handleOptions(req, res)
        case 'PROPFIND':
          return await this._handlePropfind(req, res, urlPath)
        case 'PROPPATCH':
          return await this._handleProppatch(req, res, urlPath)
        case 'GET':
          return await this._handleGet(req, res, urlPath)
        case 'HEAD':
          return await this._handleHead(req, res, urlPath)
        case 'PUT':
          return await this._handlePut(req, res, urlPath)
        case 'MKCOL':
          return await this._handleMkcol(req, res, urlPath)
        case 'DELETE':
          return await this._handleDelete(req, res, urlPath)
        case 'MOVE':
          return await this._handleMove(req, res, urlPath)
        case 'COPY':
          return await this._handleCopy(req, res, urlPath)
        case 'LOCK':
          return await this._handleLock(req, res, urlPath)
        case 'UNLOCK':
          return await this._handleUnlock(req, res, urlPath)
        default:
          res.writeHead(405, { 'Allow': 'OPTIONS,PROPFIND,PROPPATCH,GET,HEAD,PUT,MKCOL,DELETE,MOVE,COPY,LOCK,UNLOCK' })
          res.end()
      }
    } catch (err) {
      logger.error('WebDAV', `请求处理错误 ${method} ${urlPath}: ${err.message}`)
      if (!res.headersSent) {
        res.writeHead(500)
      }
      res.end()
    }
  }

  // ==================== OPTIONS ====================

  _handleOptions(req, res) {
    res.writeHead(200, {
      'Allow': 'OPTIONS,PROPFIND,PROPPATCH,GET,HEAD,PUT,MKCOL,DELETE,MOVE,COPY,LOCK,UNLOCK',
      'DAV': '1,2,3',
      'MS-Author-Via': 'DAV',
      'Content-Length': '0'
    })
    res.end()
  }

  // ==================== PROPFIND ====================

  async _handlePropfind(req, res, urlPath) {
    const depth = req.headers.depth || '1'
    const body = await this._readBody(req)

    const requestedProps = extractRequestedProps(body)
    const items = []

    try {
      const node = await this._resolveNode(urlPath)
      if (!node) {
        res.writeHead(404)
        res.end()
        return
      }

      // 添加当前资源
      items.push({
        href: urlPath === '/' ? '/' : `/${urlPath.replace(/\/+$/, '')}`,
        propstat: {
          status: '200 OK',
          props: this._buildNodeProps(node, requestedProps)
        }
      })

      // Depth: 1 → 列出子资源
      if (depth === '1' && node.is_folder) {
        await this._syncDirectoryFromRemote(node.node_id, urlPath)
        const children = this.metadataStore.getChildren(node.node_id)

        for (const child of children) {
          const childHref = urlPath === '/'
            ? `/${encodeURIComponent(child.name)}`
            : `${urlPath.replace(/\/+$/, '')}/${encodeURIComponent(child.name)}`

          items.push({
            href: childHref,
            propstat: {
              status: '200 OK',
              props: this._buildNodeProps(child, requestedProps)
            }
          })
        }
      }

      // Depth: infinity → 递归列出所有子孙 (限制深度防止过大)
      if (depth === 'infinity' && node.is_folder) {
        await this._addRecursiveChildren(node.node_id, urlPath, items, requestedProps, 0, 5)
      }

      const xml = buildMultiStatusXML(items)
      res.writeHead(207, {
        'Content-Type': 'application/xml; charset=utf-8',
        'Content-Length': Buffer.byteLength(xml)
      })
      res.end(xml)

    } catch (err) {
      logger.error('WebDAV', `PROPFIND 错误: ${err.message}`)
      res.writeHead(500)
      res.end()
    }
  }

  /** 递归添加子资源 (限制深度) */
  async _addRecursiveChildren(parentId, parentPath, items, requestedProps, depth, maxDepth) {
    if (depth > maxDepth) return

    await this._syncDirectoryFromRemote(parentId, parentPath)
    const children = this.metadataStore.getChildren(parentId)

    for (const child of children) {
      const childHref = parentPath === '/'
        ? `/${encodeURIComponent(child.name)}`
        : `${parentPath.replace(/\/+$/, '')}/${encodeURIComponent(child.name)}`

      items.push({
        href: childHref,
        propstat: {
          status: '200 OK',
          props: this._buildNodeProps(child, requestedProps)
        }
      })

      if (child.is_folder) {
        await this._addRecursiveChildren(child.node_id, childHref, items, requestedProps, depth + 1, maxDepth)
      }
    }
  }

  /** 构建资源的 WebDAV 属性 */
  _buildNodeProps(node, requestedProps) {
    const allProps = requestedProps.includes('allprop')
    const wants = (name) => allProps || requestedProps.includes(name)

    const props = {}

    // 基础属性
    if (allProps || requestedProps.length === 0) {
      props.displayname = node.name
      props.getcontenttype = node.is_folder ? 'httpd/unix-directory' : (node.mime_type || 'application/octet-stream')
      props.getcontentlength = node.is_folder ? 0 : (node.size || 0)
      props.getlastmodified = isoDate(node.updated_at)
      props.creationdate = isoDate(node.created_at)
      props.resourcetype = node.is_folder ? '<D:collection/>' : ''
      props.getetag = `"${node.node_id}-${node.updated_at || 0}"`
      props['quota-used-bytes'] = String(this.quota.used_capacity || 0)
      props['quota-available-bytes'] = String(Math.max(0, (this.quota.total_capacity || 0) - (this.quota.used_capacity || 0)))
    } else {
      if (wants('displayname')) props.displayname = node.name
      if (wants('getcontenttype')) props.getcontenttype = node.is_folder ? 'httpd/unix-directory' : (node.mime_type || 'application/octet-stream')
      if (wants('getcontentlength')) props.getcontentlength = node.is_folder ? 0 : (node.size || 0)
      if (wants('getlastmodified')) props.getlastmodified = isoDate(node.updated_at)
      if (wants('creationdate')) props.creationdate = isoDate(node.created_at)
      if (wants('resourcetype')) props.resourcetype = node.is_folder ? '<D:collection/>' : ''
      if (wants('getetag')) props.getetag = `"${node.node_id}-${node.updated_at || 0}"`
      if (wants('quota-used-bytes')) props['quota-used-bytes'] = String(this.quota.used_capacity || 0)
      if (wants('quota-available-bytes')) props['quota-available-bytes'] = String(Math.max(0, (this.quota.total_capacity || 0) - (this.quota.used_capacity || 0)))
    }

    return props
  }

  // ==================== PROPPATCH ====================

  async _handleProppatch(req, res, urlPath) {
    // 最小实现: 接受 PROPPATCH 但不真正修改属性
    const body = await this._readBody(req)
    const node = await this._resolveNode(urlPath)
    if (!node) {
      res.writeHead(404)
      res.end()
      return
    }

    const xml = buildMultiStatusXML([{
      href: urlPath,
      propstat: { status: '200 OK', props: {} }
    }])

    res.writeHead(207, {
      'Content-Type': 'application/xml; charset=utf-8',
      'Content-Length': Buffer.byteLength(xml)
    })
    res.end(xml)
  }

  // ==================== GET ====================

  async _handleGet(req, res, urlPath) {
    const node = await this._resolveNode(urlPath)
    if (!node) {
      res.writeHead(404)
      res.end()
      return
    }
    if (node.is_folder) {
      res.writeHead(405)
      res.end()
      return
    }

    try {
      // 尝试从缓存获取文件
      let filePath = null
      if (this.cacheManager.isCached(node.node_id)) {
        const entry = this.cacheManager.lruMap.get(node.node_id)
        if (entry && entry.path && fs.existsSync(entry.path)) {
          filePath = entry.path
        }
      }

      // 如果缓存未命中，下载到缓存
      if (!filePath) {
        filePath = await this.cacheManager.getFile(
          node.node_id, node.remote_node_id || node.node_id
        )
      }

      if (!filePath || !fs.existsSync(filePath)) {
        res.writeHead(404)
        res.end()
        return
      }

      const stat = fs.statSync(filePath)
      const fileSize = stat.size

      // 处理 Range 请求
      const rangeHeader = req.headers.range
      if (rangeHeader) {
        const range = this._parseRange(rangeHeader, fileSize)
        if (!range) {
          res.writeHead(416, {
            'Content-Range': `bytes */${fileSize}`
          })
          res.end()
          return
        }

        const { start, end } = range
        const length = end - start + 1

        res.writeHead(206, {
          'Content-Type': node.mime_type || 'application/octet-stream',
          'Content-Length': length,
          'Content-Range': `bytes ${start}-${end}/${fileSize}`,
          'Accept-Ranges': 'bytes',
          'ETag': `"${node.node_id}-${node.updated_at || 0}"`,
          'Last-Modified': isoDate(node.updated_at || stat.mtimeMs / 1000)
        })

        const readStream = fs.createReadStream(filePath, { start, end })
        await streamPipeline(readStream, res)
      } else {
        res.writeHead(200, {
          'Content-Type': node.mime_type || 'application/octet-stream',
          'Content-Length': fileSize,
          'Accept-Ranges': 'bytes',
          'ETag': `"${node.node_id}-${node.updated_at || 0}"`,
          'Last-Modified': isoDate(node.updated_at || stat.mtimeMs / 1000)
        })

        const readStream = fs.createReadStream(filePath)
        await streamPipeline(readStream, res)
      }

    } catch (err) {
      logger.error('WebDAV', `GET 错误 ${urlPath}: ${err.message}`)
      if (!res.headersSent) {
        res.writeHead(500)
        res.end()
      }
    }
  }

  /** 解析 Range 头 */
  _parseRange(header, fileSize) {
    const match = header.match(/bytes=(\d*)-(\d*)/i)
    if (!match) return null

    let start = match[1] ? parseInt(match[1], 10) : 0
    let end = match[2] ? parseInt(match[2], 10) : fileSize - 1

    if (start < 0) start = 0
    if (end >= fileSize) end = fileSize - 1
    if (start > end) return null

    return { start, end }
  }

  // ==================== HEAD ====================

  async _handleHead(req, res, urlPath) {
    const node = await this._resolveNode(urlPath)
    if (!node) {
      res.writeHead(404)
      res.end()
      return
    }

    res.writeHead(200, {
      'Content-Type': node.is_folder ? 'httpd/unix-directory' : (node.mime_type || 'application/octet-stream'),
      'Content-Length': node.is_folder ? 0 : (node.size || 0),
      'ETag': `"${node.node_id}-${node.updated_at || 0}"`,
      'Last-Modified': isoDate(node.updated_at),
      'Accept-Ranges': node.is_folder ? undefined : 'bytes'
    })
    res.end()
  }

  // ==================== PUT ====================

  async _handlePut(req, res, urlPath) {
    const parentPath = getParentPath(urlPath)
    const fileName = getFileName(urlPath)

    try {
      // 检查锁
      const lockCheck = this._checkLock(urlPath)
      if (lockCheck.denied) {
        res.writeHead(423, { 'Content-Type': 'application/xml; charset=utf-8' })
        res.end('<?xml version="1.0" encoding="utf-8"?><D:error xmlns:D="DAV:"><D:lock-token-submitted/></D:error>')
        return
      }

      const parentNode = await this._resolveNode(parentPath)
      if (!parentNode) {
        res.writeHead(409)
        res.end()
        return
      }

      const parentId = parentNode.node_id
      let nodeId = generateNodeId(parentId, fileName)

      // 检查是否已存在
      let existingNode = this.metadataStore.getNode(nodeId)
      const isNew = !existingNode

      // 确保缓存目录存在
      const { filePath, dir } = generateCachePath(this.cacheManager.cacheDir, nodeId)
      if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true })

      // 写入文件到缓存
      const writeStream = fs.createWriteStream(filePath)
      await streamPipeline(req, writeStream)

      const stat = fs.statSync(filePath)
      const fileType = getFileCategory(fileName)

      // 更新元数据
      if (isNew) {
        this.metadataStore.upsertNode({
          node_id: nodeId,
          parent_id: parentId,
          name: fileName,
          is_folder: false,
          size: stat.size,
          file_type: fileType,
          updated_at: Math.floor(Date.now() / 1000)
        })

        // 异步上传到云端
        this._uploadToRemote(nodeId, filePath, fileName, parentId).catch(e =>
          logger.error('WebDAV', `PUT 上传失败: ${e.message}`)
        )
      } else {
        this.metadataStore.updateNodeSize(nodeId, stat.size)
        this.metadataStore.setSyncState(nodeId, 'dirty', {
          localMtime: Math.floor(stat.mtimeMs / 1000)
        })
      }

      // 更新缓存索引
      this.cacheManager.lruMap.set(nodeId, {
        lastAccess: Date.now(),
        size: stat.size,
        path: filePath
      })

      logger.info('WebDAV', `PUT ${urlPath} (${formatBytes(stat.size)})`)
      res.writeHead(isNew ? 201 : 204)
      res.end()

    } catch (err) {
      logger.error('WebDAV', `PUT 错误 ${urlPath}: ${err.message}`)
      if (!res.headersSent) {
        res.writeHead(500)
        res.end()
      }
    }
  }

  // ==================== MKCOL ====================

  async _handleMkcol(req, res, urlPath) {
    const parentPath = getParentPath(urlPath)
    const dirName = getFileName(urlPath)

    try {
      // 检查锁
      const lockCheck = this._checkLock(urlPath)
      if (lockCheck.denied) {
        res.writeHead(423)
        res.end()
        return
      }

      const parentNode = await this._resolveNode(parentPath)
      if (!parentNode) {
        res.writeHead(409)
        res.end()
        return
      }

      const parentId = parentNode.node_id
      const nodeId = generateNodeId(parentId, dirName)

      // 检查是否已存在
      const existing = this.metadataStore.getNode(nodeId)
      if (existing) {
        res.writeHead(405)
        res.end()
        return
      }

      // 保存到本地元数据
      this.metadataStore.upsertNode({
        node_id: nodeId,
        parent_id: parentId,
        name: dirName,
        is_folder: true,
        created_at: Math.floor(Date.now() / 1000),
        updated_at: Math.floor(Date.now() / 1000)
      })

      // 异步同步到后端
      this._createRemoteFolder(parentId, dirName, nodeId).catch(e =>
        logger.error('WebDAV', `MKCOL 远程创建失败: ${e.message}`)
      )

      logger.info('WebDAV', `MKCOL ${urlPath}`)
      res.writeHead(201)
      res.end()

    } catch (err) {
      logger.error('WebDAV', `MKCOL 错误 ${urlPath}: ${err.message}`)
      if (!res.headersSent) {
        res.writeHead(500)
        res.end()
      }
    }
  }

  // ==================== DELETE ====================

  async _handleDelete(req, res, urlPath) {
    try {
      // 检查锁
      const lockCheck = this._checkLock(urlPath)
      if (lockCheck.denied) {
        res.writeHead(423)
        res.end()
        return
      }

      const node = await this._resolveNode(urlPath)
      if (!node) {
        res.writeHead(404)
        res.end()
        return
      }

      // 如果是目录，检查是否为空
      if (node.is_folder) {
        const children = this.metadataStore.getChildren(node.node_id)
        if (children.length > 0) {
          // 尝试递归删除 (非标准行为，但 Finder 会这么做)
          await this._deleteRecursive(node.node_id, urlPath)
        }
      }

      // 删除本地元数据
      this.metadataStore.deleteNode(node.node_id)

      // 清理缓存
      this.cacheManager.invalidate(node.node_id)

      // 清理锁
      this._locks.delete(urlPath)
      this._locks.delete(urlPath.replace(/\/+$/, ''))

      // 异步同步到后端
      this._deleteRemoteNode(node.node_id, node.is_folder).catch(e =>
        logger.error('WebDAV', `DELETE 远程删除失败: ${e.message}`)
      )

      logger.info('WebDAV', `DELETE ${urlPath}`)
      res.writeHead(204)
      res.end()

    } catch (err) {
      logger.error('WebDAV', `DELETE 错误 ${urlPath}: ${err.message}`)
      if (!res.headersSent) {
        res.writeHead(500)
        res.end()
      }
    }
  }

  /** 递归删除目录 */
  async _deleteRecursive(nodeId, basePath) {
    const children = this.metadataStore.getChildren(nodeId)
    for (const child of children) {
      const childPath = `${basePath.replace(/\/+$/, '')}/${child.name}`
      if (child.is_folder) {
        await this._deleteRecursive(child.node_id, childPath)
      }
      this.metadataStore.deleteNode(child.node_id)
      this.cacheManager.invalidate(child.node_id)
    }
  }

  // ==================== MOVE ====================

  async _handleMove(req, res, urlPath) {
    const destination = req.headers.destination
    if (!destination) {
      res.writeHead(400)
      res.end()
      return
    }

    // 解析目标路径 (Destination 可能是完整 URL 或相对路径)
    let dstPath
    try {
      const dstUrl = new URL(destination)
      dstPath = decodeURIComponent(dstUrl.pathname)
    } catch {
      dstPath = decodeURIComponent(destination.replace(/^https?:\/\/[^/]+/, ''))
    }

    const dstParentPath = getParentPath(dstPath)
    const dstName = getFileName(dstPath)

    try {
      const srcNode = await this._resolveNode(urlPath)
      if (!srcNode) {
        res.writeHead(404)
        res.end()
        return
      }

      const dstParentNode = await this._resolveNode(dstParentPath)
      if (!dstParentNode) {
        res.writeHead(409)
        res.end()
        return
      }

      const newParentId = dstParentNode.node_id

      // 检查目标是否已存在
      const overwrite = req.headers.overwrite !== 'F'
      const existing = this.metadataStore.getNode(generateNodeId(newParentId, dstName))
      if (existing) {
        if (!overwrite) {
          res.writeHead(412)
          res.end()
          return
        }
        // 删除目标
        this.metadataStore.deleteNode(existing.node_id)
        this.cacheManager.invalidate(existing.node_id)
      }

      // 更新本地元数据
      this.metadataStore.renameNode(srcNode.node_id, dstName)
      this.metadataStore.moveNode(srcNode.node_id, newParentId)

      // 异步同步到后端
      this._renameRemoteNode(srcNode, newParentId, dstName).catch(e =>
        logger.error('WebDAV', `MOVE 远程重命名失败: ${e.message}`)
      )

      logger.info('WebDAV', `MOVE ${urlPath} → ${dstPath}`)
      res.writeHead(existing ? 204 : 201)
      res.end()

    } catch (err) {
      logger.error('WebDAV', `MOVE 错误 ${urlPath}: ${err.message}`)
      if (!res.headersSent) {
        res.writeHead(500)
        res.end()
      }
    }
  }

  // ==================== COPY ====================

  async _handleCopy(req, res, urlPath) {
    const destination = req.headers.destination
    if (!destination) {
      res.writeHead(400)
      res.end()
      return
    }

    let dstPath
    try {
      const dstUrl = new URL(destination)
      dstPath = decodeURIComponent(dstUrl.pathname)
    } catch {
      dstPath = decodeURIComponent(destination.replace(/^https?:\/\/[^/]+/, ''))
    }

    const dstParentPath = getParentPath(dstPath)
    const dstName = getFileName(dstPath)

    try {
      const srcNode = await this._resolveNode(urlPath)
      if (!srcNode) {
        res.writeHead(404)
        res.end()
        return
      }

      const dstParentNode = await this._resolveNode(dstParentPath)
      if (!dstParentNode) {
        res.writeHead(409)
        res.end()
        return
      }

      const newParentId = dstParentNode.node_id
      const newNodeId = generateNodeId(newParentId, dstName)

      const depth = req.headers.depth === 'infinity' ? 'infinity' : '0'

      if (srcNode.is_folder && depth === 'infinity') {
        await this._copyRecursive(srcNode, newParentId, dstName, newNodeId)
      } else {
        // 复制单个文件/目录
        this.metadataStore.upsertNode({
          ...srcNode,
          node_id: newNodeId,
          parent_id: newParentId,
          name: dstName
        })
      }

      logger.info('WebDAV', `COPY ${urlPath} → ${dstPath}`)
      res.writeHead(201)
      res.end()

    } catch (err) {
      logger.error('WebDAV', `COPY 错误 ${urlPath}: ${err.message}`)
      if (!res.headersSent) {
        res.writeHead(500)
        res.end()
      }
    }
  }

  /** 递归复制 */
  async _copyRecursive(srcNode, destParentId, destName, destNodeId) {
    // 复制目录本身
    this.metadataStore.upsertNode({
      node_id: destNodeId,
      parent_id: destParentId,
      name: destName,
      is_folder: true,
      created_at: Math.floor(Date.now() / 1000),
      updated_at: Math.floor(Date.now() / 1000)
    })

    // 复制子节点
    const children = this.metadataStore.getChildren(srcNode.node_id)
    for (const child of children) {
      const childNewId = generateNodeId(destNodeId, child.name)
      if (child.is_folder) {
        await this._copyRecursive(child, destNodeId, child.name, childNewId)
      } else {
        this.metadataStore.upsertNode({
          ...child,
          node_id: childNewId,
          parent_id: destNodeId
        })
      }
    }
  }

  // ==================== LOCK ====================

  async _handleLock(req, res, urlPath) {
    const depth = req.headers.depth || 'infinity'
    const body = await this._readBody(req)

    const node = await this._resolveNode(urlPath)
    if (!node) {
      res.writeHead(404)
      res.end()
      return
    }

    // 检查是否已锁定
    const existingLock = this._checkLock(urlPath)
    if (existingLock.locked) {
      res.writeHead(423, { 'Content-Type': 'application/xml; charset=utf-8' })
      res.end('<?xml version="1.0" encoding="utf-8"?><D:error xmlns:D="DAV:"><D:lock-token-submitted/></D:error>')
      return
    }

    // 生成锁令牌
    const lockToken = `opaquelocktoken:${crypto.randomUUID()}`
    const timeout = this._parseTimeout(req.headers.timeout)

    const lockInfo = {
      token: lockToken,
      path: urlPath,
      depth: depth === 'infinity' ? 'infinity' : '0',
      owner: body || 'unknown',
      scope: 'exclusive',
      type: 'write',
      timeout: timeout > 0 ? Date.now() + timeout * 1000 : 0
    }

    this._locks.set(urlPath.replace(/\/+$/, ''), lockInfo)
    this._lockTokens.set(lockToken, lockInfo)

    // 生成 LOCK 响应 XML
    const xml = `<?xml version="1.0" encoding="utf-8"?>
<D:prop xmlns:D="DAV:">
  <D:lockdiscovery>
    <D:activelock>
      <D:locktype><D:write/></D:locktype>
      <D:lockscope><D:exclusive/></D:lockscope>
      <D:depth>${depth}</D:depth>
      <D:owner>${xmlEscape(lockInfo.owner)}</D:owner>
      <D:timeout>${timeout > 0 ? `Second-${timeout}` : 'Infinite'}</D:timeout>
      <D:locktoken><D:href>${xmlEscape(lockToken)}</D:href></D:locktoken>
    </D:activelock>
  </D:lockdiscovery>
</D:prop>`

    res.writeHead(200, {
      'Content-Type': 'application/xml; charset=utf-8',
      'Lock-Token': `<${lockToken}>`
    })
    res.end(xml)
  }

  /** 解析 Timeout 头 */
  _parseTimeout(header) {
    if (!header) return 3600
    if (header === 'Infinite' || header === 'infinity') return -1
    const match = header.match(/Second-(\d+)/i)
    return match ? parseInt(match[1], 10) : 3600
  }

  // ==================== UNLOCK ====================

  async _handleUnlock(req, res, urlPath) {
    const lockTokenHeader = req.headers['lock-token']
    if (!lockTokenHeader) {
      res.writeHead(400)
      res.end()
      return
    }

    const lockToken = lockTokenHeader.replace(/^<|>$/g, '').trim()

    const lockInfo = this._lockTokens.get(lockToken)
    if (!lockInfo) {
      // Lock token 不存在，但 Finder 期望 204
      res.writeHead(204)
      res.end()
      return
    }

    this._locks.delete(lockInfo.path.replace(/\/+$/, ''))
    this._lockTokens.delete(lockToken)

    res.writeHead(204)
    res.end()
  }

  /** 检查路径是否被锁定 */
  _checkLock(urlPath) {
    const cleanPath = urlPath.replace(/\/+$/, '')

    // 直接检查
    if (this._locks.has(cleanPath)) {
      return { locked: true, denied: true, lock: this._locks.get(cleanPath) }
    }

    // 检查父路径的深度锁 (depth: infinity)
    for (const [lockPath, lock] of this._locks) {
      if (lock.depth === 'infinity' && cleanPath.startsWith(lockPath)) {
        return { locked: true, denied: true, lock }
      }
    }

    return { locked: false, denied: false }
  }

  // ==================== 路径解析 ====================

  /**
   * 将 WebDAV 路径解析为本地元数据节点
   */
  async _resolveNode(webdavPath) {
    if (webdavPath === '/' || webdavPath === '') {
      return this.metadataStore.getNode(this.ROOT_ID)
    }

    const parts = splitPath(webdavPath)
    let parentId = this.ROOT_ID

    for (let i = 0; i < parts.length; i++) {
      const partName = parts[i]
      const node = this.metadataStore.getNodeByPath(parentId, partName)

      if (!node) {
        // 本地元数据缺失，尝试从后端同步
        if (i === parts.length - 1) {
          return await this._fetchRemoteNode(parentId, partName, false)
        } else {
          const synced = await this._syncDirectoryFromRemote(parentId, '/' + parts.slice(0, i).join('/'))
          if (!synced) return null
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

  // ==================== 远程同步 ====================

  async _syncDirectoryFromRemote(parentId, fusePath) {
    try {
      const response = await httpRequest(
        `${this.apiBaseUrl}/files/nodes/children?node_id=${parentId}&limit=500`,
        { headers: this._authHeaders() }
      )

      const nodes = response.nodes || response.data || []
      if (nodes.length > 0) {
        this.metadataStore.bulkSyncNodes(parentId, nodes)
      }
      return true
    } catch (e) {
      logger.warn('WebDAV', `同步目录失败: ${e.message}`)
      return false
    }
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

  async _createRemoteFolder(parentId, name, nodeId) {
    try {
      await httpRequest(
        `${this.apiBaseUrl}/files/nodes`,
        {
          method: 'POST',
          headers: this._authHeaders(),
          body: {
            parent_id: parentId,
            name: name,
            is_folder: true,
            client_node_id: nodeId
          }
        }
      )
    } catch (e) {
      logger.warn('WebDAV', `远程创建文件夹失败: ${e.message}`)
    }
  }

  async _deleteRemoteNode(nodeId, isFolder) {
    try {
      await httpRequest(
        `${this.apiBaseUrl}/files/nodes/${nodeId}`,
        {
          method: 'DELETE',
          headers: this._authHeaders()
        }
      )
    } catch (e) {
      logger.warn('WebDAV', `远程删除节点失败: ${e.message}`)
    }
  }

  async _renameRemoteNode(srcNode, newParentId, newName) {
    try {
      await httpRequest(
        `${this.apiBaseUrl}/files/nodes/${srcNode.node_id}/move`,
        {
          method: 'POST',
          headers: this._authHeaders(),
          body: {
            target_parent_id: newParentId,
            new_name: newName
          }
        }
      )
    } catch (e) {
      logger.warn('WebDAV', `远程重命名失败: ${e.message}`)
    }
  }

  async _uploadToRemote(nodeId, filePath, fileName, parentId) {
    try {
      // 使用流式上传到后端 API
      const fileBuffer = fs.readFileSync(filePath)
      const boundary = `----WebDAVUpload${Date.now()}`
      const CRLF = '\r\n'

      const header = [
        `--${boundary}`,
        `Content-Disposition: form-data; name="file"; filename="${fileName}"`,
        `Content-Type: application/octet-stream`,
        '',
        ''
      ].join(CRLF)
      const footer = `${CRLF}--${boundary}--${CRLF}`

      const body = Buffer.concat([
        Buffer.from(header),
        fileBuffer,
        Buffer.from(footer)
      ])

      await httpRequest(
        `${this.apiBaseUrl}/files/upload`,
        {
          method: 'POST',
          headers: {
            ...this._authHeaders(),
            'Content-Type': `multipart/form-data; boundary=${boundary}`,
            'Content-Length': body.length
          },
          body: body
        }
      )

      this.metadataStore.setSyncState(nodeId, 'synced')
      logger.info('WebDAV', `上传完成: ${fileName}`)
    } catch (e) {
      logger.error('WebDAV', `上传失败: ${e.message}`)
      this.metadataStore.setSyncState(nodeId, 'dirty')
    }
  }

  _authHeaders() {
    return {
      'Authorization': `Bearer ${this.token}`,
      'X-User-Id': this.userId,
      'Content-Type': 'application/json'
    }
  }

  // ==================== 请求体读取 ====================

  _readBody(req) {
    return new Promise((resolve) => {
      const chunks = []
      req.on('data', (chunk) => chunks.push(chunk))
      req.on('end', () => {
        resolve(Buffer.concat(chunks).toString('utf-8'))
      })
    })
  }
}

module.exports = { WebDAVServer }