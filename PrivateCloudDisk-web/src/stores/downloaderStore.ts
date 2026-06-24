import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getFileContentApi, getFileContentChunkApi, createDownloadGrantApi, cancelDownloadGrantApi, releaseDownloadGrantApi } from '@/api'
import { CHUNK_SIZE, MAX_CONCURRENT_DOWNLOADS, UPLOAD_THRESHOLD } from '@/utils/constants'
import { useToastStore } from './toastStore'
import { useTransferStore } from './transferStore'

export const useDownloaderStore = defineStore('downloader', () => {
  const toastStore = useToastStore()
  const transferStore = useTransferStore()

  const downloading = ref(false)
  const downloadProgress = ref(0)

  async function downloadFile(nodeId: string, fileSize: number, fileName: string): Promise<Blob> {
    downloading.value = true
    downloadProgress.value = 0

    const transferId = transferStore.addRecord('download', fileName, fileSize)

    const onProgress = (percent: number): void => {
      downloadProgress.value = percent
      transferStore.updateProgress(transferId, percent, '')
    }

    let download_grant = ''
    try {
      const initRes = await createDownloadGrantApi(nodeId)
      if (initRes.code !== 200) {
        const msg = initRes.message || '获取下载令牌失败'
        transferStore.failRecord(transferId, msg)
        throw new Error(msg)
      }
      download_grant = initRes.data.download_grant

      let result: Blob
      if (fileSize < UPLOAD_THRESHOLD) {
        result = await getFileContentApi(nodeId, download_grant, (progressEvent: ProgressEvent) => {
          if (progressEvent.total) {
            const percent = (progressEvent.loaded / progressEvent.total) * 100
            onProgress(percent)
          }
        })
        onProgress(100)
      } else {
        result = await downloadLargeFile(nodeId, fileSize, download_grant, onProgress)
      }
      //成功记得结束DownloadGrant防止影响后续下载
      await releaseDownloadGrantApi(download_grant)
      transferStore.finishRecord(transferId)
      return result
    } catch (error: any) {
      const record = transferStore.records.find(r => r.id === transferId)
      if (record && record.status !== 'failed') {
        transferStore.failRecord(transferId, error.message || '下载失败')
      }
      //失败记得销毁DownloadGrant防止影响后续下载
      await cancelDownloadGrantApi(download_grant)
      toastStore.showToast('下载失败：' + (error.message || '网络错误'), 'error')
      throw error
    } finally {
      downloading.value = false
    }
  }

  async function downloadLargeFile(
    nodeId: string,
    fileSize: number,
    operationToken: string,
    onProgress: (percent: number) => void
  ): Promise<Blob> {
    const totalChunks = Math.ceil(fileSize / CHUNK_SIZE)
    const chunks: Blob[] = new Array(totalChunks).fill(null)
    let downloadedSize = 0

    const downloadChunk = async (index: number): Promise<void> => {
      const start = index * CHUNK_SIZE
      const end = index === totalChunks - 1 ? fileSize - 1 : start + CHUNK_SIZE - 1

      const res = await getFileContentChunkApi(nodeId, operationToken, start, end)

      chunks[index] = res
      downloadedSize += res.size
      const percent = (downloadedSize / fileSize) * 100
      downloadProgress.value = percent
      if (onProgress) onProgress(percent)
    }

    const queue = Array.from({ length: totalChunks }, (_, i) => i)
    const workers = Array(MAX_CONCURRENT_DOWNLOADS).fill(null).map(async () => {
      while (queue.length) {
        const idx = queue.shift()
        if (idx !== undefined) await downloadChunk(idx)
      }
    })
    await Promise.all(workers)

    return new Blob(chunks)
  }

  return { downloading, downloadProgress, downloadFile }
})