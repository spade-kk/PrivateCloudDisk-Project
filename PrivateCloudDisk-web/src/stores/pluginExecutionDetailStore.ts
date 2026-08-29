// ============================================================
// pluginExecutionDetailStore.ts — 插件执行可观测性状态域
// ============================================================
// [PLUGIN-EXEC-OBS-001] 原执行记录页只维护一段 outputSummary，无法分页、取消或复用
// 日志/审计详情。新状态域以 executionId 为键，抽屉与独立页面可共享真实后端数据，
// 同时避免切换不同执行记录时发生异步响应覆盖。
// ============================================================

import { computed, reactive } from 'vue'
import { defineStore } from 'pinia'
import { cookie } from '@/utils/cookie'
import { TOKEN_COOKIE_KEY } from '@/utils/request'
import {
  downloadPluginExecutionAuditTrailsApi,
  downloadPluginExecutionLogsApi,
  getPluginExecutionApi,
  getPluginExecutionAuditTrailsApi,
  getPluginExecutionLogsApi,
  type PluginExecutionAuditQuery,
  type PluginExecutionAuditTrail,
  type PluginExecutionDetail,
  type PluginExecutionLogLine,
  type PluginExecutionLogQuery,
} from '@/api/modules/plugins'

export interface PluginExecutionDetailState {
  executionId: string
  detail: PluginExecutionDetail | null
  logs: PluginExecutionLogLine[]
  audits: PluginExecutionAuditTrail[]
  logCursor: string | null
  auditCursor: string | null
  logsHasMore: boolean
  auditsHasMore: boolean
  loading: boolean
  loadingLogs: boolean
  loadingAudits: boolean
  loadingMoreLogs: boolean
  loadingMoreAudits: boolean
  error: string
  logQuery: PluginExecutionLogQuery
  auditQuery: PluginExecutionAuditQuery
  streamPaused: boolean
  updatedAt: number
}

function createState(executionId: string): PluginExecutionDetailState {
  return {
    executionId,
    detail: null,
    logs: [],
    audits: [],
    logCursor: null,
    auditCursor: null,
    logsHasMore: false,
    auditsHasMore: false,
    loading: false,
    loadingLogs: false,
    loadingAudits: false,
    loadingMoreLogs: false,
    loadingMoreAudits: false,
    error: '',
    logQuery: { order: 'desc', limit: 200 },
    auditQuery: { limit: 100 },
    streamPaused: false,
    updatedAt: 0,
  }
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '执行详情加载失败，请稍后重试'
}

