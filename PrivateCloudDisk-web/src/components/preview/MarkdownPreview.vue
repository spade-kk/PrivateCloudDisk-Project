<template>
  <!-- ============================================================ -->
  <!-- MarkdownPreview.vue — 企业级 Markdown 文件预览组件             -->
  <!-- ============================================================ -->
  <!-- 基于 markdown-it + highlight.js + mermaid + katex 实现        -->
  <!-- 企业级 Markdown 渲染，支持：                                   -->
  <!--   - GitHub Flavored Markdown (GFM) 完整语法                   -->
  <!--   - 代码块语法高亮（highlight.js，190+ 语言）                 -->
  <!--   - Mermaid 流程图/时序图/甘特图/类图                         -->
  <!--   - KaTeX 数学公式（行内 + 块级）                             -->
  <!--   - 自动生成目录导航（TOC）                                   -->
  <!--   - 代码块一键复制                                           -->
  <!--   - 图片点击放大查看（灯箱）                                  -->
  <!--   - 全文搜索高亮                                             -->
  <!--   - 表格横向滚动                                             -->
  <!--   - 响应式布局（桌面端 + 移动端）                             -->
  <!--   - XSS 防护（DOMPurify 净化）                                -->
  <!--   - 大文件懒加载优化                                          -->
  <!--   - 阅读进度条                                               -->
  <!-- ============================================================ -->
  <div class="md-preview-root" ref="rootRef" :class="{ 'dark-mode': isDark }">
    <!-- ======================================================== -->
    <!-- 阅读进度条 -->
    <!-- ======================================================== -->
    <div class="md-progress-bar" :style="{ width: progressPercent + '%' }"></div>

    <!-- ======================================================== -->
    <!-- 顶部工具栏 -->
    <!-- ======================================================== -->
    <div class="md-toolbar" ref="toolbarRef">
      <!-- 左侧：文件信息 -->
      <div class="md-toolbar-left">
        <span class="md-file-icon">
          <i class="fa fa-file-text-o"></i>
        </span>
        <span class="md-file-name" :title="fileName">{{ fileName || 'README.md' }}</span>
        <span class="md-badge">MD</span>
      </div>

      <!-- 中间：搜索框 -->
      <div class="md-toolbar-center" v-if="showSearch">
        <div class="md-search-box">
          <i class="fa fa-search md-search-icon"></i>
          <input
            ref="searchInputRef"
            v-model="searchQuery"
            type="text"
            class="md-search-input"
            placeholder="搜索文档内容..."
            @input="onSearchInput"
            @keydown.enter="searchNext"
            @keydown.escape="closeSearch"
          />
          <span class="md-search-count" v-if="searchQuery">
            {{ searchMatchIndex + 1 }} / {{ searchMatchCount }}
          </span>
          <button class="md-search-nav-btn" @click="searchPrev" title="上一个">
            <i class="fa fa-chevron-up"></i>
          </button>
          <button class="md-search-nav-btn" @click="searchNext" title="下一个">
            <i class="fa fa-chevron-down"></i>
          </button>
          <button class="md-search-close-btn" @click="closeSearch" title="关闭">
            <i class="fa fa-times"></i>
          </button>
        </div>
      </div>

      <!-- 右侧：操作按钮 -->
      <div class="md-toolbar-right">
        <button class="md-tool-btn" @click="toggleSearch" title="搜索 (Ctrl+F)">
          <i class="fa fa-search"></i>
        </button>
        <button class="md-tool-btn" :class="{ active: showOutline }" @click="toggleOutline" title="目录导航">
          <i class="fa fa-list-ul"></i>
        </button>
        <button class="md-tool-btn" @click="toggleDarkMode" title="切换主题">
          <i :class="isDark ? 'fa fa-sun-o' : 'fa fa-moon-o'"></i>
        </button>
        <div class="md-toolbar-divider"></div>
        <button class="md-tool-btn" @click="copyDocument" title="复制全文">
          <i :class="copyIcon"></i>
        </button>
        <button class="md-tool-btn" @click="downloadMarkdown" title="下载源文件">
          <i class="fa fa-download"></i>
        </button>
        <button class="md-tool-btn" @click="exportPdf" title="导出 PDF">
          <i class="fa fa-file-pdf-o"></i>
        </button>
        <button class="md-tool-btn" @click="toggleFullscreen" title="全屏">
          <i :class="isFullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i>
        </button>
      </div>
    </div>

    <!-- ======================================================== -->
    <!-- 主体区域 -->
    <!-- ======================================================== -->
    <div class="md-body">
      <!-- ==================================================== -->
      <!-- 目录侧边栏 -->
      <!-- ==================================================== -->
      <Transition name="outline-slide">
        <div v-if="showOutline" class="md-outline-panel">
          <div class="md-outline-header">
            <span>目录</span>
            <button class="md-outline-close" @click="showOutline = false">
              <i class="fa fa-times"></i>
            </button>
          </div>
          <div class="md-outline-content" ref="outlineContentRef">
            <div v-if="outlineItems.length === 0" class="md-outline-empty">
              暂无标题
            </div>
            <div
              v-for="item in outlineItems"
              :key="item.id"
              class="md-outline-item"
              :class="{ active: item.id === activeOutlineId }"
              :style="{ paddingLeft: (item.level - 1) * 16 + 12 + 'px' }"
              @click="scrollToHeading(item.id)"
            >
              <span class="md-outline-bullet" :class="'level-' + item.level"></span>
              <span class="md-outline-text">{{ item.text }}</span>
            </div>
          </div>
        </div>
      </Transition>

      <!-- ==================================================== -->
      <!-- 主内容区 -->
      <!-- ==================================================== -->
      <div class="md-content-wrapper" ref="contentWrapperRef" @scroll="onContentScroll">
        <!-- 加载状态 -->
        <div v-if="isBusy" class="md-loading" aria-live="polite" aria-busy="true">
          <div class="md-loading-spinner">
            <div class="ring"></div>
          </div>
          <p>正在渲染文档...</p>
        </div>

        <!-- 错误状态 -->
        <div v-else-if="errorMessage" class="md-error">
          <i class="fa fa-exclamation-triangle"></i>
          <h3>渲染失败</h3>
          <p>{{ errorMessage }}</p>
          <button @click="retryRender" class="md-retry-btn">
            <i class="fa fa-refresh"></i> 重新渲染
          </button>
        </div>

        <!-- Markdown 渲染内容 -->
        <div
          v-else
          class="md-content"
          ref="contentRef"
          v-safe-html="renderedHtml"
          @click="onContentClick"
        ></div>
      </div>
    </div>

    <!-- ======================================================== -->
    <!-- 底部状态栏 -->
    <!-- ======================================================== -->
    <div class="md-statusbar">
      <div class="md-statusbar-left">
        <span class="md-status-item">
          <i class="fa fa-file-text-o"></i>
          {{ wordCount.toLocaleString() }} 字
        </span>
        <span class="md-status-divider">|</span>
        <span class="md-status-item">
          <i class="fa fa-align-left"></i>
          {{ lineCount.toLocaleString() }} 行
        </span>
        <span class="md-status-divider">|</span>
        <span class="md-status-item">
          <i class="fa fa-clock-o"></i>
          约 {{ readTime }} 分钟阅读
        </span>
      </div>
      <div class="md-statusbar-right">
        <span class="md-status-item" v-if="fileSize">
          <i class="fa fa-hdd-o"></i>
          {{ fileSize }}
        </span>
        <span class="md-status-divider">|</span>
        <span class="md-status-item">
          <i class="fa fa-file-code-o"></i>
          Markdown
        </span>
      </div>
    </div>

    <!-- ======================================================== -->
    <!-- 图片灯箱 -->
    <!-- ======================================================== -->
    <Teleport to="body">
      <Transition name="lightbox-fade">
        <div v-if="lightboxVisible" class="md-lightbox-overlay" @click.self="closeLightbox">
          <div class="md-lightbox-content">
            <img :src="lightboxSrc" :alt="lightboxAlt" class="md-lightbox-img" />
            <div class="md-lightbox-info" v-if="lightboxAlt">
              {{ lightboxAlt }}
            </div>
          </div>
          <button class="md-lightbox-close" @click="closeLightbox">
            <i class="fa fa-times"></i>
          </button>
          <button class="md-lightbox-prev" @click.stop="lightboxPrev" v-if="lightboxImages.length > 1">
            <i class="fa fa-chevron-left"></i>
          </button>
          <button class="md-lightbox-next" @click.stop="lightboxNext" v-if="lightboxImages.length > 1">
            <i class="fa fa-chevron-right"></i>
          </button>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
