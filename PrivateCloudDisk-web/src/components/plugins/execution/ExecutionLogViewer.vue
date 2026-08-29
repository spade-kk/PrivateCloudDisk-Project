<template>
  <section class="execution-log-viewer" aria-label="插件执行日志">
    <header class="log-toolbar">
      <div class="log-toolbar__filters">
        <label class="sr-only" :for="`${idPrefix}-search`">搜索日志</label>
        <div class="log-search">
          <i class="fa fa-search" aria-hidden="true"></i>
          <input :id="`${idPrefix}-search`" v-model="keyword" type="search" placeholder="搜索日志内容（Ctrl+F）" @keydown.stop />
        </div>
        <select v-model="level" aria-label="日志级别" @change="emitFilters">
          <option value="">全部级别</option><option value="DEBUG">Debug</option><option value="INFO">Info</option><option value="WARN">Warn</option><option value="ERROR">Error</option>
        </select>
        <select v-model="source" aria-label="日志来源" @change="emitFilters">
          <option value="">全部来源</option><option value="STDOUT">stdout</option><option value="STDERR">stderr</option><option value="PYCLOUDSDK">pycloud sdk</option><option value="SYSTEM">system</option>
        </select>
      </div>
      <div class="log-toolbar__actions">
        <button type="button" :class="{ active: errorsOnly }" title="只显示错误" @click="errorsOnly = !errorsOnly"><i class="fa fa-exclamation-circle"></i><span>错误</span></button>
        <button type="button" :class="{ active: followTail }" :title="followTail ? '暂停自动滚动' : '恢复自动滚动'" @click="followTail = !followTail"><i :class="followTail ? 'fa fa-pause' : 'fa fa-play'"></i><span>{{ followTail ? '暂停' : '跟随' }}</span></button>
        <button type="button" title="重新加载日志" aria-label="重新加载日志" :disabled="loading" @click="$emit('refresh')"><i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i></button>
        <button type="button" title="下载完整日志" aria-label="下载完整日志" @click="$emit('download')"><i class="fa fa-download"></i></button>
      </div>
    </header>

    <div ref="viewport" class="log-viewport" tabindex="0" @scroll.passive="onScroll" @keydown.ctrl.f.prevent="focusSearch">
      <div v-if="hasMore" class="log-load-more"><button type="button" :disabled="loadingMore" @click="$emit('load-more')"><i v-if="loadingMore" class="fa fa-spinner fa-spin"></i>{{ loadingMore ? '正在加载更早日志…' : '加载更早日志' }}</button></div>
      <div v-if="loading && !logs.length" class="log-placeholder" aria-live="polite"><i class="fa fa-spinner fa-spin"></i> 正在读取受控日志流…</div>
      <div v-else-if="!filteredLogs.length" class="log-placeholder"><i class="fa fa-terminal"></i>{{ logs.length ? '没有符合筛选条件的日志' : '此执行尚未产生可展示日志' }}</div>
      <div v-else class="log-virtual-space" :style="{ height: `${filteredLogs.length * lineHeight}px` }">
        <div class="log-virtual-content" :style="{ transform: `translateY(${startIndex * lineHeight}px)` }">
          <article v-for="line in visibleLines" :key="line.sequenceNo" class="log-line" :class="[`is-${String(line.level).toLowerCase()}`, `source-${String(line.source).toLowerCase()}`]" :style="{ minHeight: `${lineHeight}px` }" @dblclick="copyLine(line)">
            <span v-if="showLineNumbers" class="log-line__number">{{ line.sequenceNo }}</span>
            <time class="log-line__timestamp" :title="line.timestamp">{{ formatTime(line.timestamp) }}</time>
            <span class="log-line__level">{{ line.level }}</span>
            <span class="log-line__source">{{ sourceLabel(line.source) }}</span>
            <!-- 已先 HTML 转义；高亮只插入受控 mark 标签，避免日志内容成为 XSS 载体。 -->
            <code class="log-line__content" v-html="highlight(line.content)"></code>
          </article>
        </div>
      </div>
      <button v-if="!atBottom && filteredLogs.length" class="log-tail-button" type="button" @click="scrollBottom"><i class="fa fa-arrow-down"></i> 回到底部</button>
    </div>
    <footer class="log-statusbar"><span>{{ filteredLogs.length }} / {{ logs.length }} 行</span><label><input v-model="showLineNumbers" type="checkbox" /> 显示行号</label><span v-if="keyword">已高亮“{{ keyword }}”</span><span class="log-statusbar__spacer"></span><span>双击日志行即可复制</span></footer>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { PluginExecutionLogLine } from '@/api/modules/plugins'

