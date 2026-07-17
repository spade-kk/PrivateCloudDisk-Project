<!--
  ============================================================
  ArchivePreviewView.vue — 压缩包文件独立预览页面
  ============================================================

  企业级压缩包文件预览页面，支持 ZIP、RAR、7Z、ISO、TAR、GZ、BZ2
  等主流压缩/归档格式的目录结构在线预览。

  核心特性：
    - 目录树结构展示：清晰呈现文件夹层级、文件类型及元数据
    - 仅预览不编辑：不支持在线编辑压缩包内容，不提供单独文件下载
    - 前端零缓存：严格禁止前端解压缓存，通过后端解析中间数据预览
    - 状态机驱动：loading → processing → completed/failed/not_found

  后端流水线（自动触发）：
    文件上传成功 → 检测 ARCHIVE_TYPES → 触发 archive_parse 增强阶段
    → 解析目录结构 → 生成 JSON 目录树 → 前端轮询预览状态

  与 PDFPreviewView、WordPreviewView、CodePreviewView 的设计模式一致：
    - 独立路由页面 /preview/archive/:fileId
    - 统一的状态管理：loading / processing / pending / failed / not_found / completed
    - 统一的 UI 样式：状态覆盖层、返回按钮、重试机制

  路由：/app/preview/archive/:fileId
  查询参数：name - 文件名
  ============================================================
-->
<template>
  <div class="archive-preview-page">
    <!-- ============================================================
         加载状态 — 加载中
         ============================================================ -->
    <div v-if="loading" class="state-overlay">
      <div class="state-content">
        <div class="state-spinner">
          <div class="ring"></div>
        </div>
        <h2 class="state-title">正在加载压缩包预览...</h2>
        <p class="state-subtitle">{{ fileName || '' }}</p>
      </div>
    </div>

    <!-- ============================================================
         处理状态 — 后端流水线正在解析中
         ============================================================ -->
    <div v-else-if="isProcessing" class="state-overlay">
      <div class="state-content">
        <div class="state-icon processing-icon">
          <i class="fa fa-cog fa-spin"></i>
        </div>
        <h2 class="state-title">正在解析压缩包目录结构</h2>
        <p class="state-subtitle">
          {{ previewMessage || '后台正在解析压缩包内容，生成目录结构预览，请稍候...' }}
        </p>
        <div class="progress-wrapper" v-if="progress > 0">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: progress + '%' }"></div>
          </div>
          <span class="progress-text">{{ progress }}%</span>
        </div>
        <p class="state-hint">文件上传后自动触发解析流水线，预览就绪后此页面将自动刷新</p>
        <div class="state-actions">
          <button @click="handleRetry" class="state-btn state-btn-secondary">
            <i class="fa fa-refresh"></i> 刷新状态
          </button>
          <button @click="goBack" class="state-btn state-btn-outline">
            <i class="fa fa-arrow-left"></i> 返回
          </button>
        </div>
      </div>
    </div>

    <!-- ============================================================
         等待状态 — 文件已上传，流水线尚未启动
         ============================================================ -->
    <div v-else-if="isPending" class="state-overlay">
      <div class="state-content">
        <div class="state-icon pending-icon">
          <i class="fa fa-clock-o"></i>
        </div>
        <h2 class="state-title">等待处理</h2>
        <p class="state-subtitle">
          {{ previewMessage || '文件已上传，后台解析流水线即将启动，请稍后再试' }}
        </p>
        <div class="state-actions">
          <button @click="handleRetry" class="state-btn state-btn-secondary">
            <i class="fa fa-refresh"></i> 刷新状态
          </button>
          <button @click="goBack" class="state-btn state-btn-outline">
            <i class="fa fa-arrow-left"></i> 返回
          </button>
        </div>
      </div>
    </div>

    <!-- ============================================================
         错误状态 — 解析失败 / 文件损坏
         ============================================================ -->
    <div v-else-if="isFailed" class="state-overlay">
      <div class="state-content">
        <div class="state-icon error-icon">
          <i class="fa fa-exclamation-triangle"></i>
        </div>
        <h2 class="state-title">{{ errorTitle || '目录解析失败' }}</h2>
        <p class="state-subtitle">{{ errorMessage || '无法解析该压缩包文件，文件可能已损坏或格式不受支持' }}</p>
        <div v-if="errorDetail" class="state-meta">
          <span class="meta-label">错误详情：</span>
          <span class="meta-value">{{ errorDetail }}</span>
        </div>
        <div class="state-actions">
          <button @click="handleRetry" class="state-btn state-btn-primary">
            <i class="fa fa-refresh"></i> 重新加载
          </button>
          <button @click="goBack" class="state-btn state-btn-secondary">
            <i class="fa fa-arrow-left"></i> 返回
          </button>
        </div>
      </div>
    </div>

    <!-- ============================================================
         错误状态 — 文件不存在
         ============================================================ -->
    <div v-else-if="isNotFound" class="state-overlay">
      <div class="state-content">
        <div class="state-icon notfound-icon">
          <i class="fa fa-file-archive-o"></i>
        </div>
        <h2 class="state-title">文件不存在</h2>
        <p class="state-subtitle">无法找到该压缩包文件，文件可能已被删除或移动</p>
        <div class="state-actions">
          <button @click="handleRetry" class="state-btn state-btn-secondary">
            <i class="fa fa-refresh"></i> 刷新状态
          </button>
          <button @click="goBack" class="state-btn state-btn-primary">
            <i class="fa fa-arrow-left"></i> 返回
          </button>
        </div>
      </div>
    </div>

    <!-- ============================================================
         正常预览状态 — 目录树已加载完成
         ============================================================ -->
    <template v-else>
      <!-- 返回按钮（浮动在左上角） -->
      <button @click="goBack" class="floating-back-btn" title="返回">
        <i class="fa fa-arrow-left"></i>
      </button>

      <!-- 页面头部 -->
      <div class="archive-header">
        <div class="header-icon">
          <i class="fa fa-file-archive-o"></i>
        </div>
        <div class="header-info">
          <h1 class="header-title">{{ fileName }}</h1>
          <p class="header-subtitle">
            压缩包目录预览
            <span class="header-separator">|</span>
            解析于 {{ parsedAt }}
          </p>
        </div>
      </div>

      <!-- 目录树组件 -->
      <ArchiveTree
        class="archive-tree-wrapper"
        :tree-data="treeData"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
