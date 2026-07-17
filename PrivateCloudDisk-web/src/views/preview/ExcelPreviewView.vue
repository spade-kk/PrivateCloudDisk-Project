<!--
  ============================================================
  ExcelPreviewView.vue — Excel 电子表格预览页面
  ============================================================
  
  模拟电子表格软件的网格布局，功能包括：
    - 工作表切换（底部标签栏）
    - 行列导航（通过 PDF 页面模拟）
    - 工作表元数据展示
    - 缩放控制（适合查看数据密集型表格）
    - 冻结窗格效果（视觉提示）
  
  后端自动流水线已将 Excel 转换为 PDF（每页 = 一个工作表视图），
  前端使用 PDF.js 渲染。
  与视频播放器 (VideoPlayerView.vue) 对接模式一致。
  
  路由：/preview/excel/:fileId
  查询参数：name - 文件名
  ============================================================
-->
<template>
  <div class="excel-preview-page" :class="{ 'dark-mode': store.config.colorMode === 'dark' }">
    <!-- ============================================================
         状态覆盖层（加载/处理/等待/错误/未找到）
         ============================================================ -->
    <div v-if="store.loading" class="state-overlay">
      <div class="state-content">
        <div class="state-spinner"><div class="ring"></div></div>
        <h2 class="state-title">正在加载电子表格...</h2>
        <p class="state-subtitle">{{ store.currentFile?.node_name || '' }}</p>
      </div>
    </div>

    <div v-else-if="store.isProcessing" class="state-overlay">
      <div class="state-content">
        <div class="state-icon processing-icon"><i class="fa fa-cog fa-spin"></i></div>
        <h2 class="state-title">电子表格转换中</h2>
        <p class="state-subtitle">{{ store.previewInfo?.message || '后台正在将电子表格转换为可预览格式，请稍候...' }}</p>
        <div class="progress-wrapper" v-if="store.progress > 0">
          <div class="progress-bar"><div class="progress-fill" :style="{ width: store.progress + '%' }"></div></div>
          <span class="progress-text">{{ store.progress }}%</span>
        </div>
        <p class="state-hint">文件上传后自动触发转换流水线，预览就绪后此页面将自动刷新</p>
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
         类 Excel 布局：工具栏 + 表格视图 + 工作表标签
         ============================================================ -->
    <template v-else>
      <!-- 顶部工具栏（Excel 风格 — 隐藏旋转，突出工作表导航） -->
      <PreviewToolbar
        :file-name="store.currentFile?.node_name || '电子表格'"
        :file-type="store.documentType"
        :scale="store.config.scale"
        :rotation="store.config.rotation"
        :current-page="store.config.currentPage"
        :total-pages="store.totalPages"
        :show-page-nav="true"
        :show-zoom="true"
        :show-rotate="false"
        :show-search="true"
        :show-fullscreen="true"
        :show-print="true"
        :show-share="true"
        :show-download="true"
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
        @search="toggleSearchPanel"
        @fullscreen="toggleFullscreen"
        @print="handlePrint"
        @share="handleShare"
        @download="handleDownload"
      />

      <!-- 主体区域 -->
      <div class="excel-main-area">
        <!-- 左侧工作表列表面板 -->
        <Transition name="panel-slide">
          <div v-if="showSheetPanel" class="sheet-panel">
            <div class="sheet-panel-header">
              <span class="sheet-panel-title">
                <i class="fa fa-table"></i> 工作表
              </span>
              <button @click="showSheetPanel = false" class="panel-close-btn">
                <i class="fa fa-times"></i>
              </button>
            </div>

            <!-- 工作簿元数据 -->
            <div class="sheet-meta">
              <div class="meta-row" v-if="sheetList.length">
                <i class="fa fa-list-alt"></i>
                <span>{{ sheetList.length }} 个工作表</span>
              </div>
              <div class="meta-row" v-if="store.fileSizeFormatted">
                <i class="fa fa-hdd-o"></i>
                <span>{{ store.fileSizeFormatted }}</span>
              </div>
            </div>

            <!-- 工作表列表 -->
            <div class="sheet-list">
              <div
                v-for="(sheet, index) in sheetList"
                :key="index"
                @click="switchSheet(index)"
                class="sheet-item"
                :class="{ active: activeSheetIndex === index }"
              >
                <i class="fa fa-table sheet-icon"></i>
                <div class="sheet-info">
                  <span class="sheet-name" :title="sheet.name">{{ sheet.name }}</span>
                  <span class="sheet-dims" v-if="sheet.rowCount">
                    {{ sheet.rowCount }} 行 x {{ sheet.columnCount }} 列
                  </span>
                </div>
                <span class="sheet-badge" v-if="activeSheetIndex === index">
                  <i class="fa fa-check"></i>
                </span>
              </div>
            </div>
          </div>
        </Transition>

        <!-- 中央表格查看器 -->
        <div class="excel-viewer-container" ref="viewerContainerRef">
          <!-- 表格模拟区域 -->
          <div class="excel-grid-wrapper">
            <!-- 列标题栏 -->
            <div class="excel-column-headers" v-if="showGridHeaders">
              <div
                v-for="col in columnHeaders"
                :key="col"
                class="column-header"
                :style="{ width: colWidth + 'px' }"
              >
                {{ col }}
              </div>
            </div>

            <!-- 主体区域（PDF 画布 + 行号） -->
            <div class="excel-grid-body">
              <!-- 行号栏 -->
              <div class="excel-row-numbers" v-if="showGridHeaders">
                <div
                  v-for="row in rowHeaders"
                  :key="row"
                  class="row-number"
                  :style="{ height: rowHeight + 'px' }"
                >
                  {{ row }}
                </div>
              </div>

              <!-- PDF 画布 -->
              <div class="excel-canvas-area">
                <div
                  class="excel-canvas-wrapper"
                  :style="{ transform: `scale(${store.config.scale})`, transformOrigin: 'top left' }"
                >
                  <canvas ref="excelCanvasRef" class="excel-canvas"></canvas>
                </div>
              </div>
            </div>
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
                placeholder="在表格中搜索..."
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

      <!-- 底部工作表标签栏（Excel 风格） -->
      <div class="excel-sheet-tabs">
        <div class="sheet-tabs-left">
          <button
            @click="showSheetPanel = !showSheetPanel"
            class="sheet-tab-btn"
            :class="{ active: showSheetPanel }"
            title="工作表列表"
          >
            <i class="fa fa-bars"></i>
          </button>
        </div>
        <div class="sheet-tabs-scroll" ref="sheetTabsScrollRef">
          <button
            v-for="(sheet, index) in sheetList"
            :key="index"
            @click="switchSheet(index)"
            class="sheet-tab"
            :class="{ active: activeSheetIndex === index }"
            :ref="(el) => setSheetTabRef(el, index)"
          >
            <i class="fa fa-table"></i>
            <span>{{ sheet.name }}</span>
          </button>
        </div>
        <div class="sheet-tabs-right">
          <span class="sheet-info-text">
            {{ activeSheetIndex + 1 }} / {{ sheetList.length }}
          </span>
        </div>
      </div>

      <!-- 底部状态栏 -->
      <div class="excel-status-bar">
        <div class="status-left">
          <button
            @click="showGridHeaders = !showGridHeaders"
            class="status-btn"
            :class="{ active: showGridHeaders }"
            title="切换行列标题"
          >
            <i class="fa fa-th"></i>
          </button>
        </div>
        <div class="status-center">
          <span class="status-text" v-if="activeSheetMeta?.rowCount">
            {{ activeSheetMeta.rowCount }} 行 x {{ activeSheetMeta.columnCount }} 列
          </span>
          <span class="status-divider" v-if="activeSheetMeta?.rowCount">|</span>
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
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
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
const excelCanvasRef = ref<HTMLCanvasElement | null>(null)
const viewerContainerRef = ref<HTMLDivElement | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)
const sheetTabsScrollRef = ref<HTMLDivElement | null>(null)
const sheetTabRefs = new Map<number, HTMLElement>()

