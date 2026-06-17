// ============================================================
// uploads.ts — 文件上传 API 模块
// ============================================================
// 封装分片上传流程：创建上传会话 → 分片上传 → 完成合并 → 轮询任务状态。
// 支持大文件断点续传（通过 uploads_id 追踪），配合 AbortController 支持取消。
//
// 上传流程：
//   1. createUploadsSessionApi() — 创建上传会话，返回 uploads_id
//   2. uploadFileChunkApi()       — 逐个上传分片（可并发、可暂停）
//   3. completeUploadSessionApi() — 通知后端合并分片，返回 task_id
//   4. getTaskStatusApi()         — 轮询任务状态（合并/哈希/病毒扫描/缩略图/转码）
//
// 架构优化 TODO：
//   后续可将 getTaskStatusApi 的轮询替换为 WebSocket 推送通知，
//   服务端在任务状态变更时通过 WebSocket 推送 task_id + status，
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
 * @param total_chunks - 总分片数（文件大小 / 分片大小 向上取整）
 * @param file_size - 文件总大小（字节）
 * @param file_checksum - 文件 SHA-256 校验和，用于上传完成后验证完整性
 * @param chunks_max_size - 每个分片的最大大小（字节）
 * @param file_type - 文件 MIME 类型
 * @param file_name - 原始文件名
 * @param node_id - 目标目录节点 ID（上传到哪个文件夹）
 * @returns Promise<{ uploads_id: string }> 上传会话 ID
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
  const data = {
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
 * @returns Promise 分片上传结果
 */
export function uploadFileChunkApi(
  uploads_id: string,
  chunk_index: number,
  upload_file_chunk: File,
  signal?: AbortSignal,
): Promise<any> {
  const data = new FormData()
  data.append('chunk_index', String(chunk_index))
  data.append('file', upload_file_chunk)
  return post(`files/uploads/${uploads_id}/chunks`, data, {
    signal,
    timeout: 0, // 不设超时，大分片上传可能耗时较长
  })
}

// ============================================================
// 上传完成与任务状态
// ============================================================

/**
 * 完成上传会话，通知服务器合并文件分片
 *
 * 注意：此接口返回的是异步任务 ID，不代表文件已合并完成。
 * 返回格式：{ code: 200, data: { task_id, file_id, status: "processing" } }
 * 需通过 getTaskStatusApi 轮询任务完成的实际状态。
 *
 * @param uploads_id - 上传会话 ID
 * @returns Promise<{ task_id: string, file_id: string, status: string }> 异步任务信息
 */
export function completeUploadSessionApi(uploads_id: string): Promise<any> {
  return post(`files/uploads/${uploads_id}/merge`)
}

/**
 * 查询文件处理任务状态（轮询用）
 *
 * 用于在上传完成后轮询后端文件处理进度。
 * 后端处理流程：合并分片 → 哈希校验 → 病毒扫描 → 缩略图生成 → 视频转码 → 标记为可用
 *
 * 返回格式：{ code: 200, data: { task_id, file_id, status, current_step, steps[] } }
 *
 * 状态说明：
 *   - "pending":    等待处理
 *   - "processing": 正在处理中（查看 current_step 了解当前步骤）
 *   - "completed":  处理完成，文件已可用
 *   - "failed":     处理失败，查看 error 字段了解原因
 *   - "cancelled":  已取消
 *
 * 当前步骤 (current_step)：
 *   - "merge":           分片合并中
 *   - "hash_calculate":  哈希校验中
 *   - "virus_scan":      病毒扫描中
 *   - "thumbnail":       缩略图生成中
 *   - "video_transcode": 视频转码中
 *   - "mark_active":     标记为可用
 *
 * TODO: 后续可替换为 WebSocket 推送通知，避免轮询开销。
 * WebSocket 方案：服务端在任务状态变更时通过 WebSocket 推送 task_id + status，
 * 前端收到通知后更新对应任务状态，status === "completed" 时刷新文件列表。
 *
 * @param task_id - 任务 ID（由 completeUploadSessionApi 返回）
 * @returns Promise<{ task_id, file_id, status, current_step, steps[] }> 任务状态
 */
export function getTaskStatusApi(task_id: string): Promise<any> {
  return get(`files/tasks/${task_id}`)
}