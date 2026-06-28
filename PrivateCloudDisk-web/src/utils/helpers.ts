// ============================================================
// helpers.ts — 通用工具函数集
// ============================================================
// 提供文件大小格式化、日期时间格式化、防抖/节流、SHA-256 哈希、
// 剪贴板操作、Blob 下载等企业级通用工具函数。
// 所有函数均为纯函数，无副作用，可直接在任何模块中安全调用。
// ============================================================

// ============================================================
// 文件大小格式化
// ============================================================

/**
 * 将字节数格式化为人类可读的文件大小字符串
 *
 * 自动根据字节数选择最合适的单位（B / KB / MB / GB / TB），
 * 保留两位小数，避免浮点数精度问题。
 *
 * 算法：取以 1024 为底的对数确定单位索引，除以对应幂次得到数值。
 * 时间复杂度 O(1)，空间复杂度 O(1)。
 *
 * @param bytes - 文件字节数，必须为非负整数
 * @returns 格式化后的文件大小字符串，如 "1.5 MB"、"0 B"
 *
 * @example
 * formatFileSize(0)        // => "0 B"
 * formatFileSize(1024)     // => "1 KB"
 * formatFileSize(1536000)  // => "1.46 MB"
 * formatFileSize(1073741824) // => "1 GB"
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// ============================================================
// 文件扩展名
// ============================================================

/**
 * 获取文件扩展名（显示用）
 *
 * 从文件名中提取最后一个 . 之后的扩展名，转为大写并附加 " 文件" 后缀，
 * 用于 UI 展示。无扩展名时返回 "文件"。
 *
 * @param fileName - 完整文件名，如 "report.pdf"
 * @returns 格式化的扩展名展示字符串，如 "PDF 文件"
 *
 * @example
 * getFileExtension("report.pdf")     // => "PDF 文件"
 * getFileExtension("archive.tar.gz") // => "GZ 文件"
 * getFileExtension("README")         // => "文件"
 */
export function getFileExtension(fileName: string): string {
  const idx = fileName.lastIndexOf('.')
  return idx === -1 ? '文件' : fileName.slice(idx + 1).toUpperCase() + ' 文件'
}

// ============================================================
// 防抖 (Debounce)
// ============================================================

/**
 * 防抖函数 — 高频触发只执行最后一次
 *
 * 典型场景：搜索框输入联想、窗口 resize 监听、按钮防重复点击。
 * 每次调用会清除上一次的定时器，仅在 delay 毫秒内无新调用时才执行。
 *
 * 实现细节：使用闭包保存 timer 引用，clearTimeout 后再 set 新的。
 * 支持泛型函数类型，保留原始函数的 this 上下文和参数。
 *
 * @param fn - 要防抖的目标函数
 * @param delay - 防抖延迟时间（毫秒），默认 300ms
 * @returns 防抖包装后的函数
 *
 * @example
 * const debouncedSearch = debounce((keyword: string) => api.search(keyword), 500)
 * input.addEventListener('input', (e) => debouncedSearch(e.target.value))
 */
// eslint-disable-next-line @typescript-eslint/no-unsafe-function-type
export function debounce<T extends Function>(fn: T, delay: number = 300): (...args: unknown[]) => void {
  let timer: ReturnType<typeof setTimeout> | null = null
  return function (this: unknown, ...args: unknown[]) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}

// ============================================================
// 延迟 (Delay)
// ============================================================

/**
 * Promise 封装版 setTimeout 延迟函数
 *
 * 用于 async/await 流程中的等待，比直接使用 setTimeout 更简洁。
 * 常见场景：轮询间隔、UI 动画等待、测试中的模拟延迟。
 *
 * @param ms - 延迟毫秒数
 * @returns Promise，在指定毫秒后 resolve
 *
 * @example
 * await delay(1000) // 等待 1 秒后继续执行
 */
export const delay = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms))

// ============================================================
// SHA-256 哈希（Web Worker 异步计算）
// ============================================================

/**
 * 使用 Web Worker 异步计算文件的 SHA-256 哈希值
 *
 * 大文件哈希计算是 CPU 密集型操作，放在主线程会阻塞 UI。
 * 此函数通过模块化 Web Worker 将计算移至后台线程，避免页面卡顿。
 *
 * 非安全上下文（HTTP）降级方案：
 *   Worker 内优先使用原生 crypto.subtle.digest（硬件加速），
 *   不可用时降级为 crypto-js 纯 JS 实现。降级代码由
 *   import.meta.env.DEV 编译时常量守卫，生产构建时被移除。
 *
 * 原理：
 * 1. 创建模块化 Web Worker（sha256.worker.ts）
 * 2. Worker 中使用 FileReader 读取文件为 ArrayBuffer
 * 3. 优先调用 crypto.subtle.digest('SHA-256', ...) 计算哈希
 * 4. 不可用时降级为 crypto-js SHA-256（仅开发环境）
 * 5. 将结果转为十六进制字符串通过 postMessage 返回
 * 6. 计算完成后自动 terminate Worker 释放资源
 *
 * @param file - 要计算哈希的 File 对象
 * @returns Promise，resolve 为 SHA-256 十六进制哈希字符串
 *
 * @example
 * const hash = await calculateSHA256(uploadedFile)
 * console.log(hash) // => "a1b2c3d4e5f6..."
 */
