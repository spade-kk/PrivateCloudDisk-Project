<template>
  <section ref="root" class="git-diff-viewer" :class="{ 'is-fullscreen': fullscreen, 'is-loading': loading }" aria-label="代码差异">
    <header class="git-diff-viewer__header">
      <div class="git-diff-viewer__title">
        <i class="fa fa-exchange" aria-hidden="true"></i>
        <strong>{{ fileName || '文件差异' }}</strong>
        <span class="git-diff-viewer__summary"><b class="is-add">+{{ summary.additions }}</b><b class="is-delete">-{{ summary.deletions }}</b></span>
      </div>
      <div class="git-diff-viewer__actions">
        <button type="button" :class="{ active: mode === 'split' }" title="左右分栏" @click="mode = 'split'">左右</button>
        <button type="button" :class="{ active: mode === 'unified' }" title="统一视图" @click="mode = 'unified'">统一</button>
        <button type="button" title="复制差异" @click="copyDiff"><i class="fa fa-copy"></i></button>
        <button type="button" :title="fullscreen ? '退出全屏' : '全屏查看差异'" @click="toggleFullscreen"><i :class="fullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i></button>
      </div>
    </header>
    <div v-if="loading" class="git-diff-viewer__skeleton"><span v-for="line in 12" :key="line" :style="{ width: `${44 + (line * 13) % 48}%` }"></span></div>
    <div v-else-if="!files.length" class="git-diff-viewer__empty"><i class="fa fa-check-circle-o"></i><span>没有可展示的差异</span></div>
    <div v-else ref="viewport" class="git-diff-viewer__viewport" tabindex="0" @keydown="onKeydown">
      <article v-for="file in files" :key="file.path" class="git-diff-file" :class="{ 'is-collapsed': collapsedFiles.has(file.path) }">
        <header class="git-diff-file__header">
          <button type="button" class="git-diff-file__toggle" :aria-expanded="!collapsedFiles.has(file.path)" @click="toggleFile(file.path)"><i :class="collapsedFiles.has(file.path) ? 'fa fa-chevron-right' : 'fa fa-chevron-down'"></i></button>
          <span class="git-diff-file__status" :class="`is-${file.status}`">{{ statusLabel(file.status) }}</span>
          <strong :title="file.path">{{ file.path }}</strong>
          <span class="git-diff-file__stats"><b class="is-add">+{{ file.additions }}</b><b class="is-delete">-{{ file.deletions }}</b></span>
        </header>
        <div v-if="!collapsedFiles.has(file.path)" class="git-diff-file__body">
          <div v-if="mode === 'unified'" class="git-diff-unified">
            <div v-for="(line, index) in file.lines" :key="`${file.path}-${index}`" class="git-diff-row" :class="`is-${line.kind}`">
              <span class="git-diff-gutter git-diff-gutter--old">{{ line.oldLine || '' }}</span>
              <span class="git-diff-gutter git-diff-gutter--new">{{ line.newLine || '' }}</span>
              <span class="git-diff-marker" aria-hidden="true">{{ marker(line.kind) }}</span>
              <span v-if="line.kind === 'hunk'" class="git-diff-code git-diff-code--hunk">{{ line.text }}</span>
              <code v-else class="git-diff-code" :title="line.text" @dblclick="copyLine(line.text)"><span v-for="(token, tokenIndex) in line.tokens" :key="tokenIndex" :class="token.kind">{{ token.text }}</span></code>
            </div>
          </div>
          <div v-else class="git-diff-split">
            <div class="git-diff-split__side">
              <div v-for="(line, index) in splitRows(file).left" :key="`${file.path}-left-${index}`" class="git-diff-row" :class="`is-${line.kind}`"><span class="git-diff-gutter">{{ line.oldLine || '' }}</span><span class="git-diff-marker">{{ marker(line.kind) }}</span><code class="git-diff-code"><span v-for="(token, tokenIndex) in line.tokens" :key="tokenIndex" :class="token.kind">{{ token.text }}</span></code></div>
            </div>
            <div class="git-diff-split__side">
              <div v-for="(line, index) in splitRows(file).right" :key="`${file.path}-right-${index}`" class="git-diff-row" :class="`is-${line.kind}`"><span class="git-diff-gutter">{{ line.newLine || '' }}</span><span class="git-diff-marker">{{ marker(line.kind) }}</span><code class="git-diff-code"><span v-for="(token, tokenIndex) in line.tokens" :key="tokenIndex" :class="token.kind">{{ token.text }}</span></code></div>
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { splitGitDiff, type GitDiffFile } from '@/utils/gitRepositoryPresentation'

