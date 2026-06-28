<template>
  <div
    ref="containerRef"
    class="throughput-chart"
    :class="{ 'is-empty': segments.length === 0 }"
    @mousedown="onMouseDown"
    @mouseup="onMouseUp"
    @mouseleave="onMouseUp"
    @mousemove="onMouseMove"
    @wheel.prevent="onWheel"
  >
    <!-- 空状态 -->
    <div v-if="segments.length === 0" class="chart-empty">
      <i class="fa fa-area-chart"></i>
      <span>暂无吞吐量数据</span>
      <span class="chart-empty-hint">上传或下载文件后，速率曲线将显示在这里</span>
    </div>

    <canvas
      ref="canvasRef"
      class="chart-canvas"
      :style="{ cursor: dragging ? 'grabbing' : (hoveredPoint ? 'crosshair' : 'grab') }"
    ></canvas>

    <!-- 悬停提示 -->
    <div
      v-if="hoveredPoint && !dragging"
      class="chart-tooltip"
      :style="tooltipStyle"
    >
      <div class="tooltip-time">{{ formatTime(hoveredPoint.t) }}</div>
      <div class="tooltip-row upload" v-if="hoveredPoint.u > 0">
        <span class="tooltip-dot upload"></span>
        上传 {{ formatSpeed(hoveredPoint.u) }}
      </div>
      <div class="tooltip-row download" v-if="hoveredPoint.d > 0">
        <span class="tooltip-dot download"></span>
        下载 {{ formatSpeed(hoveredPoint.d) }}
      </div>
      <div class="tooltip-row" v-if="hoveredPoint.u === 0 && hoveredPoint.d === 0">
        <span class="tooltip-dot idle"></span>
        空闲
      </div>
    </div>

    <!-- 图例 -->
    <div class="chart-legend">
      <span class="legend-item">
        <span class="legend-dot upload"></span>上传
      </span>
      <span class="legend-item">
        <span class="legend-dot download"></span>下载
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useThroughputStore, type ThroughputPoint } from '@/stores/throughputStore'

const store = useThroughputStore()

// ---- DOM refs ----
const containerRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)

// ---- 交互状态 ----
const dragging = ref(false)
const dragStartX = ref(0)
const dragStartOffset = ref(0)
const scrollOffset = ref(0) // 画布水平偏移（px）
const followMode = ref(true) // 自动跟随最新数据
const hoveredPoint = ref<ThroughputPoint | null>(null)
const hoverX = ref(0)
const hoverY = ref(0)
const dpr = ref(1)

// ---- 常量 ----
const GAP_THRESHOLD = 5 * 60 * 1000 // 5 分钟无数据视为间隙
const PADDING = { top: 20, right: 40, bottom: 40, left: 60 }
const MIN_CHART_WIDTH = 600

// ---- 时间段 ----
interface TimeSegment {
  startIdx: number
  endIdx: number
  startTime: number
  endTime: number
  pointCount: number
}

const segments = computed<TimeSegment[]>(() => {
  const pts = store.points
  if (pts.length < 2) return []

  const segs: TimeSegment[] = []
  let segStart = 0

  for (let i = 1; i < pts.length; i++) {
    if (pts[i].t - pts[i - 1].t > GAP_THRESHOLD) {
      // 只保留有实际传输的段（至少有一个非零点）
      if (hasData(pts, segStart, i - 1)) {
        segs.push({
          startIdx: segStart,
          endIdx: i - 1,
          startTime: pts[segStart].t,
          endTime: pts[i - 1].t,
          pointCount: i - segStart,
        })
      }
      segStart = i
    }
  }

  // 最后一段
  if (hasData(pts, segStart, pts.length - 1)) {
    segs.push({
      startIdx: segStart,
      endIdx: pts.length - 1,
      startTime: pts[segStart].t,
      endTime: pts[pts.length - 1].t,
      pointCount: pts.length - segStart,
    })
  }

  return segs
})

function hasData(pts: ThroughputPoint[], start: number, end: number): boolean {
  for (let i = start; i <= end; i++) {
    if (pts[i].u > 0 || pts[i].d > 0) return true
  }
  return false
}

// ---- 工具函数 ----
function formatSpeed(bps: number): string {
  if (bps <= 0) return '0 B/s'
  if (bps >= 1073741824) return (bps / 1073741824).toFixed(2) + ' GB/s'
  if (bps >= 1048576) return (bps / 1048576).toFixed(1) + ' MB/s'
  if (bps >= 1024) return (bps / 1024).toFixed(0) + ' KB/s'
  return Math.round(bps) + ' B/s'
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  const h = d.getHours().toString().padStart(2, '0')
  const m = d.getMinutes().toString().padStart(2, '0')
  const s = d.getSeconds().toString().padStart(2, '0')
  return `${h}:${m}:${s}`
}

