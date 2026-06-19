/**
 * api/download.js - 文件下载 & 操作凭证 API
 *
 * 后端: FastAPI file service
 *   - POST /files/operation-tokens  申请操作凭证
 *   - DELETE /files/operation-tokens/{token_id}  销毁凭证
 *   - GET /files/files/{file_id}/content  下载文件
 *   - GET /files/files/{file_id}/thumbnail  获取缩略图
 */
import { post, get, del } from '@/utils/request'
import { FILE_BASE_URL } from '@/utils/const'
import { getToken, getUserId } from '@/utils/storage'

/** 申请操作凭证 */
export function requestOperationToken(data) {
  return post('/files/operation-tokens', data, { service: 'file' })
}

/** 销毁操作凭证 */
export function revokeOperationToken(tokenId) {
  return del(`/files/operation-tokens/${tokenId}`, {}, { service: 'file' })
}

/**
 * 下载文件 (使用操作凭证)
 * 返回临时文件路径
 *
 * @param {string} fileId      文件 ID
 * @param {string} operationToken 操作凭证 JWT
 * @param {Function} [onProgress] 下载进度回调 (receivedBytes, totalBytes)
 */
export function downloadFile(fileId, operationToken, onProgress) {
  const url = `${FILE_BASE_URL}/files/files/${fileId}/content`
  const token = getToken()
  const userId = getUserId()

  return new Promise((resolve, reject) => {
    const downloadTask = uni.downloadFile({
      url,
      header: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(userId ? { 'X-User-Id': userId } : {}),
        'X-Operation-Token': operationToken
      },
      success(res) {
        if (res.statusCode === 200) {
          resolve(res.tempFilePath)
        } else {
          reject(new Error(`下载失败, 状态码: ${res.statusCode}`))
        }
      },
      fail(err) {
        reject(err)
      }
    })

    // 监听下载进度
    if (onProgress && downloadTask) {
      downloadTask.onProgressUpdate((res) => {
        onProgress(res.totalBytesWritten, res.totalBytesExpectedToWrite)
      })
    }
  })
}

/**
 * 获取文件缩略图 (返回临时图片路径)
 * @param {string} fileId
 * @param {string} operationToken
 * @param {number} [width=256]
 * @param {number} [height=256]
 */
export function getThumbnail(fileId, operationToken, width = 256, height = 256) {
  const url = `${FILE_BASE_URL}/files/files/${fileId}/thumbnail?width=${width}&height=${height}`
  const token = getToken()
  const userId = getUserId()

  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url,
      header: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(userId ? { 'X-User-Id': userId } : {}),
        'X-Operation-Token': operationToken
      },
      success(res) {
        if (res.statusCode === 200) {
          resolve(res.tempFilePath)
        } else {
          reject(new Error('获取缩略图失败'))
        }
      },
      fail(err) {
        reject(err)
      }
    })
  })
}