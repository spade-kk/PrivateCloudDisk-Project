<!--
  ============================================================
  CodePreviewView.vue — 代码文件独立预览页面（Monaco Editor 版）
  ============================================================

  企业级代码文件预览页面，基于 Microsoft Monaco Editor 提供
  与 VS Code 完全一致的代码阅读体验：
    - 内置 60+ 语言的精确语法高亮（TextMate 语法引擎）
    - TypeScript/JavaScript/CSS/HTML/JSON 原生 IntelliSense 悬停提示
    - 代码折叠、括号匹配、缩进参考线、小地图
    - 内置搜索（Ctrl+F）、跳转到行（Ctrl+G）
    - 行号、光标位置、缩进检测

  路由：/app/preview/code/:fileId
  查询参数：name - 文件名

  状态机：loading → error/not_found/completed
  ============================================================
-->
<template>
  <div class="code-preview-page">
    <!-- ============================================================
         加载状态 — 加载中
         ============================================================ -->
    <div v-if="loading" class="state-overlay">
      <div class="state-content">
        <div class="state-spinner">
          <div class="ring"></div>
        </div>
        <h2 class="state-title">正在加载代码文件...</h2>
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
          <i class="fa fa-file-code-o"></i>
        </div>
        <h2 class="state-title">文件不存在</h2>
        <p class="state-subtitle">无法找到该代码文件，文件可能已被删除或移动</p>
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
         正常预览状态 — 代码内容已加载完成
         ============================================================ -->
    <template v-else>
      <!-- 返回按钮（浮动在左上角） -->
      <button @click="goBack" class="floating-back-btn" title="返回">
        <i class="fa fa-arrow-left"></i>
      </button>

      <!-- Monaco Editor 代码预览 -->
      <MonacoPreview
        ref="monacoPreviewRef"
        :code-content="codeContent"
        :file-extension="fileExtension"
        :file-name="fileName"
        :file-size="fileSizeBytes"
        :read-only="true"
        theme="vs-dark"
        :font-size="14"
        :show-minimap="true"
        @ready="onEditorReady"
        @error="onEditorError"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
// ============================================================
// CodePreviewView.vue — 代码文件独立预览页面
// ============================================================
// 作为独立路由页面，负责：
//   1. 从路由参数获取文件 ID 和文件名
//   2. 通过下载授权 API 获取文件内容
//      【需求三变更】上述为原有行为记录；现已替换为 Preview Token + preview-content，
//      不再触发下载事件或最近访问记录。
//   3. 管理各个状态（loading / error / not_found / completed）
//   4. 将代码内容传递给 MonacoPreview 进行渲染
//
// 所有预览功能（语法高亮、悬停提示、搜索、代码折叠等）
// 由 MonacoPreview 组件（基于 Microsoft Monaco Editor）提供。
// ============================================================

import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toastStore'
import { fetchPreviewContentBlob, getPreviewErrorMessage } from '@/api/modules/previewContent'
import { getFileInfoApi } from '@/api/modules/files'
import MonacoPreview from '@/components/preview/code/MonacoPreview.vue'

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

/** 文件内容（原始文本） */
const codeContent = ref('')

/** 加载状态 */
const loading = ref(true)

/** 错误状态 */
const errorState = ref<{ title: string; message: string; detail?: string } | null>(null)

/** 文件不存在状态 */
const notFound = ref(false)

// ============================================================
// 模板引用
// ============================================================

const monacoPreviewRef = ref<InstanceType<typeof MonacoPreview> | null>(null)

// ============================================================
// 计算属性
// ============================================================

/** 文件扩展名（小写，不含点） */
const fileExtension = computed(() => {
  const name = fileName.value
  if (!name) return 'txt'
  const lastDot = name.lastIndexOf('.')
  if (lastDot === -1) return 'txt'
  return name.substring(lastDot + 1).toLowerCase()
})

// ============================================================
// 编辑器事件处理
// ============================================================

/** Monaco Editor 就绪回调 */
const onEditorReady = (): void => {
  // 编辑器已就绪，无需额外操作
}

/** Monaco Editor 错误回调 */
const onEditorError = (err: Error): void => {
  console.error('[CodePreviewView] 编辑器错误:', err)
}

