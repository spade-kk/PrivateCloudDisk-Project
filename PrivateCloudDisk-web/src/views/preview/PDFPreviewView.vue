<!--
  ============================================================
  PDFPreviewView.vue — PDF 文档预览页面
  ============================================================
  
  后端自动流水线处理 Office 文档，前端仅查询预览状态。
  与视频播放器 (VideoPlayerView.vue) 对接模式一致：
    - loading:    显示加载动画
    - processing: 显示"文档转换中" + 进度
    - failed:     显示错误详情 + 重试/返回
    - not_found:  显示"预览资源不存在" + 返回
    - pending:    显示"等待处理"提示
    - completed:  渲染 PDF 预览（工具栏 + 画布 + 缩略图 + 搜索）
  
  路由：/preview/pdf/:fileId
  查询参数：name - 文件名
  ============================================================
-->
<template>
  <div class="pdf-preview-page" :class="{ 'dark-mode': store.config.colorMode === 'dark' }">
    <!-- ============================================================
         加载状态 — 加载中
         ============================================================ -->
    <div v-if="store.loading" class="state-overlay">
      <div class="state-content">
        <div class="state-spinner">
          <div class="ring"></div>
        </div>
        <h2 class="state-title">正在加载文档...</h2>
        <p class="state-subtitle">{{ store.currentFile?.node_name || '' }}</p>
      </div>
    </div>

    <!-- ============================================================
         处理状态 — 后端流水线正在转换中
         ============================================================ -->
    <div v-else-if="store.isProcessing" class="state-overlay">
      <div class="state-content">
        <div class="state-icon processing-icon">
          <i class="fa fa-cog fa-spin"></i>
        </div>
        <h2 class="state-title">文档转换中</h2>
        <p class="state-subtitle">
          {{ store.previewInfo?.message || '后台正在将文档转换为可预览格式，请稍候...' }}
        </p>
        <div class="progress-wrapper" v-if="store.progress > 0">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: store.progress + '%' }"></div>
          </div>
          <span class="progress-text">{{ store.progress }}%</span>
        </div>
        <p class="state-hint">文件上传后自动触发转换流水线，预览就绪后此页面将自动刷新</p>
        <div class="state-actions">
          <button @click="handleRetry" class="state-btn state-btn-secondary">
            <i class="fa fa-refresh"></i> 刷新状态
          </button>
          <button @click="goBack" class="state-btn state-btn-outline">
            <i class="fa fa-arrow-left"></i> 返回
          </button>
        </div>
      </div>
    </div>

    <!-- ============================================================
         等待状态 — 文件已上传，流水线尚未启动
         ============================================================ -->
    <div v-else-if="store.isPending" class="state-overlay">
      <div class="state-content">
        <div class="state-icon pending-icon">
          <i class="fa fa-clock-o"></i>
        </div>
        <h2 class="state-title">等待处理</h2>
        <p class="state-subtitle">
          {{ store.previewInfo?.message || '文件已上传，后台转换流水线即将启动，请稍后再试' }}
        </p>
        <div class="state-actions">
          <button @click="handleRetry" class="state-btn state-btn-secondary">
            <i class="fa fa-refresh"></i> 刷新状态
          </button>
          <button @click="goBack" class="state-btn state-btn-outline">
            <i class="fa fa-arrow-left"></i> 返回
          </button>
        </div>
      </div>
    </div>

    <!-- ============================================================
         错误状态 — 转换失败 / 文件损坏
         ============================================================ -->
    <div v-else-if="store.isFailed" class="state-overlay">
      <div class="state-content">
        <div class="state-icon error-icon">
          <i class="fa fa-exclamation-triangle"></i>
        </div>
        <h2 class="state-title">{{ store.error?.title || '预览生成失败' }}</h2>
        <p class="state-subtitle">{{ store.error?.message }}</p>
        <div v-if="store.previewInfo?.errorDetail" class="state-meta">
          <span class="meta-label">错误详情：</span>
          <span class="meta-value">{{ store.previewInfo.errorDetail }}</span>
        </div>
        <div class="state-actions">
          <button @click="handleRetry" class="state-btn state-btn-primary">
            <i class="fa fa-refresh"></i> 重新加载
          </button>
          <button @click="goBack" class="state-btn state-btn-secondary">
            <i class="fa fa-arrow-left"></i> 返回
          </button>
        </div>
      </div>
    </div>

    <!-- ============================================================
         错误状态 — 资源不存在
         ============================================================ -->
    <div v-else-if="store.isNotFound" class="state-overlay">
      <div class="state-content">
        <div class="state-icon notfound-icon">
          <i class="fa fa-file-o"></i>
        </div>
        <h2 class="state-title">{{ store.error?.title || '预览资源不存在' }}</h2>
        <p class="state-subtitle">{{ store.error?.message }}</p>
        <div class="state-actions">
          <button @click="handleRetry" class="state-btn state-btn-secondary">
            <i class="fa fa-refresh"></i> 刷新状态
          </button>
          <button @click="goBack" class="state-btn state-btn-primary">
            <i class="fa fa-arrow-left"></i> 返回
          </button>
        </div>
      </div>
    </div>

    <!-- ============================================================
         错误状态 — 网络错误
         ============================================================ -->
    <div v-else-if="store.error" class="state-overlay">
      <div class="state-content">
        <div class="state-icon network-error-icon">
          <i class="fa fa-wifi"></i>
        </div>
        <h2 class="state-title">{{ store.error.title }}</h2>
        <p class="state-subtitle">{{ store.error.message }}</p>
        <div class="state-actions">
          <button @click="handleRetry" class="state-btn state-btn-primary">
            <i class="fa fa-refresh"></i> 重新加载
          </button>
          <button @click="goBack" class="state-btn state-btn-secondary">
            <i class="fa fa-arrow-left"></i> 返回
          </button>
        </div>
      </div>
    </div>

    <!-- ============================================================
         正常预览状态 — 转换已完成
         ============================================================ -->
    <template v-else>
      <!-- 顶部工具栏 -->
      <PreviewToolbar
        :file-name="store.currentFile?.node_name || 'PDF 文档'"
        file-type="pdf"
        :scale="store.config.scale"
        :rotation="store.config.rotation"
        :current-page="store.config.currentPage"
        :total-pages="store.totalPages"
        :show-page-nav="true"
        :show-zoom="true"
        :show-rotate="true"
        :show-search="true"
        :show-fullscreen="true"
        :show-print="true"
        :show-share="true"
        :show-download="true"
        :show-thumbnails-toggle="true"
        :thumbnails-active="showThumbnails"
        :is-fullscreen="isFullscreen"
        :is-dark="store.config.colorMode === 'dark'"
        @close="goBack"
        @page-first="handlePageFirst"
        @page-prev="store.previousPage"
        @page-next="store.nextPage"
        @page-last="handlePageLast"
        @page-change="handlePageChange"
        @zoom-in="store.zoomIn"
        @zoom-out="store.zoomOut"
        @zoom-reset="store.resetZoom"
        @rotate-left="handleRotateLeft"
        @rotate-right="handleRotateRight"
        @search="toggleSearchPanel"
        @fullscreen="toggleFullscreen"
        @print="handlePrint"
        @share="handleShare"
        @download="handleDownload"
        @toggle-thumbnails="toggleThumbnailsPanel"
      />

      <!-- 主体区域 -->
      <div class="pdf-main-area">
        <!-- 左侧缩略图面板 -->
        <Transition name="panel-slide">
          <div v-if="showThumbnails" class="thumbnails-panel">
            <div class="thumbnails-header">
              <span class="thumbnails-title">页面缩略图</span>
              <button @click="showThumbnails = false" class="panel-close-btn">
                <i class="fa fa-times"></i>
              </button>
            </div>
            <div class="thumbnails-list" ref="thumbnailsListRef">
              <div
                v-for="page in store.totalPages"
                :key="page"
                @click="store.setCurrentPage(page); renderPage(page)"
                class="thumbnail-card"
                :class="{ active: store.config.currentPage === page }"
              >
                <div class="thumbnail-page">
                  <canvas
                    :ref="(el) => setThumbnailRef(el, page)"
                    class="thumbnail-canvas"
                  ></canvas>
                </div>
                <span class="thumbnail-label">{{ page }}</span>
              </div>
            </div>
          </div>
        </Transition>

        <!-- 中央 PDF 查看器 -->
        <div class="pdf-viewer-container" ref="viewerContainerRef">
          <div
            class="pdf-canvas-wrapper"
            :style="{
              transform: `scale(${store.config.scale}) rotate(${store.config.rotation}deg)`,
            }"
          >
            <canvas ref="pdfCanvasRef" class="pdf-canvas"></canvas>
          </div>
        </div>

        <!-- 右侧搜索面板 -->
        <Transition name="panel-slide-right">
          <div v-if="showSearch" class="search-panel">
            <div class="search-header">
              <span class="search-title">搜索</span>
              <button @click="showSearch = false" class="panel-close-btn">
                <i class="fa fa-times"></i>
              </button>
            </div>
            <div class="search-input-wrapper">
              <input
                v-model="searchQuery"
                @keyup.enter="handleSearch"
                placeholder="输入搜索关键词..."
                class="search-input"
                ref="searchInputRef"
              />
              <button @click="handleSearch" class="search-btn">
                <i class="fa fa-search"></i>
              </button>
            </div>
            <div class="search-results" v-if="searchResults.length > 0">
              <div class="search-count">找到 {{ searchResults.length }} 个结果</div>
              <div
                v-for="(result, index) in searchResults"
                :key="index"
                @click="navigateToSearchResult(result)"
                class="search-result-item"
                :class="{ active: activeSearchIndex === index }"
              >
                <div class="result-page">第 {{ result.pageNumber }} 页</div>
                <div class="result-text" v-safe-html="highlightMatch(result.text)"></div>
              </div>
            </div>
            <div v-else-if="searchQuery && searchPerformed" class="search-empty">
              未找到匹配结果
            </div>
          </div>
        </Transition>
      </div>

      <!-- 底部状态栏 -->
      <div class="pdf-status-bar">
        <div class="status-left">
          <button @click="toggleThumbnailsPanel" class="status-btn" :class="{ active: showThumbnails }">
            <i class="fa fa-th-large"></i>
          </button>
          <button @click="showFileInfo = !showFileInfo" class="status-btn" :class="{ active: showFileInfo }">
            <i class="fa fa-info-circle"></i>
          </button>
        </div>
        <div class="status-center">
          <span class="status-text">{{ store.fileSizeFormatted }}</span>
          <span class="status-divider">|</span>
          <span class="status-text">{{ store.config.currentPage }} / {{ store.totalPages }} 页</span>
          <span class="status-divider">|</span>
          <span class="status-text">{{ Math.round(store.config.scale * 100) }}%</span>
        </div>
        <div class="status-right">
          <select v-model="store.config.colorMode" class="color-mode-select" title="颜色模式">
            <option value="auto">自动</option>
            <option value="light">浅色</option>
            <option value="dark">深色</option>
            <option value="sepia">护眼</option>
          </select>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useOfficePreviewStore } from '@/stores/officePreviewStore'
