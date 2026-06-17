<template>
  <div class="pdf-preview-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="preview-loading">
      <div class="loading-spinner">
        <i class="fa fa-spinner fa-spin fa-3x"></i>
      </div>
      <p class="loading-text">正在加载PDF文档...</p>
    </div>

    <!-- PDF预览 -->
    <div v-else-if="pdfUrl" class="preview-content">
      <!-- 工具栏 -->
      <div class="preview-toolbar">
        <div class="toolbar-left">
          <span class="file-name truncate">{{ fileName }}</span>
        </div>
        <div class="toolbar-center">
          <button @click="previousPage" :disabled="currentPage <= 1" class="tool-btn">
            <i class="fa fa-chevron-left"></i>
          </button>
          <span class="page-info">
            <input
              type="number"
              v-model.number="inputPage"
              @keyup.enter="goToPage"
              @blur="goToPage"
              min="1"
              :max="totalPages"
              class="page-input"
            />
            <span>/ {{ totalPages }}</span>
          </span>
          <button @click="nextPage" :disabled="currentPage >= totalPages" class="tool-btn">
            <i class="fa fa-chevron-right"></i>
          </button>
        </div>
        <div class="toolbar-right">
          <button @click="zoomOut" class="tool-btn" title="缩小">
            <i class="fa fa-search-minus"></i>
          </button>
          <span class="zoom-level">{{ Math.round(scale * 100) }}%</span>
          <button @click="zoomIn" class="tool-btn" title="放大">
            <i class="fa fa-search-plus"></i>
          </button>
          <button @click="downloadPdf" class="tool-btn" title="下载">
            <i class="fa fa-download"></i>
          </button>
          <button @click="toggleFullscreen" class="tool-btn" title="全屏">
            <i :class="isFullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i>
          </button>
        </div>
      </div>

      <!-- PDF查看器 -->
      <div class="pdf-viewer" ref="pdfViewer">
        <div class="pdf-canvas-container" :style="{ transform: `scale(${scale})` }">
          <canvas ref="pdfCanvas"></canvas>
        </div>
      </div>

      <!-- PDF信息 -->
      <div class="pdf-info-bar">
        <div class="info-item">
          <i class="fa fa-file-pdf-o"></i>
          <span>PDF文档</span>
        </div>
        <div class="info-item">
          <i class="fa fa-file"></i>
          <span>{{ fileSize }}</span>
        </div>
        <div class="info-item">
          <i class="fa fa-clock-o"></i>
          <span>共 {{ totalPages }} 页</span>
        </div>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else class="preview-error">
      <i class="fa fa-exclamation-triangle fa-4x"></i>
      <h3>PDF加载失败</h3>
      <p>{{ errorMessage }}</p>
      <button @click="$emit('retry')" class="retry-btn">
        <i class="fa fa-refresh"></i> 重新加载
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'

