<template>
  <section class="git-commit-graph" aria-label="仓库提交图">
    <header class="git-commit-graph__toolbar">
      <div class="git-commit-graph__scope" role="group" aria-label="提交图范围">
        <button type="button" :class="{ active: graphScope === 'all' }" @click="setScope('all')"><i class="fa fa-code-fork"></i>全部分支</button>
        <button type="button" :class="{ active: graphScope === 'current' }" @click="setScope('current')">仅当前分支</button>
      </div>
      <label v-if="graphScope === 'current'"><i class="fa fa-code-fork"></i><select v-model="selectedRef" @change="reload"><option v-for="item in branchOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
      <label><i class="fa fa-user"></i><select v-model="authorFilter"><option value="">所有作者</option><option v-for="author in authors" :key="author" :value="author">{{ author }}</option></select></label>
      <label><i class="fa fa-sort-amount-desc"></i><select v-model="layoutMode"><option value="topology">拓扑排序</option><option value="date">日期排序</option></select></label>
      <label class="git-commit-graph__search"><i class="fa fa-search"></i><input v-model.trim="query" type="search" placeholder="搜索哈希或提交信息" /></label>
      <span v-if="filePath" class="git-commit-graph__path-filter"><i class="fa fa-file-o"></i>{{ filePath }}<button type="button" title="清除文件历史筛选" @click="$emit('clear-file-history')"><i class="fa fa-times"></i></button></span>
      <div class="git-commit-graph__zoom" role="group" aria-label="提交图缩放"><button type="button" title="缩小纵向间距" @click="zoomY = Math.max(.75, zoomY - .1)">Y−</button><button type="button" title="放大纵向间距" @click="zoomY = Math.min(1.5, zoomY + .1)">Y+</button><button type="button" title="缩小横向间距" @click="zoomX = Math.max(.75, zoomX - .1)">X−</button><button type="button" title="放大横向间距" @click="zoomX = Math.min(1.7, zoomX + .1)">X+</button></div>
      <button type="button" title="刷新提交图" aria-label="刷新提交图" @click="reload"><i class="fa fa-refresh"></i></button>
    </header>
    <div v-if="branchLegend.length" class="git-commit-graph__legend" aria-label="分支图例"><button v-for="branch in branchLegend" :key="branch.name" type="button" :title="`跳转到 ${branch.name} 最新提交`" @click="jumpToBranch(branch.name)"><i :style="{ backgroundColor: branch.color }"></i><span>{{ branch.name }}</span><b v-if="branch.remote">远端</b></button></div>

    <div v-if="error" class="git-commit-graph__error"><i class="fa fa-exclamation-triangle"></i>{{ error }}<button type="button" @click="reload">重试</button></div>
    <div v-else-if="loading && !rows.length" class="git-commit-graph__skeleton"><span v-for="index in 7" :key="index"></span></div>
    <div v-else-if="!rows.length" class="git-commit-graph__empty"><i class="fa fa-code-fork"></i><h3>{{ query || authorFilter ? '没有匹配的提交' : '当前引用暂无提交' }}</h3><p>{{ query || authorFilter ? '请调整筛选条件后重试。' : '首次推送到该分支后，提交图会自动出现。' }}</p></div>
    <div v-else ref="scrollElement" class="git-commit-graph__scroll" @scroll="onScroll">
      <div class="git-commit-graph__spacer" :style="{ height: `${rows.length * rowHeight}px` }">
        <div class="git-commit-graph__window" :style="{ transform: `translateY(${windowStart * rowHeight}px)` }">
          <article v-for="row in visibleRows" :key="row.commit.hash" class="git-commit-graph__row" :class="{ 'is-selected': selectedCommit?.hash === row.commit.hash }" :style="{ height: `${rowHeight}px` }" @click="selectCommit(row.commit)">
            <svg class="git-commit-graph__svg" :style="{ width: `${graphWidth(row, laneWidth)}px` }" :viewBox="`0 0 ${graphWidth(row, laneWidth)} ${rowHeight}`" aria-hidden="true">
              <line v-for="(_, lane) in row.activeLanes" :key="`vertical-${lane}`" :x1="graphX(lane, laneWidth)" :x2="graphX(lane, laneWidth)" y1="0" :y2="rowHeight" :stroke="laneColor(row.laneKeys[lane])" :stroke-dasharray="laneDash(row.laneKeys[lane])" stroke-width="2" />
              <path v-for="(edge, index) in row.edges" :key="`edge-${index}`" :d="edgePath(edge.from, edge.to)" :stroke="laneColor(edge.branchKey)" :stroke-dasharray="laneDash(edge.branchKey)" fill="none" stroke-width="2" />
              <!-- AUDIT FIX [1.5-1.7] Merge commits keep the active branch color; the
                   double ring expresses the merge without replacing branch identity. -->
              <circle :cx="graphX(row.lane, laneWidth)" :cy="nodeY" :r="row.isMerge ? 7 : 6" :fill="laneColor(row.branchKey)" :stroke="row.isMerge ? '#8250df' : 'var(--git-panel,#fff)'" stroke-width="2" />
              <circle v-if="row.isMerge" :cx="graphX(row.lane, laneWidth)" :cy="nodeY" r="2.2" fill="var(--git-panel,#fff)" />
            </svg>
            <div class="git-commit-graph__card">
              <div class="git-commit-graph__title"><strong>{{ row.commit.subject || '（无提交说明）' }}</strong><span v-if="row.isMerge" class="git-commit-graph__merge"><i class="fa fa-code-fork"></i>合并</span></div>
              <div class="git-commit-graph__meta"><button type="button" :title="row.commit.hash" @click.stop="copy(row.commit.hash)">{{ shortHash(row.commit.hash) }}</button><span>{{ row.commit.authorName || '未知作者' }}</span><time :title="formatAbsoluteTime(commitTimestamp(row.commit))">{{ formatGitRelativeTime(commitTimestamp(row.commit)) }}</time></div>
              <div v-if="row.labels.length" class="git-commit-graph__labels"><span v-for="label in row.labels" :key="`${label.type}-${label.name}`" :class="label.type === 'TAG' ? 'tag' : 'branch'"><i :class="label.type === 'TAG' ? 'fa fa-tag' : 'fa fa-code-fork'"></i>{{ label.name }}<b v-if="label.name === refName">HEAD</b></span></div>
            </div>
          </article>
        </div>
      </div>
      <div v-if="loadingMore" class="git-commit-graph__more"><i class="fa fa-circle-o-notch fa-spin"></i>正在加载更早提交…</div>
      <div v-else-if="!hasMore" class="git-commit-graph__more">已展示全部已加载提交</div>
    </div>

    <section v-if="selectedCommit" class="git-commit-graph__detail" aria-label="提交详情">
      <header><div><span class="git-commit-graph__eyebrow">提交详情</span><h3>{{ selectedCommit.subject || '（无提交说明）' }}</h3><p>{{ selectedCommit.message || '暂无提交说明' }}</p></div><button type="button" title="关闭详情" aria-label="关闭提交详情" @click="selectedCommit = null"><i class="fa fa-times"></i></button></header>
      <dl><div><dt>完整哈希</dt><dd><button type="button" @click="copy(selectedCommit.hash)">{{ selectedCommit.hash }}</button></dd></div><div><dt>作者</dt><dd>{{ selectedCommit.authorName }} &lt;{{ selectedCommit.authorEmail }}&gt;</dd></div><div><dt>提交时间</dt><dd>{{ formatAbsoluteTime(commitTimestamp(selectedCommit)) }}</dd></div><div><dt>父提交</dt><dd>{{ selectedCommit.parents.map((item) => shortHash(item)).join('、') || '初始提交' }}</dd></div></dl>
      <div v-if="diffLoading" class="git-commit-graph__diff-loading"><i class="fa fa-circle-o-notch fa-spin"></i>正在计算文件差异…</div>
      <div v-else-if="!diffFiles.length" class="git-commit-graph__diff-empty">{{ selectedCommit.parents.length ? '本次提交未产生文本差异，或文件为二进制。' : '初始提交暂无可比较的父提交。' }}</div>
      <div v-else class="git-commit-graph__diff-files"><button v-for="file in diffFiles" :key="file.path" type="button" :class="{ active: selectedDiffFile?.path === file.path }" @click="selectedDiffFile = file"><span :class="`status-${file.status}`">{{ statusText(file.status) }}</span><b>{{ file.path }}</b><em>+{{ file.additions }} −{{ file.deletions }}</em></button></div>
      <GitDiffViewer v-if="selectedDiffFile" :diff="selectedDiffFile.content" :file-name="selectedDiffFile.path" @toast="(message, type) => emit('toast', message, type)" />
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { GitCommit, GitRef } from '@/api/modules/git'
import { getGitDiffApi, listGitCommitsApi } from '@/api/modules/git'
import GitDiffViewer from '@/components/git/GitDiffViewer.vue'
import { BRANCH_COLOR_PALETTE, GRAPH_LANE_WIDTH, GRAPH_ROW_HEIGHT, buildGitGraphRows, graphWidth, graphX, isRemoteBranch, stableBranchColors } from '@/utils/gitCommitGraph'
import { commitTimestamp, formatGitRelativeTime, shortHash, splitGitDiff, type GitDiffFile } from '@/utils/gitRepositoryPresentation'

