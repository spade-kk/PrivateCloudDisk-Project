<template>
  <section class="audit-viewer" aria-label="插件行为审计">
    <header class="audit-toolbar">
      <div>
        <p class="audit-toolbar__eyebrow">CAPABILITY AUDIT TRAIL</p>
        <h3>行为审计 <span>{{ audits.length }}</span></h3>
      </div>
      <div class="audit-toolbar__controls">
        <div class="audit-mode" role="group" aria-label="审计展示模式"><button :class="{ active: mode === 'summary' }" type="button" title="以自然语言查看行为摘要" @click="setMode('summary')">摘要</button><button :class="{ active: mode === 'detail' }" type="button" title="查看能力键、参数与返回 JSON" @click="setMode('detail')">详情</button></div>
        <button type="button" title="导出审计 JSON" aria-label="导出审计 JSON" @click="$emit('download')"><i class="fa fa-download"></i></button>
      </div>
    </header>
    <div class="audit-filters">
      <select v-model="typeFilter" aria-label="能力类型" @change="emitFilters"><option value="">全部能力</option><option value="BUILTIN">内置能力</option><option value="PLATFORM_API">平台 API</option><option value="PLUGIN">插件能力</option></select>
      <select v-model="statusFilter" aria-label="调用状态" @change="emitFilters"><option value="">全部状态</option><option value="SUCCESS">成功</option><option value="FAILED">失败</option><option value="TIMEOUT">超时</option><option value="RUNNING">运行中</option></select>
      <label class="audit-search"><i class="fa fa-search"></i><input v-model="keyword" type="search" placeholder="搜索能力或摘要" /></label>
    </div>

    <div class="audit-scroll" tabindex="0">
      <div v-if="loading && !audits.length" class="audit-empty"><i class="fa fa-spinner fa-spin"></i> 正在加载审计记录…</div>
      <div v-else-if="!visibleRows.length" class="audit-empty"><i class="fa fa-shield"></i>{{ audits.length ? '没有符合条件的审计记录' : '暂无能力调用审计记录' }}</div>
      <ol v-else class="audit-list">
        <li v-for="row in visibleRows" :key="row.auditId" class="audit-item" :style="{ '--audit-depth': row.depth }">
          <div class="audit-line" :class="[`status-${row.status.toLowerCase()}`, `type-${row.capabilityType.toLowerCase()}`]">
            <span class="audit-line__connector" aria-hidden="true"></span>
            <span class="audit-status" :title="statusText(row.status)"><i :class="statusIcon(row.status)"></i></span>
            <button class="audit-main" type="button" @click="toggle(row.auditId)">
              <span class="audit-main__summary">{{ row.summary || row.capabilityKey }}</span>
              <span class="audit-main__meta"><code>{{ row.capabilityKey }}</code><span>{{ relativeTime(row.timestamp) }}</span><span>{{ duration(row.durationMs) }}</span></span>
            </button>
            <span class="audit-type" :title="typeText(row.capabilityType)">{{ typeShort(row.capabilityType) }}</span>
            <button class="audit-expand" type="button" :title="expanded.has(row.auditId) ? '收起调用详情' : '查看调用详情'" @click="toggle(row.auditId)"><i :class="expanded.has(row.auditId) ? 'fa fa-chevron-up' : 'fa fa-chevron-down'"></i></button>
          </div>
          <transition name="audit-detail">
            <div v-if="mode === 'detail' && expanded.has(row.auditId)" class="audit-detail">
              <div class="audit-detail__grid"><JsonBlock title="输入参数（已脱敏）" :value="row.inputParams || {}" /><JsonBlock title="返回结果（已脱敏）" :value="row.outputResult || {}" /></div>
              <dl><div><dt>调用 ID</dt><dd><code>{{ row.auditId }}</code></dd></div><div v-if="row.parentAuditId"><dt>父调用</dt><dd><code>{{ row.parentAuditId }}</code></dd></div><div><dt>状态</dt><dd>{{ statusText(row.status) }}</dd></div><div><dt>耗时</dt><dd>{{ duration(row.durationMs) }}</dd></div><div v-if="row.retryCount"><dt>重试</dt><dd>{{ row.retryCount }} 次</dd></div><div v-if="row.errorCode"><dt>错误</dt><dd class="is-error">{{ row.errorCode }} {{ row.errorSummary || '' }}</dd></div></dl>
            </div>
          </transition>
          <div v-if="mode === 'summary' && expanded.has(row.auditId)" class="audit-summary-actions"><button type="button" @click="setMode('detail'); toggle(row.auditId, true)">查看受控参数与结果</button><button type="button" @click="copy(row.summary)">复制描述</button></div>
        </li>
      </ol>
      <button v-if="hasMore" class="audit-more" type="button" :disabled="loadingMore" @click="$emit('load-more')"><i v-if="loadingMore" class="fa fa-spinner fa-spin"></i>{{ loadingMore ? '加载中…' : '加载更多审计记录' }}</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, ref, watch } from 'vue'
