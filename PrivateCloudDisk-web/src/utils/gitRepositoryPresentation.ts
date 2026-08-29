// ============================================================
// gitRepositoryPresentation.ts — Git 公开仓库浏览器展示工具
// ============================================================
// [REQ-GIT-UIUX-20260816] 将文件类型、代码语言、Blame 与 Diff 的纯展示逻辑
// 从页面组件抽离，保证文件树、文件预览和提交详情采用同一规则，并可独立测试。

import type { GitCommit } from '@/api/modules/git'

export type GitFileKind = 'code' | 'markdown' | 'image' | 'pdf' | 'media' | 'binary'

const LANGUAGE_BY_EXTENSION: Record<string, string> = {
  go: 'go', ts: 'typescript', tsx: 'typescript', js: 'javascript', jsx: 'javascript',
  vue: 'xml', java: 'java', py: 'python', rs: 'rust', rb: 'ruby', php: 'php',
  c: 'c', h: 'c', cc: 'cpp', cpp: 'cpp', cxx: 'cpp', hpp: 'cpp', cs: 'csharp',
  json: 'json', yaml: 'yaml', yml: 'yaml', xml: 'xml', html: 'xml', htm: 'xml',
  css: 'css', scss: 'scss', sass: 'sass', less: 'less', sql: 'sql', sh: 'bash',
  bash: 'bash', zsh: 'bash', fish: 'bash', dockerfile: 'dockerfile', toml: 'ini',
  ini: 'ini', conf: 'ini', properties: 'properties', gradle: 'groovy', kt: 'kotlin',
  swift: 'swift', scala: 'scala', lua: 'lua', r: 'r', makefile: 'makefile',
}

const MARKDOWN_EXTENSIONS = new Set(['md', 'mdx', 'markdown', 'mdown', 'mkdn'])
const IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg', 'avif'])
const PDF_EXTENSIONS = new Set(['pdf'])
const MEDIA_EXTENSIONS = new Set(['mp3', 'wav', 'ogg', 'flac', 'm4a', 'mp4', 'webm', 'mov', 'mkv'])

export function getExtension(fileName: string): string {
  const index = fileName.lastIndexOf('.')
  return index >= 0 ? fileName.slice(index + 1).toLowerCase() : ''
}

export function detectGitFileKind(fileName: string, mimeType = ''): GitFileKind {
  const extension = getExtension(fileName)
  if (MARKDOWN_EXTENSIONS.has(extension)) return 'markdown'
  if (IMAGE_EXTENSIONS.has(extension) || mimeType.startsWith('image/')) return 'image'
  if (PDF_EXTENSIONS.has(extension) || mimeType === 'application/pdf') return 'pdf'
  if (MEDIA_EXTENSIONS.has(extension) || mimeType.startsWith('audio/') || mimeType.startsWith('video/')) return 'media'
  if (getGitLanguage(fileName)) return 'code'
  if (mimeType.startsWith('text/')) return 'code'
  return 'binary'
}

export function getGitLanguage(fileName: string): string {
  const normalized = fileName.split('/').pop()?.toLowerCase() || ''
  if (normalized === 'dockerfile') return 'dockerfile'
  if (normalized === 'makefile') return 'makefile'
  return LANGUAGE_BY_EXTENSION[getExtension(normalized)] || ''
}

export function gitFileIcon(fileName: string, isTree = false): string {
  if (isTree) return 'fa fa-folder-o text-amber-500'
  const kind = detectGitFileKind(fileName)
  if (kind === 'markdown') return 'fa fa-file-text-o text-emerald-600'
  if (kind === 'image') return 'fa fa-file-image-o text-purple-500'
  if (kind === 'pdf') return 'fa fa-file-pdf-o text-red-500'
  if (kind === 'media') return 'fa fa-file-video-o text-pink-500'
  if (kind === 'code') return 'fa fa-file-code-o text-sky-600'
  return 'fa fa-file-o text-slate-400'
}

export function formatGitRelativeTime(value?: string): string {
  if (!value) return '—'
  const timestamp = new Date(value).getTime()
  if (Number.isNaN(timestamp)) return '—'
  const seconds = Math.max(0, Math.floor((Date.now() - timestamp) / 1000))
  if (seconds < 60) return '刚刚'
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟前`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} 小时前`
  if (seconds < 86400 * 7) return `${Math.floor(seconds / 86400)} 天前`
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' }).format(timestamp)
}

export function escapeHtml(value: string): string {
  return value.replace(/[&<>'"]/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[character] || character)
}

export interface GitBlameLine {
  hash: string
  author: string
  authoredAt?: string
}

/** 解析 `git blame --line-porcelain`，一项对应最终文件的一行。 */
export function parseGitBlamePorcelain(content: string): GitBlameLine[] {
  const result: GitBlameLine[] = []
  let current: GitBlameLine = { hash: '', author: '' }
  for (const line of content.split('\n')) {
    const header = /^([0-9a-f]{40,64})\s+\d+\s+\d+(?:\s+(\d+))?$/.exec(line)
    if (header) {
      current = { hash: header[1], author: '' }
      continue
    }
    if (line.startsWith('author ')) current.author = line.slice('author '.length)
    if (line.startsWith('author-time ')) {
      const seconds = Number(line.slice('author-time '.length))
      if (Number.isFinite(seconds)) current.authoredAt = new Date(seconds * 1000).toISOString()
    }
    if (line.startsWith('\t')) result.push({ ...current })
  }
  return result
}

export interface GitDiffFile {
  path: string
  status: 'added' | 'deleted' | 'modified' | 'renamed'
  additions: number
  deletions: number
  content: string
}

/** 将 unified diff 按文件分段，供提交详情卡片的文件列表和展开 Diff 共用。 */
export function splitGitDiff(content: string): GitDiffFile[] {
  const chunks = content.split(/^diff --git /m).filter(Boolean)
  return chunks.map((chunk) => {
    const normalized = `diff --git ${chunk}`
    const pathMatch = /^diff --git a\/(.+?) b\/(.+)$/m.exec(normalized)
    const oldPath = pathMatch?.[1] || ''
    const newPath = pathMatch?.[2] || oldPath
    const added = normalized.match(/^\+(?!\+\+)/gm)?.length || 0
    const deleted = normalized.match(/^-(?!---)/gm)?.length || 0
    let status: GitDiffFile['status'] = 'modified'
    if (/^new file mode /m.test(normalized)) status = 'added'
    else if (/^deleted file mode /m.test(normalized)) status = 'deleted'
    else if (/^rename (from|to) /m.test(normalized)) status = 'renamed'
    return { path: status === 'deleted' ? oldPath : newPath, status, additions: added, deletions: deleted, content: normalized }
  })
}

export function shortHash(value: string, length = 8): string { return value.slice(0, length) }

export function commitTimestamp(commit: GitCommit): string | undefined { return commit.committedAt || commit.authoredAt }
