import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const TransferStatus = {
  UPLOADING: 'uploading',
  DOWNLOADING: 'downloading',
  PROCESSING: 'processing',
  COMPLETED: 'completed',
  FAILED: 'failed',
  CANCELLED: 'cancelled',
} as const

export type TransferStatusType = typeof TransferStatus[keyof typeof TransferStatus]

export interface TransferRecord {
  id: number
  type: 'upload' | 'download'
  fileName: string
  fileSize: number
  progress: number
  speed: string
  status: TransferStatusType
  processingStatus: string
  backendTaskId: string | null
  processingProgress: number
  startTime: number
  endTime: number | null
  /** 文件夹上传时对应的文件夹名，用于分组展示 */
  folderName?: string
}

type RetryHandler = () => Promise<void> | void

const MAX_RECORDS = 200
const STORAGE_KEY = 'cloud_drive_transfers'
const NEXT_ID_KEY = 'cloud_drive_transfers_next_id'

function loadRecords(): TransferRecord[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter((r: any) => r && typeof r.id === 'number' && typeof r.type === 'string')
  } catch {
    return []
  }
}

function loadNextId(): number {
  try {
    const raw = localStorage.getItem(NEXT_ID_KEY)
    const val = parseInt(raw || '', 10)
    return Number.isFinite(val) ? val : 1
  } catch {
    return 1
  }
}

function saveRecords(records: TransferRecord[]): void {
  try {
    const toSave = records.map(r => {
      if (r.status === TransferStatus.UPLOADING ||
          r.status === TransferStatus.DOWNLOADING ||
          r.status === TransferStatus.PROCESSING) {
        return { ...r, status: TransferStatus.CANCELLED, progress: r.progress, endTime: Date.now() }
      }
      return r
    })
    localStorage.setItem(STORAGE_KEY, JSON.stringify(toSave))
  } catch {
    // storage 满了或不可用
  }
}

function saveNextId(id: number): void {
  try {
    localStorage.setItem(NEXT_ID_KEY, String(id))
  } catch {
    // 静默失败
  }
}

let nextId = loadNextId()
// File 对象不能序列化到 localStorage；重试句柄只保存在当前页面生命周期内。
const retryHandlers = new Map<number, RetryHandler>()

export const useTransferStore = defineStore('transfer', () => {
  const records = ref<TransferRecord[]>(loadRecords())

  const ongoingCount = computed(() =>
    records.value.filter(r =>
      r.status === TransferStatus.UPLOADING ||
      r.status === TransferStatus.DOWNLOADING ||
      r.status === TransferStatus.PROCESSING
    ).length
  )

  const hasOngoing = computed(() => ongoingCount.value > 0)

  const recentRecords = computed(() => records.value.slice(0, 20))

  function persist(): void {
    saveRecords(records.value)
    saveNextId(nextId)
  }

  function addRecord(type: 'upload' | 'download', fileName: string, fileSize: number, folderName?: string): number {
    const id = nextId++
    const record: TransferRecord = {
      id,
      type,
      fileName,
      fileSize,
      progress: 0,
      speed: '',
      status: type === 'upload' ? TransferStatus.UPLOADING : TransferStatus.DOWNLOADING,
      processingStatus: '',
      backendTaskId: null,
      processingProgress: 0,
      startTime: Date.now(),
      endTime: null,
      folderName,
    }
    records.value.unshift(record)
    if (records.value.length > MAX_RECORDS) {
      records.value = records.value.slice(0, MAX_RECORDS)
    }
    persist()
    return id
  }

  function registerRetryHandler(id: number, handler: RetryHandler): void {
    retryHandlers.set(id, handler)
  }

  function unregisterRetryHandler(id: number): void {
    retryHandlers.delete(id)
  }

  function resetRecordForRetry(id: number): void {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.status = r.type === 'upload' ? TransferStatus.UPLOADING : TransferStatus.DOWNLOADING
    r.progress = 0
    r.speed = ''
    r.processingStatus = ''
    r.backendTaskId = null
    r.processingProgress = 0
    r.startTime = Date.now()
    r.endTime = null
    persist()
  }

  /** 手动重试失败上传；实际上传逻辑由上传 Store 注册，断点续传暂不启用。 */
  async function retryRecord(id: number): Promise<boolean> {
    const record = records.value.find(r => r.id === id)
    const handler = retryHandlers.get(id)
    if (!record || record.type !== 'upload' || record.status !== TransferStatus.FAILED || !handler) {
      return false
    }
    resetRecordForRetry(id)
    try {
      await handler()
      return true
    } catch (error: any) {
      const current = records.value.find(r => r.id === id)
      if (current && current.status !== TransferStatus.FAILED) {
        failRecord(id, error?.message || '重试失败')
      }
      return false
    }
  }

  function updateProgress(id: number, progress: number, speed?: string): void {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.progress = Math.min(100, Math.max(0, progress))
    if (speed !== undefined) r.speed = speed
    schedulePersist()
  }

  function enterProcessing(id: number, backendTaskId: string, processingStatus: string): void {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.status = TransferStatus.PROCESSING
    r.progress = 100
    r.backendTaskId = backendTaskId
    r.processingStatus = processingStatus || '服务器处理中'
    r.processingProgress = 0
    persist()
  }

  function updateProcessingStatus(id: number, processingStatus: string): void {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.processingStatus = processingStatus
  }

  function updateProcessingProgress(id: number, progress: number): void {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.processingProgress = Math.min(100, Math.max(0, progress))
  }

  function finishRecord(id: number): void {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.status = TransferStatus.COMPLETED
    r.progress = 100
    r.processingStatus = ''
    r.endTime = Date.now()
    persist()
  }

  function failRecord(id: number, reason?: string): void {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.status = TransferStatus.FAILED
    r.processingStatus = reason || '传输失败'
    r.endTime = Date.now()
    persist()
  }

  function cancelRecord(id: number): void {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.status = TransferStatus.CANCELLED
    r.endTime = Date.now()
    persist()
  }

  function clearCompleted(): void {
    records.value = records.value.filter(r =>
      r.status === TransferStatus.UPLOADING ||
      r.status === TransferStatus.DOWNLOADING ||
      r.status === TransferStatus.PROCESSING
    )
    persist()
  }

  function clearAll(): void {
    records.value = []
    persist()
  }

  let persistTimer: ReturnType<typeof setTimeout> | null = null
  function schedulePersist(): void {
    if (persistTimer) return
    persistTimer = setTimeout(() => {
      persist()
      persistTimer = null
    }, 2000)
  }

  return {
    records,
    ongoingCount,
    hasOngoing,
    recentRecords,
    addRecord,
    registerRetryHandler,
    unregisterRetryHandler,
    retryRecord,
    resetRecordForRetry,
    updateProgress,
    enterProcessing,
    updateProcessingStatus,
    updateProcessingProgress,
    finishRecord,
    failRecord,
    cancelRecord,
    clearCompleted,
    clearAll,
  }
})
