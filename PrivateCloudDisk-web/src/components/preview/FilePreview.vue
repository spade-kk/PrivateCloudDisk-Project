<template>
  <Teleport to="body">
    <Transition name="preview-fade">
      <div v-if="visible" class="preview-overlay" @click.self="handleClose">
        <div class="preview-modal" :class="{ 'fullscreen': isFullscreen }">
          <!-- 头部 -->
          <div class="preview-header">
            <div class="header-left">
              <h2 class="preview-title">
                <i :class="fileTypeIcon"></i>
                <span>文件预览</span>
              </h2>
            </div>
            <div class="header-actions">
              <button @click="toggleFullscreen" class="action-btn" :title="isFullscreen ? '退出全屏' : '全屏'">
                <i :class="isFullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i>
              </button>
              <button @click="handleClose" class="action-btn close-btn" title="关闭">
                <i class="fa fa-times"></i>
              </button>
            </div>
          </div>

          <!-- 预览内容 -->
          <div class="preview-body">
            <!-- 图片预览 -->
            <ImagePreview
              v-if="previewStore.isImage"
              :file-url="previewStore.previewUrl"
              :file-name="previewStore.currentFile?.node_name || ''"
              :file-size="previewStore.fileSizeFormatted"
              :file-extension="previewStore.fileExtension"
              :loading="previewStore.loading"
              @retry="handleRetry"
            />

            <!-- 视频预览 -->
            <VideoPreview
              v-else-if="previewStore.isVideo"
              :file-url="previewStore.previewUrl"
              :file-name="previewStore.currentFile?.node_name || ''"
              :file-size="previewStore.fileSizeFormatted"
              :file-extension="previewStore.fileExtension"
              :file-id="previewStore.currentFile?.node_id || ''"
              :loading="previewStore.loading"
              @retry="handleRetry"
            />

            <!-- 音频预览 -->
            <AudioPreview
              v-else-if="previewStore.isAudio"
              :file-url="previewStore.previewUrl"
              :file-name="previewStore.currentFile?.node_name || ''"
              :file-size="previewStore.fileSizeFormatted"
              :file-extension="previewStore.fileExtension"
              :loading="previewStore.loading"
              @retry="handleRetry"
            />

            <!-- PDF预览 -->
            <PdfPreview
              v-else-if="previewStore.isPdf"
              :file-url="previewStore.previewUrl"
              :file-name="previewStore.currentFile?.node_name || ''"
              :file-size="previewStore.fileSizeFormatted"
              :file-extension="previewStore.fileExtension"
              :loading="previewStore.loading"
              @retry="handleRetry"
            />

            <!-- Office文档预览 -->
            <OfficePreview
              v-else-if="previewStore.isOffice"
              :file-url="previewStore.previewUrl"
              :file-name="previewStore.currentFile?.node_name || ''"
              :file-size="previewStore.fileSizeFormatted"
              :file-extension="previewStore.fileExtension"
              :conversion-status="previewStore.conversionStatus"
              :loading="previewStore.loading"
              @conversion-requested="handleConversionRequested"
              @retry="handleRetry"
            />

            <!-- 代码预览 -->
            <CodePreview
              v-else-if="previewStore.isCode"
              :code-content="previewStore.previewContent"
              :file-name="previewStore.currentFile?.node_name || ''"
              :file-size="previewStore.fileSizeFormatted"
              :file-extension="previewStore.fileExtension"
              :loading="previewStore.loading"
              @retry="handleRetry"
            />

            <!-- 文本预览 -->
            <TextPreview
              v-else-if="previewStore.isText"
              :text-content="previewStore.previewContent"
              :file-name="previewStore.currentFile?.node_name || ''"
              :file-size="previewStore.fileSizeFormatted"
              :file-extension="previewStore.fileExtension"
              :loading="previewStore.loading"
              @retry="handleRetry"
            />

            <!-- 不支持的格式 -->
            <div v-else class="unsupported-preview">
              <div class="unsupported-icon">
                <i class="fa fa-file-o"></i>
              </div>
              <h3>暂不支持预览此文件格式</h3>
              <p>文件类型: {{ previewStore.fileExtension.toUpperCase() }}</p>
              <div class="unsupported-actions">
                <button @click="handleDownload" class="download-btn">
                  <i class="fa fa-download"></i>
                  下载文件
                </button>
                <button @click="handleClose" class="close-preview-btn">
                  关闭
                </button>
              </div>
            </div>
          </div>

          <!-- 错误提示 -->
          <div v-if="previewStore.error" class="error-banner">
            <i class="fa fa-exclamation-circle"></i>
            <span>{{ previewStore.error.message }}</span>
            <button @click="previewStore.error = null" class="error-dismiss">
              <i class="fa fa-times"></i>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { usePreviewStore } from '@/stores/previewStore'
import { useToastStore } from '@/stores/toastStore'
import ImagePreview from './ImagePreview.vue'
import VideoPreview from './VideoPreview.vue'
import AudioPreview from './AudioPreview.vue'
import PdfPreview from './PdfPreview.vue'
import OfficePreview from './OfficePreview.vue'
import CodePreview from './CodePreview.vue'
import TextPreview from './TextPreview.vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  file: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['close', 'download'])