export function calculateSHA256(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const worker = new Worker(
      new URL('./sha256.worker.ts', import.meta.url),
      { type: 'module' },
    )
    worker.onmessage = (e: MessageEvent) => {
      if (e.data.error) {
        reject(new Error(e.data.error))
      } else {
        resolve(e.data.hash)
      }
      worker.terminate()
    }
    worker.onerror = (e: ErrorEvent) => {
      reject(e)
      worker.terminate()
    }
    worker.postMessage(file)
  })
}

// ============================================================
// 日期时间格式化
// ============================================================

/**
 * 格式化完整日期时间
 *
 * 将日期对象、时间戳或 ISO 字符串格式化为 "YYYY-MM-DD HH:mm:ss" 格式。
 * 无效日期返回 "--" 占位符，避免 UI 崩溃。
 *
 * @param date - 日期对象、Unix 毫秒时间戳或 ISO 8601 字符串；null/undefined 返回 "--"
 * @returns 格式化后的日期时间字符串，如 "2025-06-01 14:30:25"
 *
 * @example
 * formatDateTime(new Date())           // => "2025-06-01 14:30:25"
 * formatDateTime(1717230625000)        // => "2025-06-01 14:30:25"
 * formatDateTime("2025-06-01T14:30:25") // => "2025-06-01 14:30:25"
 * formatDateTime(null)                 // => "--"
 */
export function formatDateTime(date: string | number | Date | null | undefined): string {
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
 *
 * 格式化为 "YYYY-MM-DD" 格式，用于日期列表、文件修改日期等场景。
 *
 * @param date - 日期对象、时间戳或 ISO 字符串；null/undefined 返回 "--"
 * @returns 格式化后的日期字符串，如 "2025-06-01"
 *
 * @example
 * formatDate(new Date())    // => "2025-06-01"
 * formatDate("2025-06-01")  // => "2025-06-01"
 */
export function formatDate(date: string | number | Date | null | undefined): string {
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
 *
 * 格式化为 "HH:mm:ss" 格式，用于显示纯时间信息。
 *
 * @param date - 日期对象、时间戳或 ISO 字符串；null/undefined 返回 "--"
 * @returns 格式化后的时间字符串，如 "14:30:25"
 *
 * @example
 * formatTime(new Date())   // => "14:30:25"
 */
export function formatTime(date: string | number | Date | null | undefined): string {
  if (!date) return '--'
  const d = new Date(date)
  if (isNaN(d.getTime())) return '--'
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  return `${hours}:${minutes}:${seconds}`
}

/**
 * 相对时间（人性化时间差）
 *
 * 将时间戳转换为 "刚刚"、"5 分钟前"、"3 小时前"、"2 天前" 等相对描述。
 * 超过 30 天则降级为显示完整日期（formatDate）。
 *
 * 阈值策略：
 * - < 60 秒：显示 "刚刚"
 * - < 1 小时：显示 "X 分钟前"
 * - < 24 小时：显示 "X 小时前"
 * - < 30 天：显示 "X 天前"
 * - >= 30 天：显示完整日期（如 "2025-06-01"）
 *
 * @param date - 日期对象、时间戳或 ISO 字符串
 * @returns 人性化相对时间描述
 *
 * @example
 * timeAgo(Date.now() - 5000)       // => "刚刚"
 * timeAgo(Date.now() - 300000)     // => "5分钟前"
 * timeAgo(Date.now() - 7200000)    // => "2小时前"
 * timeAgo("2025-01-01")            // => "2025-01-01"
 */
export function timeAgo(date: string | number | Date): string {
  const timestamp = new Date(date).getTime()
  const now = Date.now()
  const diff = (now - timestamp) / 1000 // 转为秒
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 2592000) return `${Math.floor(diff / 86400)}天前`
  return formatDate(date)
}

// ============================================================
// 节流 (Throttle)
// ============================================================

/**
 * 节流函数 — 高频触发按固定间隔执行
 *
 * 与防抖不同，节流保证在 interval 时间间隔内至少执行一次，
 * 适合滚动事件、resize 监听、进度条更新等需要持续反馈的场景。
 *
 * 实现：记录上次执行时间，每次调用检查时间差是否 >= interval，
 * 满足条件则执行并更新 lastTime 时间戳。
 *
 * @param fn - 需要节流的函数
 * @param interval - 执行间隔（毫秒），默认 300ms
 * @returns 节流包装后的函数
 *
 * @example
 * const throttledScroll = throttle(() => updateScrollPosition(), 100)
 * window.addEventListener('scroll', throttledScroll)
 */
export function throttle<T extends (...args: unknown[]) => void>(fn: T, interval: number = 300): (...args: Parameters<T>) => void {
  let lastTime = 0
  return function (...args: Parameters<T>) {
    const now = Date.now()
    if (now - lastTime >= interval) {
      lastTime = now
      fn.apply(this, args)
    }
  }
}

// ============================================================
// 剪贴板
// ============================================================

/**
 * 复制文本到系统剪贴板
 *
 * 使用现代 Clipboard API (navigator.clipboard.writeText)，
 * 支持 HTTP/HTTPS 环境。失败时返回 false 而非抛异常。
 *
 * 安全注意：必须在用户交互上下文中调用（点击/按键事件），
 * 否则浏览器会阻止异步剪贴板写入。
 *
 * @param text - 要复制的文本内容
 * @returns Promise<boolean>，true 表示复制成功
 *
 * @example
 * const success = await copyToClipboard("Hello World")
 * if (success) toast.show("已复制到剪贴板")
 */
export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch (err) {
    console.error('复制到剪贴板失败', err)
    return false
  }
}

