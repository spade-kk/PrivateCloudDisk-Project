<!--
  ============================================================
  PPTPreviewView.vue — PowerPoint 演示文稿预览页面
  ============================================================
  
  幻灯片式预览布局，功能包括：
    - 左侧幻灯片缩略图列表（可切换显示/隐藏）
    - 中央幻灯片主视图
    - 幻灯片切换动画（Fade/Slide）
    - 全屏演示模式（暗色背景，隐藏工具栏）
    - 幻灯片标题导航
    - 键盘快捷键（← → 切换幻灯片，F 全屏，Esc 退出）
  
  后端自动流水线已将 PPT 转换为 PDF，前端使用 PDF.js 渲染第页。
  与视频播放器 (VideoPlayerView.vue) 对接模式一致。
  
  路由：/preview/ppt/:fileId
  查询参数：name - 文件名
  ============================================================
-->
<template>
  <div class="ppt-preview-page" :class="{ 'fullscreen-mode': isFullscreen }">
    <!-- ============================================================
         状态覆盖层（加载/处理/等待/错误/未找到）
         ============================================================ -->
    <div v-if="store.loading" class="state-overlay">
      <div class="state-content">
        <div class="state-spinner"><div class="ring"></div></div>
        <h2 class="state-title">正在加载演示文稿...</h2>
        <p class="state-subtitle">{{ store.currentFile?.node_name || '' }}</p>
      </div>
    </div>

    <div v-else-if="store.isProcessing" class="state-overlay">
      <div class="state-content">
        <div class="state-icon processing-icon"><i class="fa fa-cog fa-spin"></i></div>
        <h2 class="state-title">演示文稿转换中</h2>
        <p class="state-subtitle">{{ store.previewInfo?.message || '后台正在将演示文稿转换为可预览格式，请稍候...' }}</p>
        <div class="progress-wrapper" v-if="store.progress > 0">
          <div class="progress-bar"><div class="progress-fill" :style="{ width: store.progress + '%' }"></div></div>
          <span class="progress-text">{{ store.progress }}%</span>
        </div>
        <p class="state-hint">文件上传后自动触发转换流水线，预览就绪后将自动刷新</p>
        <div class="state-actions">
          <button @click="handleRetry" class="state-btn state-btn-secondary"><i class="fa fa-refresh"></i> 刷新状态</button>
          <button @click="goBack" class="state-btn state-btn-outline"><i class="fa fa-arrow-left"></i> 返回</button>
        </div>
      </div>
    </div>

    <div v-else-if="store.isPending" class="state-overlay">
      <div class="state-content">
        <div class="state-icon pending-icon"><i class="fa fa-clock-o"></i></div>
        <h2 class="state-title">等待处理</h2>
        <p class="state-subtitle">{{ store.previewInfo?.message || '文件已上传，后台转换流水线即将启动，请稍后再试' }}</p>
        <div class="state-actions">
          <button @click="handleRetry" class="state-btn state-btn-secondary"><i class="fa fa-refresh"></i> 刷新状态</button>
          <button @click="goBack" class="state-btn state-btn-outline"><i class="fa fa-arrow-left"></i> 返回</button>
        </div>
      </div>
    </div>

    <div v-else-if="store.isFailed" class="state-overlay">
      <div class="state-content">
        <div class="state-icon error-icon"><i class="fa fa-exclamation-triangle"></i></div>
        <h2 class="state-title">{{ store.error?.title || '预览生成失败' }}</h2>
        <p class="state-subtitle">{{ store.error?.message }}</p>
        <div v-if="store.previewInfo?.errorDetail" class="state-meta">
          <span class="meta-label">错误详情：</span>
          <span class="meta-value">{{ store.previewInfo.errorDetail }}</span>
        </div>
        <div class="state-actions">
          <button @click="handleRetry" class="state-btn state-btn-primary"><i class="fa fa-refresh"></i> 重新加载</button>
          <button @click="goBack" class="state-btn state-btn-secondary"><i class="fa fa-arrow-left"></i> 返回</button>
        </div>
      </div>
    </div>

    <div v-else-if="store.isNotFound" class="state-overlay">
      <div class="state-content">
        <div class="state-icon notfound-icon"><i class="fa fa-file-o"></i></div>
        <h2 class="state-title">{{ store.error?.title || '预览资源不存在' }}</h2>
        <p class="state-subtitle">{{ store.error?.message }}</p>
        <div class="state-actions">
          <button @click="handleRetry" class="state-btn state-btn-secondary"><i class="fa fa-refresh"></i> 刷新状态</button>
          <button @click="goBack" class="state-btn state-btn-primary"><i class="fa fa-arrow-left"></i> 返回</button>
        </div>
      </div>
    </div>

    <div v-else-if="store.error" class="state-overlay">
      <div class="state-content">
        <div class="state-icon network-error-icon"><i class="fa fa-wifi"></i></div>
        <h2 class="state-title">{{ store.error.title }}</h2>
        <p class="state-subtitle">{{ store.error.message }}</p>
        <div class="state-actions">
          <button @click="handleRetry" class="state-btn state-btn-primary"><i class="fa fa-refresh"></i> 重新加载</button>
          <button @click="goBack" class="state-btn state-btn-secondary"><i class="fa fa-arrow-left"></i> 返回</button>
        </div>
      </div>
    </div>

    <!-- ============================================================
         正常预览状态 — 转换已完成
         幻灯片布局：左侧缩略图列表 + 中央幻灯片 + 底部控制栏
         ============================================================ -->
    <template v-else>
      <!-- 顶部工具栏 -->
      <PreviewToolbar
        :file-name="store.currentFile?.node_name || '演示文稿'"
        :file-type="store.documentType"
        :scale="store.config.scale"
        :rotation="store.config.rotation"
        :current-page="store.config.currentPage"
        :total-pages="store.totalPages"
        :show-page-nav="true"
        :show-zoom="true"
        :show-rotate="false"
        :show-search="false"
        :show-fullscreen="true"
        :show-print="true"
        :show-share="true"
        :show-download="true"
        :show-thumbnails-toggle="true"
        :thumbnails-active="showSlidesPanel"
        :is-fullscreen="isFullscreen"
        :is-dark="false"
        @close="goBack"
        @page-first="handlePageFirst"
        @page-prev="previousSlide"
        @page-next="nextSlide"
        @page-last="handlePageLast"
        @page-change="handlePageChange"
        @zoom-in="store.zoomIn"
        @zoom-out="store.zoomOut"
        @zoom-reset="store.resetZoom"
        @fullscreen="toggleFullscreen"
        @print="handlePrint"
        @share="handleShare"
        @download="handleDownload"
        @toggle-thumbnails="toggleSlidesPanel"
      />

      <!-- 主体区域 -->
      <div class="ppt-main-area">
        <!-- 左侧幻灯片缩略图列表 -->
        <Transition name="panel-slide">
          <div v-if="showSlidesPanel" class="slides-panel">
            <div class="slides-header">
              <span class="slides-title">
                <i class="fa fa-th"></i> 幻灯片
              </span>
              <span class="slides-count">{{ store.totalPages }} 张</span>
            </div>
            <div class="slides-list" ref="slidesListRef">
              <div
                v-for="page in store.totalPages"
                :key="page"
                @click="navigateToSlide(page)"
                class="slide-card"
                :class="{ active: store.config.currentPage === page }"
              >
                <div class="slide-thumb">
                  <canvas
                    :ref="(el) => setSlideThumbRef(el, page)"
                    class="slide-thumb-canvas"
                  ></canvas>
                </div>
                <div class="slide-info">
                  <span class="slide-number">{{ page }}</span>
                  <span class="slide-name" v-if="slideTitles[page - 1]" :title="slideTitles[page - 1]">
                    {{ slideTitles[page - 1] }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </Transition>

        <!-- 中央幻灯片主视图 -->
        <div
          class="ppt-viewer-container"
          ref="viewerContainerRef"
          @click="handleViewerClick"
        >
          <!-- 上一张按钮 -->
          <button
            v-if="store.config.currentPage > 1"
            @click.stop="previousSlide"
            class="nav-btn nav-prev"
            title="上一张"
          >
            <i class="fa fa-chevron-left"></i>
          </button>

          <!-- 幻灯片画布 -->
          <div
            class="slide-canvas-wrapper"
            :class="[`transition-${slideTransition}`]"
            :style="{ transform: `scale(${store.config.scale})` }"
          >
            <canvas ref="slideCanvasRef" class="slide-canvas"></canvas>
          </div>

          <!-- 下一张按钮 -->
          <button
            v-if="store.config.currentPage < store.totalPages"
            @click.stop="nextSlide"
            class="nav-btn nav-next"
            title="下一张"
          >
            <i class="fa fa-chevron-right"></i>
          </button>

          <!-- 全屏模式下的幻灯片信息和进度 -->
          <div v-if="isFullscreen" class="fullscreen-info">
            <span class="fs-slide-name">{{ store.currentFile?.node_name }}</span>
            <span class="fs-slide-progress">{{ store.config.currentPage }} / {{ store.totalPages }}</span>
          </div>
        </div>
      </div>

      <!-- 底部控制栏（PPT 演示风格） -->
      <div class="ppt-control-bar">
        <div class="control-left">
          <button @click="toggleSlidesPanel" class="control-btn" :class="{ active: showSlidesPanel }">
            <i class="fa fa-th"></i>
          </button>
          <button @click="toggleSlideTransition" class="control-btn" :title="`切换动画: ${slideTransition === 'fade' ? '淡入淡出' : '滑动'}`">
            <i class="fa fa-magic"></i>
          </button>
        </div>
        <div class="control-center">
          <button @click="previousSlide" class="control-btn" :disabled="store.config.currentPage <= 1">
            <i class="fa fa-step-backward"></i>
          </button>
          <span class="control-page-info">{{ store.config.currentPage }} / {{ store.totalPages }}</span>
          <button @click="nextSlide" class="control-btn" :disabled="store.config.currentPage >= store.totalPages">
            <i class="fa fa-step-forward"></i>
          </button>
        </div>
        <div class="control-right">
          <button @click="toggleFullscreen" class="control-btn" :title="isFullscreen ? '退出全屏' : '全屏演示'">
            <i :class="isFullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i>
          </button>
          <button @click="handlePrint" class="control-btn" title="打印">
            <i class="fa fa-print"></i>
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useOfficePreviewStore } from '@/stores/officePreviewStore'
import { buildAuthenticatedPdfSource } from '@/utils/pdfPreviewAuth'
import { useToastStore } from '@/stores/toastStore'
import PreviewToolbar from '@/components/preview/shared/PreviewToolbar.vue'

