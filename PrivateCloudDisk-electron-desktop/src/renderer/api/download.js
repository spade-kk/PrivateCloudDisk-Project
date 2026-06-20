/**
 * api/download.js - 文件下载 API
 *
 * 流程:
 * 1. 申请操作凭证 → file service POST /files/operation-tokens (with X-User-Id)
 * 2. 下载文件 → file service GET /downloads/files/{file_id}/content?token={op_token}
 * 3. 获取缩略图 → file service GET /files/nodes/{node_id}/thumbnails/{file_name}
 */
import { get, post } from '@/utils/request'
import { FILE_BASE_URL } from '@/utils/const'

/** 申请操作凭证 (file service)
 *  body: { file_id, operation_type }
 *  header: X-User-Id
 *  返回: { operation_token: string }
 */
export function requestOperationToken(data) {
  return post('/files/operation-tokens', data, 'file')
}

/** 获取缩略图 URL (file service)
 *  GET /files/nodes/{node_id}/thumbnails/{file_name}
 */
export function getThumbnailUrl(nodeId, fileName) {
  const token = window.localStorage.getItem('pcd_token')
  return `${FILE_BASE_URL}/files/nodes/${nodeId}/thumbnails/${encodeURIComponent(fileName)}?token=${token || ''}`
}

/** 下载文件 (Electron/IPC 调用)
 *  file service: GET /downloads/files/{file_id}/content?token={op_token}
 *  Range 请求头支持断点续传
 */
export function downloadFile(fileId, operationToken, fileName, onProgress) {
  return new Promise((resolve, reject) => {
    const url = `${FILE_BASE_URL}/downloads/files/${fileId}/content?token=${operationToken}`

    // Use Electron IPC to download
    if (window.electronAPI?.downloadFile) {
      window.electronAPI.downloadFile(url, fileName, onProgress)
        .then(resolve)
        .catch(reject)
      return
    }

    // Fallback: fetch download
    fetch(url, {
      headers: {
        'X-User-Id': window.localStorage.getItem('pcd_user_id') || ''
      }
    })
      .then(response => {
        if (!response.ok) throw new Error('下载失败')
        const contentLength = response.headers.get('content-length')
        const total = parseInt(contentLength, 10)
        let loaded = 0

        const reader = response.body.getReader()
        const chunks = []

        function pump() {
          return reader.read().then(({ done, value }) => {
            if (done) {
              const blob = new Blob(chunks)
              const url = URL.createObjectURL(blob)
              const a = document.createElement('a')
              a.href = url
              a.download = fileName
              a.click()
              URL.revokeObjectURL(url)
              resolve(fileName)
              return
            }
            chunks.push(value)
            loaded += value.length
            if (onProgress && total) {
              onProgress(Math.round((loaded / total) * 100))
            }
            return pump()
          })
        }
        return pump()
      })
      .catch(reject)
  })
}

/** 文件夹下载（打包为 ZIP）
 *  1. 调用 Spring Boot 获取文件夹下所有文件信息
 *  2. 调用 FastAPI 打包下载 ZIP
 *  3. 通过 Electron IPC 保存文件到本地
 *
 * @param {string} nodeId - 文件夹节点ID
 * @param {string} nodeName - 文件夹名称
 * @param {Array} files - 文件列表 [{ fileId, fileName, fileSize, storagePath }]
 * @param {function} onProgress - 进度回调 (percent)
 * @returns {Promise<string>} 保存的文件路径
 */
export async function downloadFolder(nodeId, nodeName, files, onProgress) {
  const url = `${FILE_BASE_URL}/downloads/folders`
  const userId = window.localStorage.getItem('pcd_user_id') || ''
  const token = window.localStorage.getItem('pcd_token') || ''

  const requestBody = {
    node_name: nodeName,
    files: files.map(f => ({
      file_id: f.fileId,
      file_name: f.fileName,
      file_size: f.fileSize,
      storage_path: f.storagePath
    }))
  }

  // 使用 Electron IPC 下载（支持保存对话框）
  if (window.electronAPI?.saveFileDialog) {
    try {
      const savePath = await window.electronAPI.saveFileDialog({
        defaultName: `${nodeName}.zip`,
        filters: [{ name: 'ZIP 文件', extensions: ['zip'] }]
      })
      if (!savePath) throw new Error('用户取消下载')

      // 通过 fetch 下载 ZIP 文件
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-User-Id': userId,
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify(requestBody)
      })

      if (!response.ok) {
        const errorText = await response.text()
        throw new Error(`下载失败: ${errorText}`)
      }

      const contentLength = response.headers.get('content-length')
      const total = parseInt(contentLength, 10)
      let loaded = 0

      const reader = response.body.getReader()
      const chunks = []

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        chunks.push(value)
        loaded += value.length
        if (onProgress && total) {
          onProgress(Math.round((loaded / total) * 100))
        }
      }

      // 合并所有分片
      const blob = new Blob(chunks)
      const arrayBuffer = await blob.arrayBuffer()
      const uint8Array = new Uint8Array(arrayBuffer)

      // 通过 IPC 写入文件
      await window.electronAPI.writeFile(savePath, Array.from(uint8Array))
      return savePath
    } catch (e) {
      throw e
    }
  }

  // Fallback: 浏览器直接下载
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': userId,
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify(requestBody)
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`下载失败: ${errorText}`)
  }

  const blob = await response.blob()
  const downloadUrl = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = downloadUrl
  a.download = `${nodeName}.zip`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(downloadUrl)

  return `${nodeName}.zip`
}