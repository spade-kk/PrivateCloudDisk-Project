import { defineStore } from 'pinia'
import { ref } from 'vue'
import { CHUNK_SIZE, MAX_CONCURRENT_UPLOADS, MAX_RETRIES } from '@/utils/constants'
import { calculateSHA256 } from '@/utils/helpers'
import { useToastStore } from './toastStore'
import { useFileBrowserStore } from './fileBrowserStore'
import { useTransferStore } from './transferStore'
import { createUploadsSessionApi, uploadFileChunkApi, completeUploadSessionApi, getTaskStatusApi } from '@/api/index'

const STEP_LABELS: Record<string, string> = {
  merge: '文件合并中',
  hash_calculate: '哈希校验中',
  virus_scan: '病毒扫描中',
  thumbnail: '生成缩略图中',
  video_transcode: '视频转码中',
  mark_active: '即将完成',
}

const POLL_INTERVAL = 2000
const MAX_POLL_COUNT = 150

export interface ChunkStatus {
  index: number
  status: string
  retries: number
  start: number
  end: number
}

export const useUploaderStore = defineStore('uploader', () => {
  const toastStore = useToastStore()
  const fileBrowserStore = useFileBrowserStore()
  const transferStore = useTransferStore()

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
  const taskId = ref<string | null>(null)

  let pollTimer: ReturnType<typeof setInterval> | null = null
  let pollCount = 0

  const fileChecksum = ref('')
  let speedTimer: ReturnType<typeof setInterval> | null = null

  const concurrentUploads = MAX_CONCURRENT_UPLOADS

  let lastCompletedBytes = 0
  let lastSpeedTime = 0

  function getCompletedBytes(): number {
    return chunksStatus.value.reduce((sum, c) => {
      if (c.status !== 'success') return sum
      return sum + Math.max(0, (c.end || 0) - (c.start || 0))
    }, 0)
  }

  function calcSpeed(): string {
    const now = Date.now()
    const completed = getCompletedBytes()
    if (!lastSpeedTime) { lastSpeedTime = now; lastCompletedBytes = completed; return '0 KB/s' }
    const elapsed = (now - lastSpeedTime) / 1000
    if (elapsed <= 0) return '0 KB/s'
    const speedBps = (completed - lastCompletedBytes) / elapsed
    lastSpeedTime = now
    lastCompletedBytes = completed
    if (speedBps > 1048576) return `${(speedBps / 1048576).toFixed(1)} MB/s`
    return `${(speedBps / 1024).toFixed(1)} KB/s`
  }

  function updateTransferProgress(): void {
    if (transferRecordId.value == null) return
    const completed = chunksStatus.value.filter(c => c.status === 'success').length
    const progress = totalChunks.value ? (completed / totalChunks.value) * 100 : 0
    transferStore.updateProgress(transferRecordId.value, progress, calcSpeed())
  }

  function startSpeedMonitor(): void {
    lastSpeedTime = 0
    lastCompletedBytes = 0
    if (speedTimer) clearInterval(speedTimer)
    speedTimer = setInterval(() => {
      if (!isProcessing.value) updateTransferProgress()
    }, 1000)
  }

  function stopSpeedMonitor(): void {
    if (speedTimer) {
      clearInterval(speedTimer)
      speedTimer = null
    }
  }

  function stopPolling(): void {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    pollCount = 0
  }

  async function pollTaskStatus(): Promise<void> {
    if (!taskId.value || !isProcessing.value) return

    pollCount++
    if (pollCount > MAX_POLL_COUNT) {
      stopPolling()
      isProcessing.value = false
      processingStatus.value = ''
      if (transferRecordId.value != null) {
        transferStore.failRecord(transferRecordId.value, '处理超时')
      }
      toastStore.showToast('文件处理超时，请稍后刷新页面查看', 'warning')
      return
    }

    try {
      const res = await getTaskStatusApi(taskId.value)
      if (res.code !== 200 || !res.data) return

      const { status, current_step } = res.data

      const label = STEP_LABELS[current_step] || '服务器处理中'
      processingStatus.value = label
      if (transferRecordId.value != null) {
        transferStore.updateProcessingStatus(transferRecordId.value, label)
      }

      if (status === 'completed') {
        handleTaskCompleted()
      } else if (status === 'failed') {
        handleTaskFailed(res.data)
      } else if (status === 'cancelled') {
        handleTaskCancelled()
      }
    } catch {
      pollCount = Math.max(0, pollCount - 1)
    }
  }

  function handleTaskCompleted(): void {
    stopPolling()
    isProcessing.value = false
    processingStatus.value = ''
    if (transferRecordId.value != null) {
      transferStore.finishRecord(transferRecordId.value)
    }
    toastStore.showToast('上传成功！', 'success')
    fileBrowserStore.refresh()
    resetUpload()
  }

  function handleTaskFailed(taskData: any): void {
    stopPolling()
    isProcessing.value = false
    processingStatus.value = ''
    const failedStep = (taskData && taskData.current_step) ? STEP_LABELS[taskData.current_step] || taskData.current_step : '处理'
    if (transferRecordId.value != null) {
      transferStore.failRecord(transferRecordId.value, `${failedStep}失败`)
    }
    toastStore.showToast(`文件${failedStep}失败，请重试`, 'error')
    resetUpload()
  }

  function handleTaskCancelled(): void {
    stopPolling()
    isProcessing.value = false
    processingStatus.value = ''
    if (transferRecordId.value != null) {
      transferStore.cancelRecord(transferRecordId.value)
    }
    toastStore.showToast('文件处理已取消', 'warning')
    resetUpload()
  }

  function startPolling(): void {
    stopPolling()
    pollCount = 0
    pollTaskStatus()
    pollTimer = setInterval(pollTaskStatus, POLL_INTERVAL)
  }

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

    try {
      const res = await uploadFileChunkApi(uploadSessionId.value!, chunkIdx, blob, controller.signal)

      if (res.code === 200) {
        chunk.status = 'success'
        chunk.retries = 0
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
    stopSpeedMonitor()

    if (transferRecordId.value != null) {
      transferStore.updateProgress(transferRecordId.value, 100, '')
    }

    try {
      const res = await completeUploadSessionApi(uploadSessionId.value)
      if (res.code === 200 && res.data?.task_id) {
        taskId.value = res.data.task_id
        processingStatus.value = '文件合并中'
        if (transferRecordId.value != null) {
          transferStore.enterProcessing(transferRecordId.value, res.data.task_id, '文件合并中')
        }
        startPolling()
      } else {
        isProcessing.value = false
        processingStatus.value = ''
        if (transferRecordId.value != null) {
          transferStore.failRecord(transferRecordId.value, res.message || '文件合并请求失败')
        }
        toastStore.showToast(res.message || '文件合并请求失败', 'error')
        resetUpload()
      }
    } catch {
      isProcessing.value = false
      processingStatus.value = ''
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
    taskId.value = null
    fileChecksum.value = ''
    lastCompletedBytes = 0
    lastSpeedTime = 0
  }

  return {
    uploadFile,
    uploadSessionId,
    totalChunks,
    chunksStatus,
    uploadPaused,
    uploadCancelled,
    isProcessing,
    processingStatus,
    taskId,
    startUpload,
    pauseUpload,
    resumeUpload,
    cancelUpload,
    resetUpload,
  }
})