const props = withDefaults(defineProps<{ repositoryId: string; refName: string; refs: GitRef[]; filePath?: string }>(), { filePath: '' })
const emit = defineEmits<{ toast: [message: string, type?: 'success' | 'error' | 'warning']; 'clear-file-history': [] }>()
const PAGE_SIZE = 50
const OVERSCAN = 7
const commits = ref<GitCommit[]>([])
const selectedRef = ref(props.refName)
const graphScope = ref<'all' | 'current'>('all')
const authorFilter = ref('')
const query = ref('')
const layoutMode = ref<'topology' | 'date'>('topology')
const loading = ref(false)
const loadingMore = ref(false)
const hasMore = ref(true)
const error = ref('')
const page = ref(1)
const scrollElement = ref<HTMLElement | null>(null)
const scrollTop = ref(0)
const viewportHeight = ref(560)
const selectedCommit = ref<GitCommit | null>(null)
const diffFiles = ref<GitDiffFile[]>([])
const selectedDiffFile = ref<GitDiffFile | null>(null)
const diffLoading = ref(false)
const zoomY = ref(1)
const zoomX = ref(1)

const branchOptions = computed(() => {
  const refs = props.refs.filter((item) => item.type === 'BRANCH' || item.type === 'TAG')
  return refs.map((item) => ({ value: item.name, label: `${item.type === 'TAG' ? '标签' : '分支'}：${item.name}` }))
})
const authors = computed(() => [...new Set(commits.value.map((item) => item.authorName).filter(Boolean))].sort((left, right) => left.localeCompare(right, 'zh-CN')))
const filteredCommits = computed(() => {
  const keyword = query.value.toLocaleLowerCase()
  const items = commits.value.filter((item) => (!authorFilter.value || item.authorName === authorFilter.value) && (!keyword || item.hash.includes(keyword) || item.subject.toLocaleLowerCase().includes(keyword) || item.message.toLocaleLowerCase().includes(keyword)))
  return layoutMode.value === 'date' ? [...items].sort((left, right) => new Date(commitTimestamp(right) || 0).getTime() - new Date(commitTimestamp(left) || 0).getTime()) : items
})
const branchColors = computed(() => stableBranchColors(props.refs, props.refName, `pcd.git.graph.branch-colors.${props.repositoryId}`))
const branchLegend = computed(() => props.refs.filter((ref) => ref.type === 'BRANCH').map((ref) => ({ name: ref.name, color: branchColors.value[ref.name] || BRANCH_COLOR_PALETTE[0], remote: isRemoteBranch(ref.name) })))
const rowHeight = computed(() => GRAPH_ROW_HEIGHT * zoomY.value)
const laneWidth = computed(() => GRAPH_LANE_WIDTH * zoomX.value)
const nodeY = computed(() => rowHeight.value * (31 / GRAPH_ROW_HEIGHT))
const rows = computed(() => buildGitGraphRows(filteredCommits.value, props.refs, props.refName))
const windowStart = computed(() => Math.max(0, Math.floor(scrollTop.value / rowHeight.value) - OVERSCAN))
const windowEnd = computed(() => Math.min(rows.value.length, Math.ceil((scrollTop.value + viewportHeight.value) / rowHeight.value) + OVERSCAN))
const visibleRows = computed(() => rows.value.slice(windowStart.value, windowEnd.value))