import type { PluginExecutionAuditTrail } from '@/api/modules/plugins'

const JsonBlock = defineComponent({
  name: 'AuditJsonBlock',
  props: { title: { type: String, required: true }, value: { type: Object, required: true } },
  setup(props) {
    const copied = ref(false)
    const text = computed(() => JSON.stringify(props.value, null, 2))
    async function copy() { await navigator.clipboard?.writeText(text.value); copied.value = true; window.setTimeout(() => { copied.value = false }, 1300) }
    return () => h('section', { class: 'json-block' }, [h('header', [h('span', props.title), h('button', { type: 'button', onClick: copy }, copied.value ? '已复制' : '复制')]), h('pre', text.value)])
  },
})
const props = withDefaults(defineProps<{ audits: PluginExecutionAuditTrail[]; loading?: boolean; loadingMore?: boolean; hasMore?: boolean; context?: 'drawer' | 'page' }>(), { loading: false, loadingMore: false, hasMore: false, context: 'drawer' })
const emit = defineEmits<{ download: []; 'load-more': []; filters: [filters: { capabilityType: string; status: string }] }>()
type Mode = 'summary' | 'detail'
const storageKey = `pcd.plugin-execution.audit-mode.${props.context}`
const storedMode = typeof localStorage === 'undefined' ? null : localStorage.getItem(storageKey)
const mode = ref<Mode>(storedMode === 'detail' ? 'detail' : 'summary')
const keyword = ref('')
const typeFilter = ref('')
const statusFilter = ref('')
const expanded = ref(new Set<string>())
const visibleRows = computed(() => {
  const all = props.audits.filter((row) => !keyword.value || `${row.summary} ${row.capabilityKey}`.toLowerCase().includes(keyword.value.toLowerCase()))
  const records = new Map(all.map((row) => [row.auditId, row]))
  const withDepth = (row: PluginExecutionAuditTrail) => {
    let depth = 0; let parent = row.parentAuditId; const visited = new Set<string>([row.auditId])
    while (parent && records.has(parent) && !visited.has(parent) && depth < 8) { visited.add(parent); depth += 1; parent = records.get(parent)?.parentAuditId || null }
    return { ...row, depth }
  }
  // [PLUGIN-EXEC-OBS-001] 摘要模式保留服务端的时间倒序，便于快速回看；专业模式
  // 改为父节点先于子节点的稳定 DFS，保证能力调用链不会出现“子调用悬在根节点上方”。
  if (mode.value === 'summary') return all.map(withDepth)
  const children = new Map<string, PluginExecutionAuditTrail[]>()
  const roots: PluginExecutionAuditTrail[] = []
  for (const row of all) {
    if (!row.parentAuditId || !records.has(row.parentAuditId)) roots.push(row)
    else children.set(row.parentAuditId, [...(children.get(row.parentAuditId) || []), row])
  }
  const sortBySequence = (left: PluginExecutionAuditTrail, right: PluginExecutionAuditTrail) => left.sequenceNo - right.sequenceNo
  roots.sort(sortBySequence)
  const ordered: PluginExecutionAuditTrail[] = []
  const visit = (row: PluginExecutionAuditTrail) => {
    ordered.push(row)
    ;(children.get(row.auditId) || []).sort(sortBySequence).forEach(visit)
  }
  roots.forEach(visit)
  return ordered.map(withDepth)
})
function setMode(next: Mode) { mode.value = next; localStorage?.setItem(storageKey, next); if (next === 'summary') expanded.value = new Set(); else if (visibleRows.value[0]) expanded.value = new Set([visibleRows.value[0].auditId]) }
function emitFilters() { emit('filters', { capabilityType: typeFilter.value, status: statusFilter.value }) }
function toggle(id: string, forceOpen = false) { const next = new Set(expanded.value); if (forceOpen || !next.has(id)) next.add(id); else next.delete(id); expanded.value = next }
function duration(value?: number | null) { return value == null ? '—' : value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(2)} s` }
function relativeTime(value: string) { const ms = Date.now() - Date.parse(value); if (!Number.isFinite(ms)) return value; if (ms < 60_000) return '刚刚'; if (ms < 3_600_000) return `${Math.max(1, Math.floor(ms / 60_000))} 分钟前`; return new Date(value).toLocaleString() }
function statusIcon(status: string) { return ({ SUCCESS: 'fa fa-check', FAILED: 'fa fa-times', TIMEOUT: 'fa fa-clock-o', RUNNING: 'fa fa-spinner fa-spin', SKIPPED: 'fa fa-forward' } as Record<string, string>)[status] || 'fa fa-circle-o' }
function statusText(status: string) { return ({ SUCCESS: '成功', FAILED: '失败', TIMEOUT: '超时', RUNNING: '运行中', SKIPPED: '已跳过' } as Record<string, string>)[status] || status }
function typeShort(type: string) { return ({ BUILTIN: '内置', PLATFORM_API: 'API', PLUGIN: '插件' } as Record<string, string>)[type] || type }
function typeText(type: string) { return ({ BUILTIN: '内置能力', PLATFORM_API: '平台 API', PLUGIN: '插件能力' } as Record<string, string>)[type] || type }
async function copy(value: string) { await navigator.clipboard?.writeText(value) }
watch(mode, (value) => { localStorage?.setItem(storageKey, value) })
</script>

<style scoped>
.audit-viewer { --audit-border: #e7ebf2; --audit-surface: #fff; --audit-muted: #718096; display: grid; min-height: 0; height: 100%; overflow: hidden; border: 1px solid var(--audit-border); border-radius: 12px; background: var(--audit-surface); color: #20293a; }.audit-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: .85rem 1rem .65rem; border-bottom: 1px solid var(--audit-border); }.audit-toolbar__eyebrow { margin: 0 0 .16rem; color: #8190a5; font-size: 9px; font-weight: 700; letter-spacing: .11em; }.audit-toolbar h3 { margin: 0; font-size: 14px; }.audit-toolbar h3 span { color: var(--audit-muted); font-size: 11px; font-weight: 500; }.audit-toolbar__controls { display: flex; align-items: center; gap: .45rem; }.audit-toolbar__controls > button { width: 31px; height: 31px; border: 1px solid var(--audit-border); border-radius: 7px; background: transparent; color: #59677d; cursor: pointer; }.audit-mode { display: flex; overflow: hidden; border: 1px solid var(--audit-border); border-radius: 8px; }.audit-mode button { border: 0; padding: .38rem .64rem; background: transparent; color: #64748b; font-size: 11px; cursor: pointer; }.audit-mode button.active { background: #245fca; color: white; }.audit-filters { display: flex; gap: .5rem; padding: .65rem 1rem; border-bottom: 1px solid var(--audit-border); }.audit-filters select, .audit-search { min-width: 0; height: 30px; border: 1px solid var(--audit-border); border-radius: 7px; background: transparent; color: #556276; font-size: 11px; }.audit-filters select { padding: 0 .4rem; }.audit-search { display: flex; flex: 1; align-items: center; gap: .35rem; padding: 0 .55rem; }.audit-search input { width: 100%; min-width: 0; border: 0; outline: 0; background: transparent; color: inherit; font: inherit; }.audit-scroll { min-height: 0; overflow: auto; padding: .5rem .85rem; outline: 0; }.audit-list { display: grid; gap: .2rem; margin: 0; padding: 0; list-style: none; }.audit-item { margin-left: calc(var(--audit-depth) * 14px); }.audit-line { position: relative; display: grid; grid-template-columns: 22px minmax(0, 1fr) auto 28px; align-items: center; min-height: 52px; border: 1px solid transparent; border-radius: 9px; transition: .18s ease; }.audit-line:hover { border-color: #c9d9f4; background: #f7faff; }.audit-line.status-failed { background: #fff8f8; }.audit-line.status-timeout { background: #fffaf0; }.audit-line__connector { position: absolute; left: -8px; top: -8px; bottom: 25px; width: 1px; background: #d7dfeb; }.audit-status { display: grid; width: 20px; height: 20px; place-items: center; border-radius: 50%; background: #d9e9df; color: #27814d; font-size: 10px; }.status-failed .audit-status { background: #fde1e3; color: #c83545; }.status-timeout .audit-status { background: #fff0cd; color: #ba7617; }.status-running .audit-status { background: #dce9ff; color: #2861c4; }.audit-main { min-width: 0; border: 0; padding: .42rem .35rem; background: transparent; text-align: left; cursor: pointer; }.audit-main__summary { display: block; overflow: hidden; color: #273449; font-size: 12px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }.audit-main__meta { display: flex; gap: .45rem; overflow: hidden; color: #8390a4; font-size: 10px; }.audit-main__meta code { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.audit-type { margin-right: .2rem; border-radius: 99px; padding: .2rem .35rem; background: #edf0f5; color: #64748b; font-size: 9px; font-weight: 700; }.type-platform_api .audit-type { background: #dceee3; color: #27734a; }.type-plugin .audit-type { background: #eadffd; color: #7348af; }.audit-expand { width: 27px; height: 27px; border: 0; border-radius: 6px; background: transparent; color: #7c899d; cursor: pointer; }.audit-detail { margin: .1rem .25rem .55rem 22px; overflow: hidden; border-left: 2px solid #c6d5ef; padding: .55rem .65rem; background: #f8fbff; }.audit-detail__grid { display: grid; grid-template-columns: 1fr 1fr; gap: .55rem; }.json-block { min-width: 0; border: 1px solid #dce5f1; border-radius: 7px; background: #111827; overflow: hidden; }.json-block header { display: flex; justify-content: space-between; padding: .35rem .5rem; background: #1e293b; color: #cbd5e1; font-size: 10px; }.json-block button { border: 0; background: transparent; color: #92b6fb; font-size: 10px; cursor: pointer; }.json-block pre { max-height: 190px; overflow: auto; margin: 0; padding: .55rem; color: #d6e5ff; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 10px; line-height: 1.55; white-space: pre-wrap; }.audit-detail dl { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: .4rem .8rem; margin: .6rem 0 0; font-size: 10px; }.audit-detail dt { color: #8290a5; }.audit-detail dd { overflow: hidden; margin: .1rem 0 0; color: #43526a; text-overflow: ellipsis; white-space: nowrap; }.audit-detail dd.is-error { color: #bc3848; }.audit-summary-actions { display: flex; gap: .5rem; padding: .15rem 0 .55rem 22px; }.audit-summary-actions button, .audit-more { border: 1px solid #d5dfeb; border-radius: 6px; padding: .3rem .5rem; background: white; color: #3567ae; font-size: 10px; cursor: pointer; }.audit-more { display: block; margin: .8rem auto .2rem; }.audit-empty { display: grid; min-height: 170px; place-content: center; gap: .5rem; color: #8b98ab; font-size: 12px; text-align: center; }.audit-detail-enter-active, .audit-detail-leave-active { transition: all .16s ease; }.audit-detail-enter-from, .audit-detail-leave-to { max-height: 0; opacity: 0; transform: translateY(-4px); }.audit-viewer button:focus-visible, .audit-viewer select:focus-visible, .audit-search:focus-within, .audit-scroll:focus-visible { outline: 2px solid #3978d6; outline-offset: 2px; }
:global(.dark) .audit-viewer { --audit-border: #293445; --audit-surface: #151b26; --audit-muted: #94a3b8; color: #e2e8f0; }.dark .audit-line:hover { background: #1b2a42; }.dark .audit-main__summary { color: #e5edf9; }.dark .audit-detail { background: #111a28; border-color: #4268a2; }.dark .audit-mode button { color: #b5c0d1; }.dark .audit-filters select, .dark .audit-search { color: #cdd8e8; }
@media (prefers-reduced-motion: reduce) { .audit-viewer *, .audit-viewer *::before, .audit-viewer *::after { scroll-behavior: auto !important; transition-duration: .01ms !important; animation-duration: .01ms !important; animation-iteration-count: 1 !important; } } @media (max-width: 768px) { .audit-toolbar { padding-inline: .75rem; }.audit-filters { overflow-x: auto; padding-inline: .75rem; }.audit-filters select { flex: 0 0 auto; }.audit-search { flex: 0 0 145px; }.audit-scroll { padding-inline: .55rem; }.audit-main__meta span:first-of-type { display: none; }.audit-detail { margin-left: 12px; padding: .45rem; }.audit-detail__grid { grid-template-columns: 1fr; }.audit-detail dl { grid-template-columns: repeat(2, minmax(0, 1fr)); }.audit-line { grid-template-columns: 20px minmax(0, 1fr) auto 24px; }.audit-type { font-size: 8px; }.audit-toolbar__controls > button, .audit-mode button, .audit-expand, .json-block button, .audit-summary-actions button, .audit-more, .audit-filters select { min-height: 44px; } }
</style>
