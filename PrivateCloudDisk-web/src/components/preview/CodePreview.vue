<template>
  <div class="code-preview-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="preview-loading">
      <div class="loading-spinner">
        <i class="fa fa-spinner fa-spin fa-3x"></i>
      </div>
      <p class="loading-text">正在加载代码...</p>
    </div>

    <!-- 代码预览 -->
    <div v-else-if="codeContent" class="preview-content">
      <!-- 工具栏 -->
      <div class="preview-toolbar">
        <div class="toolbar-left">
          <span class="file-name truncate">{{ fileName }}</span>
          <span class="file-extension">{{ fileExtension.toUpperCase() }}</span>
        </div>
        <div class="toolbar-right">
          <button @click="copyCode" class="tool-btn" title="复制代码">
            <i class="fa fa-copy"></i>
          </button>
          <button @click="downloadCode" class="tool-btn" title="下载">
            <i class="fa fa-download"></i>
          </button>
        </div>
      </div>

      <!-- 代码显示区 -->
      <div class="code-viewer">
        <pre class="code-block" ref="codeBlock"><code
          :class="`language-${fileExtension}`"
          ref="codeElement"
        >{{ codeContent }}</code></pre>
      </div>

      <!-- 文件信息 -->
      <div class="code-info-bar">
        <div class="info-item">
          <i class="fa fa-file-code-o"></i>
          <span>{{ fileExtension.toUpperCase() }}</span>
        </div>
        <div class="info-item">
          <i class="fa fa-file"></i>
          <span>{{ fileSize }}</span>
        </div>
        <div class="info-item">
          <i class="fa fa-code"></i>
          <span>{{ lineCount }} 行</span>
        </div>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else class="preview-error">
      <i class="fa fa-exclamation-triangle fa-4x"></i>
      <h3>代码加载失败</h3>
      <p>{{ errorMessage }}</p>
      <button @click="$emit('retry')" class="retry-btn">
        <i class="fa fa-refresh"></i> 重新加载
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { useToastStore } from '@/stores/toastStore'

