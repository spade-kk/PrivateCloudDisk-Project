import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getFileContentApi, getFileContentChunkApi, createDownloadGrantApi, cancelDownloadGrantApi, releaseDownloadGrantApi } from '@/api'
import { CHUNK_SIZE, MAX_CONCURRENT_DOWNLOADS, UPLOAD_THRESHOLD } from '@/utils/constants'
import { SpeedSampler } from '@/utils/speedSampler'
import { useToastStore } from './toastStore'
import { useTransferStore } from './transferStore'
import { useThroughputStore } from './throughputStore'

export const useDownloaderStore = defineStore('downloader', () => {
  const toastStore = useToastStore()
  const transferStore = useTransferStore()
  const throughputStore = useThroughputStore()

  const downloading = ref(false)
  const downloadProgress = ref(0)

  // ============================================================
  // 企业级速率采样器（仅用于分块下载）
  // ============================================================
  const speedSampler = new SpeedSampler(5000, 200, 0.3)
  let speedTimer: ReturnType<typeof setInterval> | null = null

  /** 实时累计已接收字节数 */
  let accumulatedDownloadedBytes = 0

  function startDownloadSpeedMonitor(): void {
    accumulatedDownloadedBytes = 0
    speedSampler.reset()
    if (speedTimer) clearInterval(speedTimer)
    speedTimer = setInterval(() => {
      speedSampler.addSample(accumulatedDownloadedBytes)
      const { bps, formatted } = speedSampler.getFormattedSpeed()
      throughputStore.setDownloadSpeed(bps)
      if (transferId) {
        transferStore.updateProgress(transferId, downloadProgress.value, formatted)
      }
    }, 200)
  }

  function stopDownloadSpeedMonitor(): void {
    if (speedTimer) {
      clearInterval(speedTimer)
      speedTimer = null
    }
    throughputStore.setDownloadSpeed(0)
  }

  let transferId: number | null = null

  async function downloadFile(nodeId: string, fileSize: number, fileName: string): Promise<Blob> {
    downloading.value = true
    downloadProgress.value = 0

    transferId = transferStore.addRecord('download', fileName, fileSize)

    const onProgress = (percent: number): void => {
      downloadProgress.value = percent
      // 整文件下载不显示速度，传空字符串
      transferStore.updateProgress(transferId!, percent, '')
    }

    let download_grant = ''
    try {
      const initRes = await createDownloadGrantApi(nodeId)
      if (initRes.code !== 200) {
        const msg = initRes.message || '获取下载令牌失败'
        transferStore.failRecord(transferId!, msg)
        throw new Error(msg)
      }
      download_grant = initRes.data.download_grant

      let result: Blob
      if (fileSize < UPLOAD_THRESHOLD) {
        // 小文件：整文件下载，不显示速度
        result = await getFileContentApi(nodeId, download_grant, (progressEvent: ProgressEvent) => {
          if (progressEvent.total) {
            const percent = (progressEvent.loaded / progressEvent.total) * 100
            onProgress(percent)
          }
        })
        onProgress(100)
      } else {
        // 大文件：分块 Range 下载，显示实时速度
        result = await downloadLargeFile(nodeId, fileSize, download_grant, onProgress)
      }
      await releaseDownloadGrantApi(download_grant)
      stopDownloadSpeedMonitor()
      transferStore.finishRecord(transferId!)
      return result
    } catch (error: any) {
      stopDownloadSpeedMonitor()
      const record = transferStore.records.find(r => r.id === transferId!)
      if (record && record.status !== 'failed') {
        transferStore.failRecord(transferId!, error.message || '下载失败')
      }
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
    startDownloadSpeedMonitor()

    const totalChunks = Math.ceil(fileSize / CHUNK_SIZE)
    const chunks: Blob[] = new Array(totalChunks).fill(null)
    let downloadedSize = 0

    /** 每个分块当前已接收字节数（用于增量计算） */
    const chunkReceivedBytes = new Map<number, number>()

    const downloadChunk = async (index: number): Promise<void> => {
      const start = index * CHUNK_SIZE
      const end = index === totalChunks - 1 ? fileSize - 1 : start + CHUNK_SIZE - 1
      const chunkSize = end - start + 1

      chunkReceivedBytes.set(index, 0)

      const res = await getFileContentChunkApi(
        nodeId,
        operationToken,
        start,
        end,
        // onProgress: 实时追踪该分块已接收字节数
        (loaded: number) => {
          const prev = chunkReceivedBytes.get(index) || 0
          const delta = loaded - prev
          if (delta > 0) {
            chunkReceivedBytes.set(index, loaded)
            accumulatedDownloadedBytes += delta
          }
        },
      )

      chunks[index] = res
      // 确保该分块字节数精确计入
      const prev = chunkReceivedBytes.get(index) || 0
      if (prev < chunkSize) {
        accumulatedDownloadedBytes += (chunkSize - prev)
        chunkReceivedBytes.set(index, chunkSize)
      }
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