type DiffKind = 'context' | 'addition' | 'deletion' | 'hunk'
interface DiffToken { text: string; kind: 'plain' | 'inline-add' | 'inline-delete' }
interface DiffLine { kind: DiffKind; oldLine?: number; newLine?: number; text: string; tokens: DiffToken[] }

const props = withDefaults(defineProps<{ diff: string; fileName?: string; loading?: boolean }>(), { fileName: '', loading: false })
const emit = defineEmits<{ toast: [message: string, type?: 'success' | 'error' | 'warning'] }>()
const root = ref<HTMLElement | null>(null)
const viewport = ref<HTMLElement | null>(null)
const mode = ref<'split' | 'unified'>((localStorage.getItem('pcd.git.diff.mode') as 'split' | 'unified') || 'split')
const fullscreen = ref(false)
const collapsedFiles = ref(new Set<string>())

const files = computed(() => parseFiles(props.diff))
const summary = computed(() => files.value.reduce((result, file) => ({ additions: result.additions + file.additions, deletions: result.deletions + file.deletions }), { additions: 0, deletions: 0 }))

function parseFiles(diff: string): Array<GitDiffFile & { lines: DiffLine[] }> {
  return splitGitDiff(diff).map((file) => ({ ...file, lines: addInlineDiff(parseLines(file.content)) }))
}

function parseLines(content: string): DiffLine[] {
  let oldLine = 0
  let newLine = 0
  return content.split('\n').filter((line) => !line.startsWith('diff --git ') && !line.startsWith('--- ') && !line.startsWith('+++ ')).map((raw) => {
    if (raw.startsWith('@@')) {
      const match = raw.match(/@@ -(\d+)\s*,?\d* \+(\d+)/)
      oldLine = Number(match?.[1] || oldLine)
      newLine = Number(match?.[2] || newLine)
      return { kind: 'hunk', text: raw, tokens: [{ text: raw, kind: 'plain' }] }
    }
    if (raw.startsWith('\\ No newline')) return { kind: 'context', text: raw, tokens: [{ text: raw, kind: 'plain' }] }
    const kind: DiffKind = raw.startsWith('+') && !raw.startsWith('+++') ? 'addition' : raw.startsWith('-') && !raw.startsWith('---') ? 'deletion' : 'context'
    const text = kind === 'context' ? raw : raw.slice(1)
    const line: DiffLine = { kind, oldLine: kind === 'addition' ? undefined : oldLine++, newLine: kind === 'deletion' ? undefined : newLine++, text, tokens: [{ text, kind: 'plain' }] }
    return line
  })
}

function addInlineDiff(lines: DiffLine[]): DiffLine[] {
  for (let index = 0; index < lines.length - 1; index += 1) {
    const left = lines[index]
    const right = lines[index + 1]
    if (left.kind !== 'deletion' || right.kind !== 'addition') continue
    const commonPrefix = commonStart(left.text, right.text)
    const commonSuffix = commonEnd(left.text.slice(commonPrefix), right.text.slice(commonPrefix))
    const leftEnd = left.text.length - commonSuffix
    const rightEnd = right.text.length - commonSuffix
    left.tokens = [
      { text: left.text.slice(0, commonPrefix), kind: 'plain' },
      { text: left.text.slice(commonPrefix, leftEnd), kind: 'inline-delete' },
      { text: left.text.slice(leftEnd), kind: 'plain' },
    ].filter((token) => token.text)
    right.tokens = [
      { text: right.text.slice(0, commonPrefix), kind: 'plain' },
      { text: right.text.slice(commonPrefix, rightEnd), kind: 'inline-add' },
      { text: right.text.slice(rightEnd), kind: 'plain' },
    ].filter((token) => token.text)
  }
  return lines
}

function commonStart(left: string, right: string): number {
  let index = 0
  while (index < left.length && index < right.length && left[index] === right[index]) index += 1
  return index
}

function commonEnd(left: string, right: string): number {
  let index = 0
  // The suffix scan runs on the prefix-trimmed remainder, so
  // it may consume the complete remainder without corrupting inline ranges.
  while (index < left.length && index < right.length && left[left.length - 1 - index] === right[right.length - 1 - index]) index += 1
  return index
}

