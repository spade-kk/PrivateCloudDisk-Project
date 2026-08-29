// ============================================================
// imAttachmentUploader.ts — IM 附件上传适配器
// ============================================================
// [IM-WEB-ENTERPRISE-20260809 / 12.9,14.17]
// 改动原因：消息附件不能使用 blob: URL 发送给其他设备。该适配器复用网盘上传会话、
// 分块并发、分块重试与合并接口，合并接口返回 file_id 后再构建 FilePayload/ImagePayload。
// 影响范围：消息中心本地附件；不改变全局 uploaderStore 的传输列表和后处理轮询。
// ============================================================

import {
  completeUploadSessionApi,
  createUploadsSessionApi,
  getMyUserRootNodeApi,
  uploadFileChunkApi,
} from '@/api'
import { calculateSHA256 } from '@/utils/helpers'
import { CHUNK_SIZE, MAX_RETRIES } from '@/utils/constants'

export interface ImAttachmentUploadResult {
  fileId: string
  backendTaskId?: string
  fileName: string
  fileSize: number
  mimeType: string
}

export async function uploadImAttachment(
  file: File,
  onProgress?: (percentage: number) => void,
  signal?: AbortSignal,
): Promise<ImAttachmentUploadResult> {
  const root = await getMyUserRootNodeApi()
  const nodeId = root?.data?.node_id || root?.data?.nodeId
  if (!nodeId) throw new Error('无法获取附件上传目录')

  const checksum = await calculateSHA256(file)
  const totalChunks = Math.max(1, Math.ceil(file.size / CHUNK_SIZE))
  let sessionResponse: any
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      sessionResponse = await createUploadsSessionApi(
        totalChunks, file.size, checksum, CHUNK_SIZE,
        file.type || 'application/octet-stream', file.name, nodeId,
      )
      if (sessionResponse?.code === 200) break
      throw new Error(sessionResponse?.message || '创建附件上传会话失败')
    } catch (error) {
      if (attempt === 2) throw error
      await new Promise(resolve => setTimeout(resolve, 100 + Math.random() * 400))
    }
  }

  const uploadId = sessionResponse?.data?.uploads_id || sessionResponse?.data?.upload_id || sessionResponse?.data?.id
  if (!uploadId) throw new Error('附件上传会话缺少 uploads_id')

  let completedBytes = 0
  let nextChunk = 0
  const worker = async (): Promise<void> => {
    while (nextChunk < totalChunks) {
      if (signal?.aborted) throw new DOMException('附件上传已取消', 'AbortError')
      const index = nextChunk++
      const start = index * CHUNK_SIZE
      const end = Math.min(file.size, start + CHUNK_SIZE)
      const chunk = file.slice(start, end)
      let uploaded = false
      for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
        try {
          const response = await uploadFileChunkApi(uploadId, index + 1, chunk, signal)
          if (response?.code !== 200) throw new Error(response?.message || `分块 ${index + 1} 上传失败`)
          uploaded = true
          break
        } catch (error) {
          if (attempt >= MAX_RETRIES || signal?.aborted) throw error
          await new Promise(resolve => setTimeout(resolve, 300 * 2 ** attempt))
        }
      }
      if (!uploaded) throw new Error(`分块 ${index + 1} 重试耗尽`)
      completedBytes += chunk.size
      onProgress?.(Math.min(99, Math.round(completedBytes / Math.max(file.size, 1) * 100)))
    }
  }
  await Promise.all(Array.from({ length: Math.min(3, totalChunks) }, () => worker()))

  const merged = await completeUploadSessionApi(uploadId)
  if (merged?.code !== 200) throw new Error(merged?.message || '附件合并请求失败')
  const fileId = merged?.data?.file_id || merged?.data?.fileId
  if (!fileId) throw new Error('附件合并响应缺少 file_id')
  onProgress?.(100)
  return {
    fileId,
    backendTaskId: merged?.data?.backend_task_id,
    fileName: file.name,
    fileSize: file.size,
    mimeType: file.type || 'application/octet-stream',
  }
}

