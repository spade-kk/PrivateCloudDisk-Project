// ============================================================
// downloads.ts — 文件下载 API 模块
// ============================================================
// 封装文件下载相关接口：操作令牌管理、文件内容获取、Range 分片下载。
// 通过操作令牌机制实现安全的下载授权，支持断点续传和进度监听。
// ============================================================

import { get, post, del } from '@/utils/request'

// ============================================================
// 操作令牌管理
// ============================================================

/**
 * 创建下载操作令牌
 *
 * 在下载文件前需先获取操作令牌，后端验证权限后返回临时 Token。
 * 令牌有有效期限制，过期后需重新获取。
 *
 * @param file_id - 要下载的文件 ID
 * @param operation_type - 操作类型，如 "download"、"preview"、"stream"
 * @returns Promise<{ operation_token: string, expires_at: string }> 操作令牌及过期时间
 */
export function createOperationTokenApi(file_id: string, operation_type: string): Promise<any> {
  const data = { file_id, operation_type }
  return post('files/operation-tokens', data)
}

/**
 * 取消/销毁操作令牌
 *
 * 当用户取消下载或预览时，主动销毁令牌以释放后端资源。
 * 令牌过期后也会自动失效。
 *
 * @param operation_token - 要取消的操作令牌
 * @returns Promise 取消结果
 */
export function cancelOperationApi(operation_token: string): Promise<any> {
  const data = { operation_token }
  return del('files/operation-tokens/', data)
}

// ============================================================
// 文件内容下载
// ============================================================

/**
 * 获取文件内容（完整下载）
 *
 * 通过操作令牌获取文件 Blob 数据，支持进度回调。
 * 返回 Blob 后前端通过 downloadBlob() 触发浏览器下载。
 *
 * @param file_id - 文件 ID
 * @param operation_token - 下载操作令牌
 * @param onProgress - 可选的下载进度回调函数
 * @returns Promise<Blob> 文件二进制数据
 */
export function getFileContentApi(
  file_id: string,
  operation_token: string,
  onProgress?: (progressEvent: ProgressEvent) => void,
): Promise<any> {
  return get(`files/files/${file_id}/content`, {}, {
    responseType: 'blob',
    headers: { 'X-Operation-Token': operation_token },
    onDownloadProgress: onProgress,
  })
}

/**
 * 获取文件内容分片（Range 下载 / 断点续传）
 *
 * 通过 HTTP Range 头指定字节范围，实现分片下载和断点续传。
 * 适用于大文件下载和视频流式播放场景。
 *
 * @param file_id - 文件 ID
 * @param operation_token - 下载操作令牌
 * @param start - 起始字节偏移（包含）
 * @param end - 结束字节偏移（包含）
 * @returns Promise<Blob> 指定范围的二进制数据
 */
export function getFileContentChunkApi(
  file_id: string,
  operation_token: string,
  start: number,
  end: number,
): Promise<any> {
  return get(`files/files/${file_id}/content`, {}, {
    responseType: 'blob',
    headers: {
      'X-Operation-Token': operation_token,
      'Range': `bytes=${start}-${end}`,
    },
  })
}