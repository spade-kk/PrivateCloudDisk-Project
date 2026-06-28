// ============================================================
// preview.ts — 文件预览 API 模块
// ============================================================
// 封装文件预览相关接口：预览令牌获取、预览 URL、缩略图生成、
// 文件元数据、Office 文档转换、预览历史记录等。
// 支持多种预览模式：inline（内联嵌入）、attachment（下载预览）。
// ============================================================

import { get, del, post } from '@/utils/request'

// ============================================================
// 预览令牌与 URL
// ============================================================

/**
 * 获取文件预览 Token
 *
 * 用于获取文件预览所需的临时访问令牌。
 * 令牌有有效期，过期后需重新获取。
 *
 * @param file_id - 文件 ID
 * @returns Promise<{ preview_token: string, expires_at: string }> 预览令牌
 */
export function getFilePreviewTokenApi(file_id: string): Promise<any> {
  return get(`files/files/${file_id}/preview-token`)
}

/**
 * 获取文件预览 URL
 *
 * 根据文件类型和预览模式获取预览 URL。
 * 支持 inline（内嵌展示）和 attachment（下载预览）两种模式。
 *
 * @param file_id - 文件 ID
 * @param options - 预览选项
 * @param options.mode - 预览模式: 'inline'（内联）, 'attachment'（下载）
 * @param options.thumbnail - 是否获取缩略图: 'true', 'false'
 * @returns Promise<{ preview_url: string, preview_type: string }> 预览 URL 和类型
 */
export function getFilePreviewUrlApi(
  file_id: string,
  options: { mode?: string; thumbnail?: string } = {},
): Promise<any> {
  const params = new URLSearchParams(options as any).toString()
  return get(`files/files/${file_id}/preview-url${params ? '?' + params : ''}`)
}

// ============================================================
// 文件元数据
// ============================================================

/**
 * 获取文件元数据
 *
 * 返回文件详细元数据用于预览组件初始化：
 * 尺寸、分辨率、时长、编码格式、页码等（取决于文件类型）。
 *
 * @param file_id - 文件 ID
 * @returns Promise<{ file_id, file_name, size, mime_type, ... }> 文件元数据
 */
export function getFileMetadataApi(file_id: string): Promise<any> {
  return get(`files/files/${file_id}/metadata`)
}

// ============================================================
// 缩略图
// ============================================================

/**
 * 生成文件缩略图
 *
 * 请求后端生成指定尺寸的缩略图，用于文件列表、网格视图等场景。
 * 支持 cover（裁剪填充）和 contain（缩放适配）两种模式。
 *
 * @param file_id - 文件 ID
 * @param options - 缩略图选项
 * @param options.width - 缩略图宽度（像素）
 * @param options.height - 缩略图高度（像素）
 * @param options.type - 缩略图模式: 'cover'（裁剪填充）, 'contain'（缩放适配）
 * @returns Promise<{ thumbnail_url: string }> 缩略图 URL
 */
export function generateThumbnailApi(
  file_id: string,
  options: { width?: number; height?: number; type?: string } = {},
): Promise<any> {
  return get(`files/files/${file_id}/thumbnail`, options)
}

/**
 * 获取缩略图直接 URL
 *
 * 返回可直接用于 <img src="..."> 的缩略图 URL。
 * 支持三种预设尺寸：small(100×100)、medium(400×400)、large(800×800)。
 *
 * @param fileId - 文件 ID
 * @param size - 缩略图尺寸: 'small' | 'medium' | 'large'
 * @returns 缩略图 URL 字符串
 */
export function getThumbnailUrl(
  fileId: string,
  size: 'small' | 'medium' | 'large' = 'small',
): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  return `${baseUrl}/files/files/${encodeURIComponent(fileId)}/thumbnail?size=${size}`
}

// ============================================================
// 预览缓存
// ============================================================

/**
 * 清除文件预览缓存
 *
 * 当文件内容更新后，清除预览缓存以强制重新生成预览。
 * 适用于文件内容变更后需要重新预览的场景。
 *
 * @param file_id - 文件 ID
 * @returns Promise 清除结果
 */
export function clearPreviewCacheApi(file_id: string): Promise<any> {
  return del(`files/files/${file_id}/preview-cache`)
}

// ============================================================
// 支持格式
// ============================================================

/**
 * 获取系统支持的预览格式列表
 *
 * 返回后端当前支持预览的文件格式，前端据此控制预览按钮的可用性。
 *
 * @returns Promise<{ formats: string[] }> 支持的文件格式列表
 */
export function getSupportedFormatsApi(): Promise<any> {
  return get('files/supported-formats')
}

// ============================================================
// Office 文档转换
// ============================================================

/**
 * 获取 Office 文档转换状态
 *
 * 检查 Office 文档是否已转换为可预览的 PDF/图片格式。
 * 用于决定是否显示"预览"按钮还是"请求转换"按钮。
 *
 * @param file_id - 文件 ID
 * @returns Promise<{ status: 'pending'|'processing'|'completed'|'failed', preview_url?: string }> 转换状态
 */
export function getDocumentConversionStatusApi(file_id: string): Promise<any> {
  return get(`files/files/${file_id}/conversion-status`)
}

/**
 * 请求 Office 文档转换
 *
 * 触发后端将 Office 文档（Word/Excel/PPT）转换为可预览的 PDF 或图片格式。
 * 转换是异步的，需通过 getDocumentConversionStatusApi 轮询状态。
 *
 * @param file_id - 文件 ID
 * @param options - 转换选项
 * @param options.format - 目标格式: 'pdf'（PDF）, 'images'（图片集）
 * @param options.dpi - 转换 DPI 分辨率（默认 150）
 * @returns Promise<{ task_id: string }> 转换任务 ID
 */
export function requestDocumentConversionApi(
  file_id: string,
  options: { format?: string; dpi?: number } = {},
): Promise<any> {
  return post(`files/files/${file_id}/convert`, options)
}

// ============================================================
// 预览历史
// ============================================================

/**
 * 记录文件预览历史
 *
 * 记录用户的预览行为，用于数据分析、推荐算法、最近查看等场景。
 * 仅记录元数据，不记录文件内容。
 *
 * @param file_id - 文件 ID
 * @param metadata - 预览元数据（如预览时长、预览来源等）
 * @returns Promise 记录结果
 */
export function recordPreviewHistoryApi(file_id: string, metadata: Record<string, any> = {}): Promise<any> {
  return post(`files/files/${file_id}/preview-history`, metadata)
}