// ============================================================
// MarkdownPreview.vue — 企业级 Markdown 渲染组件
// ============================================================
// 核心渲染流程:
//   1. 接收 Markdown 原始内容（Props）
//   2. 使用 markdown-it 解析为 HTML
//   3. 使用 DOMPurify 净化 HTML（防 XSS）
//   4. 渲染到 DOM
//   5. 后处理：初始化代码高亮、Mermaid 图表、KaTeX 公式
//   6. 后处理：注入代码复制按钮、图片点击事件
//
// 降级策略:
//   - markdown-it 不可用 → 使用基础正则替换
//   - highlight.js 不可用 → 代码块无高亮
//   - Mermaid 不可用 → 显示原始代码块
//   - KaTeX 不可用 → 显示原始公式文本
// ============================================================

import { ref, computed, watch, onMounted, onUnmounted, nextTick, render } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import { sanitizeHtml } from '@/utils/sanitize'
import { loadMarkdownItWithPlugins } from '@/utils/markdownCdn'
import { loadMermaid } from '@/utils/mermaidCdn'
import { loadKaTeX } from '@/utils/katexCdn'
import { loadHighlight, getHighlightSync, injectHighlightTheme } from '@/utils/highlightCdn'
import Loading from 'element-plus/es/components/loading/src/service.mjs'

// ============================================================
// Props
// ============================================================
const props = defineProps({
  /** Markdown 原始内容 */
  markdownContent: {
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
  /** 是否启用暗色模式 */
  darkMode: {
    type: Boolean,
    default: true,
  },
})

// ============================================================
// Emits
// ============================================================
const emit = defineEmits<{
  (e: 'retry'): void
  (e: 'ready', data: { wordCount: number; lineCount: number; readTime: number }): void
  (e: 'error', message: string): void
}>()

// ============================================================
// 外部依赖
// ============================================================
const toastStore = useToastStore()

// ============================================================
// 模板引用
// ============================================================
const rootRef = ref<HTMLElement | null>(null)
const toolbarRef = ref<HTMLElement | null>(null)
const contentWrapperRef = ref<HTMLElement | null>(null)
const contentRef = ref<HTMLElement | null>(null)
const outlineContentRef = ref<HTMLElement | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)

// ============================================================
// 响应式状态
// ============================================================

// UI 状态
const isDark = ref(props.darkMode)
const isFullscreen = ref(false)
// 需求二-1：桌面端默认展示左侧目录；移动端保持按需打开，避免遮挡正文。
const showOutline = ref(typeof window === 'undefined' ? true : window.innerWidth > 768)
const showSearch = ref(false)
const copyIcon = ref('fa fa-copy')
const progressPercent = ref(0)

// 搜索状态
const searchQuery = ref('')
const searchMatchIndex = ref(0)
const searchMatchCount = ref(0)
const searchHighlights: HTMLElement[] = []

// 目录状态
const outlineItems = ref<{ id: string; text: string; level: number }[]>([])
const activeOutlineId = ref('')

// 图片灯箱
const lightboxVisible = ref(false)
const lightboxSrc = ref('')
const lightboxAlt = ref('')
const lightboxImages = ref<{ src: string; alt: string }[]>([])
const lightboxCurrentIndex = ref(0)

// 渲染状态
const renderedHtml = ref('')
const rendering = ref(false)
const libraryLoading = ref(true)

// markdown-it 实例
let md: any = null

/** 外部内容加载、CDN 初始化和当前渲染统一为一个可观测加载状态，避免弱网下出现空白正文。 */
const isBusy = computed(() => props.loading || libraryLoading.value || rendering.value)

// IntersectionObserver 用于目录高亮
let headingObserver: IntersectionObserver | null = null

// ============================================================
// 计算属性
// ============================================================

/** 字数统计 */
const wordCount = computed(() => {
  if (!props.markdownContent) return 0
  // 中文字符数 + 英文单词数
  const text = props.markdownContent
  const chineseChars = (text.match(/[\u4e00-\u9fff]/g) || []).length
  const englishWords = (text.match(/[a-zA-Z]+/g) || []).length
  return chineseChars + englishWords
})

/** 行数 */
const lineCount = computed(() => {
  if (!props.markdownContent) return 0
  return props.markdownContent.split('\n').length
})

/** 预估阅读时间（分钟） */
const readTime = computed(() => {
  // 中文约 400 字/分钟，英文约 200 词/分钟
  const minutes = Math.ceil(wordCount.value / 400)
  return Math.max(1, minutes)
})

// ============================================================
// markdown-it 初始化
// ============================================================