// ============================================================
// ArchivePreviewView.vue — 压缩包文件独立预览页面
// ============================================================
// 作为独立路由页面，负责：
//   1. 从路由参数获取文件 ID 和文件名
//   2. 轮询后端解析流水线状态（pending → processing → completed/failed）
//   3. 管理各个状态对应的 UI
//   4. 在 completed 状态时加载目录树数据并渲染
//
// 与 PDFPreviewView 设计模式一致，使用本地状态管理，
// 不依赖外部 store，保持组件独立性和可复用性。
// ============================================================

import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getFileInfoApi } from '@/api/modules/files'
import {
  getArchivePreviewInfoApi,
  getArchiveTreeApi,
  type ArchivePreviewStatus,
  type ArchiveTreeData,
} from '@/api/modules/archivePreview'
import ArchiveTree from '@/components/preview/archive/ArchiveTree.vue'

// ============================================================
// 路由与外部依赖
// ============================================================
const route = useRoute()
const router = useRouter()

// ============================================================
// 响应式状态
// ============================================================

/** 文件 ID（从路由参数获取） */
const fileId = ref('')

/** 文件名（从路由查询参数或 API 获取） */
const fileName = ref('')

/** 加载状态 */
const loading = ref(true)

/** 预览状态 */
const previewStatus = ref<ArchivePreviewStatus>('pending')

/** 预览消息（后端返回的友好提示） */
const previewMessage = ref('')

/** 处理进度（0-100） */
const progress = ref(0)

/** 错误标题 */
const errorTitle = ref('')

/** 错误消息 */
const errorMessage = ref('')

/** 错误详情 */
const errorDetail = ref('')

/** 目录树数据 */
const treeData = ref<ArchiveTreeData | null>(null)

/** 解析时间（格式化后） */
const parsedAt = ref('')

/** 轮询定时器 */
let pollingTimer: ReturnType<typeof setInterval> | null = null

// ============================================================
// 计算属性
// ============================================================

/** 是否正在处理中 */
const isProcessing = computed(() => previewStatus.value === 'processing')

/** 是否等待处理 */
const isPending = computed(() => previewStatus.value === 'pending')

/** 是否解析失败 */
const isFailed = computed(() => previewStatus.value === 'failed')

/** 是否文件不存在 */
const isNotFound = computed(() => previewStatus.value === 'not_found')

/** 是否已完成 */
const isCompleted = computed(() => previewStatus.value === 'completed')

// ============================================================
// 数据加载
// ============================================================

/**
 * 加载文件元数据
 */
const loadFileInfo = async (): Promise<void> => {
  try {
    const res = await getFileInfoApi(fileId.value)
    const fileInfo = res.data || res
    if (fileInfo) {
      fileName.value = fileInfo.file_name || fileInfo.node_name || fileName.value
    }
  } catch (err: any) {
    const status = err?.response?.status || err?.status
    if (status === 404) {
      previewStatus.value = 'not_found'
      loading.value = false
    }
    // 其他错误不影响预览流程，继续尝试加载预览状态
  }
}

