<template>
  <section ref="viewerElement" class="git-file-viewer" :class="{ 'git-file-viewer--fullscreen': fullscreen }" aria-live="polite">
    <header class="git-file-viewer__header">
      <GitBreadcrumbs :repository-name="repositoryName" :path="file?.path || directoryPath" :is-file="Boolean(file)" :loading="directoryLoading || loading" @navigate="(path) => $emit('navigate', path)" />
      <div v-if="file" class="git-file-viewer__actions">
        <button type="button" title="复制路径" aria-label="复制路径" @click="copyPath"><i class="fa fa-link"></i></button>
        <button type="button" title="复制文件内容" aria-label="复制文件内容" :disabled="!canCopy" @click="copyContent"><i class="fa fa-copy"></i></button>
        <button type="button" title="下载原始文件" aria-label="下载原始文件" @click="download"><i class="fa fa-download"></i></button>
        <button v-if="canBlame" type="button" :class="{ active: showBlame }" title="逐行 Blame" aria-label="逐行 Blame" @click="toggleBlame"><i class="fa fa-user-secret"></i></button>
        <button type="button" title="文件历史" aria-label="文件历史" @click="$emit('history', file?.path)"><i class="fa fa-history"></i></button>
        <button type="button" :title="fullscreen ? '退出全屏' : '全屏查看文件'" :aria-label="fullscreen ? '退出全屏' : '全屏查看文件'" @click="toggleFullscreen"><i :class="fullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i></button>
      </div>
    </header>

    <div v-if="!file && directoryPath !== undefined" class="git-file-viewer__directory">
      <div v-if="directoryLoading" class="git-file-viewer__skeleton"><span v-for="line in 7" :key="line" :style="{ width: `${48 + (line * 13) % 42}%` }"></span></div>
      <div v-else-if="!directoryEntries.length" class="git-file-viewer__empty"><i class="fa fa-folder-open-o"></i><h3>空目录</h3><p>该目录暂时没有可展示的文件。</p></div>
      <div v-else class="git-file-viewer__directory-list" role="list" aria-label="目录内容">
        <button v-for="entry in directoryEntries" :key="entry.path" type="button" role="listitem" @click="$emit('navigate', entry.path)">
          <FileTypeIcon class="git-file-viewer__dir-icon" :file-name="entry.name" :path="entry.path" :is-directory="entry.type === 'tree'" color-mode="monochrome" />
          <span>{{ entry.name }}</span>
          <small v-if="entry.type === 'tree'">目录</small>
          <small v-else>{{ entry.size ? formatFileSize(entry.size) : '文件' }}</small>
          <i class="fa fa-angle-right" aria-hidden="true"></i>
        </button>
      </div>
    </div>
    <div v-else-if="!file" class="git-file-viewer__empty"><i class="fa fa-file-code-o"></i><h3>从目录中选择文件</h3><p>支持代码高亮、Markdown、图片、PDF、Blame 与文件历史。</p></div>
    <div v-else-if="loading" class="git-file-viewer__skeleton"><span v-for="line in 16" :key="line" :style="{ width: `${48 + (line * 17) % 46}%` }"></span></div>
    <div v-else-if="error" class="git-file-viewer__error"><i class="fa fa-exclamation-triangle"></i><p>{{ error }}</p><button type="button" @click="load">重新加载</button></div>
    <template v-else-if="blob">
      <div class="git-file-viewer__meta"><span><FileTypeIcon class="git-file-viewer__meta-icon" :file-name="file.name" :path="file.path" color-mode="monochrome" />{{ file.name }}</span><span>{{ formatFileSize(blob.size) }}</span><span v-if="blob.lineCount">{{ blob.lineCount.toLocaleString('zh-CN') }} 行</span><span>{{ blob.mimeType || '未知类型' }}</span><span v-if="fileKind === 'code'">{{ languageLabel }}</span></div>
      <!-- 原行为遇到超限文本直接隐藏所有内容；新行为展示服务端安全截取的前 5000 行，同时保留完整下载。 -->
      <div v-if="blob.truncated && !blob.content" class="git-file-viewer__large"><i class="fa fa-file-o"></i><h3>文件过大，未加载文本预览</h3><p>{{ formatFileSize(blob.size) }} 超过安全预览限制。可下载完整文件，或使用本地 IDE 打开。</p><button type="button" @click="download"><i class="fa fa-download"></i>下载完整文件</button></div>
      <div v-else-if="fileKind === 'markdown'" class="git-file-viewer__markdown">
        <p v-if="blob.truncated" class="git-file-viewer__truncated"><i class="fa fa-info-circle"></i>文件较大，仅显示前 {{ blob.lineCount.toLocaleString('zh-CN') }} 行；可下载完整文件。</p>
        <div class="git-file-viewer__mode"><button type="button" :class="{ active: markdownMode === 'preview' }" @click="markdownMode = 'preview'">预览</button><button type="button" :class="{ active: markdownMode === 'source' }" @click="markdownMode = 'source'">源码</button></div>
        <MarkdownPreview v-if="markdownMode === 'preview'" :markdown-content="blob.content" :file-name="file.name" :file-size="formatFileSize(blob.size)" :dark-mode="isDark" />
        <GitCodeTable v-else :lines="codeLines" :highlighted-lines="highlightedLines" :selected-lines="selectedLines" :blame-lines="[]" :wrap="wrapCode" @select-line="selectLine" />
      </div>
      <div v-else-if="fileKind === 'image'" class="git-file-viewer__asset"><ImagePreview v-if="rawUrl" :file-url="rawUrl" :file-name="file.name" :file-size="formatFileSize(blob.size)" :file-extension="file.name.split('.').pop() || ''" /><div v-else class="git-file-viewer__error"><p>正在取得图片数据…</p></div></div>
      <div v-else-if="fileKind === 'pdf'" class="git-file-viewer__asset"><PdfPreview v-if="rawUrl" :file-url="rawUrl" :file-name="file.name" :file-size="formatFileSize(blob.size)" /><div v-else class="git-file-viewer__error"><p>正在取得 PDF 数据…</p></div></div>
      <div v-else-if="fileKind === 'media'" class="git-file-viewer__media"><video v-if="blob.mimeType.startsWith('video/') && rawUrl" controls :src="rawUrl"></video><audio v-else-if="rawUrl" controls :src="rawUrl"></audio><div v-else class="git-file-viewer__error"><p>正在取得媒体数据…</p></div></div>
      <div v-else-if="blob.isBinary || fileKind === 'binary'" class="git-file-viewer__binary"><FileTypeIcon class="git-file-viewer__meta-icon" :file-name="file.name" :path="file.path" color-mode="monochrome" /><h3>二进制文件</h3><p>{{ blob.mimeType || '无法识别的二进制格式' }} · {{ formatFileSize(blob.size) }}</p><button type="button" @click="download"><i class="fa fa-download"></i>下载文件</button></div>
      <div v-else class="git-file-viewer__code">
        <p v-if="blob.truncated" class="git-file-viewer__truncated"><i class="fa fa-info-circle"></i>文件较大，仅显示前 {{ blob.lineCount.toLocaleString('zh-CN') }} 行；可下载完整文件。</p>
        <div class="git-file-viewer__code-tools"><label><input v-model="wrapCode" type="checkbox" />自动换行</label><span v-if="showBlame && blameLoading"><i class="fa fa-circle-o-notch fa-spin"></i>加载 Blame…</span><span v-else-if="showBlame && blameError" class="is-error">{{ blameError }}</span></div>
        <GitCodeTable :lines="codeLines" :highlighted-lines="highlightedLines" :selected-lines="selectedLines" :blame-lines="showBlame ? blameLines : []" :wrap="wrapCode" @select-line="selectLine" />
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import GitCodeTable from '@/components/git/GitCodeTable.vue'
import GitBreadcrumbs from '@/components/git/GitBreadcrumbs.vue'
import ImagePreview from '@/components/preview/ImagePreview.vue'
import MarkdownPreview from '@/components/preview/MarkdownPreview.vue'
import PdfPreview from '@/components/preview/PdfPreview.vue'
import type { GitBlobPreview, GitTreeEntry } from '@/api/modules/git'
import { getGitBlameApi, getGitBlobApi, getGitRawBlobApi } from '@/api/modules/git'
import { formatFileSize } from '@/utils/helpers'
import { getHighlightSync, injectHighlightTheme, loadHighlight } from '@/utils/highlightCdn'
import { detectGitFileKind, escapeHtml, getGitLanguage, parseGitBlamePorcelain, type GitBlameLine, type GitFileKind } from '@/utils/gitRepositoryPresentation'
import FileTypeIcon from '@/components/file/FileTypeIcon.vue'

