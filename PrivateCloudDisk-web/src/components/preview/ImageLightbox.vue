<template>
  <Teleport to="body">
    <Transition name="lightbox-fade">
      <div
        v-if="visible"
        class="image-lightbox"
        @wheel.prevent="onWheel"
        @mousedown="onMouseDown"
        @mousemove="onMouseMove"
        @mouseup="onMouseUp"
        @mouseleave="onMouseUp"
        @dblclick="onDoubleClick"
      >
        <!-- 黑色蒙版 -->
        <div class="lightbox-mask" @click="close"></div>

        <!-- 图片加载占位 -->
        <div v-if="loading" class="lightbox-loading">
          <i class="fa fa-spinner fa-spin"></i>
          <span>加载中...</span>
        </div>

        <!-- 加载错误 -->
        <div v-if="error" class="lightbox-error">
          <i class="fa fa-exclamation-triangle"></i>
          <span>{{ error }}</span>
          <button @click="retry" class="retry-btn">重试</button>
        </div>

        <!-- 图片容器 -->
        <div
          v-show="!loading && !error"
          class="lightbox-image-container"
          :style="containerStyle"
        >
          <img
            ref="imageRef"
            :src="objectUrl || undefined"
            :alt="fileName"
            class="lightbox-image"
            :style="imageStyle"
            @load="onImageLoad"
            @error="onImageError"
            draggable="false"
          />
        </div>

        <!-- 顶部工具栏 -->
        <div class="lightbox-toolbar-top">
          <div class="toolbar-left">
            <span class="file-name">{{ fileName }}</span>
            <span v-if="imageSize" class="image-size">{{ imageSize }}</span>
          </div>
        </div>

        <!-- 底部工具栏 -->
        <div class="lightbox-toolbar-bottom">
          <div class="toolbar-group">
            <button @click="zoomOut" class="tool-btn" title="缩小 (-)">
              <i class="fa fa-search-minus"></i>
            </button>
            <span class="zoom-level">{{ Math.round(scale * 100) }}%</span>
            <button @click="zoomIn" class="tool-btn" title="放大 (+)">
              <i class="fa fa-search-plus"></i>
            </button>
          </div>

          <div class="toolbar-divider"></div>

          <div class="toolbar-group">
            <button @click="resetZoom" class="tool-btn" title="原始大小 (0)">
              <i class="fa fa-arrows-alt"></i>
            </button>
            <button @click="fitToScreen" class="tool-btn" title="适应屏幕 (F)">
              <i class="fa fa-expand"></i>
            </button>
          </div>

          <div class="toolbar-divider"></div>

          <div class="toolbar-group">
            <button @click="rotateLeft" class="tool-btn" title="左旋转 (L)">
              <i class="fa fa-rotate-left"></i>
            </button>
            <button @click="rotateRight" class="tool-btn" title="右旋转 (R)">
              <i class="fa fa-rotate-right"></i>
            </button>
          </div>

          <div class="toolbar-divider"></div>

          <div class="toolbar-group">
            <button @click="toggleFullscreen" class="tool-btn" title="全屏">
              <i :class="isFullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i>
            </button>
          </div>
        </div>

        <!-- 关闭按钮 -->
        <button class="lightbox-close-btn" @click="close" title="关闭 (ESC)">
          <i class="fa fa-times"></i>
        </button>

        <!-- 上一个 / 下一个 -->
        <button
          v-if="hasPrev"
          class="lightbox-nav-btn nav-prev"
          @click.stop="$emit('prev')"
          title="上一张"
        >
          <i class="fa fa-chevron-left"></i>
        </button>
        <button
          v-if="hasNext"
          class="lightbox-nav-btn nav-next"
          @click.stop="$emit('next')"
          title="下一张"
        >
          <i class="fa fa-chevron-right"></i>
        </button>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { loadOriginalImage, imageCache } from '@/utils/imageCache'
import { getPreviewErrorMessage } from '@/api/modules/previewContent'

const props = withDefaults(
  defineProps<{
    visible: boolean
    fileId: string
    fileName: string
    /** 公开仓库预览的空间上下文；控制台调用不传，保持原行为。 */
    spaceId?: string
    hasPrev?: boolean
    hasNext?: boolean
  }>(),
  {
    hasPrev: false,
    hasNext: false,
  },
)

const emit = defineEmits<{
  close: []
  prev: []
  next: []
}>()

