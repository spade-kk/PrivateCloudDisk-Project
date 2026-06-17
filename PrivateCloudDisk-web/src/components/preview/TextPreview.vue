<template>
  <div class="text-preview-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="preview-loading">
      <div class="loading-spinner">
        <i class="fa fa-spinner fa-spin fa-3x"></i>
      </div>
      <p class="loading-text">正在加载文本...</p>
    </div>

    <!-- 文本预览 -->
    <div v-else-if="textContent" class="preview-content">
      <!-- 工具栏 -->
      <div class="preview-toolbar">
        <div class="toolbar-left">
          <span class="file-name truncate">{{ fileName }}</span>
          <span class="file-extension">{{ fileExtension.toUpperCase() }}</span>
        </div>
        <div class="toolbar-right">
          <button @click="copyText" class="tool-btn" title="复制文本">
            <i class="fa fa-copy"></i>
          </button>
          <button @click="downloadText" class="tool-btn" title="下载">
            <i class="fa fa-download"></i>
          </button>
        </div>
      </div>

      <!-- 文本显示区 -->
      <div class="text-viewer">
        <pre class="text-block">{{ textContent }}</pre>
      </div>

      <!-- 文件信息 -->
      <div class="text-info-bar">
        <div class="info-item">
          <i class="fa fa-file-text-o"></i>
          <span>{{ fileExtension.toUpperCase() }}</span>
        </div>
        <div class="info-item">
          <i class="fa fa-file"></i>
          <span>{{ fileSize }}</span>
        </div>
        <div class="info-item">
          <i class="fa fa-align-left"></i>
          <span>{{ lineCount }} 行</span>
        </div>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else class="preview-error">
      <i class="fa fa-exclamation-triangle fa-4x"></i>
      <h3>文本加载失败</h3>
      <p>{{ errorMessage }}</p>
      <button @click="$emit('retry')" class="retry-btn">
        <i class="fa fa-refresh"></i> 重新加载
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useToastStore } from '@/stores/toastStore'

const props = defineProps({
  textContent: {
    type: String,
    default: ''
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
    default: 'txt'
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['retry', 'loaded', 'error'])

const toastStore = useToastStore()

// 计算行数
const lineCount = computed(() => {
  if (!props.textContent) return 0
  return props.textContent.split('\n').length
})

// 复制文本
const copyText = async () => {
  try {
    await navigator.clipboard.writeText(props.textContent)
    toastStore.showToast('文本已复制到剪贴板', 'success')
  } catch (err) {
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = props.textContent
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    toastStore.showToast('文本已复制到剪贴板', 'success')
  }
}

// 下载文本
const downloadText = () => {
  const blob = new Blob([props.textContent], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = props.fileName
  link.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.text-preview-container {
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

.file-extension {
  background: #4b6cb7;
  color: white;
  padding: 0.2rem 0.6rem;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
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

.text-viewer {
  flex: 1;
  overflow: auto;
  padding: 1.5rem;
}

.text-block {
  margin: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 0.9rem;
  line-height: 1.8;
  color: #333;
  background: white;
  padding: 1.5rem;
  border-radius: 0.75rem;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  white-space: pre-wrap;
  word-wrap: break-word;
}

.text-info-bar {
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
