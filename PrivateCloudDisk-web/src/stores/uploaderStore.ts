// ============================================================
// uploaderStore.ts — 文件上传业务逻辑
// ============================================================
// 完整上传流程：
//   createUploadsSession → uploadChunks(concurrent) → completeUpload → pollBackendTaskStatus
//
// 后台处理流水线（仅后台处理，增强事件独立并发）：
//   merge → hash_calculate → virus_scan → mark_active
//
// 轮询策略：
//   每 2s 轮询一次，最多 150 次（5 分钟），status === "completed" 后刷新文件列表。
//   基于 stages 数组计算实时进度（completed 阶段数 / 总阶段数 × 100%）。
//
// 架构优化：
//   后续可将轮询替换为 WebSocket 推送通知，
//   服务端在任务状态变更时通过 WebSocket 推送 backend_task_id + status，
//   前端收到通知后更新对应任务状态，completed 时刷新文件列表。
// ============================================================

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { CHUNK_SIZE, MAX_CONCURRENT_UPLOADS, MAX_RETRIES } from '@/utils/constants'
import { calculateSHA256 } from '@/utils/helpers'
import { SpeedSampler } from '@/utils/speedSampler'
import { useToastStore } from './toastStore'
import { useFileBrowserStore } from './fileBrowserStore'
import { useTransferStore } from './transferStore'
import { useThroughputStore } from './throughputStore'
import {
  createUploadsSessionApi,
  uploadFileChunkApi,
  completeUploadSessionApi,
  getBackendTaskStatusApi,
} from '@/api/index'

// ============================================================
// 后台处理阶段标签
// 仅包含后台处理阶段（merge → hash_calculate → virus_scan → mark_active）
// 增强事件（缩略图、转码、HLS、索引）独立并发执行，不在此轮询
// ============================================================
const STAGE_LABELS: Record<string, string> = {
  merge: '文件合并中',
  hash_calculate: '哈希校验中',
  virus_scan: '病毒扫描中',
  mark_active: '即将完成',
}

/** 后台处理阶段顺序（用于进度计算） */
const BACKEND_STAGES = ['merge', 'hash_calculate', 'virus_scan', 'mark_active'] as const

const POLL_INTERVAL = 2000
const MAX_POLL_COUNT = 150

// ============================================================
// 类型定义
// ============================================================

export interface ChunkStatus {
  index: number
  status: string
  retries: number
  start: number
  end: number
}

/** 后台任务阶段状态 */
export interface TaskStage {
  stage: string
  status: 'processing' | 'completed' | 'failed' | 'pending'
  summary: string
}

/** 后台任务状态响应 */
export interface BackendTaskStatus {
  backend_task_id: string
  file_id: string
  file_name: string
  status: 'processing' | 'completed' | 'failed'
  current_stage: string
  created_at: string
  updated_at: string
  stages: TaskStage[]
}

// ============================================================
// Store
// ============================================================

