// ============================================================
// folderUploaderStore.ts — 文件夹上传业务逻辑（混合懒上传模型）
// ============================================================
// 设计要点：
//   - 上传即创建路径：用户拖拽文件夹，每个文件通过懒上传接口自动创建不存在的目录
//   - 不再预建目录树，完全复用现有单文件上传流程（分片上传 → 合并 → 轮询）
//   - 每个文件调用 createLazyUploadSessionApi(parent_node_id + relative_path)
//   - 服务端自动逐级 ensure folder，路径缓存 + 幂等
//   - 取消只停止后续文件上传，已上传文件保留
// ============================================================

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { CHUNK_SIZE, MAX_CONCURRENT_FILES, MAX_CONCURRENT_UPLOADS, MAX_RETRIES } from '@/utils/constants'
import { calculateSHA256 } from '@/utils/helpers'
import { SpeedSampler } from '@/utils/speedSampler'
import { useToastStore } from './toastStore'
import { useFileBrowserStore } from './fileBrowserStore'
import { useTransferStore, TransferStatus } from './transferStore'
import {
  createLazyUploadSessionApi,
  uploadFileChunkApi,
  completeUploadSessionApi,
  getBackendTaskStatusApi,
  isOptimisticLockConflict,
} from '@/api/index'

// ============================================================
// 类型定义
// ============================================================

export interface FolderUploadFileItem {
  relativePath: string
  file: File
  fileSize: number
  status: 'pending' | 'uploading' | 'completed' | 'error'
  progress: number
  errorMessage?: string
  /** 传输列表中的记录 ID */
  transferRecordId?: number
}

// ============================================================
// Store
// ============================================================