const route = useRoute()
const router = useRouter()
const store = useOfficePreviewStore()
const toastStore = useToastStore()

// ============================================================
// Refs
// ============================================================
const slideCanvasRef = ref<HTMLCanvasElement | null>(null)
const viewerContainerRef = ref<HTMLDivElement | null>(null)
const slidesListRef = ref<HTMLDivElement | null>(null)

// ============================================================
// 状态
// ============================================================
const pdfDoc = ref<any>(null)
const isFullscreen = ref(false)
const showSlidesPanel = ref(true)
const slideTransition = ref<'fade' | 'slide'>('fade')
const slideThumbRefs = new Map<number, HTMLCanvasElement>()

let pdfjsLib: any = null

// ============================================================
// 计算属性
// ============================================================

/** 幻灯片标题列表 */
const slideTitles = computed(() => {
  return store.metadata?.slideTitles || []
})

// ============================================================
// 初始化
// ============================================================

onMounted(async () => {
  const fileId = route.params.fileId as string
  const fileName = route.query.name as string

  if (!fileId) {
    store.error = { title: '参数错误', message: '缺少文件 ID 参数' }
    return
  }

  await store.loadDocument({
    node_id: fileId,
    node_name: fileName ? decodeURIComponent(fileName) : '演示文稿',
    node_type: 'FILE',
  })

  if (store.isCompleted) {
    await initPdfJs()
    await loadPdfIntoCanvas()
  }

  document.addEventListener('fullscreenchange', handleFullscreenChange)
  document.addEventListener('keydown', handleKeydown)

  watch(() => store.config.currentPage, () => { if (pdfDoc.value) renderSlide(store.config.currentPage) })
  watch(() => store.config.scale, () => { if (pdfDoc.value) renderSlide(store.config.currentPage) })
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  document.removeEventListener('keydown', handleKeydown)
  if (pdfDoc.value) pdfDoc.value.destroy?.()
  store.closePreview()
})