const props = defineProps({
  codeContent: {
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
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['retry', 'loaded', 'error'])

const toastStore = useToastStore()
const codeElement = ref(null)
const codeBlock = ref(null)

// 计算行数
const lineCount = computed(() => {
  if (!props.codeContent) return 0
  return props.codeContent.split('\n').length
})

// 复制代码
const copyCode = async () => {
  try {
    await navigator.clipboard.writeText(props.codeContent)
    toastStore.showToast('代码已复制到剪贴板', 'success')
  } catch (err) {
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = props.codeContent
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    toastStore.showToast('代码已复制到剪贴板', 'success')
  }
}

// 下载代码
const downloadCode = () => {
  const blob = new Blob([props.codeContent], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = props.fileName
  link.click()
  URL.revokeObjectURL(url)
}

// 应用语法高亮（使用基础高亮）
const applyHighlighting = () => {
  if (!codeElement.value) return

  const code = props.codeContent
  const ext = props.fileExtension.toLowerCase()

  // 简单的语法高亮（实际项目中可以使用 Prism.js 或 Highlight.js）
  let highlighted = code
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // 注释高亮
  highlighted = highlighted.replace(/(\/\/.*$)/gm, '<span class="comment">$1</span>')
  highlighted = highlighted.replace(/(\/\*[\s\S]*?\*\/)/g, '<span class="comment">$1</span>')
  highlighted = highlighted.replace(/(#.*$)/gm, '<span class="comment">$1</span>')

  // 字符串高亮
  highlighted = highlighted.replace(/("(?:[^"\\]|\\.)*")/g, '<span class="string">$1</span>')
  highlighted = highlighted.replace(/('(?:[^'\\]|\\.)*')/g, '<span class="string">$1</span>')
  highlighted = highlighted.replace(/(`(?:[^`\\]|\\.)*`)/g, '<span class="string">$1</span>')

  // 关键字高亮
  const keywords = {
    js: ['const', 'let', 'var', 'function', 'return', 'if', 'else', 'for', 'while', 'class', 'import', 'export', 'default', 'async', 'await', 'try', 'catch', 'throw', 'new', 'this', 'true', 'false', 'null', 'undefined'],
    ts: ['const', 'let', 'var', 'function', 'return', 'if', 'else', 'for', 'while', 'class', 'import', 'export', 'default', 'async', 'await', 'try', 'catch', 'throw', 'new', 'this', 'true', 'false', 'null', 'undefined', 'interface', 'type', 'enum', 'implements', 'extends', 'public', 'private', 'protected'],
    py: ['def', 'class', 'if', 'elif', 'else', 'for', 'while', 'return', 'import', 'from', 'as', 'try', 'except', 'finally', 'with', 'lambda', 'True', 'False', 'None', 'and', 'or', 'not', 'in', 'is', 'pass', 'break', 'continue', 'raise', 'yield', 'global', 'nonlocal'],
    java: ['public', 'private', 'protected', 'class', 'interface', 'extends', 'implements', 'static', 'final', 'void', 'int', 'String', 'boolean', 'return', 'if', 'else', 'for', 'while', 'new', 'this', 'super', 'try', 'catch', 'throw', 'throws', 'import', 'package', 'true', 'false', 'null'],
    cpp: ['int', 'void', 'char', 'float', 'double', 'bool', 'class', 'struct', 'public', 'private', 'protected', 'virtual', 'const', 'static', 'return', 'if', 'else', 'for', 'while', 'switch', 'case', 'break', 'continue', 'new', 'delete', 'try', 'catch', 'throw', 'include', 'define', 'namespace', 'using', 'template', 'typename', 'true', 'false', 'nullptr'],
    c: ['int', 'void', 'char', 'float', 'double', 'struct', 'enum', 'union', 'typedef', 'const', 'static', 'return', 'if', 'else', 'for', 'while', 'switch', 'case', 'break', 'continue', 'sizeof', 'include', 'define', 'ifdef', 'ifndef', 'endif', 'NULL', 'true', 'false'],
    go: ['func', 'package', 'import', 'var', 'const', 'type', 'struct', 'interface', 'return', 'if', 'else', 'for', 'range', 'switch', 'case', 'default', 'break', 'continue', 'go', 'defer', 'select', 'chan', 'map', 'make', 'new', 'nil', 'true', 'false'],
    rb: ['def', 'class', 'module', 'if', 'elsif', 'else', 'unless', 'case', 'when', 'for', 'while', 'until', 'do', 'end', 'begin', 'rescue', 'ensure', 'raise', 'return', 'yield', 'break', 'next', 'redo', 'retry', 'self', 'super', 'nil', 'true', 'false', 'require', 'include', 'extend', 'attr_accessor', 'attr_reader', 'attr_writer'],
    php: ['function', 'class', 'interface', 'trait', 'extends', 'implements', 'public', 'private', 'protected', 'static', 'final', 'const', 'return', 'if', 'else', 'elseif', 'for', 'foreach', 'while', 'do', 'switch', 'case', 'break', 'continue', 'try', 'catch', 'throw', 'new', 'use', 'namespace', 'require', 'include', 'echo', 'print', 'true', 'false', 'null', 'array', 'global'],
    sql: ['SELECT', 'FROM', 'WHERE', 'INSERT', 'UPDATE', 'DELETE', 'CREATE', 'DROP', 'ALTER', 'TABLE', 'INDEX', 'VIEW', 'DATABASE', 'SCHEMA', 'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER', 'ON', 'AND', 'OR', 'NOT', 'IN', 'LIKE', 'BETWEEN', 'IS', 'NULL', 'ORDER', 'BY', 'GROUP', 'HAVING', 'LIMIT', 'OFFSET', 'UNION', 'ALL', 'DISTINCT', 'AS', 'INTO', 'VALUES', 'SET', 'PRIMARY', 'KEY', 'FOREIGN', 'REFERENCES', 'CONSTRAINT', 'DEFAULT', 'CHECK', 'UNIQUE', 'CASCADE', 'true', 'false'],
    html: ['html', 'head', 'body', 'div', 'span', 'p', 'a', 'img', 'ul', 'ol', 'li', 'table', 'tr', 'td', 'th', 'form', 'input', 'button', 'select', 'option', 'textarea', 'label', 'script', 'style', 'link', 'meta', 'title', 'header', 'footer', 'nav', 'section', 'article', 'aside', 'main', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'br', 'hr'],
    css: ['color', 'background', 'font', 'margin', 'padding', 'border', 'width', 'height', 'display', 'position', 'top', 'right', 'bottom', 'left', 'flex', 'grid', 'overflow', 'text-align', 'line-height', 'font-size', 'font-weight', 'font-family', 'background-color', 'background-image', 'border-radius', 'box-shadow', 'transition', 'transform', 'opacity', 'visibility', 'z-index', 'float', 'clear', 'content', 'before', 'after', 'hover', 'active', 'focus', 'first-child', 'last-child', 'nth-child'],
    json: ['true', 'false', 'null'],
    xml: ['xml', 'version', 'encoding'],
    yaml: ['true', 'false', 'null', 'yes', 'no'],
    md: ['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'strong', 'em', 'code', 'pre', 'blockquote', 'ul', 'ol', 'li', 'a', 'img', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'hr', 'br']
  }

  const langKeywords = keywords[ext] || []

  langKeywords.forEach(keyword => {
    const regex = new RegExp(`\\b(${keyword})\\b`, 'g')
    highlighted = highlighted.replace(regex, '<span class="keyword">$1</span>')
  })

  // 数字高亮
  highlighted = highlighted.replace(/\b(\d+\.?\d*)\b/g, '<span class="number">$1</span>')

  codeElement.value.innerHTML = highlighted
}

watch(() => props.codeContent, () => {
  nextTick(() => {
    applyHighlighting()
    emit('loaded', {
      lineCount: lineCount.value
    })
  })
})

onMounted(() => {
  if (props.codeContent) {
    nextTick(() => {
      applyHighlighting()
      emit('loaded', {
        lineCount: lineCount.value
      })
    })
  }
})
</script>

<style scoped>
.code-preview-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #1e1e1e;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #6c8ef5;
}

.loading-spinner {
  margin-bottom: 1rem;
}

.loading-text {
  font-size: 0.95rem;
  opacity: 0.9;
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
  background: #252526;
  border-bottom: 1px solid #3e3e42;
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
  color: #cccccc;
  font-size: 0.95rem;
  font-family: 'Consolas', 'Monaco', monospace;
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
  background: #3e3e42;
  border-radius: 0.5rem;
  cursor: pointer;
  transition: all 0.2s;
  color: #cccccc;
  font-size: 0.9rem;
}

.tool-btn:hover {
  background: #4b6cb7;
  color: white;
}

.code-viewer {
  flex: 1;
  overflow: auto;
  padding: 1rem;
}

.code-block {
  margin: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 0.9rem;
  line-height: 1.6;
  color: #d4d4d4;
  background: #1e1e1e;
  white-space: pre;
  overflow-x: auto;
}

.code-block code {
  display: block;
  color: #d4d4d4;
}

/* 语法高亮样式 */
:deep(.keyword) {
  color: #569cd6;
  font-weight: 600;
}

:deep(.string) {
  color: #ce9178;
}

:deep(.number) {
  color: #b5cea8;
}

:deep(.comment) {
  color: #6a9955;
  font-style: italic;
}

.code-info-bar {
  display: flex;
  justify-content: center;
  gap: 2rem;
  padding: 0.75rem 1rem;
  background: #252526;
  border-top: 1px solid #3e3e42;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: #858585;
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
  color: #cccccc;
}

.preview-error p {
  color: #858585;
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