/**
 * 加载压缩包预览状态
 *
 * 调用后端 preview-status 接口获取当前解析状态。
 * 根据状态决定下一步操作：
 *   - completed: 加载目录树数据
 *   - processing/pending: 启动轮询等待
 *   - failed/not_found: 显示对应错误状态
 */
const loadPreviewStatus = async (): Promise<void> => {
  try {
    const info = await getArchivePreviewInfoApi(fileId.value)
    previewStatus.value = info.status
    previewMessage.value = info.message || ''
    progress.value = info.progress || 0

    if (info.status === 'completed') {
      // 解析完成，加载目录树
      await loadArchiveTree()
    } else if (info.status === 'failed') {
      errorTitle.value = '目录解析失败'
      errorMessage.value = '无法解析该压缩包文件，文件可能已损坏或格式不受支持'
      errorDetail.value = info.errorDetail || ''
      loading.value = false
    } else if (info.status === 'processing' || info.status === 'pending') {
      // 启动轮询
      loading.value = false
      startPolling()
    } else if (info.status === 'not_found') {
      loading.value = false
    }
  } catch (err: any) {
    const status = err?.response?.status || err?.status
    if (status === 404) {
      previewStatus.value = 'not_found'
    } else {
      // 接口不可用时，回退到直接加载目录树
      await loadArchiveTree()
    }
    loading.value = false
  }
}

/**
 * 加载压缩包目录树数据
 */
const loadArchiveTree = async (): Promise<void> => {
  try {
    const data = await getArchiveTreeApi(fileId.value)
    treeData.value = data

    // 格式化解析时间
    if (data.parsedAt) {
      try {
        const date = new Date(data.parsedAt)
        parsedAt.value = date.toLocaleString('zh-CN', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
        })
      } catch {
        parsedAt.value = data.parsedAt
      }
    }

    previewStatus.value = 'completed'
    loading.value = false
    stopPolling()
  } catch (err: any) {
    const status = err?.response?.status || err?.status
    if (status === 404) {
      // 目录树不存在，可能后端还在处理中
      previewStatus.value = 'processing'
      previewMessage.value = '目录树数据尚未生成，后台解析流水线正在进行中'
      loading.value = false
      startPolling()
    } else {
      previewStatus.value = 'failed'
      errorTitle.value = '加载目录树失败'
      errorMessage.value = '无法获取压缩包目录结构数据'
      errorDetail.value = err?.message || ''
      loading.value = false
    }
  }
}

/**
 * 启动轮询
 *
 * 每 3 秒查询一次预览状态，直到解析完成或失败。
 * 最多轮询 100 次（5 分钟），超时后显示错误。
 */
let pollCount = 0
const MAX_POLL_COUNT = 100

const startPolling = (): void => {
  stopPolling()
  pollCount = 0
  pollingTimer = setInterval(async () => {
    pollCount++
    if (pollCount > MAX_POLL_COUNT) {
      stopPolling()
      previewStatus.value = 'failed'
      errorTitle.value = '解析超时'
      errorMessage.value = '压缩包解析时间过长，请稍后重试或联系管理员'
      return
    }

    try {
      const info = await getArchivePreviewInfoApi(fileId.value)
      previewStatus.value = info.status
      previewMessage.value = info.message || ''
      progress.value = info.progress || 0

      if (info.status === 'completed') {
        await loadArchiveTree()
      } else if (info.status === 'failed') {
        stopPolling()
        errorTitle.value = '目录解析失败'
        errorMessage.value = '无法解析该压缩包文件，文件可能已损坏或格式不受支持'
        errorDetail.value = info.errorDetail || ''
      } else if (info.status === 'not_found') {
        stopPolling()
      }
    } catch {
      // 轮询失败不中断，继续等待
    }
  }, 3000)
}

/** 停止轮询 */
const stopPolling = (): void => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// ============================================================
// 页面操作
// ============================================================

/** 返回上一页 */
const goBack = (): void => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/app')
  }
}

/** 重新加载 */
const handleRetry = (): void => {
  loading.value = true
  previewStatus.value = 'pending'
  errorTitle.value = ''
  errorMessage.value = ''
  errorDetail.value = ''
  treeData.value = null
  stopPolling()
  initPage()
}

// ============================================================
// 初始化
// ============================================================

const initPage = async (): Promise<void> => {
  loading.value = true

  // 验证文件 ID
  if (!fileId.value) {
    previewStatus.value = 'failed'
    errorTitle.value = '参数错误'
    errorMessage.value = '缺少文件 ID 参数'
    loading.value = false
    return
  }

  // 并行加载文件信息和预览状态
  await Promise.all([
    loadFileInfo(),
    loadPreviewStatus(),
  ])
}