watch(() => [props.refName, props.filePath] as const, ([value]) => { selectedRef.value = value; void reload() }, { immediate: true })

async function reload(): Promise<void> { commits.value = []; page.value = 1; hasMore.value = true; selectedCommit.value = null; diffFiles.value = []; selectedDiffFile.value = null; if (scrollElement.value) scrollElement.value.scrollTop = 0; await fetchPage(false) }
/**
 * [REQ-GIT-GRAPH-5.1~5.25] 默认请求 Git 服务端的 all=1 聚合历史；旧版只请求
 * selectedRef，导致存在分支但图上完全不可见。服务端固定映射为 `git log --all`，
 * 保留“仅当前分支”诊断模式，不把任意 revision 表达式交给浏览器或用户输入。
 */
function setScope(scope: 'all' | 'current'): void { if (graphScope.value === scope) return; graphScope.value = scope; void reload() }
async function fetchPage(append: boolean): Promise<void> {
  if (!props.repositoryId || (append && (!hasMore.value || loadingMore.value))) return
  if (append) loadingMore.value = true; else loading.value = true
  error.value = ''
  try { const batch = (await listGitCommitsApi(props.repositoryId, selectedRef.value, page.value, PAGE_SIZE, { path: props.filePath || undefined, all: graphScope.value === 'all' })).data || []; commits.value = append ? [...commits.value, ...batch.filter((candidate) => !commits.value.some((item) => item.hash === candidate.hash))] : batch; hasMore.value = batch.length === PAGE_SIZE; if (hasMore.value) page.value += 1 } catch (cause: any) { error.value = cause?.message || '提交图加载失败' } finally { loading.value = false; loadingMore.value = false }
}
function onScroll(event: Event): void { const target = event.currentTarget as HTMLElement; scrollTop.value = target.scrollTop; viewportHeight.value = target.clientHeight; if (target.scrollTop + target.clientHeight >= target.scrollHeight - rowHeight.value * 4) void fetchPage(true) }
async function selectCommit(commit: GitCommit): Promise<void> { selectedCommit.value = commit; diffFiles.value = []; selectedDiffFile.value = null; if (!commit.parents.length) return; diffLoading.value = true; try { const response = await getGitDiffApi(props.repositoryId, commit.parents[0], commit.hash); diffFiles.value = splitGitDiff(response.data.diff || ''); selectedDiffFile.value = diffFiles.value[0] || null } catch (cause: any) { emit('toast', cause?.message || '提交差异加载失败', 'error') } finally { diffLoading.value = false } }
function laneColor(branchKey: string): string { return branchColors.value[branchKey] || BRANCH_COLOR_PALETTE[Math.abs(hashCode(branchKey)) % BRANCH_COLOR_PALETTE.length] }
function laneDash(branchKey: string): string { return isRemoteBranch(branchKey) ? '6 4' : 'none' }
function edgePath(from: number, to: number): string { const start = graphX(from, laneWidth.value); const end = graphX(to, laneWidth.value); return `M ${start} ${nodeY.value} C ${start} ${nodeY.value + rowHeight.value * .25}, ${end} ${rowHeight.value * .72}, ${end} ${rowHeight.value}` }
function hashCode(value: string): number { return Array.from(value).reduce((hash, char) => ((hash << 5) - hash + char.charCodeAt(0)) | 0, 0) }
function jumpToBranch(name: string): void { const index = rows.value.findIndex((row) => row.labels.some((label) => label.name === name)); if (index < 0 || !scrollElement.value) return; scrollElement.value.scrollTop = index * rowHeight.value }
function statusText(status: GitDiffFile['status']): string { return ({ added: 'A', deleted: 'D', modified: 'M', renamed: 'R' })[status] }
function formatAbsoluteTime(value?: string): string { return value ? new Date(value).toLocaleString('zh-CN') : '—' }
async function copy(value: string): Promise<void> { try { await navigator.clipboard.writeText(value); emit('toast', '提交哈希已复制', 'success') } catch { emit('toast', '当前浏览器不允许自动复制', 'warning') } }
</script>