const props = withDefaults(defineProps<{
  logs: PluginExecutionLogLine[]
  loading?: boolean
  loadingMore?: boolean
  hasMore?: boolean
  initialLevel?: string
  initialSource?: string
}>(), { loading: false, loadingMore: false, hasMore: false, initialLevel: '', initialSource: '' })
const emit = defineEmits<{
  refresh: []
  download: []
  'load-more': []
  filters: [filters: { level: string; source: string }]
}>()

const lineHeight = 28
const viewport = ref<HTMLElement | null>(null)
const scrollTop = ref(0)
const viewportHeight = ref(440)
const keyword = ref('')
const level = ref(props.initialLevel)
const source = ref(props.initialSource)
const errorsOnly = ref(false)
const followTail = ref(true)
const atBottom = ref(true)
const showLineNumbers = ref(false)
const idPrefix = `plugin-log-${Math.random().toString(36).slice(2, 9)}`
let resizeObserver: ResizeObserver | null = null

const filteredLogs = computed(() => props.logs.filter((line) => {
  if (errorsOnly.value && !['ERROR', 'WARN'].includes(String(line.level).toUpperCase())) return false
  if (level.value && String(line.level).toUpperCase() !== level.value) return false
  if (source.value && String(line.source).toUpperCase() !== source.value) return false
  return !keyword.value || line.content.toLowerCase().includes(keyword.value.toLowerCase())
}))
const startIndex = computed(() => Math.max(0, Math.floor(scrollTop.value / lineHeight) - 8))
const visibleCount = computed(() => Math.ceil(viewportHeight.value / lineHeight) + 18)
const visibleLines = computed(() => filteredLogs.value.slice(startIndex.value, startIndex.value + visibleCount.value))

