// ============================================================
// previewHelper.ts — 文件预览工具类
// ============================================================
// 提供文件类型检测、预览配置、文件图标、类型名称等工具函数。
// 用于文件列表、预览弹窗、右键菜单等场景的文件类型判断和 UI 展示。
//
// 支持的文件类型：
//   图片 (images)     — jpg, png, gif, webp, svg, bmp, ico, tiff, tif
//   视频 (videos)     — mp4, webm, ogg, mov, avi, mkv, wmv, flv, m4v
//   音频 (audios)     — mp3, wav, ogg, flac, aac, m4a, wma, opus
//   文档 (documents)  — pdf
//   Office (office)   — doc, docx, xls, xlsx, ppt, pptx, pptm, csv, rtf
//   代码 (code)       — js, ts, html, css, json, py, java, cpp, go, rs, ...
//   压缩包 (archives) — zip, rar, 7z, tar, gz, bz2, xz, tgz
// ============================================================

// ============================================================
// 类型定义
// ============================================================

import { getFileIconDefinition } from './fileTypeIcons'

/** 预览配置接口 */
export interface PreviewConfig {
  /** 预览类型：image, video, audio, pdf, office, text */
  type: string
  /** 支持的操作：zoom, rotate, fullscreen, download, quality, page, visualization */
  supports: string[]
  /** 最大预览文件大小（字节） */
  maxSize: number
  /** 是否生成缩略图 */
  thumbnail: boolean
}

/** 支持的文件格式映射 */
export interface SupportedFormatsMap {
  images: string[]
  videos: string[]
  audios: string[]
  documents: string[]
  office: string[]
  code: string[]
  archives: string[]
}

// ============================================================
// 支持的文件格式常量
// ============================================================

/**
 * 系统支持的文件格式分类
 *
 * 按类型分组管理所有支持的文件扩展名。
 * 新增文件类型支持时只需在此处添加扩展名即可。
 */
export const SUPPORTED_FORMATS: SupportedFormatsMap = {
  // 图片 — 支持缩放、旋转、全屏预览
  images: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'ico', 'tiff', 'tif'],

  // 视频 — 支持在线播放、全屏、清晰度切换
  videos: ['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv', 'wmv', 'flv', 'm4v'],

  // 音频 — 支持在线播放、波形可视化
  audios: ['mp3', 'wav', 'ogg', 'flac', 'aac', 'm4a', 'wma', 'opus'],

  // 文档 — PDF 支持在线预览（pdf.js）
  documents: ['pdf'],

  // Office 文档 — 仅支持下载，预览需后端转换为 PDF/图片
  office: ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pptm', 'csv', 'rtf'],

  // 代码文件 — 支持语法高亮预览
  code: [
    'js', 'ts', 'jsx', 'tsx',
    'html', 'htm', 'css', 'scss', 'sass', 'less',
    'json', 'xml', 'yaml', 'yml', 'toml',
    'py', 'pyw', 'java', 'class',
    'cpp', 'c', 'h', 'hpp',
    'cs', 'go', 'rs', 'rb', 'php',
    'swift', 'kt', 'scala',
    'sql', 'sh', 'bash', 'zsh',
    'md', 'txt', 'log', 'ini', 'conf', 'cfg',
  ],

  // 压缩包 — 仅支持下载，不支持在线预览
  archives: ['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'tgz'],
}

// ============================================================
// 文件扩展名
// ============================================================

/**
 * 获取文件扩展名（小写）
 *
 * 从文件名中提取最后一个 . 之后的扩展名并转为小写。
 * 无扩展名时返回空字符串。
 *
 * @param fileName - 文件名
 * @returns 小写扩展名，如 "pdf"、"jpg"，无扩展名返回 ""
 */
export function getFileExtension(fileName: string): string {
  if (!fileName) return ''
  const parts = fileName.split('.')
  return parts.length > 1 ? parts.pop()!.toLowerCase() : ''
}

// ============================================================
// 文件类型检测
// ============================================================

/** 检测是否为图片文件 */
export function isImage(fileName: string): boolean {
  return SUPPORTED_FORMATS.images.includes(getFileExtension(fileName))
}

/** 检测是否为视频文件 */
export function isVideo(fileName: string): boolean {
  return SUPPORTED_FORMATS.videos.includes(getFileExtension(fileName))
}

/** 检测是否为音频文件 */
export function isAudio(fileName: string): boolean {
  return SUPPORTED_FORMATS.audios.includes(getFileExtension(fileName))
}

/** 检测是否为 PDF 文档 */
export function isPdf(fileName: string): boolean {
  return SUPPORTED_FORMATS.documents.includes(getFileExtension(fileName))
}

/** 检测是否为 Office 文档（Word/Excel/PPT/CSV） */
export function isOffice(fileName: string): boolean {
  return SUPPORTED_FORMATS.office.includes(getFileExtension(fileName))
}

/** 检测是否为 Word 文档（.doc / .docx） */
export function isWord(fileName: string): boolean {
  return ['doc', 'docx'].includes(getFileExtension(fileName))
}

/** 检测是否为 Excel 文件（.xls / .xlsx / .csv） */
export function isExcel(fileName: string): boolean {
  return ['xls', 'xlsx', 'csv'].includes(getFileExtension(fileName))
}

