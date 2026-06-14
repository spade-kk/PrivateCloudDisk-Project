import { defineStore } from 'pinia'
import { ref } from 'vue'
import { CHUNK_SIZE, MAX_CONCURRENT_UPLOADS, MAX_RETRIES } from '@/utils/constants'
import { calculateSHA256 } from '@/utils/helpers'
import { useToastStore } from './toastStore'
import { useFileBrowserStore } from './fileBrowserStore'
import { useTransferStore } from './transferStore'
import { createUploadsSessionApi, uploadFileChunkApi, completeUploadSessionApi, getTaskStatusApi } from '@/api/index'

// 任务处理步骤 → 中文描述映射
const STEP_LABELS = {
  merge: '文件合并中',
  hash_calculate: '哈希校验中',
  virus_scan: '病毒扫描中',
  thumbnail: '生成缩略图中',
  video_transcode: '视频转码中',
  mark_active: '即将完成',
}

// 轮询间隔（毫秒）
const POLL_INTERVAL = 2000
// 最大轮询次数（防止无限轮询，约 5 分钟）
const MAX_POLL_COUNT = 150

export const useUploaderStore = defineStore('uploader', () => {
  const toastStore = useToastStore()
  const fileBrowserStore = useFileBrowserStore()
  const transferStore = useTransferStore()

  // 上传状态
  const uploadFile = ref(null)
  const uploadSessionId = ref(null)
  const totalChunks = ref(0)
  const chunksStatus = ref([]) // { index, status, retries, error }
  const activeControllers = ref([])
  const uploadPaused = ref(false)
  const uploadCancelled = ref(false)

  // 传输记录 ID（关联 transferStore）
  const transferRecordId = ref(null)

  // 异步任务处理状态
  const isProcessing = ref(false)
  const processingStatus = ref('')
  const taskId = ref(null)

  // 轮询
  let pollTimer = null
  let pollCount = 0

  const fileChecksum = ref('')
  let startTime = 0
  let speedTimer = null

  const concurrentUploads = MAX_CONCURRENT_UPLOADS

  // 计算已上传字节数 & 速度
  let lastCompletedBytes = 0
  let lastSpeedTime = 0

  function getCompletedBytes() {
    return chunksStatus.value.reduce((sum, c) => {
      if (c.status !== 'success') return sum
      return sum + Math.max(0, (c.end || 0) - (c.start || 0))
    }, 0)
  }

  function calcSpeed() {
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

  function updateTransferProgress() {
    if (transferRecordId.value == null) return
    const completed = chunksStatus.value.filter(c => c.status === 'success').length
    const progress = totalChunks.value ? (completed / totalChunks.value) * 100 : 0
    transferStore.updateProgress(transferRecordId.value, progress, calcSpeed())
  }

  function startSpeedMonitor() {
    lastSpeedTime = 0
    lastCompletedBytes = 0
    if (speedTimer) clearInterval(speedTimer)
    speedTimer = setInterval(() => {
      if (!isProcessing.value) updateTransferProgress()
    }, 1000)
  }

  function stopSpeedMonitor() {
    if (speedTimer) {
      clearInterval(speedTimer)
      speedTimer = null
    }
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    pollCount = 0
  }

  // ============================================================
  // 轮询查询任务状态
  //
  // TODO: 后续可替换为 WebSocket 推送通知
  // WebSocket 方案：
  //   1. 建立 WebSocket 连接后，发送 { type: "subscribe_task", task_id }
  //   2. 服务端在任务状态变更时推送 { type: "task_update", task_id, status, current_step }
  //   3. 前端收到 status === "completed" 时调用 handleTaskCompleted()
  //   4. 前端收到 status === "failed" 时调用 handleTaskFailed()
  //   这样可完全移除 pollTaskStatus() 和 pollTimer 相关逻辑
  // ============================================================
  async function pollTaskStatus() {
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

      // 更新传输记录中的处理状态
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

  function handleTaskCompleted() {
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

  function handleTaskFailed(taskData) {
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

  function handleTaskCancelled() {
    stopPolling()
    isProcessing.value = false
    processingStatus.value = ''
    if (transferRecordId.value != null) {
      transferStore.cancelRecord(transferRecordId.value)
    }
    toastStore.showToast('文件处理已取消', 'warning')
    resetUpload()
  }

  function startPolling() {
    stopPolling()
    pollCount = 0
    pollTaskStatus()
    pollTimer = setInterval(pollTaskStatus, POLL_INTERVAL)
  }

  // 上传单个分片（带重试）
  async function uploadSingleChunk(chunkIdx) {
    const chunk = chunksStatus.value.find(c => c.index === chunkIdx)
    if (!chunk || chunk.status !== 'pending') return

    chunk.status = 'uploading'
    updateTransferProgress()

    const start = (chunkIdx - 1) * CHUNK_SIZE
    const end = Math.min(start + CHUNK_SIZE, uploadFile.value.size)
    const blob = uploadFile.value.slice(start, end)
    chunk.start = start
    chunk.end = end

    const controller = new AbortController()
    activeControllers.value.push(controller)

    try {
      const res = await uploadFileChunkApi(uploadSessionId.value, chunkIdx, blob, controller.signal)

      if (res.code === 200) {
        chunk.status = 'success'
        chunk.retries = 0
        updateTransferProgress()
        checkCompletion()
      } else {
        throw new Error(res.message || '上传失败')
      }
    } catch (err) {
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

  function scheduleChunks() {
    if (uploadCancelled.value || uploadPaused.value) return
    const pending = chunksStatus.value.filter(c => c.status === 'pending')
    const uploading = chunksStatus.value.filter(c => c.status === 'uploading').length
    const slots = concurrentUploads - uploading
    if (slots <= 0) return
    pending.slice(0, slots).forEach(chunk => uploadSingleChunk(chunk.index))
  }

  async function startUpload(file) {
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
    startTime = Date.now()

    // 在 transferStore 中创建上传记录
    transferRecordId.value = transferStore.addRecord('upload', file.name, file.size)
    startSpeedMonitor()

    // 计算校验和
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

    // 创建上传会话
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
    } catch (err) {
      toastStore.showToast(err.message, 'error')
      if (transferRecordId.value != null) {
        transferStore.failRecord(transferRecordId.value, err.message)
      }
      resetUpload()
    }
  }

  function pauseUpload() {
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

  function resumeUpload() {
    if (!uploadPaused.value) return
    uploadPaused.value = false
    startSpeedMonitor()
    scheduleChunks()
  }

  function cancelUpload(silent = false) {
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

  // ============================================================
  // 完成上传（合并文件）
  // 合并接口返回的只是任务提交确认（task_id），不代表文件已合并完成。
  // 此处将记录转为 processing 状态并启动轮询。
  // ============================================================
  async function completeUpload() {
    if (!uploadSessionId.value || uploadCancelled.value) return
    isProcessing.value = true
    processingStatus.value = '提交合并请求'
    stopSpeedMonitor()

    // 传输记录进入后台处理状态
    if (transferRecordId.value != null) {
      transferStore.updateProgress(transferRecordId.value, 100, '')
      // 先更新为处理中，等拿到 taskId 后再调用 enterProcessing
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

  function resetUpload() {
    stopPolling()
    uploadFile.value = null
    uploadSessionId.value = null
    totalChunks.value = 0
    chunksStatus.value = []
    activeControllers.value = []
    uploadPaused.value = false
    uploadCancelled.value = false
    isProcessing.value = false
    processingStatus.value = ''
    taskId.value = null
    transferRecordId.value = null
    fileChecksum.value = ''
    startTime = 0
    stopSpeedMonitor()
  }

  function checkCompletion() {
    const allSuccess = chunksStatus.value.length > 0 && chunksStatus.value.every(c => c.status === 'success')
    if (allSuccess && !uploadCancelled.value && !isProcessing.value) {
      updateTransferProgress()
      completeUpload()
    }
  }

  return {
    uploadFile,
    uploadPaused,
    isProcessing,
    processingStatus,
    taskId,
    transferRecordId,
    // 方法
    startUpload,
    pauseUpload,
    resumeUpload,
    cancelUpload,
    completeUpload,
    resetUpload,
    checkCompletion,
    chunksStatus,
  }
})