/* 以下内联 CodeTable 是本次拆分前的实现痕迹。
 * 已由独立 GitCodeTable.vue 替代，以避免运行时模板与文件预览状态耦合；保留原注释和代码
 * 仅供回溯，当前不会参与编译或渲染。
const CodeTable = defineComponent({
  name: 'GitCodeTable',
  props: { lines: { type: Array as PropType<string[]>, required: true }, highlightedLines: { type: Array as PropType<string[]>, required: true }, selectedLines: { type: Array as PropType<number[]>, required: true }, blameLines: { type: Array as PropType<GitBlameLine[]>, required: true }, wrap: { type: Boolean, default: false } },
  emits: ['select-line'],
  template: `<div class="git-code-table" :class="{ 'is-wrapped': wrap }"><div v-for="(line, index) in lines" :key="index" class="git-code-table__row" :class="{ 'is-selected': selectedLines.includes(index + 1) }"><button type="button" class="git-code-table__line" :aria-label="'选择第 ' + (index + 1) + ' 行'" @click="$emit('select-line', index + 1, $event)">{{ index + 1 }}</button><span v-if="blameLines[index]" class="git-code-table__blame" :title="blameLines[index].hash"><b>{{ blameLines[index].author || '未知作者' }}</b><small>{{ blameLines[index].hash.slice(0, 8) }}</small></span><code class="git-code-table__content" v-html="highlightedLines[index] || '&nbsp;'"></code></div></div>`,
})

// `defineComponent`/`PropType` 为内联纯展示子组件服务，避免每一行触发独立 Blob 请求。
import { defineComponent, type PropType } from 'vue'
*/

