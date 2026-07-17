<!--
  ============================================================
  PreviewToolbar.vue — 文件预览工具栏
  ============================================================
  统一的预览工具栏组件，用于所有文件预览页面。
  提供缩放、旋转、全屏、打印、分享、下载等核心功能。
  
  用法：
    <PreviewToolbar
      :file-name="fileName"
      :file-type="documentType"
      :scale="scale"
      :rotation="rotation"
      :current-page="currentPage"
      :total-pages="totalPages"
      :show-zoom="true"
      :show-rotate="true"
      :show-search="true"
      :show-fullscreen="true"
      :show-print="true"
      :show-share="true"
      :show-download="true"
      :show-thumbnails="showThumbnails"
      :show-outline="showOutline"
      @zoom-in="handleZoomIn"
      @zoom-out="handleZoomOut"
      @zoom-reset="handleZoomReset"
      @rotate-left="handleRotateLeft"
      @rotate-right="handleRotateRight"
      @search="handleSearch"
      @fullscreen="handleFullscreen"
      @print="handlePrint"
      @share="handleShare"
      @download="handleDownload"
      @toggle-thumbnails="handleToggleThumbnails"
      @toggle-outline="handleToggleOutline"
      @close="handleClose"
    />
  ============================================================
-->
<template>
  <div class="preview-toolbar" :class="{ 'toolbar-dark': isDark }">
    <!-- 左侧：文件信息 -->
    <div class="toolbar-left">
      <button @click="$emit('close')" class="tool-btn back-btn" title="返回">
        <i class="fa fa-arrow-left"></i>
      </button>
      <div class="file-info">
        <i :class="fileTypeIcon" class="file-type-icon"></i>
        <span class="file-name" :title="fileName">{{ fileName }}</span>
      </div>
    </div>

    <!-- 中间：页面导航 -->
    <div class="toolbar-center" v-if="showPageNav">
      <button
        @click="$emit('page-first')"
        :disabled="currentPage <= 1"
        class="tool-btn"
        title="首页"
      >
        <i class="fa fa-angle-double-left"></i>
      </button>
      <button
        @click="$emit('page-prev')"
        :disabled="currentPage <= 1"
        class="tool-btn"
        title="上一页"
      >
        <i class="fa fa-chevron-left"></i>
      </button>
      <div class="page-control">
        <input
          type="number"
          :value="currentPage"
          @input="onPageInput"
          @keyup.enter="onPageEnter"
          @blur="onPageBlur"
          min="1"
          :max="totalPages"
          class="page-input"
          title="当前页码"
        />
        <span class="page-separator">/</span>
        <span class="page-total">{{ totalPages }}</span>
      </div>
      <button
        @click="$emit('page-next')"
        :disabled="currentPage >= totalPages"
        class="tool-btn"
        title="下一页"
      >
        <i class="fa fa-chevron-right"></i>
      </button>
      <button
        @click="$emit('page-last')"
        :disabled="currentPage >= totalPages"
        class="tool-btn"
        title="末页"
      >
        <i class="fa fa-angle-double-right"></i>
      </button>
    </div>

    <!-- 右侧：功能按钮 -->
    <div class="toolbar-right">
      <!-- 缩放控制 -->
      <div v-if="showZoom" class="zoom-group">
        <button @click="$emit('zoom-out')" class="tool-btn" title="缩小">
          <i class="fa fa-search-minus"></i>
        </button>
        <button
          @click="$emit('zoom-reset')"
          class="tool-btn zoom-label"
          :title="`缩放: ${Math.round(scale * 100)}%`"
        >
          {{ Math.round(scale * 100) }}%
        </button>
        <button @click="$emit('zoom-in')" class="tool-btn" title="放大">
          <i class="fa fa-search-plus"></i>
        </button>
      </div>

      <!-- 旋转控制 -->
      <div v-if="showRotate" class="tool-group">
        <button @click="$emit('rotate-left')" class="tool-btn" title="向左旋转">
          <i class="fa fa-rotate-left"></i>
        </button>
        <button @click="$emit('rotate-right')" class="tool-btn" title="向右旋转">
          <i class="fa fa-rotate-right"></i>
        </button>
      </div>

      <div class="toolbar-divider" v-if="showSearch || showFullscreen"></div>

      <!-- 搜索 -->
      <button v-if="showSearch" @click="$emit('search')" class="tool-btn" title="搜索">
        <i class="fa fa-search"></i>
      </button>

      <!-- 缩略图切换 -->
      <button
        v-if="showThumbnailsToggle"
        @click="$emit('toggle-thumbnails')"
        class="tool-btn"
        :class="{ active: thumbnailsActive }"
        title="缩略图"
      >
        <i class="fa fa-th-large"></i>
      </button>

      <!-- 大纲切换 -->
      <button
        v-if="showOutlineToggle"
        @click="$emit('toggle-outline')"
        class="tool-btn"
        :class="{ active: outlineActive }"
        title="大纲"
      >
        <i class="fa fa-list-ul"></i>
      </button>

      <div class="toolbar-divider" v-if="showFullscreen || showPrint || showShare || showDownload"></div>

      <!-- 全屏 -->
      <button v-if="showFullscreen" @click="$emit('fullscreen')" class="tool-btn" title="全屏">
        <i :class="isFullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i>
      </button>

      <!-- 打印 -->
      <button v-if="showPrint" @click="$emit('print')" class="tool-btn" title="打印">
        <i class="fa fa-print"></i>
      </button>

      <!-- 分享 -->
      <button v-if="showShare" @click="$emit('share')" class="tool-btn" title="分享">
        <i class="fa fa-share-alt"></i>
      </button>

      <!-- 下载 -->
      <button v-if="showDownload" @click="$emit('download')" class="tool-btn download-btn" title="下载">
        <i class="fa fa-download"></i>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps({
  /** 文件名 */
  fileName: { type: String, default: '' },
  /** 文件类型 */
  fileType: { type: String, default: 'unknown' },
  /** 当前缩放比例 */
  scale: { type: Number, default: 1.0 },
  /** 当前旋转角度 */
  rotation: { type: Number, default: 0 },
  /** 当前页码 */
  currentPage: { type: Number, default: 1 },
  /** 总页数 */
  totalPages: { type: Number, default: 0 },
  /** 是否显示页面导航 */
  showPageNav: { type: Boolean, default: true },
  /** 是否显示缩放控制 */
  showZoom: { type: Boolean, default: true },
  /** 是否显示旋转控制 */
  showRotate: { type: Boolean, default: false },
  /** 是否显示搜索 */
  showSearch: { type: Boolean, default: false },
  /** 是否显示全屏 */
  showFullscreen: { type: Boolean, default: true },
  /** 是否显示打印 */
  showPrint: { type: Boolean, default: true },
  /** 是否显示分享 */
  showShare: { type: Boolean, default: false },
  /** 是否显示下载 */
  showDownload: { type: Boolean, default: true },
  /** 是否显示缩略图切换 */
  showThumbnailsToggle: { type: Boolean, default: false },
  /** 是否显示大纲切换 */
  showOutlineToggle: { type: Boolean, default: false },
  /** 缩略图是否激活 */
  thumbnailsActive: { type: Boolean, default: false },
  /** 大纲是否激活 */
  outlineActive: { type: Boolean, default: false },
  /** 是否全屏 */
  isFullscreen: { type: Boolean, default: false },
  /** 是否暗色主题 */
  isDark: { type: Boolean, default: false },
})

