import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// 状态枚举
export const TransferStatus = {
  UPLOADING: 'uploading',
  DOWNLOADING: 'downloading',
  PROCESSING: 'processing',
  COMPLETED: 'completed',
  FAILED: 'failed',
  CANCELLED: 'cancelled',
}

// 最大历史记录数
const MAX_RECORDS = 200
const STORAGE_KEY = 'cloud_drive_transfers'
const NEXT_ID_KEY = 'cloud_drive_transfers_next_id'

/**
 * 从 localStorage 加载传输记录
 * @returns {Array}
 */
function loadRecords() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    // 只保留有效记录，过滤脏数据
    return parsed.filter(r => r && typeof r.id === 'number' && typeof r.type === 'string')
  } catch {
    return []
  }
}

/**
 * 从 localStorage 加载 nextId
 * @returns {number}
 */
function loadNextId() {
  try {
    const raw = localStorage.getItem(NEXT_ID_KEY)
    const val = parseInt(raw, 10)
    return Number.isFinite(val) ? val : 1
  } catch {
    return 1
  }
}

/**
 * 保存传输记录到 localStorage
 * @param {Array} records
 */
function saveRecords(records) {
  try {
    // 只持久化非进行中的记录 + 进行中但完成了分片的记录
    // 进行中的记录在刷新后状态会丢失，标记为"已取消"
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
    // storage 满了或不可用，静默失败
  }
}

/**
 * 保存 nextId 到 localStorage
 * @param {number} id
 */
function saveNextId(id) {
  try {
    localStorage.setItem(NEXT_ID_KEY, String(id))
  } catch {
    // 静默失败
  }
}

let nextId = loadNextId()

/**
 * 传输记录 Store
 * 数据持久化到 localStorage，页面刷新后历史记录不丢失。
 * 进行中的传输（uploading/downloading/processing）在刷新后自动标记为"已取消"。
 *
 * 由 TransferPanel（头部小组件）和 TransfersView（传输记录页）共用。
 * uploaderStore 和 downloaderStore 向此 store 推送进度。
 */
export const useTransferStore = defineStore('transfer', () => {
  /** @type {import('vue').Ref<Array<TransferRecord>>} */
  const records = ref(loadRecords())

  /** 正在进行的传输数量 */
  const ongoingCount = computed(() =>
    records.value.filter(r =>
      r.status === TransferStatus.UPLOADING ||
      r.status === TransferStatus.DOWNLOADING ||
      r.status === TransferStatus.PROCESSING
    ).length
  )

  /** 是否正在进行传输 */
  const hasOngoing = computed(() => ongoingCount.value > 0)

  /** 最近完成的记录（用于面板显示） */
  const recentRecords = computed(() =>
    records.value.slice(0, 20)
  )

  /** 内部：持久化到 localStorage */
  function persist() {
    saveRecords(records.value)
    saveNextId(nextId)
  }

  /**
   * 添加一条传输记录
   * @param {'upload'|'download'} type
   * @param {string} fileName
   * @param {number} fileSize - 字节数
   * @returns {number} record id
   */
  function addRecord(type, fileName, fileSize) {
    const id = nextId++
    const record = {
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
    // 限制最大记录数
    if (records.value.length > MAX_RECORDS) {
      records.value = records.value.slice(0, MAX_RECORDS)
    }
    persist()
    return id
  }

  /**
   * 更新传输进度
   * @param {number} id
   * @param {number} progress - 0-100
   * @param {string} [speed]
   */
  function updateProgress(id, progress, speed) {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.progress = Math.min(100, Math.max(0, progress))
    if (speed !== undefined) r.speed = speed
    // 进度更新频繁，用节流持久化减少写入
    schedulePersist()
  }

  /**
   * 上传进入后台处理阶段（合并/扫毒等）
   * @param {number} id
   * @param {string} taskId
   * @param {string} processingStatus - 如 "文件合并中"
   */
  function enterProcessing(id, taskId, processingStatus) {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.status = TransferStatus.PROCESSING
    r.progress = 100
    r.taskId = taskId
    r.processingStatus = processingStatus || '服务器处理中'
    persist()
  }

  /**
   * 更新处理状态描述
   * @param {number} id
   * @param {string} processingStatus
   */
  function updateProcessingStatus(id, processingStatus) {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.processingStatus = processingStatus
  }

  /**
   * 标记传输完成
   * @param {number} id
   */
  function finishRecord(id) {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.status = TransferStatus.COMPLETED
    r.progress = 100
    r.processingStatus = ''
    r.endTime = Date.now()
    persist()
  }

  /**
   * 标记传输失败
   * @param {number} id
   * @param {string} [reason]
   */
  function failRecord(id, reason) {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.status = TransferStatus.FAILED
    r.processingStatus = reason || '传输失败'
    r.endTime = Date.now()
    persist()
  }

  /**
   * 标记传输取消
   * @param {number} id
   */
  function cancelRecord(id) {
    const r = records.value.find(r => r.id === id)
    if (!r) return
    r.status = TransferStatus.CANCELLED
    r.endTime = Date.now()
    persist()
  }

  /**
   * 清除已完成/已失败/已取消的记录
   */
  function clearCompleted() {
    records.value = records.value.filter(r =>
      r.status === TransferStatus.UPLOADING ||
      r.status === TransferStatus.DOWNLOADING ||
      r.status === TransferStatus.PROCESSING
    )
    persist()
  }

  /**
   * 清除全部记录
   */
  function clearAll() {
    records.value = []
    persist()
  }

  // ---- 进度持久化节流 ----
  let persistTimer = null
  function schedulePersist() {
    if (persistTimer) return
    persistTimer = setTimeout(() => {
      persist()
      persistTimer = null
    }, 2000) // 每 2 秒最多写一次 localStorage
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

/**
 * @typedef {Object} TransferRecord
 * @property {number} id
 * @property {'upload'|'download'} type
 * @property {string} fileName
 * @property {number} fileSize
 * @property {number} progress - 0-100
 * @property {string} speed
 * @property {string} status - TransferStatus
 * @property {string} processingStatus
 * @property {string|null} taskId
 * @property {number} startTime
 * @property {number|null} endTime
 */