const props = withDefaults(defineProps<{
  repositoryId: string
  repositoryName: string
  refName: string
  file: GitTreeEntry | null
  directoryPath?: string
  directoryEntries?: GitTreeEntry[]
  directoryLoading?: boolean
  isDark?: boolean
  initialLine?: number | null
}>(), { isDark: false, initialLine: null })
const emit = defineEmits<{ navigate: [path: string]; history: [path: string]; toast: [message: string, type?: 'success' | 'error' | 'warning'] }>()

const viewerElement = ref<HTMLElement | null>(null)
const blob = ref<GitBlobPreview | null>(null)
const loading = ref(false)
const error = ref('')
const rawUrl = ref('')
const markdownMode = ref<'preview' | 'source'>('preview')
const wrapCode = ref(false)
const highlightedLines = ref<string[]>([])
const selectedLines = ref<number[]>([])
const blameLines = ref<GitBlameLine[]>([])
const showBlame = ref(false)
const blameLoading = ref(false)
const blameError = ref('')
const fullscreen = ref(false)
let selectionAnchor: number | null = null

const directoryEntries = computed(() => props.directoryEntries || [])
const directoryPath = computed(() => props.directoryPath)
const directoryLoading = computed(() => Boolean(props.directoryLoading))
const fileKind = computed<GitFileKind>(() => props.file && blob.value ? detectGitFileKind(props.file.name, blob.value.mimeType) : 'binary')
const language = computed(() => props.file ? getGitLanguage(props.file.name) : '')
const languageLabel = computed(() => language.value || '纯文本')
const codeLines = computed(() => blob.value?.content.split('\n') || [])
const canCopy = computed(() => Boolean(blob.value && !blob.value.isBinary && !blob.value.truncated))
const canBlame = computed(() => fileKind.value === 'code' && Boolean(blob.value?.content) && !blob.value?.truncated)

watch(() => [props.repositoryId, props.refName, props.file?.path] as const, load, { immediate: true })
watch(() => props.isDark, (dark) => injectHighlightTheme(dark ? 'github-dark' : 'github'), { immediate: true })
watch(() => [blob.value?.content, language.value] as const, highlightCode)
watch(() => props.initialLine, async (line) => { if (line) { selectedLines.value = [line]; await nextTick(); scrollToLine(line) } })

async function load(): Promise<void> {
  releaseRawUrl()
  blob.value = null; error.value = ''; highlightedLines.value = []; blameLines.value = []; selectedLines.value = []; showBlame.value = false; markdownMode.value = 'preview'
  if (!props.file || !props.repositoryId) return
  loading.value = true
  try {
    const response = await getGitBlobApi(props.repositoryId, props.refName, props.file.path)
    blob.value = response.data
    if (fileKind.value === 'image' || fileKind.value === 'pdf' || fileKind.value === 'media') await loadRawUrl()
    await highlightCode()
    injectHighlightTheme(props.isDark ? 'github-dark' : 'github')
    if (props.initialLine) { selectedLines.value = [props.initialLine]; await nextTick(); scrollToLine(props.initialLine) }
  } catch (cause: any) { error.value = cause?.message || '文件内容加载失败' } finally { loading.value = false }
}

