/**
 * 格式化文件大小
 * @param {number} bytes - 字节数
 * @returns {string}
 */
export function formatFileSize(bytes) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

/**
 * 获取文件扩展名（显示用）
 * @param {string} fileName
 * @returns {string}
 */
export function getFileExtension(fileName) {
  const idx = fileName.lastIndexOf('.')
  return idx === -1 ? '文件' : fileName.slice(idx + 1).toUpperCase() + ' 文件'
}

/**
 * 防抖函数
 * @param {Function} fn
 * @param {number} delay
 * @returns {Function}
 */
export function debounce(fn, delay = 300) {
  let timer = null
  return function (...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}

/**
 * 延迟函数
 * @param {number} ms
 * @returns {Promise<void>}
 */
export const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * 计算 SHA-256 哈希值（使用 Web Worker）
 * @param {File} file
 * @returns {Promise<string>}
 */
export function calculateSHA256(file) {
  return new Promise((resolve, reject) => {
    const workerCode = `
      self.onmessage = function(e) {
        const file = e.data;
        const reader = new FileReader();
        reader.onload = function(ev) {
          crypto.subtle.digest('SHA-256', ev.target.result)
            .then(buf => {
              const arr = Array.from(new Uint8Array(buf));
              const hex = arr.map(b => b.toString(16).padStart(2,'0')).join('');
              self.postMessage({ hash: hex });
            })
            .catch(err => reject(err));
        };
        reader.onerror = (e) => reject(e);
        reader.readAsArrayBuffer(file);
      };
    `
    const blob = new Blob([workerCode], { type: 'application/javascript' })
    const worker = new Worker(URL.createObjectURL(blob))
    worker.onmessage = (e) => {
      resolve(e.data.hash)
      worker.terminate()
    }
    worker.onerror = (e) => {
      reject(e)
      worker.terminate()
    }
    worker.postMessage(file)
  })
}

// src/utils/helpers.js 新增

/**
 * 格式化时间（完整日期时间）
 * @param {string|number|Date} date - 日期对象、时间戳或ISO字符串
 * @returns {string} 格式化后的日期时间，如 "2025-06-01 14:30:25"
 */
export function formatDateTime(date) {
  if (!date) return '--'
  const d = new Date(date)
  if (isNaN(d.getTime())) return '--'
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

/**
 * 格式化日期（仅年月日）
 * @param {string|number|Date} date - 日期对象、时间戳或ISO字符串
 * @returns {string} 格式化后的日期，如 "2025-06-01"
 */
export function formatDate(date) {
  if (!date) return '--'
  const d = new Date(date)
  if (isNaN(d.getTime())) return '--'
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * 格式化时间（仅时分秒）
 * @param {string|number|Date} date - 日期对象、时间戳或ISO字符串
 * @returns {string} 格式化后的时间，如 "14:30:25"
 */
export function formatTime(date) {
  if (!date) return '--'
  const d = new Date(date)
  if (isNaN(d.getTime())) return '--'
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  return `${hours}:${minutes}:${seconds}`
}

/**
 * 相对时间（如“刚刚”、“5分钟前”）
 * @param {string|number|Date} date - 日期对象、时间戳或ISO字符串
 * @returns {string} 相对时间描述
 */
export function timeAgo(date) {
  const timestamp = new Date(date).getTime()
  const now = Date.now()
  const diff = (now - timestamp) / 1000 // 秒
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 2592000) return `${Math.floor(diff / 86400)}天前`
  return formatDate(date)
}

/**
 * 节流函数
 * @param {Function} fn - 需要节流的函数
 * @param {number} interval - 间隔时间（毫秒）
 * @returns {Function} 节流后的函数
 */
export function throttle(fn, interval = 300) {
  let lastTime = 0
  return function (...args) {
    const now = Date.now()
    if (now - lastTime >= interval) {
      lastTime = now
      fn.apply(this, args)
    }
  }
}

/**
 * 复制文本到剪贴板
 * @param {string} text - 要复制的文本
 * @returns {Promise<boolean>} 是否成功
 */
export async function copyToClipboard(text) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch (err) {
    console.error('复制失败', err)
    return false
  }
}

/**
 * 下载 Blob 为文件
 * @param {Blob} blob - Blob 对象
 * @param {string} fileName - 保存的文件名
 */
export function downloadBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/**
 * 获取文件 MIME 类型（根据扩展名）
 * @param {string} fileName - 文件名
 * @returns {string} MIME 类型
 */
export function getMimeType(fileName) {
  const ext = fileName.split('.').pop()?.toLowerCase() || ''
  const map = {
    jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', gif: 'image/gif', webp: 'image/webp',
    mp4: 'video/mp4', webm: 'video/webm', ogg: 'video/ogg',
    mp3: 'audio/mpeg', wav: 'audio/wav', flac: 'audio/flac',
    pdf: 'application/pdf',
    txt: 'text/plain', md: 'text/markdown',
    json: 'application/json', xml: 'application/xml',
    zip: 'application/zip', rar: 'application/x-rar-compressed',
  }
  return map[ext] || 'application/octet-stream'
}

/**
 * 判断是否为图片文件
 * @param {string} fileName
 * @returns {boolean}
 */
export function isImageFile(fileName) {
  const ext = fileName.split('.').pop()?.toLowerCase()
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg'].includes(ext)
}

/**
 * 判断是否为视频文件
 * @param {string} fileName
 * @returns {boolean}
 */
export function isVideoFile(fileName) {
  const ext = fileName.split('.').pop()?.toLowerCase()
  return ['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv'].includes(ext)
}

/**
 * 判断是否为音频文件
 * @param {string} fileName
 * @returns {boolean}
 */
export function isAudioFile(fileName) {
  const ext = fileName.split('.').pop()?.toLowerCase()
  return ['mp3', 'wav', 'ogg', 'flac', 'm4a'].includes(ext)
}

/**
 * 判断是否为文档文件（PDF、Word、Excel、PPT）
 * @param {string} fileName
 * @returns {boolean}
 */
export function isDocumentFile(fileName) {
  const ext = fileName.split('.').pop()?.toLowerCase()
  return ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(ext)
}

/**
 * 判断是否为文本文件
 * @param {string} fileName
 * @returns {boolean}
 */
export function isTextFile(fileName) {
  const ext = fileName.split('.').pop()?.toLowerCase()
  return ['txt', 'md', 'js', 'css', 'html', 'json', 'xml', 'log'].includes(ext)
}