import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { CHUNK_SIZE, MAX_CONCURRENT_UPLOADS, MAX_RETRIES } from '@/utils/constants'
import { calculateSHA256 } from '@/utils/helpers'
import { useToastStore } from './toastStore'
import { useFileBrowserStore } from './fileBrowserStore'
import { createUploadsSessionApi, uploadFileChunkApi,  completeUploadSessionApi} from '@/api/index'

export const useUploaderStore = defineStore('uploader', () => {
  const toastStore = useToastStore()
  const fileBrowserStore = useFileBrowserStore()

  // 上传状态
  const uploadFile = ref(null)
  const uploadSessionId = ref(null)
  const totalChunks = ref(0)
  const chunksStatus = ref([]) // { index, status, retries, error }
  const activeControllers = ref([])
  const uploadPaused = ref(false)
  const uploadCancelled = ref(false)
  const uploadProgress = ref(0)
  const uploadSpeed = ref('0 KB/s')
  const isUploading = ref(false)
  const uploadFileName = ref('')
  const fileChecksum = ref('')
  let startTime = 0
  let lastLoadedBytes = 0
  let speedTimer = null

  // 并发控制
  const concurrentUploads = MAX_CONCURRENT_UPLOADS

  // 更新进度和速度
  function updateProgress() {
    const completed = chunksStatus.value.filter(c => c.status === 'success').length
    uploadProgress.value = totalChunks.value ? (completed / totalChunks.value) * 100 : 0
  }

  function updateSpeed() {
    if (!isUploading.value || uploadPaused.value || uploadCancelled.value) return
    const completed = chunksStatus.value.filter(c => c.status === 'success').length
    const loadedBytes = completed * CHUNK_SIZE
    const elapsed = (Date.now() - startTime) / 1000
    if (elapsed > 0 && loadedBytes > 0) {
      const speedBps = loadedBytes / elapsed
      uploadSpeed.value = speedBps > 1048576
        ? `${(speedBps / 1048576).toFixed(1)} MB/s`
        : `${(speedBps / 1024).toFixed(1)} KB/s`
    }
  }

  // 启动速度监控定时器
  function startSpeedMonitor() {
    if (speedTimer) clearInterval(speedTimer)
    speedTimer = setInterval(() => {
      updateSpeed()
    }, 1000)
  }

  function stopSpeedMonitor() {
    if (speedTimer) {
      clearInterval(speedTimer)
      speedTimer = null
    }
  }

  // 上传单个分片（带重试）
  async function uploadSingleChunk(chunkIdx) {
    const chunk = chunksStatus.value.find(c => c.index === chunkIdx)
    if (!chunk || chunk.status !== 'pending') return

    chunk.status = 'uploading'
    updateProgress()

    const start = (chunkIdx - 1) * CHUNK_SIZE
    const end = Math.min(start + CHUNK_SIZE, uploadFile.value.size)
    const blob = uploadFile.value.slice(start, end)
    const formData = new FormData()
    formData.append('file', blob, uploadFile.value.name)

    const controller = new AbortController()
    activeControllers.value.push(controller)

    try {
      const res = await uploadFileChunkApi(uploadSessionId.value, chunkIdx, blob, controller.signal);

      if (res.code === 200) {
        chunk.status = 'success'
        chunk.retries = 0
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
      updateProgress()
      if (!uploadPaused.value && !uploadCancelled.value) {
        scheduleChunks()
      }
    }
  }

  // 调度分片上传（并发控制）
  function scheduleChunks() {
    if (uploadCancelled.value || uploadPaused.value) return
    const pending = chunksStatus.value.filter(c => c.status === 'pending')
    const uploading = chunksStatus.value.filter(c => c.status === 'uploading').length
    const slots = concurrentUploads - uploading
    if (slots <= 0) return
    const toStart = pending.slice(0, slots)
    toStart.forEach(chunk => uploadSingleChunk(chunk.index))
  }

  // 开始上传
  async function startUpload(file) {
    if (!file) return
    // 重置状态
    resetUpload()
    uploadFile.value = file
    uploadFileName.value = file.name
    totalChunks.value = Math.ceil(file.size / CHUNK_SIZE)
    chunksStatus.value = Array.from({ length: totalChunks.value }, (_, i) => ({
      index: i + 1,
      status: 'pending',
      retries: 0,
    }))
    uploadProgress.value = 0
    uploadPaused.value = false
    uploadCancelled.value = false
    isUploading.value = true
    startTime = Date.now()
    startSpeedMonitor()

    // 计算校验和
    try {
      fileChecksum.value = await calculateSHA256(file)
    } catch (err) {
      toastStore.showToast('计算文件校验和失败，无法上传', 'error')
      cancelUpload(true)
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
      );
      if (res.code === 200) {
        uploadSessionId.value = res.data
        scheduleChunks()
      } else {
        throw new Error(res.message || '创建上传会话失败')
      }
    } catch (err) {
      toastStore.showToast(err.message, 'error')
      cancelUpload(true)
    }
  }

  // 暂停上传
  function pauseUpload() {
    if (!isUploading.value || uploadPaused.value) return
    uploadPaused.value = true
    // 中止所有进行中的请求
    activeControllers.value.forEach(ctrl => ctrl.abort())
    activeControllers.value = []
    // 将 uploading 状态重置为 pending
    chunksStatus.value.forEach(c => {
      if (c.status === 'uploading') c.status = 'pending'
    })
    updateProgress()
    stopSpeedMonitor()
    uploadSpeed.value = '已暂停'
  }

  // 恢复上传
  function resumeUpload() {
    if (!isUploading.value || !uploadPaused.value) return
    uploadPaused.value = false
    startTime = Date.now() - (uploadProgress.value / 100) * (Date.now() - startTime) // 粗略校准
    startSpeedMonitor()
    scheduleChunks()
  }

  // 取消上传
  function cancelUpload(silent = false) {
    if (!silent && !confirm('确定要取消上传吗？')) return
    uploadCancelled.value = true
    activeControllers.value.forEach(ctrl => ctrl.abort())
    activeControllers.value = []
    isUploading.value = false
    stopSpeedMonitor()
    if (!silent) {
      toastStore.showToast('上传已取消', 'warning')
    }
    resetUpload()
  }

  // 完成上传（合并文件）
  async function completeUpload() {
    if (!uploadSessionId.value) return
    try {
      const res = await completeUploadSessionApi(uploadSessionId.value);
      if (res.code === 200) {
        toastStore.showToast('上传成功！', 'success')
        fileBrowserStore.refresh()
        resetUpload()
      } else {
        toastStore.showToast(res.message || '文件合并失败', 'error')
      }
    } catch (err) {
      toastStore.showToast('文件合并请求失败', 'error')
    } finally {
      isUploading.value = false
      stopSpeedMonitor()
    }
  }

  // 重置所有上传相关状态
  function resetUpload() {
    uploadFile.value = null
    uploadSessionId.value = null
    totalChunks.value = 0
    chunksStatus.value = []
    activeControllers.value = []
    uploadPaused.value = false
    uploadCancelled.value = false
    uploadProgress.value = 0
    uploadSpeed.value = '0 KB/s'
    uploadFileName.value = ''
    fileChecksum.value = ''
    startTime = 0
    stopSpeedMonitor()
  }

  // 检查是否全部完成，若是则触发合并
  function checkCompletion() {
    const allSuccess = chunksStatus.value.length > 0 && chunksStatus.value.every(c => c.status === 'success')
    if (allSuccess && !uploadCancelled.value) {
      completeUpload()
    }
  }

  // 监听进度变化，当所有分片成功时自动合并
  // 通过 watch 在 store 外部实现，此处提供一个注册方法
  function registerProgressWatcher() {
    // 在组件中可以使用 watch 监听 chunksStatus 的变化
  }

  return {
    uploadFile,
    uploadProgress,
    uploadSpeed,
    isUploading,
    uploadFileName,
    uploadPaused,
    startUpload,
    pauseUpload,
    resumeUpload,
    cancelUpload,
    completeUpload,
    resetUpload,
    checkCompletion, // 供外部调用
    chunksStatus, // 暴露给外部用于监听
  }
})