async function loadRawUrl(): Promise<void> {
  if (!props.file) return
  try {
    const content = await getGitRawBlobApi(props.repositoryId, props.refName, props.file.path)
    rawUrl.value = URL.createObjectURL(content)
  } catch (cause: any) { error.value = cause?.message || '原始文件读取失败，请确认仓库允许下载' }
}

async function highlightCode(): Promise<void> {
  if (!blob.value || blob.value.isBinary) return
  if (!getHighlightSync()) await loadHighlight().catch(() => null)
  const hljs = getHighlightSync()
  highlightedLines.value = codeLines.value.map((line) => {
    if (!line) return '&nbsp;'
    try { return language.value && hljs?.getLanguage(language.value) ? hljs.highlight(line, { language: language.value, ignoreIllegals: true }).value : escapeHtml(line) } catch { return escapeHtml(line) }
  })
}

function selectLine(line: number, event: MouseEvent): void {
  if (event.shiftKey && selectionAnchor) {
    const start = Math.min(selectionAnchor, line); const end = Math.max(selectionAnchor, line)
    selectedLines.value = Array.from({ length: end - start + 1 }, (_, index) => start + index)
  } else if (event.metaKey || event.ctrlKey) {
    selectedLines.value = selectedLines.value.includes(line) ? selectedLines.value.filter((value) => value !== line) : [...selectedLines.value, line].sort((left, right) => left - right)
    selectionAnchor = line
  } else { selectedLines.value = [line]; selectionAnchor = line }
  const url = new URL(window.location.href); url.searchParams.set('line', String(line)); window.history.replaceState({}, '', url)
}

async function toggleBlame(): Promise<void> {
  showBlame.value = !showBlame.value
  if (!showBlame.value || blameLines.value.length || !props.file) return
  blameLoading.value = true; blameError.value = ''
  try { blameLines.value = parseGitBlamePorcelain((await getGitBlameApi(props.repositoryId, props.refName, props.file.path)).data.porcelain || '') } catch (cause: any) { blameError.value = cause?.message || 'Blame 数据加载失败' } finally { blameLoading.value = false }
}

async function copyPath(): Promise<void> { if (!props.file) return; try { await navigator.clipboard.writeText(props.file.path); emit('toast', '文件路径已复制', 'success') } catch { emit('toast', '当前浏览器不允许自动复制', 'warning') } }
async function copyContent(): Promise<void> { if (!blob.value) return; try { await navigator.clipboard.writeText(blob.value.content); emit('toast', '文件内容已复制', 'success') } catch { emit('toast', '当前浏览器不允许自动复制', 'warning') } }
async function download(): Promise<void> {
  if (!props.file) return
  try { const content = await getGitRawBlobApi(props.repositoryId, props.refName, props.file.path, true); const url = URL.createObjectURL(content); const anchor = document.createElement('a'); anchor.href = url; anchor.download = props.file.name; anchor.click(); URL.revokeObjectURL(url); emit('toast', '下载已开始', 'success') } catch (cause: any) { emit('toast', cause?.message || '下载失败', 'error') }
}
async function toggleFullscreen(): Promise<void> { if (!viewerElement.value) return; if (!document.fullscreenElement) { await viewerElement.value.requestFullscreen(); fullscreen.value = true } else { await document.exitFullscreen(); fullscreen.value = false } }
function scrollToLine(line: number): void { viewerElement.value?.querySelector(`.git-code-table__row:nth-child(${line})`)?.scrollIntoView({ block: 'center', behavior: 'smooth' }) }
function releaseRawUrl(): void { if (rawUrl.value) URL.revokeObjectURL(rawUrl.value); rawUrl.value = '' }
onBeforeUnmount(releaseRawUrl)
</script>