function dedupeBy<T>(items: T[], key: (item: T) => string | number): T[] {
  const seen = new Set<string | number>()
  return items.filter((item) => {
    const id = key(item)
    if (seen.has(id)) return false
    seen.add(id)
    return true
  })
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

export const usePluginExecutionDetailStore = defineStore('pluginExecutionDetail', () => {
  const states = reactive<Record<string, PluginExecutionDetailState>>({})
  const requests = new Map<string, AbortController>()
  const streams = new Map<string, AbortController>()

  const openExecutionIds = computed(() => Object.keys(states))

  function ensure(executionId: string): PluginExecutionDetailState {
    if (!states[executionId]) states[executionId] = createState(executionId)
    return states[executionId]
  }

  function abort(executionId: string) {
    requests.get(executionId)?.abort()
    requests.delete(executionId)
  }

  async function load(executionId: string, force = false) {
    const state = ensure(executionId)
    if (!force && state.updatedAt && Date.now() - state.updatedAt < 10_000) return state
    abort(executionId)
    const controller = new AbortController()
    requests.set(executionId, controller)
    state.loading = true
    state.loadingLogs = true
    state.loadingAudits = true
    state.error = ''
    try {
      const [detail, logs, audits] = await Promise.all([
        getPluginExecutionApi(executionId, { signal: controller.signal }),
        getPluginExecutionLogsApi(executionId, { ...state.logQuery, cursor: undefined }, { signal: controller.signal }),
        getPluginExecutionAuditTrailsApi(executionId, { ...state.auditQuery, cursor: undefined }, { signal: controller.signal }),
      ])
      if (requests.get(executionId) !== controller) return state
      state.detail = detail.data
      // 服务端 desc 查询用于高效获取尾部，展示仍按 sequence 升序保留 Docker 日志时间感。
      state.logs = [...logs.data.items].sort((a, b) => a.sequenceNo - b.sequenceNo)
      state.audits = audits.data.items
      state.logCursor = logs.data.nextCursor || null
      state.auditCursor = audits.data.nextCursor || null
      state.logsHasMore = !!logs.data.hasMore
      state.auditsHasMore = !!audits.data.hasMore
      state.updatedAt = Date.now()
      if (state.detail?.executionStatus === 'RUNNING') startLogStream(executionId)
    } catch (error) {
      if (!controller.signal.aborted) state.error = errorMessage(error)
    } finally {
      if (requests.get(executionId) === controller) {
        state.loading = false
        state.loadingLogs = false
        state.loadingAudits = false
      }
    }
    return state
  }

  async function reloadLogs(executionId: string, query: PluginExecutionLogQuery = {}) {
    const state = ensure(executionId)
    state.logQuery = { ...state.logQuery, ...query, cursor: undefined }
    state.loadingLogs = true
    try {
      const response = await getPluginExecutionLogsApi(executionId, state.logQuery)
      state.logs = [...response.data.items].sort((a, b) => a.sequenceNo - b.sequenceNo)
      state.logCursor = response.data.nextCursor || null
      state.logsHasMore = !!response.data.hasMore
    } catch (error) {
      state.error = errorMessage(error)
    } finally {
      state.loadingLogs = false
    }
  }

  async function loadMoreLogs(executionId: string) {
    const state = ensure(executionId)
    if (!state.logsHasMore || !state.logCursor || state.loadingMoreLogs) return
    state.loadingMoreLogs = true
    try {
      const response = await getPluginExecutionLogsApi(executionId, { ...state.logQuery, cursor: state.logCursor })
      state.logs = dedupeBy([...response.data.items, ...state.logs], (item) => item.sequenceNo)
        .sort((a, b) => a.sequenceNo - b.sequenceNo)
      state.logCursor = response.data.nextCursor || null
      state.logsHasMore = !!response.data.hasMore
    } catch (error) {
      state.error = errorMessage(error)
    } finally {
      state.loadingMoreLogs = false
    }
  }

  async function reloadAudits(executionId: string, query: PluginExecutionAuditQuery = {}) {
    const state = ensure(executionId)
    state.auditQuery = { ...state.auditQuery, ...query, cursor: undefined }
    state.loadingAudits = true
    try {
      const response = await getPluginExecutionAuditTrailsApi(executionId, state.auditQuery)
      state.audits = response.data.items
      state.auditCursor = response.data.nextCursor || null
      state.auditsHasMore = !!response.data.hasMore
    } catch (error) {
      state.error = errorMessage(error)
    } finally {
      state.loadingAudits = false
    }
  }

  async function loadMoreAudits(executionId: string) {
    const state = ensure(executionId)
    if (!state.auditsHasMore || !state.auditCursor || state.loadingMoreAudits) return
    state.loadingMoreAudits = true
    try {
      const response = await getPluginExecutionAuditTrailsApi(executionId, { ...state.auditQuery, cursor: state.auditCursor })
      state.audits = dedupeBy([...state.audits, ...response.data.items], (item) => item.auditId)
      state.auditCursor = response.data.nextCursor || null
      state.auditsHasMore = !!response.data.hasMore
    } catch (error) {
      state.error = errorMessage(error)
    } finally {
      state.loadingMoreAudits = false
    }
  }

  async function downloadLogs(executionId: string) {
    const blob = await downloadPluginExecutionLogsApi(executionId)
    downloadBlob(blob, `plugin-execution-${executionId}.log`)
  }

  async function downloadAudits(executionId: string) {
    const blob = await downloadPluginExecutionAuditTrailsApi(executionId)
    downloadBlob(blob, `plugin-execution-${executionId}-audit.json`)
  }

  /**
   * 以 fetch 流消费 SSE，原因是 EventSource 无法附加现有 Bearer 请求头。
   * [PLUGIN-EXEC-OBS-001] 仍只连接 Plugin Service，不暴露 Runtime 地址或凭据给浏览器。
   */
  async function startLogStream(executionId: string) {
    if (streams.has(executionId)) return
    const controller = new AbortController()
    streams.set(executionId, controller)
    const state = ensure(executionId)
    try {
      const base = import.meta.env.VITE_API_BASE_URL || '/api/v1'
      // 项目当前浏览器目标不包含 ES2022 Array.prototype.at；采用末项索引保持兼容。
      const lastLog = state.logs[state.logs.length - 1]
      const path = `${String(base).replace(/\/$/, '')}/plugins/executions/${encodeURIComponent(executionId)}/logs/stream?after=${lastLog?.sequenceNo || 0}`
      const response = await fetch(new URL(path, window.location.origin), {
        headers: { ...(cookie.get(TOKEN_COOKIE_KEY) ? { Authorization: `Bearer ${cookie.get(TOKEN_COOKIE_KEY)}` } : {}) },
        credentials: 'include',
        signal: controller.signal,
      })
      if (!response.ok || !response.body) throw new Error(`日志流连接失败（HTTP ${response.status}）`)
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (!controller.signal.aborted) {
        const chunk = await reader.read()
        if (chunk.done) break
        buffer += decoder.decode(chunk.value, { stream: true })
        const frames = buffer.split(/\r?\n\r?\n/)
        buffer = frames.pop() || ''
        for (const frame of frames) {
          if (!/^event:\s*log/m.test(frame)) continue
          const payload = frame.split(/\r?\n/).find((line) => line.startsWith('data:'))?.slice(5).trim()
          if (!payload) continue
          try {
            const line = JSON.parse(payload) as PluginExecutionLogLine
            state.logs = dedupeBy([...state.logs, line], (item) => item.sequenceNo).sort((a, b) => a.sequenceNo - b.sequenceNo)
          } catch { /* 单帧格式错误不影响后续已鉴权日志帧 */ }
        }
      }
    } catch (error) {
      if (!controller.signal.aborted && state.detail?.executionStatus === 'RUNNING') state.error = errorMessage(error)
    } finally {
      if (streams.get(executionId) === controller) streams.delete(executionId)
    }
  }

  function stopLogStream(executionId: string) {
    streams.get(executionId)?.abort()
    streams.delete(executionId)
  }

  function close(executionId: string) {
    abort(executionId)
    stopLogStream(executionId)
    delete states[executionId]
  }

  function reset() {
    Object.keys(states).forEach(close)
  }

  return {
    states,
    openExecutionIds,
    ensure,
    load,
    reloadLogs,
    loadMoreLogs,
    reloadAudits,
    loadMoreAudits,
    downloadLogs,
    downloadAudits,
    startLogStream,
    stopLogStream,
    abort,
    close,
    reset,
  }
})
