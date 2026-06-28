// ============================================================
// throughputStore.ts — 企业级吞吐量数据采集与持久化
// ============================================================
// 从 uploaderStore / downloaderStore 的 SpeedSampler 中采集实时速率，
// 以紧凑格式持久化到 localStorage，支持跨页面刷新恢复。
//
// 存储策略：
//   - 高精度区：最近 30 分钟，每 2 秒一个采样点
//   - 低精度区：30 分钟 ~ 12 小时，每 30 秒聚合一个采样点
//   - 总量上限 ~2000 点，约 30KB，远低于 localStorage 5MB 限制
//   - 自动清理超过 12 小时的过期数据
//
// 数据格式（紧凑 JSON 数组）：
//   [[ts, upBps, downBps], ...]
//   ts: Unix 毫秒时间戳
//   upBps: 上传速率（字节/秒），0 表示无上传
//   downBps: 下载速率（字节/秒），0 表示无下载
// ============================================================

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// ---- 常量 ----

const STORAGE_KEY = 'cloud_drive_throughput'
const SAMPLING_INTERVAL = 2000 // 采集间隔 2s
const HIGH_RES_WINDOW = 30 * 60 * 1000 // 高精度窗口 30 分钟
const LOW_RES_INTERVAL = 30000 // 低精度聚合间隔 30s
const MAX_POINTS = 2000
const MAX_AGE = 12 * 60 * 60 * 1000 // 12 小时

// ---- 类型 ----

export interface ThroughputPoint {
  /** Unix 毫秒时间戳 */
  t: number
  /** 上传速率（字节/秒） */
  u: number
  /** 下载速率（字节/秒） */
  d: number
}

/** 紧凑存储格式：[timestamp, uploadBps, downloadBps] */
type CompactPoint = [number, number, number]

// ---- 工具函数 ----

function now(): number {
  return Date.now()
}

function loadFromStorage(): ThroughputPoint[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const compact: CompactPoint[] = JSON.parse(raw)
    if (!Array.isArray(compact)) return []
    const cutoff = now() - MAX_AGE
    return compact
      .filter((p) => Array.isArray(p) && p.length === 3 && p[0] > cutoff)
      .map(([t, u, d]) => ({ t, u, d }))
  } catch {
    return []
  }
}

function saveToStorage(points: ThroughputPoint[]): void {
  try {
    const compact: CompactPoint[] = points.map((p) => [p.t, p.u, p.d])
    localStorage.setItem(STORAGE_KEY, JSON.stringify(compact))
  } catch {
    // storage 满或不可用
  }
}

/**
 * 聚合一批点：取平均值
 */
function aggregatePoints(points: ThroughputPoint[]): ThroughputPoint {
  if (points.length === 0) return { t: 0, u: 0, d: 0 }
  const t = points[Math.floor(points.length / 2)].t
  const u = Math.round(points.reduce((s, p) => s + p.u, 0) / points.length)
  const d = Math.round(points.reduce((s, p) => s + p.d, 0) / points.length)
  return { t, u, d }
}

// ---- Store ----