// ============================================================
// 状态
// ============================================================
const pdfDoc = ref<any>(null)
const isFullscreen = ref(false)
const showSheetPanel = ref(false)
const showSearch = ref(false)
const showGridHeaders = ref(true)
const searchQuery = ref('')
const searchPerformed = ref(false)
const searchResults = ref<any[]>([])
const activeSearchIndex = ref(-1)
const activeSheetIndex = ref(0)

let pdfjsLib: any = null

// ============================================================
// 计算属性
// ============================================================

/** 工作表列表 */
const sheetList = computed(() => {
  const sheets = store.metadata?.sheets || []
  if (sheets.length === 0) {
    // 降级：使用 PDF 页面模拟工作表
    return Array.from({ length: store.totalPages || 1 }, (_, i) => ({
      name: `工作表 ${i + 1}`,
      index: i,
      rowCount: undefined,
      columnCount: undefined,
    }))
  }
  return sheets
})

/** 当前工作表元数据 */
const activeSheetMeta = computed(() => {
  return sheetList.value[activeSheetIndex.value] || null
})

/** 列标题（字母 A-Z, AA-ZZ） */
const columnHeaders = computed(() => {
  const count = activeSheetMeta.value?.columnCount || 26
  const headers: string[] = []
  for (let i = 0; i < Math.min(count, 52); i++) {
    if (i < 26) {
      headers.push(String.fromCharCode(65 + i))
    } else {
      headers.push(`A${String.fromCharCode(65 + i - 26)}`)
    }
  }
  return headers
})

