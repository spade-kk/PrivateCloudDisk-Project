<!--
  ============================================================
  PageNavigator.vue — 页面导航器（缩略图/大纲/幻灯片导航）
  ============================================================
  左侧导航面板，支持三种模式：
    - thumbnails：PDF 页面缩略图导航
    - outline：Word 文档大纲导航
    - slides：PPT 幻灯片导航
    - sheets：Excel 工作表标签导航
  
  用法：
    <PageNavigator
      :visible="showNavigator"
      :mode="navigatorMode"
      :pages="pages"
      :outline="outline"
      :slides="slideTitles"
      :sheets="sheetNames"
      :current-page="currentPage"
      :current-sheet="currentSheetIndex"
      @page-select="handlePageSelect"
      @sheet-select="handleSheetSelect"
      @close="handleClose"
    />
  ============================================================
-->
<template>
  <Transition name="navigator-slide">
    <div v-if="visible" class="page-navigator">
      <!-- 头部 -->
      <div class="navigator-header">
        <div class="navigator-tabs">
          <button
            v-for="tab in availableTabs"
            :key="tab.key"
            @click="activeTab = tab.key"
            class="tab-btn"
            :class="{ active: activeTab === tab.key }"
            :title="tab.label"
          >
            <i :class="tab.icon"></i>
            <span>{{ tab.label }}</span>
          </button>
        </div>
        <button @click="$emit('close')" class="nav-close-btn">
          <i class="fa fa-times"></i>
        </button>
      </div>

      <!-- 内容区域 -->
      <div class="navigator-body">
        <!-- 缩略图模式 -->
        <div v-if="activeTab === 'thumbnails'" class="thumbnails-list">
          <div
            v-for="page in pageCount"
            :key="page"
            @click="$emit('page-select', page)"
            class="thumbnail-item"
            :class="{ active: currentPage === page }"
          >
            <div class="thumbnail-canvas">
              <span class="page-number-badge">{{ page }}</span>
            </div>
          </div>
        </div>

        <!-- 大纲模式（Word） -->
        <div v-else-if="activeTab === 'outline'" class="outline-list">
          <div v-if="outline.length === 0" class="empty-hint">
            暂无大纲数据
          </div>
          <div
            v-for="(item, index) in outline"
            :key="index"
            @click="$emit('page-select', item.pageNumber)"
            class="outline-item"
            :class="{ active: currentPage === item.pageNumber }"
            :style="{ paddingLeft: (item.level * 16 + 8) + 'px' }"
          >
            <span class="outline-text">{{ item.title }}</span>
            <span class="outline-page">{{ item.pageNumber }}</span>
          </div>
        </div>

        <!-- 幻灯片模式（PPT） -->
        <div v-else-if="activeTab === 'slides'" class="slides-list">
          <div v-if="slides.length === 0" class="empty-hint">
            暂无幻灯片数据
          </div>
          <div
            v-for="(title, index) in slides"
            :key="index"
            @click="$emit('page-select', index + 1)"
            class="slide-item"
            :class="{ active: currentPage === index + 1 }"
          >
            <div class="slide-thumb">
              <span class="slide-number">{{ index + 1 }}</span>
            </div>
            <span class="slide-title" :title="title">{{ title || `幻灯片 ${index + 1}` }}</span>
          </div>
        </div>

        <!-- 工作表模式（Excel） -->
        <div v-else-if="activeTab === 'sheets'" class="sheets-list">
          <div v-if="sheets.length === 0" class="empty-hint">
            暂无工作表数据
          </div>
          <div
            v-for="(sheet, index) in sheets"
            :key="index"
            @click="$emit('sheet-select', index)"
            class="sheet-item"
            :class="{ active: currentSheet === index }"
          >
            <i class="fa fa-table"></i>
            <span class="sheet-name">{{ sheet.name || sheet }}</span>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps({
  /** 是否显示 */
  visible: { type: Boolean, default: false },
  /** 导航模式 */
  mode: {
    type: String as () => 'thumbnails' | 'outline' | 'slides' | 'sheets',
    default: 'thumbnails',
  },
  /** 总页数 */
  pageCount: { type: Number, default: 0 },
  /** 文档大纲 */
  outline: { type: Array as () => any[], default: () => [] },
  /** 幻灯片标题 */
  slides: { type: Array as () => string[], default: () => [] },
  /** 工作表列表 */
  sheets: { type: Array as () => any[], default: () => [] },
  /** 当前页码 */
  currentPage: { type: Number, default: 1 },
  /** 当前工作表索引 */
  currentSheet: { type: Number, default: 0 },
})

defineEmits(['page-select', 'sheet-select', 'close'])