<style scoped>
.git-file-viewer { display:flex;min-width:0;min-height:640px;flex:1;flex-direction:column;background:var(--git-panel,#fff);color:var(--git-text,#24292f); }.git-file-viewer--fullscreen { height:100vh;background:var(--git-panel,#fff); }.git-file-viewer__header { display:flex;min-height:52px;align-items:center;justify-content:space-between;gap:12px;border-bottom:1px solid var(--git-border,#d0d7de);padding:0 14px; }.git-file-viewer__breadcrumbs { display:flex;min-width:0;align-items:center;gap:7px;overflow:hidden;color:var(--git-muted,#57606a);font-size:12px;white-space:nowrap; }.git-file-viewer__breadcrumbs button { overflow:hidden;text-overflow:ellipsis;border:0;background:transparent;color:inherit;cursor:pointer; }.git-file-viewer__breadcrumbs button:hover { color:#0969da;text-decoration:underline; }.git-file-viewer__actions { display:flex;flex-shrink:0;gap:2px; }.git-file-viewer__actions button { display:inline-flex;width:30px;height:30px;align-items:center;justify-content:center;border:0;border-radius:6px;background:transparent;color:var(--git-muted,#57606a); }.git-file-viewer__actions button:hover,.git-file-viewer__actions button.active { background:var(--git-hover,#f6f8fa);color:#0969da; }.git-file-viewer__actions button:disabled { opacity:.4;cursor:not-allowed; }.git-file-viewer__meta { display:flex;flex-wrap:wrap;gap:10px;border-bottom:1px solid var(--git-border,#d0d7de);padding:8px 14px;color:var(--git-muted,#57606a);font-size:11px; }.git-file-viewer__meta span:first-child { color:var(--git-text,#24292f);font-weight:600; }.git-file-viewer__meta i,.git-file-viewer__meta .git-file-viewer__meta-icon { margin-right:6px; }.git-file-viewer__empty,.git-file-viewer__large,.git-file-viewer__binary,.git-file-viewer__error { display:flex;min-height:360px;flex:1;flex-direction:column;align-items:center;justify-content:center;gap:10px;padding:30px;color:var(--git-muted,#57606a);text-align:center; }.git-file-viewer__empty i,.git-file-viewer__large>i,.git-file-viewer__binary>i,.git-file-viewer__error>i { font-size:34px;color:#8c959f; }.git-file-viewer__binary .git-file-viewer__meta-icon { font-size:34px;color:var(--git-icon-color,currentColor); }.git-file-viewer__empty h3,.git-file-viewer__large h3,.git-file-viewer__binary h3 { margin:0;color:var(--git-text,#24292f);font-size:16px; }.git-file-viewer__empty p,.git-file-viewer__large p,.git-file-viewer__binary p { margin:0;max-width:430px;font-size:13px;line-height:1.7; }.git-file-viewer__large button,.git-file-viewer__binary button,.git-file-viewer__error button { border:1px solid var(--git-border,#d0d7de);border-radius:6px;background:var(--git-panel,#fff);padding:7px 11px;color:#0969da;font-size:12px; }.git-file-viewer__skeleton { display:flex;flex:1;flex-direction:column;gap:12px;padding:22px; }.git-file-viewer__skeleton span { height:13px;border-radius:4px;background:linear-gradient(90deg,#eaeef2 25%,#f6f8fa 50%,#eaeef2 75%);background-size:200% 100%;animation:git-view-loading 1.2s infinite; }.git-file-viewer__mode,.git-file-viewer__code-tools { display:flex;align-items:center;gap:6px;border-bottom:1px solid var(--git-border,#d0d7de);padding:8px 12px;color:var(--git-muted,#57606a);font-size:12px; }.git-file-viewer__mode button { border:0;border-radius:6px;background:transparent;padding:5px 8px;color:inherit; }.git-file-viewer__mode button.active { background:var(--git-hover,#eaeef2);color:#0969da;font-weight:600; }.git-file-viewer__markdown { min-height:0;flex:1;overflow:auto; }.git-file-viewer__markdown :deep(.md-preview-root) { min-height:580px;border:0;box-shadow:none; }.git-file-viewer__asset,.git-file-viewer__media { display:flex;min-height:0;flex:1;overflow:auto; }.git-file-viewer__asset :deep(.image-preview-container),.git-file-viewer__asset :deep(.pdf-preview-container) { min-height:560px;flex:1; }.git-file-viewer__media { align-items:center;justify-content:center;background:#111827;padding:18px; }.git-file-viewer__media video,.git-file-viewer__media audio { max-width:100%;max-height:75vh; }.git-file-viewer__code { min-height:0;flex:1;overflow:auto; }.git-file-viewer__code-tools { justify-content:flex-end; }.git-file-viewer__code-tools .is-error { color:#cf222e; }
:deep(.git-code-table) { min-width:max-content;padding:8px 0 24px;font:13px/1.6 ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,"Liberation Mono","Courier New",monospace; }.git-file-viewer :deep(.git-code-table.is-wrapped) { min-width:100%; }.git-file-viewer :deep(.git-code-table__row) { display:flex;min-height:21px; }.git-file-viewer :deep(.git-code-table__row.is-selected) { background:#ddf4ff; }.git-file-viewer :deep(.git-code-table__line) { width:56px;flex:0 0 56px;border:0;border-right:1px solid var(--git-border,#d0d7de);background:transparent;padding:0 10px;color:var(--git-muted,#57606a);text-align:right;cursor:pointer;user-select:none; }.git-file-viewer :deep(.git-code-table__line:hover) { background:var(--git-hover,#f6f8fa);color:#0969da; }.git-file-viewer :deep(.git-code-table__blame) { display:flex;width:155px;flex:0 0 155px;align-items:center;gap:6px;overflow:hidden;border-right:1px solid var(--git-border,#d0d7de);padding:0 8px;color:var(--git-muted,#57606a);font:10px/1.3 system-ui,sans-serif;white-space:nowrap; }.git-file-viewer :deep(.git-code-table__blame b) { overflow:hidden;text-overflow:ellipsis;font-weight:500; }.git-file-viewer :deep(.git-code-table__blame small) { color:#8c959f; }.git-file-viewer :deep(.git-code-table__content) { display:block;min-width:0;flex:1;padding:0 14px;white-space:pre; }.git-file-viewer :deep(.git-code-table.is-wrapped .git-code-table__content) { white-space:pre-wrap;overflow-wrap:anywhere; }.git-file-viewer :deep(.hljs) { background:transparent;padding:0; }
:global(.dark) .git-file-viewer { --git-panel:#0d1117;--git-text:#c9d1d9;--git-muted:#8b949e;--git-border:#30363d;--git-hover:#21262d; }.dark-mode .git-file-viewer { --git-panel:#0d1117;--git-text:#c9d1d9;--git-muted:#8b949e;--git-border:#30363d;--git-hover:#21262d; }.dark-mode .git-file-viewer :deep(.git-code-table__row.is-selected) { background:#1f6feb44; }
@keyframes git-view-loading { to { background-position:-200% 0; } } @media (max-width:767px) { .git-file-viewer { min-height:520px; }.git-file-viewer__header { padding:0 8px; }.git-file-viewer__actions button:nth-child(1),.git-file-viewer__actions button:nth-child(2),.git-file-viewer__actions button:nth-child(5) { display:none; }.git-file-viewer :deep(.git-code-table__line) { width:46px;flex-basis:46px;padding:0 7px; }.git-file-viewer :deep(.git-code-table__blame) { display:none; } } @media (prefers-reduced-motion:reduce) { .git-file-viewer__skeleton span { animation:none; } }
/* [REQ-GIT-UIUX-20260816] 大文本前缀提示不占用代码区，内容仍由独立滚动区承载。 */
.git-file-viewer__truncated { display:flex;align-items:center;gap:6px;margin:0;border-bottom:1px solid var(--git-border,#d0d7de);background:#fff8c5;padding:7px 12px;color:#7d4e00;font-size:11px;line-height:1.45; }.dark-mode .git-file-viewer__truncated,:global(.dark) .git-file-viewer__truncated { background:#9e6a0333;color:#e3b341; }
.git-file-viewer__directory { min-height:0;flex:1;overflow:auto; }.git-file-viewer__directory-list { display:flex;flex-direction:column;padding:8px 0; }.git-file-viewer__directory-list button { display:grid;grid-template-columns:22px minmax(0,1fr) auto 18px;align-items:center;gap:8px;border:0;border-bottom:1px solid var(--git-border,#d0d7de);background:transparent;padding:11px 16px;color:var(--git-text,#24292f);text-align:left; }.git-file-viewer__directory-list button:hover { background:var(--git-hover,#f6f8fa);color:#0969da; }.git-file-viewer__directory-list button .git-file-viewer__dir-icon { color:var(--git-icon-color,currentColor); justify-self:start; }.git-file-viewer__directory-list button span { overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }.git-file-viewer__directory-list button small { color:var(--git-muted,#57606a);font-size:10px; }
</style>
