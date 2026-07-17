<template>
  <!-- ============================================================ -->
  <!-- CodePreview.vue — 企业级代码文件预览组件（Monaco Editor 版） -->
  <!-- ============================================================ -->
  <!-- 基于 Microsoft Monaco Editor 提供与 VS Code 一致的代码预览体验。 -->
  <!-- 作为嵌入式组件使用（由 FilePreview.vue 等容器组件调用）， -->
  <!-- 通过 Props 接收代码内容，通过 Emits 通知父组件状态变化。 -->
  <!--                                                              -->
  <!-- 内置功能：                                                    -->
  <!--   - 60+ 语言精确语法高亮（TextMate 语法引擎）                 -->
  <!--   - TypeScript/JS/CSS/HTML/JSON 原生 IntelliSense 悬停提示    -->
  <!--   - 代码折叠、括号匹配、缩进参考线、小地图                    -->
  <!--   - 内置搜索（Ctrl+F）、跳转到行（Ctrl+G）                    -->
  <!--   - 工具栏：代码结构、复制、下载                              -->
  <!--   - 状态栏：语言、行数、字符数、光标位置                      -->
  <!-- ============================================================ -->
  <div class="code-preview-container">
    <!-- ======================================================== -->
    <!-- 加载状态 -->
    <!-- ======================================================== -->
    <div v-if="loading" class="preview-loading">
      <div class="loading-spinner">
        <i class="fa fa-spinner fa-spin fa-3x"></i>
      </div>
      <p class="loading-text">正在加载代码...</p>
    </div>

    <!-- ======================================================== -->
    <!-- 错误状态 -->
    <!-- ======================================================== -->
    <div v-else-if="errorMessage" class="preview-error">
      <i class="fa fa-exclamation-triangle fa-4x"></i>
      <h3>代码加载失败</h3>
      <p>{{ errorMessage }}</p>
      <button @click="$emit('retry')" class="retry-btn">
        <i class="fa fa-refresh"></i> 重新加载
      </button>
    </div>

    <!-- ======================================================== -->
    <!-- 正常预览状态 -->
    <!-- ======================================================== -->
    <MonacoPreview
      v-else-if="codeContent"
      ref="monacoPreviewRef"
      :code-content="codeContent"
      :file-extension="fileExtension"
      :file-name="fileName"
      :file-size="fileSizeBytes"
      :read-only="true"
      theme="vs-dark"
      :font-size="13"
      :show-minimap="false"
      @ready="onEditorReady"
    />
  </div>
</template>

<script setup lang="ts">
// ============================================================
// CodePreview.vue — 企业级代码文件预览组件
// ============================================================
// 作为嵌入式预览组件，由 FilePreview.vue 等容器调用。
// 通过 Props 接收代码内容，委托 Monaco Editor 进行渲染。
//
// Props 接口与 FilePreview.vue 兼容：
//   - codeContent: 代码文本内容
//   - fileName: 文件名（用于展示和语言检测）
//   - fileSize: 已格式化的文件大小字符串
//   - fileExtension: 文件扩展名
//   - loading: 加载状态
//   - errorMessage: 错误信息
// ============================================================

import { ref, computed, watch, nextTick } from 'vue'
import MonacoPreview from './code/MonacoPreview.vue'

// ============================================================
// Props — 保持与 FilePreview.vue 的兼容性
// ============================================================
const props = defineProps({
  /** 原始代码内容 */
  codeContent: {
    type: String,
    default: '',
  },
  /** 文件名 */
  fileName: {
    type: String,
    default: '',
  },
  /** 文件大小（已格式化） */
  fileSize: {
    type: String,
    default: '',
  },
  /** 文件扩展名 */
  fileExtension: {
    type: String,
    default: '',
  },
  /** 是否正在加载 */
  loading: {
    type: Boolean,
    default: false,
  },
  /** 错误信息 */
  errorMessage: {
    type: String,
    default: '',
  },
})

// ============================================================
// 事件定义
// ============================================================
const emit = defineEmits<{
  (e: 'retry'): void
  (e: 'loaded', data: { lineCount: number; charCount: number }): void
  (e: 'error', message: string): void
}>()

// ============================================================
// 模板引用
// ============================================================

const monacoPreviewRef = ref<InstanceType<typeof MonacoPreview> | null>(null)

// ============================================================
// 计算属性
// ============================================================

/** 估算文件大小（字节数），用于传递给 MonacoPreview */
const fileSizeBytes = computed(() => {
  // 从 props.fileSize 字符串解析，或从 codeContent 计算
  const content = props.codeContent
  if (content) {
    return new Blob([content]).size
  }
  return 0
})

/** 行数 */
const lineCount = computed(() => {
  if (!props.codeContent) return 0
  return props.codeContent.split('\n').length
})

/** 字符数 */
const charCount = computed(() => {
  return props.codeContent.length
})

// ============================================================
// 编辑器事件处理
// ============================================================

/** Monaco Editor 就绪 */
const onEditorReady = (): void => {
  // 编辑器就绪后通知父组件
  emit('loaded', {
    lineCount: lineCount.value,
    charCount: charCount.value,
  })
}

// ============================================================
// 内容变化监听
// ============================================================
watch(
  () => props.codeContent,
  (newContent) => {
    if (newContent && monacoPreviewRef.value) {
      // 编辑器已就绪，通过 setContent API 更新内容
      monacoPreviewRef.value.setContent(newContent)
      nextTick(() => {
        emit('loaded', {
          lineCount: lineCount.value,
          charCount: charCount.value,
        })
      })
    }
  }
)
</script>

<style scoped>
/* ============================================================ */
/* 代码预览容器 — VS Code Dark 主题 */
/* ============================================================ */

.code-preview-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #1e1e1e;
  overflow: hidden;
}

/* ---- 加载状态 ---- */
.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #007acc;
  gap: 1rem;
}

.loading-spinner {
  color: #007acc;
}

.loading-text {
  font-size: 0.95rem;
  opacity: 0.9;
  color: #cccccc;
}

/* ---- 错误状态 ---- */
.preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #f48771;
  text-align: center;
  padding: 2rem;
  gap: 0.75rem;
}

.preview-error h3 {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0;
  color: #e0e0e0;
}

.preview-error p {
  font-size: 0.9rem;
  color: #858585;
  margin: 0;
  max-width: 360px;
  line-height: 1.5;
}

.retry-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.5rem;
  padding: 0.5rem 1.25rem;
  border: 1px solid #007acc;
  border-radius: 4px;
  background: transparent;
  color: #007acc;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.15s;
}

.retry-btn:hover {
  background: rgba(0, 122, 204, 0.1);
}
</style>