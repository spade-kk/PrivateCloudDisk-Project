/**
 * api/upload.js - 文件上传 API
 *
 * 后端: UploadsController -> /business/uploads (会话创建)
 *       FastAPI uploads endpoint -> /files/uploads/... (分片上传 & 合并)
 */
import { post } from '@/utils/request'
import { FILE_BASE_URL } from '@/utils/const'
import { getToken, getUserId } from '@/utils/storage'

const BASE = '/business/uploads'

/** 创建上传会话 (Spring Boot 平台服务) */
export function createUploadSession(data) {
  return post(`${BASE}/`, data)
}

/**
 * 上传单个分片 (FastAPI 文件服务)
 *
 * @param {string} uploadsId   上传会话 ID
 * @param {number} chunkIndex  分片索引 (1-based)
 * @param {string} filePath    分片临时文件路径 (uni.chooseFile 返回的)
 */
export function uploadChunk(uploadsId, chunkIndex, filePath) {
  const token = getToken()
  const userId = getUserId()

  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${FILE_BASE_URL}/files/uploads/${uploadsId}/chunks`,
      filePath,
      name: 'file',
      formData: { chunk_index: chunkIndex },
      header: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(userId ? { 'X-User-Id': userId } : {})
      },
      success(res) {
        try {
          const body = JSON.parse(res.data)
          if (body.code === 200) resolve(body)
          else reject(new Error(body.message || '分片上传失败'))
        } catch (e) {
          reject(new Error('响应解析失败'))
        }
      },
      fail(err) {
        reject(err)
      }
    })
  })
}

/** 合并文件分片 (FastAPI 文件服务) */
export function mergeChunks(uploadsId) {
  return post(`/files/uploads/${uploadsId}/merge`, {}, { service: 'file' })
}