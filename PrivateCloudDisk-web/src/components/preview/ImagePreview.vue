<template>
  <div class="image-preview-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="preview-loading">
      <div class="loading-spinner">
        <i class="fa fa-spinner fa-spin fa-3x"></i>
      </div>
      <p class="loading-text">正在加载图片...</p>
    </div>

    <!-- 图片预览 -->
    <div v-else-if="imageUrl" class="preview-content" ref="previewContainer">
      <!-- 工具栏 -->
      <div class="preview-toolbar">
        <div class="toolbar-left">
          <span class="file-name truncate">{{ fileName }}</span>
        </div>
        <div class="toolbar-right">
          <button @click="zoomIn" class="tool-btn" title="放大">
            <i class="fa fa-search-plus"></i>
          </button>
          <button @click="zoomOut" class="tool-btn" title="缩小">
            <i class="fa fa-search-minus"></i>
          </button>
          <button @click="resetZoom" class="tool-btn" title="重置">
            <i class="fa fa-refresh"></i>
          </button>
          <button @click="rotateImage" class="tool-btn" title="旋转">
            <i class="fa fa-rotate-right"></i>
          </button>
          <button @click="toggleFullscreen" class="tool-btn" title="全屏">
            <i :class="isFullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i>
          </button>
        </div>
      </div>

      <!-- 图片容器 -->
      <div class="image-viewport" ref="viewport">
        <img
          ref="imageRef"
          :src="imageUrl"
          :style="imageStyle"
          @load="onImageLoad"
          @error="onImageError"
          class="preview-image"
          :alt="fileName"
        />
      </div>

      <!-- 缩放信息 -->
      <div class="zoom-info">
        <span>{{ Math.round(zoom * 100) }}%</span>
      </div>

      <!-- 文件信息 -->
      <div class="file-info-bar">
        <div class="info-item">
          <i class="fa fa-image"></i>
          <span>{{ fileExtension.toUpperCase() }}</span>
        </div>
        <div class="info-item">
          <i class="fa fa-expand"></i>
          <span>{{ naturalWidth }} × {{ naturalHeight }}</span>
        </div>
        <div class="info-item">
          <i class="fa fa-file"></i>
          <span>{{ fileSize }}</span>
        </div>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else class="preview-error">
      <i class="fa fa-exclamation-triangle fa-4x"></i>
      <h3>图片加载失败</h3>
      <p>{{ errorMessage }}</p>
      <button @click="$emit('retry')" class="retry-btn">
        <i class="fa fa-refresh"></i> 重新加载
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'

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
    default: ''
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['retry', 'loaded', 'error'])

const imageRef = ref(null)
const viewport = ref(null)
const previewContainer = ref(null)

// 状态
const zoom = ref(1)
const rotation = ref(0)
const isFullscreen = ref(false)
const naturalWidth = ref(0)
const naturalHeight = ref(0)
const errorMessage = ref('')

// 计算样式
const imageStyle = computed(() => ({
  transform: `scale(${zoom.value}) rotate(${rotation.value}deg)`,
  transition: 'transform 0.3s ease',
  cursor: 'zoom-in'
}))

// 计算图片URL（处理 Blob URL）
const imageUrl = computed(() => {
  if (!props.fileUrl) return ''
  // 如果已经是完整 URL，直接返回
  if (props.fileUrl.startsWith('http://') || props.fileUrl.startsWith('https://')) {
    return props.fileUrl
  }
  // 如果是相对路径，添加 API 前缀
  if (props.fileUrl.startsWith('/')) {
    return import.meta.env.VITE_API_BASE_URL + props.fileUrl
  }
  // 其他情况作为 Blob URL 处理
  return props.fileUrl
})

// 方法
const onImageLoad = (event) => {
  const img = event.target
  naturalWidth.value = img.naturalWidth
  naturalHeight.value = img.naturalHeight
  emit('loaded', {
    width: img.naturalWidth,
    height: img.naturalHeight
  })
}

const onImageError = (error) => {
  errorMessage.value = '无法加载图片，文件可能已损坏或格式不支持'
  emit('error', error)
}

const zoomIn = () => {
  zoom.value = Math.min(zoom.value + 0.25, 5)
}

const zoomOut = () => {
  zoom.value = Math.max(zoom.value - 0.25, 0.1)
}

const resetZoom = () => {
  zoom.value = 1
  rotation.value = 0
}

const rotateImage = () => {
  rotation.value = (rotation.value + 90) % 360
}

const toggleFullscreen = async () => {
  if (!document.fullscreenElement) {
    await previewContainer.value?.requestFullscreen()
    isFullscreen.value = true
  } else {
    await document.exitFullscreen()
    isFullscreen.value = false
  }
}

// 监听 ESC 键退出全屏
const handleKeydown = (e) => {
  if (e.key === 'Escape' && isFullscreen.value) {
    toggleFullscreen()
  }
  if (e.key === '+' || e.key === '=') {
    zoomIn()
  }
  if (e.key === '-') {
    zoomOut()
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.image-preview-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #4b6cb7;
}

.loading-spinner {
  margin-bottom: 1rem;
}

.loading-text {
  font-size: 0.95rem;
  opacity: 0.8;
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
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
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

.toolbar-right {
  display: flex;
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

.tool-btn:hover {
  background: #4b6cb7;
  color: white;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(75, 108, 183, 0.3);
}

.image-viewport {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
  padding: 1.5rem;
  position: relative;
}

.preview-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 0.5rem;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.zoom-info {
  position: absolute;
  bottom: 5rem;
  right: 1.5rem;
  background: rgba(255, 255, 255, 0.95);
  padding: 0.5rem 1rem;
  border-radius: 2rem;
  font-size: 0.85rem;
  color: #4b6cb7;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.file-info-bar {
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
  color: #4b6cb7;
}

.preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #fbbf24;
  text-align: center;
  padding: 2rem;
}

.preview-error h3 {
  margin: 1rem 0 0.5rem;
  color: #182848;
}

.preview-error p {
  color: #64748b;
  margin-bottom: 1.5rem;
}

.retry-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  background: linear-gradient(90deg, #4b6cb7 0%, #182848 100%);
  color: white;
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
  box-shadow: 0 6px 20px rgba(75, 108, 183, 0.4);
}
</style>