export const useThroughputStore = defineStore('throughput', () => {
  // ---- 状态 ----

  const points = ref<ThroughputPoint[]>(loadFromStorage())

  /** 当前上传速率（由外部更新） */
  const currentUploadBps = ref(0)
  /** 当前下载速率（由外部更新） */
  const currentDownloadBps = ref(0)

  // ---- 计算属性 ----

  /** 是否有活跃传输 */
  const isActive = computed(() => currentUploadBps.value > 0 || currentDownloadBps.value > 0)

  /** 时间范围 */
  const timeRange = computed(() => {
    if (points.value.length === 0) return { start: 0, end: 0 }
    return {
      start: points.value[0].t,
      end: points.value[points.value.length - 1].t,
    }
  })

  // ---- 采集 ----

  let collectTimer: ReturnType<typeof setInterval> | null = null

  /**
   * 启动采集（应用启动时调用一次）
   */
  function startCollecting(): void {
    if (collectTimer) return
    collectTimer = setInterval(() => {
      collectSample()
    }, SAMPLING_INTERVAL)
  }

  /**
   * 停止采集
   */
  function stopCollecting(): void {
    if (collectTimer) {
      clearInterval(collectTimer)
      collectTimer = null
    }
  }

  /**
   * 采集一个采样点
   */
  function collectSample(): void {
    const up = currentUploadBps.value
    const down = currentDownloadBps.value
    const ts = now()

    // 只记录有实际传输的采样点，但保留最后 60 个零值点作为"传输结束"标记
    const all = points.value
    const lastNonZero = [...all].reverse().find((p) => p.u > 0 || p.d > 0)

    if (up === 0 && down === 0) {
      // 如果最近 60 个点全是零，不再追加
      const recentZeros = all.slice(-60).filter((p) => p.u === 0 && p.d === 0).length
      if (recentZeros >= 60 && lastNonZero && ts - lastNonZero.t > 120000) {
        return // 超过 2 分钟无传输，停止记录零值
      }
    }

    points.value = [...points.value, { t: ts, u: up, d: down }]
    compactAndPersist()
  }

  /**
   * 压缩存储：高精度 + 低精度混合
   */
  function compactAndPersist(): void {
    let all = points.value
    const cutoff = now() - MAX_AGE

    // 1. 清除过期数据
    all = all.filter((p) => p.t > cutoff)

    if (all.length <= MAX_POINTS) {
      points.value = all
      saveToStorage(all)
      return
    }

    // 2. 分离高精度和低精度
    const highResCutoff = now() - HIGH_RES_WINDOW
    const highRes = all.filter((p) => p.t >= highResCutoff)
    const lowResRaw = all.filter((p) => p.t < highResCutoff)

    // 3. 低精度聚合：每 LOW_RES_INTERVAL 聚合为 1 个点
    const aggregated: ThroughputPoint[] = []
    let bucket: ThroughputPoint[] = []
    let bucketStart = lowResRaw.length > 0 ? lowResRaw[0].t : 0

    for (const p of lowResRaw) {
      if (p.t - bucketStart >= LOW_RES_INTERVAL && bucket.length > 0) {
        aggregated.push(aggregatePoints(bucket))
        bucket = []
        bucketStart = p.t
      }
      bucket.push(p)
    }
    if (bucket.length > 0) {
      aggregated.push(aggregatePoints(bucket))
    }

    // 4. 合并
    const merged = [...aggregated, ...highRes]

    // 5. 如果仍然超限，从低精度区头部删减
    if (merged.length > MAX_POINTS) {
      const excess = merged.length - MAX_POINTS
      const final = merged.slice(excess)
      points.value = final
      saveToStorage(final)
    } else {
      points.value = merged
      saveToStorage(merged)
    }
  }

  // ---- 外部更新接口 ----

  /**
   * 更新当前上传速率（由 uploaderStore 的速度监控循环调用）
   */
  function setUploadSpeed(bps: number): void {
    currentUploadBps.value = bps
  }

  /**
   * 更新当前下载速率（由 downloaderStore 的速度监控循环调用）
   */
  function setDownloadSpeed(bps: number): void {
    currentDownloadBps.value = bps
  }

  /**
   * 重置当前速率（传输全部完成时调用）
   */
  function resetCurrent(): void {
    currentUploadBps.value = 0
    currentDownloadBps.value = 0
  }

  /**
   * 清空全部历史数据
   */
  function clearHistory(): void {
    points.value = []
    saveToStorage([])
  }

  // ---- 初始化 ----

  // 启动采集
  startCollecting()

  return {
    points,
    currentUploadBps,
    currentDownloadBps,
    isActive,
    timeRange,
    setUploadSpeed,
    setDownloadSpeed,
    resetCurrent,
    clearHistory,
    startCollecting,
    stopCollecting,
  }
})