// ============================================================
// 生命周期
// ============================================================

onMounted(() => {
  fileId.value = route.params.fileId as string
  const nameParam = route.query.name as string
  if (nameParam) {
    fileName.value = decodeURIComponent(nameParam)
  }
  initPage()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
/* ============================================================
   页面容器
   ============================================================ */
.archive-preview-page {
  position: relative;
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-primary, #ffffff);
  overflow: hidden;
}

/* ============================================================
   状态覆盖层（loading / processing / error / not_found）
   ============================================================ */
.state-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-primary, #ffffff);
  z-index: 10;
  padding: 24px;
}

.state-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 480px;
}

/* ---- 加载旋转动画 ---- */
.state-spinner {
  margin-bottom: 24px;
}

.state-spinner .ring {
  width: 48px;
  height: 48px;
  border: 3px solid var(--color-border, #e1e4e8);
  border-top-color: var(--color-primary, #0366d6);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---- 状态图标 ---- */
.state-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  font-size: 28px;
}

.processing-icon {
  background: var(--color-bg-info, #e3f2fd);
  color: var(--color-primary, #0366d6);
}

.pending-icon {
  background: var(--color-bg-warning, #fff3e0);
  color: #f57c00;
}

.error-icon {
  background: var(--color-bg-danger, #ffebee);
  color: #d32f2f;
}

.notfound-icon {
  background: var(--color-bg-secondary, #f5f5f5);
  color: var(--color-text-tertiary, #959da5);
}

/* ---- 状态标题和描述 ---- */
.state-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary, #24292e);
  margin: 0 0 8px 0;
}

.state-subtitle {
  font-size: 14px;
  color: var(--color-text-secondary, #586069);
  margin: 0 0 20px 0;
  line-height: 1.5;
}

.state-hint {
  font-size: 12px;
  color: var(--color-text-tertiary, #959da5);
  margin: 8px 0 0 0;
}

/* ---- 进度条 ---- */
.progress-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 320px;
  margin-bottom: 16px;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background: var(--color-bg-secondary, #e1e4e8);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--color-primary, #0366d6);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary, #586069);
  min-width: 36px;
}

/* ---- 错误详情元数据 ---- */
.state-meta {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 14px;
  background: var(--color-bg-secondary, #f8f9fa);
  border-radius: 6px;
  margin-bottom: 20px;
  font-size: 12px;
  text-align: left;
  max-width: 100%;
  overflow: auto;
}

.meta-label {
  color: var(--color-text-tertiary, #959da5);
  white-space: nowrap;
  flex-shrink: 0;
}

.meta-value {
  color: var(--color-text-secondary, #586069);
  word-break: break-all;
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Consolas', monospace;
}

/* ---- 状态操作按钮 ---- */
.state-actions {
  display: flex;
  gap: 12px;
}

.state-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  border: 1px solid transparent;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.state-btn-primary {
  background: var(--color-primary, #0366d6);
  color: #fff;
  border-color: var(--color-primary, #0366d6);
}

.state-btn-primary:hover {
  background: var(--color-primary-hover, #0256b9);
}

.state-btn-secondary {
  background: var(--color-bg-secondary, #f5f5f5);
  color: var(--color-text-primary, #24292e);
  border-color: var(--color-border, #e1e4e8);
}

.state-btn-secondary:hover {
  background: var(--color-bg-hover, #e8eaed);
}

.state-btn-outline {
  background: transparent;
  color: var(--color-text-secondary, #586069);
  border-color: var(--color-border, #e1e4e8);
}

.state-btn-outline:hover {
  background: var(--color-bg-secondary, #f5f5f5);
}

/* ============================================================
   浮动返回按钮
   ============================================================ */
.floating-back-btn {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 5;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--color-border, #e1e4e8);
  background: var(--color-bg-primary, #ffffff);
  color: var(--color-text-secondary, #586069);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 16px;
  transition: all 0.15s ease;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.floating-back-btn:hover {
  background: var(--color-bg-secondary, #f5f5f5);
  color: var(--color-text-primary, #24292e);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

/* ============================================================
   页面头部
   ============================================================ */
.archive-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px 24px 16px 64px;
  border-bottom: 1px solid var(--color-border, #e1e4e8);
  flex-shrink: 0;
}

.header-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: linear-gradient(135deg, #f0ad4e, #ec971f);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.header-info {
  flex: 1;
  min-width: 0;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary, #24292e);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-subtitle {
  font-size: 13px;
  color: var(--color-text-tertiary, #959da5);
  margin: 4px 0 0 0;
}

.header-separator {
  margin: 0 8px;
  opacity: 0.4;
}

/* ============================================================
   目录树容器
   ============================================================ */
.archive-tree-wrapper {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>