function splitRows(file: { lines: DiffLine[] }): { left: DiffLine[]; right: DiffLine[] } {
  const left: DiffLine[] = []
  const right: DiffLine[] = []
  for (const line of file.lines) {
    if (line.kind === 'hunk') { left.push(line); right.push(line); continue }
    if (line.kind === 'deletion') left.push(line)
    else if (line.kind === 'addition') right.push(line)
    else { left.push(line); right.push(line) }
  }
  return { left, right }
}

function marker(kind: DiffKind): string { return kind === 'addition' ? '+' : kind === 'deletion' ? '−' : kind === 'hunk' ? '·' : '' }
function statusLabel(status: string): string { return status === 'added' ? 'A' : status === 'deleted' ? 'D' : status === 'renamed' ? 'R' : 'M' }
function toggleFile(path: string): void { const next = new Set(collapsedFiles.value); next.has(path) ? next.delete(path) : next.add(path); collapsedFiles.value = next }
async function copyDiff(): Promise<void> { try { await navigator.clipboard.writeText(props.diff); emit('toast', 'Diff 已复制', 'success') } catch { emit('toast', '当前浏览器不允许自动复制', 'warning') } }
async function copyLine(text: string): Promise<void> { try { await navigator.clipboard.writeText(text); emit('toast', '代码行已复制', 'success') } catch { emit('toast', '当前浏览器不允许自动复制', 'warning') } }
async function toggleFullscreen(): Promise<void> { if (!root.value) return; if (!document.fullscreenElement) { await root.value.requestFullscreen(); fullscreen.value = true } else { await document.exitFullscreen(); fullscreen.value = false } }
function onKeydown(event: KeyboardEvent): void { if (event.key === 'ArrowDown') { event.preventDefault(); viewport.value?.scrollBy({ top: 36, behavior: 'smooth' }) } else if (event.key === 'ArrowUp') { event.preventDefault(); viewport.value?.scrollBy({ top: -36, behavior: 'smooth' }) } }
function onFullscreenChange(): void { fullscreen.value = document.fullscreenElement === root.value }
document.addEventListener('fullscreenchange', onFullscreenChange)
onBeforeUnmount(() => document.removeEventListener('fullscreenchange', onFullscreenChange))
</script>

