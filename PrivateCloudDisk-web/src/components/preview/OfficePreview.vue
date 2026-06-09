<template>
  <div class="office-preview-container">
    <!-- 加载状态 -->
    <div v-if="loading || conversionStatus === 'processing'" class="preview-loading">
      <div class="loading-spinner">
        <i class="fa fa-spinner fa-spin fa-3x"></i>
      </div>
      <p class="loading-text">{{ conversionStatus === 'processing' ? '文档正在转换中...' : '正在加载文档...' }}</p>
      <p class="loading-hint">Office文档需要转换为PDF格式进行预览，请稍候</p>
    </div>

    <!-- Office预览 -->
    <div v-else-if="previewUrl" class="preview-content">
      <!-- 工具栏 -->
      <div class="preview-toolbar">
        <div class="toolbar-left">
          <span class="file-name truncate">{{ fileName }}</span>
        </div>
        <div class="toolbar-right">
          <button @click="downloadDocument" class="tool-btn" title="下载">
            <i class="fa fa-download"></i>
          </button>
        </div>
      </div>

      <!-- 文档预览区（使用iframe加载转换后的PDF） -->
      <div class="office-viewer">
        <iframe :src="previewUrl" class="document-iframe" frameborder="0"></iframe>
      </div>

      <!-- 文件信息 -->
      <div class="office-info-bar">
        <div class="info-item">
          <i :class="documentIcon"></i>
          <span>{{ documentType }}</span>
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
      <h3>{{ errorTitle }}</h3>
      <p>{{ errorMessage }}</p>
      <button @click="retryConversion" class="retry-btn">
        <i class="fa fa-refresh"></i> 重新转换
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useToastStore } from '@/stores/toastStore'

const props = defineProps({
  fileUrl: {
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
    default: ''
  },
  conversionStatus: {
    type: String,
    default: 'pending' // pending, processing, completed, failed
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['retry', 'loaded', 'error', 'conversion-requested'])

const toastStore = useToastStore()

// 错误信息
const errorTitle = ref('文档预览失败')
const errorMessage = ref('无法预览此Office文档')

// 计算预览URL
const previewUrl = computed(() => {
  if (!props.fileUrl) return ''
  if (props.conversionStatus !== 'completed') return ''
  return props.fileUrl
})

// 文档类型
const documentType = computed(() => {
  const ext = props.fileExtension.toLowerCase()
  const types = {
    doc: 'Word文档',
    docx: 'Word文档',
    xls: 'Excel表格',
    xlsx: 'Excel表格',
    csv: 'CSV文件',
    ppt: 'PowerPoint演示文稿',
    pptx: 'PowerPoint演示文稿',
    pptm: 'PowerPoint演示文稿'
  }
  return types[ext] || 'Office文档'
})

// 文档图标
const documentIcon = computed(() => {
  const ext = props.fileExtension.toLowerCase()
  if (['doc', 'docx'].includes(ext)) return 'fa fa-file-word-o'
  if (['xls', 'xlsx', 'csv'].includes(ext)) return 'fa fa-file-excel-o'
  if (['ppt', 'pptx', 'pptm'].includes(ext)) return 'fa fa-file-powerpoint-o'
  return 'fa fa-file-o'
})

// 重试转换
const retryConversion = () => {
  emit('conversion-requested')
}

// 下载文档
const downloadDocument = () => {
  const link = document.createElement('a')
  link.href = props.fileUrl
  link.download = props.fileName
  link.click()
}
</script>

<style scoped>
.office-preview-container {
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
  text-align: center;
  padding: 2rem;
}

.loading-spinner {
  margin-bottom: 1rem;
  color: #6c8ef5;
}

.loading-text {
  font-size: 1.1rem;
  margin-bottom: 0.5rem;
}

.loading-hint {
  font-size: 0.9rem;
  opacity: 0.8;
  max-width: 400px;
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
}

.office-viewer {
  flex: 1;
  overflow: hidden;
  display: flex;
  justify-content: center;
  padding: 1rem;
  background: #525659;
}

.document-iframe {
  width: 100%;
  height: 100%;
  background: white;
  border-radius: 0.5rem;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

.office-info-bar {
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
  font-size: 1.1rem;
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
  max-width: 400px;
}

.retry-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  background: white;
  color: #4b6cb7;
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