// ============================================================
// PDF.js 初始化
// ============================================================

async function initPdfJs(): Promise<void> {
  if ((window as any).pdfjsLib) {
    pdfjsLib = (window as any).pdfjsLib
    return
  }
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.4.120/pdf.min.js'
    script.onload = () => {
      pdfjsLib = (window as any).pdfjsLib
      pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.4.120/pdf.worker.min.js'
      resolve()
    }
    script.onerror = () => reject(new Error('PDF.js 加载失败'))
    document.head.appendChild(script)
  })
}

// ============================================================
// PDF 加载与渲染
// ============================================================

async function loadPdfIntoCanvas(): Promise<void> {
  try {
    const pdfUrl = store.previewUrl
    if (!pdfUrl) throw new Error('预览 URL 不可用')

    const loadingTask = pdfjsLib.getDocument(buildAuthenticatedPdfSource(pdfUrl))
    pdfDoc.value = await loadingTask.promise
    await renderSlide(store.config.currentPage)

    if (showSlidesPanel.value) {
      await nextTick()
      renderSlideThumbnails()
    }
  } catch (err: any) {
    console.error('PDF 加载失败:', err)
    store.error = { title: 'PDF 渲染失败', message: err.message || '无法渲染演示文稿' }
  }
}

async function renderSlide(pageNum: number): Promise<void> {
  if (!pdfDoc.value || !slideCanvasRef.value) return

  try {
    const page = await pdfDoc.value.getPage(pageNum)
    const canvas = slideCanvasRef.value
    const context = canvas.getContext('2d')!

    // 幻灯片采用 16:9 比例渲染
    const containerWidth = viewerContainerRef.value?.clientWidth
      ? viewerContainerRef.value.clientWidth - 120
      : 960
    const containerHeight = viewerContainerRef.value?.clientHeight
      ? viewerContainerRef.value.clientHeight - 80
      : 540

    const viewport = page.getViewport({ scale: 1.0 })
    const fitScaleW = containerWidth / viewport.width
    const fitScaleH = containerHeight / viewport.height
    const fitScale = Math.min(fitScaleW, fitScaleH) * store.config.scale

    const scaledViewport = page.getViewport({ scale: fitScale })
    canvas.height = scaledViewport.height
    canvas.width = scaledViewport.width

    await page.render({ canvasContext: context, viewport: scaledViewport }).promise
    store.config.currentPage = pageNum

    // 滚动缩略图列表到当前幻灯片
    scrollThumbnailIntoView(pageNum)
  } catch (err: any) {
    console.error('渲染幻灯片失败:', err)
  }
}

