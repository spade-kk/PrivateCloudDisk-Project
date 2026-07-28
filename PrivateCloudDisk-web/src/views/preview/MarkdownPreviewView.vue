<!--
  ============================================================
  MarkdownPreviewView.vue — Markdown 文件独立预览页面
  ============================================================

  企业级 Markdown 文件预览页面，提供完整的文档阅读体验：
    - 基于 markdown-it + highlight.js 的实时渲染
    - Mermaid 图表（流程图、时序图、甘特图、类图）
    - KaTeX 数学公式（行内 + 块级）
    - 自动生成目录导航侧边栏
    - 全文搜索高亮
    - 代码块语法高亮 + 一键复制
    - 图片灯箱放大查看
    - 表格横向滚动
    - 阅读进度条 + 字数统计 + 预估阅读时间
    - 导出 PDF、下载源文件、复制全文
    - 暗色/亮色主题切换
    - 响应式布局（桌面端 + 移动端）
    - XSS 安全防护（DOMPurify 净化）

  路由：/app/preview/markdown/:fileId
  AUDIT FIX [2.2]（需求一-5）：当前独立工作区实际路由为 /preview/markdown/:fileId；
  保留上方历史路由说明用于回溯，避免后续误把预览页重新嵌回控制台布局。
  查询参数：name - 文件名

  状态机：loading → error/not_found/completed
  ============================================================
-->
<template>
  <div class="markdown-preview-page">
    <!-- ============================================================
         加载状态 — 加载中
         ============================================================ -->
    <div v-if="loading" class="state-overlay">
      <div class="state-content">
        <div class="state-spinner">
          <div class="ring"></div>
        </div>
        <h2 class="state-title">正在加载 Markdown 文档...</h2>
        <p class="state-subtitle">{{ fileName || '' }}</p>
      </div>
    </div>

    <!-- ============================================================
         错误状态 — 网络错误或服务端错误
         ============================================================ -->
    <div v-else-if="errorState" class="state-overlay">
      <div class="state-content">
        <div class="state-icon error-icon">
          <i class="fa fa-exclamation-triangle"></i>
        </div>
        <h2 class="state-title">{{ errorState.title }}</h2>
        <p class="state-subtitle">{{ errorState.message }}</p>
        <div v-if="errorState.detail" class="state-meta">
          <span class="meta-label">错误详情：</span>
          <span class="meta-value">{{ errorState.detail }}</span>
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
    <div v-else-if="notFound" class="state-overlay">
      <div class="state-content">
        <div class="state-icon notfound-icon">
          <i class="fa fa-file-text-o"></i>
        </div>
        <h2 class="state-title">文件不存在</h2>
        <p class="state-subtitle">无法找到该 Markdown 文件，文件可能已被删除或移动</p>
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
         正常预览状态 — Markdown 内容已加载完成
         ============================================================ -->
    <template v-else>
      <!-- 返回按钮（浮动在左上角） -->
      <button @click="goBack" class="floating-back-btn" title="返回">
        <i class="fa fa-arrow-left"></i>
      </button>

      <!-- Markdown 预览组件 -->
      <MarkdownPreview
        ref="markdownPreviewRef"
        :markdown-content="markdownContent"
        :file-name="fileName"
        :file-size="fileSizeFormatted"
        :loading="false"
        :dark-mode="true"
        @ready="onPreviewReady"
        @error="onPreviewError"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
// ============================================================
// MarkdownPreviewView.vue — Markdown 文件独立预览页面
// ============================================================
// 作为独立路由页面，负责：
//   1. 从路由参数获取文件 ID 和文件名
//   2. 通过下载授权 API 获取文件内容
//      【需求三变更】上述为原有行为记录；现已替换为 Preview Token + preview-content，
//      不产生下载行为或最近访问记录。
//   3. 管理各个状态（loading / error / not_found / completed）
//   4. 将 Markdown 内容传递给 MarkdownPreview 组件进行渲染
//
// 所有预览功能（语法高亮、目录导航、搜索、图表、公式等）
// 由 MarkdownPreview 组件提供。
// ============================================================

import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toastStore'
import { fetchPreviewContentBlob, getPreviewErrorMessage } from '@/api/modules/previewContent'
import { getFileInfoApi } from '@/api/modules/files'
import MarkdownPreview from '@/components/preview/MarkdownPreview.vue'

// ============================================================
// 路由与外部依赖
// ============================================================
const route = useRoute()
const router = useRouter()
const toastStore = useToastStore()

// ============================================================
// 响应式状态 — 核心数据
// ============================================================

/** 文件 ID（从路由参数获取） */
const fileId = ref('')

/** 文件名（从路由查询参数获取） */
const fileName = ref('')

/** 文件原始大小（字节） */
const fileSizeBytes = ref(0)

/** Markdown 文件原始内容 */
const markdownContent = ref('')

/** 加载状态 */
const loading = ref(true)

/** 错误状态 */
const errorState = ref<{ title: string; message: string; detail?: string } | null>(null)

/** 文件不存在状态 */
const notFound = ref(false)

// ============================================================
// 模板引用
// ============================================================

const markdownPreviewRef = ref<InstanceType<typeof MarkdownPreview> | null>(null)

// ============================================================
// 计算属性
// ============================================================

/** 文件大小格式化 */
const fileSizeFormatted = computed(() => {
  const bytes = fileSizeBytes.value
  if (bytes === 0) return ''
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
})

// ============================================================
// 预览组件事件处理
// ============================================================

/** Markdown 预览就绪回调 */
const onPreviewReady = (data: { wordCount: number; lineCount: number; readTime: number }): void => {
  // 文档已渲染完成，可用于统计分析
}