const previewStore = usePreviewStore()
const toastStore = useToastStore()

const isFullscreen = ref(false)

// 文件类型图标
const fileTypeIcon = computed(() => {
  if (previewStore.isImage) return 'fa fa-image'
  if (previewStore.isVideo) return 'fa fa-film'
  if (previewStore.isAudio) return 'fa fa-music'
  if (previewStore.isPdf) return 'fa fa-file-pdf-o'
  if (previewStore.isOffice) return 'fa fa-file-word-o'
  if (previewStore.isCode) return 'fa fa-code'
  if (previewStore.isText) return 'fa fa-file-text-o'
  return 'fa fa-file'
})

// 打开预览
const openPreview = async () => {
  if (props.file) {
    await previewStore.openFile(props.file)
  }
}

// 关闭预览
const handleClose = () => {
  previewStore.closePreview()
  emit('close')
}

// 下载文件
const handleDownload = () => {
  emit('download', previewStore.currentFile)
}

// 重试
const handleRetry = () => {
  if (props.file) {
    previewStore.openFile(props.file)
  }
}

// 请求文档转换
const handleConversionRequested = () => {
  // 触发文档转换请求
  toastStore.showToast('正在准备文档预览...', 'info')
}

// 全屏切换
const toggleFullscreen = async () => {
  if (!document.fullscreenElement) {
    await document.documentElement.requestFullscreen()
    isFullscreen.value = true
  } else {
    await document.exitFullscreen()
    isFullscreen.value = false
  }
}

// ESC 键关闭
const handleKeydown = (e) => {
  if (e.key === 'Escape' && props.visible) {
    handleClose()
  }
}

// 监听文件变化
watch(() => props.file, (newFile) => {
  if (newFile && props.visible) {
    openPreview()
  }
}, { immediate: true })

// 监听可见性变化
watch(() => props.visible, (visible) => {
  if (visible) {
    openPreview()
  }
})

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('fullscreenchange', () => {
    isFullscreen.value = !!document.fullscreenElement
  })
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  previewStore.closePreview()
})
</script>

<style scoped>
.preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

.preview-modal {
  width: 100%;
  max-width: 1400px;
  max-height: 95vh;
  background: white;
  border-radius: 1rem;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 25px 80px rgba(0, 0, 0, 0.5);
}

.preview-modal.fullscreen {
  max-width: 100%;
  max-height: 100%;
  border-radius: 0;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  background: linear-gradient(135deg, #4b6cb7 0%, #182848 100%);
  color: white;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.preview-title {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 0.5rem;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 0.5rem;
  color: white;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 0.95rem;
}

.action-btn:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: scale(1.05);
}

.action-btn.close-btn:hover {
  background: #dc2626;
}

.preview-body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1.5rem;
  background: #fef2f2;
  border-top: 1px solid #fecaca;
  color: #dc2626;
  font-size: 0.9rem;
}

.error-banner i {
  flex-shrink: 0;
}

.error-banner span {
  flex: 1;
}

.error-dismiss {
  background: none;
  border: none;
  color: #dc2626;
  cursor: pointer;
  padding: 0.25rem;
  font-size: 1rem;
}

.error-dismiss:hover {
  opacity: 0.7;
}

.unsupported-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 3rem;
  text-align: center;
}

.unsupported-icon {
  width: 120px;
  height: 120px;
  background: #f1f5f9;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 1.5rem;
}

.unsupported-icon i {
  font-size: 4rem;
  color: #94a3b8;
}

.unsupported-preview h3 {
  font-size: 1.5rem;
  color: #182848;
  margin-bottom: 0.5rem;
}

.unsupported-preview p {
  color: #64748b;
  margin-bottom: 2rem;
}

.unsupported-actions {
  display: flex;
  gap: 1rem;
}

.download-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  background: linear-gradient(90deg, #10b981 0%, #047857 100%);
  color: white;
  border: none;
  padding: 0.75rem 1.5rem;
  border-radius: 2rem;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.download-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(16, 185, 129, 0.4);
}

.close-preview-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  background: #f1f5f9;
  color: #64748b;
  border: none;
  padding: 0.75rem 1.5rem;
  border-radius: 2rem;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.close-preview-btn:hover {
  background: #e2e8f0;
}

/* 过渡动画 */
.preview-fade-enter-active,
.preview-fade-leave-active {
  transition: opacity 0.3s ease;
}

.preview-fade-enter-from,
.preview-fade-leave-to {
  opacity: 0;
}

.preview-fade-enter-active .preview-modal,
.preview-fade-leave-active .preview-modal {
  transition: transform 0.3s ease;
}

.preview-fade-enter-from .preview-modal,
.preview-fade-leave-to .preview-modal {
  transform: scale(0.9);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .preview-overlay {
    padding: 0;
  }

  .preview-modal {
    max-height: 100vh;
    border-radius: 0;
  }

  .preview-header {
    padding: 0.75rem 1rem;
  }

  .preview-title span {
    display: none;
  }

  .unsupported-actions {
    flex-direction: column;
  }
}
</style>
