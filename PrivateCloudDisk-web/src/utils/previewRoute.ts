// ============================================================
// 文件预览路由统一分发器
// ============================================================
// AUDIT FIX [7.1]: 所有文件入口共用同一份类型识别规则，避免网格、列表、详情抽屉
// 各自维护扩展名白名单造成同一文件在不同入口行为不一致。

import type { Router, RouteLocationRaw } from 'vue-router'

const extensionGroups = {
  image: new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'avif', 'heic']),
  video: new Set(['mp4', 'mkv', 'webm', 'mov', 'avi', 'm4v', 'flv', 'mpeg', 'mpg']),
  pdf: new Set(['pdf']),
  word: new Set(['doc', 'docx']),
  excel: new Set(['xls', 'xlsx', 'xlsm', 'csv']),
  ppt: new Set(['ppt', 'pptx', 'pptm']),
  markdown: new Set(['md', 'markdown', 'mdown']),
  archive: new Set(['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'tgz', 'iso']),
  code: new Set([
    'txt', 'log', 'html', 'css', 'scss', 'less', 'js', 'jsx', 'ts', 'tsx', 'vue',
    'json', 'xml', 'yaml', 'yml', 'java', 'kt', 'py', 'go', 'rs', 'c', 'h', 'cpp',
    'hpp', 'cs', 'php', 'rb', 'sh', 'sql', 'properties', 'ini', 'toml',
  ]),
}

export type PreviewKind = keyof typeof extensionGroups

export function getPreviewKind(fileName: string): PreviewKind | null {
  const extension = fileName.split('.').pop()?.toLowerCase() || ''
  for (const [kind, extensions] of Object.entries(extensionGroups)) {
    if (extensions.has(extension)) return kind as PreviewKind
  }
  return null
}

const routeNames: Record<PreviewKind, string> = {
  image: 'ImagePreview',
  video: 'VideoPlayer',
  pdf: 'PDFPreview',
  word: 'WordPreview',
  excel: 'ExcelPreview',
  ppt: 'PPTPreview',
  markdown: 'MarkdownPreview',
  archive: 'ArchivePreview',
  code: 'CodePreview',
}

export function buildPreviewRoute(file: any): RouteLocationRaw | null {
  const fileName = file?.node_name || file?.name || ''
  const kind = getPreviewKind(fileName)
  if (!kind || !file?.node_id) return null
  return {
    name: routeNames[kind],
    params: { fileId: file.node_id },
    query: {
      name: fileName,
      size: String(file.node_size || file.file_size || 0),
      from: 'dashboard',
    },
  }
}

export function navigateToFilePreview(router: Router, file: any): boolean {
  const target = buildPreviewRoute(file)
  if (!target) return false
  void router.push(target)
  return true
}