function formatTimeAxis(ts: number): string {
  const d = new Date(ts)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

const tooltipStyle = computed(() => {
  if (!containerRef.value) return {}
  const rect = containerRef.value.getBoundingClientRect()
  const left = Math.min(hoverX.value + 12, rect.width - 180)
  const top = Math.max(hoverY.value - 60, 8)
  return {
    left: left + 'px',
    top: top + 'px',
  }
})

// ---- 渲染 ----
let animFrameId = 0

function render(): void {
  const canvas = canvasRef.value
  const container = containerRef.value
  if (!canvas || !container) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  dpr.value = window.devicePixelRatio || 1

  const rect = container.getBoundingClientRect()
  const w = rect.width
  const h = rect.height
  canvas.width = w * dpr.value
  canvas.height = h * dpr.value
  canvas.style.width = w + 'px'
  canvas.style.height = h + 'px'
  ctx.scale(dpr.value, dpr.value)

  const pts = store.points
  const segs = segments.value

  if (pts.length < 2 || segs.length === 0) {
    ctx.clearRect(0, 0, w, h)
    return
  }

  // 计算总时间跨度
  const totalTimeSpan = segs[segs.length - 1].endTime - segs[0].startTime
  const totalPoints = segs.reduce((s, seg) => s + seg.pointCount, 0)

  // 每个像素代表的时间
  const pxPerMs = totalPoints / Math.max(MIN_CHART_WIDTH, w * 2) // 可用宽度是视口的 2 倍

  // 计算画布总宽度
  const gapWidth = 40 // 段间省略号宽度
  let totalWidth = (segs.length - 1) * gapWidth
  for (const seg of segs) {
    totalWidth += Math.max(seg.pointCount * 3, 100) // 每点至少 3px
  }
  totalWidth = Math.max(totalWidth, w)

  // 限制滚动范围
  const maxScroll = Math.max(0, totalWidth - w)
  if (followMode.value && store.isActive) {
    scrollOffset.value = maxScroll
  }
  scrollOffset.value = Math.max(0, Math.min(maxScroll, scrollOffset.value))

  // 清空
  ctx.clearRect(0, 0, w, h)

  // 绘制区域
  const plotLeft = PADDING.left
  const plotRight = w - PADDING.right
  const plotTop = PADDING.top
  const plotBottom = h - PADDING.bottom
  const plotW = plotRight - plotLeft
  const plotH = plotBottom - plotTop

  // 计算 Y 轴范围
  let maxBps = 1024 // 最小 1KB/s
  for (const p of pts) {
    if (p.u > maxBps) maxBps = p.u
    if (p.d > maxBps) maxBps = p.d
  }
  // 上取整到合适的刻度
  const yTicks = calcYTicks(maxBps)

  // 绘制背景网格
  ctx.strokeStyle = 'rgba(0,0,0,0.06)'
  ctx.lineWidth = 1
  for (const tick of yTicks) {
    const y = plotBottom - (tick / maxBps) * plotH
    ctx.beginPath()
    ctx.moveTo(plotLeft, y)
    ctx.lineTo(plotRight, y)
    ctx.stroke()
  }

  // Y 轴标签
  ctx.fillStyle = '#9ca3af'
  ctx.font = '11px -apple-system, BlinkMacSystemFont, sans-serif'
  ctx.textAlign = 'right'
  for (const tick of yTicks) {
    const y = plotBottom - (tick / maxBps) * plotH
    ctx.fillText(formatSpeed(tick), plotLeft - 8, y + 4)
  }

  // 绘制数据段
  let currentX = -scrollOffset.value + plotLeft

  for (let si = 0; si < segs.length; si++) {
    const seg = segs[si]
    const segPoints = pts.slice(seg.startIdx, seg.endIdx + 1)
    const segWidth = Math.max(segPoints.length * 3, 100)

    drawSegment(ctx, segPoints, segWidth, currentX, plotTop, plotH, plotBottom, maxBps)

    currentX += segWidth

    // 段间省略号
    if (si < segs.length - 1) {
      const gapX = currentX + gapWidth / 2
      if (gapX > plotLeft && gapX < plotRight) {
        ctx.fillStyle = '#9ca3af'
        ctx.font = '14px -apple-system, BlinkMacSystemFont, sans-serif'
        ctx.textAlign = 'center'
        ctx.fillText('···', gapX, plotBottom + 20)
      }
      currentX += gapWidth
    }
  }

  // X 轴时间标签
  drawXAxis(ctx, pts, segs, plotLeft, plotBottom, plotH, gapWidth, w, totalWidth)

  // 实时指示器
  if (store.isActive) {
    ctx.strokeStyle = 'rgba(59,130,246,0.3)'
    ctx.lineWidth = 1
    ctx.setLineDash([4, 4])
    const indicatorX = Math.min(plotRight - 2, totalWidth - scrollOffset.value + plotLeft)
    ctx.beginPath()
    ctx.moveTo(indicatorX, plotTop)
    ctx.lineTo(indicatorX, plotBottom)
    ctx.stroke()
    ctx.setLineDash([])
  }
}

function drawSegment(
  ctx: CanvasRenderingContext2D,
  pts: ThroughputPoint[],
  segWidth: number,
  startX: number,
  plotTop: number,
  plotH: number,
  plotBottom: number,
  maxBps: number,
): void {
  if (pts.length < 2) return

  const xScale = segWidth / (pts.length - 1)

  // 上传梯度填充
  drawGradientLine(ctx, pts, 'u', '#22c55e', 'rgba(34,197,94,0.15)', startX, xScale, plotTop, plotH, plotBottom, maxBps)

  // 下载梯度填充
  drawGradientLine(ctx, pts, 'd', '#3b82f6', 'rgba(59,130,246,0.15)', startX, xScale, plotTop, plotH, plotBottom, maxBps)
}

function drawGradientLine(
  ctx: CanvasRenderingContext2D,
  pts: ThroughputPoint[],
  key: 'u' | 'd',
  strokeColor: string,
  fillColor: string,
  startX: number,
  xScale: number,
  plotTop: number,
  plotH: number,
  plotBottom: number,
  maxBps: number,
): void {
  const hasData = pts.some((p) => p[key] > 0)
  if (!hasData) return

  ctx.beginPath()
  let firstX = 0
  let firstY = 0
  let started = false

  for (let i = 0; i < pts.length; i++) {
    const x = startX + i * xScale
    const bps = pts[i][key]
    const y = plotBottom - (bps / maxBps) * plotH

    if (!started) {
      ctx.moveTo(x, y)
      firstX = x
      firstY = y
      started = true
    } else {
      ctx.lineTo(x, y)
    }
  }

  // 描边
  ctx.strokeStyle = strokeColor
  ctx.lineWidth = 2
  ctx.lineJoin = 'round'
  ctx.stroke()

  // 梯度填充
  if (started) {
    const lastX = startX + (pts.length - 1) * xScale
    ctx.lineTo(lastX, plotBottom)
    ctx.lineTo(firstX, plotBottom)
    ctx.closePath()
    ctx.fillStyle = fillColor
    ctx.fill()
  }
}

function drawXAxis(
  ctx: CanvasRenderingContext2D,
  pts: ThroughputPoint[],
  segs: TimeSegment[],
  plotLeft: number,
  plotBottom: number,
  plotH: number,
  gapWidth: number,
  w: number,
  totalWidth: number,
): void {
  ctx.fillStyle = '#9ca3af'
  ctx.font = '10px -apple-system, BlinkMacSystemFont, sans-serif'
  ctx.textAlign = 'center'

  let currentX = -scrollOffset.value + plotLeft
  const plotRight = w - PADDING.right

  for (let si = 0; si < segs.length; si++) {
    const seg = segs[si]
    const segPoints = pts.slice(seg.startIdx, seg.endIdx + 1)
    const segWidth = Math.max(segPoints.length * 3, 100)

    // 在可见区域内绘制时间标签
    const segStartX = currentX
    const segEndX = currentX + segWidth

    if (segEndX > plotLeft && segStartX < plotRight) {
      const visibleStart = Math.max(0, plotLeft - segStartX)
      const visibleEnd = Math.min(segWidth, plotRight - segStartX)

      // 计算应显示的标签数量
      const labelCount = Math.max(2, Math.floor(segWidth / 80))
      const step = Math.max(1, Math.floor(segPoints.length / labelCount))

      for (let i = 0; i < segPoints.length; i += step) {
        const x = segStartX + i * (segWidth / (segPoints.length - 1 || 1))
        if (x >= plotLeft - 30 && x <= plotRight + 30) {
          ctx.fillText(formatTimeAxis(segPoints[i].t), x, plotBottom + 16)
        }
      }
    }

    currentX += segWidth + gapWidth
  }
}

function calcYTicks(maxBps: number): number[] {
  const ticks: number[] = []
  // 找到合适的步长
  let step: number
  if (maxBps <= 1024) step = 256
  else if (maxBps <= 10240) step = 2048
  else if (maxBps <= 102400) step = 20480
  else if (maxBps <= 1048576) step = 262144
  else if (maxBps <= 10485760) step = 2097152
  else step = 10485760

  for (let v = 0; v <= maxBps; v += step) {
    ticks.push(v)
  }
  // 确保最后一个刻度等于 maxBps
  if (ticks[ticks.length - 1] !== maxBps && ticks.length > 0) {
    ticks.push(maxBps)
  }
  return ticks
}

// ---- 交互 ----
function onMouseDown(e: MouseEvent): void {
  dragging.value = true
  dragStartX.value = e.clientX
  dragStartOffset.value = scrollOffset.value
  followMode.value = false
}

function onMouseUp(): void {
  dragging.value = false
}

function onMouseMove(e: MouseEvent): void {
  if (dragging.value) {
    const dx = dragStartX.value - e.clientX
    scrollOffset.value = dragStartOffset.value + dx
    render()
    return
  }

  // 悬停检测
  if (!containerRef.value) return
  const rect = containerRef.value.getBoundingClientRect()
  const mx = e.clientX - rect.left
  const my = e.clientY - rect.top

  hoverX.value = mx
  hoverY.value = my

  // 找到最近的数据点
  const pts = store.points
  const segs = segments.value
  if (pts.length < 2 || segs.length === 0) {
    hoveredPoint.value = null
    return
  }

  let currentX = -scrollOffset.value + PADDING.left
  const gapWidth = 40
  let closest: ThroughputPoint | null = null
  let closestDist = Infinity

  for (let si = 0; si < segs.length; si++) {
    const seg = segs[si]
    const segPoints = pts.slice(seg.startIdx, seg.endIdx + 1)
    const segWidth = Math.max(segPoints.length * 3, 100)

    if (mx >= currentX && mx <= currentX + segWidth) {
      const idx = Math.round(((mx - currentX) / segWidth) * (segPoints.length - 1))
      const clamped = Math.max(0, Math.min(segPoints.length - 1, idx))
      closest = segPoints[clamped]
      break
    }

    const distToStart = Math.abs(mx - currentX)
    const distToEnd = Math.abs(mx - (currentX + segWidth))
    const minDist = Math.min(distToStart, distToEnd)
    if (minDist < closestDist && minDist < 30) {
      closestDist = minDist
      closest = distToStart < distToEnd ? segPoints[0] : segPoints[segPoints.length - 1]
    }

    currentX += segWidth + gapWidth
  }

  hoveredPoint.value = closest
}

function onWheel(e: WheelEvent): void {
  followMode.value = false
  scrollOffset.value += e.deltaY * 0.5 + e.deltaX * 0.5
  render()
}

// ---- 响应式 ----
let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  nextTick(() => {
    render()
    if (containerRef.value) {
      resizeObserver = new ResizeObserver(() => render())
      resizeObserver.observe(containerRef.value)
    }
  })
})

