// ============================================================
// previewContent.ts — 原始文件内容预览授权与读取
// ============================================================
// 需求三-1/2：该模块与 downloads.ts 明确分离。Preview Token 只用于
// Markdown、图片、代码/文本源文件的临时展示，不触发下载事件或最近访问记录。
// ============================================================

import { get, post } from '@/utils/request'

export interface PreviewGrantData {
  preview_grant: string
  expires_at: number
  file_name: string
  file_size: number
  preview_kind: 'markdown' | 'image' | 'text'
}

export function createPreviewGrantApi(fileId: string): Promise<any> {
  return post('files/preview-grants', { file_id: fileId }, { skipAuthRedirect: true })
}

export function releasePreviewGrantApi(previewGrant: string): Promise<any> {
  return post(
    'files/preview-grants/release',
    { preview_grant: previewGrant },
    { skipAuthRedirect: true, silent: true },
  )
}

export function getPreviewContentApi(fileId: string, previewGrant: string): Promise<Blob> {
  return get(
    `files/files/${encodeURIComponent(fileId)}/preview-content`,
    {},
    {
      responseType: 'blob',
      headers: { 'X-Preview-Grant': previewGrant },
      skipAuthRedirect: true,
      timeout: 30_000,
    },
  )
}

/**
 * 申请 → 读取 → 释放组成单次完整预览事务。
 *
 * 令牌释放失败只等待 Redis TTL 回收，不覆盖已成功取得的内容；令牌申请或读取失败
 * 由页面统一展示“重试”按钮，避免自动无限重试放大限流压力。
 */
export async function fetchPreviewContentBlob(fileId: string): Promise<Blob> {
  let previewGrant = ''
  try {
    const grantResponse = await createPreviewGrantApi(fileId)
    previewGrant = grantResponse?.data?.preview_grant || ''
    if (!previewGrant) throw new Error('无法获取预览授权，请稍后重试')
    const content = await getPreviewContentApi(fileId, previewGrant)
    return content instanceof Blob ? content : new Blob([content as any])
  } finally {
    if (previewGrant) {
      await releasePreviewGrantApi(previewGrant).catch(() => {
        // 主内容读取结果优先；释放失败由短 TTL 自动兜底。
      })
    }
  }
}

export function getPreviewErrorMessage(error: any): string {
  const status = error?.status || error?.response?.status
  if (status === 429) return '同时预览的文件过多，请关闭部分预览页或稍后重试'
  if (status === 413) return '文件超过在线预览大小上限，请使用下载功能'
  if (status === 415) return '该文件类型不支持读取原始内容，请使用对应的专用预览'
  if (status === 401) return '预览授权已过期，请点击重新加载'
  if (status === 404) return '文件不存在或已被移动'
  return error?.message || '文件预览加载失败，请稍后重试'
}
