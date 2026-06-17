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
  taskId: string | null
  startTime: number
  endTime: number | null
}

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

  function addRecord(type: 'upload' | 'download', fileName: string, fileSize: number): number {
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
      taskId: null,
      startTime: Date.now(),
      endTime: null,
    }
    records.value.unshift(record)
    if (records.value.length > MAX_RECORDS) {
      records.value = records.value.slice(0, MAX_RECORDS)
    }
    persist()
    return id
  }

  function updateProgress(id: number, progress: number, speed?: string): void {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.progress = Math.min(100, Math.max(0, progress))
    if (speed !== undefined) r.speed = speed
    schedulePersist()
  }

  function enterProcessing(id: number, taskId: string, processingStatus: string): void {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.status = TransferStatus.PROCESSING
    r.progress = 100
    r.taskId = taskId
    r.processingStatus = processingStatus || '服务器处理中'
    persist()
  }

  function updateProcessingStatus(id: number, processingStatus: string): void {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.processingStatus = processingStatus
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
    updateProgress,
    enterProcessing,
    updateProcessingStatus,
    finishRecord,
    failRecord,
    cancelRecord,
    clearCompleted,
    clearAll,
  }
})