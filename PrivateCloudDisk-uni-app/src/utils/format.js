/**
 * utils/format.js - 企业级格式化工具集
 *
 * 对标 Vue3 Web 应用的数据格式化模式
 */

// ==================== 文件大小格式化 ====================

const SIZE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']

/**
 * 格式化文件大小
 * @param {number} bytes  字节数
 * @param {number} [decimals=2] 小数位数
 * @returns {string} 如 "1.5 MB"
 */
export function formatFileSize(bytes, decimals = 2) {
  if (bytes === 0 || bytes === undefined || bytes === null) return '0 B'
  if (bytes < 0) return '-' + formatFileSize(-bytes, decimals)

  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  const size = parseFloat((bytes / Math.pow(k, i)).toFixed(decimals))

  return `${size} ${SIZE_UNITS[i]}`
}

/**
 * 格式化文件大小 (简洁版，无小数)
 * @param {number} bytes
 * @returns {string} 如 "1.5MB"
 */
export function formatFileSizeCompact(bytes) {
  if (!bytes) return '0B'
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  const size = (bytes / Math.pow(k, i)).toFixed(i === 0 ? 0 : 1)
  return `${size}${SIZE_UNITS[i]}`
}

// ==================== 日期时间格式化 ====================

/**
 * 格式化日期时间
 * @param {string|number|Date} date 日期
 * @param {string} [format='YYYY-MM-DD HH:mm:ss'] 格式
 * @returns {string}
 */
export function formatDate(date, format = 'YYYY-MM-DD HH:mm:ss') {
  if (!date) return ''

  const d = new Date(date)
  if (isNaN(d.getTime())) return ''

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * 相对时间 (如 "3分钟前", "昨天 14:30")
 * @param {string|number|Date} date
 * @returns {string}
 */
export function formatRelativeTime(date) {
  if (!date) return ''

  const now = Date.now()
  const target = new Date(date).getTime()
  const diff = now - target

  if (diff < 0) return '刚刚'

  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)}分钟前`
  if (diff < day) return `${Math.floor(diff / hour)}小时前`

  const yesterday = new Date(now - day)
  const targetDate = new Date(target)

  if (
    targetDate.getFullYear() === yesterday.getFullYear() &&
    targetDate.getMonth() === yesterday.getMonth() &&
    targetDate.getDate() === yesterday.getDate()
  ) {
    return `昨天 ${formatDate(target, 'HH:mm')}`
  }

  const thisYear = new Date().getFullYear()
  if (targetDate.getFullYear() === thisYear) {
    return formatDate(target, 'MM-DD HH:mm')
  }

  return formatDate(target, 'YYYY-MM-DD')
}

/**
 * 格式化时长 (秒 -> mm:ss 或 hh:mm:ss)
 * @param {number} seconds
 * @returns {string}
 */
export function formatDuration(seconds) {
  if (!seconds || isNaN(seconds)) return '00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  const pad = (n) => String(n).padStart(2, '0')
  if (h > 0) return `${pad(h)}:${pad(m)}:${pad(s)}`
  return `${pad(m)}:${pad(s)}`
}

// ==================== 数字格式化 ====================

/**
 * 格式化数字 (千分位)
 * @param {number} num
 * @returns {string} 如 "1,234,567"
 */
export function formatNumber(num) {
  if (num === undefined || num === null) return '0'
  return Number(num).toLocaleString('zh-CN')
}

/**
 * 格式化百分比
 * @param {number} value 0-1 或 0-100
 * @param {number} [decimals=1]
 * @returns {string} 如 "85.5%"
 */
export function formatPercent(value, decimals = 1) {
  if (value === undefined || value === null) return '0%'
  const pct = value > 1 ? value : value * 100
  return `${pct.toFixed(decimals)}%`
}

// ==================== 文件类型格式化 ====================

/**
 * 获取文件扩展名
 * @param {string} filename
 * @returns {string} 小写扩展名，不含点
 */
export function getFileExtension(filename) {
  if (!filename) return ''
  const lastDot = filename.lastIndexOf('.')
  if (lastDot === -1) return ''
  return filename.slice(lastDot + 1).toLowerCase()
}

/**
 * 获取文件类型图标（uView Plus 内置图标名）
 *
 * uView Plus 图标集以 uicon- 为前缀，
 * 可用图标见: node_modules/uview-plus/components/u-icon/icons.js
 *
 * @param {string} filename
 * @returns {string} uView 图标名
 */
export function getFileTypeIcon(filename) {
  const ext = getFileExtension(filename)
  const iconMap = {
    // 图片
    jpg: 'photo', jpeg: 'photo', png: 'photo', gif: 'photo',
    webp: 'photo', bmp: 'photo', svg: 'photo',
    // 视频
    mp4: 'play-circle', avi: 'play-circle', mov: 'play-circle',
    mkv: 'play-circle', flv: 'play-circle', webm: 'play-circle',
    // 音频 — uView 无 music 图标，用 volume 替代
    mp3: 'volume', wav: 'volume', flac: 'volume', aac: 'volume', ogg: 'volume',
    // 文档 — uView 无 file-pdf/file-code 图标，统一用 file-text
    pdf: 'file-text',
    doc: 'file-text', docx: 'file-text',
    xls: 'file-text', xlsx: 'file-text',
    ppt: 'file-text', pptx: 'file-text',
    txt: 'file-text', md: 'file-text',
    // 压缩包 — uView 无 file-zip 图标，用 file-text 替代
    zip: 'file-text', rar: 'file-text', '7z': 'file-text',
    tar: 'file-text', gz: 'file-text',
    // 代码 — uView 无 file-code 图标，用 file-text 替代
    js: 'file-text', ts: 'file-text', jsx: 'file-text',
    vue: 'file-text', html: 'file-text', css: 'file-text',
    py: 'file-text', java: 'file-text', go: 'file-text'
  }
  return iconMap[ext] || 'file-text'
}

/**
 * 获取文件类型分类
 * @param {string} filename
 * @returns {string} image | video | audio | document | archive | other
 */
export function getFileCategory(filename) {
  const ext = getFileExtension(filename)
  const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'ico', 'heic', 'raw']
  const videoExts = ['mp4', 'avi', 'mov', 'wmv', 'flv', 'mkv', 'webm', 'm4v']
  const audioExts = ['mp3', 'wav', 'flac', 'aac', 'ogg', 'wma', 'm4a']
  const archiveExts = ['zip', 'rar', '7z', 'tar', 'gz', 'bz2']

  if (imageExts.includes(ext)) return 'image'
  if (videoExts.includes(ext)) return 'video'
  if (audioExts.includes(ext)) return 'audio'
  if (archiveExts.includes(ext)) return 'archive'
  if (ext) return 'document'
  return 'other'
}

/**
 * 判断文件是否可预览
 * @param {string} filename
 * @returns {boolean}
 */
export function isPreviewable(filename) {
  const category = getFileCategory(filename)
  return ['image', 'video', 'audio'].includes(category)
}

/**
 * 判断是否为视频文件
 * @param {string} filename
 * @returns {boolean}
 */
export function isVideoFile(filename) {
  return getFileCategory(filename) === 'video'
}

/**
 * 判断是否为图片文件
 * @param {string} filename
 * @returns {boolean}
 */
export function isImageFile(filename) {
  return getFileCategory(filename) === 'image'
}