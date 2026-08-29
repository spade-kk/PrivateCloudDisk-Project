<template>
  <section class="execution-detail-panel" :class="`mode-${mode}`">
    <header class="execution-summary">
      <div class="execution-summary__identity"><span class="execution-summary__glyph"><i class="fa fa-cube"></i></span><div><p>{{ state.detail?.pluginName || pluginName || '插件执行' }}</p><h2>{{ executionId }}</h2><small>{{ state.detail?.version ? `版本 ${state.detail.version}` : '正在读取版本信息' }}</small></div></div>
      <div class="execution-summary__metrics"><span :class="['execution-status', `status-${String(state.detail?.executionStatus || '').toLowerCase()}`]">{{ statusText(state.detail?.executionStatus) }}</span><span><i class="fa fa-clock-o"></i> {{ duration(state.detail?.durationMs) }}</span><span><i class="fa fa-history"></i> {{ formatTime(state.detail?.startedAt) }}</span></div>
      <div class="execution-summary__actions"><button type="button" title="刷新执行详情" :disabled="state.loading" @click="refresh"><i class="fa fa-refresh" :class="{ 'fa-spin': state.loading }"></i></button><button v-if="mode === 'drawer'" type="button" title="在独立页面打开" @click="$emit('open-page')"><i class="fa fa-external-link"></i></button><button v-if="mode === 'drawer'" type="button" title="关闭（Esc）" @click="$emit('close')"><i class="fa fa-times"></i></button></div>
    </header>
    <div v-if="state.error" class="execution-error"><i class="fa fa-exclamation-triangle"></i><span>{{ state.error }}</span><button type="button" @click="refresh">重试</button></div>
    <nav class="execution-tabs" aria-label="执行详情内容"><button :class="{ active: tab === 'logs' }" type="button" @click="tab = 'logs'"><i class="fa fa-terminal"></i> 日志 <small>{{ state.detail?.logLineCount ?? state.logs.length }}</small></button><button :class="{ active: tab === 'audit' }" type="button" @click="tab = 'audit'"><i class="fa fa-sitemap"></i> 行为审计 <small>{{ state.detail?.auditCallCount ?? state.audits.length }}</small></button><button :class="{ active: tab === 'overview' }" type="button" @click="tab = 'overview'"><i class="fa fa-info-circle"></i> 摘要</button></nav>
    <main class="execution-detail-panel__content">
      <ExecutionLogViewer v-if="tab === 'logs'" :logs="state.logs" :loading="state.loadingLogs" :loading-more="state.loadingMoreLogs" :has-more="state.logsHasMore" :initial-level="state.logQuery.level" :initial-source="state.logQuery.source" @refresh="refreshLogs" @download="downloadLogs" @load-more="store.loadMoreLogs(executionId)" @filters="onLogFilters" />
      <ExecutionAuditTrailViewer v-else-if="tab === 'audit'" :audits="state.audits" :loading="state.loadingAudits" :loading-more="state.loadingMoreAudits" :has-more="state.auditsHasMore" :context="mode" @download="downloadAudits" @load-more="store.loadMoreAudits(executionId)" @filters="onAuditFilters" />
      <section v-else class="execution-overview">
        <div class="overview-grid"><article><span>触发事件</span><strong>{{ state.detail?.triggerEvent || '—' }}</strong></article><article><span>触发来源</span><strong>{{ state.detail?.triggerSource || '—' }}</strong></article><article><span>入口模块</span><strong>{{ state.detail?.entrypoint || '由 manifest 控制' }}</strong></article><article><span>日志行数</span><strong>{{ state.detail?.logLineCount ?? 0 }}</strong></article><article><span>审计调用</span><strong>{{ state.detail?.auditCallCount ?? 0 }}</strong></article><article><span>错误代码</span><strong :class="{ danger: state.detail?.errorCode }">{{ state.detail?.errorCode || '—' }}</strong></article></div>
        <article class="overview-block"><h3>执行输出摘要</h3><pre>{{ state.detail?.outputSummary || '本次执行没有返回输出摘要。' }}</pre></article>
        <article class="overview-block"><h3>沙箱资源限制</h3><pre>{{ JSON.stringify(state.detail?.manifestLimits || {}, null, 2) }}</pre></article>
      </section>
    </main>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import { usePluginExecutionDetailStore } from '@/stores/pluginExecutionDetailStore'
import ExecutionAuditTrailViewer from './ExecutionAuditTrailViewer.vue'
import ExecutionLogViewer from './ExecutionLogViewer.vue'

const props = withDefaults(defineProps<{ executionId: string; pluginName?: string; mode?: 'drawer' | 'page' }>(), { pluginName: '', mode: 'drawer' })
defineEmits<{ close: []; 'open-page': [] }>()
const store = usePluginExecutionDetailStore()
const toast = useToastStore()
const state = computed(() => store.ensure(props.executionId))
const tab = ref<'logs' | 'audit' | 'overview'>('logs')

