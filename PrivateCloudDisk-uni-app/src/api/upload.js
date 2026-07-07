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
 * 上传文件（简化封装，带进度回调）
 *
 * @param {string} filePath 文件临时路径
 * @param {Object} options  { onProgress(percent) }
 */
export function uploadFile(filePath, options = {}) {
  const token = getToken()
  const userId = getUserId()

  return new Promise((resolve, reject) => {
    const uploadTask = uni.uploadFile({
      url: `${FILE_BASE_URL}/files/uploads/simple`,
      filePath,
      name: 'file',
      header: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(userId ? { 'X-User-Id': userId } : {})
      },
      success(res) {
        try {
          const body = JSON.parse(res.data)
          if (body.code === 200) resolve(body)
          else reject(new Error(body.message || '上传失败'))
        } catch (e) {
          reject(new Error('响应解析失败'))
        }
      },
      fail(err) {
        reject(err)
      }
    })

    // 上传进度回调
    if (options.onProgress) {
      uploadTask.onProgressUpdate((res) => {
        options.onProgress(res.progress)
      })
    }
  })
}

/**
 * 上传单个分片 (FastAPI 文件服务)
 *
 * @param {string} uploadsId   上传会话 ID
 * @param {number} chunkIndex  分片索引 (1-based)
 * @param {string} filePath    分片临时文件路径
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

/** 合并分片 (FastAPI 文件服务) */
export function mergeChunks(uploadsId) {
  return post(`${BASE}/${uploadsId}/merge`)
}