/** Markdown 预览错误回调 */
const onPreviewError = (message: string): void => {
  console.error('[MarkdownPreviewView] 渲染错误:', message)
  toastStore.showToast('Markdown 渲染异常: ' + message, 'error')
}

// ============================================================
// 数据加载 — 获取文件内容
// ============================================================

/**
 * 加载 Markdown 文件内容
 *
 * 流程：
 *   1. 获取文件元数据（名称、大小）
 *   2. 创建下载授权令牌
 *   3. 通过令牌获取文件内容（Blob）
 *   4. 将 Blob 转换为 UTF-8 文本
 *   5. 释放下载授权令牌
 *
 * 【需求三变更】保留以上原流程注释用于回溯；当前实际流程使用 Preview Token，
 * 由 previewContent.ts 负责短期授权的申请、源内容读取和释放。
 */
const loadFileContent = async (): Promise<void> => {
  loading.value = true
  errorState.value = null
  notFound.value = false

  try {
    // 1. 获取文件元数据
    const fileInfoRes = await getFileInfoApi(fileId.value)
    const fileInfo = fileInfoRes.data || fileInfoRes
    if (fileInfo) {
      fileName.value = fileInfo.file_name || fileInfo.node_name || fileName.value
      fileSizeBytes.value = fileInfo.file_size || 0
    }

    /*
     * 需求二 / 三-1/2：
     * 原行为从下载接口读取 Markdown；新行为统一走有状态 Preview Token，
     * 后端只临时返回原始内容，由当前页面在浏览器内完成渲染和高亮。
     */
    const blob = await fetchPreviewContentBlob(fileId.value)

    // 4. 将 Blob 转换为文本
    const text = await blobToText(blob)

    // 5. 大小限制检查（Markdown 文件最多 10MB）
    if (text.length > 10 * 1024 * 1024) {
      throw new Error('文件过大（超过 10MB），无法在线预览')
    }

    markdownContent.value = text

    loading.value = false
  } catch (err: any) {
    loading.value = false

    // 根据错误类型区分状态
    const status = err?.response?.status || err?.status
    if (status === 404) {
      notFound.value = true
    } else if (status === 401 || status === 403) {
      errorState.value = {
        title: '无访问权限',
        message: '您没有权限访问该文件，请检查登录状态',
        detail: err?.message || '',
      }
    } else {
      errorState.value = {
        title: '文件加载失败',
        message: getPreviewErrorMessage(err),
        detail: err?.message || '',
      }
    }
  }
}

/**
 * 将 Blob 对象转换为 UTF-8 文本
 */
const blobToText = (blob: Blob): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.readAsText(blob, 'UTF-8')
  })
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
  loadFileContent()
}

// ============================================================
// 生命周期
// ============================================================

onMounted(() => {
  // 从路由参数获取文件信息
  fileId.value = route.params.fileId as string
  const nameParam = route.query.name as string
  if (nameParam) {
    fileName.value = decodeURIComponent(nameParam)
  }

  // 验证文件 ID
  if (!fileId.value) {
    errorState.value = {
      title: '参数错误',
      message: '缺少文件 ID 参数',
    }
    loading.value = false
    return
  }

  // 加载文件内容
  loadFileContent()
})

</script>

<style scoped>
/* ============================================================ */
/* Markdown 预览页面 — 全屏沉浸式阅读体验 */
/* ============================================================ */

.markdown-preview-page {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: #1e1e1e;
}

/* ============================================================ */
/* 状态覆盖层 */
/* ============================================================ */

.state-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1e1e1e;
  z-index: 10;
}

.state-content {
  text-align: center;
  max-width: 480px;
  padding: 2rem;
}

.state-spinner {
  margin-bottom: 1.5rem;
}

.state-spinner .ring {
  display: inline-block;
  width: 48px;
  height: 48px;
  border: 3px solid #3e3e42;
  border-top-color: #4b6cb7;
  border-radius: 50%;
  animation: md-spin 0.8s linear infinite;
}

@keyframes md-spin {
  to { transform: rotate(360deg); }
}

.state-icon {
  margin-bottom: 1.5rem;
  font-size: 3rem;
}

.state-icon.error-icon {
  color: #e74c3c;
}

.state-icon.notfound-icon {
  color: #858585;
}

.state-title {
  color: #e0e0e0;
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
}

.state-subtitle {
  color: #999;
  font-size: 0.9rem;
  margin: 0 0 1.5rem 0;
  line-height: 1.5;
}

.state-meta {
  margin-bottom: 1.5rem;
  padding: 0.75rem;
  background: #252526;
  border: 1px solid #3e3e42;
  border-radius: 4px;
  text-align: left;
  font-size: 0.8rem;
}

.meta-label {
  color: #858585;
}

.meta-value {
  color: #ce9178;
  word-break: break-all;
}

.state-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
}

.state-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 1.25rem;
  border-radius: 4px;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.15s;
  border: none;
}

.state-btn-primary {
  background: #4b6cb7;
  color: #fff;
}

.state-btn-primary:hover {
  background: #3a5a9e;
}

.state-btn-secondary {
  background: #3e3e42;
  color: #ccc;
  border: 1px solid #555;
}

.state-btn-secondary:hover {
  background: #4e4e52;
}

/* ============================================================ */
/* 浮动返回按钮 */
/* ============================================================ */

.floating-back-btn {
  position: fixed;
  top: 0.75rem;
  left: 0.75rem;
  z-index: 100;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 50%;
  border: 1px solid #3e3e42;
  background: rgba(30, 30, 30, 0.9);
  backdrop-filter: blur(8px);
  color: #ccc;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.15s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.floating-back-btn:hover {
  background: rgba(75, 108, 183, 0.2);
  border-color: #4b6cb7;
  color: #4b6cb7;
}
</style>