/**
 * 初始化 markdown-it 解析器
 *
 * 实现说明（CDN 化后）：
 *   - markdown-it 及所有插件通过 CDN 动态加载（markdownCdn.ts）
 *   - highlight.js 通过 CDN 加载（highlightCdn.ts），传入 markdown-it 的 highlight 回调
 *   - 加载失败时返回 null，触发降级渲染
 *
 * 插件列表:
 *   - markdown-it-anchor: 为标题添加锚点 ID
 *   - markdown-it-table-of-contents: 生成 [TOC] 目录
 *   - markdown-it-emoji: Emoji 支持 :smile:
 *   - markdown-it-task-lists: 任务列表
 *   - highlight.js: 代码语法高亮（通过 highlightFn 注入）
 */
const initMarkdownIt = async (): Promise<void> => {
  try {
    // 先并行触发 highlight.js 加载（不阻塞 markdown-it 创建）
    // 因为 highlight 回调是同步被 markdown-it 调用的，
    // 提前加载可让首次渲染时 highlight 已就绪
    const hljsPromise = loadHighlight().catch(() => null)

    // 通过 CDN 加载 markdown-it + 所有插件，并注入 highlight 回调
    md = await loadMarkdownItWithPlugins({
      /*
       * 需求二-2：
       * 原回调返回完整 <pre><code>，会与 markdown-it 的 fence 包装重复嵌套；
       * 新行为让 markdown-it 先输出标准代码节点，再在 DOM 挂载后统一调用 highlightElement。
       */
      
    })

    // AUDIT FIX [2.3]（需求一-5）:
    // 原行为没有等待高亮库，首次 render 永远走无高亮降级；新行为等待同一并行 Promise，
    // 失败仍返回 null，不影响 Markdown 主渲染。
    await hljsPromise
  } catch (err) {
    console.warn('[MarkdownPreview] markdown-it CDN 加载失败，使用基础渲染:', err)
    md = null
  }
}

/**
 * 使用 highlight.js 进行代码语法高亮
 *
 * 实现说明（CDN 化后）：
 *   - 优先使用同步已就绪的 highlight.js 实例（getHighlightSync）
 *   - 若未就绪则降级为基础 HTML 转义
 *   - 异步触发 loadHighlight 以便下次调用时可用
 */
const highlightCode = (code: string, lang: string): string => {
  // 同步获取已加载的 highlight.js 实例
  const hljs = getHighlightSync()

  if (hljs) {
    try {
      if (lang && hljs.getLanguage(lang)) {
        const result = hljs.highlight(code, { language: lang, ignoreIllegals: true })
        return `<pre><code class="hljs language-${lang}">${result.value}</code></pre>`
      }
      // 自动检测语言
      try {
        const result = hljs.highlightAuto(code)
        return `<pre><code class="hljs">${result.value}</code></pre>`
      } catch { /* 自动检测失败 */ }
    } catch { /* 高亮失败 */ }
  } else {
    // 异步触发加载（首次调用时）
    void loadHighlight().catch(() => {})
  }

  // 降级：基础 HTML 转义
  const escaped = code
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  const langClass = lang ? ` class="language-${lang}"` : ''
  return `<pre><code${langClass}>${escaped}</code></pre>`
}

// ============================================================
// Markdown 渲染
// ============================================================

/**
 * 渲染 Markdown → HTML
 *
 * 流程:
 *   1. 使用 markdown-it 解析 Markdown
 *   2. 使用 DOMPurify 净化 HTML
 *   3. 设置 renderedHtml 触发 Vue 渲染
 *   4. 后处理（代码复制按钮、Mermaid、KaTeX 等）
 */
const renderMarkdown = async (): Promise<void> => {
  if (!props.markdownContent) {
    renderedHtml.value = ''
    return
  }

  rendering.value = true

  try {
    let html = ''

    if (md) {
      // 使用 markdown-it 解析
      html = md.render(props.markdownContent)
    } else {
      // 降级：基础正则替换
      html = fallbackRender(props.markdownContent)
    }

    // XSS 净化
    html = sanitizeHtml(html)

    renderedHtml.value = html
    //提前关闭渲染中 因为md-content标签 用了else-if条件渲染 如果不停止 另外isBusy也要停止 那么contentRef永远为空
    rendering.value = false

    // 等待 DOM 更新
    await nextTick()

    // 需求二-1：先为标题生成稳定 ID，再初始化 IntersectionObserver。
    extractOutline()

    // 后处理
    await postProcess()

    // 发送就绪事件
    emit('ready', {
      wordCount: wordCount.value,
      lineCount: lineCount.value,
      readTime: readTime.value,
    })
  } catch (err: any) {
    console.error('[MarkdownPreview] 渲染失败:', err)
    emit('error', err?.message || 'Markdown 渲染失败')
  } finally {
    rendering.value = false
  }
}

/** 降级渲染（无 markdown-it 时） */
const fallbackRender = (content: string): string => {
  // AUDIT FIX [2.3]（需求一-5）:
  // 降级渲染必须先整体转义用户输入；原行为在正则替换前保留原始 HTML，CDN 失败时会扩大 XSS 面。
  let html = content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // 代码块
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (_m, lang: string, code: string) => {
    return `<pre><code class="language-${lang || ''}">${code}</code></pre>`
  })

  // 行内代码
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')

  // 标题
  html = html.replace(/^###### (.+)$/gm, '<h6>$1</h6>')
  html = html.replace(/^##### (.+)$/gm, '<h5>$1</h5>')
  html = html.replace(/^#### (.+)$/gm, '<h4>$1</h4>')
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>')
  html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>')
  html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>')

  // 加粗/斜体
  html = html.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>')
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*(.+?)\*/g, '<em>$1</em>')

  // 图片
  html = html.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '<img src="$2" alt="$1" loading="lazy">')

  // 链接（必须在图片之后处理，避免 ![alt](url) 被链接规则提前吞掉）
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
  // 段落
  html = html.replace(/\n\n+/g, '</p><p>')
  html = `<p>${html}</p>`

  return html
}

// ============================================================
// DOM 后处理
// ============================================================

/**
 * 后处理渲染后的 DOM
 *   1. 注入代码块复制按钮
 *   2. 初始化 Mermaid 图表
 *   3. 初始化 KaTeX 数学公式
 *   4. 绑定图片点击事件（灯箱）
 *   5. 为表格添加横向滚动容器
 *   6. 设置标题 IntersectionObserver
 */
const postProcess = async (): Promise<void> => {
  const el = contentRef.value
  if (!el) return

  // 1. 内容节点已挂载后逐块调用 highlight.js，避免首次渲染时序遗漏。
  await highlightRenderedBlocks(el)

  // 2. 注入代码块复制按钮
  injectCopyButtons(el)

  // 3. 为表格添加横向滚动容器
  wrapTables(el)

  // 4. 绑定图片点击事件
  bindImageClick(el)

  // 5. 初始化 Mermaid 图表
  await initMermaid(el)

  // 6. 初始化 KaTeX 公式
  await initKaTeX(el)

  // 7. 设置标题观察器
  setupHeadingObserver(el)
}