<style scoped>
.git-diff-viewer { display:flex; min-width:0; min-height:280px; flex-direction:column; overflow:hidden; border:1px solid var(--git-border,#d0d7de); border-radius:8px; background:var(--git-panel,#fff); color:var(--git-text,#24292f); }
.git-diff-viewer.is-fullscreen { height:100vh; border:0; border-radius:0; }
.git-diff-viewer__header { display:flex; min-height:48px; align-items:center; justify-content:space-between; gap:12px; border-bottom:1px solid var(--git-border,#d0d7de); padding:0 12px; }
.git-diff-viewer__title,.git-diff-viewer__actions,.git-diff-viewer__summary { display:flex; align-items:center; gap:8px; }
.git-diff-viewer__title { min-width:0; }.git-diff-viewer__title strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.git-diff-viewer__title>i { color:#0969da; }
.git-diff-viewer__summary b,.git-diff-file__stats b { font-size:11px; font-weight:700; }.is-add { color:#1a7f37; }.is-delete { color:#cf222e; }
.git-diff-viewer__actions button { min-width:30px; height:30px; border:1px solid transparent; border-radius:6px; background:transparent; color:var(--git-muted,#57606a); font-size:11px; }.git-diff-viewer__actions button:hover,.git-diff-viewer__actions button.active { border-color:var(--git-border,#d0d7de); background:var(--git-hover,#f6f8fa); color:#0969da; }
.git-diff-viewer__viewport { min-height:220px; flex:1; overflow:auto; outline:0; }.git-diff-file { min-width:max-content; border-bottom:1px solid var(--git-border,#d0d7de); }.git-diff-file:last-child { border-bottom:0; }
.git-diff-file__header { display:flex; min-width:100%; min-height:40px; align-items:center; gap:8px; border-bottom:1px solid var(--git-border,#d0d7de); background:var(--git-subtle,#f6f8fa); padding:0 10px; }.git-diff-file__toggle { width:24px; height:24px; border:0; background:transparent; color:var(--git-muted,#57606a); }.git-diff-file__header strong { max-width:min(70vw,780px); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.git-diff-file__status { display:inline-flex; width:19px; height:19px; align-items:center; justify-content:center; border-radius:4px; background:#ddf4ff; color:#0969da; font:700 10px ui-monospace,monospace; }.git-diff-file__status.is-added { background:#dcffe4; color:#1a7f37; }.git-diff-file__status.is-deleted { background:#ffebe9; color:#cf222e; }.git-diff-file__stats { display:flex; gap:7px; margin-left:auto; }
.git-diff-split { display:grid; grid-template-columns:minmax(0,1fr) minmax(0,1fr); min-width:max-content; }.git-diff-split__side + .git-diff-split__side { border-left:1px solid var(--git-border,#d0d7de); }.git-diff-unified { min-width:max-content; }
.git-diff-row { display:flex; min-height:22px; align-items:stretch; width:max-content; min-width:100%; font:13px/1.7 ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace; }.git-diff-gutter { display:inline-flex; min-width:52px; flex:0 0 52px; align-items:center; justify-content:flex-end; border-right:1px solid var(--git-border,#d0d7de); background:var(--git-subtle,#f6f8fa); padding:0 8px; color:var(--git-muted,#8c959f); user-select:none; }.git-diff-gutter--old { border-left:0; }.git-diff-marker { display:inline-flex; width:22px; flex:0 0 22px; align-items:center; justify-content:center; color:var(--git-muted,#8c959f); user-select:none; }.git-diff-code { display:inline-block;  min-width:calc(100% - 126px); padding:0 12px 0 2px; color:var(--git-text,#24292f); white-space:pre; }.git-diff-code--hunk { min-width:0; color:#0969da; }
.git-diff-row.is-addition .git-diff-gutter { border-left:3px solid #07b112; background:#dcffe4; color:#07b112; }.git-diff-row.is-addition .git-diff-marker { background:#dcffe4; color:#07b112; }.git-diff-row.is-addition .git-diff-code { background:#effff3; }.git-diff-row.is-deletion .git-diff-gutter { border-left:3px solid #cf222e; background:#ffebe9; color:#cf222e; }.git-diff-row.is-deletion .git-diff-marker { background:#ffebe9; color:#cf222e; }.git-diff-row.is-deletion .git-diff-code { background:#fff1f0; }.git-diff-row.is-hunk { background:#ddf4ff; }.git-diff-row.is-hunk .git-diff-gutter,.git-diff-row.is-hunk .git-diff-marker { background:#ddf4ff; color:#0969da; }
.git-diff-row.is-context .git-diff-code { background:transparent; }.git-diff-code span { white-space:pre; }.git-diff-code .inline-add { background:#aceebb; color:#116329; }.git-diff-code .inline-delete { background:#ffb8b3; color:#a40e26; }.git-diff-viewer__empty,.git-diff-viewer__skeleton { display:flex; min-height:220px; flex:1; flex-direction:column; align-items:center; justify-content:center; gap:10px; color:var(--git-muted,#57606a); }.git-diff-viewer__empty i { color:#1a7f37; font-size:26px; }.git-diff-viewer__skeleton { align-items:stretch; padding:20px; }.git-diff-viewer__skeleton span { height:17px; border-radius:4px; background:linear-gradient(90deg,#eaeef2 25%,#f6f8fa 50%,#eaeef2 75%); background-size:200% 100%; animation:git-diff-loading 1.2s infinite; }
:global(.dark) .git-diff-viewer { --git-panel:#0d1117; --git-subtle:#161b22; --git-text:#c9d1d9; --git-muted:#8b949e; --git-border:#30363d; --git-hover:#21262d; }.dark-mode .git-diff-viewer { --git-panel:#0d1117; --git-subtle:#161b22; --git-text:#c9d1d9; --git-muted:#8b949e; --git-border:#30363d; --git-hover:#21262d; }.dark-mode .git-diff-row.is-addition .git-diff-code { background:#12261a; }.dark-mode .git-diff-row.is-deletion .git-diff-code { background:#2d1618; }.dark-mode .git-diff-code .inline-add { background:#246b36; color:#aff5b5; }.dark-mode .git-diff-code .inline-delete { background:#8d2430; color:#ffd7d5; }
@keyframes git-diff-loading { to { background-position:-200% 0; } }
@media (max-width:767px) { .git-diff-viewer__header { align-items:flex-start; flex-direction:column; padding:9px 10px; }.git-diff-viewer__actions { width:100%; }.git-diff-viewer__actions button:first-child,.git-diff-viewer__actions button:nth-child(2) { flex:1; }.git-diff-code { padding-right:25px; } }
@media (prefers-reduced-motion:reduce) { .git-diff-viewer__skeleton span { animation:none; } }
</style>
