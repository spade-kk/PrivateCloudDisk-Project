/**
 * utils/helper.js - 通用工具函数
 */
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import { FILE_CATEGORIES } from './const'

dayjs.extend(relativeTime)

// ==================== 文件大小格式化 ====================

/** 格式化字节数为可读字符串 */
export function formatFileSize(bytes, decimals = 2) {
  if (bytes === 0 || bytes === undefined || bytes === null) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(decimals)) + ' ' + sizes[i]
}

// ==================== 时间格式化 ====================

/** 格式化为完整时间 */
export function formatTime(date) {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD HH:mm:ss')
}

/** 格式化为相对时间 */
export function formatRelativeTime(date) {
  if (!date) return '-'
  return dayjs(date).fromNow()
}

// ==================== 文件图标映射 ====================

/** 获取文件分类 */
export function getFileCategory(fileName) {
  if (!fileName) return null
  const ext = '.' + (fileName.split('.').pop() || '').toLowerCase()
  for (const [key, cat] of Object.entries(FILE_CATEGORIES)) {
    if (cat.extensions.includes(ext)) return { key, ...cat }
  }
  return { key: 'OTHER', label: '其他', extensions: [] }
}

// ==================== 文件名称操作 ====================

/** 获取文件扩展名 */
export function getFileExtension(fileName) {
  if (!fileName || !fileName.includes('.')) return ''
  return fileName.split('.').pop().toLowerCase()
}

/** 获取无扩展名的文件名 */
export function getBaseName(fileName) {
  if (!fileName) return ''
  const lastDot = fileName.lastIndexOf('.')
  return lastDot === -1 ? fileName : fileName.substring(0, lastDot)
}

// ==================== 防抖 & 节流 ====================

/** 防抖 */
export function debounce(fn, delay = 300) {
  let timer = null
  return function (...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}

/** 节流 */
export function throttle(fn, delay = 300) {
  let last = 0
  return function (...args) {
    const now = Date.now()
    if (now - last >= delay) {
      last = now
      fn.apply(this, args)
    }
  }
}

// ==================== 剪贴板 ====================

/** 复制到剪贴板 */
export async function copyToClipboard(text) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    // fallback
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    return true
  }
}

// ==================== 生成唯一 ID ====================

export function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).substr(2)
}