/**
 * 对最终 DOM 中的 fenced code 逐块执行 highlight.js。
 *
 * 需求二-2：只处理尚未高亮的节点；未知语言由 highlightAuto 降级，
 * 单个代码块异常不会中断目录、Mermaid 或正文渲染。
 */
const highlightRenderedBlocks = async (el: HTMLElement): Promise<void> => {
  const hljs = getHighlightSync() ?? await loadHighlight().catch(() => null)
  if (!hljs) return

  const codeBlocks = el.querySelectorAll<HTMLElement>('pre code')
  codeBlocks.forEach((code) => {
    if (code.dataset.highlighted === 'yes') return
    try {
      hljs.highlightElement(code)
    } catch {
      // 保留 markdown-it 已安全转义的原始代码文本。
    }
  })
}

/** 为代码块注入复制按钮 */
const injectCopyButtons = (el: HTMLElement): void => {
  const preBlocks = el.querySelectorAll('pre')
  preBlocks.forEach((pre) => {
    // 避免重复注入
    if (pre.querySelector('.code-copy-btn')) return

    const wrapper = document.createElement('div')
    wrapper.className = 'code-block-wrapper'

    // 语言标签
    const codeEl = pre.querySelector('code')
    let lang = ''
    if (codeEl) {
      const classes = codeEl.className.split(' ')
      for (const cls of classes) {
        if (cls.startsWith('language-')) {
          lang = cls.replace('language-', '')
          break
        }
      }
    }
    if (lang) {
      const langLabel = document.createElement('span')
      langLabel.className = 'code-lang-label'
      langLabel.textContent = lang
      wrapper.appendChild(langLabel)
    }

    // 复制按钮
    const copyBtn = document.createElement('button')
    copyBtn.className = 'code-copy-btn'
    copyBtn.innerHTML = '<i class="fa fa-copy"></i> 复制'
    copyBtn.onclick = () => {
      const code = codeEl?.textContent || pre.textContent || ''
      navigator.clipboard.writeText(code).then(() => {
        copyBtn.innerHTML = '<i class="fa fa-check"></i> 已复制'
        setTimeout(() => {
          copyBtn.innerHTML = '<i class="fa fa-copy"></i> 复制'
        }, 2000)
      }).catch(() => {
        toastStore.showToast('复制失败', 'error')
      })
    }
    wrapper.appendChild(copyBtn)

    // 将 pre 包裹在 wrapper 中
    pre.parentNode?.insertBefore(wrapper, pre)
    wrapper.appendChild(pre)
  })
}

/** 为表格添加横向滚动容器 */
const wrapTables = (el: HTMLElement): void => {
  const tables = el.querySelectorAll('table')
  tables.forEach((table) => {
    if (table.parentElement?.classList.contains('table-wrapper')) return
    const wrapper = document.createElement('div')
    wrapper.className = 'table-wrapper'
    table.parentNode?.insertBefore(wrapper, table)
    wrapper.appendChild(table)
  })
}

/** 绑定图片点击事件 */
const bindImageClick = (el: HTMLElement): void => {
  // 收集所有图片
  lightboxImages.value = []
  const images = el.querySelectorAll('img')
  images.forEach((img, index) => {
    lightboxImages.value.push({
      src: img.getAttribute('src') || '',
      alt: img.getAttribute('alt') || '',
    })

    img.style.cursor = 'zoom-in'
    img.addEventListener('click', (e) => {
      e.stopPropagation()
      lightboxCurrentIndex.value = index
      lightboxSrc.value = img.getAttribute('src') || ''
      lightboxAlt.value = img.getAttribute('alt') || ''
      lightboxVisible.value = true
    })
  })
}

/** 初始化 Mermaid 图表 */
const initMermaid = async (el: HTMLElement): Promise<void> => {
  const mermaidBlocks = el.querySelectorAll('.language-mermaid')
  if (mermaidBlocks.length === 0) return

  try {
    // 通过 CDN 加载 mermaid
    const mermaid = await loadMermaid()
    mermaid.initialize({
      startOnLoad: false,
      theme: isDark.value ? 'dark' : 'default',
      securityLevel: 'sandbox',
      themeVariables: isDark.value ? {
        primaryColor: '#4b6cb7',
        primaryTextColor: '#e0e0e0',
        lineColor: '#4b6cb7',
      } : {},
    })

    let mermaidIndex = 0
    for (const block of mermaidBlocks) {
      try {
        const code = block.textContent || ''
        const id = `mermaid-${Date.now()}-${mermaidIndex++}`
        const { svg } = await mermaid.render(id, code)
        const container = document.createElement('div')
        container.className = 'mermaid-container'
        container.innerHTML = svg
        const pre = block.closest('pre')
        if (pre) {
          pre.parentNode?.replaceChild(container, pre)
        } else {
          block.parentNode?.replaceChild(container, block)
        }
      } catch {
        // Mermaid 渲染失败，保留原始代码块
      }
    }
  } catch {
    // Mermaid CDN 加载失败
  }
}

/** 初始化 KaTeX 数学公式 */
const initKaTeX = async (_el: HTMLElement): Promise<void> => {
  // 通过 CDN 加载 KaTeX
  // 加载失败则跳过公式渲染（保留原始公式文本）
  let katex: typeof import('katex').default
  try {
    katex = await loadKaTeX()
  } catch {
    // KaTeX CDN 加载失败
    return
  }

  const el = contentRef.value
  if (!el) return

  // 处理块级公式 $$...$$
  const html = el.innerHTML
  if (html.includes('$$')) {
    const newHtml = html.replace(/\$\$([\s\S]*?)\$\$/g, (_m: string, formula: string) => {
      try {
        return katex.renderToString(formula.trim(), {
          throwOnError: false,
          displayMode: true,
        })
      } catch {
        return _m
      }
    })
    el.innerHTML = newHtml
  }

  // 处理行内公式 $...$
  // 注意：避免匹配代码块中的 $
  const paragraphs = el.querySelectorAll('p, li, td, th')
  for (const p of paragraphs) {
    if (p.querySelector('code')) continue // 跳过包含代码的元素
    const text = p.innerHTML
    if (text.includes('$') && !text.includes('$$')) {
      const newText = text.replace(/\$([^$]+)\$/g, (_m: string, formula: string) => {
        try {
          return katex.renderToString(formula.trim(), {
            throwOnError: false,
            displayMode: false,
          })
        } catch {
          return _m
        }
      })
      p.innerHTML = newText
    }
  }
}

// ============================================================
// 目录提取
// ============================================================