const props = defineProps({
  fileUrl: {
    type: String,
    required: true
  },
  fileName: {
    type: String,
    default: ''
  },
  fileSize: {
    type: String,
    default: ''
  },
  fileExtension: {
    type: String,
    default: 'pdf'
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['retry', 'loaded', 'error', 'pageChanged'])

const pdfCanvas = ref(null)
const pdfViewer = ref(null)
const pdfDoc = ref(null)

// 状态
const currentPage = ref(1)
const totalPages = ref(0)
const scale = ref(1.0)
const inputPage = ref(1)
const isFullscreen = ref(false)
const errorMessage = ref('')
const pdfjsLib = ref(null)

// 计算PDF URL
const pdfUrl = computed(() => {
  if (!props.fileUrl) return ''
  if (props.fileUrl.startsWith('http://') || props.fileUrl.startsWith('https://')) {
    return props.fileUrl
  }
  if (props.fileUrl.startsWith('/')) {
    return import.meta.env.VITE_API_BASE_URL + props.fileUrl
  }
  return props.fileUrl
})

// 初始化PDF.js
const initPdfJs = async () => {
  try {
    // 动态加载 PDF.js
    if (!window.pdfjsLib) {
      const script = document.createElement('script')
      script.src = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.4.120/pdf.min.js'
      script.onload = () => {
        window.pdfjsLib.GlobalWorkerOptions.workerSrc =
          'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.4.120/pdf.worker.min.js'
        loadPdf()
      }
      document.head.appendChild(script)
    } else {
      loadPdf()
    }
  } catch (err) {
    errorMessage.value = '无法加载PDF预览组件'
    emit('error', err)
  }
}

// 加载PDF
const loadPdf = async () => {
  try {
    const loadingTask = window.pdfjsLib.getDocument(pdfUrl.value)
    pdfDoc.value = await loadingTask.promise
    totalPages.value = pdfDoc.value.numPages
    await renderPage(currentPage.value)
    emit('loaded', {
      numPages: totalPages.value
    })
  } catch (err) {
    errorMessage.value = '无法加载PDF文件，文件可能已损坏或格式不支持'
    emit('error', err)
  }
}

// 渲染页面
const renderPage = async (pageNum) => {
  if (!pdfDoc.value || !pdfCanvas.value) return

  try {
    const page = await pdfDoc.value.getPage(pageNum)
    const viewport = page.getViewport({ scale: scale.value })

    const canvas = pdfCanvas.value
    const context = canvas.getContext('2d')

    canvas.height = viewport.height
    canvas.width = viewport.width

    const renderContext = {
      canvasContext: context,
      viewport: viewport
    }

    await page.render(renderContext).promise
    currentPage.value = pageNum
    inputPage.value = pageNum
  } catch (err) {
    console.error('渲染PDF页面失败:', err)
  }
}

// 翻页
const previousPage = () => {
  if (currentPage.value > 1) {
    renderPage(currentPage.value - 1)
    emit('pageChanged', currentPage.value - 1)
  }
}

const nextPage = () => {
  if (currentPage.value < totalPages.value) {
    renderPage(currentPage.value + 1)
    emit('pageChanged', currentPage.value + 1)
  }
}

const goToPage = () => {
  let page = inputPage.value
  if (page < 1) page = 1
  if (page > totalPages.value) page = totalPages.value
  inputPage.value = page
  renderPage(page)
  emit('pageChanged', page)
}

// 缩放
const zoomIn = () => {
  scale.value = Math.min(scale.value + 0.25, 3)
  renderPage(currentPage.value)
}

const zoomOut = () => {
  scale.value = Math.max(scale.value - 0.25, 0.5)
  renderPage(currentPage.value)
}

// 全屏
const toggleFullscreen = async () => {
  if (!document.fullscreenElement) {
    await pdfViewer.value?.requestFullscreen()
    isFullscreen.value = true
  } else {
    await document.exitFullscreen()
    isFullscreen.value = false
  }
}

// 下载
const downloadPdf = () => {
  const link = document.createElement('a')
  link.href = pdfUrl.value
  link.download = props.fileName
  link.click()
}

// 监听文件URL变化
watch(() => props.fileUrl, () => {
  if (props.fileUrl) {
    initPdfJs()
  }
})

// 监听全屏变化
const handleFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
}

onMounted(() => {
  if (props.fileUrl) {
    initPdfJs()
  }
  document.addEventListener('fullscreenchange', handleFullscreenChange)
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  if (pdfDoc.value) {
    pdfDoc.value.destroy()
  }
})
</script>

<style scoped>
.pdf-preview-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #525659;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: white;
}

.loading-spinner {
  margin-bottom: 1rem;
}

.loading-text {
  font-size: 0.95rem;
  opacity: 0.9;
}

.preview-content {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.preview-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid #e2e8f0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  gap: 1rem;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
  flex: 1;
}

.file-name {
  font-weight: 600;
  color: #182848;
  font-size: 0.95rem;
}

.toolbar-center {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  border: none;
  background: #f1f5f9;
  border-radius: 0.5rem;
  cursor: pointer;
  transition: all 0.2s;
  color: #4b6cb7;
  font-size: 0.9rem;
}

.tool-btn:hover:not(:disabled) {
  background: #4b6cb7;
  color: white;
}

.tool-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
  color: #64748b;
}

.page-input {
  width: 50px;
  padding: 0.25rem 0.5rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.25rem;
  text-align: center;
  font-size: 0.9rem;
}

.zoom-level {
  font-size: 0.85rem;
  color: #64748b;
  font-weight: 600;
  min-width: 50px;
  text-align: center;
}

.pdf-viewer {
  flex: 1;
  overflow: auto;
  display: flex;
  justify-content: center;
  padding: 1rem;
  background: #525659;
}

.pdf-canvas-container {
  background: white;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
  transform-origin: top center;
}

.pdf-info-bar {
  display: flex;
  justify-content: center;
  gap: 2rem;
  padding: 0.75rem 1rem;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-top: 1px solid #e2e8f0;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: #64748b;
}

.info-item i {
  color: #dc2626;
}

.preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: white;
  text-align: center;
  padding: 2rem;
}

.preview-error h3 {
  margin: 1rem 0 0.5rem;
}

.preview-error p {
  opacity: 0.9;
  margin-bottom: 1.5rem;
}

.retry-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  background: white;
  color: #dc2626;
  border: none;
  padding: 0.75rem 1.5rem;
  border-radius: 2rem;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.retry-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(255, 255, 255, 0.3);
}
</style>
