// ============================================================
// fileIcon.ts — 旧图标 API 兼容层
// ============================================================
// AUDIT FIX [8.1-8.3]：文件浏览器的新组件直接使用 fileTypeIcons.ts；
// 这里保留原有 getFileIconClass 字符串 API，避免详情、回收站、上传确认等
// 尚未迁移的页面发生行为回归。
// ============================================================

import { getFileIconDefinition } from './fileTypeIcons'

/**
 * 兼容旧组件的 Font Awesome 类名接口。
 * 未知后缀由 FileTypeIcon 组件显示动态 SVG；旧组件仍回退到通用 FA 文件类。
 */
export function getFileIconClass(fileName: string, isDirectory = false): string {
  const descriptor = getFileIconDefinition(fileName, isDirectory)
  return `${descriptor.faClass} ${descriptor.legacyColorClass}`
}

export { getFileIconDefinition } from './fileTypeIcons'
export {
  createDynamicFileSvg,
  fileExtensionAbbreviation,
  resolveFileTypeIcon,
  FILE_ICON_EXTENSION_COUNT,
  FILE_ICON_SPECIAL_FILE_COUNT,
  FILE_ICON_SPECIAL_DIRECTORY_COUNT,
  FILE_ICON_EXTENSION_MAP,
  FILE_ICON_SPECIAL_FILE_MAP,
  FILE_ICON_SPECIAL_DIRECTORY_MAP,
} from './fileTypeIcons'
export type { FileIconDescriptor, FileIconKind } from './fileTypeIcons'