import { useToastStore } from '@/stores/toastStore'
import PreviewToolbar from '@/components/preview/shared/PreviewToolbar.vue'

const route = useRoute()
const router = useRouter()
const store = useOfficePreviewStore()
const toastStore = useToastStore()

// ============================================================
// Refs
// ============================================================
const pdfCanvasRef = ref<HTMLCanvasElement | null>(null)
const viewerContainerRef = ref<HTMLDivElement | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)
const thumbnailsListRef = ref<HTMLDivElement | null>(null)

// ============================================================
// 状态
// ============================================================
const pdfDoc = ref<any>(null)
const isFullscreen = ref(false)
const showThumbnails = ref(true)
const showFileInfo = ref(false)
const showSearch = ref(false)
const searchQuery = ref('')
const searchPerformed = ref(false)
const searchResults = ref<any[]>([])
const activeSearchIndex = ref(-1)
const thumbnailRefs = new Map<number, HTMLCanvasElement>()

let pdfjsLib: any = null

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

  // 加载文档预览状态
  await store.loadDocument({
    node_id: fileId,
    node_name: fileName ? decodeURIComponent(fileName) : 'PDF 文档',
    node_type: 'FILE',
  })

  // 如果转换已完成，初始化 PDF.js 并渲染
  if (store.isCompleted) {
    await initPdfJs()
    await loadPdfIntoCanvas()
  }

  document.addEventListener('fullscreenchange', handleFullscreenChange)

  // 监听页码、缩放、旋转变化重新渲染
  watch(() => store.config.currentPage, () => { if (pdfDoc.value) renderPage(store.config.currentPage) })
  watch(() => store.config.scale, () => { if (pdfDoc.value) renderPage(store.config.currentPage) })
  watch(() => store.config.rotation, () => { if (pdfDoc.value) renderPage(store.config.currentPage) })
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  if (pdfDoc.value) {
    pdfDoc.value.destroy?.()
  }
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
      pdfjsLib.GlobalWorkerOptions.workerSrc =
        'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.4.120/pdf.worker.min.js'
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

    const loadingTask = pdfjsLib.getDocument(pdfUrl)
    pdfDoc.value = await loadingTask.promise

    await renderPage(store.config.currentPage)

    if (showThumbnails.value) {
      await nextTick()
      renderThumbnails()
    }
  } catch (err: any) {
    console.error('PDF 加载失败:', err)
    store.error = {
      title: 'PDF 渲染失败',
      message: err.message || '无法渲染 PDF 文件',
    }
  }
}