// ---- 状态 ----
const imageRef = ref<HTMLImageElement | null>(null)
const loading = ref(true)
const error = ref('')
const objectUrl = ref<string | null>(null)
const scale = ref(1)
const rotation = ref(0)
const position = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0 })
const isFullscreen = ref(false)
const imageDimensions = ref({ width: 0, height: 0 })
const imageSize = ref('')

// ---- 计算属性 ----
const imageStyle = computed(() => ({
  transform: `translate(${position.value.x}px, ${position.value.y}px) scale(${scale.value}) rotate(${rotation.value}deg)`,
  transition: isDragging.value ? 'none' : 'transform 0.3s cubic-bezier(0.25, 0.46, 0.45, 0.94)',
  cursor: scale.value > 1 ? (isDragging.value ? 'grabbing' : 'grab') : 'default',
}))

const containerStyle = computed(() => ({
  cursor: scale.value > 1 ? (isDragging.value ? 'grabbing' : 'grab') : 'default',
}))

// ---- 缩放 ----
const MIN_SCALE = 0.1
const MAX_SCALE = 10
const ZOOM_STEP = 0.25

function zoomIn() {
  setScale(scale.value + ZOOM_STEP)
}

function zoomOut() {
  setScale(scale.value - ZOOM_STEP)
}

function setScale(newScale: number) {
  scale.value = Math.min(MAX_SCALE, Math.max(MIN_SCALE, newScale))
  if (scale.value <= 1) {
    position.value = { x: 0, y: 0 }
  }
}

function resetZoom() {
  scale.value = 1
  rotation.value = 0
  position.value = { x: 0, y: 0 }
}

function fitToScreen() {
  scale.value = 1
  position.value = { x: 0, y: 0 }
}

// ---- 旋转 ----
function rotateLeft() {
  rotation.value = (rotation.value - 90) % 360
}

function rotateRight() {
  rotation.value = (rotation.value + 90) % 360
}

// ---- 拖拽 ----
function onMouseDown(e: MouseEvent) {
  if (scale.value <= 1) return
  isDragging.value = true
  dragStart.value = {
    x: e.clientX - position.value.x,
    y: e.clientY - position.value.y,
  }
}

function onMouseMove(e: MouseEvent) {
  if (!isDragging.value) return
  position.value = {
    x: e.clientX - dragStart.value.x,
    y: e.clientY - dragStart.value.y,
  }
}

function onMouseUp() {
  isDragging.value = false
}

// ---- 滚轮缩放 ----
function onWheel(e: WheelEvent) {
  const delta = e.deltaY > 0 ? -ZOOM_STEP : ZOOM_STEP
  setScale(scale.value + delta)
}

// ---- 双击 ----
function onDoubleClick() {
  if (scale.value > 1) {
    fitToScreen()
  } else {
    setScale(2)
  }
}

// ---- 全屏 ----
async function toggleFullscreen() {
  if (!document.fullscreenElement) {
    await document.documentElement.requestFullscreen()
    isFullscreen.value = true
  } else {
    await document.exitFullscreen()
    isFullscreen.value = false
  }
}

// ---- 图片加载（异步 axios blob） ----
async function loadImage() {
  if (!props.fileId) return

  loading.value = true
  error.value = ''

  try {
    // AUDIT FIX [4.4]: 灯箱加载原文件而非 large 有损缩略图，修复图片点击后无法获得大图预览的问题。
    const url = await loadOriginalImage(props.fileId, props.spaceId)
    objectUrl.value = url
    loading.value = false
    error.value = ''

    // 获取图片尺寸
    if (imageRef.value) {
      imageDimensions.value = {
        width: imageRef.value.naturalWidth,
        height: imageRef.value.naturalHeight,
      }
      imageSize.value = `${imageRef.value.naturalWidth} × ${imageRef.value.naturalHeight}`
    }
  } catch (loadError) {
    loading.value = false
    error.value = getPreviewErrorMessage(loadError)
  }
}

function onImageLoad() {
  // 图片 DOM 已就绪，读取尺寸
  if (imageRef.value) {
    imageDimensions.value = {
      width: imageRef.value.naturalWidth,
      height: imageRef.value.naturalHeight,
    }
    imageSize.value = `${imageRef.value.naturalWidth} × ${imageRef.value.naturalHeight}`
  }
}

function onImageError() {
  loading.value = false
  error.value = '图片数据无法解析，请确认文件内容完整后重试'
}