/** 从渲染后的 DOM 中提取标题，生成目录 */
const extractOutline = (): void => {
  const el = contentRef.value
  if (!el) return

  const headings = el.querySelectorAll('h1, h2, h3, h4')
  const items: { id: string; text: string; level: number }[] = []

  const usedIds = new Set<string>()
  headings.forEach((h, index) => {
    const level = parseInt(h.tagName.charAt(1), 10)
    const text = h.textContent?.replace(/#$/, '').trim() || ''
    // 确保标题有 ID
    let id = h.id
    if (!id) {
      id = text
        .toLowerCase()
        .replace(/[^\w\u4e00-\u9fff]+/g, '-')
        .replace(/^-+|-+$/g, '')
    }
    const baseId = id || `heading-${index + 1}`
    id = baseId
    let duplicateIndex = 2
    while (usedIds.has(id)) {
      id = `${baseId}-${duplicateIndex++}`
    }
    usedIds.add(id)
    h.id = id
    items.push({ id, text, level })
  })

  outlineItems.value = items
  activeOutlineId.value = items[0]?.id || ''
}

/** 设置 IntersectionObserver 跟踪当前可见标题 */
const setupHeadingObserver = (el: HTMLElement): void => {
  headingObserver?.disconnect()

  const headings = el.querySelectorAll('h1, h2, h3, h4')
  if (headings.length === 0) return

  headingObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          activeOutlineId.value = entry.target.id
          break
        }
      }
    },
    { rootMargin: '-80px 0px -80% 0px' }
  )

  headings.forEach((h) => headingObserver?.observe(h))
}

// ============================================================
// 工具栏操作
// ============================================================

/** 切换搜索 */
const toggleSearch = (): void => {
  showSearch.value = !showSearch.value
  if (showSearch.value) {
    nextTick(() => searchInputRef.value?.focus())
  } else {
    clearSearch()
  }
}

/** 关闭搜索 */
const closeSearch = (): void => {
  showSearch.value = false
  clearSearch()
}

/** 清除搜索高亮 */
const clearSearch = (): void => {
  searchQuery.value = ''
  searchMatchIndex.value = 0
  searchMatchCount.value = 0
  removeSearchHighlights()
}

/** 搜索输入处理 */
const onSearchInput = (): void => {
  performSearch()
}

/** 执行搜索 */
const performSearch = (requestedIndex = 0): void => {
  removeSearchHighlights()

  if (!searchQuery.value || !contentRef.value) {
    searchMatchCount.value = 0
    return
  }

  const query = searchQuery.value.toLowerCase()
  const walker = document.createTreeWalker(
    contentRef.value,
    NodeFilter.SHOW_TEXT,
    {
      acceptNode: (node) => {
        // 跳过代码块和脚本/样式
        const parent = node.parentElement
        if (!parent) return NodeFilter.FILTER_REJECT
        if (parent.closest('pre, code, script, style, .code-copy-btn, .code-lang-label')) {
          return NodeFilter.FILTER_REJECT
        }
        return node.textContent?.toLowerCase().includes(query)
          ? NodeFilter.FILTER_ACCEPT
          : NodeFilter.FILTER_REJECT
      },
    }
  )

  const matches: { node: Text; startIndex: number }[] = []
  let node: Text | null
  while ((node = walker.nextNode() as Text | null)) {
    const text = node.textContent || ''
    let idx = text.toLowerCase().indexOf(query)
    while (idx !== -1) {
      matches.push({ node, startIndex: idx })
      idx = text.toLowerCase().indexOf(query, idx + 1)
    }
  }

  searchMatchCount.value = matches.length
  searchMatchIndex.value = matches.length
    ? Math.min(Math.max(requestedIndex, 0), matches.length - 1)
    : 0

  if (matches.length > 0) {
    highlightSearchMatch(matches[searchMatchIndex.value])
  }
}

