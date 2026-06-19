/**
 * utils/helper.js - 通用工具函数
 */
import { FILE_EXT_CATEGORY, FILE_TYPE_ICONS, FILE_CATEGORY } from './const'

/**
 * 格式化文件大小
 * @param {number} bytes
 * @returns {string} 如 "1.5 MB"
 */
export function formatFileSize(bytes) {
  if (bytes == null || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return `${size.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

/**
 * 根据文件扩展名推断分类
 * @param {string} fileName
 * @returns {string} FILE_CATEGORY 枚举值
 */
export function getFileCategory(fileName) {
  if (!fileName) return FILE_CATEGORY.OTHER
  const ext = fileName.split('.').pop()?.toLowerCase()
  return FILE_EXT_CATEGORY[ext] || FILE_CATEGORY.OTHER
}

/**
 * 根据文件名获取对应图标
 * @param {string} fileName
 * @returns {string} uView 图标名称
 */
export function getFileIcon(fileName) {
  const category = getFileCategory(fileName)
  const map = {
    [FILE_CATEGORY.IMAGE]: 'photo',
    [FILE_CATEGORY.VIDEO]: 'play-circle',
    [FILE_CATEGORY.AUDIO]: 'music',
    [FILE_CATEGORY.DOCUMENT]: 'file-text',
    [FILE_CATEGORY.ARCHIVE]: 'file-zip',
    [FILE_CATEGORY.OTHER]: 'file'
  }
  return map[category] || 'file'
}

/**
 * 根据文件分类返回颜色
 * @param {string} fileName
 * @returns {string} 颜色值
 */
export function getFileIconColor(fileName) {
  const category = getFileCategory(fileName)
  const colorMap = {
    [FILE_CATEGORY.IMAGE]: '#34a853',
    [FILE_CATEGORY.VIDEO]: '#ea4335',
    [FILE_CATEGORY.AUDIO]: '#fbbc04',
    [FILE_CATEGORY.DOCUMENT]: '#1a73e8',
    [FILE_CATEGORY.ARCHIVE]: '#ff9800',
    [FILE_CATEGORY.OTHER]: '#9aa0a6'
  }
  return colorMap[category] || '#9aa0a6'
}

/**
 * 格式化时间
 * @param {string|number} time ISO 时间字符串或时间戳
 * @returns {string}
 */
export function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const diff = now - d

  // 1分钟内
  if (diff < 60 * 1000) return '刚刚'
  // 1小时内
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)}分钟前`
  // 今天内
  if (d.toDateString() === now.toDateString()) {
    return d.toTimeString().slice(0, 5)
  }
  // 昨天
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (d.toDateString() === yesterday.toDateString()) return '昨天'

  // 年内
  if (d.getFullYear() === now.getFullYear()) {
    return `${d.getMonth() + 1}-${d.getDate()}`
  }

  // 跨年
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * 防抖
 * @param {Function} fn
 * @param {number} delay ms
 */
export function debounce(fn, delay = 300) {
  let timer = null
  return function (...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}

/**
 * 节流
 * @param {Function} fn
 * @param {number} interval ms
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
 * @param {string} text
 */
export function copyText(text) {
  uni.setClipboardData({
    data: text,
    success() {
      uni.showToast({ title: '已复制', icon: 'success' })
    }
  })
}