function emitFilters() { emit('filters', { level: level.value, source: source.value }) }
function formatTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toISOString().replace('T', ' ').replace('Z', '')
}
function sourceLabel(value: string) { return ({ STDOUT: 'stdout', STDERR: 'stderr', PYCLOUDSDK: 'sdk', SYSTEM: 'system' } as Record<string, string>)[String(value).toUpperCase()] || value.toLowerCase() }
function escapeHtml(value: string) { return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;') }
function stripAnsi(value: string) { return value.replace(/[\u001B\u009B][[\]()#;?]*(?:(?:(?:[a-zA-Z\d]*(?:;[-a-zA-Z\d\/#&.:=?%@~_]+)*)?\u0007)|(?:(?:\d{1,4}(?:;\d{0,4})*)?[\dA-PR-TZcf-nq-uy=><~]))/g, '') }
function highlight(value: string) {
  const safe = escapeHtml(stripAnsi(value))
  if (!keyword.value) return safe
  const escaped = keyword.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return safe.replace(new RegExp(`(${escaped})`, 'gi'), '<mark>$1</mark>')
}
async function copyLine(line: PluginExecutionLogLine) {
  await navigator.clipboard?.writeText(`${line.timestamp} ${line.level} ${line.source} ${line.content}`)
}
function onScroll() {
  const element = viewport.value
  if (!element) return
  scrollTop.value = element.scrollTop
  atBottom.value = element.scrollHeight - element.scrollTop - element.clientHeight < 12
  if (element.scrollTop < 24 && props.hasMore && !props.loadingMore) emit('load-more')
}
function scrollBottom() { viewport.value?.scrollTo({ top: viewport.value.scrollHeight, behavior: 'smooth' }) }
function focusSearch() { document.getElementById(`${idPrefix}-search`)?.focus() }
watch(() => props.logs.length, async () => { if (followTail.value && atBottom.value) { await nextTick(); scrollBottom() } })
onMounted(() => {
  if (viewport.value) {
    viewportHeight.value = viewport.value.clientHeight
    resizeObserver = new ResizeObserver(() => { viewportHeight.value = viewport.value?.clientHeight || 440 })
    resizeObserver.observe(viewport.value)
  }
})
onBeforeUnmount(() => resizeObserver?.disconnect())
</script>

<style scoped>
.execution-log-viewer { --terminal-bg: #10151f; --terminal-surface: #171e2b; --terminal-border: #293447; --terminal-muted: #8491a7; --terminal-text: #dce7f7; display: grid; min-height: 0; height: 100%; overflow: hidden; border: 1px solid var(--terminal-border); border-radius: 12px; background: var(--terminal-bg); color: var(--terminal-text); font-family: ui-monospace, "SFMono-Regular", "JetBrains Mono", Consolas, monospace; }
.log-toolbar, .log-statusbar { display: flex; align-items: center; gap: .65rem; min-height: 44px; padding: .45rem .65rem; border-bottom: 1px solid var(--terminal-border); background: color-mix(in srgb, var(--terminal-surface) 92%, transparent); font-family: inherit; }
.log-toolbar { justify-content: space-between; flex-wrap: wrap; }
.log-toolbar__filters, .log-toolbar__actions { display: flex; min-width: 0; align-items: center; gap: .45rem; }
.log-search { display: flex; min-width: min(280px, 45vw); align-items: center; gap: .45rem; padding: 0 .6rem; border: 1px solid var(--terminal-border); border-radius: 7px; background: #0d121b; color: var(--terminal-muted); }
.log-search input, .log-toolbar select { height: 29px; border: 0; outline: 0; background: transparent; color: var(--terminal-text); font: inherit; font-size: 11px; }
.log-search input { min-width: 0; flex: 1; }
.log-toolbar select { border: 1px solid var(--terminal-border); border-radius: 7px; padding: 0 .35rem; background: #0d121b; }
.log-toolbar button { min-height: 29px; border: 1px solid transparent; border-radius: 7px; padding: 0 .5rem; background: transparent; color: var(--terminal-muted); font: inherit; font-size: 11px; cursor: pointer; }
.log-toolbar button:hover, .log-toolbar button.active { border-color: #3d79e6; background: #1c345c; color: #dbeaff; }.log-toolbar button:disabled { opacity: .5; cursor: wait; }.log-toolbar button:focus-visible, .log-search:focus-within, .log-toolbar select:focus-visible, .log-viewport:focus-visible, .log-tail-button:focus-visible { outline: 2px solid #80b0ff; outline-offset: 2px; }
.log-viewport { position: relative; min-height: 0; overflow: auto; outline: 0; overscroll-behavior: contain; }
.log-virtual-space { position: relative; min-width: max-content; }.log-virtual-content { position: absolute; inset: 0 0 auto; width: 100%; }
.log-line { display: grid; grid-template-columns: auto 162px 52px 70px minmax(0, 1fr); align-items: start; gap: .6rem; padding: 4px .75rem; border-left: 2px solid transparent; font-size: 12px; line-height: 20px; white-space: pre; }.log-line:hover { background: rgba(88, 133, 210, .12); }.log-line.is-error { border-left-color: #ef5f6c; background: rgba(239, 95, 108, .06); }.log-line.is-warn { border-left-color: #e2a93f; }.log-line.is-debug { opacity: .72; }
.log-line__number, .log-line__timestamp { color: #77849a; user-select: none; }.log-line__number { width: 48px; text-align: right; }.log-line__timestamp { font-variant-numeric: tabular-nums; }.log-line__level { font-weight: 700; color: #70aaff; }.is-warn .log-line__level { color: #f6cf68; }.is-error .log-line__level { color: #ff8190; }.is-debug .log-line__level { color: #9aa5b5; }.log-line__source { color: #b49af6; text-transform: lowercase; }.source-stderr .log-line__source { color: #ff9e77; }.log-line__content { overflow: hidden; color: var(--terminal-text); text-overflow: ellipsis; } :deep(mark) { border-radius: 2px; background: #edc855; color: #202020; }
.log-placeholder { display: grid; min-height: 180px; place-content: center; gap: .5rem; color: var(--terminal-muted); font-size: 12px; }.log-load-more { display: grid; place-content: center; min-height: 42px; }.log-load-more button, .log-tail-button { border: 1px solid #355a8d; border-radius: 999px; padding: .35rem .8rem; background: #192d4e; color: #cfe2ff; font: inherit; font-size: 11px; cursor: pointer; }.log-tail-button { position: sticky; bottom: .75rem; left: calc(100% - 150px); margin: .5rem; }
.log-statusbar { min-height: 31px; border-top: 1px solid var(--terminal-border); border-bottom: 0; color: var(--terminal-muted); font-size: 10px; }.log-statusbar label { display: flex; align-items: center; gap: .3rem; cursor: pointer; }.log-statusbar__spacer { flex: 1; }
:global(.dark) .execution-log-viewer { --terminal-bg: #0c1119; } @media (prefers-reduced-motion: reduce) { .execution-log-viewer *, .execution-log-viewer *::before, .execution-log-viewer *::after { scroll-behavior: auto !important; transition-duration: .01ms !important; animation-duration: .01ms !important; animation-iteration-count: 1 !important; } } @media (max-width: 768px) { .log-toolbar__filters { width: 100%; overflow-x: auto; }.log-search { min-width: 180px; }.log-toolbar button, .log-toolbar select { min-width: 44px; min-height: 44px; }.log-toolbar button span { display: none; }.log-line { grid-template-columns: 123px 46px 46px minmax(220px, 1fr); gap: .42rem; padding-inline: .55rem; font-size: 11px; }.log-line__source { display: none; }.log-line__number { display: none; }.log-statusbar__spacer, .log-statusbar span:last-child { display: none; } }
</style>