async function renderPage(pageNum: number): Promise<void> {
  if (!pdfDoc.value || !pdfCanvasRef.value) return

  try {
    const page = await pdfDoc.value.getPage(pageNum)
    const canvas = pdfCanvasRef.value
    const context = canvas.getContext('2d')!

    const viewport = page.getViewport({ scale: 1.0 })
    const maxWidth = viewerContainerRef.value?.clientWidth
      ? viewerContainerRef.value.clientWidth - 40
      : 800
    const fitScale = maxWidth / viewport.width
    const actualScale = fitScale * store.config.scale

    const scaledViewport = page.getViewport({ scale: actualScale })
    canvas.height = scaledViewport.height
    canvas.width = scaledViewport.width

    await page.render({ canvasContext: context, viewport: scaledViewport }).promise
    store.config.currentPage = pageNum
  } catch (err: any) {
    console.error('渲染 PDF 页面失败:', err)
  }
}

async function renderThumbnails(): Promise<void> {
  if (!pdfDoc.value) return

  const maxThumbnails = Math.min(pdfDoc.value.numPages, 50)
  for (let i = 1; i <= maxThumbnails; i++) {
    const canvas = thumbnailRefs.get(i)
    if (!canvas) continue
    try {
      const page = await pdfDoc.value.getPage(i)
      const viewport = page.getViewport({ scale: 0.2 })
      canvas.height = viewport.height
      canvas.width = viewport.width
      await page.render({ canvasContext: canvas.getContext('2d')!, viewport }).promise
    } catch { /* 缩略图渲染失败不影响主流程 */ }
  }
}