<style scoped>
.git-commit-graph__legend { display:flex; flex-wrap:wrap; gap:5px; border-bottom:1px solid var(--git-border,#d0d7de); padding:7px 10px; }.git-commit-graph__legend button { display:inline-flex; align-items:center; gap:5px; border:0; border-radius:999px; background:transparent; padding:4px 7px; color:var(--git-muted,#57606a); font-size:10px; }.git-commit-graph__legend button:hover { background:var(--git-hover,#f6f8fa); color:var(--git-text,#24292f); }.git-commit-graph__legend i { width:9px; height:9px; border-radius:50%; }.git-commit-graph__legend b { font-size:9px; font-weight:500; }.git-commit-graph__zoom { display:inline-flex; overflow:hidden; border:1px solid var(--git-border,#d0d7de); border-radius:6px; }.git-commit-graph__zoom button { height:28px; border:0; border-right:1px solid var(--git-border,#d0d7de); background:transparent; padding:0 6px; color:var(--git-muted,#57606a); font-size:10px; }.git-commit-graph__zoom button:last-child { border-right:0; }.git-commit-graph__zoom button:hover { background:var(--git-hover,#f6f8fa); color:#0969da; }
.git-commit-graph { overflow:hidden; border:1px solid var(--git-border,#d0d7de); border-radius:8px; background:var(--git-panel,#fff); color:var(--git-text,#24292f); }
.git-commit-graph__toolbar { display:flex; flex-wrap:wrap; align-items:center; gap:8px; border-bottom:1px solid var(--git-border,#d0d7de); padding:10px; }.git-commit-graph__scope { display:inline-flex; overflow:hidden; border:1px solid var(--git-border,#d0d7de); border-radius:6px; }.git-commit-graph__scope button { min-height:30px; border:0; border-right:1px solid var(--git-border,#d0d7de); background:transparent; padding:0 8px; color:var(--git-muted,#57606a); font-size:11px; font-weight:600; }.git-commit-graph__scope button:last-child { border-right:0; }.git-commit-graph__scope button.active { background:#ddf4ff; color:#0969da; }
.git-commit-graph__toolbar label { display:flex; align-items:center; gap:6px; border:1px solid var(--git-border,#d0d7de); border-radius:6px; padding:0 7px; color:var(--git-muted,#57606a); }.git-commit-graph__toolbar select,.git-commit-graph__toolbar input { height:30px; min-width:0; border:0; outline:0; background:transparent; color:var(--git-text,#24292f); font-size:12px; }.git-commit-graph__toolbar select { max-width:170px; }.git-commit-graph__search { min-width:170px; flex:1; }.git-commit-graph__search input { width:100%; }.git-commit-graph__path-filter { display:inline-flex; max-width:260px; align-items:center; gap:5px; overflow:hidden; border-radius:999px; background:var(--git-hover,#f6f8fa); padding:5px 7px; color:var(--git-muted,#57606a); font:11px ui-monospace,monospace; text-overflow:ellipsis; white-space:nowrap; }.git-commit-graph__path-filter button { border:0; background:transparent; color:inherit; }.git-commit-graph__toolbar>button { width:30px; height:30px; border:0; border-radius:6px; background:transparent; color:var(--git-muted,#57606a); }.git-commit-graph__toolbar>button:hover { background:var(--git-hover,#f6f8fa); color:#0969da; }
.git-commit-graph__scroll { max-height:650px; overflow:auto; overscroll-behavior:contain; }.git-commit-graph__spacer { position:relative; min-width:660px; }.git-commit-graph__window { position:absolute; top:0; left:0; right:0; }.git-commit-graph__row { display:flex; min-width:660px; cursor:pointer; }.git-commit-graph__row:hover,.git-commit-graph__row.is-selected { background:var(--git-hover,#f6f8fa); }.git-commit-graph__svg { flex:0 0 auto; overflow:visible; }.git-commit-graph__card { display:flex; min-width:0; flex:1; flex-direction:column; justify-content:center; gap:4px; padding:8px 12px 8px 4px; }.git-commit-graph__title { display:flex; min-width:0; align-items:center; gap:7px; }.git-commit-graph__title strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:13px; }.git-commit-graph__merge { flex:0 0 auto; border-radius:999px; background:#fbefff; padding:2px 6px; color:#8250df; font-size:10px; }.git-commit-graph__meta { display:flex; align-items:center; gap:9px; color:var(--git-muted,#57606a); font-size:11px; }.git-commit-graph__meta button { border:0; border-radius:4px; background:var(--git-hover,#eaeef2); padding:2px 5px; color:#0969da; font:11px ui-monospace,monospace; }.git-commit-graph__meta time { margin-left:auto; }.git-commit-graph__labels { display:flex; flex-wrap:wrap; gap:4px; }.git-commit-graph__labels span { display:inline-flex; align-items:center; gap:4px; border-radius:999px; padding:1px 5px; font-size:10px; }.git-commit-graph__labels .branch { background:#ddf4ff; color:#0969da; }.git-commit-graph__labels .tag { background:#fff8c5; color:#9a6700; }.git-commit-graph__labels b { margin-left:2px; }.git-commit-graph__more { min-width:660px; padding:11px; color:var(--git-muted,#57606a); font-size:12px; text-align:center; }
.git-commit-graph__empty,.git-commit-graph__error { display:flex; min-height:270px; flex-direction:column; align-items:center; justify-content:center; gap:9px; padding:24px; color:var(--git-muted,#57606a); text-align:center; }.git-commit-graph__empty i,.git-commit-graph__error i { font-size:28px; color:#8c959f; }.git-commit-graph__empty h3 { margin:0; color:var(--git-text,#24292f); font-size:15px; }.git-commit-graph__empty p { margin:0; font-size:12px; }.git-commit-graph__error { min-height:120px; color:#cf222e; }.git-commit-graph__error button { border:0; background:transparent; color:#0969da; text-decoration:underline; }.git-commit-graph__skeleton { display:flex; flex-direction:column; gap:13px; padding:18px; }.git-commit-graph__skeleton span { height:40px; border-radius:6px; background:linear-gradient(90deg,#eaeef2 25%,#f6f8fa 50%,#eaeef2 75%); background-size:200% 100%; animation:git-graph-loading 1.2s infinite; }
.git-commit-graph__detail { border-top:1px solid var(--git-border,#d0d7de); padding:16px; }.git-commit-graph__detail>header { display:flex; justify-content:space-between; gap:12px; }.git-commit-graph__detail>header h3 { margin:3px 0; font-size:15px; }.git-commit-graph__detail>header p { margin:0; white-space:pre-wrap; color:var(--git-muted,#57606a); font-size:12px; }.git-commit-graph__detail>header>button { width:28px; height:28px; border:0; border-radius:6px; background:transparent; color:var(--git-muted,#57606a); }.git-commit-graph__eyebrow { color:#0969da; font-size:11px; font-weight:700; }.git-commit-graph__detail dl { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; margin:15px 0; }.git-commit-graph__detail dt { color:var(--git-muted,#57606a); font-size:10px; }.git-commit-graph__detail dd { overflow:hidden; margin:3px 0 0; text-overflow:ellipsis; white-space:nowrap; font-size:12px; }.git-commit-graph__detail dd button { max-width:100%; overflow:hidden; border:0; background:transparent; padding:0; color:#0969da; text-overflow:ellipsis; white-space:nowrap; font:12px ui-monospace,monospace; }
.git-commit-graph__diff-loading,.git-commit-graph__diff-empty { padding:18px; color:var(--git-muted,#57606a); font-size:12px; text-align:center; }.git-commit-graph__diff-files { display:flex; flex-direction:column; overflow:hidden; border:1px solid var(--git-border,#d0d7de); border-radius:6px; }.git-commit-graph__diff-files button { display:flex; align-items:center; gap:8px; border:0; border-bottom:1px solid var(--git-border,#d0d7de); background:transparent; padding:8px 10px; color:var(--git-text,#24292f); text-align:left; font-size:12px; }.git-commit-graph__diff-files button:last-child { border-bottom:0; }.git-commit-graph__diff-files button:hover,.git-commit-graph__diff-files button.active { background:var(--git-hover,#f6f8fa); }.git-commit-graph__diff-files span { display:inline-flex; width:18px; align-items:center; justify-content:center; border-radius:4px; font-size:10px; font-weight:700; }.git-commit-graph__diff-files .status-added { background:#dafbe1; color:#1a7f37; }.git-commit-graph__diff-files .status-deleted { background:#ffebe9; color:#cf222e; }.git-commit-graph__diff-files .status-modified { background:#fff8c5; color:#9a6700; }.git-commit-graph__diff-files .status-renamed { background:#ddf4ff; color:#0969da; }.git-commit-graph__diff-files b { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-weight:500; }.git-commit-graph__diff-files em { margin-left:auto; color:var(--git-muted,#57606a); font-style:normal; white-space:nowrap; }
:global(.dark) .git-commit-graph { --git-panel:#0d1117;--git-text:#c9d1d9;--git-muted:#8b949e;--git-border:#30363d;--git-hover:#21262d;--git-code-bg:#161b22; }.dark-mode .git-commit-graph { --git-panel:#0d1117;--git-text:#c9d1d9;--git-muted:#8b949e;--git-border:#30363d;--git-hover:#21262d;--git-code-bg:#161b22; }.dark-mode .git-commit-graph__labels .branch { background:#1f6feb44;color:#58a6ff; }.dark-mode .git-commit-graph__labels .tag { background:#9e6a0333;color:#e3b341; }.dark-mode .git-commit-graph__diff .addition,.dark-mode .git-commit-graph__diff-files .status-added { background:#2ea04326;color:#3fb950; }.dark-mode .git-commit-graph__diff .deletion,.dark-mode .git-commit-graph__diff-files .status-deleted { background:#f8514926;color:#ff7b72; }
@keyframes git-graph-loading { to { background-position:-200% 0; } } @media (max-width:767px) { .git-commit-graph__toolbar { gap:6px; }.git-commit-graph__toolbar label { max-width:100%; }.git-commit-graph__toolbar select { max-width:130px; }.git-commit-graph__search { order:2;flex-basis:100%; }.git-commit-graph__detail dl { grid-template-columns:1fr; }.git-commit-graph__card { padding-right:8px; }.git-commit-graph__meta span { max-width:90px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap; } } @media (prefers-reduced-motion:reduce) { .git-commit-graph__skeleton span { animation:none; } }
</style>