/** 行标题 */
const rowHeaders = computed(() => {
  const count = activeSheetMeta.value?.rowCount || 50
  return Array.from({ length: Math.min(count, 100) }, (_, i) => i + 1)
})

/** 列宽 */
const colWidth = 80

/** 行高 */
const rowHeight = 24

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
    node_name: fileName ? decodeURIComponent(fileName) : '电子表格',
    node_type: 'FILE',
  })

  if (store.isCompleted) {
    await initPdfJs()
    await loadPdfIntoCanvas()
  }

  document.addEventListener('fullscreenchange', handleFullscreenChange)

  watch(() => store.config.currentPage, () => { if (pdfDoc.value) renderSheet(store.config.currentPage) })
  watch(() => store.config.scale, () => { if (pdfDoc.value) renderSheet(store.config.currentPage) })
  watch(activeSheetIndex, (newIdx) => {
    // 切换工作表时更新页码
    store.setCurrentPage(newIdx + 1)
    if (pdfDoc.value) renderSheet(newIdx + 1)
  })
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
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

    const loadingTask = pdfjsLib.getDocument(pdfUrl)
    pdfDoc.value = await loadingTask.promise
    await renderSheet(store.config.currentPage)
  } catch (err: any) {
    console.error('PDF 加载失败:', err)
    store.error = { title: 'PDF 渲染失败', message: err.message || '无法渲染电子表格' }
  }
}

async function renderSheet(pageNum: number): Promise<void> {
  if (!pdfDoc.value || !excelCanvasRef.value) return

  try {
    const page = await pdfDoc.value.getPage(pageNum)
    const canvas = excelCanvasRef.value
    const context = canvas.getContext('2d')!

    const viewport = page.getViewport({ scale: 1.0 })
    const maxWidth = viewerContainerRef.value?.clientWidth
      ? viewerContainerRef.value.clientWidth - 60
      : 1000
    const fitScale = maxWidth / viewport.width
    const actualScale = fitScale * store.config.scale

    const scaledViewport = page.getViewport({ scale: actualScale })
    canvas.height = scaledViewport.height
    canvas.width = scaledViewport.width

    await page.render({ canvasContext: context, viewport: scaledViewport }).promise
    store.config.currentPage = pageNum
  } catch (err: any) {
    console.error('渲染 Excel 工作表失败:', err)
  }
}

// ============================================================
// 工作表切换
// ============================================================

function switchSheet(index: number): void {
  activeSheetIndex.value = index
  scrollSheetTabIntoView(index)
}

function scrollSheetTabIntoView(index: number): void {
  nextTick(() => {
    const tab = sheetTabRefs.get(index)
    if (tab) {
      tab.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' })
    }
  })
}

function setSheetTabRef(el: any, index: number): void {
  if (el) sheetTabRefs.set(index, el as HTMLElement)
}

// ============================================================
// 页面导航
// ============================================================

function handlePageFirst(): void {
  store.setCurrentPage(1)
  renderSheet(1)
}

function handlePageLast(): void {
  const lastPage = pdfDoc.value?.numPages || 1
  store.setCurrentPage(lastPage)
  renderSheet(lastPage)
}

function handlePageChange(page: number): void {
  store.setCurrentPage(page)
  renderSheet(page)
}