async function renderSlideThumbnails(): Promise<void> {
  if (!pdfDoc.value) return

  const maxSlides = Math.min(pdfDoc.value.numPages, 30)
  for (let i = 1; i <= maxSlides; i++) {
    const canvas = slideThumbRefs.get(i)
    if (!canvas) continue
    try {
      const page = await pdfDoc.value.getPage(i)
      const viewport = page.getViewport({ scale: 0.15 })
      canvas.height = viewport.height
      canvas.width = viewport.width
      await page.render({ canvasContext: canvas.getContext('2d')!, viewport }).promise
    } catch { /* skip */ }
  }
}

function setSlideThumbRef(el: any, page: number): void {
  if (el) slideThumbRefs.set(page, el as HTMLCanvasElement)
}

function scrollThumbnailIntoView(pageNum: number): void {
  nextTick(() => {
    const list = slidesListRef.value
    if (!list) return
    const cards = list.querySelectorAll('.slide-card')
    const card = cards[pageNum - 1] as HTMLElement
    if (card) {
      card.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  })
}

// ============================================================
// 幻灯片导航
// ============================================================

function navigateToSlide(page: number): void {
  store.setCurrentPage(page)
  renderSlide(page)
}

function previousSlide(): void {
  if (store.config.currentPage > 1) {
    store.setCurrentPage(store.config.currentPage - 1)
    renderSlide(store.config.currentPage)
  }
}

function nextSlide(): void {
  if (store.config.currentPage < store.totalPages) {
    store.setCurrentPage(store.config.currentPage + 1)
    renderSlide(store.config.currentPage)
  }
}

function handlePageFirst(): void {
  store.setCurrentPage(1)
  renderSlide(1)
}

function handlePageLast(): void {
  store.setCurrentPage(store.totalPages)
  renderSlide(store.totalPages)
}

function handlePageChange(page: number): void {
  store.setCurrentPage(page)
  renderSlide(page)
}

// ============================================================
// 幻灯片面板
// ============================================================

function toggleSlidesPanel(): void {
  showSlidesPanel.value = !showSlidesPanel.value
}

// ============================================================
// 切换动画
// ============================================================

function toggleSlideTransition(): void {
  slideTransition.value = slideTransition.value === 'fade' ? 'slide' : 'fade'
}

// ============================================================
// 全屏
// ============================================================

async function toggleFullscreen(): Promise<void> {
  if (!document.fullscreenElement) {
    await document.documentElement.requestFullscreen()
    isFullscreen.value = true
  } else {
    await document.exitFullscreen()
    isFullscreen.value = false
  }
}

function handleFullscreenChange(): void {
  isFullscreen.value = !!document.fullscreenElement
}

// ============================================================
// 键盘快捷键
// ============================================================

function handleKeydown(e: KeyboardEvent): void {
  const tag = document.activeElement?.tagName?.toLowerCase()
  if (tag === 'input' || tag === 'textarea' || tag === 'select') return

  switch (e.key) {
    case 'ArrowLeft':
    case 'ArrowUp':
      e.preventDefault()
      previousSlide()
      break
    case 'ArrowRight':
    case 'ArrowDown':
    case ' ':
      e.preventDefault()
      nextSlide()
      break
    case 'Home':
      e.preventDefault()
      handlePageFirst()
      break
    case 'End':
      e.preventDefault()
      handlePageLast()
      break
    case 'f':
    case 'F':
      e.preventDefault()
      toggleFullscreen()
      break
    case 'Escape':
      if (isFullscreen.value) {
        e.preventDefault()
        document.exitFullscreen().catch(() => {})
      }
      break
  }
}

// ============================================================
// 点击切换
// ============================================================

function handleViewerClick(e: MouseEvent): void {
  const target = e.target as HTMLElement
  if (target.closest('.nav-btn')) return // 不处理导航按钮点击

  const rect = viewerContainerRef.value?.getBoundingClientRect()
  if (!rect) return

  const clickX = e.clientX - rect.left
  const midX = rect.width / 2

  if (clickX < midX * 0.3) {
    previousSlide()
  } else if (clickX > midX * 1.7) {
    nextSlide()
  }
}

// ============================================================
// 打印
// ============================================================

function handlePrint(): void {
  const pdfUrl = store.previewUrl
  if (!pdfUrl) return
  const printWindow = window.open(pdfUrl, '_blank')
  if (printWindow) printWindow.addEventListener('load', () => printWindow.print())
}

// ============================================================
// 分享
// ============================================================

function handleShare(): void {
  const fileName = store.currentFile?.node_name || ''
  if (navigator.share) {
    navigator.share({ title: fileName, text: `查看演示文稿: ${fileName}`, url: window.location.href }).catch(() => copyShareLink())
  } else {
    copyShareLink()
  }
}

function copyShareLink(): void {
  navigator.clipboard.writeText(window.location.href).then(() => {
    toastStore.showToast('链接已复制到剪贴板', 'success')
  }).catch(() => {
    toastStore.showToast('无法复制链接', 'error')
  })
}

// ============================================================
// 下载
// ============================================================

function handleDownload(): void {
  const fileId = store.currentFile?.node_id
  const fileName = store.currentFile?.node_name || 'presentation'
  if (!fileId) return
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  const link = document.createElement('a')
  link.href = `${baseUrl}/files/files/${fileId}/download`
  link.download = fileName
  link.click()
}

// ============================================================
// 导航
// ============================================================

function goBack(): void {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/app')
  }
}