const emit = defineEmits([
  'close',
  'page-first',
  'page-prev',
  'page-next',
  'page-last',
  'page-change',
  'zoom-in',
  'zoom-out',
  'zoom-reset',
  'rotate-left',
  'rotate-right',
  'search',
  'fullscreen',
  'print',
  'share',
  'download',
  'toggle-thumbnails',
  'toggle-outline',
])

/** 临时页码输入 */
const tempPage = ref(props.currentPage)

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

/** 页码输入处理 */
function onPageInput(e: Event) {
  const target = e.target as HTMLInputElement
  tempPage.value = parseInt(target.value) || 0
}

function onPageEnter() {
  const page = Math.max(1, Math.min(props.totalPages, tempPage.value))
  if (page >= 1 && page <= props.totalPages) {
    emit('page-change', page)
  }
}

function onPageBlur() {
  onPageEnter()
}
</script>

<style scoped>
.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 1rem;
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  gap: 0.75rem;
  min-height: 52px;
  z-index: 100;
  flex-shrink: 0;
}

/* 暗色主题 */
.preview-toolbar.toolbar-dark {
  background: rgba(30, 30, 30, 0.98);
  border-bottom-color: #3e3e42;
}

.preview-toolbar.toolbar-dark .tool-btn {
  background: #3e3e42;
  color: #cccccc;
}