// ============================================================
// 搜索
// ============================================================

function toggleSearchPanel(): void {
  showSearch.value = !showSearch.value
  if (showSearch.value) nextTick(() => searchInputRef.value?.focus())
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
  const maxSearchPages = Math.min(pdfDoc.value.numPages, 50)

  for (let i = 1; i <= maxSearchPages; i++) {
    try {
      const page = await pdfDoc.value.getPage(i)
      const textContent = await page.getTextContent()
      const pageText = textContent.items.map((item: any) => item.str).join(' ')
      const lowerText = pageText.toLowerCase()
      let idx = lowerText.indexOf(query)

      while (idx !== -1) {
        const start = Math.max(0, idx - 20)
        const end = Math.min(pageText.length, idx + query.length + 20)
        searchResults.value.push({
          pageNumber: i,
          text: pageText.substring(start, end),
          matchIndex: idx - start,
          matchLength: query.length,
        })
        idx = lowerText.indexOf(query, idx + 1)
      }
    } catch { /* skip */ }
  }

  searchPerformed.value = true
}

function navigateToSearchResult(result: any): void {
  store.setCurrentPage(result.pageNumber)
  renderSheet(result.pageNumber)
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
    navigator.share({ title: fileName, text: `查看电子表格: ${fileName}`, url: window.location.href }).catch(() => copyShareLink())
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
  const fileName = store.currentFile?.node_name || 'spreadsheet'
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
.excel-preview-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f8fafc;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.excel-preview-page.dark-mode {
  background: #1a1a1a;
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
.excel-main-area {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
}

/* ============================================================
   工作表列表面板
   ============================================================ */
.sheet-panel {
  width: 240px;
  background: #fff;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  z-index: 10;
}

.sheet-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #f1f5f9;
  background: #f8fafc;
}

.sheet-panel-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 0.5rem;
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

.panel-close-btn:hover { background: #e2e8f0; color: #475569; }

.sheet-meta {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #f1f5f9;
  background: #fafbfc;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
  color: #64748b;
}

.meta-row i {
  width: 14px;
  text-align: center;
  color: #94a3b8;
}

.sheet-list {
  flex: 1;
  overflow-y: auto;
}

.sheet-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.625rem 0.75rem;
  cursor: pointer;
  transition: background 0.1s;
  border-left: 3px solid transparent;
}

.sheet-item:hover {
  background: #f1f5f9;
}

.sheet-item.active {
  background: #eff6ff;
  border-left-color: #4b6cb7;
}

.sheet-icon {
  color: #22c55e;
  font-size: 0.85rem;
  flex-shrink: 0;
}

.sheet-info {
  flex: 1;
  min-width: 0;
}

.sheet-name {
  display: block;
  font-size: 0.8rem;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sheet-dims {
  font-size: 0.7rem;
  color: #94a3b8;
}

.sheet-badge {
  color: #4b6cb7;
  font-size: 0.7rem;
}

/* ============================================================
   Excel 查看器
   ============================================================ */
.excel-viewer-container {
  flex: 1;
  overflow: auto;
  background: #e8eaed;
}

.excel-grid-wrapper {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

/* 列标题 */
.excel-column-headers {
  display: flex;
  background: #f1f5f9;
  border-bottom: 1px solid #d1d5db;
  position: sticky;
  top: 0;
  z-index: 5;
  margin-left: 46px;
}

.column-header {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.7rem;
  font-weight: 600;
  color: #64748b;
  height: 24px;
  border-right: 1px solid #e2e8f0;
  flex-shrink: 0;
  background: #f1f5f9;
}

/* 主体区域 */
.excel-grid-body {
  display: flex;
}

/* 行号 */
.excel-row-numbers {
  display: flex;
  flex-direction: column;
  background: #f1f5f9;
  border-right: 1px solid #d1d5db;
  flex-shrink: 0;
  width: 46px;
  position: sticky;
  left: 0;
  z-index: 5;
}

.row-number {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.7rem;
  color: #64748b;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
}

/* PDF 画布 */
.excel-canvas-area {
  flex: 1;
  padding: 0;
  background: #fff;
}

.excel-canvas-wrapper {
  transition: transform 0.2s ease;
}

.excel-canvas {
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

.search-title { font-size: 0.9rem; font-weight: 600; color: #1e293b; }

.search-input-wrapper { display: flex; padding: 0.75rem; gap: 0.5rem; border-bottom: 1px solid #f1f5f9; }

.search-input {
  flex: 1;
  padding: 0.5rem 0.75rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.375rem;
  font-size: 0.85rem;
  outline: none;
  transition: border-color 0.15s;
}

.search-input:focus { border-color: #4b6cb7; box-shadow: 0 0 0 2px rgba(75, 108, 183, 0.1); }

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

.search-btn:hover { background: #3b5ba7; }

.search-results { flex: 1; overflow-y: auto; }
.search-count { padding: 0.5rem 0.75rem; font-size: 0.8rem; color: #64748b; background: #f8fafc; border-bottom: 1px solid #f1f5f9; }

.search-result-item {
  padding: 0.625rem 0.75rem;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.1s;
}

.search-result-item:hover { background: #f1f5f9; }
.search-result-item.active { background: #eff6ff; }

.result-page { font-size: 0.75rem; color: #4b6cb7; font-weight: 600; margin-bottom: 0.25rem; }
.result-text { font-size: 0.8rem; color: #475569; line-height: 1.4; }
.result-text :deep(mark) { background: #fef08a; padding: 0.125rem 0; border-radius: 0.125rem; }

.search-empty { padding: 2rem; text-align: center; color: #94a3b8; font-size: 0.85rem; }

/* ============================================================
   工作表标签栏（Excel 风格）
   ============================================================ */
.excel-sheet-tabs {
  display: flex;
  align-items: center;
  background: #fff;
  border-top: 1px solid #e2e8f0;
  flex-shrink: 0;
  height: 36px;
}

.sheet-tabs-left {
  display: flex;
  align-items: center;
  border-right: 1px solid #e2e8f0;
  flex-shrink: 0;
}

.sheet-tab-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 100%;
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  font-size: 0.75rem;
}

.sheet-tab-btn:hover { background: #f1f5f9; color: #475569; }
.sheet-tab-btn.active { color: #4b6cb7; }

.sheet-tabs-scroll {
  flex: 1;
  display: flex;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
}

.sheet-tabs-scroll::-webkit-scrollbar { height: 0; }

.sheet-tab {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0 0.875rem;
  height: 100%;
  border: none;
  border-right: 1px solid #e2e8f0;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 0.75rem;
  white-space: nowrap;
  transition: all 0.1s;
  flex-shrink: 0;
}

.sheet-tab:hover {
  background: #f1f5f9;
}

.sheet-tab.active {
  background: #fff;
  color: #22c55e;
  border-bottom: 2px solid #22c55e;
  font-weight: 600;
}

.sheet-tab i {
  font-size: 0.7rem;
}

.sheet-tabs-right {
  display: flex;
  align-items: center;
  padding: 0 0.75rem;
  flex-shrink: 0;
  border-left: 1px solid #e2e8f0;
}

.sheet-info-text {
  font-size: 0.7rem;
  color: #94a3b8;
}

/* ============================================================
   底部状态栏
   ============================================================ */
.excel-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.25rem 0.75rem;
  background: #fff;
  border-top: 1px solid #e2e8f0;
  font-size: 0.7rem;
  color: #64748b;
  min-height: 28px;
  flex-shrink: 0;
}

.status-left, .status-right { display: flex; align-items: center; gap: 0.25rem; }
.status-center { display: flex; align-items: center; gap: 0.5rem; }

.status-btn {
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
  font-size: 0.7rem;
}

.status-btn:hover { background: #e2e8f0; color: #475569; }
.status-btn.active { color: #4b6cb7; }

.status-text { font-size: 0.7rem; }
.status-divider { color: #cbd5e1; }

.color-mode-select {
  font-size: 0.65rem;
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
.panel-slide-enter-active, .panel-slide-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}

.panel-slide-enter-from, .panel-slide-leave-to {
  transform: translateX(-100%);
  opacity: 0;
}

.panel-slide-right-enter-active, .panel-slide-right-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}

.panel-slide-right-enter-from, .panel-slide-right-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 768px) {
  .sheet-panel { width: 200px; }
  .search-panel { width: 100%; }
  .excel-column-headers { margin-left: 36px; }
  .excel-row-numbers { width: 36px; }
}

@media (max-width: 480px) {
  .sheet-panel { display: none; }
}
</style>