onUnmounted(() => {
  if (animFrameId) cancelAnimationFrame(animFrameId)
  if (resizeObserver) resizeObserver.disconnect()
})

// 监听数据变化重新渲染
watch(
  () => store.points.length,
  () => {
    nextTick(render)
  },
)

// 监听活跃状态
watch(
  () => store.isActive,
  (active) => {
    if (active) {
      followMode.value = true
    }
    nextTick(render)
  },
)
</script>

<style scoped>
.throughput-chart {
  position: relative;
  width: 100%;
  height: 260px;
  background: #fafbfc;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
  user-select: none;
  -webkit-user-select: none;
}

.throughput-chart.is-empty {
  height: auto;
  min-height: 160px;
}

.chart-canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #9ca3af;
  font-size: 14px;
}

.chart-empty i {
  font-size: 32px;
  opacity: 0.4;
}

.chart-empty-hint {
  font-size: 12px;
  opacity: 0.6;
}

/* 悬停提示 */
.chart-tooltip {
  position: absolute;
  z-index: 10;
  background: rgba(0, 0, 0, 0.85);
  color: #fff;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12px;
  pointer-events: none;
  backdrop-filter: blur(8px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  min-width: 140px;
}

.tooltip-time {
  color: rgba(255, 255, 255, 0.6);
  font-size: 11px;
  margin-bottom: 4px;
}

.tooltip-row {
  display: flex;
  align-items: center;
  gap: 6px;
  line-height: 1.8;
}

.tooltip-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.tooltip-dot.upload {
  background: #22c55e;
  box-shadow: 0 0 6px rgba(34, 197, 94, 0.5);
}

.tooltip-dot.download {
  background: #3b82f6;
  box-shadow: 0 0 6px rgba(59, 130, 246, 0.5);
}

.tooltip-dot.idle {
  background: #9ca3af;
}

/* 图例 */
.chart-legend {
  position: absolute;
  top: 8px;
  right: 12px;
  display: flex;
  gap: 16px;
  z-index: 2;
  pointer-events: none;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #6b7280;
}

.legend-dot {
  width: 10px;
  height: 3px;
  border-radius: 2px;
}

.legend-dot.upload {
  background: #22c55e;
}

.legend-dot.download {
  background: #3b82f6;
}
</style>