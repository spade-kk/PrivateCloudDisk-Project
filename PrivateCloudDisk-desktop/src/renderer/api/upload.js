/**
 * api/upload.js - 分片上传 API
 *
 * 流程:
 * 1. 创建上传会话 → platform service POST /business/uploads/
 * 2. 上传分片 → file service POST /files/uploads/{uploads_id}/chunks (multipart)
 * 3. 合并分片 → file service POST /files/uploads/{uploads_id}/merge
 */
import { get, post } from '@/utils/request'
import { uploadFile } from '@/utils/request'
import { FILE_BASE_URL } from '@/utils/const'

/** 创建上传会话 (platform service)
 *  body: {
 *    total_chunks: int,
 *    file_size: int,
 *    file_checksum: string,
 *    chunks_max_size: int,
 *    file_name: string,
 *    file_type: string,
 *    node_id: string
 *  }
 *  返回: uploads_id (UUID string)
 */
export function createUploadSession(data) {
  return post('/uploads', data)
}

/** 上传分片 (file service, multipart form)
 *  form fields: chunk_index, file (binary)
 *  endpoint: POST /files/uploads/{uploads_id}/chunks
 */
export function uploadChunk(uploadsId, chunkIndex, filePath, start, end) {
  const formData = new FormData()
  formData.append('chunk_index', chunkIndex)

  // Read the file chunk from disk
  const fs = window.require('fs')
  const path = window.require('path')
  const buffer = Buffer.alloc(end - start)
  const fd = fs.openSync(filePath, 'r')
  fs.readSync(fd, buffer, 0, buffer.length, start)
  fs.closeSync(fd)

  const blob = new Blob([buffer])
  formData.append('file', blob, `${path.basename(filePath)}.chunk${chunkIndex}`)

  // Use fetch directly for multipart upload to file service
  const token = window.localStorage.getItem('pcd_token')
  return fetch(`${FILE_BASE_URL}/files/uploads/${uploadsId}/chunks`, {
    method: 'POST',
    headers: {
      'X-User-Id': window.localStorage.getItem('pcd_user_id') || '',
      'Authorization': token ? `Bearer ${token}` : ''
    },
    body: formData
  }).then(async (res) => {
    if (!res.ok) {
      const err = await res.json().catch(() => ({ detail: res.statusText }))
      throw new Error(err.detail || err.message || '上传分片失败')
    }
    return res.json()
  })
}

/** 合并分片 (file service)
 *  POST /files/uploads/{uploads_id}/merge
 *  返回: { task_id: string }
 */
export function mergeChunks(uploadsId) {
  return post(`/files/uploads/${uploadsId}/merge`, {}, 'file')
}