async function refresh() { await store.load(props.executionId, true) }
async function refreshLogs() { await store.reloadLogs(props.executionId) }
async function downloadLogs() { try { await store.downloadLogs(props.executionId) } catch (error) { toast.showToast(error instanceof Error ? error.message : '日志下载失败', 'error') } }
async function downloadAudits() { try { await store.downloadAudits(props.executionId) } catch (error) { toast.showToast(error instanceof Error ? error.message : '审计导出失败', 'error') } }
function onLogFilters(filters: { level: string; source: string }) { store.reloadLogs(props.executionId, filters) }
function onAuditFilters(filters: { capabilityType: string; status: string }) { store.reloadAudits(props.executionId, { capability_type: filters.capabilityType, status: filters.status }) }
function duration(value?: number | null) { return value == null ? '—' : value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(2)} s` }
function formatTime(value?: string | null) { if (!value) return '—'; const date = new Date(value); return Number.isNaN(date.getTime()) ? value : date.toLocaleString() }
function statusText(value?: string | null) { return ({ SUCCESS: '成功', FAILED: '失败', TIMEOUT: '超时', RUNNING: '运行中', QUEUED: '排队中', SKIPPED: '已跳过' } as Record<string, string>)[value || ''] || value || '加载中' }
function onEscape(event: KeyboardEvent) { if (event.key === 'Escape' && props.mode === 'drawer') window.dispatchEvent(new CustomEvent('pcd-plugin-execution-close')) }
watch(() => props.executionId, () => { tab.value = 'logs'; store.load(props.executionId) }, { immediate: true })
onMounted(() => window.addEventListener('keydown', onEscape))
onBeforeUnmount(() => window.removeEventListener('keydown', onEscape))
</script>

<style scoped>
.execution-detail-panel { --detail-border: #e2e8f0; display: grid; grid-template-rows: auto auto auto minmax(0, 1fr); min-width: 0; min-height: 0; height: 100%; overflow: hidden; background: #fff; color: #263448; }.execution-summary { display: flex; align-items: center; gap: 1rem; padding: 1rem 1.1rem; border-bottom: 1px solid var(--detail-border); }.execution-summary__identity { display: flex; min-width: 0; align-items: center; gap: .7rem; }.execution-summary__glyph { display: grid; width: 35px; height: 35px; flex: 0 0 auto; place-items: center; border-radius: 10px; background: linear-gradient(135deg, #305fc0, #6f8ee4); color: white; }.execution-summary p, .execution-summary h2, .execution-summary small { overflow: hidden; margin: 0; text-overflow: ellipsis; white-space: nowrap; }.execution-summary p { color: #62718a; font-size: 11px; }.execution-summary h2 { max-width: 280px; color: #27364d; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }.execution-summary small { color: #91a0b5; font-size: 10px; }.execution-summary__metrics { display: flex; flex: 1; justify-content: flex-end; gap: .8rem; color: #78869b; font-size: 11px; }.execution-status { border-radius: 99px; padding: .25rem .55rem; background: #e8eef8; color: #426798; font-weight: 700; }.execution-status.status-success { background: #e0f4e6; color: #267743; }.execution-status.status-failed { background: #ffe5e8; color: #bc3848; }.execution-status.status-timeout { background: #fff1d3; color: #a86b18; }.execution-summary__actions { display: flex; gap: .35rem; }.execution-summary__actions button { width: 31px; height: 31px; border: 1px solid var(--detail-border); border-radius: 8px; background: white; color: #65758e; cursor: pointer; }.execution-summary__actions button:hover { border-color: #6f9ae8; color: #2d61bd; }.execution-error { display: flex; align-items: center; gap: .5rem; padding: .55rem 1rem; background: #fff3f4; color: #b33646; font-size: 12px; }.execution-error button { margin-left: auto; border: 0; background: transparent; color: inherit; text-decoration: underline; cursor: pointer; }.execution-tabs { display: flex; gap: .25rem; padding: .45rem .8rem 0; border-bottom: 1px solid var(--detail-border); }.execution-tabs button { border: 0; border-bottom: 2px solid transparent; padding: .55rem .75rem; background: transparent; color: #738198; font-size: 12px; cursor: pointer; }.execution-tabs button.active { border-color: #2d67cf; color: #1f57b3; font-weight: 700; }.execution-tabs small { margin-left: .25rem; color: #9aa7b9; }.execution-detail-panel__content { min-height: 0; overflow: hidden; padding: .7rem; }.execution-overview { height: 100%; overflow: auto; padding: .5rem; }.overview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: .7rem; }.overview-grid article, .overview-block { border: 1px solid var(--detail-border); border-radius: 10px; padding: .75rem; background: #fbfcfe; }.overview-grid span { display: block; color: #8290a5; font-size: 10px; }.overview-grid strong { display: block; overflow: hidden; margin-top: .25rem; color: #35445a; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.overview-grid strong.danger { color: #bc3848; }.overview-block { margin-top: .7rem; }.overview-block h3 { margin: 0 0 .45rem; font-size: 12px; }.overview-block pre { max-height: 180px; overflow: auto; margin: 0; color: #526077; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11px; line-height: 1.55; white-space: pre-wrap; }
:global(.dark) .execution-detail-panel { --detail-border: #2c3749; background: #141b27; color: #dce6f4; }.dark .execution-summary h2 { color: #e5eefc; }.dark .execution-summary__actions button { background: #182130; color: #b9c5d7; }.dark .execution-tabs button.active { color: #8fb8ff; }.dark .overview-grid article, .dark .overview-block { background: #17202e; }.dark .overview-grid strong, .dark .overview-block pre { color: #c8d5e7; }
@media (max-width: 768px) { .execution-summary { align-items: flex-start; padding: .75rem; }.execution-summary__metrics { display: none; }.execution-summary h2 { max-width: 180px; }.execution-detail-panel__content { padding: .45rem; }.execution-tabs { overflow-x: auto; padding-inline: .45rem; }.execution-tabs button, .execution-summary__actions button { min-height: 44px; min-width: 44px; flex: 0 0 auto; }.overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