function setThumbnailRef(el: any, page: number): void {
  if (el) thumbnailRefs.set(page, el as HTMLCanvasElement)
}

// ============================================================
// 页面导航
// ============================================================

function handlePageFirst(): void {
  store.setCurrentPage(1)
  renderPage(1)
}

function handlePageLast(): void {
  const lastPage = pdfDoc.value?.numPages || 1
  store.setCurrentPage(lastPage)
  renderPage(lastPage)
}

function handlePageChange(page: number): void {
  store.setCurrentPage(page)
  renderPage(page)
}

// ============================================================
// 旋转
// ============================================================

function handleRotateLeft(): void {
  store.rotate(-90)
}

function handleRotateRight(): void {
  store.rotate(90)
}

// ============================================================
// 搜索
// ============================================================

function toggleSearchPanel(): void {
  showSearch.value = !showSearch.value
  if (showSearch.value) {
    nextTick(() => searchInputRef.value?.focus())
  }
}

function toggleThumbnailsPanel(): void {
  showThumbnails.value = !showThumbnails.value
}

async function handleSearch(): Promise<void> {
  if (!searchQuery.value.trim() || !pdfDoc.value) {
    searchResults.value = []
    searchPerformed.value = true
    return
  }

  searchResults.value = []
  activeSearchIndex.value = -1

  const query = searchQuery.value.trim().toLowerCase()
  const maxSearchPages = Math.min(pdfDoc.value.numPages, 100)

  for (let i = 1; i <= maxSearchPages; i++) {
    try {
      const page = await pdfDoc.value.getPage(i)
      const textContent = await page.getTextContent()
      const pageText = textContent.items.map((item: any) => item.str).join(' ')
      const lowerText = pageText.toLowerCase()
      let idx = lowerText.indexOf(query)

      while (idx !== -1) {
        const start = Math.max(0, idx - 30)
        const end = Math.min(pageText.length, idx + query.length + 30)
        searchResults.value.push({
          pageNumber: i,
          text: pageText.substring(start, end),
          matchIndex: idx - start,
          matchLength: query.length,
        })
        idx = lowerText.indexOf(query, idx + 1)
      }
    } catch { /* 跳过无法搜索的页面 */ }
  }

  searchPerformed.value = true
}

