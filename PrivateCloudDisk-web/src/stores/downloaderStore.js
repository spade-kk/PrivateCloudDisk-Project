import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getFileContentApi, getFileContentChunkApi, createOperationTokenApi } from '@/api'
import { CHUNK_SIZE, MAX_CONCURRENT_DOWNLOADS, UPLOAD_THRESHOLD } from '@/utils/constants'
import { useToastStore } from './toastStore'

export const useDownloaderStore = defineStore('downloader', () => {
  const toastStore = useToastStore()
  const downloading = ref(false)
  const downloadProgress = ref(0)

  /**
   * 下载文件（自动选择全量或分片）
   * @param {string} nodeId
   * @param {number} fileSize
   * @param {Function} onProgress
   * @returns {Promise<Blob>}
   */
  async function downloadFile(nodeId, fileSize, onProgress) {
    downloading.value = true
    downloadProgress.value = 0
    try {
      // 获取操作令牌
      const initRes = await createOperationTokenApi(nodeId, 'download');

      if (initRes.code !== 200) {
        console.error('获取操作令牌失败', initRes)
        throw new Error(initRes.message || '获取下载令牌失败')
      }
      const operationToken = initRes.data.operation_token

      if (fileSize < UPLOAD_THRESHOLD) {
        // 小文件全量下载
        const res = await getFileContentApi(nodeId, operationToken, (progressEvent) => {
          if (onProgress && progressEvent.total) {
            const percent = (progressEvent.loaded / progressEvent.total) * 100
            downloadProgress.value = percent
            onProgress(percent)
          }
        })
        downloadProgress.value = 100
        if (onProgress) onProgress(100)
        return res
      } else {
        // 大文件分片下载
        return await downloadLargeFile(nodeId, fileSize, operationToken, onProgress)
      }
    } catch (error) {
      toastStore.showToast('下载失败：' + (error.message || '网络错误'), 'error')
      throw error
    } finally {
      downloading.value = false
    }
  }

  /**
   * 分片下载大文件
   */
  async function downloadLargeFile(nodeId, fileSize, operationToken, onProgress) {
    const totalChunks = Math.ceil(fileSize / CHUNK_SIZE)
    const chunks = new Array(totalChunks).fill(null)
    let downloadedSize = 0

    const downloadChunk = async (index) => {
      const start = index * CHUNK_SIZE
      const end = index === totalChunks - 1 ? fileSize - 1 : start + CHUNK_SIZE - 1

      const res = await getFileContentChunkApi(nodeId, operationToken, start, end)

      chunks[index] = res
      downloadedSize += res.size
      const percent = (downloadedSize / fileSize) * 100
      downloadProgress.value = percent
      if (onProgress) onProgress(percent)
    }

    // 并发下载
    const queue = Array.from({ length: totalChunks }, (_, i) => i)
    const workers = Array(MAX_CONCURRENT_DOWNLOADS).fill().map(async () => {
      while (queue.length) {
        const idx = queue.shift()
        if (idx !== undefined) await downloadChunk(idx)
      }
    })
    await Promise.all(workers)

    // 合并 Blob
    return new Blob(chunks)
  }

  return { downloading, downloadProgress, downloadFile }
})