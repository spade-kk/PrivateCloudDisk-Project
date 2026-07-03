// ============================================================
// folderDownloaderStore.ts — 文件夹下载业务逻辑
// ============================================================
// 完整文件夹下载流程：
//   1. 调用 getFolderFilesRecursiveApi 获取文件夹下所有文件清单（含相对路径）
//   2. 逐个文件：创建下载令牌 → 下载文件内容 → 保存到本地
//   3. 根据 relative_path 在本地重建文件夹结构
//
// 设计要点：
//   - 完全复用现有单文件下载流程
//   - 每个文件单独走 downloadGrant → getFileContent 流程
//   - 取消只停止后续文件下载，已下载文件保留
// ============================================================

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { SpeedSampler } from '@/utils/speedSampler'
import { useToastStore } from './toastStore'
import { useTransferStore } from './transferStore'
import {
  getFolderFilesRecursiveApi,
  createDownloadGrantApi,
  getFileContentApi,
  releaseDownloadGrantApi,
  cancelDownloadGrantApi,
  type FolderFileInfo,
} from '@/api/index'

// ============================================================
// 类型定义
// ============================================================

export interface FolderDownloadFileItem {
  fileInfo: FolderFileInfo
  status: 'pending' | 'downloading' | 'completed' | 'error'
  progress: number
  errorMessage?: string
}

// ============================================================
// Store
// ============================================================