/** 检测是否为 PowerPoint 文件（.ppt / .pptx / .pptm） */
export function isPowerPoint(fileName: string): boolean {
  return ['ppt', 'pptx', 'pptm'].includes(getFileExtension(fileName))
}

/** 检测是否为代码/标记语言文件 */
export function isCode(fileName: string): boolean {
  return SUPPORTED_FORMATS.code.includes(getFileExtension(fileName))
}

/** 检测是否为压缩包文件 */
export function isArchive(fileName: string): boolean {
  return SUPPORTED_FORMATS.archives.includes(getFileExtension(fileName))
}

/** 检测是否为纯文本文件（.txt / .md / .log） */
export function isText(fileName: string): boolean {
  return ['txt', 'md', 'log'].includes(getFileExtension(fileName))
}

// ============================================================
// 预览能力
// ============================================================

/**
 * 检测文件是否可预览
 *
 * 综合判断文件是否属于任何支持预览的类型。
 * 用于控制文件列表中"预览"按钮的显示/禁用状态。
 *
 * @param fileName - 文件名
 * @returns 是否可预览
 */
export function isPreviewable(fileName: string): boolean {
  return (
    isImage(fileName) ||
    isVideo(fileName) ||
    isAudio(fileName) ||
    isPdf(fileName) ||
    isOffice(fileName) ||
    isCode(fileName) ||
    isArchive(fileName) ||
    isText(fileName)
  )
}

// ============================================================
// UI 辅助
// ============================================================

/**
 * 获取文件类型对应的 Font Awesome 图标类名
 *
 * 用于文件列表、网格视图等场景的文件类型图标展示。
 *
 * @param fileName - 文件名
 * @returns Font Awesome 图标类名，如 "fa fa-image"
 */
export function getFileTypeIcon(fileName: string): string {
  // AUDIT FIX [2.1/8.3]：预览入口也复用文件浏览器的单一图标目录。
  const descriptor = getFileIconDefinition(fileName)
  return `fa ${descriptor.faClass}`
}

/**
 * 获取文件类型的中文名称
 *
 * 用于文件详情面板、提示文本等场景的类型展示。
 *
 * @param fileName - 文件名
 * @returns 文件类型中文名，如 "PDF文档"、"视频"
 */
export function getFileTypeName(fileName: string): string {
  const ext = getFileExtension(fileName)
  if (isImage(fileName)) return '图片'
  if (isVideo(fileName)) return '视频'
  if (isAudio(fileName)) return '音频'
  if (isPdf(fileName)) return 'PDF文档'
  if (isWord(fileName)) return 'Word文档'
  if (isExcel(fileName)) return 'Excel表格'
  if (isPowerPoint(fileName)) return 'PowerPoint演示文稿'
  if (isCode(fileName)) return '代码文件'
  if (isArchive(fileName)) return '压缩文件'
  if (isText(fileName)) return '文本文件'
  return `${ext.toUpperCase()} 文件`
}

// ============================================================
// 预览配置
// ============================================================

/**
 * 获取文件的预览能力配置
 *
 * 返回前端预览组件所需的配置参数：
 * - type: 预览组件类型选择
 * - supports: 支持的操作列表（用于控制工具栏按钮）
 * - maxSize: 最大预览文件大小限制（超出则提示下载）
 * - thumbnail: 是否需要生成缩略图
 *
 * @param fileName - 文件名
 * @returns 预览配置对象，不支持预览的文件返回 null
 */
export function getPreviewConfig(fileName: string): PreviewConfig | null {
  // 图片预览：支持缩放、旋转、全屏
  if (isImage(fileName)) {
    return {
      type: 'image',
      supports: ['zoom', 'rotate', 'fullscreen'],
      maxSize: 10 * 1024 * 1024, // 10MB
      thumbnail: true,
    }
  }

  // 视频预览：支持全屏、下载、清晰度切换
  if (isVideo(fileName)) {
    return {
      type: 'video',
      supports: ['fullscreen', 'download', 'quality'],
      maxSize: 500 * 1024 * 1024, // 500MB
      thumbnail: true,
    }
  }

  // 音频预览：支持下载、波形可视化
  if (isAudio(fileName)) {
    return {
      type: 'audio',
      supports: ['download', 'visualization'],
      maxSize: 50 * 1024 * 1024, // 50MB
      thumbnail: false,
    }
  }

  // PDF 预览：支持全屏、下载、缩放、翻页
  if (isPdf(fileName)) {
    return {
      type: 'pdf',
      supports: ['fullscreen', 'download', 'zoom', 'page'],
      maxSize: 100 * 1024 * 1024, // 100MB
      thumbnail: false,
    }
  }

  // Office 文档：仅支持下载（预览需后端转换）
  if (isOffice(fileName)) {
    return {
      type: 'office',
      supports: ['download'],
      maxSize: 50 * 1024 * 1024, // 50MB
      thumbnail: false,
    }
  }

  // 代码/文本文件：支持下载、语法高亮
  if (isCode(fileName) || isText(fileName)) {
    return {
      type: 'text',
      supports: ['download'],
      maxSize: 5 * 1024 * 1024, // 5MB
      thumbnail: false,
    }
  }

  return null
}