// ============================================================
// 数据加载 — 获取文件内容
// ============================================================

/**
 * 加载代码文件内容
 *
 * 流程：
 *   1. 获取文件元数据（名称、大小）
 *   2. 创建下载授权令牌
 *   3. 通过令牌获取文件内容（Blob）
 *   4. 将 Blob 转换为 UTF-8 文本
 *   5. 释放下载授权令牌
 *
 * 【需求三变更】保留以上原流程注释用于回溯；实现中的“下载授权令牌”现对应职责隔离的
 * Preview Token，申请、读取、释放均由 previewContent.ts 统一编排。
 */
const loadFileContent = async (): Promise<void> => {
  loading.value = true
  errorState.value = null
  notFound.value = false

  try {
    // 1. 获取文件元数据
    const fileInfoRes = await getFileInfoApi(fileId.value, String(route.query.space || '') || undefined)
    const fileInfo = fileInfoRes.data || fileInfoRes
    if (fileInfo) {
      fileName.value = fileInfo.file_name || fileInfo.node_name || fileName.value
      fileSizeBytes.value = fileInfo.file_size || 0
    }

    /*
     * 需求一-2 / 三-1/2：
     * 原行为复用下载授权与下载接口，可能产生错误的下载审计；
     * 新行为由共享工具完成 Preview Token 申请、源内容读取和释放。
     */
    const blob = await fetchPreviewContentBlob(fileId.value, String(route.query.space || '') || undefined)

    // 4. 将 Blob 转换为文本
    const text = await blobToText(blob)

    // 5. 检查是否为有效的代码文件（大小限制：最大 10MB）
    if (text.length > 10 * 1024 * 1024) {
      throw new Error('文件过大（超过 10MB），无法在线预览')
    }

    codeContent.value = text

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
/* 代码预览页面 — VS Code Dark 主题 */
/* ============================================================ */

.code-preview-page {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #1e1e1e;
  overflow: hidden;
}

/* ============================================================ */
/* 浮动返回按钮 */
/* ============================================================ */

.floating-back-btn {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  background: rgba(37, 37, 38, 0.9);
  color: #cccccc;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  transition: all 0.15s;
  backdrop-filter: blur(8px);
}

.floating-back-btn:hover {
  background: rgba(62, 62, 66, 0.95);
  color: #ffffff;
  transform: scale(1.05);
}

/* ============================================================ */
/* 状态覆盖层（加载 / 错误 / 未找到） */
/* ============================================================ */

.state-overlay {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #1e1e1e;
}

.state-content {
  text-align: center;
  color: #cccccc;
  max-width: 480px;
  padding: 2rem;
}

.state-spinner {
  margin-bottom: 1.5rem;
}

.state-spinner .ring {
  width: 48px;
  height: 48px;
  border: 3px solid #3e3e42;
  border-top-color: #007acc;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.state-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.error-icon {
  color: #f48771;
}

.notfound-icon {
  color: #858585;
}

.state-title {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0 0 0.5rem;
  color: #e0e0e0;
}

.state-subtitle {
  font-size: 0.9rem;
  color: #858585;
  margin: 0 0 1.5rem;
  line-height: 1.5;
}

.state-meta {
  display: flex;
  gap: 0.5rem;
  align-items: flex-start;
  justify-content: center;
  margin-bottom: 1.5rem;
  font-size: 0.8rem;
  background: #2d2d2d;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  border: 1px solid #3e3e42;
  text-align: left;
  word-break: break-all;
}

.meta-label {
  color: #858585;
  flex-shrink: 0;
}

.meta-value {
  color: #cccccc;
  font-family: 'Consolas', 'Monaco', monospace;
}

.state-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
}

.state-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 1.5rem;
  border-radius: 0.5rem;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}

.state-btn-primary {
  background: linear-gradient(90deg, #4b6cb7 0%, #182848 100%);
  color: white;
}

.state-btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(75, 108, 183, 0.4);
}

.state-btn-secondary {
  background: #3e3e42;
  color: #cccccc;
}

.state-btn-secondary:hover {
  background: #4e4e52;
}
</style>