export const useFolderUploaderStore = defineStore('folderUploader', () => {
  const toastStore = useToastStore()
  const fileBrowserStore = useFileBrowserStore()
  const transferStore = useTransferStore()

  const folderName = ref('')
  const status = ref<'idle' | 'scanning' | 'uploading' | 'completed' | 'cancelled' | 'error'>('idle')
  const files = ref<FolderUploadFileItem[]>([])
  const currentFileIndex = ref(0)
  const totalBytes = ref(0)
  const uploadedBytes = ref(0)
  const errorMessage = ref('')
  const cancelled = ref(false)

  const speedSampler = new SpeedSampler(5000, 200, 0.3)
  let speedTimer: ReturnType<typeof setInterval> | null = null
  let accumulatedBytes = 0
  let folderRunId = 0
  const activeControllers = new Set<AbortController>()

  const completedCount = computed(() =>
    files.value.filter(f => f.status === 'completed').length
  )

  const errorCount = computed(() =>
    files.value.filter(f => f.status === 'error').length
  )

  const folderProgress = computed(() => {
    if (files.value.length === 0) return 0
    return Math.round((completedCount.value / files.value.length) * 100)
  })

  const isActive = computed(() =>
    status.value === 'scanning' || status.value === 'uploading'
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
  // 文件夹上传流程（懒上传模型）
  // ============================================================

  /**
   * 从 <input webkitdirectory> 文件列表解析相对路径
   */
  function parseFileList(fileList: File[]): {
    rootName: string
    fileItems: FolderUploadFileItem[]
  } {
    const firstPath = (fileList[0] as any).webkitRelativePath || ''
    const rootName = firstPath.split('/')[0] || '未命名文件夹'

    const fileItems = fileList.map(file => {
      const fullPath = (file as any).webkitRelativePath || ''
      // 去掉根目录名，保留相对路径（如 "subfolder1/subfolder2/file.txt"）
      const parts = fullPath.split('/')
      parts.shift() // 移除根目录名
      const relativePath = parts.join('/')

      return {
        relativePath,
        file,
        fileSize: file.size,
        status: 'pending' as const,
        progress: 0,
      }
    })

    return { rootName, fileItems }
  }

  /**
   * 启动文件夹上传
   * @param targetNodeId 当前所在目录的 node_id（文件将上传到此目录下）
   * @param fileList 从 <input webkitdirectory> 获取的文件列表
   */
  async function startFolderUpload(
    targetNodeId: string,
    fileList: File[],
  ): Promise<void> {
    if (fileList.length === 0) return
    reset()

    try {
      // 1. 解析文件列表
      status.value = 'scanning'
      const { rootName, fileItems } = parseFileList(fileList)
      folderName.value = rootName
      files.value = fileItems
      totalBytes.value = fileItems.reduce((sum, f) => sum + f.fileSize, 0)

      // 2. 为每个文件在传输列表中注册记录
      for (const fileItem of files.value) {
        const displayName = fileItem.relativePath || fileItem.file.name
        fileItem.transferRecordId = transferStore.addRecord(
          'upload',
          displayName,
          fileItem.fileSize,
          rootName, // folderName 用于分组
        )
        const recordId = fileItem.transferRecordId!
        transferStore.registerRetryHandler(recordId, async () => {
          fileItem.status = 'uploading'
          fileItem.progress = 0
          fileItem.errorMessage = ''
          try {
            await uploadSingleFileInFolder(fileItem, targetNodeId)
            fileItem.status = 'completed'
            fileItem.progress = 100
          } catch (error: any) {
            fileItem.status = 'error'
            fileItem.errorMessage = error?.message || '重试失败'
            transferStore.failRecord(recordId, fileItem.errorMessage)
            throw error
          }
        })
      }

      // 3. 文件级并发池：每个文件内部再使用独立的分块并发池；文件合并接口成功即释放文件槽位。
      status.value = 'uploading'
      startSpeedMonitor()
      cancelled.value = false

      const runId = ++folderRunId
      let nextFileIndex = 0
      const worker = async (): Promise<void> => {
        while (!cancelled.value && runId === folderRunId) {
          const index = nextFileIndex++
          if (index >= files.value.length) return
          const fileItem = files.value[index]
          currentFileIndex.value = index
          fileItem.status = 'uploading'
          try {
            await uploadSingleFileInFolder(fileItem, targetNodeId, runId)
            fileItem.status = 'completed'
            fileItem.progress = 100
            uploadedBytes.value += fileItem.fileSize
          } catch (err: any) {
            if (cancelled.value || runId !== folderRunId) return
            fileItem.status = 'error'
            fileItem.errorMessage = err.message || '上传失败'
            toastStore.showToast(`"${fileItem.relativePath || fileItem.file.name}" 上传失败: ${err.message}`, 'error')
          }
        }
      }
      await Promise.all(
        Array.from({ length: Math.min(MAX_CONCURRENT_FILES, files.value.length) }, () => worker()),
      )

      stopSpeedMonitor()

      if (cancelled.value) {
        status.value = 'cancelled'
        toastStore.showToast('文件夹上传已取消', 'info')
      } else {
        const hasErrors = files.value.some(f => f.status === 'error')
        status.value = 'completed'
        if (hasErrors) {
          toastStore.showToast('文件夹上传完成（部分文件失败）', 'warning')
        } else {
          toastStore.showToast('文件夹上传完成！', 'success')
        }
        fileBrowserStore.refresh()
      }
    } catch (err: any) {
      stopSpeedMonitor()
      status.value = 'error'
      errorMessage.value = err.message || '文件夹上传失败'
      toastStore.showToast('文件夹上传失败: ' + errorMessage.value, 'error')
    }
  }

  /**
   * 上传单个文件（文件夹上传中的文件）
   * 使用懒上传会话创建 API，自动创建路径中不存在的目录
   */
  async function uploadSingleFileInFolder(
    fileItem: FolderUploadFileItem,
    targetNodeId: string,
    runId = folderRunId,
  ): Promise<void> {
    const { file, relativePath, transferRecordId } = fileItem

    // 计算 SHA-256
    const checksum = await calculateSHA256(file)

    // 计算分片参数
    const totalChunks = Math.max(1, Math.ceil(file.size / CHUNK_SIZE))

    // 提取相对路径的目录部分（如 "subfolder1/subfolder2/file.txt" → "subfolder1/subfolder2"）
    const pathParts = relativePath.split('/')
    const fileName = pathParts.pop() || file.name
    const dirPath = pathParts.join('/') || ''

    // 创建懒上传会话（自动创建路径中不存在的目录）
    let sessionId: string
    let actualNodeId: string
    try {
      let sessionRes: any
      for (let attempt = 0; attempt < 3; attempt += 1) {
        try {
          sessionRes = await createLazyUploadSessionApi(
            totalChunks,
            file.size,
            checksum,
            CHUNK_SIZE,
            file.type || 'application/octet-stream',
            fileName,
            targetNodeId,
            dirPath || undefined, // relative_path
            undefined, // breadcrumb_path
          )
          if (sessionRes?.code === 'OPTIMISTIC_LOCK_CONFLICT'
            || sessionRes?.data?.code === 'OPTIMISTIC_LOCK_CONFLICT') {
            const conflict = new Error('服务器繁忙，请稍后重试') as any
            conflict.status = 409
            throw conflict
          }
          break
        } catch (error: any) {
          if (!isOptimisticLockConflict(error) || attempt >= 2) throw error
          // REQ-UPLOAD-OPTIMISTIC-RETRY-2026-07：懒上传会话与普通会话使用同一冲突协议。
          await new Promise(resolve => setTimeout(resolve, 100 + Math.floor(Math.random() * 401)))
        }
      }
      if (sessionRes.code !== 200) {
        throw new Error(sessionRes.message || '创建上传会话失败')
      }
      sessionId = sessionRes.data?.uploads_id
      actualNodeId = sessionRes.data?.node_id
      if (!sessionId) {
        throw new Error('创建上传会话失败：未返回 uploads_id')
      }
    } catch (err: any) {
      if (transferRecordId != null) {
        transferStore.failRecord(transferRecordId, err.message)
      }
      throw new Error('创建上传会话失败: ' + err.message)
    }

    // REQ-UPLOAD-CONCURRENCY-2026-07：单文件分块使用固定并发池；一个分块完成后立即补充下一个。
    let nextChunk = 1
    let completedChunks = 0
    const chunkLoaded = new Map<number, number>()
    const uploadChunk = async (): Promise<void> => {
      while (!cancelled.value && runId === folderRunId) {
        const chunkIdx = nextChunk++
        if (chunkIdx > totalChunks) return
        const start = (chunkIdx - 1) * CHUNK_SIZE
        const end = Math.min(start + CHUNK_SIZE, file.size)
        const blob = file.slice(start, end)
        let retries = 0
        let success = false
        while (retries < MAX_RETRIES && !success) {
          if (cancelled.value || runId !== folderRunId) throw new Error('上传已取消')
          const controller = new AbortController()
          activeControllers.add(controller)
          chunkLoaded.set(chunkIdx, 0)
          try {
            const res = await uploadFileChunkApi(
              sessionId,
              chunkIdx,
              blob,
              controller.signal,
              (loaded: number) => {
                const previous = chunkLoaded.get(chunkIdx) || 0
                const delta = Math.max(0, loaded - previous)
                if (delta > 0) {
                  chunkLoaded.set(chunkIdx, loaded)
                  accumulatedBytes += delta
                }
              },
            )
            if (res.code !== 200) throw new Error(res.message || '分片上传失败')
            success = true
          } catch (error: any) {
            if (error?.name === 'AbortError' || cancelled.value) throw new Error('上传已取消')
            retries += 1
            if (retries >= MAX_RETRIES) {
              if (transferRecordId != null) transferStore.failRecord(transferRecordId, `分片 ${chunkIdx} 上传失败`)
              throw new Error(`分片 ${chunkIdx} 上传失败，已重试 ${MAX_RETRIES} 次`)
            }
          } finally {
            activeControllers.delete(controller)
            chunkLoaded.delete(chunkIdx)
          }
        }
        completedChunks += 1
        fileItem.progress = Math.round((completedChunks / totalChunks) * 100)
        if (transferRecordId != null) {
          transferStore.updateProgress(transferRecordId, fileItem.progress, getSpeed())
        }
      }
    }
    await Promise.all(
      Array.from({ length: Math.min(MAX_CONCURRENT_UPLOADS, totalChunks) }, () => uploadChunk()),
    )

    // 完成上传（合并）
    try {
      const completeRes = await completeUploadSessionApi(sessionId)
      if (completeRes.code !== 200) {
        if (transferRecordId != null) {
          transferStore.failRecord(transferRecordId, completeRes.message || '合并请求失败')
        }
        throw new Error(completeRes.message || '合并请求失败')
      }

      // 合并接口成功即完成本文件的上传会话；后处理轮询独立运行，不占用文件并发槽位。
      if (completeRes.data?.backend_task_id) {
        if (transferRecordId != null) {
          transferStore.enterProcessing(transferRecordId, completeRes.data.backend_task_id, '文件合并中')
        }
        void pollBackendTask(completeRes.data.backend_task_id, transferRecordId, fileItem)
      } else {
        throw new Error('合并响应缺少后台任务 ID')
      }
    } catch (err: any) {
      if (transferRecordId != null) {
        transferStore.failRecord(transferRecordId, err.message)
      }
      throw new Error('文件合并失败: ' + err.message)
    }
  }

  async function pollBackendTask(taskId: string, transferRecordId?: number, fileItem?: FolderUploadFileItem): Promise<void> {
    const MAX_POLL = 150
    return new Promise((resolve, reject) => {
      let count = 0
      const timer = setInterval(async () => {
        count++
        if (count > MAX_POLL) {
          clearInterval(timer)
          if (transferRecordId != null) {
            transferStore.failRecord(transferRecordId, '处理超时')
          }
          reject(new Error('处理超时'))
          return
        }
        try {
          const res = await getBackendTaskStatusApi(taskId)
          if (res.code !== 200) return
          const data = res.data
          if (transferRecordId != null) {
            transferStore.updateProcessingStatus(transferRecordId, data.status === 'completed' ? '处理完成' : '处理中')
          }
          if (data.status === 'completed') {
            clearInterval(timer)
            if (transferRecordId != null) transferStore.finishRecord(transferRecordId)
            resolve()
          } else if (data.status === 'failed') {
            clearInterval(timer)
            if (transferRecordId != null) {
              transferStore.failRecord(transferRecordId, '处理失败')
            }
            if (fileItem) {
              fileItem.status = 'error'
              fileItem.errorMessage = '文件后处理失败'
            }
            reject(new Error('处理失败'))
          }
        } catch {
          // 网络错误继续轮询
        }
      }, 2000)
    })
  }

  function cancelFolderUpload(): void {
    cancelled.value = true
    folderRunId += 1
    activeControllers.forEach(controller => controller.abort())
    activeControllers.clear()
  }

  function reset(): void {
    folderRunId += 1
    activeControllers.forEach(controller => controller.abort())
    activeControllers.clear()
    folderName.value = ''
    status.value = 'idle'
    files.value = []
    currentFileIndex.value = 0
    totalBytes.value = 0
    uploadedBytes.value = 0
    errorMessage.value = ''
    cancelled.value = false
    stopSpeedMonitor()
  }

  return {
    folderName,
    status,
    files,
    currentFileIndex,
    totalBytes,
    uploadedBytes,
    errorMessage,
    completedCount,
    errorCount,
    folderProgress,
    isActive,
    startFolderUpload,
    cancelFolderUpload,
    reset,
    getSpeed,
  }
})