export const useFolderDownloaderStore = defineStore('folderDownloader', () => {
  const toastStore = useToastStore()
  const transferStore = useTransferStore()

  const folderNodeId = ref<string | null>(null)
  const folderName = ref('')
  const status = ref<'idle' | 'fetching' | 'downloading' | 'completed' | 'cancelled' | 'error'>('idle')
  const files = ref<FolderDownloadFileItem[]>([])
  const currentFileIndex = ref(0)
  const totalBytes = ref(0)
  const downloadedBytes = ref(0)
  const errorMessage = ref('')
  const cancelled = ref(false)

  const speedSampler = new SpeedSampler(5000, 200, 0.3)
  let speedTimer: ReturnType<typeof setInterval> | null = null
  let accumulatedBytes = 0

  const folderProgress = computed(() => {
    if (files.value.length === 0) return 0
    const completed = files.value.filter(f => f.status === 'completed').length
    return Math.round((completed / files.value.length) * 100)
  })

  const isActive = computed(() =>
    status.value === 'fetching' || status.value === 'downloading'
  )

  // ============================================================
  // 速度监控
  // ============================================================

  function startSpeedMonitor(): void {
    accumulatedBytes = 0
    speedSampler.reset()
    if (speedTimer) clearInterval(speedTimer)
    speedTimer = setInterval(() => {
      speedSampler.addSample(accumulatedBytes)
    }, 200)
  }

  function stopSpeedMonitor(): void {
    if (speedTimer) {
      clearInterval(speedTimer)
      speedTimer = null
    }
  }

  function getSpeed(): string {
    return speedSampler.getFormattedSpeed().formatted
  }

  // ============================================================
  // 文件夹下载流程
  // ============================================================

  /**
   * 开始文件夹下载
   * @param nodeId 文件夹节点ID
   * @param nodeName 文件夹名称
   */
  async function startFolderDownload(
    nodeId: string,
    nodeName: string,
  ): Promise<void> {
    try {
      status.value = 'fetching'
      folderNodeId.value = nodeId
      folderName.value = nodeName
      cancelled.value = false

      // 1. 获取文件夹下所有文件清单（含相对路径）
      const res = await getFolderFilesRecursiveApi(nodeId)
      if (res.code !== 200) {
        throw new Error(res.message || '获取文件夹文件列表失败')
      }

      const fileList: FolderFileInfo[] = res.data || []
      if (fileList.length === 0) {
        status.value = 'completed'
        toastStore.showToast('文件夹为空，无需下载', 'info')
        return
      }

      files.value = fileList.map(f => ({
        fileInfo: f,
        status: 'pending' as const,
        progress: 0,
      }))

      totalBytes.value = fileList.reduce((sum, f) => sum + (f.fileSize || 0), 0)

      // 2. 逐个下载文件
      status.value = 'downloading'
      startSpeedMonitor()

      for (let i = 0; i < files.value.length; i++) {
        if (cancelled.value) break

        const fileItem = files.value[i]
        currentFileIndex.value = i
        fileItem.status = 'downloading'

        try {
          const blob = await downloadSingleFile(
            fileItem.fileInfo.fileId,
            fileItem.fileInfo.fileSize,
            (progress) => {
              fileItem.progress = progress
            },
          )

          if (cancelled.value) break

          // 触发浏览器下载（保存到本地）
          await saveFileToLocal(
            blob,
            fileItem.fileInfo.fileName,
            folderName.value,
            fileItem.fileInfo.relativePath,
          )

          fileItem.status = 'completed'
          fileItem.progress = 100
          downloadedBytes.value += fileItem.fileInfo.fileSize
        } catch (err: any) {
          if (cancelled.value) break
          fileItem.status = 'error'
          fileItem.errorMessage = err.message || '下载失败'
          toastStore.showToast(`文件 "${fileItem.fileInfo.fileName}" 下载失败: ${err.message}`, 'error')
          // 继续下载后续文件
        }
      }

      stopSpeedMonitor()

      if (cancelled.value) {
        status.value = 'cancelled'
        toastStore.showToast('文件夹下载已取消', 'info')
      } else {
        status.value = 'completed'
        toastStore.showToast('文件夹下载完成！', 'success')
      }
    } catch (err: any) {
      stopSpeedMonitor()
      status.value = 'error'
      errorMessage.value = err.message || '文件夹下载失败'
      toastStore.showToast('文件夹下载失败: ' + errorMessage.value, 'error')
    }
  }

  /**
   * 下载单个文件内容
   */
  async function downloadSingleFile(
    fileId: string,
    fileSize: number,
    onProgress: (percent: number) => void,
  ): Promise<Blob> {
    // 创建下载令牌
    const grantRes = await createDownloadGrantApi(fileId)
    if (grantRes.code !== 200) {
      throw new Error(grantRes.message || '获取下载令牌失败')
    }
    const downloadGrant = grantRes.data.download_grant

    try {
      const blob = await getFileContentApi(
        fileId,
        downloadGrant,
        (progressEvent: ProgressEvent) => {
          if (progressEvent.total) {
            const percent = (progressEvent.loaded / progressEvent.total) * 100
            onProgress(percent)
            // 更新累计字节数
            const delta = progressEvent.loaded - (accumulatedBytes % fileSize)
            if (delta > 0) accumulatedBytes += delta
          }
        },
      )

      await releaseDownloadGrantApi(downloadGrant)
      return blob
    } catch (err: any) {
      await cancelDownloadGrantApi(downloadGrant)
      throw err
    }
  }

  /**
   * 将文件保存到本地，根据 relativePath 重建文件夹结构
   * 通过浏览器 download 属性触发下载，使用 File System Access API 或 fallback
   */
  async function saveFileToLocal(
    blob: Blob,
    fileName: string,
    rootFolderName: string,
    relativePath: string,
  ): Promise<void> {
    // 使用 Blob URL 触发浏览器下载，通过相对路径构建子目录下载
    // 浏览器会自动处理文件保存
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    // 用于下载的文件名：包含相对路径结构
    // 如 "folder/subfolder/file.txt" 浏览器会提示保存位置
    a.download = rootFolderName + '/' + relativePath
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)

    // 等待一小段时间确保浏览器开始下载
    await new Promise(resolve => setTimeout(resolve, 100))
  }

  async function cancelFolderDownload(): Promise<void> {
    cancelled.value = true
  }

  function reset(): void {
    folderNodeId.value = null
    folderName.value = ''
    status.value = 'idle'
    files.value = []
    currentFileIndex.value = 0
    totalBytes.value = 0
    downloadedBytes.value = 0
    errorMessage.value = ''
    cancelled.value = false
    stopSpeedMonitor()
  }

  return {
    folderNodeId,
    folderName,
    status,
    files,
    currentFileIndex,
    totalBytes,
    downloadedBytes,
    errorMessage,
    folderProgress,
    isActive,
    startFolderDownload,
    cancelFolderDownload,
    reset,
    getSpeed,
  }
})