/** 当前激活的标签 */
const activeTab = ref(props.mode)

/** 可用的标签 */
const availableTabs = computed(() => {
  const tabs: { key: string; label: string; icon: string }[] = []

  if (props.mode === 'thumbnails' || props.pageCount > 0) {
    tabs.push({ key: 'thumbnails', label: '缩略图', icon: 'fa fa-th-large' })
  }
  if (props.mode === 'outline' || props.outline.length > 0) {
    tabs.push({ key: 'outline', label: '大纲', icon: 'fa fa-list-ul' })
  }
  if (props.mode === 'slides' || props.slides.length > 0) {
    tabs.push({ key: 'slides', label: '幻灯片', icon: 'fa fa-clone' })
  }
  if (props.mode === 'sheets' || props.sheets.length > 0) {
    tabs.push({ key: 'sheets', label: '工作表', icon: 'fa fa-table' })
  }

  return tabs
})
</script>

<style scoped>
.page-navigator {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 240px;
  background: #fff;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  z-index: 50;
  overflow: hidden;
}

.navigator-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem;
  border-bottom: 1px solid #f1f5f9;
  background: #f8fafc;
}

.navigator-tabs {
  display: flex;
  gap: 0.125rem;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.625rem;
  border: none;
  background: transparent;
  color: #64748b;
  border-radius: 0.375rem;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.tab-btn:hover {
  background: #e2e8f0;
  color: #475569;
}

.tab-btn.active {
  background: #4b6cb7;
  color: #fff;
}

.tab-btn i {
  font-size: 0.75rem;
}

.nav-close-btn {
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
  transition: all 0.15s ease;
}

.nav-close-btn:hover {
  background: #e2e8f0;
  color: #475569;
}

/* 内容区域 */
.navigator-body {
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem;
}

/* 空白提示 */
.empty-hint {
  padding: 2rem 1rem;
  text-align: center;
  color: #94a3b8;
  font-size: 0.85rem;
}

/* ========== 缩略图列表 ========== */
.thumbnails-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.thumbnail-item {
  padding: 0.5rem;
  border: 2px solid transparent;
  border-radius: 0.375rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.thumbnail-item:hover {
  background: #f1f5f9;
}

.thumbnail-item.active {
  border-color: #4b6cb7;
  background: #eff6ff;
}

.thumbnail-canvas {
  height: 80px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 0.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.page-number-badge {
  position: absolute;
  bottom: 4px;
  right: 4px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 0.7rem;
  padding: 0.125rem 0.375rem;
  border-radius: 0.25rem;
}

/* ========== 大纲列表 ========== */
.outline-list {
  display: flex;
  flex-direction: column;
}

.outline-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0.5rem;
  border-radius: 0.25rem;
  cursor: pointer;
  transition: all 0.15s ease;
  border-left: 2px solid transparent;
}

.outline-item:hover {
  background: #f1f5f9;
}

.outline-item.active {
  background: #eff6ff;
  border-left-color: #4b6cb7;
}

.outline-text {
  font-size: 0.85rem;
  color: #475569;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.outline-page {
  font-size: 0.75rem;
  color: #94a3b8;
  margin-left: 0.5rem;
  flex-shrink: 0;
}

/* ========== 幻灯片列表 ========== */
.slides-list {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.slide-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem;
  border-radius: 0.375rem;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 2px solid transparent;
}

.slide-item:hover {
  background: #f1f5f9;
}

.slide-item.active {
  border-color: #4b6cb7;
  background: #eff6ff;
}

.slide-thumb {
  width: 48px;
  height: 36px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 0.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.slide-number {
  font-size: 0.75rem;
  font-weight: 600;
  color: #4b6cb7;
}

.slide-title {
  font-size: 0.8rem;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ========== 工作表列表 ========== */
.sheets-list {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.sheet-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.625rem 0.75rem;
  border-radius: 0.375rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.sheet-item:hover {
  background: #f1f5f9;
}

.sheet-item.active {
  background: #eff6ff;
  color: #4b6cb7;
}

.sheet-item i {
  font-size: 0.85rem;
  color: #22c55e;
}

.sheet-name {
  font-size: 0.85rem;
  color: #475569;
  font-weight: 500;
}

.sheet-item.active .sheet-name {
  color: #4b6cb7;
}

/* 过渡动画 */
.navigator-slide-enter-active,
.navigator-slide-leave-active {
  transition: transform 0.25s ease;
}

.navigator-slide-enter-from,
.navigator-slide-leave-to {
  transform: translateX(-100%);
}

@media (max-width: 640px) {
  .page-navigator {
    width: 100%;
  }
}
</style>