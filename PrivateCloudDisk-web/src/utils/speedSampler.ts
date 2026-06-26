// ============================================================
// speedSampler.ts — 企业级传输速率采样器
// ============================================================
// 基于滑动窗口 + 指数移动平均 (EMA) 算法，提供精准的实时吞吐量计算。
//
// 核心设计：
//   - 滑动窗口 (sliding window)：保留最近 N 秒的字节增量样本，
//     每秒最多采集 S 个样本点，避免高频事件导致计算抖动。
//   - 指数移动平均 (EMA)：对滑动窗口速率做平滑处理，减少瞬时波动，
//     使显示的速度曲线更稳定，近似真实吞吐量。
//   - 零速衰减：当窗口内无新数据时，速率向 0 平滑衰减，避免速度
//     "卡住"在旧值不更新。
//
// 使用场景：
//   - 分块上传：每个分块的 onUploadProgress 事件推送已传输字节数
//   - 分块下载：每个分块的 onDownloadProgress 事件推送已接收字节数
//   - 不适用于整文件下载（无需显示速度）
// ============================================================

export interface SpeedSample {
  /** 采样时间戳 (ms) */
  time: number
  /** 累计已传输字节数 */
  accumulatedBytes: number
}

/** 速率格式化结果 */
export interface FormattedSpeed {
  /** 字节/秒 */
  bps: number
  /** 格式化字符串，如 "1.5 MB/s" */
  formatted: string
}

export class SpeedSampler {
  /** 滑动窗口大小 (ms)，默认 5 秒 */
  private readonly windowMs: number
  /** 最小采样间隔 (ms)，默认 200ms，避免高频事件 */
  private readonly minSampleInterval: number
  /** 最大保留样本数 */
  private readonly maxSamples: number
  /** EMA 平滑系数 (0-1)，越接近 1 越平滑，越接近 0 越灵敏 */
  private readonly emaAlpha: number

  /** 样本列表 */
  private samples: SpeedSample[] = []

  /** 上一次采样时间戳 */
  private lastSampleTime = 0

  /** 当前 EMA 平滑速率 (bytes/s) */
  private emaSpeed = 0

  /** 是否已初始化 EMA */
  private emaInitialized = false

  /**
   * @param windowMs - 滑动窗口大小 (ms)，默认 5000
   * @param minSampleInterval - 最小采样间隔 (ms)，默认 200
   * @param emaAlpha - EMA 平滑系数，默认 0.3（偏灵敏，适合传输场景）
   */
  constructor(
    windowMs: number = 5000,
    minSampleInterval: number = 200,
    emaAlpha: number = 0.3,
  ) {
    this.windowMs = windowMs
    this.minSampleInterval = minSampleInterval
    this.maxSamples = Math.ceil(windowMs / minSampleInterval) + 10
    this.emaAlpha = emaAlpha
  }

  /**
   * 添加累计字节数采样点
   *
   * 内部会做节流：若距离上次采样不足 minSampleInterval 则忽略。
   * 累计字节数应单调递增（新值 >= 旧值），重置传输时调用 reset()。
   *
   * @param accumulatedBytes - 当前累计已传输字节数
   */
  addSample(accumulatedBytes: number): void {
    const now = Date.now()
    if (now - this.lastSampleTime < this.minSampleInterval) return
    this.lastSampleTime = now

    this.samples.push({ time: now, accumulatedBytes })
    this.pruneOldSamples(now)
    this.limitSamples()
  }

  /**
   * 获取当前滑动窗口内原始速率 (bytes/s)
   *
   * 计算方式：窗口内总字节增量 / 窗口时间跨度
   * 若窗口内样本不足 2 个，返回 0。
   */
  getRawSpeed(): number {
    this.pruneOldSamples(Date.now())
    if (this.samples.length < 2) return 0

    const oldest = this.samples[0]
    const newest = this.samples[this.samples.length - 1]
    const elapsed = (newest.time - oldest.time) / 1000
    const deltaBytes = newest.accumulatedBytes - oldest.accumulatedBytes

    if (elapsed <= 0 || deltaBytes <= 0) return 0
    return deltaBytes / elapsed
  }

  /**
   * 获取 EMA 平滑后的速率 (bytes/s)
   *
   * 使用指数移动平均平滑原始速率，避免瞬时抖动。
   * 当窗口内无新数据时，平滑衰减至 0。
   */
  getSpeed(): number {
    const rawSpeed = this.getRawSpeed()

    if (!this.emaInitialized) {
      this.emaSpeed = rawSpeed
      this.emaInitialized = true
    } else {
      // EMA: newEma = alpha * raw + (1 - alpha) * oldEma
      this.emaSpeed = this.emaAlpha * rawSpeed + (1 - this.emaAlpha) * this.emaSpeed
    }

    return this.emaSpeed
  }

  /**
   * 获取格式化后的速率字符串
   *
   * @returns { bps: 字节/秒, formatted: "1.5 MB/s" }
   */
  getFormattedSpeed(): FormattedSpeed {
    const bps = this.getSpeed()
    return {
      bps: Math.round(bps),
      formatted: SpeedSampler.formatSpeed(bps),
    }
  }

  /**
   * 重置采样器状态
   *
   * 在新的传输任务开始时调用，清除旧数据。
   */
  reset(): void {
    this.samples = []
    this.lastSampleTime = 0
    this.emaSpeed = 0
    this.emaInitialized = false
  }

  /**
   * 静态方法：将字节/秒格式化为人类可读字符串
   *
   * @param bps - 字节/秒
   * @returns "1.5 MB/s" | "856.3 KB/s" | "128 B/s" | "0 B/s"
   */
  static formatSpeed(bps: number): string {
    if (bps <= 0) return '0 B/s'
    if (bps >= 1073741824) return `${(bps / 1073741824).toFixed(2)} GB/s`
    if (bps >= 1048576) return `${(bps / 1048576).toFixed(1)} MB/s`
    if (bps >= 1024) return `${(bps / 1024).toFixed(1)} KB/s`
    return `${Math.round(bps)} B/s`
  }

  /**
   * 清除超出滑动窗口的过期样本
   */
  private pruneOldSamples(now: number): void {
    const cutoff = now - this.windowMs
    while (this.samples.length > 0 && this.samples[0].time < cutoff) {
      this.samples.shift()
    }
  }

  /**
   * 限制样本数量，防止内存无限增长
   */
  private limitSamples(): void {
    if (this.samples.length > this.maxSamples) {
      this.samples = this.samples.slice(-this.maxSamples)
    }
  }
}