function handleRetry(): void {
  store.retry().then(() => {
    if (store.isCompleted) initPdfJs().then(() => loadPdfIntoCanvas())
  })
}
</script>

<style scoped>
/* ============================================================
   整体布局
   ============================================================ */
.ppt-preview-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f5f5;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.ppt-preview-page.fullscreen-mode {
  background: #000;
}

/* ============================================================
   状态覆盖层（与 WordPreviewView 一致）
   ============================================================ */
.state-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.92);
  z-index: 100;
}

.state-content {
  text-align: center;
  color: #fff;
  max-width: 440px;
  padding: 40px;
}

.state-spinner { margin-bottom: 20px; }

.ring {
  width: 48px;
  height: 48px;
  border: 3px solid rgba(255, 255, 255, 0.15);
  border-top-color: #1677ff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto;
}

@keyframes spin { to { transform: rotate(360deg); } }

.state-icon { font-size: 48px; margin-bottom: 20px; opacity: 0.9; }
.processing-icon { color: #1677ff; }
.error-icon { color: #f56c6c; }
.notfound-icon { color: #8c8c8c; }
.pending-icon { color: #faad14; }
.network-error-icon { color: #ff7875; }

.state-title { font-size: 20px; font-weight: 600; margin: 0 0 10px; color: #fff; }
.state-subtitle { font-size: 14px; color: rgba(255, 255, 255, 0.6); margin: 0 0 8px; line-height: 1.5; }
.state-hint { font-size: 12px; color: rgba(255, 255, 255, 0.35); margin: 16px 0 0; }

.state-meta {
  margin-top: 12px;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  text-align: left;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  word-break: break-all;
}

.meta-label { font-weight: 600; color: rgba(255, 255, 255, 0.7); }

.state-actions { display: flex; gap: 12px; justify-content: center; margin-top: 28px; }

.state-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 22px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.state-btn-primary { background: #1677ff; color: #fff; }
.state-btn-primary:hover { background: #4096ff; }
.state-btn-secondary { background: rgba(255, 255, 255, 0.1); color: rgba(255, 255, 255, 0.85); border: 1px solid rgba(255, 255, 255, 0.15); }
.state-btn-secondary:hover { background: rgba(255, 255, 255, 0.18); }
.state-btn-outline { background: transparent; color: rgba(255, 255, 255, 0.65); border: 1px solid rgba(255, 255, 255, 0.2); }
.state-btn-outline:hover { color: rgba(255, 255, 255, 0.85); border-color: rgba(255, 255, 255, 0.35); }

.progress-wrapper { display: flex; align-items: center; gap: 12px; margin-top: 16px; max-width: 280px; margin-left: auto; margin-right: auto; }
.progress-bar { flex: 1; height: 6px; background: rgba(255, 255, 255, 0.1); border-radius: 3px; overflow: hidden; }
.progress-fill { height: 100%; background: #1677ff; border-radius: 3px; transition: width 0.5s ease; }
.progress-text { font-size: 13px; color: rgba(255, 255, 255, 0.5); min-width: 36px; text-align: right; }

/* ============================================================
   主体区域
   ============================================================ */
.ppt-main-area {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
}

/* ============================================================
   幻灯片缩略图面板
   ============================================================ */
.slides-panel {
  width: 220px;
  background: #fff;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  z-index: 10;
}

.slides-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #f1f5f9;
  background: #f8fafc;
}

.slides-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.slides-count {
  font-size: 0.75rem;
  color: #94a3b8;
}

.slides-list {
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.slide-card {
  cursor: pointer;
  border: 2px solid transparent;
  border-radius: 0.5rem;
  padding: 0.375rem;
  transition: all 0.15s ease;
}

.slide-card:hover {
  background: #f1f5f9;
}

.slide-card.active {
  border-color: #4b6cb7;
  background: #eff6ff;
}

.slide-thumb {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 0.25rem;
  overflow: hidden;
  display: flex;
  justify-content: center;
  aspect-ratio: 16 / 9;
}

.slide-thumb-canvas {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.slide-info {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.25rem 0.125rem 0;
}

.slide-number {
  font-size: 0.7rem;
  font-weight: 600;
  color: #94a3b8;
  min-width: 1.25rem;
  text-align: center;
}

.slide-name {
  font-size: 0.7rem;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ============================================================
   幻灯片查看器
   ============================================================ */
.ppt-viewer-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background: #e8eaed;
  padding: 2rem;
  cursor: pointer;
}

.fullscreen-mode .ppt-viewer-container {
  background: #000;
  padding: 0;
}

/* 导航按钮 */
.nav-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 10;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #e2e8f0;
  color: #475569;
  font-size: 1rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.nav-btn:hover {
  background: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.nav-prev { left: 1rem; }
.nav-next { right: 1rem; }

.fullscreen-mode .nav-btn {
  background: rgba(0, 0, 0, 0.4);
  border-color: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.8);
}

.fullscreen-mode .nav-btn:hover {
  background: rgba(0, 0, 0, 0.6);
}

/* 幻灯片画布 */
.slide-canvas-wrapper {
  transition: transform 0.2s ease;
}

.slide-canvas-wrapper.transition-fade {
  animation: slideFadeIn 0.3s ease;
}

.slide-canvas-wrapper.transition-slide {
  animation: slideInRight 0.3s ease;
}

@keyframes slideFadeIn {
  from { opacity: 0; transform: scale(0.98); }
  to { opacity: 1; transform: scale(1); }
}

@keyframes slideInRight {
  from { opacity: 0; transform: translateX(30px); }
  to { opacity: 1; transform: translateX(0); }
}

.slide-canvas {
  display: block;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
}

.fullscreen-mode .slide-canvas {
  box-shadow: none;
}

/* 全屏信息 */
.fullscreen-info {
  position: absolute;
  bottom: 1rem;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 1rem;
  font-size: 0.8rem;
  color: rgba(255, 255, 255, 0.5);
  background: rgba(0, 0, 0, 0.4);
  padding: 0.375rem 1rem;
  border-radius: 1rem;
  backdrop-filter: blur(8px);
}

/* ============================================================
   底部控制栏（PPT 风格）
   ============================================================ */
.ppt-control-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 1rem;
  background: #fff;
  border-top: 1px solid #e2e8f0;
  flex-shrink: 0;
}

.fullscreen-mode .ppt-control-bar {
  background: rgba(0, 0, 0, 0.8);
  border-top-color: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.7);
}

.control-left,
.control-right {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.control-center {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.control-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border: none;
  background: transparent;
  color: #64748b;
  border-radius: 0.375rem;
  cursor: pointer;
  font-size: 0.85rem;
  transition: all 0.15s;
}

.control-btn:hover:not(:disabled) {
  background: #f1f5f9;
  color: #1e293b;
}

.control-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.control-btn.active {
  color: #4b6cb7;
  background: #eff6ff;
}

.fullscreen-mode .control-btn {
  color: rgba(255, 255, 255, 0.5);
}

.fullscreen-mode .control-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.85);
}

.control-page-info {
  font-size: 0.8rem;
  font-weight: 500;
  color: #475569;
  min-width: 60px;
  text-align: center;
}

.fullscreen-mode .control-page-info {
  color: rgba(255, 255, 255, 0.7);
}

/* ============================================================
   过渡动画
   ============================================================ */
.panel-slide-enter-active, .panel-slide-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}

.panel-slide-enter-from, .panel-slide-leave-to {
  transform: translateX(-100%);
  opacity: 0;
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 768px) {
  .slides-panel { width: 180px; }
  .ppt-viewer-container { padding: 1rem; }
  .nav-btn { width: 36px; height: 36px; }
}

@media (max-width: 480px) {
  .slides-panel { display: none; }
  .nav-btn { display: none; }
}
</style>
