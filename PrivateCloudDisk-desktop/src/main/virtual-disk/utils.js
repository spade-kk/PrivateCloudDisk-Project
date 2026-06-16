/**
 * virtual-disk/utils.js - 工具函数
 *
 * 提供虚拟磁盘模块通用的工具函数：
 * - 路径处理 (FUSE 路径 ↔ Node ID 转换)
 * - 文件类型识别 (MIME → 类别)
 * - 网络请求封装 (与后端 API 通信)
 * - 日志系统
 */

const path = require('path')
const crypto = require('crypto')
const http = require('http')
const https = require('https')

// ==================== 路径工具 ====================

/** 将 FUSE 路径标准化: 去掉首尾斜杠, 转为数组 */
function splitPath(fusePath) {
  const clean = fusePath.replace(/^\/+|\/+$/g, '')
  if (!clean) return []
  return clean.split('/').filter(Boolean)
}

/** 拼接为 FUSE 路径 */
function joinPath(...segments) {
  return '/' + segments.filter(Boolean).join('/')
}

/** 从 FUSE 路径获取文件名 */
function getFileName(fusePath) {
  const parts = splitPath(fusePath)
  return parts.length > 0 ? parts[parts.length - 1] : '/'
}

/** 从 FUSE 路径获取父路径 */
function getParentPath(fusePath) {
  const parts = splitPath(fusePath)
  if (parts.length === 0) return '/'
  return '/' + parts.slice(0, -1).join('/')
}

// ==================== 哈希工具 ====================

/** 生成 Node ID (基于父路径 + 文件名) */
function generateNodeId(parentPath, name) {
  const hash = crypto.createHash('md5').update(`${parentPath}::${name}`).digest('hex')
  return `fuse-${hash.substring(0, 16)}`
}

/** 生成文件的本地缓存路径 (基于 Node ID) */
function generateCachePath(cacheDir, nodeId) {
  const subDir = nodeId.substring(0, 2)
  const fullDir = path.join(cacheDir, subDir)
  return { dir: fullDir, filePath: path.join(fullDir, nodeId) }
}

// ==================== 文件类型工具 ====================

/** MIME 类型 → 文件类别 */
const MIME_CATEGORY_MAP = {
  'image/': 'image',
  'video/': 'video',
  'audio/': 'audio',
  'text/': 'document',
  'application/pdf': 'document',
  'application/msword': 'document',
  'application/vnd.openxmlformats-officedocument.wordprocessingml': 'document',
  'application/vnd.ms-excel': 'document',
  'application/vnd.openxmlformats-officedocument.spreadsheetml': 'document',
  'application/vnd.ms-powerpoint': 'document',
  'application/vnd.openxmlformats-officedocument.presentationml': 'document',
  'application/zip': 'archive',
  'application/x-rar-compressed': 'archive',
  'application/x-7z-compressed': 'archive',
  'application/gzip': 'archive',
  'application/x-tar': 'archive'
}

/** 扩展名 → 文件类别 */
const EXT_CATEGORY_MAP = {
  '.jpg': 'image', '.jpeg': 'image', '.png': 'image', '.gif': 'image',
  '.webp': 'image', '.bmp': 'image', '.svg': 'image', '.ico': 'image',
  '.mp4': 'video', '.avi': 'video', '.mov': 'video', '.mkv': 'video',
  '.wmv': 'video', '.flv': 'video', '.webm': 'video',
  '.mp3': 'audio', '.wav': 'audio', '.flac': 'audio', '.aac': 'audio',
  '.ogg': 'audio', '.wma': 'audio', '.m4a': 'audio',
  '.pdf': 'document', '.doc': 'document', '.docx': 'document',
  '.xls': 'document', '.xlsx': 'document', '.ppt': 'document',
  '.pptx': 'document', '.txt': 'document', '.md': 'document',
  '.csv': 'document', '.rtf': 'document',
  '.zip': 'archive', '.rar': 'archive', '.7z': 'archive',
  '.tar': 'archive', '.gz': 'archive', '.bz2': 'archive'
}

function getFileCategory(fileName) {
  const ext = path.extname(fileName).toLowerCase()
  return EXT_CATEGORY_MAP[ext] || 'other'
}

