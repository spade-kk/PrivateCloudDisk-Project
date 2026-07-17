<!--
  ============================================================
  FileInfoPanel.vue — 文件信息侧边面板
  ============================================================
  显示文件详细信息的面板，适用于所有预览页面。
  包含文件元数据、文档属性、预览统计等。
  
  用法：
    <FileInfoPanel
      :visible="showInfo"
      :file-name="fileName"
      :file-size="fileSize"
      :file-type="documentType"
      :metadata="metadata"
      :page-count="totalPages"
      @close="$emit('close')"
    />
  ============================================================
-->
<template>
  <Transition name="panel-slide">
    <div v-if="visible" class="file-info-panel">
      <div class="panel-header">
        <h3 class="panel-title">文件信息</h3>
        <button @click="$emit('close')" class="panel-close-btn">
          <i class="fa fa-times"></i>
        </button>
      </div>

      <div class="panel-body">
        <!-- 文件预览图 -->
        <div class="file-preview-thumb">
          <i :class="fileTypeIcon" class="file-icon-large"></i>
        </div>

        <!-- 基本信息 -->
        <div class="info-section">
          <h4 class="section-title">基本信息</h4>
          <div class="info-row">
            <span class="info-label">文件名</span>
            <span class="info-value" :title="fileName">{{ fileName }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">文件类型</span>
            <span class="info-value">{{ fileTypeLabel }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">文件大小</span>
            <span class="info-value">{{ fileSize }}</span>
          </div>
          <div class="info-row" v-if="pageCount > 0">
            <span class="info-label">页数</span>
            <span class="info-value">{{ pageCount }} 页</span>
          </div>
        </div>

        <!-- Word 特有信息 -->
        <div v-if="fileType === 'word' && metadata" class="info-section">
          <h4 class="section-title">文档属性</h4>
          <div class="info-row" v-if="metadata.author">
            <span class="info-label">作者</span>
            <span class="info-value">{{ metadata.author }}</span>
          </div>
          <div class="info-row" v-if="metadata.wordCount">
            <span class="info-label">字数</span>
            <span class="info-value">{{ metadata.wordCount?.toLocaleString() }}</span>
          </div>
          <div class="info-row" v-if="metadata.characterCount">
            <span class="info-label">字符数</span>
            <span class="info-value">{{ metadata.characterCount?.toLocaleString() }}</span>
          </div>
          <div class="info-row" v-if="metadata.paragraphCount">
            <span class="info-label">段落数</span>
            <span class="info-value">{{ metadata.paragraphCount }}</span>
          </div>
        </div>

        <!-- Excel 特有信息 -->
        <div v-if="fileType === 'excel' && metadata" class="info-section">
          <h4 class="section-title">工作簿属性</h4>
          <div class="info-row" v-if="metadata.sheets">
            <span class="info-label">工作表数</span>
            <span class="info-value">{{ metadata.sheets.length }} 个</span>
          </div>
          <div class="info-row" v-if="metadata.totalRows">
            <span class="info-label">总行数</span>
            <span class="info-value">{{ metadata.totalRows?.toLocaleString() }}</span>
          </div>
          <div class="info-row" v-if="metadata.totalColumns">
            <span class="info-label">总列数</span>
            <span class="info-value">{{ metadata.totalColumns?.toLocaleString() }}</span>
          </div>
        </div>

        <!-- PPT 特有信息 -->
        <div v-if="fileType === 'powerpoint' && metadata" class="info-section">
          <h4 class="section-title">演示文稿属性</h4>
          <div class="info-row" v-if="metadata.totalPages">
            <span class="info-label">幻灯片数</span>
            <span class="info-value">{{ metadata.totalPages }} 张</span>
          </div>
          <div class="info-row" v-if="metadata.presentationDuration">
            <span class="info-label">演示时长</span>
            <span class="info-value">约 {{ metadata.presentationDuration }} 分钟</span>
          </div>
        </div>

        <!-- 时间信息 -->
        <div class="info-section">
          <h4 class="section-title">时间信息</h4>
          <div class="info-row" v-if="metadata?.createdAt">
            <span class="info-label">创建时间</span>
            <span class="info-value">{{ formatDate(metadata.createdAt) }}</span>
          </div>
          <div class="info-row" v-if="metadata?.modifiedAt">
            <span class="info-label">修改时间</span>
            <span class="info-value">{{ formatDate(metadata.modifiedAt) }}</span>
          </div>
        </div>
      </div>

      <div class="panel-footer">
        <button @click="$emit('download')" class="panel-action-btn primary">
          <i class="fa fa-download"></i>
          下载文件
        </button>
        <button @click="$emit('share')" class="panel-action-btn">
          <i class="fa fa-share-alt"></i>
          分享
        </button>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps({
  /** 是否显示 */
  visible: { type: Boolean, default: false },
  /** 文件名 */
  fileName: { type: String, default: '' },
  /** 文件大小（已格式化） */
  fileSize: { type: String, default: '' },
  /** 文件类型 */
  fileType: { type: String, default: 'unknown' },
  /** 文档元数据 */
  metadata: { type: Object, default: null },
  /** 页数 */
  pageCount: { type: Number, default: 0 },
})

defineEmits(['close', 'download', 'share'])

/** 文件类型图标 */
const fileTypeIcon = computed(() => {
  const icons: Record<string, string> = {
    word: 'fa fa-file-word-o',
    excel: 'fa fa-file-excel-o',
    powerpoint: 'fa fa-file-powerpoint-o',
    pdf: 'fa fa-file-pdf-o',
  }
  return icons[props.fileType] || 'fa fa-file-o'
})

/** 文件类型中文名 */
const fileTypeLabel = computed(() => {
  const labels: Record<string, string> = {
    word: 'Word 文档',
    excel: 'Excel 表格',
    powerpoint: 'PowerPoint 演示文稿',
    pdf: 'PDF 文档',
  }
  return labels[props.fileType] || '未知类型'
})

/** 格式化日期 */
function formatDate(dateStr: string): string {
  if (!dateStr) return '-'
  try {
    const date = new Date(dateStr)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return dateStr
  }
}
</script>

<style scoped>
.file-info-panel {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 320px;
  background: #fff;
  border-left: 1px solid #e2e8f0;
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  z-index: 50;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #f1f5f9;
}

.panel-title {
  font-size: 1rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.panel-close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border: none;
  background: #f1f5f9;
  border-radius: 0.375rem;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s ease;
}

.panel-close-btn:hover {
  background: #e2e8f0;
  color: #1e293b;
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 1.25rem;
}

/* 文件预览缩略图 */
.file-preview-thumb {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
  background: #f8fafc;
  border-radius: 0.5rem;
  margin-bottom: 1.25rem;
}

.file-icon-large {
  font-size: 3rem;
  color: #4b6cb7;
}

/* 信息区域 */
.info-section {
  margin-bottom: 1.25rem;
  padding-bottom: 1.25rem;
  border-bottom: 1px solid #f1f5f9;
}

.info-section:last-of-type {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}

.section-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin: 0 0 0.75rem;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 0.375rem 0;
  gap: 0.75rem;
}

.info-label {
  font-size: 0.85rem;
  color: #64748b;
  flex-shrink: 0;
}

.info-value {
  font-size: 0.85rem;
  color: #1e293b;
  font-weight: 500;
  text-align: right;
  word-break: break-all;
  max-width: 60%;
}

/* 底部操作 */
.panel-footer {
  display: flex;
  gap: 0.5rem;
  padding: 1rem 1.25rem;
  border-top: 1px solid #f1f5f9;
  background: #f8fafc;
}

.panel-action-btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.625rem 1rem;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #475569;
  border-radius: 0.5rem;
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.panel-action-btn:hover {
  background: #f1f5f9;
}

.panel-action-btn.primary {
  background: #4b6cb7;
  color: #fff;
  border-color: #4b6cb7;
}

.panel-action-btn.primary:hover {
  background: #3b5ba7;
}

/* 过渡动画 */
.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: transform 0.25s ease;
}

.panel-slide-enter-from,
.panel-slide-leave-to {
  transform: translateX(100%);
}

@media (max-width: 640px) {
  .file-info-panel {
    width: 100%;
  }
}
</style>