/** 高亮当前搜索匹配 */
const highlightSearchMatch = (match: { node: Text; startIndex: number }): void => {
  const range = document.createRange()
  range.setStart(match.node, match.startIndex)
  range.setEnd(match.node, match.startIndex + searchQuery.value.length)

  const span = document.createElement('span')
  span.className = 'search-highlight active'
  range.surroundContents(span)

  searchHighlights.push(span)
  span.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

/** 移除所有搜索高亮 */
const removeSearchHighlights = (): void => {
  searchHighlights.forEach((span) => {
    const parent = span.parentNode
    if (parent) {
      parent.replaceChild(document.createTextNode(span.textContent || ''), span)
      parent.normalize()
    }
  })
  searchHighlights.length = 0
}

/** 下一个搜索结果 */
const searchNext = (): void => {
  if (searchMatchCount.value === 0) return
  // AUDIT FIX [2.3]（需求一-6）: 原 performSearch 会把索引重新归零，上一项/下一项按钮永远停在首项。
  performSearch((searchMatchIndex.value + 1) % searchMatchCount.value)
}

/** 上一个搜索结果 */
const searchPrev = (): void => {
  if (searchMatchCount.value === 0) return
  performSearch((searchMatchIndex.value - 1 + searchMatchCount.value) % searchMatchCount.value)
}

/** 切换目录 */
const toggleOutline = (): void => {
  showOutline.value = !showOutline.value
}

/** 滚动到指定标题 */
const scrollToHeading = (id: string): void => {
  const el = contentRef.value?.querySelector(`#${CSS.escape(id)}`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

/** 切换暗色模式 */
const toggleDarkMode = (): void => {
  isDark.value = !isDark.value
  // 切换 Highlight 主题
  injectHighlightTheme(isDark.value ? 'vs2015' : 'default')
}

/** 切换全屏 */
const toggleFullscreen = (): void => {
  if (!rootRef.value) return
  if (!isFullscreen.value) {
    rootRef.value.requestFullscreen?.()
    isFullscreen.value = true
  } else {
    document.exitFullscreen?.()
    isFullscreen.value = false
  }
}

/** 复制全文 */
const copyDocument = async (): Promise<void> => {
  try {
    await navigator.clipboard.writeText(props.markdownContent)
    copyIcon.value = 'fa fa-check'
    toastStore.showToast('文档已复制到剪贴板', 'success')
    setTimeout(() => { copyIcon.value = 'fa fa-copy' }, 2000)
  } catch {
    toastStore.showToast('复制失败', 'error')
  }
}

/** 下载 Markdown 源文件 */
const downloadMarkdown = (): void => {
  const blob = new Blob([props.markdownContent], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = props.fileName || 'README.md'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  toastStore.showToast('下载已开始', 'success')
}

/** 导出 PDF */
const exportPdf = (): void => {
  window.print()
}

/** 重新渲染 */
const retryRender = (): void => {
  emit('retry')
  renderMarkdown()
}

// ============================================================
// 图片灯箱
// ============================================================

const closeLightbox = (): void => {
  lightboxVisible.value = false
}

const lightboxPrev = (): void => {
  lightboxCurrentIndex.value = (lightboxCurrentIndex.value - 1 + lightboxImages.value.length) % lightboxImages.value.length
  const img = lightboxImages.value[lightboxCurrentIndex.value]
  lightboxSrc.value = img.src
  lightboxAlt.value = img.alt
}

const lightboxNext = (): void => {
  lightboxCurrentIndex.value = (lightboxCurrentIndex.value + 1) % lightboxImages.value.length
  const img = lightboxImages.value[lightboxCurrentIndex.value]
  lightboxSrc.value = img.src
  lightboxAlt.value = img.alt
}

// ============================================================
// 滚动处理
// ============================================================

const onContentScroll = (): void => {
  if (!contentWrapperRef.value) return
  const { scrollTop, scrollHeight, clientHeight } = contentWrapperRef.value
  progressPercent.value = Math.round((scrollTop / (scrollHeight - clientHeight)) * 100)
}

/** 内容区点击（用于关闭搜索等） */
const onContentClick = (): void => {
  // 预留
}

// ============================================================
// 键盘快捷键
// ============================================================

const handleKeydown = (e: KeyboardEvent): void => {
  const isCtrl = e.ctrlKey || e.metaKey

  if (isCtrl && e.key === 'f') {
    e.preventDefault()
    toggleSearch()
  } else if (e.key === 'Escape') {
    if (showSearch.value) {
      closeSearch()
    } else if (lightboxVisible.value) {
      closeLightbox()
    }
  }
}

// ============================================================
// 全屏变化监听
// ============================================================

const onFullscreenChange = (): void => {
  isFullscreen.value = !!document.fullscreenElement
}

// ============================================================
// 监听 & 生命周期
// ============================================================

watch(
  () => props.markdownContent,
  (newContent) => {
    if (newContent) {
      renderMarkdown()
    }
  }
)

onMounted(async () => {
  libraryLoading.value = true
  try {
    await initMarkdownIt()
    // 初始注入主题
    injectHighlightTheme(isDark.value ? 'vs2015' : 'default')
    //第三方库初始化完毕 提前停止libraryLoading 不然isBusy一直为True contentRef一直为null
    libraryLoading.value = false
    if (props.markdownContent) {
      await renderMarkdown()
    }
  } finally {
    libraryLoading.value = false
  }
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onUnmounted(() => {
  headingObserver?.disconnect()
  removeSearchHighlights()
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<style scoped>
/* ============================================================ */
/* Markdown 预览根容器 */
/* ============================================================ */

.md-preview-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #ffffff;
  overflow: hidden;
  position: relative;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
}

.md-preview-root.dark-mode {
  background: #1e1e1e;
  color: #d4d4d4;
}

/* ============================================================ */
/* 阅读进度条 */
/* ============================================================ */

.md-progress-bar {
  position: absolute;
  top: 0;
  left: 0;
  height: 3px;
  background: linear-gradient(90deg, #4b6cb7, #182848);
  z-index: 100;
  transition: width 0.1s linear;
}

/* ============================================================ */
/* 工具栏 */
/* ============================================================ */

.md-toolbar {
  display: flex;
  align-items: center;
  padding: 0.5rem 0.75rem;
  background: #f3f3f3;
  border-bottom: 1px solid #e0e0e0;
  gap: 0.5rem;
  flex-shrink: 0;
  z-index: 50;
  min-height: 44px;
}

.dark-mode .md-toolbar {
  background: #252526;
  border-bottom: 1px solid #3e3e42;
}

/* 左侧 */
.md-toolbar-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  flex: 1;
}

.md-file-icon {
  color: #4b6cb7;
  font-size: 1.1rem;
}

.md-file-name {
  font-weight: 600;
  font-size: 0.85rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
  color: #333;
}

.dark-mode .md-file-name {
  color: #cccccc;
}

.md-badge {
  background: #4b6cb7;
  color: white;
  padding: 0.1rem 0.4rem;
  border-radius: 3px;
  font-size: 0.6rem;
  font-weight: 700;
  letter-spacing: 0.5px;
  flex-shrink: 0;
}

/* 中间搜索框 */
.md-toolbar-center {
  flex-shrink: 0;
}

.md-search-box {
  display: flex;
  align-items: center;
  background: #ffffff;
  border: 1px solid #4b6cb7;
  border-radius: 4px;
  padding: 0;
  gap: 0;
}

.dark-mode .md-search-box {
  background: #3c3c3c;
}

.md-search-icon {
  color: #858585;
  font-size: 0.7rem;
  padding: 0 0.5rem;
}

.md-search-input {
  background: transparent;
  border: none;
  color: #333;
  padding: 0.3rem 0;
  font-size: 0.8rem;
  width: 180px;
  outline: none;
}

.dark-mode .md-search-input {
  color: #cccccc;
}

.md-search-input::placeholder {
  color: #999;
}

.md-search-count {
  color: #858585;
  font-size: 0.7rem;
  padding: 0 0.25rem;
  white-space: nowrap;
}

.md-search-nav-btn,
.md-search-close-btn {
  background: transparent;
  border: none;
  color: #666;
  cursor: pointer;
  padding: 0.3rem 0.4rem;
  font-size: 0.7rem;
}

.dark-mode .md-search-nav-btn,
.dark-mode .md-search-close-btn {
  color: #cccccc;
}

.md-search-nav-btn:hover,
.md-search-close-btn:hover {
  color: #333;
}

.dark-mode .md-search-nav-btn:hover,
.dark-mode .md-search-close-btn:hover {
  color: #fff;
}

/* 右侧 */
.md-toolbar-right {
  display: flex;
  align-items: center;
  gap: 0.15rem;
  flex-shrink: 0;
}

.md-tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
  color: #555;
  font-size: 0.8rem;
}

.dark-mode .md-tool-btn {
  color: #cccccc;
}

.md-tool-btn:hover {
  background: #e0e0e0;
  color: #333;
}

.dark-mode .md-tool-btn:hover {
  background: #3e3e42;
  color: #fff;
}

.md-tool-btn.active {
  background: #4b6cb7;
  color: white;
}

.md-toolbar-divider {
  width: 1px;
  height: 1.25rem;
  background: #d0d0d0;
  margin: 0 0.25rem;
}

.dark-mode .md-toolbar-divider {
  background: #3e3e42;
}

/* ============================================================ */
/* 主体区域 */
/* ============================================================ */

.md-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* ============================================================ */
/* 目录侧边栏 */
/* ============================================================ */

.md-outline-panel {
  width: 260px;
  min-width: 260px;
  background: #fafafa;
  border-right: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0;
}

.dark-mode .md-outline-panel {
  background: #252526;
  border-right: 1px solid #3e3e42;
}

.outline-slide-enter-active,
.outline-slide-leave-active {
  transition: width 0.2s ease, min-width 0.2s ease, opacity 0.2s ease;
}

.outline-slide-enter-from,
.outline-slide-leave-to {
  width: 0;
  min-width: 0;
  opacity: 0;
}

.md-outline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.6rem 0.75rem;
  border-bottom: 1px solid #e0e0e0;
  font-size: 0.8rem;
  font-weight: 600;
  color: #333;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.dark-mode .md-outline-header {
  border-bottom: 1px solid #3e3e42;
  color: #cccccc;
}

.md-outline-close {
  background: transparent;
  border: none;
  color: #999;
  cursor: pointer;
  font-size: 0.75rem;
  padding: 0.2rem;
}

.md-outline-content {
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem 0;
}

.md-outline-empty {
  padding: 1rem;
  color: #999;
  font-size: 0.8rem;
  text-align: center;
}

.md-outline-item {
  display: flex;
  align-items: center;
  padding: 0.3rem 0.75rem;
  cursor: pointer;
  transition: background 0.1s;
  gap: 0.4rem;
  font-size: 0.8rem;
  color: #555;
  border-left: 2px solid transparent;
}

.dark-mode .md-outline-item {
  color: #999;
}

.md-outline-item:hover {
  background: #e8e8e8;
  color: #333;
}

.dark-mode .md-outline-item:hover {
  background: #2a2d2e;
  color: #ccc;
}

.md-outline-item.active {
  background: #e3edf7;
  border-left-color: #4b6cb7;
  color: #4b6cb7;
  font-weight: 500;
}

.dark-mode .md-outline-item.active {
  background: #1e3a5f;
  border-left-color: #4b6cb7;
  color: #4ea1f3;
}

.md-outline-bullet {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ccc;
  flex-shrink: 0;
}

.md-outline-item.active .md-outline-bullet {
  background: #4b6cb7;
}

.md-outline-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ============================================================ */
/* 主内容区 */
/* ============================================================ */

.md-content-wrapper {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  scroll-behavior: smooth;
}

.md-content {
  max-width: 900px;
  margin: 0 auto;
  padding: 2rem 1.5rem;
  line-height: 1.8;
  font-size: 16px;
}

/* ============================================================ */
/* 加载 & 错误状态 */
/* ============================================================ */

.md-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  color: #4b6cb7;
  gap: 1rem;
}

.md-loading .ring {
  width: 36px;
  height: 36px;
  border: 3px solid #e0e0e0;
  border-top-color: #4b6cb7;
  border-radius: 50%;
  animation: md-spin 0.8s linear infinite;
}

@keyframes md-spin {
  to { transform: rotate(360deg); }
}

.md-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  color: #e74c3c;
  text-align: center;
  gap: 0.5rem;
}

.md-error h3 {
  margin: 0;
  font-size: 1.1rem;
}

.md-retry-btn {
  margin-top: 0.5rem;
  padding: 0.5rem 1.25rem;
  border: 1px solid #4b6cb7;
  border-radius: 4px;
  background: transparent;
  color: #4b6cb7;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.15s;
}

.md-retry-btn:hover {
  background: rgba(75, 108, 183, 0.1);
}

/* ============================================================ */
/* Markdown 内容样式（通过 :deep() 穿透 scoped） */
/* ============================================================ */

/* 标题 */
.md-content :deep(h1) {
  font-size: 2em;
  margin: 0.8em 0 0.4em;
  padding-bottom: 0.3em;
  border-bottom: 1px solid #e0e0e0;
  color: #1a1a1a;
  font-weight: 700;
}

.dark-mode .md-content :deep(h1) {
  border-bottom-color: #3e3e42;
  color: #e0e0e0;
}

.md-content :deep(h2) {
  font-size: 1.5em;
  margin: 0.8em 0 0.4em;
  padding-bottom: 0.3em;
  border-bottom: 1px solid #e0e0e0;
  color: #1a1a1a;
  font-weight: 600;
}

.dark-mode .md-content :deep(h2) {
  border-bottom-color: #3e3e42;
  color: #e0e0e0;
}

.md-content :deep(h3) { font-size: 1.25em; margin: 0.7em 0 0.3em; color: #1a1a1a; font-weight: 600; }
.dark-mode .md-content :deep(h3) { color: #e0e0e0; }
.md-content :deep(h4) { font-size: 1.1em; margin: 0.6em 0 0.3em; color: #1a1a1a; font-weight: 600; }
.dark-mode .md-content :deep(h4) { color: #e0e0e0; }
.md-content :deep(h5) { font-size: 1em; margin: 0.5em 0 0.2em; color: #333; font-weight: 600; }
.dark-mode .md-content :deep(h5) { color: #ccc; }
.md-content :deep(h6) { font-size: 0.9em; margin: 0.5em 0 0.2em; color: #555; font-weight: 600; }
.dark-mode .md-content :deep(h6) { color: #999; }

/* 标题锚点 */
.md-content :deep(.header-anchor) {
  float: left;
  margin-left: -1.2em;
  padding-right: 0.3em;
  font-size: 0.8em;
  color: #4b6cb7;
  opacity: 0;
  transition: opacity 0.15s;
  text-decoration: none;
}

.md-content :deep(h1:hover .header-anchor),
.md-content :deep(h2:hover .header-anchor),
.md-content :deep(h3:hover .header-anchor) {
  opacity: 1;
}

/* 段落 */
.md-content :deep(p) {
  margin: 0.75em 0;
}

/* 链接 */
.md-content :deep(a) {
  color: #4b6cb7;
  text-decoration: none;
}

.md-content :deep(a:hover) {
  text-decoration: underline;
}

/* 行内代码 */
.md-content :deep(code:not(pre code)) {
  font-family: "Fira Code", "Consolas", "Monaco", monospace;
  font-size: 0.9em;
  background: #f0f0f0;
  padding: 0.2em 0.4em;
  border-radius: 3px;
  color: #c7254e;
}

.dark-mode .md-content :deep(code:not(pre code)) {
  background: #2d2d2d;
  color: #ce9178;
}

/* 代码块 */
.md-content :deep(.code-block-wrapper) {
  position: relative;
  margin: 1em 0;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #e0e0e0;
}

.dark-mode .md-content :deep(.code-block-wrapper) {
  border-color: #3e3e42;
}

.md-content :deep(.code-lang-label) {
  position: absolute;
  top: 0;
  right: 70px;
  padding: 0.2em 0.6em;
  font-size: 0.7rem;
  color: #999;
  font-family: "Consolas", monospace;
  background: transparent;
  z-index: 1;
  text-transform: uppercase;
}

.md-content :deep(.code-copy-btn) {
  position: absolute;
  top: 4px;
  right: 4px;
  padding: 0.2em 0.6em;
  font-size: 0.7rem;
  border: 1px solid #d0d0d0;
  border-radius: 3px;
  background: #fafafa;
  color: #555;
  cursor: pointer;
  z-index: 1;
  opacity: 0;
  transition: opacity 0.15s;
}

.dark-mode .md-content :deep(.code-copy-btn) {
  border-color: #3e3e42;
  background: #2d2d2d;
  color: #ccc;
}

.md-content :deep(.code-block-wrapper:hover .code-copy-btn) {
  opacity: 1;
}

.md-content :deep(.code-copy-btn:hover) {
  background: #4b6cb7;
  color: white;
  border-color: #4b6cb7;
}

.md-content :deep(pre) {
  background: #f8f8f8;
  padding: 1em;
  overflow-x: auto;
  margin: 0;
  line-height: 1.5;
  font-size: 0.9em;
}

.dark-mode .md-content :deep(pre) {
  background: #252526;
}

.md-content :deep(pre code) {
  background: transparent;
  padding: 0;
  color: #333;
  font-family: "Fira Code", "Consolas", "Monaco", monospace;
}

.dark-mode .md-content :deep(pre code) {
  color: #d4d4d4;
}

/* 表格 */
.md-content :deep(.table-wrapper) {
  overflow-x: auto;
  margin: 1em 0;
}

.md-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0;
}

.md-content :deep(th),
.md-content :deep(td) {
  border: 1px solid #d0d0d0;
  padding: 0.5em 0.75em;
  text-align: left;
}

.dark-mode .md-content :deep(th),
.dark-mode .md-content :deep(td) {
  border-color: #3e3e42;
}

.md-content :deep(th) {
  background: #f0f0f0;
  font-weight: 600;
}

.dark-mode .md-content :deep(th) {
  background: #2d2d2d;
}

.md-content :deep(tr:nth-child(even)) {
  background: #fafafa;
}

.dark-mode .md-content :deep(tr:nth-child(even)) {
  background: #252526;
}

/* 引用块 */
.md-content :deep(blockquote) {
  border-left: 4px solid #4b6cb7;
  margin: 1em 0;
  padding: 0.5em 1em;
  background: #f5f7fa;
  color: #555;
}

.dark-mode .md-content :deep(blockquote) {
  background: #252526;
  color: #9cdcfe;
}

/* 列表 */
.md-content :deep(ul),
.md-content :deep(ol) {
  padding-left: 2em;
  margin: 0.5em 0;
}

.md-content :deep(li) {
  margin: 0.25em 0;
}

/* 任务列表 */
.md-content :deep(.task-list-item) {
  list-style: none;
}

.md-content :deep(.task-list-item input[type="checkbox"]) {
  margin-right: 0.5em;
}

/* 分隔线 */
.md-content :deep(hr) {
  border: none;
  border-top: 1px solid #e0e0e0;
  margin: 2em 0;
}

.dark-mode .md-content :deep(hr) {
  border-top-color: #3e3e42;
}

/* 图片 */
.md-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 0.5em 0;
}

/* Mermaid 图表 */
.md-content :deep(.mermaid-container) {
  text-align: center;
  margin: 1em 0;
  overflow-x: auto;
}

/* KaTeX 公式 */
.md-content :deep(.katex-display) {
  overflow-x: auto;
  overflow-y: hidden;
  margin: 1em 0;
}

/* 搜索高亮 */
:deep(.search-highlight) {
  background: #ffeb3b;
  color: #333;
  border-radius: 2px;
  padding: 0 1px;
}

:deep(.search-highlight.active) {
  background: #ff9800;
  color: #fff;
}

/* ============================================================ */
/* 底部状态栏 */
/* ============================================================ */

.md-statusbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.3rem 0.75rem;
  background: #f3f3f3;
  border-top: 1px solid #e0e0e0;
  font-size: 0.75rem;
  color: #888;
  flex-shrink: 0;
  min-height: 28px;
}

.dark-mode .md-statusbar {
  background: #252526;
  border-top: 1px solid #3e3e42;
  color: #858585;
}

.md-statusbar-left,
.md-statusbar-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.md-status-item {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.md-status-divider {
  color: #d0d0d0;
}

.dark-mode .md-status-divider {
  color: #3e3e42;
}

/* ============================================================ */
/* 图片灯箱 */
/* ============================================================ */

.md-lightbox-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.9);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
}

.md-lightbox-content {
  max-width: 90vw;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.md-lightbox-img {
  max-width: 90vw;
  max-height: 85vh;
  object-fit: contain;
  border-radius: 4px;
}

.md-lightbox-info {
  color: #ccc;
  font-size: 0.85rem;
  margin-top: 0.5rem;
  text-align: center;
}

.md-lightbox-close {
  position: fixed;
  top: 1rem;
  right: 1rem;
  background: transparent;
  border: none;
  color: #fff;
  font-size: 1.5rem;
  cursor: pointer;
  z-index: 1;
  padding: 0.5rem;
}

.md-lightbox-prev,
.md-lightbox-next {
  position: fixed;
  top: 50%;
  transform: translateY(-50%);
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: #fff;
  font-size: 1.5rem;
  cursor: pointer;
  padding: 1rem 0.5rem;
  border-radius: 4px;
  transition: background 0.15s;
}

.md-lightbox-prev { left: 1rem; }
.md-lightbox-next { right: 1rem; }

.md-lightbox-prev:hover,
.md-lightbox-next:hover {
  background: rgba(255, 255, 255, 0.4);
}

.lightbox-fade-enter-active,
.lightbox-fade-leave-active {
  transition: opacity 0.2s;
}

.lightbox-fade-enter-from,
.lightbox-fade-leave-to {
  opacity: 0;
}

/* ============================================================ */
/* 响应式 */
/* ============================================================ */

@media (max-width: 768px) {
  .md-outline-panel {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 60;
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
  }

  .md-content {
    padding: 1rem;
    font-size: 15px;
  }

  .md-search-input {
    width: 120px;
  }

  .md-toolbar {
    padding: 0.4rem 0.5rem;
  }
}

/* ============================================================ */
/* 打印样式（导出 PDF） */
/* ============================================================ */

@media print {
  .md-toolbar,
  .md-outline-panel,
  .md-statusbar,
  .md-progress-bar,
  .code-copy-btn,
  .header-anchor {
    display: none !important;
  }

  .md-preview-root {
    background: #fff !important;
    color: #000 !important;
  }

  .md-content {
    max-width: 100%;
    padding: 0;
  }

  .md-content :deep(pre) {
    background: #f5f5f5 !important;
    border: 1px solid #ddd;
    page-break-inside: avoid;
  }

  .md-content :deep(h1),
  .md-content :deep(h2),
  .md-content :deep(h3) {
    page-break-after: avoid;
  }
}
</style>
