// ============================================================
// fileIcon.ts — 文件图标映射工具
// ============================================================
// 根据文件扩展名返回对应的 Font Awesome 图标类名和样式色，
// 用于文件列表、网格视图等 UI 场景。
// 映射表覆盖常见文档、图片、音视频、压缩包、代码等文件类型。
// ============================================================

/** 扩展名 → [图标类名, 颜色类名] 映射表 */
const FILE_ICON_MAP: Record<string, [string, string]> = {
  // PDF 文档
  pdf: ['fa-file-pdf-o', 'text-danger'],
  // Word 文档
  doc: ['fa-file-word-o', 'text-blue-600'],
  docx: ['fa-file-word-o', 'text-blue-600'],
  // Excel 表格
  xls: ['fa-file-excel-o', 'text-green-600'],
  xlsx: ['fa-file-excel-o', 'text-green-600'],
  csv: ['fa-file-excel-o', 'text-green-600'],
  // PowerPoint 演示文稿
  ppt: ['fa-file-powerpoint-o', 'text-orange-500'],
  pptx: ['fa-file-powerpoint-o', 'text-orange-500'],
  // 图片
  jpg: ['fa-file-image-o', 'text-purple-500'],
  jpeg: ['fa-file-image-o', 'text-purple-500'],
  png: ['fa-file-image-o', 'text-purple-500'],
  gif: ['fa-file-image-o', 'text-purple-500'],
  svg: ['fa-file-image-o', 'text-purple-500'],
  webp: ['fa-file-image-o', 'text-purple-500'],
  bmp: ['fa-file-image-o', 'text-purple-500'],
  // 视频
  mp4: ['fa-file-video-o', 'text-red-500'],
  avi: ['fa-file-video-o', 'text-red-500'],
  mov: ['fa-file-video-o', 'text-red-500'],
  mkv: ['fa-file-video-o', 'text-red-500'],
  webm: ['fa-file-video-o', 'text-red-500'],
  // 音频
  mp3: ['fa-file-audio-o', 'text-pink-500'],
  wav: ['fa-file-audio-o', 'text-pink-500'],
  flac: ['fa-file-audio-o', 'text-pink-500'],
  aac: ['fa-file-audio-o', 'text-pink-500'],
  // 压缩包
  zip: ['fa-file-archive-o', 'text-yellow-600'],
  rar: ['fa-file-archive-o', 'text-yellow-600'],
  '7z': ['fa-file-archive-o', 'text-yellow-600'],
  tar: ['fa-file-archive-o', 'text-yellow-600'],
  gz: ['fa-file-archive-o', 'text-yellow-600'],
  // 代码
  js: ['fa-file-code-o', 'text-neutral-700'],
  ts: ['fa-file-code-o', 'text-neutral-700'],
  html: ['fa-file-code-o', 'text-neutral-700'],
  css: ['fa-file-code-o', 'text-neutral-700'],
  json: ['fa-file-code-o', 'text-neutral-700'],
  xml: ['fa-file-code-o', 'text-neutral-700'],
  py: ['fa-file-code-o', 'text-neutral-700'],
  java: ['fa-file-code-o', 'text-neutral-700'],
  // 文本
  txt: ['fa-file-text-o', 'text-neutral-500'],
  md: ['fa-file-text-o', 'text-neutral-500'],
  log: ['fa-file-text-o', 'text-neutral-500'],
}

/** 默认图标（未知文件类型） */
const DEFAULT_ICON: [string, string] = ['fa-file-o', 'text-neutral-400']

/**
 * 根据文件名获取 Font Awesome 图标类名
 *
 * 从文件名中提取扩展名，查表返回对应的图标类。
 * 未匹配的扩展名返回默认文件图标。
 *
 * @param fileName - 完整文件名，如 "report.pdf"
 * @returns Font Awesome 图标类名，如 "fa-file-pdf-o text-danger"
 *
 * @example
 * getFileIconClass("report.pdf")    // => "fa-file-pdf-o text-danger"
 * getFileIconClass("photo.jpg")     // => "fa-file-image-o text-purple-500"
 * getFileIconClass("unknown.xyz")   // => "fa-file-o text-neutral-400"
 */
export function getFileIconClass(fileName: string): string {
  const ext = fileName.split('.').pop()?.toLowerCase() || ''
  const [icon, color] = FILE_ICON_MAP[ext] || DEFAULT_ICON
  return `${icon} ${color}`
}