function navigateToSearchResult(result: any): void {
  store.setCurrentPage(result.pageNumber)
  renderPage(result.pageNumber)
  activeSearchIndex.value = searchResults.value.indexOf(result)
}

function highlightMatch(text: string): string {
  if (!searchQuery.value) return text
  const escaped = searchQuery.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return text.replace(new RegExp(`(${escaped})`, 'gi'), '<mark>$1</mark>')
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
// 打印
// ============================================================

function handlePrint(): void {
  if (!pdfDoc.value) return
  const pdfUrl = store.previewUrl
  if (!pdfUrl) return

  const printWindow = window.open(pdfUrl, '_blank')
  if (printWindow) {
    printWindow.addEventListener('load', () => printWindow.print())
  }
}

// ============================================================
// 分享
// ============================================================

function handleShare(): void {
  const fileName = store.currentFile?.node_name || ''
  if (navigator.share) {
    navigator.share({
      title: fileName,
      text: `查看文件: ${fileName}`,
      url: window.location.href,
    }).catch(() => copyShareLink())
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
  const fileName = store.currentFile?.node_name || 'document.pdf'
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
    if (store.isCompleted) {
      initPdfJs().then(() => loadPdfIntoCanvas())
    }
  })
}
</script>

<style scoped>
/* ============================================================
   整体布局
   ============================================================ */
.pdf-preview-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #525659;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.pdf-preview-page.dark-mode {
  background: #1a1a1a;
}

/* ============================================================
   状态覆盖层（加载/处理/错误/等待/未找到）
   参考 VideoPlayerView 的 loading/error overlay 设计
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

.state-spinner {
  margin-bottom: 20px;
}

.ring {
  width: 48px;
  height: 48px;
  border: 3px solid rgba(255, 255, 255, 0.15);
  border-top-color: #1677ff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto;
}

.state-icon {
  font-size: 48px;
  margin-bottom: 20px;
  opacity: 0.9;
}

.processing-icon {
  color: #1677ff;
}

.error-icon {
  color: #f56c6c;
}

.notfound-icon {
  color: #8c8c8c;
}

.pending-icon {
  color: #faad14;
}

.network-error-icon {
  color: #ff7875;
}

.state-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 10px;
  color: #fff;
}

.state-subtitle {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
  margin: 0 0 8px;
  line-height: 1.5;
}

.state-hint {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.35);
  margin: 16px 0 0;
}

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

.meta-label {
  font-weight: 600;
  color: rgba(255, 255, 255, 0.7);
}

.state-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 28px;
}

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

.state-btn-primary {
  background: #1677ff;
  color: #fff;
}

.state-btn-primary:hover {
  background: #4096ff;
}

.state-btn-secondary {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.15);
}

.state-btn-secondary:hover {
  background: rgba(255, 255, 255, 0.18);
}

.state-btn-outline {
  background: transparent;
  color: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.state-btn-outline:hover {
  color: rgba(255, 255, 255, 0.85);
  border-color: rgba(255, 255, 255, 0.35);
}

/* 进度条 */
.progress-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  max-width: 280px;
  margin-left: auto;
  margin-right: auto;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: #1677ff;
  border-radius: 3px;
  transition: width 0.5s ease;
}

.progress-text {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  min-width: 36px;
  text-align: right;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ============================================================
   主体区域
   ============================================================ */
.pdf-main-area {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
}

/* ============================================================
   缩略图面板
   ============================================================ */
.thumbnails-panel {
  width: 200px;
  background: #fff;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  z-index: 10;
}

.thumbnails-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.625rem 0.75rem;
  border-bottom: 1px solid #f1f5f9;
  background: #f8fafc;
}

.thumbnails-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #475569;
}

.panel-close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.5rem;
  height: 1.5rem;
  border: none;
  background: transparent;
  color: #94a3b8;
  border-radius: 0.25rem;
  cursor: pointer;
  font-size: 0.75rem;
}

.panel-close-btn:hover {
  background: #e2e8f0;
  color: #475569;
}