.preview-toolbar.toolbar-dark .tool-btn:hover:not(:disabled) {
  background: #4b6cb7;
  color: #fff;
}

.preview-toolbar.toolbar-dark .file-name {
  color: #cccccc;
}

.preview-toolbar.toolbar-dark .page-input {
  background: #3e3e42;
  color: #cccccc;
  border-color: #555;
}

.preview-toolbar.toolbar-dark .page-separator,
.preview-toolbar.toolbar-dark .page-total {
  color: #999;
}

.preview-toolbar.toolbar-dark .toolbar-divider {
  background: #555;
}

/* 左/中/右 区域 */
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  flex: 1;
}

.toolbar-center {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  flex-shrink: 0;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  flex-shrink: 0;
  justify-content: flex-end;
  flex: 1;
}

/* 文件信息 */
.file-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  overflow: hidden;
}

.file-type-icon {
  font-size: 1.1rem;
  color: #4b6cb7;
  flex-shrink: 0;
}

.preview-toolbar.toolbar-dark .file-type-icon {
  color: #6c8ef5;
}

.file-name {
  font-weight: 600;
  font-size: 0.9rem;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

/* 工具按钮 */
.tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border: none;
  background: #f1f5f9;
  border-radius: 0.375rem;
  cursor: pointer;
  transition: all 0.15s ease;
  color: #475569;
  font-size: 0.85rem;
  flex-shrink: 0;
}

.tool-btn:hover:not(:disabled) {
  background: #4b6cb7;
  color: #fff;
}

.tool-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.tool-btn.active {
  background: #4b6cb7;
  color: #fff;
}

.back-btn {
  margin-right: 0.25rem;
}

.download-btn {
  background: #4b6cb7;
  color: #fff;
}

.download-btn:hover:not(:disabled) {
  background: #3b5ba7;
}

/* 页面导航 */
.page-control {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0 0.25rem;
}

.page-input {
  width: 44px;
  padding: 0.2rem 0.25rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.25rem;
  text-align: center;
  font-size: 0.8rem;
  color: #475569;
  background: #fff;
  -moz-appearance: textfield;
}

.page-input::-webkit-outer-spin-button,
.page-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.page-separator {
  font-size: 0.8rem;
  color: #94a3b8;
}

.page-total {
  font-size: 0.8rem;
  color: #475569;
  font-weight: 500;
  min-width: 24px;
}

/* 缩放组 */
.zoom-group {
  display: flex;
  align-items: center;
  gap: 0.125rem;
}

.zoom-label {
  width: auto !important;
  min-width: 3rem;
  font-size: 0.75rem !important;
  font-weight: 600;
}

/* 工具组 */
.tool-group {
  display: flex;
  align-items: center;
  gap: 0.125rem;
}

/* 分隔线 */
.toolbar-divider {
  width: 1px;
  height: 1.5rem;
  background: #e2e8f0;
  margin: 0 0.25rem;
}

/* 响应式 */
@media (max-width: 768px) {
  .preview-toolbar {
    padding: 0.5rem 0.75rem;
    gap: 0.5rem;
  }

  .file-name {
    max-width: 100px;
    font-size: 0.8rem;
  }

  .tool-btn {
    width: 2rem;
    height: 2rem;
    font-size: 0.8rem;
  }

  .page-input {
    width: 36px;
  }
}

@media (max-width: 480px) {
  .toolbar-center {
    display: none;
  }

  .zoom-group {
    display: none;
  }
}
</style>