function retry() {
  // 清除旧缓存，强制重新加载
  imageCache.evictOriginal(props.fileId, props.spaceId)
  loadImage()
}

// ---- 关闭 ----
function close() {
  emit('close')
}

// ---- 键盘快捷键 ----
function onKeydown(e: KeyboardEvent) {
  if (!props.visible) return

  switch (e.key) {
    case 'Escape':
      close()
      break
    case '+':
    case '=':
      zoomIn()
      break
    case '-':
      zoomOut()
      break
    case '0':
      resetZoom()
      break
    case 'ArrowLeft':
      if (props.hasPrev) emit('prev')
      break
    case 'ArrowRight':
      if (props.hasNext) emit('next')
      break
    case 'r':
    case 'R':
      rotateRight()
      break
    case 'l':
    case 'L':
      rotateLeft()
      break
    case 'f':
    case 'F':
      fitToScreen()
      break
  }
}

// ---- 生命周期 ----
onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  document.addEventListener('fullscreenchange', () => {
    isFullscreen.value = !!document.fullscreenElement
  })
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
})

// 当 visible 变化时加载
watch(
  () => props.visible,
  (val) => {
    if (val) {
      loading.value = true
      error.value = ''
      objectUrl.value = null
      scale.value = 1
      rotation.value = 0
      position.value = { x: 0, y: 0 }
      loadImage()
    }
  },
  { immediate: true },
)

// 当 fileId 变化时重新加载
watch(
  () => props.fileId,
  () => {
    loading.value = true
    error.value = ''
    objectUrl.value = null
    scale.value = 1
    rotation.value = 0
    position.value = { x: 0, y: 0 }
    loadImage()
  },
)
</script>

<style scoped>
.image-lightbox {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
  -webkit-user-select: none;
}

/* ---- 蒙版 ---- */
.lightbox-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.92);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

/* ---- 过渡动画 ---- */
.lightbox-fade-enter-active,
.lightbox-fade-leave-active {
  transition: opacity 0.3s ease;
}

.lightbox-fade-enter-from,
.lightbox-fade-leave-to {
  opacity: 0;
}

/* ---- 加载状态 ---- */
.lightbox-loading {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
}

.lightbox-loading i {
  font-size: 32px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---- 错误状态 ---- */
.lightbox-error {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
}

.lightbox-error i {
  font-size: 40px;
  color: rgba(239, 68, 68, 0.8);
}

.retry-btn {
  padding: 6px 20px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.2s;
}

.retry-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

/* ---- 图片容器 ---- */
.lightbox-image-container {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 90vw;
  max-height: 85vh;
}

.lightbox-image {
  max-width: 90vw;
  max-height: 85vh;
  object-fit: contain;
  border-radius: 2px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
  will-change: transform;
}

/* ---- 顶部工具栏 ---- */
.lightbox-toolbar-top {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.5), transparent);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-name {
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  font-weight: 500;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.image-size {
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
  font-family: 'SF Mono', 'Fira Code', monospace;
}

/* ---- 底部工具栏 ---- */
.lightbox-toolbar-bottom {
  position: absolute;
  bottom: 20px;
  left: 50%;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 0;
  padding: 6px 8px;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 12px;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  transform: translateX(-50%);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.3);
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  background: rgba(255, 255, 255, 0.15);
  margin: 0 6px;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
}

.tool-btn:hover {
  background: rgba(255, 255, 255, 0.15);
  color: white;
}

.tool-btn:active {
  background: rgba(255, 255, 255, 0.25);
  transform: scale(0.95);
}

.zoom-level {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 48px;
  color: rgba(255, 255, 255, 0.8);
  font-size: 12px;
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-weight: 500;
}

/* ---- 关闭按钮 ---- */
.lightbox-close-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.4);
  color: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  font-size: 18px;
  transition: all 0.2s;
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.lightbox-close-btn:hover {
  background: rgba(239, 68, 68, 0.7);
  color: white;
  transform: scale(1.1);
}

/* ---- 导航按钮 ---- */
.lightbox-nav-btn {
  position: absolute;
  top: 50%;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.3);
  color: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  font-size: 20px;
  transition: all 0.2s;
  transform: translateY(-50%);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.nav-prev { left: 20px; }
.nav-next { right: 20px; }

.lightbox-nav-btn:hover {
  background: rgba(0, 0, 0, 0.5);
  color: white;
  transform: translateY(-50%) scale(1.1);
}
</style>