.thumbnails-list {
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.thumbnail-card {
  cursor: pointer;
  border: 2px solid transparent;
  border-radius: 0.375rem;
  padding: 0.25rem;
  transition: all 0.15s ease;
}

.thumbnail-card:hover {
  background: #f1f5f9;
}

.thumbnail-card.active {
  border-color: #4b6cb7;
  background: #eff6ff;
}

.thumbnail-page {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 0.25rem;
  overflow: hidden;
  display: flex;
  justify-content: center;
}

.thumbnail-canvas {
  max-width: 100%;
  height: auto;
}

.thumbnail-label {
  display: block;
  text-align: center;
  font-size: 0.7rem;
  color: #94a3b8;
  margin-top: 0.25rem;
}

/* ============================================================
   PDF 查看器
   ============================================================ */
.pdf-viewer-container {
  flex: 1;
  overflow: auto;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 1.5rem;
}

.pdf-canvas-wrapper {
  transform-origin: top center;
  transition: transform 0.2s ease;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.2);
  background: #fff;
}

.pdf-canvas {
  display: block;
}

/* ============================================================
   搜索面板
   ============================================================ */
.search-panel {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 300px;
  background: #fff;
  border-left: 1px solid #e2e8f0;
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  z-index: 20;
}

.search-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #f1f5f9;
}

.search-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #1e293b;
}

.search-input-wrapper {
  display: flex;
  padding: 0.75rem;
  gap: 0.5rem;
  border-bottom: 1px solid #f1f5f9;
}

.search-input {
  flex: 1;
  padding: 0.5rem 0.75rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
  font-size: 0.85rem;
  outline: none;
  transition: border-color 0.15s;
}

.search-input:focus {
  border-color: #4b6cb7;
  box-shadow: 0 0 0 2px rgba(75, 108, 183, 0.1);
}

.search-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border: none;
  background: #4b6cb7;
  color: #fff;
  border-radius: 0.375rem;
  cursor: pointer;
  flex-shrink: 0;
}

.search-btn:hover {
  background: #3b5ba7;
}

.search-results {
  flex: 1;
  overflow-y: auto;
}

.search-count {
  padding: 0.5rem 0.75rem;
  font-size: 0.8rem;
  color: #64748b;
  background: #f8fafc;
  border-bottom: 1px solid #f1f5f9;
}

.search-result-item {
  padding: 0.625rem 0.75rem;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.1s;
}

.search-result-item:hover {
  background: #f1f5f9;
}

.search-result-item.active {
  background: #eff6ff;
}

.result-page {
  font-size: 0.75rem;
  color: #4b6cb7;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.result-text {
  font-size: 0.8rem;
  color: #475569;
  line-height: 1.4;
}

.result-text :deep(mark) {
  background: #fef08a;
  padding: 0.125rem 0;
  border-radius: 0.125rem;
}

.search-empty {
  padding: 2rem;
  text-align: center;
  color: #94a3b8;
  font-size: 0.85rem;
}

/* ============================================================
   底部状态栏
   ============================================================ */
.pdf-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.375rem 0.75rem;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-top: 1px solid #e2e8f0;
  font-size: 0.75rem;
  color: #64748b;
  min-height: 32px;
  flex-shrink: 0;
}

.status-left,
.status-right {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.status-center {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.status-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.75rem;
  height: 1.75rem;
  border: none;
  background: transparent;
  color: #94a3b8;
  border-radius: 0.25rem;
  cursor: pointer;
  font-size: 0.75rem;
}

.status-btn:hover {
  background: #e2e8f0;
  color: #475569;
}

.status-btn.active {
  color: #4b6cb7;
}

.status-text {
  font-size: 0.75rem;
}

.status-divider {
  color: #cbd5e1;
}

.color-mode-select {
  font-size: 0.7rem;
  padding: 0.125rem 0.25rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.25rem;
  background: #fff;
  color: #64748b;
  cursor: pointer;
}

/* ============================================================
   过渡动画
   ============================================================ */
.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}

.panel-slide-enter-from,
.panel-slide-leave-to {
  transform: translateX(-100%);
  opacity: 0;
}

.panel-slide-right-enter-active,
.panel-slide-right-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}

.panel-slide-right-enter-from,
.panel-slide-right-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 768px) {
  .thumbnails-panel {
    width: 160px;
  }
  .search-panel {
    width: 100%;
  }
  .pdf-viewer-container {
    padding: 0.75rem;
  }
}

@media (max-width: 480px) {
  .thumbnails-panel {
    display: none;
  }
}
</style>