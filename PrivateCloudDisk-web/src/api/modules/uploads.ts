// ============================================================
// uploads.ts — 文件上传 API 模块
// ============================================================
// 封装分片上传流程：创建上传会话 → 分片上传 → 完成合并 → 轮询任务状态。
// 支持大文件断点续传（通过 uploads_id 追踪），配合 AbortController 支持取消。
//
// 上传流程：
//   1. createUploadsSessionApi()   — 创建上传会话，返回 uploads_id
//   2. uploadFileChunkApi()         — 逐个上传分片（可并发、可暂停）
//   3. completeUploadSessionApi()   — 通知后端合并分片，返回 backend_task_id + pipeline_id
//   4. getBackendTaskStatusApi()    — 轮询后台任务状态（合并/哈希/病毒扫描/标记活跃）
//
// 后端处理流水线（仅后台处理，不包含增强）：
//   merge → hash_calculate → virus_scan → mark_active
//
// 增强事件（缩略图、转码、HLS、索引）独立并发执行，不提供接口查询。
//
// 架构优化：
//   后续可将轮询替换为 WebSocket 推送通知，
//   服务端在任务状态变更时通过 WebSocket 推送 backend_task_id + status，
//   前端收到通知后更新对应任务状态，completed 时刷新文件列表。
// ============================================================

import { get, post } from '@/utils/request'

// ============================================================
// 上传会话管理
// ============================================================

/**
 * 创建上传会话
 *
 * 在上传大文件前先创建会话，后端分配 uploads_id 用于追踪分片。
 * 提供文件校验和（SHA-256）用于完整性验证。
 *
 * @param total_chunks   - 总分片数（文件大小 / 分片大小 向上取整）
 * @param file_size      - 文件总大小（字节）
 * @param file_checksum  - 文件 SHA-256 校验和
 * @param chunks_max_size - 每个分片的最大大小（字节）
 * @param file_type      - 文件 MIME 类型
 * @param file_name      - 原始文件名
 * @param node_id        - 目标目录节点 ID
 * @returns Promise<{ uploads_id: string }>
 */
export function createUploadsSessionApi(
  total_chunks: number,
  file_size: number,
  file_checksum: string,
  chunks_max_size: number,
  file_type: string,
  file_name: string,
  node_id: string,
): Promise<any> {
  const data: Record<string, any> = {
    total_chunks,
    file_size,
    file_checksum,
    chunks_max_size,
    file_type,
    file_name,
    node_id,
  }
  return post('business/uploads/', data)
}

// ============================================================
// 分片上传
// ============================================================

/**
 * 上传单个文件分片
 *
 * 使用 FormData 上传分片数据，可通过 signal 参数取消上传。
 * timeout 设为 0 表示不设超时（大分片上传可能耗时较长）。
 *
 * 并发控制：建议使用 MAX_CONCURRENT_UPLOADS 常量控制并发数，
 * 通过 Promise 队列实现并发分片上传。
 *
 * @param uploads_id - 上传会话 ID（由 createUploadsSessionApi 返回）
 * @param chunk_index - 分片索引（从 0 开始）
 * @param upload_file_chunk - 分片 File 对象（Blob.slice() 切分得到）
 * @param signal - 可选的 AbortSignal，用于取消上传
 * @param onProgress - 可选的上传进度回调 (loaded, total)，用于实时速率计算
 * @returns Promise 分片上传结果
 */
export function uploadFileChunkApi(
  uploads_id: string,
  chunk_index: number,
  upload_file_chunk: File,
  signal?: AbortSignal,
  onProgress?: (loaded: number, total: number) => void,
): Promise<any> {
  const data = new FormData()
  data.append('chunk_index', String(chunk_index))
  data.append('file', upload_file_chunk)
  return post(`files/uploads/${uploads_id}/chunks`, data, {
    signal,
    timeout: 0,
    onUploadProgress: onProgress
      ? (progressEvent: ProgressEvent) => {
          onProgress(progressEvent.loaded, progressEvent.total)
        }
      : undefined,
  })
}

// ============================================================
// 上传完成与后台任务状态
// ============================================================

/**
 * 完成上传会话，通知服务器合并文件分片
 *
 * 返回格式：
 *   { code: 200, data: { backend_task_id, pipeline_id, file_id, status: "processing" } }
 *
 * backend_task_id 用于后续轮询后台任务状态。
 * pipeline_id 用于日志关联追踪。
 *
 * @param uploads_id - 上传会话 ID
 * @returns Promise<{ backend_task_id: string, pipeline_id: string, file_id: string }>
 */
export function completeUploadSessionApi(uploads_id: string): Promise<any> {
  return post(`files/uploads/${uploads_id}/merge`)
}

/**
 * 查询后台文件处理任务状态（轮询用）
 *
 * 用于在上传完成后轮询后台文件处理进度。
 * 仅查询后台处理阶段（merge → hash_calculate → virus_scan → mark_active），
 * 不包含增强事件（缩略图、转码等独立并发执行，不提供查询接口）。
 *
 * 返回格式：
 *   {
 *     code: 200,
 *     data: {
 *       backend_task_id: string,
 *       file_id: string,
 *       file_name: string,
 *       status: "processing" | "completed" | "failed",
 *       current_stage: "merge" | "hash_calculate" | "virus_scan" | "mark_active",
 *       created_at: string,
 *       updated_at: string,
 *       stages: [
 *         { stage: "merge",            status: "completed", summary: "completed" },
 *         { stage: "hash_calculate",   status: "completed", summary: "completed" },
 *         { stage: "virus_scan",       status: "processing", summary: "processing" },
 *         { stage: "mark_active",      status: "pending", summary: "none" }
 *       ]
 *     },
 *     message: null
 *   }
 *
 * status 说明：
 *   - "processing":  处理中（查看 current_stage 了解当前阶段）
 *   - "completed":   全部处理完成，文件已可用
 *   - "failed":      处理失败
 *
 * stages 中各阶段 status：
 *   - "processing":  该阶段处理中
 *   - "completed":   该阶段已完成
 *   - "failed":      该阶段失败
 *   - "pending":     该阶段尚未开始
 *
 * 轮询策略：
 *   - 每 2s 轮询一次，最多 150 次（5 分钟）
 *   - status === "completed" → 停止轮询，刷新文件列表
 *   - status === "failed" → 停止轮询，提示失败
 *
 * @param backend_task_id - 后台任务 ID（由 completeUploadSessionApi 返回）
 * @returns Promise<{ backend_task_id, file_id, status, current_stage, stages[] }>
 */
export function getBackendTaskStatusApi(backend_task_id: string): Promise<any> {
  return get(`files/tasks/${backend_task_id}`)
}