function getFileCategoryFromMime(mimeType) {
  if (!mimeType) return 'other'
  for (const [prefix, category] of Object.entries(MIME_CATEGORY_MAP)) {
    if (mimeType.startsWith(prefix)) return category
  }
  return 'other'
}

// ==================== 网络工具 ====================

/** HTTP JSON 请求封装 */
function httpRequest(url, options = {}) {
  return new Promise((resolve, reject) => {
    const parsedUrl = new URL(url)
    const isHttps = parsedUrl.protocol === 'https:'
    const transport = isHttps ? https : http

    const reqOptions = {
      hostname: parsedUrl.hostname,
      port: parsedUrl.port || (isHttps ? 443 : 80),
      path: parsedUrl.pathname + parsedUrl.search,
      method: options.method || 'GET',
      headers: {
        'Content-Type': 'application/json',
        ...options.headers
      },
      timeout: options.timeout || 30000
    }

    const req = transport.request(reqOptions, (res) => {
      const chunks = []
      res.on('data', (chunk) => chunks.push(chunk))
      res.on('end', () => {
        const body = Buffer.concat(chunks).toString()
        try {
          const data = JSON.parse(body)
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(data)
          } else {
            reject(new Error(`HTTP ${res.statusCode}: ${JSON.stringify(data)}`))
          }
        } catch {
          reject(new Error(`Invalid JSON response: ${body.substring(0, 200)}`))
        }
      })
    })

    req.on('error', reject)
    req.on('timeout', () => { req.destroy(); reject(new Error('Request timeout')) })

    if (options.body) {
      req.write(typeof options.body === 'string' ? options.body : JSON.stringify(options.body))
    }
    req.end()
  })
}

/** 流式下载文件到本地 */
function downloadFileStream(url, destPath, headers = {}) {
  return new Promise((resolve, reject) => {
    const parsedUrl = new URL(url)
    const isHttps = parsedUrl.protocol === 'https:'
    const transport = isHttps ? https : http
    const fs = require('fs')

    transport.get(url, { headers }, (res) => {
      if (res.statusCode === 302 || res.statusCode === 301) {
        return downloadFileStream(res.headers.location, destPath, headers)
          .then(resolve).catch(reject)
      }
      if (res.statusCode !== 200) {
        return reject(new Error(`Download failed: HTTP ${res.statusCode}`))
      }
      const fileStream = fs.createWriteStream(destPath)
      res.pipe(fileStream)
      fileStream.on('finish', () => { fileStream.close(); resolve(destPath) })
      fileStream.on('error', reject)
    }).on('error', reject)
  })
}

// ==================== 日志 ====================

const LOG_LEVELS = { DEBUG: 0, INFO: 1, WARN: 2, ERROR: 3 }
let currentLogLevel = LOG_LEVELS.INFO

function setLogLevel(level) {
  currentLogLevel = LOG_LEVELS[level] || LOG_LEVELS.INFO
}

function log(level, tag, message, data) {
  if (LOG_LEVELS[level] < currentLogLevel) return
  const timestamp = new Date().toISOString()
  const prefix = `[${timestamp}] [${level.padEnd(5)}] [${tag}]`
  if (data !== undefined) {
    console.log(prefix, message, JSON.stringify(data, null, 0))
  } else {
    console.log(prefix, message)
  }
}

const logger = {
  debug: (tag, msg, data) => log('DEBUG', tag, msg, data),
  info: (tag, msg, data) => log('INFO', tag, msg, data),
  warn: (tag, msg, data) => log('WARN', tag, msg, data),
  error: (tag, msg, data) => log('ERROR', tag, msg, data)
}

// ==================== 格式化 ====================

/** 字节数格式化 */
function formatBytes(bytes) {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + units[i]
}

/** 时间戳格式化 */
function formatTimestamp(ts) {
  if (!ts) return ''
  return new Date(ts * 1000).toISOString()
}

module.exports = {
  splitPath,
  joinPath,
  getFileName,
  getParentPath,
  generateNodeId,
  generateCachePath,
  getFileCategory,
  getFileCategoryFromMime,
  httpRequest,
  downloadFileStream,
  setLogLevel,
  logger,
  formatBytes,
  formatTimestamp
}