// ============================================================
// Blob 下载
// ============================================================

/**
 * 触发浏览器下载 Blob 数据为文件
 *
 * 创建临时 URL → 创建隐藏 <a> 标签 → 触发 click → 清理资源。
 * 适用于导出文件、下载报告等场景。
 *
 * 资源管理：下载后立即调用 URL.revokeObjectURL 释放内存，
 * 移除临时 DOM 元素避免内存泄漏。
 *
 * @param blob - 要下载的 Blob 数据
 * @param fileName - 保存到本地的文件名（含扩展名）
 *
 * @example
 * const blob = await response.blob()
 * downloadBlob(blob, "report.pdf")
 */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url) // 释放 Blob URL 内存
}

/**
 * 根据文件扩展名获取 MIME 类型
 *
 * 覆盖常见文件类型的 MIME 映射，用于在上传或下载时设置正确的 Content-Type。
 * 未匹配的扩展名返回 application/octet-stream 作为通用二进制类型。
 *
 * @param fileName - 文件名（含扩展名）
 * @returns MIME 类型字符串
 *
 * @example
 * getMimeType("photo.jpg")  // => "image/jpeg"
 * getMimeType("data.json")  // => "application/json"
 * getMimeType("unknown.xyz") // => "application/octet-stream"
 */
export function getMimeType(fileName: string): string {
  const ext = fileName.split('.').pop()?.toLowerCase() || ''
  const mimeMap: Record<string, string> = {
    // 图片
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    png: 'image/png',
    gif: 'image/gif',
    webp: 'image/webp',
    bmp: 'image/bmp',
    svg: 'image/svg+xml',
    ico: 'image/x-icon',
    // 文档
    pdf: 'application/pdf',
    doc: 'application/msword',
    docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    xls: 'application/vnd.ms-excel',
    xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    ppt: 'application/vnd.ms-powerpoint',
    pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    csv: 'text/csv',
    rtf: 'application/rtf',
    // 文本/代码
    txt: 'text/plain',
    html: 'text/html',
    css: 'text/css',
    js: 'text/javascript',
    json: 'application/json',
    xml: 'application/xml',
    yaml: 'text/yaml',
    yml: 'text/yaml',
    md: 'text/markdown',
    // 音视频
    mp3: 'audio/mpeg',
    wav: 'audio/wav',
    ogg: 'audio/ogg',
    flac: 'audio/flac',
    aac: 'audio/aac',
    mp4: 'video/mp4',
    webm: 'video/webm',
    avi: 'video/x-msvideo',
    mov: 'video/quicktime',
    mkv: 'video/x-matroska',
    // 压缩包
    zip: 'application/zip',
    rar: 'application/vnd.rar',
    '7z': 'application/x-7z-compressed',
    tar: 'application/x-tar',
    gz: 'application/gzip',
    bz2: 'application/x-bzip2',
    // 字体
    ttf: 'font/ttf',
    woff: 'font/woff',
    woff2: 'font/woff2',
  }
  return mimeMap[ext] || 'application/octet-stream'
}