export const useUploaderStore = defineStore('uploader', () => {
  const toastStore = useToastStore()
  const fileBrowserStore = useFileBrowserStore()
  const transferStore = useTransferStore()
  const throughputStore = useThroughputStore()

  const uploadFile = ref<File | null>(null)
  const uploadSessionId = ref<string | null>(null)
  const totalChunks = ref(0)
  const chunksStatus = ref<ChunkStatus[]>([])
  const activeControllers = ref<AbortController[]>([])
  const uploadPaused = ref(false)
  const uploadCancelled = ref(false)

  const transferRecordId = ref<number | null>(null)

  const isProcessing = ref(false)
  const processingStatus = ref('')
  const backendTaskId = ref<string | null>(null)

  /** 后台处理进度（0-100） */
  const processingProgress = ref(0)

  let pollTimer: ReturnType<typeof setInterval> | null = null
  let pollCount = 0

  const fileChecksum = ref('')

  const concurrentUploads = MAX_CONCURRENT_UPLOADS

  // ============================================================
  // 企业级速率采样器
  // 基于滑动窗口 + EMA 平滑算法，从 onUploadProgress 事件
  // 获取实时传输字节数，而非等待分块完成后才统计。
  // ============================================================
  const speedSampler = new SpeedSampler(5000, 200, 0.3)
  let speedTimer: ReturnType<typeof setInterval> | null = null

  /** 实时累计已传输字节数（跨并发分块汇总） */
  let accumulatedUploadedBytes = 0

  /** 每个分块当前已传输字节数（用于增量计算） */
  const chunkUploadedBytes = new Map<number, number>()

  /**
   * 获取实时上传速率（EMA 平滑）
   */
  function getUploadSpeed(): string {
    return speedSampler.getFormattedSpeed().formatted
  }

  function getCompletedBytes(): number {
    return chunksStatus.value.reduce((sum, c) => {
      if (c.status !== 'success') return sum
      return sum + Math.max(0, (c.end || 0) - (c.start || 0))
    }, 0)
  }

  function updateTransferProgress(): void {
    if (transferRecordId.value == null) return
    const completed = chunksStatus.value.filter(c => c.status === 'success').length
    const progress = totalChunks.value ? (completed / totalChunks.value) * 100 : 0
    transferStore.updateProgress(transferRecordId.value, progress, getUploadSpeed())
  }

  /**
   * 启动速率采样定时器
   * 每 200ms 将当前累计字节数推入 SpeedSampler，
   * 同时更新传输列表中的速度显示。
   */
  function startSpeedMonitor(): void {
    accumulatedUploadedBytes = 0
    chunkUploadedBytes.clear()
    speedSampler.reset()
    if (speedTimer) clearInterval(speedTimer)
    speedTimer = setInterval(() => {
      speedSampler.addSample(accumulatedUploadedBytes)
      throughputStore.setUploadSpeed(speedSampler.getSpeed())
      if (!isProcessing.value) updateTransferProgress()
    }, 200)
  }

  function stopSpeedMonitor(): void {
    if (speedTimer) {
      clearInterval(speedTimer)
      speedTimer = null
    }
    throughputStore.setUploadSpeed(0)
  }

  // ============================================================
  // 后台任务轮询
  // ============================================================

  /**
   * 计算后台处理进度
   * 基于 stages 数组中 completed 阶段数 / 总阶段数
   * 当前进行中的阶段按 50% 估算
   */
  function calcProcessingProgress(stages: TaskStage[]): number {
    if (!stages || stages.length === 0) return 0
    const total = stages.length
    let completed = 0

    for (const s of stages) {
      if (s.status === 'completed') {
        completed += 1
      } else if (s.status === 'processing') {
        completed += 0.5
      }
    }
    return Math.round((completed / total) * 100)
  }

  function stopPolling(): void {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    pollCount = 0
  }

  async function pollBackendTaskStatus(): Promise<void> {
    if (!backendTaskId.value || !isProcessing.value) return

    pollCount++
    if (pollCount > MAX_POLL_COUNT) {
      stopPolling()
      isProcessing.value = false
      processingStatus.value = ''
      processingProgress.value = 0
      if (transferRecordId.value != null) {
        transferStore.failRecord(transferRecordId.value, '处理超时')
      }
      toastStore.showToast('文件处理超时，请稍后刷新页面查看', 'warning')
      return
    }

    try {
      const res = await getBackendTaskStatusApi(backendTaskId.value)
      if (res.code !== 200 || !res.data) return

      const data: BackendTaskStatus = res.data
      const { status, current_stage, stages } = data

      // 更新当前阶段标签
      const label = STAGE_LABELS[current_stage] || '服务器处理中'
      processingStatus.value = label

      // 更新处理进度
      processingProgress.value = calcProcessingProgress(stages)

      // 更新传输记录
      if (transferRecordId.value != null) {
        transferStore.updateProcessingStatus(transferRecordId.value, label)
        transferStore.updateProcessingProgress(transferRecordId.value, processingProgress.value)
      }

      if (status === 'completed') {
        handleTaskCompleted()
      } else if (status === 'failed') {
        handleTaskFailed(data)
      }
    } catch {
      // 网络错误不增加 pollCount，允许重试
      pollCount = Math.max(0, pollCount - 1)
    }
  }

  function handleTaskCompleted(): void {
    stopPolling()
    isProcessing.value = false
    processingStatus.value = ''
    processingProgress.value = 100
    if (transferRecordId.value != null) {
      transferStore.finishRecord(transferRecordId.value)
    }
    toastStore.showToast('上传成功！', 'success')
    fileBrowserStore.refresh()
    resetUpload()
  }

  function handleTaskFailed(taskData: BackendTaskStatus): void {
    stopPolling()
    isProcessing.value = false
    processingStatus.value = ''
    processingProgress.value = 0

    // 找到失败阶段
    const failedStage = taskData.stages?.find(s => s.status === 'failed')
    const failedLabel = failedStage
      ? STAGE_LABELS[failedStage.stage] || failedStage.stage
      : '处理'

    if (transferRecordId.value != null) {
      transferStore.failRecord(transferRecordId.value, `${failedLabel}失败`)
    }
    toastStore.showToast(`文件${failedLabel}失败，请重试`, 'error')
    resetUpload()
  }

  function startPolling(): void {
    stopPolling()
    pollCount = 0
    pollBackendTaskStatus()
    pollTimer = setInterval(pollBackendTaskStatus, POLL_INTERVAL)
  }

  // ============================================================
  // 分片上传
  // ============================================================

  async function uploadSingleChunk(chunkIdx: number): Promise<void> {
    const chunk = chunksStatus.value.find(c => c.index === chunkIdx)
    if (!chunk || chunk.status !== 'pending') return

    chunk.status = 'uploading'
    updateTransferProgress()

    const start = (chunkIdx - 1) * CHUNK_SIZE
    const end = Math.min(start + CHUNK_SIZE, uploadFile.value!.size)
    const blob = uploadFile.value!.slice(start, end)
    chunk.start = start
    chunk.end = end

    const controller = new AbortController()
    activeControllers.value.push(controller)

    // 初始化该分块的已传输字节数
    chunkUploadedBytes.set(chunkIdx, 0)

    try {
      const res = await uploadFileChunkApi(
        uploadSessionId.value!,
        chunkIdx,
        blob,
        controller.signal,
        // onProgress: 实时追踪该分块已传输字节数
        (loaded: number) => {
          const prev = chunkUploadedBytes.get(chunkIdx) || 0
          const delta = loaded - prev
          if (delta > 0) {
            chunkUploadedBytes.set(chunkIdx, loaded)
            accumulatedUploadedBytes += delta
          }
        },
      )

      if (res.code === 200) {
        chunk.status = 'success'
        chunk.retries = 0
        // 确保该分块字节数精确计入
        const chunkSize = end - start
        const prev = chunkUploadedBytes.get(chunkIdx) || 0
        if (prev < chunkSize) {
          accumulatedUploadedBytes += (chunkSize - prev)
          chunkUploadedBytes.set(chunkIdx, chunkSize)
        }
        updateTransferProgress()
        checkCompletion()
      } else {
        throw new Error(res.message || '上传失败')
      }
    } catch (err: any) {
      if (err.name === 'AbortError') {
        chunk.status = 'pending'
      } else {
        chunk.status = 'failed'
        chunk.retries = (chunk.retries || 0) + 1
        if (chunk.retries < MAX_RETRIES) {
          chunk.status = 'pending'
        } else {
          toastStore.showToast(`分片 ${chunkIdx} 上传失败，已重试 ${MAX_RETRIES} 次`, 'error')
        }
      }
      // 失败时回退该分块的已传输字节
      chunkUploadedBytes.delete(chunkIdx)
    } finally {
      activeControllers.value = activeControllers.value.filter(c => c !== controller)
      updateTransferProgress()
      if (!uploadPaused.value && !uploadCancelled.value) {
        scheduleChunks()
      }
    }
  }

  function scheduleChunks(): void {
    if (uploadCancelled.value || uploadPaused.value) return
    const pending = chunksStatus.value.filter(c => c.status === 'pending')
    const uploading = chunksStatus.value.filter(c => c.status === 'uploading').length
    const slots = concurrentUploads - uploading
    if (slots <= 0) return
    pending.slice(0, slots).forEach(chunk => uploadSingleChunk(chunk.index))
  }

  // ============================================================
  // 上传生命周期
  // ============================================================

  async function startUpload(file: File): Promise<void> {
    if (!file) return
    resetUpload()
    uploadFile.value = file
    totalChunks.value = Math.max(1, Math.ceil(file.size / CHUNK_SIZE))
    chunksStatus.value = Array.from({ length: totalChunks.value }, (_, i) => ({
      index: i + 1,
      status: 'pending',
      retries: 0,
      start: 0,
      end: 0,
    }))
    uploadPaused.value = false
    uploadCancelled.value = false

    transferRecordId.value = transferStore.addRecord('upload', file.name, file.size)
    startSpeedMonitor()

    try {
      fileChecksum.value = await calculateSHA256(file)
    } catch {
      toastStore.showToast('计算文件校验和失败，无法上传', 'error')
      if (transferRecordId.value != null) {
        transferStore.failRecord(transferRecordId.value, '校验和计算失败')
      }
      resetUpload()
      return
    }

    try {
      const res = await createUploadsSessionApi(
        totalChunks.value,
        file.size,
        fileChecksum.value,
        CHUNK_SIZE,
        file.type || 'application/octet-stream',
        file.name,
        fileBrowserStore.currentNodeId
      )
      if (res.code === 200) {
        uploadSessionId.value = res.data?.uploads_id || res.data?.upload_id || res.data?.id || res.data
        scheduleChunks()
      } else {
        throw new Error(res.message || '创建上传会话失败')
      }
    } catch (err: any) {
      toastStore.showToast(err.message, 'error')
      if (transferRecordId.value != null) {
        transferStore.failRecord(transferRecordId.value, err.message)
      }
      resetUpload()
    }
  }

  function pauseUpload(): void {
    if (uploadCancelled.value || uploadPaused.value) return
    uploadPaused.value = true
    activeControllers.value.forEach(ctrl => ctrl.abort())
    activeControllers.value = []
    chunksStatus.value.forEach(c => {
      if (c.status === 'uploading') c.status = 'pending'
    })
    updateTransferProgress()
    stopSpeedMonitor()
  }

  function resumeUpload(): void {
    if (!uploadPaused.value) return
    uploadPaused.value = false
    startSpeedMonitor()
    scheduleChunks()
  }

  function cancelUpload(silent: boolean = false): void {
    if (!silent && !confirm('确定要取消上传吗？')) return
    stopPolling()
    uploadCancelled.value = true
    isProcessing.value = false
    processingStatus.value = ''
    processingProgress.value = 0
    activeControllers.value.forEach(ctrl => ctrl.abort())
    activeControllers.value = []
    stopSpeedMonitor()
    if (transferRecordId.value != null) {
      transferStore.cancelRecord(transferRecordId.value)
    }
    if (!silent) {
      toastStore.showToast('上传已取消', 'warning')
    }
    resetUpload()
  }

  async function completeUpload(): Promise<void> {
    if (!uploadSessionId.value || uploadCancelled.value) return
    isProcessing.value = true
    processingStatus.value = '提交合并请求'
    processingProgress.value = 0
    stopSpeedMonitor()

    if (transferRecordId.value != null) {
      transferStore.updateProgress(transferRecordId.value, 100, '')
    }

    try {
      const res = await completeUploadSessionApi(uploadSessionId.value)
      if (res.code === 200 && res.data?.backend_task_id) {
        backendTaskId.value = res.data.backend_task_id
        processingStatus.value = '文件合并中'
        processingProgress.value = 0
        if (transferRecordId.value != null) {
          transferStore.enterProcessing(
            transferRecordId.value,
            res.data.backend_task_id,
            '文件合并中',
          )
        }
        startPolling()
      } else {
        isProcessing.value = false
        processingStatus.value = ''
        processingProgress.value = 0
        if (transferRecordId.value != null) {
          transferStore.failRecord(transferRecordId.value, res.message || '文件合并请求失败')
        }
        toastStore.showToast(res.message || '文件合并请求失败', 'error')
        resetUpload()
      }
    } catch {
      isProcessing.value = false
      processingStatus.value = ''
      processingProgress.value = 0
      stopPolling()
      if (transferRecordId.value != null) {
        transferStore.failRecord(transferRecordId.value, '文件合并请求失败')
      }
      toastStore.showToast('文件合并请求失败', 'error')
      resetUpload()
    }
  }

  function checkCompletion(): void {
    const allDone = chunksStatus.value.every(c => c.status === 'success')
    if (allDone && totalChunks.value > 0) {
      completeUpload()
    }
  }

  function resetUpload(): void {
    stopPolling()
    stopSpeedMonitor()
    uploadFile.value = null
    uploadSessionId.value = null
    totalChunks.value = 0
    chunksStatus.value = []
    activeControllers.value = []
    uploadPaused.value = false
    uploadCancelled.value = false
    transferRecordId.value = null
    isProcessing.value = false
    processingStatus.value = ''
    processingProgress.value = 0
    backendTaskId.value = null
    fileChecksum.value = ''
    accumulatedUploadedBytes = 0
    chunkUploadedBytes.clear()
    speedSampler.reset()
  }

  return {
    // 上传状态
    uploadFile,
    uploadSessionId,
    totalChunks,
    chunksStatus,
    uploadPaused,
    uploadCancelled,
    // 后台处理状态
    isProcessing,
    processingStatus,
    processingProgress,
    backendTaskId,
    // 方法
    startUpload,
    pauseUpload,
    resumeUpload,
    cancelUpload,
    resetUpload,
  }
})